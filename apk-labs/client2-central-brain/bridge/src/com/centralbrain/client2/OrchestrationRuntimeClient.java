package com.centralbrain.client2;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

import com.centralbrain.sdk.DevelopmentModelProjectionClient;
import com.centralbrain.sdk.OrchestrationClient;
import com.centralbrain.sdk.effect.EffectContract;
import com.centralbrain.sdk.model.DevelopmentModelProjection;
import com.centralbrain.sdk.orchestration.ApprovalResponse;
import com.centralbrain.sdk.orchestration.ICentralBrainOrchestration;
import com.centralbrain.sdk.orchestration.OrchestrationContract;
import com.centralbrain.sdk.orchestration.OrchestrationEffect;
import com.centralbrain.sdk.orchestration.OrchestrationNode;
import com.centralbrain.sdk.orchestration.OrchestrationSnapshot;
import com.centralbrain.sdk.orchestration.OrchestrationStartRequest;
import com.centralbrain.sdk.plan.ScenarioPlan;
import com.centralbrain.sdk.session.ICentralBrainSessionRuntime;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Client2 projection of the formal Orchestration V1 SDK surface. */
public final class OrchestrationRuntimeClient implements
        AutoCloseable,
        OrchestrationClient.ConnectionListener {
    private static final String TAG = "CbClient2Orchestration";
    private static final String NOT_STARTED = "ORCHESTRATION_NOT_STARTED";

    public interface Callback {
        void onSimulatedRuntimeAvailability(boolean available, String failureCode);

        void onSimulatedScenarioSnapshot(
                CockpitSimulatedScenarioState.Projection projection);

        void onSimulatedScenarioFailure(String uiScenarioId, String failureCode);

        void onPipelineMilestone(String stage, String status, String detail);
    }

    private static final class PendingStart {
        private final long generation;
        private final String sessionId;
        private final String uiScenarioId;
        private final String canonicalScenarioId;
        private final String drivingProfile;
        private final int motionState;
        private final boolean effectAnimationOnly;

        private PendingStart(
                long generation,
                String sessionId,
                String uiScenarioId,
                String canonicalScenarioId,
                String drivingProfile,
                int motionState,
                boolean effectAnimationOnly) {
            this.generation = generation;
            this.sessionId = sessionId;
            this.uiScenarioId = uiScenarioId;
            this.canonicalScenarioId = canonicalScenarioId;
            this.drivingProfile = drivingProfile;
            this.motionState = motionState;
            this.effectAnimationOnly = effectAnimationOnly;
        }
    }

    private final Callback callback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService binderExecutor = Executors.newSingleThreadExecutor();
    private final OrchestrationClient client;
    private final DevelopmentModelProjectionClient modelProjectionClient;

    private PendingStart pendingStart;
    private OrchestrationSnapshot latestSnapshot;
    private String latestUiScenarioId = "";
    private String latestDrivingProfile = "";
    private int approvalInputCount;
    private long generation;
    private boolean connected;
    private boolean closed;

    public OrchestrationRuntimeClient(Context context, Callback callback) {
        Context appContext = Objects.requireNonNull(context, "context")
                .getApplicationContext();
        this.callback = Objects.requireNonNull(callback, "callback");
        client = new OrchestrationClient(
                appContext,
                command -> mainHandler.post(command),
                this);
        modelProjectionClient = new DevelopmentModelProjectionClient(
                appContext,
                command -> mainHandler.post(command),
                new DevelopmentModelProjectionClient.ConnectionListener() {
                    @Override
                    public void onConnected(
                            DevelopmentModelProjectionClient connectedClient,
                            boolean reconnected) {
                        Log.i(TAG, "client2_development_model_projection_connected=true"
                                + " reconnected=" + reconnected
                                + " debug_only=true");
                    }

                    @Override
                    public void onDisconnected() {
                        Log.w(TAG, "client2_development_model_projection_connected=false"
                                + " reason=DISCONNECTED");
                    }

                    @Override
                    public void onConnectionFailed(String code) {
                        Log.w(TAG, "client2_development_model_projection_connected=false"
                                + " reason=" + code);
                    }
                });
    }

    public void connect() {
        synchronized (this) {
            if (closed || connected) {
                return;
            }
        }
        milestone("RUNTIME", "CONNECTING", "Binder service discovery");
        post(() -> callback.onSimulatedRuntimeAvailability(false, "CONNECTING"));
        try {
            modelProjectionClient.connect();
            if (!client.connect()) {
                failAvailability("CB_ORCHESTRATION_BIND_REJECTED");
            }
        } catch (RuntimeException failure) {
            failAvailability("CB_ORCHESTRATION_BIND_RUNTIME");
        }
    }

    public void openOrResume(
            String sessionId,
            String uiScenarioId,
            CockpitSeatState.DrivingState drivingState,
            boolean effectAnimationOnly) {
        Objects.requireNonNull(drivingState, "drivingState");
        String scenario = requireScenario(uiScenarioId);
        PendingStart start;
        boolean ready;
        synchronized (this) {
            if (closed) {
                return;
            }
            generation++;
            start = new PendingStart(
                    generation,
                    requireUuid(sessionId),
                    scenario,
                    CockpitScenarioControlState.canonicalScenarioId(scenario),
                    effectAnimationOnly ? "PARKED" : drivingProfile(drivingState),
                    effectAnimationOnly
                            ? ICentralBrainOrchestration.MOTION_PARKED
                            : motionState(drivingState),
                    effectAnimationOnly);
            pendingStart = start;
            ready = connected;
        }
        milestone("INTENT", "ACCEPTED", start.canonicalScenarioId);
        milestone(
                "CONTEXT",
                "BOUND",
                effectAnimationOnly
                        ? "AUTOMOTIVE_COCKPIT · DRIVER · SIMULATED_PARKED"
                        : "AUTOMOTIVE_COCKPIT · DRIVER · " + start.drivingProfile);
        if (ready) {
            binderExecutor.execute(this::drainPendingStart);
        }
    }

    public void approvePending() {
        respondToApproval(ICentralBrainOrchestration.DECISION_APPROVE);
    }

    public void rejectPending() {
        respondToApproval(ICentralBrainOrchestration.DECISION_REJECT);
    }

    @Override
    public void onConnected(OrchestrationClient connectedClient, boolean reconnected) {
        synchronized (this) {
            if (closed) {
                return;
            }
            connected = true;
        }
        Log.i(TAG, "client2_orchestration_sdk_connected=true"
                + " reconnected=" + reconnected
                + " legacy_simulated_scenario_binder_used=false"
                + " hardware_accessed=false");
        milestone("RUNTIME", "CONNECTED", "Orchestration SDK V1");
        callback.onSimulatedRuntimeAvailability(true, "");
        binderExecutor.execute(this::drainPendingStart);
    }

    @Override
    public void onDisconnected() {
        synchronized (this) {
            if (closed) {
                return;
            }
            connected = false;
        }
        callback.onSimulatedRuntimeAvailability(false, "CB_ORCHESTRATION_DISCONNECTED");
        try {
            client.reconnect();
        } catch (RuntimeException failure) {
            failAvailability("CB_ORCHESTRATION_RECONNECT_FAILED");
        }
    }

    @Override
    public void onConnectionFailed(String code, String message) {
        failAvailability("CB_ORCHESTRATION_CONNECTION_FAILED");
    }

    private void drainPendingStart() {
        PendingStart start;
        OrchestrationSnapshot previous;
        String stage = "GET_SNAPSHOT";
        synchronized (this) {
            if (closed || !connected || pendingStart == null) {
                return;
            }
            start = pendingStart;
            previous = latestSnapshot;
        }
        try {
            cancelPreviousIfNeeded(previous, start.sessionId);
            OrchestrationSnapshot snapshot = client.getSnapshot(start.sessionId);
            if (isNotStarted(snapshot)) {
                stage = "START";
                milestone("MODEL", "RUNNING", "Provider request with cockpit context");
                OrchestrationStartRequest request = new OrchestrationStartRequest();
                request.schemaVersion = OrchestrationContract.SCHEMA_VERSION;
                request.requestId = UUID.randomUUID().toString();
                request.sessionId = start.sessionId;
                request.scenarioId = start.canonicalScenarioId;
                request.executionProfile =
                        ICentralBrainOrchestration.PROFILE_DEBUG_SIMULATION;
                request.simulationMotionState = start.motionState;
                request.requestedAtEpochMs = System.currentTimeMillis();
                snapshot = client.start(request);
            }
            stage = "PUBLISH_PLAN";
            publish(start, snapshot, 0);
        } catch (RemoteException failure) {
            failStart(start, "CB_ORCHESTRATION_START_REMOTE");
        } catch (IllegalArgumentException failure) {
            failStart(
                    start,
                    "CB_ORCHESTRATION_START_CONTRACT",
                    stage,
                    contractReason(failure));
        } catch (RuntimeException failure) {
            failStart(
                    start,
                    "CB_ORCHESTRATION_START_RUNTIME",
                    stage,
                    runtimeReason(failure));
        }
    }

    private void respondToApproval(int decision) {
        OrchestrationSnapshot snapshot;
        String uiScenarioId;
        String drivingProfile;
        long currentGeneration;
        synchronized (this) {
            snapshot = latestSnapshot;
            uiScenarioId = latestUiScenarioId;
            drivingProfile = latestDrivingProfile;
            currentGeneration = generation;
            if (closed || !connected || snapshot == null
                    || snapshot.state
                            != ICentralBrainOrchestration.STATE_WAITING_APPROVAL
                    || snapshot.pendingStage
                            != ICentralBrainOrchestration.PENDING_APPROVAL
                    || !snapshot.approvalResponseAvailable) {
                fail(uiScenarioId, "CB_ORCHESTRATION_APPROVAL_UNAVAILABLE");
                return;
            }
        }
        binderExecutor.execute(() -> {
            try {
                ApprovalResponse response = new ApprovalResponse();
                response.schemaVersion = OrchestrationContract.SCHEMA_VERSION;
                response.requestId = UUID.randomUUID().toString();
                response.sessionId = snapshot.sessionId;
                response.approvalId = snapshot.approvalId;
                response.expectedProjectionDigest = snapshot.projectionDigest;
                response.decision = decision;
                response.respondedAtEpochMs = System.currentTimeMillis();
                OrchestrationSnapshot updated = client.respondToApproval(response);
                PendingStart source = new PendingStart(
                        currentGeneration,
                        updated.sessionId,
                        uiScenarioId,
                        updated.scenarioId,
                        drivingProfile,
                        ICentralBrainOrchestration.MOTION_UNKNOWN,
                        true);
                publish(source, updated, 1);
            } catch (RemoteException failure) {
                fail(uiScenarioId, "CB_ORCHESTRATION_APPROVAL_REMOTE");
            } catch (IllegalArgumentException failure) {
                logFailureDiagnosis("APPROVAL", contractReason(failure));
                fail(uiScenarioId, "CB_ORCHESTRATION_APPROVAL_CONTRACT");
            } catch (RuntimeException failure) {
                logFailureDiagnosis("APPROVAL", runtimeReason(failure));
                fail(uiScenarioId, "CB_ORCHESTRATION_APPROVAL_RUNTIME");
            }
        });
    }

    private void publish(
            PendingStart start,
            OrchestrationSnapshot snapshot,
            int approvalIncrement) throws RemoteException {
        DevelopmentModelProjection modelProjection = readModelProjection(start, snapshot);
        ScenarioPlan plan = client.getPlan(start.sessionId);
        OrchestrationContract.validatePlanForSnapshot(plan, snapshot);
        if (approvalIncrement == 0) {
            if (modelProjection == null) {
                milestone("MODEL", "FALLBACK", "No network model projection");
            } else {
                milestone(
                        "MODEL",
                        "COMPLETED",
                        modelProjection.providerId + " · "
                                + modelProjection.latencyMs + " ms");
            }
            milestone("PLAN", "VALIDATED", "Revision " + snapshot.planRevision);
            milestone("POLICY", "ALLOWLISTED", "Simulation authority only");
        }
        CockpitSimulatedScenarioState.Projection projection;
        synchronized (this) {
            if (closed || start.generation != generation) {
                return;
            }
            approvalInputCount = Math.min(1, approvalInputCount + approvalIncrement);
            projection = validateAndProject(
                    start.uiScenarioId,
                    start.drivingProfile,
                    snapshot,
                    modelProjection,
                    approvalInputCount);
            pendingStart = null;
            latestSnapshot = snapshot;
            latestUiScenarioId = start.uiScenarioId;
            latestDrivingProfile = start.drivingProfile;
        }
        Log.i(TAG, "client2_orchestration_snapshot_projected=true"
                + " orchestration_state=" + snapshot.state
                + " plan_revision=" + snapshot.planRevision
                + " graph_revision=" + snapshot.graphRevision
                + " effect_count=" + length(snapshot.effects)
                + " model_projection_available=" + (modelProjection != null)
                + " model_latency_ms="
                + (modelProjection == null ? 0L : modelProjection.latencyMs)
                + " simulated_only=true"
                + " hardware_accessed=false"
                + " raw_payload_logged=false");
        milestone(
                "GRAPH",
                lifecycle(snapshot.state).name(),
                "Revision " + snapshot.graphRevision);
        milestone(
                "EFFECT",
                length(snapshot.effects) == 0 ? "WAITING" : "DISPATCHED",
                length(snapshot.effects) + " simulated effect record(s)");
        if (isTerminal(snapshot.state)) {
            milestone("READBACK", "SIMULATED", "No vehicle-bus evidence");
        }
        post(() -> callback.onSimulatedScenarioSnapshot(projection));
        if (start.effectAnimationOnly
                && approvalIncrement == 0
                && "care.fatigue".equals(start.uiScenarioId)
                && snapshot.state == ICentralBrainOrchestration.STATE_WAITING_APPROVAL
                && snapshot.pendingStage == ICentralBrainOrchestration.PENDING_APPROVAL) {
            milestone("SAFETY", "RESERVED", "Demo auto-continue · no authority granted");
            respondToApproval(ICentralBrainOrchestration.DECISION_APPROVE);
        }
    }

    private void cancelPreviousIfNeeded(
            OrchestrationSnapshot previous,
            String nextSessionId) {
        if (previous == null
                || previous.sessionId.equals(nextSessionId)
                || isTerminal(previous.state)) {
            return;
        }
        try {
            client.cancel(
                    previous.sessionId,
                    ICentralBrainSessionRuntime.CANCEL_REASON_CALLER_GONE);
        } catch (RemoteException | RuntimeException failure) {
            Log.w(TAG, "client2_orchestration_previous_cancelled=false"
                    + " hardware_accessed=false");
        }
    }

    private static CockpitSimulatedScenarioState.Projection validateAndProject(
            String uiScenarioId,
            String drivingProfile,
            OrchestrationSnapshot snapshot,
            DevelopmentModelProjection modelProjection,
            int approvalInputCount) {
        if (snapshot == null
                || snapshot.planId == null
                || snapshot.planId.isEmpty()
                || !snapshot.simulated
                || !snapshot.effectDispatchEnabled
                || snapshot.approvalAuthorityTrusted
                || snapshot.hardwareAccessed
                || snapshot.productionReady
                || snapshot.targetHardwareValidated) {
            throw new IllegalArgumentException("invalid Client2 orchestration authority flags");
        }
        if (!CockpitScenarioControlState.canonicalScenarioId(uiScenarioId)
                .equals(snapshot.scenarioId)) {
            throw new IllegalArgumentException("Client2 orchestration scenario mismatch");
        }
        if (snapshot.graphRevision > CockpitSimulatedScenarioState.MAX_REVISION) {
            throw new IllegalArgumentException("Client2 orchestration graph revision overflow");
        }
        if (modelProjection != null
                && (!snapshot.sessionId.equals(modelProjection.sessionId)
                        || !snapshot.scenarioId.equals(modelProjection.scenarioId))) {
            throw new IllegalArgumentException("Client2 model projection binding mismatch");
        }
        OrchestrationNode[] nodes = snapshot.nodes == null
                ? new OrchestrationNode[0] : snapshot.nodes;
        OrchestrationEffect[] effects = snapshot.effects == null
                ? new OrchestrationEffect[0] : snapshot.effects;
        int dispatched = 0;
        int readbackAttempts = 0;
        int readbackMatches = 0;
        int failures = 0;
        for (OrchestrationEffect effect : effects) {
            if (effect.state >= EffectContract.STATE_DISPATCHED) {
                dispatched++;
            }
            if (effect.state == EffectContract.STATE_VERIFIED) {
                readbackAttempts++;
                readbackMatches++;
            }
            if (effect.state == EffectContract.STATE_UNKNOWN
                    || effect.state == EffectContract.STATE_FAILED_RETRYABLE
                    || effect.state == EffectContract.STATE_FAILED_TERMINAL) {
                failures++;
            }
        }
        for (OrchestrationNode node : nodes) {
            if (node.state == ICentralBrainOrchestration.NODE_FAILED
                    || node.state == ICentralBrainOrchestration.NODE_STUCK) {
                failures++;
            }
        }
        CockpitSimulatedScenarioState.Lifecycle lifecycle = lifecycle(snapshot.state);
        if (lifecycle == CockpitSimulatedScenarioState.Lifecycle.COMPLETED) {
            failures = 0;
        }
        int projectedEvents = Math.max(
                1,
                Math.min(
                        CockpitSimulatedScenarioState.MAX_EVENT_COUNT,
                        nodes.length + effects.length));
        return CockpitSimulatedScenarioState.Projection.create(
                uiScenarioId,
                snapshot.scenarioId,
                drivingProfile,
                lifecycle,
                pendingStage(snapshot.pendingStage),
                pendingTarget(uiScenarioId, snapshot),
                snapshot.planRevision,
                Math.toIntExact(snapshot.graphRevision),
                projectedEvents,
                Math.min(dispatched, CockpitSimulatedScenarioState.MAX_EFFECT_COUNT),
                Math.min(readbackAttempts, CockpitSimulatedScenarioState.MAX_EFFECT_COUNT),
                Math.min(readbackMatches, readbackAttempts),
                approvalInputCount,
                Math.min(failures, CockpitSimulatedScenarioState.MAX_EFFECT_COUNT),
                modelProjection == null ? "" : modelProjection.assistantDisplayText,
                modelProjection == null ? "" : modelProjection.providerId,
                modelProjection != null,
                modelProjection == null ? 0L : modelProjection.latencyMs);
    }

    private DevelopmentModelProjection readModelProjection(
            PendingStart start,
            OrchestrationSnapshot snapshot) {
        if (!modelProjectionClient.isConnected()) {
            Log.w(TAG, "client2_development_model_projection_available=false"
                    + " reason=NOT_CONNECTED");
            return null;
        }
        try {
            DevelopmentModelProjection projection =
                    modelProjectionClient.getOwnProjection(start.sessionId);
            if (projection != null && !snapshot.scenarioId.equals(projection.scenarioId)) {
                throw new IllegalArgumentException("model projection scenario mismatch");
            }
            return projection;
        } catch (RemoteException failure) {
            Log.w(TAG, "client2_development_model_projection_available=false"
                    + " reason=REMOTE");
            return null;
        } catch (RuntimeException failure) {
            Log.w(TAG, "client2_development_model_projection_available=false"
                    + " reason=CONTRACT");
            return null;
        }
    }

    private static String pendingTarget(
            String uiScenarioId,
            OrchestrationSnapshot snapshot) {
        if (snapshot.pendingStage == ICentralBrainOrchestration.PENDING_NONE) {
            return "";
        }
        if (snapshot.pendingStage == ICentralBrainOrchestration.PENDING_APPROVAL
                && "care.fatigue".equals(uiScenarioId)
                && "request_seat_approval".equals(snapshot.pendingNodeId)
                && (snapshot.pendingCapabilityId == null
                        || snapshot.pendingCapabilityId.isEmpty())) {
            return "vehicle.seat.recline";
        }
        if (snapshot.pendingStage == ICentralBrainOrchestration.PENDING_UNDO) {
            throw new IllegalArgumentException("undo projection is not exposed by this HMI slice");
        }
        return snapshot.pendingCapabilityId == null ? "" : snapshot.pendingCapabilityId;
    }

    private static CockpitSimulatedScenarioState.Lifecycle lifecycle(int state) {
        switch (state) {
            case ICentralBrainOrchestration.STATE_PLANNING:
                return CockpitSimulatedScenarioState.Lifecycle.CONNECTING;
            case ICentralBrainOrchestration.STATE_WAITING_APPROVAL:
                return CockpitSimulatedScenarioState.Lifecycle.WAITING_APPROVAL;
            case ICentralBrainOrchestration.STATE_RUNNING:
                return CockpitSimulatedScenarioState.Lifecycle.RUNNING;
            case ICentralBrainOrchestration.STATE_COMPLETED:
                return CockpitSimulatedScenarioState.Lifecycle.COMPLETED;
            case ICentralBrainOrchestration.STATE_PARTIAL:
                return CockpitSimulatedScenarioState.Lifecycle.PARTIAL;
            case ICentralBrainOrchestration.STATE_FAILED:
            case ICentralBrainOrchestration.STATE_BLOCKED:
                return CockpitSimulatedScenarioState.Lifecycle.FAILED;
            case ICentralBrainOrchestration.STATE_CANCELLED:
                return CockpitSimulatedScenarioState.Lifecycle.CANCELLED;
            case ICentralBrainOrchestration.STATE_STUCK:
                return CockpitSimulatedScenarioState.Lifecycle.STUCK;
            default:
                throw new IllegalArgumentException("invalid orchestration state");
        }
    }

    private static CockpitSimulatedScenarioState.PendingStage pendingStage(int stage) {
        switch (stage) {
            case ICentralBrainOrchestration.PENDING_NONE:
                return CockpitSimulatedScenarioState.PendingStage.NONE;
            case ICentralBrainOrchestration.PENDING_APPROVAL:
                return CockpitSimulatedScenarioState.PendingStage.APPROVAL;
            case ICentralBrainOrchestration.PENDING_EFFECT:
                return CockpitSimulatedScenarioState.PendingStage.EFFECT;
            case ICentralBrainOrchestration.PENDING_READBACK:
                return CockpitSimulatedScenarioState.PendingStage.READBACK;
            default:
                throw new IllegalArgumentException("invalid orchestration pending stage");
        }
    }

    private static boolean isNotStarted(OrchestrationSnapshot snapshot) {
        return snapshot != null
                && (snapshot.planId == null || snapshot.planId.isEmpty())
                && snapshot.state == ICentralBrainOrchestration.STATE_BLOCKED
                && NOT_STARTED.equals(snapshot.detailCode);
    }

    private static boolean isTerminal(int state) {
        return state == ICentralBrainOrchestration.STATE_COMPLETED
                || state == ICentralBrainOrchestration.STATE_PARTIAL
                || state == ICentralBrainOrchestration.STATE_FAILED
                || state == ICentralBrainOrchestration.STATE_CANCELLED
                || state == ICentralBrainOrchestration.STATE_STUCK;
    }

    private void failStart(PendingStart start, String code) {
        failStart(start, code, "COMMAND", "NOT_APPLICABLE");
    }

    private void failStart(
            PendingStart start,
            String code,
            String stage,
            String reason) {
        synchronized (this) {
            if (start.generation == generation) {
                pendingStart = null;
            }
        }
        logFailureDiagnosis(stage, reason);
        milestone(stage, "FAILED", reason);
        fail(start.uiScenarioId, code);
    }

    private static void logFailureDiagnosis(String stage, String reason) {
        Log.w(TAG, "client2_orchestration_failure_diagnosed=true"
                + " failure_stage=" + stage
                + " failure_reason=" + reason
                + " raw_payload_logged=false"
                + " hardware_accessed=false");
    }

    private static String contractReason(IllegalArgumentException failure) {
        String message = failure.getMessage();
        if (message == null) {
            return "MESSAGE_ABSENT";
        }
        if (message.contains("Session not found for caller")) {
            return "SESSION_NOT_FOUND";
        }
        if (message.contains("scenario differs from durable Session")) {
            return "SESSION_SCENARIO_MISMATCH";
        }
        if (message.contains("Session is not eligible for orchestration")) {
            return "SESSION_NOT_ELIGIBLE";
        }
        if (message.contains("typed plan differs from orchestration projection")) {
            return "PLAN_PROJECTION_MISMATCH";
        }
        if (message.contains("invalid Client2 orchestration authority flags")) {
            return "CLIENT_AUTHORITY_FLAGS_INVALID";
        }
        if (message.contains("Client2 orchestration scenario mismatch")) {
            return "CLIENT_SCENARIO_MISMATCH";
        }
        if (message.contains("graph revision overflow")) {
            return "GRAPH_REVISION_OVERFLOW";
        }
        if (message.startsWith("CB_ORCHESTRATION_CONTRACT:")) {
            return "SDK_PROJECTION_INVALID";
        }
        if (message.startsWith("CB_DEBUG_ORCHESTRATION:")) {
            return boundedContractRule(message);
        }
        if (message.startsWith("CB_ORCHESTRATION_RUNTIME:")) {
            return "RUNTIME_CONTRACT_INVALID";
        }
        return boundedContractRule(message);
    }

    private static String boundedContractRule(String message) {
        StringBuilder rule = new StringBuilder();
        boolean separator = false;
        for (int index = 0; index < message.length() && rule.length() < 80; index++) {
            char character = message.charAt(index);
            if ((character >= 'A' && character <= 'Z')
                    || (character >= 'a' && character <= 'z')
                    || (character >= '0' && character <= '9')) {
                rule.append(Character.toUpperCase(character));
                separator = false;
            } else if (!separator && rule.length() > 0) {
                rule.append('_');
                separator = true;
            }
        }
        while (rule.length() > 0 && rule.charAt(rule.length() - 1) == '_') {
            rule.setLength(rule.length() - 1);
        }
        return rule.length() == 0 ? "UNCLASSIFIED_CONTRACT_FAILURE" : rule.toString();
    }

    private static String runtimeReason(RuntimeException failure) {
        String type = failure.getClass().getSimpleName();
        String message = failure.getMessage();
        String rule = message == null ? "MESSAGE_ABSENT" : boundedContractRule(message);
        return boundedContractRule(type + "_" + rule);
    }

    private void failAvailability(String code) {
        synchronized (this) {
            if (closed) {
                return;
            }
            connected = false;
        }
        Log.w(TAG, "client2_orchestration_sdk_available=false"
                + " error_code=" + code
                + " raw_payload_logged=false");
        post(() -> callback.onSimulatedRuntimeAvailability(false, code));
    }

    private void fail(String uiScenarioId, String code) {
        String scenario = CockpitSimulatedScenarioState.isSupported(uiScenarioId)
                ? uiScenarioId : "care.cold";
        Log.w(TAG, "client2_orchestration_command_failed=true"
                + " error_code=" + code
                + " raw_payload_logged=false"
                + " hardware_accessed=false");
        post(() -> callback.onSimulatedScenarioFailure(scenario, code));
    }

    private void post(Runnable runnable) {
        mainHandler.post(() -> {
            synchronized (OrchestrationRuntimeClient.this) {
                if (closed) {
                    return;
                }
            }
            runnable.run();
        });
    }

    private void milestone(String stage, String status, String detail) {
        post(() -> callback.onPipelineMilestone(stage, status, detail));
    }

    @Override
    public void close() {
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            connected = false;
            pendingStart = null;
            latestSnapshot = null;
        }
        client.close();
        modelProjectionClient.close();
        binderExecutor.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
    }

    private static String requireScenario(String uiScenarioId) {
        if (!CockpitSimulatedScenarioState.isSupported(uiScenarioId)) {
            throw new IllegalArgumentException("unsupported orchestration scenario");
        }
        return uiScenarioId;
    }

    private static String requireUuid(String value) {
        try {
            if (!UUID.fromString(value).toString().equals(value)) {
                throw new IllegalArgumentException();
            }
            return value;
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("invalid orchestration sessionId");
        }
    }

    private static int motionState(CockpitSeatState.DrivingState state) {
        return state == CockpitSeatState.DrivingState.PARKED
                ? ICentralBrainOrchestration.MOTION_PARKED
                : ICentralBrainOrchestration.MOTION_MOVING;
    }

    private static String drivingProfile(CockpitSeatState.DrivingState state) {
        return state == CockpitSeatState.DrivingState.PARKED
                ? "PARKED" : "MOVING_RESTRICTED";
    }

    private static int length(Object[] values) {
        return values == null ? 0 : values.length;
    }
}
