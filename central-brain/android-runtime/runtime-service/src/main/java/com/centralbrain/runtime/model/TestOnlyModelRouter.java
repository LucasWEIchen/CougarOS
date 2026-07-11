package com.centralbrain.runtime.model;

import com.centralbrain.runtime.scheduler.InferenceResourceScheduler;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Test-only Scheduler-to-Provider coordinator. There is no production factory. */
public final class TestOnlyModelRouter {
    public static final String TEST_ROUTE_ID = "test.deterministic.stub";
    private static final String EMPTY_DIGEST = sha256(new byte[0]);

    public enum FallbackPolicy {
        NO_FALLBACK
    }

    public enum SubmitOutcome {
        ADMITTED,
        REPLAYED,
        REJECTED,
        PROVIDER_UNAVAILABLE
    }

    private final InferenceResourceScheduler scheduler;
    private final ModelProvider provider;
    private final FallbackPolicy fallbackPolicy;
    private final Map<String, RouteRecord> records = new LinkedHashMap<>();

    private long submittedCount;
    private long dispatchedCount;
    private long completedCount;
    private long cancelledCount;
    private long failedCount;
    private long deadlineExceededCount;
    private long providerCancelCount;
    private long providerIdentityValidationCount;
    private long duplicateTerminalIgnoredCount;
    private long fallbackAttemptCount;

    private TestOnlyModelRouter(
            InferenceResourceScheduler scheduler,
            ModelProvider provider,
            FallbackPolicy fallbackPolicy) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.fallbackPolicy = Objects.requireNonNull(fallbackPolicy, "fallbackPolicy");
        validateTestProvider(provider);
        if (fallbackPolicy != FallbackPolicy.NO_FALLBACK) {
            throw new IllegalArgumentException("R5B2 permits only explicit no-fallback policy");
        }
    }

    public static TestOnlyModelRouter createForContractTest(
            InferenceResourceScheduler scheduler,
            ModelProvider provider) {
        return new TestOnlyModelRouter(
                scheduler,
                provider,
                FallbackPolicy.NO_FALLBACK);
    }

    public static InferenceResourceScheduler.RouteTarget routeTargetForContractTest(
            ModelProvider provider) {
        validateTestProvider(provider);
        ModelProvider.Descriptor descriptor = provider.descriptor();
        return InferenceResourceScheduler.RouteTarget.forContractTest(
                TEST_ROUTE_ID,
                descriptor.getMaxConcurrentRequests(),
                descriptor.isSupportsCancellation());
    }

    public synchronized SubmitResult submit(
            TrustedRouteRequest request,
            ModelProvider.StreamObserver observer) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(observer, "observer");
        ModelProvider.Snapshot providerSnapshot = provider.snapshot();
        if (providerSnapshot.getLifecycleState() != ModelProvider.LifecycleState.READY
                || providerSnapshot.getHealthState() != ModelProvider.HealthState.HEALTHY) {
            return new SubmitResult(
                    SubmitOutcome.PROVIDER_UNAVAILABLE,
                    null,
                    false);
        }

        InferenceResourceScheduler.Admission admission = scheduler.admit(
                InferenceResourceScheduler.TrustedSubmission.fromRuntimePolicy(
                        request.getRequestId(),
                        request.getOwnerFingerprint(),
                        request.getModelId(),
                        TEST_ROUTE_ID,
                        request.getEffectivePriority(),
                        request.getTaskDeadlineElapsedRealtimeMs(),
                        request.getMaxQueueWaitMs()));
        processExpiryLocked(admission.getExpiryReport());
        if (admission.getOutcome()
                == InferenceResourceScheduler.AdmissionOutcome.ADMITTED) {
            RouteRecord record = new RouteRecord(request, observer);
            records.put(request.getRequestId(), record);
            submittedCount++;
            int dispatched = pumpLocked();
            return new SubmitResult(
                    SubmitOutcome.ADMITTED,
                    admission.getActive(),
                    dispatched > 0);
        }
        if (admission.getOutcome()
                == InferenceResourceScheduler.AdmissionOutcome.REPLAYED) {
            RouteRecord record = records.get(request.getRequestId());
            if (record == null) {
                throw new IllegalStateException("scheduler replay has no matching route record");
            }
            if (!record.matches(request)) {
                return new SubmitResult(SubmitOutcome.REJECTED, null, false);
            }
            return new SubmitResult(
                    SubmitOutcome.REPLAYED,
                    admission.getActive(),
                    record.leaseId != null);
        }
        return new SubmitResult(SubmitOutcome.REJECTED, null, false);
    }

    public synchronized CancelResult cancelOwned(
            String requestId,
            String ownerFingerprint,
            String reason) {
        requireReason(reason);
        InferenceResourceScheduler.CancelResult schedulerResult = scheduler.cancelOwned(
                requestId,
                ownerFingerprint);
        if (schedulerResult.getOutcome()
                == InferenceResourceScheduler.CancelOutcome.CANCELLED_QUEUED) {
            RouteRecord record = records.remove(requestId);
            if (record != null) {
                cancelledCount++;
                deliverTerminal(record.observer, terminal(
                        requestId,
                        ModelProvider.TerminalState.CANCELLED,
                        "ROUTER_QUEUE_CANCELLED"));
            }
            pumpLocked();
            return new CancelResult(schedulerResult.getOutcome(), null);
        }
        if (schedulerResult.getOutcome()
                == InferenceResourceScheduler.CancelOutcome.PROVIDER_CANCEL_REQUIRED) {
            ModelProvider.CancelState providerState = issueProviderCancelLocked(
                    schedulerResult.getDirective(),
                    reason);
            return new CancelResult(schedulerResult.getOutcome(), providerState);
        }
        return new CancelResult(schedulerResult.getOutcome(), null);
    }

    public synchronized TickResult tick() {
        InferenceResourceScheduler.ExpiryReport expiry = scheduler.sweepDeadlines();
        int beforeCancelled = (int) cancelledCount;
        int beforeDeadline = (int) deadlineExceededCount;
        int beforeProviderCancel = (int) providerCancelCount;
        processExpiryLocked(expiry);
        int dispatched = pumpLocked();
        return new TickResult(
                (int) deadlineExceededCount - beforeDeadline,
                (int) cancelledCount - beforeCancelled,
                (int) providerCancelCount - beforeProviderCancel,
                dispatched,
                expiry.getCancellationUnsupportedRequestIds().size());
    }

    public synchronized int pump() {
        return pumpLocked();
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(
                records.size(),
                submittedCount,
                dispatchedCount,
                completedCount,
                cancelledCount,
                failedCount,
                deadlineExceededCount,
                providerCancelCount,
                providerIdentityValidationCount,
                duplicateTerminalIgnoredCount,
                fallbackAttemptCount,
                fallbackPolicy,
                false,
                false);
    }

    private int pumpLocked() {
        int dispatched = 0;
        while (true) {
            InferenceResourceScheduler.Claim claim = scheduler.claimNext();
            processExpiryLocked(claim.getExpiryReport());
            InferenceResourceScheduler.Lease lease = claim.getLease();
            if (lease == null) {
                return dispatched;
            }
            String requestId = lease.getActive().getRequestId();
            RouteRecord record = records.get(requestId);
            if (record == null) {
                scheduler.settle(
                        requestId,
                        lease.getLeaseId(),
                        InferenceResourceScheduler.ProviderTerminalOutcome.FAILED);
                failedCount++;
                continue;
            }
            record.leaseId = lease.getLeaseId();
            record.dispatched = true;
            try {
                ModelProvider.InferenceHandle handle = provider.infer(
                        new ModelProvider.InferenceRequest(
                                requestId,
                                record.request.getModelId(),
                                record.request.getInputDigest(),
                                record.request.getTaskDeadlineElapsedRealtimeMs(),
                                record.request.isStreamingRequested()),
                        new ProviderObserver(requestId, lease.getLeaseId()));
                if (!provider.descriptor().getProviderId().equals(handle.getProviderId())
                        || !requestId.equals(handle.getRequestId())) {
                    failDispatchLocked(
                            record,
                            lease.getLeaseId(),
                            "PROVIDER_IDENTITY_MISMATCH");
                    continue;
                }
                providerIdentityValidationCount++;
                dispatchedCount++;
                dispatched++;
            } catch (RuntimeException exception) {
                failDispatchLocked(
                        record,
                        lease.getLeaseId(),
                        "PROVIDER_SUBMIT_FAILED");
            }
        }
    }

    private void processExpiryLocked(InferenceResourceScheduler.ExpiryReport report) {
        for (String requestId : report.getQueuedExpiredRequestIds()) {
            RouteRecord record = records.remove(requestId);
            if (record != null && !record.terminalDelivered) {
                record.terminalDelivered = true;
                deadlineExceededCount++;
                deliverTerminal(record.observer, terminal(
                        requestId,
                        ModelProvider.TerminalState.DEADLINE_EXCEEDED,
                        "ROUTER_QUEUE_DEADLINE_EXCEEDED"));
            }
        }
        for (InferenceResourceScheduler.CancellationDirective directive
                : report.getCancellationDirectives()) {
            issueProviderCancelLocked(directive, "router deadline exceeded");
        }
        for (String requestId : report.getCancellationUnsupportedRequestIds()) {
            RouteRecord record = records.get(requestId);
            if (record != null && record.leaseId != null) {
                failDispatchLocked(
                        record,
                        record.leaseId,
                        "PROVIDER_CANCEL_UNSUPPORTED");
            }
        }
    }

    private ModelProvider.CancelState issueProviderCancelLocked(
            InferenceResourceScheduler.CancellationDirective directive,
            String reason) {
        RouteRecord record = records.get(directive.getRequestId());
        if (record == null || record.terminalDelivered) {
            return ModelProvider.CancelState.ALREADY_TERMINAL;
        }
        if (!directive.getLeaseId().equals(record.leaseId)) {
            failDispatchLocked(record, record.leaseId, "CANCEL_LEASE_MISMATCH");
            return ModelProvider.CancelState.NOT_FOUND;
        }
        if (record.providerCancelIssued) {
            return ModelProvider.CancelState.PENDING_PROVIDER_ACK;
        }
        record.providerCancelIssued = true;
        ModelProvider.CancelState state;
        try {
            state = provider.cancel(directive.getRequestId(), reason);
        } catch (RuntimeException exception) {
            failDispatchLocked(record, record.leaseId, "PROVIDER_CANCEL_FAILED");
            return ModelProvider.CancelState.NOT_FOUND;
        }
        providerCancelCount++;
        if (state == ModelProvider.CancelState.NOT_FOUND
                || state == ModelProvider.CancelState.UNSUPPORTED) {
            failDispatchLocked(record, record.leaseId, "PROVIDER_CANCEL_CONTRACT_VIOLATION");
        }
        return state;
    }

    private void handleChunkLocked(
            String requestId,
            String leaseId,
            ModelProvider.StreamChunk chunk) {
        RouteRecord record = records.get(requestId);
        if (record == null
                || record.terminalDelivered
                || !leaseId.equals(record.leaseId)
                || !requestId.equals(chunk.getRequestId())) {
            return;
        }
        try {
            record.observer.onChunk(chunk);
        } catch (RuntimeException ignored) {
            // Provider settlement is independent from an app observer failure.
        }
    }

    private void handleTerminalLocked(
            String requestId,
            String leaseId,
            ModelProvider.TerminalResult result) {
        RouteRecord record = records.get(requestId);
        if (record == null || record.terminalDelivered) {
            duplicateTerminalIgnoredCount++;
            return;
        }
        if (!leaseId.equals(record.leaseId)
                || !requestId.equals(result.getRequestId())) {
            failDispatchLocked(record, leaseId, "TERMINAL_IDENTITY_MISMATCH");
            return;
        }
        InferenceResourceScheduler.ProviderTerminalOutcome providerOutcome =
                providerOutcome(result.getState());
        InferenceResourceScheduler.Settlement settlement = scheduler.settle(
                requestId,
                leaseId,
                providerOutcome);
        if (settlement.getOutcome()
                != InferenceResourceScheduler.SettlementOutcome.APPLIED) {
            failRouteRecordLocked(record, "SCHEDULER_SETTLEMENT_FAILED");
            return;
        }
        record.terminalDelivered = true;
        records.remove(requestId);
        ModelProvider.TerminalResult normalized = normalizeTerminal(result, settlement);
        incrementTerminalMetric(normalized.getState());
        deliverTerminal(record.observer, normalized);
        pumpLocked();
    }

    private void failDispatchLocked(
            RouteRecord record,
            String leaseId,
            String detailCode) {
        if (record == null || record.terminalDelivered) {
            return;
        }
        scheduler.settle(
                record.request.getRequestId(),
                leaseId,
                InferenceResourceScheduler.ProviderTerminalOutcome.FAILED);
        failRouteRecordLocked(record, detailCode);
    }

    private void failRouteRecordLocked(RouteRecord record, String detailCode) {
        record.terminalDelivered = true;
        records.remove(record.request.getRequestId());
        failedCount++;
        deliverTerminal(record.observer, terminal(
                record.request.getRequestId(),
                ModelProvider.TerminalState.TERMINAL_FAILURE,
                detailCode));
    }

    private void incrementTerminalMetric(ModelProvider.TerminalState state) {
        if (state == ModelProvider.TerminalState.COMPLETED) {
            completedCount++;
        } else if (state == ModelProvider.TerminalState.CANCELLED) {
            cancelledCount++;
        } else if (state == ModelProvider.TerminalState.DEADLINE_EXCEEDED) {
            deadlineExceededCount++;
        } else {
            failedCount++;
        }
    }

    private static ModelProvider.TerminalResult normalizeTerminal(
            ModelProvider.TerminalResult providerResult,
            InferenceResourceScheduler.Settlement settlement) {
        if (settlement.getLocalTerminalState()
                == InferenceResourceScheduler.LocalTerminalState.CANCELLED) {
            return terminal(
                    providerResult.getRequestId(),
                    ModelProvider.TerminalState.CANCELLED,
                    "ROUTER_CANCELLED");
        }
        if (settlement.getLocalTerminalState()
                == InferenceResourceScheduler.LocalTerminalState.DEADLINE_EXCEEDED) {
            return terminal(
                    providerResult.getRequestId(),
                    ModelProvider.TerminalState.DEADLINE_EXCEEDED,
                    "ROUTER_DEADLINE_EXCEEDED");
        }
        return providerResult;
    }

    private static InferenceResourceScheduler.ProviderTerminalOutcome providerOutcome(
            ModelProvider.TerminalState state) {
        if (state == ModelProvider.TerminalState.COMPLETED) {
            return InferenceResourceScheduler.ProviderTerminalOutcome.COMPLETED;
        }
        if (state == ModelProvider.TerminalState.CANCELLED) {
            return InferenceResourceScheduler.ProviderTerminalOutcome.CANCELLED;
        }
        return InferenceResourceScheduler.ProviderTerminalOutcome.FAILED;
    }

    private static ModelProvider.TerminalResult terminal(
            String requestId,
            ModelProvider.TerminalState state,
            String detailCode) {
        return new ModelProvider.TerminalResult(
                requestId,
                state,
                EMPTY_DIGEST,
                detailCode);
    }

    private static void deliverTerminal(
            ModelProvider.StreamObserver observer,
            ModelProvider.TerminalResult result) {
        try {
            observer.onTerminal(result);
        } catch (RuntimeException ignored) {
            // Router state is terminal even when an app observer fails.
        }
    }

    private static void validateTestProvider(ModelProvider provider) {
        Objects.requireNonNull(provider, "provider");
        ModelProvider.Descriptor descriptor = provider.descriptor();
        if (!ModelProviderProfiles.DETERMINISTIC_STUB_ID.equals(
                        descriptor.getProviderId())
                || descriptor.getAssurance() != ModelProvider.Assurance.TEST_ONLY
                || descriptor.isHardwareBacked()
                || descriptor.isProductionEligible()
                || descriptor.getFallbackClass()
                        != ModelProvider.FallbackClass.TEST_ONLY) {
            throw new IllegalArgumentException(
                    "Router accepts only the deterministic TEST_ONLY provider");
        }
    }

    private static String sha256(byte[] value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value);
            char[] output = new char[bytes.length * 2];
            char[] digits = "0123456789abcdef".toCharArray();
            for (int index = 0; index < bytes.length; index++) {
                int current = bytes[index] & 0xff;
                output[index * 2] = digits[current >>> 4];
                output[index * 2 + 1] = digits[current & 0x0f];
            }
            return new String(output);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.trim().isEmpty() || reason.length() > 256) {
            throw new IllegalArgumentException("cancel reason is invalid");
        }
    }

    private final class ProviderObserver implements ModelProvider.StreamObserver {
        private final String requestId;
        private final String leaseId;

        ProviderObserver(String requestId, String leaseId) {
            this.requestId = requestId;
            this.leaseId = leaseId;
        }

        @Override
        public void onChunk(ModelProvider.StreamChunk chunk) {
            synchronized (TestOnlyModelRouter.this) {
                handleChunkLocked(requestId, leaseId, chunk);
            }
        }

        @Override
        public void onTerminal(ModelProvider.TerminalResult result) {
            synchronized (TestOnlyModelRouter.this) {
                handleTerminalLocked(requestId, leaseId, result);
            }
        }
    }

    private static final class RouteRecord {
        final TrustedRouteRequest request;
        final ModelProvider.StreamObserver observer;
        String leaseId;
        boolean dispatched;
        boolean providerCancelIssued;
        boolean terminalDelivered;

        RouteRecord(
                TrustedRouteRequest request,
                ModelProvider.StreamObserver observer) {
            this.request = request;
            this.observer = observer;
        }

        boolean matches(TrustedRouteRequest other) {
            return request.matches(other);
        }
    }

    public static final class TrustedRouteRequest {
        private final String requestId;
        private final String ownerFingerprint;
        private final String modelId;
        private final String inputDigest;
        private final InferenceResourceScheduler.EffectivePriority effectivePriority;
        private final long taskDeadlineElapsedRealtimeMs;
        private final long maxQueueWaitMs;
        private final boolean streamingRequested;

        private TrustedRouteRequest(
                String requestId,
                String ownerFingerprint,
                String modelId,
                String inputDigest,
                InferenceResourceScheduler.EffectivePriority effectivePriority,
                long taskDeadlineElapsedRealtimeMs,
                long maxQueueWaitMs,
                boolean streamingRequested) {
            ModelProvider.InferenceRequest validation = new ModelProvider.InferenceRequest(
                    requestId,
                    modelId,
                    inputDigest,
                    taskDeadlineElapsedRealtimeMs,
                    streamingRequested);
            InferenceResourceScheduler.TrustedSubmission.fromRuntimePolicy(
                    requestId,
                    ownerFingerprint,
                    modelId,
                    TEST_ROUTE_ID,
                    effectivePriority,
                    taskDeadlineElapsedRealtimeMs,
                    maxQueueWaitMs);
            this.requestId = validation.getRequestId();
            this.ownerFingerprint = ownerFingerprint;
            this.modelId = validation.getModelId();
            this.inputDigest = validation.getInputDigest();
            this.effectivePriority = effectivePriority;
            this.taskDeadlineElapsedRealtimeMs = taskDeadlineElapsedRealtimeMs;
            this.maxQueueWaitMs = maxQueueWaitMs;
            this.streamingRequested = streamingRequested;
        }

        public static TrustedRouteRequest fromRuntimePolicy(
                String requestId,
                String ownerFingerprint,
                String modelId,
                String inputDigest,
                InferenceResourceScheduler.EffectivePriority effectivePriority,
                long taskDeadlineElapsedRealtimeMs,
                long maxQueueWaitMs,
                boolean streamingRequested) {
            return new TrustedRouteRequest(
                    requestId,
                    ownerFingerprint,
                    modelId,
                    inputDigest,
                    effectivePriority,
                    taskDeadlineElapsedRealtimeMs,
                    maxQueueWaitMs,
                    streamingRequested);
        }

        public String getRequestId() {
            return requestId;
        }

        public String getOwnerFingerprint() {
            return ownerFingerprint;
        }

        public String getModelId() {
            return modelId;
        }

        public String getInputDigest() {
            return inputDigest;
        }

        public InferenceResourceScheduler.EffectivePriority getEffectivePriority() {
            return effectivePriority;
        }

        public long getTaskDeadlineElapsedRealtimeMs() {
            return taskDeadlineElapsedRealtimeMs;
        }

        public long getMaxQueueWaitMs() {
            return maxQueueWaitMs;
        }

        public boolean isStreamingRequested() {
            return streamingRequested;
        }

        boolean matches(TrustedRouteRequest other) {
            return requestId.equals(other.requestId)
                    && ownerFingerprint.equals(other.ownerFingerprint)
                    && modelId.equals(other.modelId)
                    && inputDigest.equals(other.inputDigest)
                    && effectivePriority == other.effectivePriority
                    && taskDeadlineElapsedRealtimeMs
                            == other.taskDeadlineElapsedRealtimeMs
                    && maxQueueWaitMs == other.maxQueueWaitMs
                    && streamingRequested == other.streamingRequested;
        }
    }

    public static final class SubmitResult {
        private final SubmitOutcome outcome;
        private final InferenceResourceScheduler.ActiveSnapshot admission;
        private final boolean dispatched;

        SubmitResult(
                SubmitOutcome outcome,
                InferenceResourceScheduler.ActiveSnapshot admission,
                boolean dispatched) {
            this.outcome = outcome;
            this.admission = admission;
            this.dispatched = dispatched;
        }

        public SubmitOutcome getOutcome() {
            return outcome;
        }

        public InferenceResourceScheduler.ActiveSnapshot getAdmission() {
            return admission;
        }

        public boolean isDispatched() {
            return dispatched;
        }
    }

    public static final class CancelResult {
        private final InferenceResourceScheduler.CancelOutcome schedulerOutcome;
        private final ModelProvider.CancelState providerState;

        CancelResult(
                InferenceResourceScheduler.CancelOutcome schedulerOutcome,
                ModelProvider.CancelState providerState) {
            this.schedulerOutcome = schedulerOutcome;
            this.providerState = providerState;
        }

        public InferenceResourceScheduler.CancelOutcome getSchedulerOutcome() {
            return schedulerOutcome;
        }

        public ModelProvider.CancelState getProviderState() {
            return providerState;
        }
    }

    public static final class TickResult {
        private final int deadlineTerminals;
        private final int cancelledTerminals;
        private final int providerCancels;
        private final int dispatched;
        private final int cancellationUnsupported;

        TickResult(
                int deadlineTerminals,
                int cancelledTerminals,
                int providerCancels,
                int dispatched,
                int cancellationUnsupported) {
            this.deadlineTerminals = deadlineTerminals;
            this.cancelledTerminals = cancelledTerminals;
            this.providerCancels = providerCancels;
            this.dispatched = dispatched;
            this.cancellationUnsupported = cancellationUnsupported;
        }

        public int getDeadlineTerminals() {
            return deadlineTerminals;
        }

        public int getCancelledTerminals() {
            return cancelledTerminals;
        }

        public int getProviderCancels() {
            return providerCancels;
        }

        public int getDispatched() {
            return dispatched;
        }

        public int getCancellationUnsupported() {
            return cancellationUnsupported;
        }
    }

    public static final class Snapshot {
        private final int activeRouteCount;
        private final long submittedCount;
        private final long dispatchedCount;
        private final long completedCount;
        private final long cancelledCount;
        private final long failedCount;
        private final long deadlineExceededCount;
        private final long providerCancelCount;
        private final long providerIdentityValidationCount;
        private final long duplicateTerminalIgnoredCount;
        private final long fallbackAttemptCount;
        private final FallbackPolicy fallbackPolicy;
        private final boolean productionWired;
        private final boolean hardwareAccessed;

        Snapshot(
                int activeRouteCount,
                long submittedCount,
                long dispatchedCount,
                long completedCount,
                long cancelledCount,
                long failedCount,
                long deadlineExceededCount,
                long providerCancelCount,
                long providerIdentityValidationCount,
                long duplicateTerminalIgnoredCount,
                long fallbackAttemptCount,
                FallbackPolicy fallbackPolicy,
                boolean productionWired,
                boolean hardwareAccessed) {
            this.activeRouteCount = activeRouteCount;
            this.submittedCount = submittedCount;
            this.dispatchedCount = dispatchedCount;
            this.completedCount = completedCount;
            this.cancelledCount = cancelledCount;
            this.failedCount = failedCount;
            this.deadlineExceededCount = deadlineExceededCount;
            this.providerCancelCount = providerCancelCount;
            this.providerIdentityValidationCount = providerIdentityValidationCount;
            this.duplicateTerminalIgnoredCount = duplicateTerminalIgnoredCount;
            this.fallbackAttemptCount = fallbackAttemptCount;
            this.fallbackPolicy = fallbackPolicy;
            this.productionWired = productionWired;
            this.hardwareAccessed = hardwareAccessed;
        }

        public int getActiveRouteCount() {
            return activeRouteCount;
        }

        public long getSubmittedCount() {
            return submittedCount;
        }

        public long getDispatchedCount() {
            return dispatchedCount;
        }

        public long getCompletedCount() {
            return completedCount;
        }

        public long getCancelledCount() {
            return cancelledCount;
        }

        public long getFailedCount() {
            return failedCount;
        }

        public long getDeadlineExceededCount() {
            return deadlineExceededCount;
        }

        public long getProviderCancelCount() {
            return providerCancelCount;
        }

        public long getProviderIdentityValidationCount() {
            return providerIdentityValidationCount;
        }

        public long getDuplicateTerminalIgnoredCount() {
            return duplicateTerminalIgnoredCount;
        }

        public long getFallbackAttemptCount() {
            return fallbackAttemptCount;
        }

        public FallbackPolicy getFallbackPolicy() {
            return fallbackPolicy;
        }

        public boolean isProductionWired() {
            return productionWired;
        }

        public boolean isHardwareAccessed() {
            return hardwareAccessed;
        }
    }
}
