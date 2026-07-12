package com.centralbrain.runtime;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

import com.centralbrain.sdk.CentralBrainSdk;
import com.centralbrain.sdk.production.AgentTaskRequest;
import com.centralbrain.sdk.production.ICentralBrainRuntime;
import com.centralbrain.sdk.production.ICentralBrainTaskCallback;
import com.centralbrain.sdk.production.TaskFailure;
import com.centralbrain.sdk.production.TaskHandle;
import com.centralbrain.sdk.production.TaskResult;
import com.centralbrain.sdk.production.TaskUpdate;
import com.centralbrain.runtime.effects.EffectDeliveryActivationSnapshot;
import com.centralbrain.runtime.events.EventRuntimeReadinessSnapshot;
import com.centralbrain.runtime.identity.AndroidCallerIdentityResolver;
import com.centralbrain.runtime.identity.CallerIdentitySnapshot;
import com.centralbrain.runtime.identity.DurablePrincipalFingerprint;
import com.centralbrain.runtime.model.ModelRuntimeReadinessSnapshot;
import com.centralbrain.runtime.persistence.CentralBrainDatabase;
import com.centralbrain.runtime.persistence.DurableDigest;
import com.centralbrain.runtime.persistence.DurableTaskRepository;
import com.centralbrain.runtime.policy.AndroidCapabilityPolicyLoader;
import com.centralbrain.runtime.policy.CallerCapabilityPolicy;
import com.centralbrain.runtime.policy.CallerCapabilityPolicy.Capability;
import com.centralbrain.runtime.supervisor.JobSupervisor;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Typed production Binder with a bounded Job Supervisor and R4 durable task state.
 * Req IDs: XSC-001, XSC-004, XSC-006, NV-F-001, FW-U-007, NV-G-003,
 * NV-G-005, NV-G-006, NV-G-007, NV-P-002.
 */
public final class CentralBrainRuntimeService extends Service {
    public static final String BIND_PERMISSION = "com.centralbrain.permission.BIND_RUNTIME";
    public static final String RUNTIME_STAGE = CentralBrainSdk.EVOLUTION_STAGE;

    private static final String TAG = "CentralBrainRuntime";
    private static final long START_DELAY_MS = 40;
    private static final long COMPLETE_DELAY_MS = BuildConfig.DEBUG ? 3000 : 160;
    private static final int MAX_TEXT_LENGTH = 4096;
    private static final int MAX_TASK_RECORDS = 128;
    private static final int MAX_REPLAY_CALLBACKS_PER_TASK = 4;
    private static final long TERMINAL_RETENTION_MS = TimeUnit.MINUTES.toMillis(5);

    private final Object admissionLock = new Object();
    private final EffectDeliveryActivationSnapshot effectDeliveryActivation =
            EffectDeliveryActivationSnapshot.current();
    private final ModelRuntimeReadinessSnapshot modelRuntimeReadiness =
            ModelRuntimeReadinessSnapshot.current();
    private final EventRuntimeReadinessSnapshot eventRuntimeReadiness =
            EventRuntimeReadinessSnapshot.current();
    private final ConcurrentMap<String, TaskRecord> tasks = new ConcurrentHashMap<>();
    private final JobSupervisor jobSupervisor = new JobSupervisor(
            MAX_TASK_RECORDS,
            TERMINAL_RETENTION_MS,
            SystemClock::elapsedRealtime);
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "central-brain-task-runner");
        thread.setDaemon(true);
        return thread;
    });
    private CentralBrainDatabase database;
    private DurableTaskRepository taskRepository;
    private Future<DurableTaskRepository.ReconciliationReport> startupReconciliation;

    private final ICentralBrainRuntime.Stub binder = new ICentralBrainRuntime.Stub() {
        @Override
        public int getProtocolVersion() {
            resolveAuthorizedCaller(Capability.PROTOCOL_READ);
            return ICentralBrainRuntime.INTERFACE_VERSION;
        }

        @Override
        public String getProtocolHash() {
            resolveAuthorizedCaller(Capability.PROTOCOL_READ);
            return ICentralBrainRuntime.INTERFACE_HASH;
        }

        @Override
        public TaskHandle submitAgentTask(
                AgentTaskRequest request,
                ICentralBrainTaskCallback callback) {
            CallerIdentitySnapshot caller = resolveAuthorizedCaller(Capability.TASK_SUBMIT);
            awaitStartupReconciliation();
            validateRequest(request, callback);
            removeTaskRecords(jobSupervisor.pruneExpired());

            synchronized (admissionLock) {
                String ownerFingerprint = DurablePrincipalFingerprint.from(caller);
                String payloadDigest = requestDigest(request);
                DurableTaskRepository.Admission durableAdmission;
                try {
                    durableAdmission = taskRepository.admit(
                            ownerFingerprint,
                            safe(request.sessionId),
                            request.clientRequestId,
                            request.idempotencyKey,
                            payloadDigest,
                            request.deadlineElapsedRealtimeMs <= 0
                                    || request.deadlineElapsedRealtimeMs
                                            > SystemClock.elapsedRealtime());
                } catch (DurableTaskRepository.AdmissionRejectedException exception) {
                    throw new IllegalArgumentException(exception.getMessage());
                }
                if (durableAdmission.getOutcome()
                        == DurableTaskRepository.AdmissionOutcome.REPLAYED) {
                    return handleDurableReplay(durableAdmission, caller, callback);
                }

                String taskId = durableAdmission.getTaskId();
                JobSupervisor.Admission admission;
                try {
                    admission = jobSupervisor.admit(taskId, caller, "accepted");
                } catch (RuntimeException exception) {
                    failUnscheduledAdmission(taskId, ownerFingerprint, payloadDigest);
                    throw exception;
                }
                removeTaskRecords(admission.getEvictedTaskIds());
                TaskRecord record = new TaskRecord(
                        taskId,
                        request,
                        callback,
                        admission.getSnapshot().getAcceptedAtElapsedRealtimeMs(),
                        ownerFingerprint,
                        payloadDigest);
                record.deathRecipient = () -> handleCallbackDeath(record);

                try {
                    callback.asBinder().linkToDeath(record.deathRecipient, 0);
                } catch (RemoteException exception) {
                    jobSupervisor.remove(taskId);
                    failUnscheduledAdmission(taskId, ownerFingerprint, payloadDigest);
                    throw new IllegalStateException("callback binder is already dead");
                }

                tasks.put(taskId, record);
                JobSupervisor.Snapshot current = jobSupervisor.find(taskId);
                if (current != null && !current.isTerminal()) {
                    executor.schedule(() -> startTask(record), START_DELAY_MS, TimeUnit.MILLISECONDS);
                }
                Log.i(TAG, "accepted taskId=" + taskId + " " + caller.auditSummary()
                        + " hardware_accessed=false");
                return copyHandle(record.handle);
            }
        }

        @Override
        public boolean cancelTask(TaskHandle handle, int reasonCode) {
            CallerIdentitySnapshot caller = resolveAuthorizedCaller(Capability.TASK_CANCEL_OWN);
            awaitStartupReconciliation();
            validateCancelReason(reasonCode);
            removeTaskRecords(jobSupervisor.pruneExpired());
            TaskRecord record = findRecord(handle);
            return record != null && cancelRecord(record, reasonCode, true, caller);
        }

        @Override
        public TaskUpdate getTaskStatus(TaskHandle handle) {
            CallerIdentitySnapshot caller = resolveAuthorizedCaller(Capability.TASK_STATUS_OWN);
            awaitStartupReconciliation();
            removeTaskRecords(jobSupervisor.pruneExpired());
            TaskRecord record = findRecord(handle);
            JobSupervisor.Snapshot snapshot = record == null
                    ? null
                    : jobSupervisor.findOwned(record.handle.taskId, caller);
            if (snapshot == null) {
                String taskId = handle == null ? "" : safe(handle.taskId);
                if (!isBlank(taskId)) {
                    DurableTaskRepository.Snapshot durable = taskRepository.findOwned(
                            taskId,
                            DurablePrincipalFingerprint.from(caller));
                    if (durable != null) {
                        return updateFor(durable);
                    }
                }
                return updateFor(taskId, ICentralBrainRuntime.TASK_STATE_UNKNOWN, 0, 0,
                        "unknown task");
            }
            return updateFor(snapshot);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        identityResolver = new AndroidCallerIdentityResolver(this);
        capabilityPolicy = AndroidCapabilityPolicyLoader.load(
                this,
                R.xml.central_brain_capability_policy,
                identityResolver.resolveOwnIdentity());
        database = CentralBrainDatabase.open(this);
        taskRepository = DurableTaskRepository.create(database);
        startupReconciliation = executor.submit(() -> {
            DurableTaskRepository.ReconciliationReport reconciliation =
                    taskRepository.reconcileInterruptedTasks();
            Log.i(TAG, "restart reconciliation completed"
                    + " restart_reconciliation_enabled=true"
                    + " task_execution_resume_enabled=false"
                    + " restart_reconciled_active_count="
                    + reconciliation.getActiveTaskCount()
                    + " restart_reconciled_incomplete_completion_count="
                    + reconciliation.getIncompleteCompletionCount()
                    + " durable_dispatch_enabled=false"
                    + " hardware_accessed=false");
            return reconciliation;
        });
        Log.i(TAG, "created maturity=" + CentralBrainSdk.MATURITY
                + " evolution_stage=" + RUNTIME_STAGE
                + " job_supervisor_max_records=" + MAX_TASK_RECORDS
                + " terminal_retention_ms=" + TERMINAL_RETENTION_MS
                + " capability_default=deny"
                + " capability_rule_count=" + capabilityPolicy.getRuleCount()
                + " runtime_repository_wired=true"
                + " task_recovery_enabled=false"
                + " restart_reconciliation_enabled=true"
                + " restart_reconciliation_pending=true"
                + " task_execution_resume_enabled=false"
                + " production_effect_activation_gate_wired=true"
                + " production_effect_delivery_activation_allowed="
                + effectDeliveryActivation.isActivationAllowed()
                + " production_effect_adapter_configured="
                + effectDeliveryActivation.isAdapterConfigured()
                + " production_effect_material_source="
                + effectDeliveryActivation.getMaterialSourceId()
                + " production_effect_material_durable="
                + effectDeliveryActivation.isMaterialDurable()
                + " production_effect_apply_enabled="
                + effectDeliveryActivation.isApplyEnabled()
                + " production_effect_status_query_enabled="
                + effectDeliveryActivation.isStatusQueryEnabled()
                + " production_effect_activation_blockers="
                + effectDeliveryActivation.getBlockersCsv()
                + " model_runtime_readiness_snapshot_wired=true"
                + " production_inference_allowed="
                + modelRuntimeReadiness.isProductionInferenceAllowed()
                + " deterministic_stub_profile_id="
                + modelRuntimeReadiness.getDeterministicStubProfileId()
                + " deterministic_stub_lifecycle="
                + modelRuntimeReadiness.getDeterministicStubLifecycle()
                + " deterministic_stub_health="
                + modelRuntimeReadiness.getDeterministicStubHealth()
                + " deterministic_stub_detail_code="
                + modelRuntimeReadiness.getDeterministicStubDetailCode()
                + " deterministic_stub_implementation_configured="
                + modelRuntimeReadiness.isDeterministicStubImplementationConfigured()
                + " deterministic_stub_routing_enabled="
                + modelRuntimeReadiness.isDeterministicStubRoutingEnabled()
                + " vendor_npu_profile_id="
                + modelRuntimeReadiness.getVendorNpuProfileId()
                + " vendor_npu_lifecycle="
                + modelRuntimeReadiness.getVendorNpuLifecycle()
                + " vendor_npu_health="
                + modelRuntimeReadiness.getVendorNpuHealth()
                + " vendor_npu_detail_code="
                + modelRuntimeReadiness.getVendorNpuDetailCode()
                + " vendor_npu_provider_available="
                + modelRuntimeReadiness.isVendorNpuProviderAvailable()
                + " scheduler_production_wired="
                + modelRuntimeReadiness.isSchedulerProductionWired()
                + " production_model_router_wired="
                + modelRuntimeReadiness.isProductionModelRouterWired()
                + " production_model_router_dispatch_enabled="
                + modelRuntimeReadiness.isProductionModelRouterDispatchEnabled()
                + " model_runtime_activation_blockers="
                + modelRuntimeReadiness.getBlockersCsv()
                + " event_runtime_readiness_snapshot_wired=true"
                + " event_runtime_activation_allowed="
                + eventRuntimeReadiness.isActivationAllowed()
                + " bounded_event_runtime_implementation_available="
                + eventRuntimeReadiness.isBoundedEventRuntimeImplementationAvailable()
                + " event_cursor_schema_ready="
                + eventRuntimeReadiness.isEventCursorSchemaReady()
                + " event_repository_implementation_available="
                + eventRuntimeReadiness.isEventRepositoryImplementationAvailable()
                + " durable_event_source_available="
                + eventRuntimeReadiness.isDurableEventSourceAvailable()
                + " event_runtime_production_wired="
                + eventRuntimeReadiness.isEventRuntimeProductionWired()
                + " event_cursor_repository_production_wired="
                + eventRuntimeReadiness.isEventRepositoryProductionWired()
                + " event_cursor_persistence_wired="
                + eventRuntimeReadiness.isEventCursorPersistenceWired()
                + " event_callback_binder_wired="
                + eventRuntimeReadiness.isCallbackBinderWired()
                + " event_broker_production_wired="
                + eventRuntimeReadiness.isProductionBrokerWired()
                + " event_middleware_chain_wired="
                + eventRuntimeReadiness.isMiddlewareChainWired()
                + " event_runtime_activation_blockers="
                + eventRuntimeReadiness.getBlockersCsv()
                + " durable_dispatch_enabled=false"
                + " hardware_accessed=false");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "started startId=" + startId + " hardware_accessed=false");
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "production binder requested hardware_accessed=false");
        return binder;
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        writer.println("production_effect_activation_gate_wired=true");
        writer.println("production_effect_delivery_activation_allowed="
                + effectDeliveryActivation.isActivationAllowed());
        writer.println("production_effect_adapter_configured="
                + effectDeliveryActivation.isAdapterConfigured());
        writer.println("production_effect_material_source="
                + effectDeliveryActivation.getMaterialSourceId());
        writer.println("production_effect_material_durable="
                + effectDeliveryActivation.isMaterialDurable());
        writer.println("production_effect_apply_enabled="
                + effectDeliveryActivation.isApplyEnabled());
        writer.println("production_effect_status_query_enabled="
                + effectDeliveryActivation.isStatusQueryEnabled());
        writer.println("production_effect_activation_blockers="
                + effectDeliveryActivation.getBlockersCsv());
        writer.println("model_runtime_readiness_snapshot_wired=true");
        writer.println("production_inference_allowed="
                + modelRuntimeReadiness.isProductionInferenceAllowed());
        writer.println("model_provider_contract_available="
                + modelRuntimeReadiness.isModelProviderContractAvailable());
        writer.println("inference_scheduler_contract_available="
                + modelRuntimeReadiness.isInferenceSchedulerContractAvailable());
        writer.println("test_model_router_implementation_available="
                + modelRuntimeReadiness.isTestModelRouterImplementationAvailable());
        writer.println("model_router_test_only="
                + modelRuntimeReadiness.isTestModelRouterTestOnly());
        writer.println("deterministic_stub_profile_id="
                + modelRuntimeReadiness.getDeterministicStubProfileId());
        writer.println("deterministic_stub_lifecycle="
                + modelRuntimeReadiness.getDeterministicStubLifecycle());
        writer.println("deterministic_stub_health="
                + modelRuntimeReadiness.getDeterministicStubHealth());
        writer.println("deterministic_stub_detail_code="
                + modelRuntimeReadiness.getDeterministicStubDetailCode());
        writer.println("deterministic_stub_implementation_configured="
                + modelRuntimeReadiness.isDeterministicStubImplementationConfigured());
        writer.println("deterministic_stub_routing_enabled="
                + modelRuntimeReadiness.isDeterministicStubRoutingEnabled());
        writer.println("vendor_npu_profile_id="
                + modelRuntimeReadiness.getVendorNpuProfileId());
        writer.println("vendor_npu_lifecycle="
                + modelRuntimeReadiness.getVendorNpuLifecycle());
        writer.println("vendor_npu_health="
                + modelRuntimeReadiness.getVendorNpuHealth());
        writer.println("vendor_npu_detail_code="
                + modelRuntimeReadiness.getVendorNpuDetailCode());
        writer.println("vendor_npu_provider_available="
                + modelRuntimeReadiness.isVendorNpuProviderAvailable());
        writer.println("scheduler_production_wired="
                + modelRuntimeReadiness.isSchedulerProductionWired());
        writer.println("production_model_router_wired="
                + modelRuntimeReadiness.isProductionModelRouterWired());
        writer.println("production_model_router_dispatch_enabled="
                + modelRuntimeReadiness.isProductionModelRouterDispatchEnabled());
        writer.println("ollama_android_provider_configured="
                + modelRuntimeReadiness.isOllamaAndroidProviderConfigured());
        writer.println("model_runtime_activation_blockers="
                + modelRuntimeReadiness.getBlockersCsv());
        writer.println("event_runtime_readiness_snapshot_wired=true");
        writer.println("event_runtime_activation_allowed="
                + eventRuntimeReadiness.isActivationAllowed());
        writer.println("bounded_event_runtime_implementation_available="
                + eventRuntimeReadiness.isBoundedEventRuntimeImplementationAvailable());
        writer.println("event_cursor_schema_ready="
                + eventRuntimeReadiness.isEventCursorSchemaReady());
        writer.println("event_repository_implementation_available="
                + eventRuntimeReadiness.isEventRepositoryImplementationAvailable());
        writer.println("trusted_event_topic_count="
                + eventRuntimeReadiness.getTrustedTopicCount());
        writer.println("durable_event_source_available="
                + eventRuntimeReadiness.isDurableEventSourceAvailable());
        writer.println("event_runtime_production_wired="
                + eventRuntimeReadiness.isEventRuntimeProductionWired());
        writer.println("event_cursor_repository_production_wired="
                + eventRuntimeReadiness.isEventRepositoryProductionWired());
        writer.println("event_cursor_persistence_wired="
                + eventRuntimeReadiness.isEventCursorPersistenceWired());
        writer.println("event_callback_binder_wired="
                + eventRuntimeReadiness.isCallbackBinderWired());
        writer.println("event_broker_production_wired="
                + eventRuntimeReadiness.isProductionBrokerWired());
        writer.println("event_middleware_chain_wired="
                + eventRuntimeReadiness.isMiddlewareChainWired());
        writer.println("raw_event_payload_persisted="
                + eventRuntimeReadiness.isRawEventPayloadPersisted());
        writer.println("dds_runtime_active=" + eventRuntimeReadiness.isDdsRuntimeActive());
        writer.println("network_transport_active="
                + eventRuntimeReadiness.isNetworkTransportActive());
        writer.println("vehicle_bus_accessed="
                + eventRuntimeReadiness.isVehicleBusAccessed());
        writer.println("event_runtime_activation_blockers="
                + eventRuntimeReadiness.getBlockersCsv());
        writer.println("service_dispatch_triggered=false");
        writer.println("hardware_accessed=false");
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        tasks.values().forEach(this::unlinkCallbackDeath);
        tasks.clear();
        if (database != null) {
            database.close();
        }
        Log.i(TAG, "destroyed hardware_accessed=false");
        super.onDestroy();
    }

    private AndroidCallerIdentityResolver identityResolver;
    private CallerCapabilityPolicy capabilityPolicy;

    private static void validateRequest(
            AgentTaskRequest request,
            ICentralBrainTaskCallback callback) {
        if (request == null || callback == null) {
            invalidArgument("request and callback are required");
        }
        if (request.schemaVersion != 1) {
            invalidArgument("unsupported request schemaVersion");
        }
        if (isBlank(request.clientRequestId)
                || isBlank(request.utterance)
                || isBlank(request.idempotencyKey)) {
            invalidArgument("clientRequestId, utterance and idempotencyKey are required");
        }
        if (tooLong(request.clientRequestId)
                || tooLong(request.sessionId)
                || tooLong(request.utterance)
                || tooLong(request.locale)
                || tooLong(request.idempotencyKey)) {
            invalidArgument("request text exceeds limit");
        }
        if (request.priority < 0 || request.priority > 3) {
            invalidArgument("priority must be in range 0..3");
        }
    }

    private static void invalidArgument(String message) {
        throw new IllegalArgumentException(message);
    }

    private static void validateCancelReason(int reasonCode) {
        if (reasonCode < ICentralBrainRuntime.CANCEL_REASON_USER
                || reasonCode > ICentralBrainRuntime.CANCEL_REASON_POLICY) {
            invalidArgument("unsupported cancellation reason");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean tooLong(String value) {
        return value != null && value.length() > MAX_TEXT_LENGTH;
    }

    private TaskRecord findRecord(TaskHandle handle) {
        if (handle == null || isBlank(handle.taskId)) {
            return null;
        }
        return tasks.get(handle.taskId);
    }

    private void startTask(TaskRecord record) {
        TaskUpdate accepted;
        TaskUpdate running;
        synchronized (record) {
            JobSupervisor.Snapshot before = jobSupervisor.find(record.handle.taskId);
            if (before == null || before.isTerminal()) {
                return;
            }
            accepted = updateFor(before);
            persistTransition(record, before, JobSupervisor.State.RUNNING, 50);
            JobSupervisor.Transition transition = jobSupervisor.transition(
                    record.handle.taskId,
                    JobSupervisor.State.RUNNING,
                    50,
                    "deterministic stub running");
            if (!transition.wasApplied()) {
                return;
            }
            running = updateFor(transition.getSnapshot());
        }

        deliverUpdate(record, accepted);
        synchronized (record) {
            JobSupervisor.Snapshot current = jobSupervisor.find(record.handle.taskId);
            if (current == null || current.isTerminal()) {
                return;
            }
        }
        deliverUpdate(record, running);
        synchronized (record) {
            JobSupervisor.Snapshot current = jobSupervisor.find(record.handle.taskId);
            if (current == null || current.isTerminal()) {
                return;
            }
        }
        executor.schedule(() -> completeTask(record), COMPLETE_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void completeTask(TaskRecord record) {
        TaskUpdate completed;
        TaskResult result;
        synchronized (record) {
            JobSupervisor.Snapshot before = jobSupervisor.find(record.handle.taskId);
            if (before == null || before.isTerminal()) {
                return;
            }
            persistTransition(record, before, JobSupervisor.State.COMPLETED, 100);
            JobSupervisor.Transition transition = jobSupervisor.transition(
                    record.handle.taskId,
                    JobSupervisor.State.COMPLETED,
                    100,
                    "deterministic stub completed");
            if (!transition.wasApplied()) {
                return;
            }
            completed = updateFor(transition.getSnapshot());
            result = completedResult(record);
        }

        deliverUpdate(record, completed);
        for (ICentralBrainTaskCallback callback : callbacksFor(record)) {
            try {
                callback.onTaskCompleted(result);
            } catch (RemoteException exception) {
                handleDeliveryFailure(record, callback, "completion", exception);
            }
        }
        clearReplayCallbacks(record);
        unlinkCallbackDeath(record);
        settleTerminalDelivery(record);
    }

    private boolean cancelRecord(
            TaskRecord record,
            int reasonCode,
            boolean notifyClient,
            CallerIdentitySnapshot caller) {
        TaskUpdate cancelled;
        TaskFailure failure;
        synchronized (record) {
            JobSupervisor.Snapshot before = caller == null
                    ? jobSupervisor.find(record.handle.taskId)
                    : jobSupervisor.findOwned(record.handle.taskId, caller);
            if (before == null) {
                return false;
            }
            if (before.getState() == JobSupervisor.State.CANCELLED) {
                return true;
            }
            if (before.isTerminal()) {
                return false;
            }
            persistTransition(
                    record,
                    before,
                    JobSupervisor.State.CANCELLED,
                    before.getProgressPercent());
            JobSupervisor.Transition transition = caller == null
                    ? jobSupervisor.cancelSystem(
                            record.handle.taskId,
                            "cancelled reason=" + reasonCode)
                    : jobSupervisor.cancelOwned(
                            record.handle.taskId,
                            caller,
                            "cancelled reason=" + reasonCode);
            if (transition.getOutcome() == JobSupervisor.TransitionOutcome.ALREADY_CANCELLED) {
                return true;
            }
            if (!transition.wasApplied()) {
                return false;
            }
            cancelled = updateFor(transition.getSnapshot());
            failure = new TaskFailure();
            failure.taskId = record.handle.taskId;
            failure.errorCode = ICentralBrainRuntime.ERROR_CANCELLED;
            failure.errorMessage = "task cancelled reason=" + reasonCode;
            failure.retryable = false;
        }

        if (notifyClient) {
            executor.execute(() -> notifyCancellation(record, cancelled, failure));
        } else {
            unlinkCallbackDeath(record);
            settleTerminalDelivery(record);
        }
        Log.i(TAG, "cancelled taskId=" + record.handle.taskId + " reason=" + reasonCode
                + " hardware_accessed=false");
        return true;
    }

    private void notifyCancellation(
            TaskRecord record,
            TaskUpdate cancelled,
            TaskFailure failure) {
        deliverUpdate(record, cancelled);
        for (ICentralBrainTaskCallback callback : callbacksFor(record)) {
            try {
                callback.onTaskFailed(failure);
            } catch (RemoteException exception) {
                handleDeliveryFailure(record, callback, "cancel", exception);
            }
        }
        clearReplayCallbacks(record);
        unlinkCallbackDeath(record);
        settleTerminalDelivery(record);
    }

    private void deliverUpdate(TaskRecord record, TaskUpdate update) {
        for (ICentralBrainTaskCallback callback : callbacksFor(record)) {
            try {
                callback.onTaskUpdate(update);
            } catch (RemoteException exception) {
                handleDeliveryFailure(record, callback, "update", exception);
            }
        }
    }

    private void handleCallbackDeath(TaskRecord record) {
        boolean cancelled = cancelRecord(
                record,
                ICentralBrainRuntime.CANCEL_REASON_CLIENT_DIED,
                false,
                null);
        if (cancelled) {
            Log.i(TAG, "callback died taskId=" + record.handle.taskId
                    + " hardware_accessed=false");
        }
    }

    private void unlinkCallbackDeath(TaskRecord record) {
        if (record.deathRecipient == null) {
            return;
        }
        try {
            record.primaryCallback.asBinder().unlinkToDeath(record.deathRecipient, 0);
        } catch (NoSuchElementException ignored) {
            // The remote callback already died and Binder removed the recipient.
        }
    }

    private static TaskHandle copyHandle(TaskHandle source) {
        TaskHandle copy = new TaskHandle();
        copy.schemaVersion = source.schemaVersion;
        copy.taskId = source.taskId;
        copy.acceptedAtElapsedRealtimeMs = source.acceptedAtElapsedRealtimeMs;
        return copy;
    }

    private TaskHandle handleDurableReplay(
            DurableTaskRepository.Admission durableAdmission,
            CallerIdentitySnapshot caller,
            ICentralBrainTaskCallback callback) {
        TaskRecord record = tasks.get(durableAdmission.getTaskId());
        if (record == null) {
            TaskHandle handle = handleFrom(durableAdmission);
            executor.execute(() -> notifyDurableReplayWithoutLiveRecord(handle, caller, callback));
            Log.w(TAG, "durable replay uses restart reconciliation taskId=" + handle.taskId
                    + " runtime_repository_wired=true restart_reconciliation_enabled=true"
                    + " task_execution_resume_enabled=false"
                    + " hardware_accessed=false");
            return handle;
        }

        JobSupervisor.Snapshot snapshot;
        synchronized (record) {
            snapshot = jobSupervisor.findOwned(record.handle.taskId, caller);
            if (snapshot == null) {
                throw new SecurityException("durable task replay is not owned by caller");
            }
            if (!snapshot.isTerminal()) {
                attachReplayCallbackLocked(record, callback);
            }
        }
        if (snapshot.isTerminal()) {
            executor.execute(() -> notifyTerminalReplay(record, snapshot, callback));
        } else {
            TaskUpdate current = updateFor(snapshot);
            executor.execute(() -> deliverSingleUpdate(record, callback, current));
        }
        Log.i(TAG, "durable replay taskId=" + record.handle.taskId
                + " state=" + snapshot.getState()
                + " runtime_repository_wired=true hardware_accessed=false");
        return copyHandle(record.handle);
    }

    private void attachReplayCallbackLocked(
            TaskRecord record,
            ICentralBrainTaskCallback callback) {
        IBinder binder = callback.asBinder();
        if (record.primaryCallback.asBinder().equals(binder)
                || record.replayCallbacks.containsKey(binder)) {
            return;
        }
        if (record.replayCallbacks.size() >= MAX_REPLAY_CALLBACKS_PER_TASK) {
            throw new IllegalStateException("task replay callback capacity exhausted");
        }
        record.replayCallbacks.put(binder, callback);
    }

    private void notifyTerminalReplay(
            TaskRecord record,
            JobSupervisor.Snapshot snapshot,
            ICentralBrainTaskCallback callback) {
        try {
            callback.onTaskUpdate(updateFor(snapshot));
            if (snapshot.getState() == JobSupervisor.State.COMPLETED) {
                callback.onTaskCompleted(completedResult(record));
            } else {
                callback.onTaskFailed(terminalFailure(record, snapshot.getState()));
            }
        } catch (RemoteException exception) {
            Log.w(TAG, "terminal replay callback failed taskId=" + record.handle.taskId,
                    exception);
        }
    }

    private void notifyDurableReplayWithoutLiveRecord(
            TaskHandle handle,
            CallerIdentitySnapshot caller,
            ICentralBrainTaskCallback callback) {
        String ownerFingerprint = DurablePrincipalFingerprint.from(caller);
        DurableTaskRepository.Snapshot durable = taskRepository.findOwned(
                handle.taskId,
                ownerFingerprint);
        try {
            if (durable != null) {
                callback.onTaskUpdate(updateFor(durable));
            }
            if (durable != null
                    && DurableTaskRepository.STATE_COMPLETED.equals(durable.getState())) {
                callback.onTaskCompleted(recoveredCompletedResult(handle.taskId));
            } else {
                TaskFailure failure = new TaskFailure();
                failure.taskId = handle.taskId;
                failure.errorCode = durable != null
                                && DurableTaskRepository.STATE_CANCELLED.equals(
                                        durable.getState())
                        ? ICentralBrainRuntime.ERROR_CANCELLED
                        : ICentralBrainRuntime.ERROR_INTERNAL;
                if (durable != null
                        && DurableTaskRepository.STATE_CANCELLED.equals(durable.getState())) {
                    failure.errorMessage = "task was cancelled before process restart";
                } else if (durable != null && durable.isTerminal()) {
                    failure.errorMessage = "task terminated by restart reconciliation";
                } else {
                    failure.errorMessage = "durable task restart reconciliation is pending";
                }
                failure.retryable = failure.errorCode == ICentralBrainRuntime.ERROR_INTERNAL;
                callback.onTaskFailed(failure);
            }
        } catch (RemoteException exception) {
            Log.w(TAG, "durable replay callback failed taskId=" + handle.taskId, exception);
        } finally {
            if (durable != null
                    && durable.isTerminal()
                    && !durable.isTerminalDeliverySettled()) {
                try {
                    String detailDigest = DurableDigest.sha256(
                            "central-brain-restart-replay-settlement-v1",
                            durable.getTaskId(),
                            durable.getState(),
                            Long.toString(durable.getSequence()));
                    taskRepository.settleTerminalDelivery(
                            durable.getTaskId(),
                            ownerFingerprint,
                            detailDigest);
                } catch (RuntimeException exception) {
                    Log.e(TAG, "durable restart replay settlement failed taskId="
                            + handle.taskId, exception);
                }
            }
        }
    }

    private DurableTaskRepository.ReconciliationReport awaitStartupReconciliation() {
        Future<DurableTaskRepository.ReconciliationReport> reconciliation = startupReconciliation;
        if (reconciliation == null) {
            throw new IllegalStateException("restart reconciliation is not initialized");
        }
        try {
            return reconciliation.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("restart reconciliation wait was interrupted", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("restart reconciliation failed", exception.getCause());
        }
    }

    private void persistTransition(
            TaskRecord record,
            JobSupervisor.Snapshot before,
            JobSupervisor.State target,
            int progressPercent) {
        long sequence = before.getSequence() + 1;
        String targetState = durableState(target);
        String checkpointDigest = DurableDigest.sha256(
                "central-brain-task-checkpoint-v1",
                record.handle.taskId,
                Long.toString(sequence),
                targetState,
                Integer.toString(progressPercent),
                record.payloadDigest);
        DurableTaskRepository.Transition transition = taskRepository.transition(
                record.handle.taskId,
                record.ownerFingerprint,
                durableState(before.getState()),
                targetState,
                progressPercent,
                sequence,
                checkpointDigest);
        if (transition.getOutcome() != DurableTaskRepository.TransitionOutcome.APPLIED) {
            throw new IllegalStateException(
                    "durable transition was not applied: " + transition.getOutcome());
        }
    }

    private void settleTerminalDelivery(TaskRecord record) {
        JobSupervisor.Snapshot snapshot = jobSupervisor.find(record.handle.taskId);
        if (snapshot == null || !snapshot.isTerminal()) {
            return;
        }
        String detailDigest = DurableDigest.sha256(
                "central-brain-terminal-delivery-v1",
                record.handle.taskId,
                durableState(snapshot.getState()),
                Long.toString(snapshot.getSequence()));
        try {
            DurableTaskRepository.Settlement settlement = taskRepository.settleTerminalDelivery(
                    record.handle.taskId,
                    record.ownerFingerprint,
                    detailDigest);
            if (settlement == DurableTaskRepository.Settlement.NOT_FOUND) {
                throw new IllegalStateException("durable terminal task was not found");
            }
            jobSupervisor.markTerminalDeliverySettled(record.handle.taskId);
        } catch (RuntimeException exception) {
            Log.e(TAG, "terminal settlement failed taskId=" + record.handle.taskId,
                    exception);
        }
    }

    private void failUnscheduledAdmission(
            String taskId,
            String ownerFingerprint,
            String payloadDigest) {
        String checkpointDigest = DurableDigest.sha256(
                "central-brain-unscheduled-task-v1",
                taskId,
                payloadDigest);
        try {
            taskRepository.transition(
                    taskId,
                    ownerFingerprint,
                    DurableTaskRepository.STATE_ACCEPTED,
                    DurableTaskRepository.STATE_FAILED,
                    0,
                    2,
                    checkpointDigest);
            taskRepository.settleTerminalDelivery(
                    taskId,
                    ownerFingerprint,
                    checkpointDigest);
        } catch (RuntimeException exception) {
            Log.e(TAG, "failed to close unscheduled durable taskId=" + taskId, exception);
        }
    }

    private static String requestDigest(AgentTaskRequest request) {
        return DurableDigest.sha256(
                "central-brain-agent-task-v1",
                Integer.toString(request.schemaVersion),
                request.clientRequestId,
                safe(request.sessionId),
                request.utterance,
                safe(request.locale),
                Long.toString(request.deadlineElapsedRealtimeMs),
                Integer.toString(request.priority));
    }

    private static String durableState(JobSupervisor.State state) {
        return state.name();
    }

    private static TaskHandle handleFrom(DurableTaskRepository.Admission admission) {
        TaskHandle handle = new TaskHandle();
        handle.taskId = admission.getTaskId();
        long ageMs = Math.max(0, System.currentTimeMillis() - admission.getAcceptedAtWallMs());
        handle.acceptedAtElapsedRealtimeMs = Math.max(0, SystemClock.elapsedRealtime() - ageMs);
        return handle;
    }

    private static TaskResult completedResult(TaskRecord record) {
        TaskResult result = new TaskResult();
        result.taskId = record.handle.taskId;
        result.completionCode = ICentralBrainRuntime.ERROR_NONE;
        result.replyText = "Deterministic Binder reply: " + safe(record.request.utterance);
        result.summary = "typed Binder task completed without hardware access";
        result.completedAtElapsedRealtimeMs = SystemClock.elapsedRealtime();
        return result;
    }

    private static TaskResult recoveredCompletedResult(String taskId) {
        TaskResult result = new TaskResult();
        result.taskId = taskId;
        result.completionCode = ICentralBrainRuntime.ERROR_NONE;
        result.replyText = "";
        result.summary = "durable completion recovered; raw result payload was not retained";
        result.completedAtElapsedRealtimeMs = SystemClock.elapsedRealtime();
        return result;
    }

    private static TaskFailure terminalFailure(
            TaskRecord record,
            JobSupervisor.State state) {
        TaskFailure failure = new TaskFailure();
        failure.taskId = record.handle.taskId;
        failure.errorCode = state == JobSupervisor.State.CANCELLED
                ? ICentralBrainRuntime.ERROR_CANCELLED
                : ICentralBrainRuntime.ERROR_INTERNAL;
        failure.errorMessage = "task terminal state=" + state;
        failure.retryable = false;
        return failure;
    }

    private static List<ICentralBrainTaskCallback> callbacksFor(TaskRecord record) {
        synchronized (record) {
            List<ICentralBrainTaskCallback> callbacks = new ArrayList<>();
            callbacks.add(record.primaryCallback);
            callbacks.addAll(record.replayCallbacks.values());
            return callbacks;
        }
    }

    private static void clearReplayCallbacks(TaskRecord record) {
        synchronized (record) {
            record.replayCallbacks.clear();
        }
    }

    private void deliverSingleUpdate(
            TaskRecord record,
            ICentralBrainTaskCallback callback,
            TaskUpdate update) {
        try {
            callback.onTaskUpdate(update);
        } catch (RemoteException exception) {
            handleDeliveryFailure(record, callback, "replay update", exception);
        }
    }

    private void handleDeliveryFailure(
            TaskRecord record,
            ICentralBrainTaskCallback callback,
            String operation,
            RemoteException exception) {
        if (record.primaryCallback.asBinder().equals(callback.asBinder())) {
            handleCallbackDeath(record);
            return;
        }
        synchronized (record) {
            record.replayCallbacks.remove(callback.asBinder());
        }
        Log.w(TAG, operation + " observer callback failed taskId=" + record.handle.taskId,
                exception);
    }

    private static TaskUpdate updateFor(JobSupervisor.Snapshot snapshot) {
        return updateFor(
                snapshot.getTaskId(),
                aidlState(snapshot.getState()),
                snapshot.getProgressPercent(),
                snapshot.getSequence(),
                snapshot.getMessage());
    }

    private static TaskUpdate updateFor(DurableTaskRepository.Snapshot snapshot) {
        return updateFor(
                snapshot.getTaskId(),
                aidlState(snapshot.getState()),
                snapshot.getProgressPercent(),
                snapshot.getSequence(),
                "durable state after restart reconciliation");
    }

    private static int aidlState(String state) {
        switch (state) {
            case DurableTaskRepository.STATE_ACCEPTED:
                return ICentralBrainRuntime.TASK_STATE_ACCEPTED;
            case DurableTaskRepository.STATE_RUNNING:
                return ICentralBrainRuntime.TASK_STATE_RUNNING;
            case DurableTaskRepository.STATE_COMPLETED:
                return ICentralBrainRuntime.TASK_STATE_COMPLETED;
            case DurableTaskRepository.STATE_FAILED:
                return ICentralBrainRuntime.TASK_STATE_FAILED;
            case DurableTaskRepository.STATE_CANCELLED:
                return ICentralBrainRuntime.TASK_STATE_CANCELLED;
            default:
                return ICentralBrainRuntime.TASK_STATE_UNKNOWN;
        }
    }

    private static int aidlState(JobSupervisor.State state) {
        switch (state) {
            case ACCEPTED:
                return ICentralBrainRuntime.TASK_STATE_ACCEPTED;
            case RUNNING:
                return ICentralBrainRuntime.TASK_STATE_RUNNING;
            case COMPLETED:
                return ICentralBrainRuntime.TASK_STATE_COMPLETED;
            case FAILED:
                return ICentralBrainRuntime.TASK_STATE_FAILED;
            case CANCELLED:
                return ICentralBrainRuntime.TASK_STATE_CANCELLED;
            default:
                throw new IllegalArgumentException("unsupported supervisor state: " + state);
        }
    }

    private CallerIdentitySnapshot resolveTrustedCaller() {
        CallerIdentitySnapshot caller = identityResolver.resolveCallingIdentity();
        if (!caller.isResolved()) {
            Log.w(TAG, "denied unresolved Binder caller " + caller.auditSummary()
                    + " reason=" + caller.getResolutionFailure());
            throw new SecurityException("trusted Binder caller identity could not be resolved");
        }
        return caller;
    }

    private CallerIdentitySnapshot resolveAuthorizedCaller(Capability capability) {
        CallerIdentitySnapshot caller = resolveTrustedCaller();
        CallerCapabilityPolicy.Decision decision = capabilityPolicy.evaluate(caller, capability);
        if (!decision.isAllowed()) {
            Log.w(TAG, "capability denied capability=" + capability.getId()
                    + " reason=" + decision.getReason()
                    + " matchedPackage=" + decision.getMatchedPackage()
                    + " " + caller.auditSummary()
                    + " hardware_accessed=false");
            throw new SecurityException("Central Brain capability denied: " + capability.getId());
        }
        return caller;
    }

    private void removeTaskRecords(Iterable<String> taskIds) {
        for (String taskId : taskIds) {
            TaskRecord removed = tasks.remove(taskId);
            if (removed != null) {
                clearReplayCallbacks(removed);
                unlinkCallbackDeath(removed);
            }
        }
    }

    private static TaskUpdate updateFor(
            String taskId,
            int state,
            int progressPercent,
            long sequence,
            String message) {
        TaskUpdate update = new TaskUpdate();
        update.taskId = safe(taskId);
        update.state = state;
        update.progressPercent = progressPercent;
        update.sequence = sequence;
        update.message = safe(message);
        return update;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class TaskRecord {
        final AgentTaskRequest request;
        final ICentralBrainTaskCallback primaryCallback;
        final TaskHandle handle;
        final String ownerFingerprint;
        final String payloadDigest;
        final Map<IBinder, ICentralBrainTaskCallback> replayCallbacks = new LinkedHashMap<>();
        IBinder.DeathRecipient deathRecipient;

        TaskRecord(
                String taskId,
                AgentTaskRequest request,
                ICentralBrainTaskCallback callback,
                long acceptedAtElapsedRealtimeMs,
                String ownerFingerprint,
                String payloadDigest) {
            this.request = request;
            this.primaryCallback = callback;
            this.ownerFingerprint = ownerFingerprint;
            this.payloadDigest = payloadDigest;
            this.handle = new TaskHandle();
            this.handle.taskId = taskId;
            this.handle.acceptedAtElapsedRealtimeMs = acceptedAtElapsedRealtimeMs;
        }
    }
}
