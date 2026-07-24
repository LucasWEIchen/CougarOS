package com.centralbrain.client2;

import java.util.Objects;

/** Immutable HMI projection of the formal Orchestration V1 debug profile. */
public final class CockpitSimulatedScenarioState {
    public static final int MAX_EFFECT_COUNT = 16;
    public static final int MAX_EVENT_COUNT = 64;
    public static final int MAX_REVISION = 1_000_000;
    public static final int MAX_APPROVAL_COUNT = 3;

    public enum Lifecycle {
        UNAVAILABLE,
        IDLE,
        CONNECTING,
        RUNNING,
        WAITING_APPROVAL,
        COMPLETED,
        PARTIAL,
        FAILED,
        CANCELLED,
        STUCK
    }

    public enum PendingStage { NONE, APPROVAL, EFFECT, READBACK }

    /** Pure-Java validated transfer object produced by the Android SDK client. */
    public static final class Projection {
        private final String uiScenarioId;
        private final String canonicalScenarioId;
        private final String drivingProfile;
        private final Lifecycle lifecycle;
        private final PendingStage pendingStage;
        private final String pendingCapabilityId;
        private final int planRevision;
        private final int graphRevision;
        private final int projectedEventCount;
        private final int effectDispatchCount;
        private final int readbackAttemptCount;
        private final int readbackMatchCount;
        private final int approvalInputCount;
        private final int failureCount;
        private final String assistantDisplayText;
        private final String modelProviderId;
        private final boolean modelInferenceCompleted;
        private final long modelLatencyMs;

        private Projection(
                String uiScenarioId,
                String canonicalScenarioId,
                String drivingProfile,
                Lifecycle lifecycle,
                PendingStage pendingStage,
                String pendingCapabilityId,
                int planRevision,
                int graphRevision,
                int projectedEventCount,
                int effectDispatchCount,
                int readbackAttemptCount,
                int readbackMatchCount,
                int approvalInputCount,
                int failureCount,
                String assistantDisplayText,
                String modelProviderId,
                boolean modelInferenceCompleted,
                long modelLatencyMs) {
            this.uiScenarioId = requireScenario(uiScenarioId);
            this.canonicalScenarioId = requireCanonical(
                    this.uiScenarioId, canonicalScenarioId);
            this.drivingProfile = requireDriving(drivingProfile);
            this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
            this.pendingStage = Objects.requireNonNull(pendingStage, "pendingStage");
            this.pendingCapabilityId = requireCapability(pendingCapabilityId, pendingStage);
            this.planRevision = positiveBounded(planRevision, MAX_EVENT_COUNT, "planRevision");
            this.graphRevision = positiveBounded(
                    graphRevision, MAX_REVISION, "graphRevision");
            this.projectedEventCount = positiveBounded(
                    projectedEventCount, MAX_EVENT_COUNT, "projectedEventCount");
            this.effectDispatchCount = count(
                    effectDispatchCount, MAX_EFFECT_COUNT, "effectDispatchCount");
            this.readbackAttemptCount = count(
                    readbackAttemptCount, MAX_EFFECT_COUNT, "readbackAttemptCount");
            this.readbackMatchCount = count(
                    readbackMatchCount, readbackAttemptCount, "readbackMatchCount");
            this.approvalInputCount = count(
                    approvalInputCount, MAX_APPROVAL_COUNT, "approvalInputCount");
            this.failureCount = count(failureCount, MAX_EFFECT_COUNT, "failureCount");
            this.assistantDisplayText = bounded(
                    assistantDisplayText, 256, "assistantDisplayText");
            this.modelProviderId = bounded(modelProviderId, 96, "modelProviderId");
            this.modelInferenceCompleted = modelInferenceCompleted;
            this.modelLatencyMs = modelLatencyMs;
            validateModelProjection();
            validateLifecycle();
        }

        public static Projection create(
                String uiScenarioId,
                String canonicalScenarioId,
                String drivingProfile,
                Lifecycle lifecycle,
                PendingStage pendingStage,
                String pendingCapabilityId,
                int planRevision,
                int graphRevision,
                int projectedEventCount,
                int effectDispatchCount,
                int readbackAttemptCount,
                int readbackMatchCount,
                int approvalInputCount,
                int failureCount,
                String assistantDisplayText,
                String modelProviderId,
                boolean modelInferenceCompleted,
                long modelLatencyMs) {
            return new Projection(
                    uiScenarioId,
                    canonicalScenarioId,
                    drivingProfile,
                    lifecycle,
                    pendingStage,
                    pendingCapabilityId,
                    planRevision,
                    graphRevision,
                    projectedEventCount,
                    effectDispatchCount,
                    readbackAttemptCount,
                    readbackMatchCount,
                    approvalInputCount,
                    failureCount,
                    assistantDisplayText,
                    modelProviderId,
                    modelInferenceCompleted,
                    modelLatencyMs);
        }

        private void validateModelProjection() {
            if (modelLatencyMs < 0L || modelLatencyMs > 120_000L) {
                throw new IllegalArgumentException("model latency is out of bounds");
            }
            if (modelInferenceCompleted) {
                if (assistantDisplayText.isEmpty()
                        || !modelProviderId.matches("[a-z][a-z0-9_.-]{2,95}")) {
                    throw new IllegalArgumentException("completed model projection is incomplete");
                }
            } else if (!assistantDisplayText.isEmpty()
                    || !modelProviderId.isEmpty()
                    || modelLatencyMs != 0L) {
                throw new IllegalArgumentException("inactive model projection carries output");
            }
        }

        private void validateLifecycle() {
            if ((lifecycle == Lifecycle.WAITING_APPROVAL)
                    != (pendingStage == PendingStage.APPROVAL)) {
                throw new IllegalArgumentException("approval lifecycle and pending stage mismatch");
            }
            if (isTerminal(lifecycle) && pendingStage != PendingStage.NONE) {
                throw new IllegalArgumentException("terminal projection cannot have pending stage");
            }
            if (lifecycle == Lifecycle.COMPLETED && failureCount != 0) {
                throw new IllegalArgumentException("completed projection cannot contain failures");
            }
        }
    }

    private final boolean runtimeAvailable;
    private final String uiScenarioId;
    private final String canonicalScenarioId;
    private final String drivingProfile;
    private final Lifecycle lifecycle;
    private final PendingStage pendingStage;
    private final String pendingCapabilityId;
    private final int planRevision;
    private final int graphRevision;
    private final int projectedEventCount;
    private final int effectDispatchCount;
    private final int readbackAttemptCount;
    private final int readbackMatchCount;
    private final int approvalInputCount;
    private final int failureCount;
    private final String failureCode;
    private final String assistantDisplayText;
    private final String modelProviderId;
    private final boolean modelInferenceCompleted;
    private final long modelLatencyMs;

    private CockpitSimulatedScenarioState(
            boolean runtimeAvailable,
            String uiScenarioId,
            String canonicalScenarioId,
            String drivingProfile,
            Lifecycle lifecycle,
            PendingStage pendingStage,
            String pendingCapabilityId,
            int planRevision,
            int graphRevision,
            int projectedEventCount,
            int effectDispatchCount,
            int readbackAttemptCount,
            int readbackMatchCount,
            int approvalInputCount,
            int failureCount,
            String failureCode,
            String assistantDisplayText,
            String modelProviderId,
            boolean modelInferenceCompleted,
            long modelLatencyMs) {
        this.runtimeAvailable = runtimeAvailable;
        this.uiScenarioId = bounded(uiScenarioId, 32, "uiScenarioId");
        this.canonicalScenarioId = bounded(canonicalScenarioId, 96, "canonicalScenarioId");
        this.drivingProfile = bounded(drivingProfile, 32, "drivingProfile");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.pendingStage = Objects.requireNonNull(pendingStage, "pendingStage");
        this.pendingCapabilityId = bounded(
                pendingCapabilityId, 128, "pendingCapabilityId");
        this.planRevision = planRevision;
        this.graphRevision = graphRevision;
        this.projectedEventCount = projectedEventCount;
        this.effectDispatchCount = effectDispatchCount;
        this.readbackAttemptCount = readbackAttemptCount;
        this.readbackMatchCount = readbackMatchCount;
        this.approvalInputCount = approvalInputCount;
        this.failureCount = failureCount;
        this.failureCode = bounded(failureCode, 64, "failureCode");
        this.assistantDisplayText = bounded(
                assistantDisplayText, 256, "assistantDisplayText");
        this.modelProviderId = bounded(modelProviderId, 96, "modelProviderId");
        this.modelInferenceCompleted = modelInferenceCompleted;
        this.modelLatencyMs = modelLatencyMs;
    }

    public static CockpitSimulatedScenarioState initial() {
        return empty(false, Lifecycle.UNAVAILABLE, "");
    }

    CockpitSimulatedScenarioState availability(boolean available, String failureCode) {
        Lifecycle nextLifecycle = lifecycle;
        if (uiScenarioId.isEmpty()) {
            nextLifecycle = available ? Lifecycle.IDLE : Lifecycle.UNAVAILABLE;
        }
        return copy(available, nextLifecycle, available ? "" : failureCode);
    }

    CockpitSimulatedScenarioState requested(String requestedUiScenarioId, String profile) {
        return new CockpitSimulatedScenarioState(
                runtimeAvailable,
                requireScenario(requestedUiScenarioId),
                "",
                requireDriving(profile),
                Lifecycle.CONNECTING,
                PendingStage.NONE,
                "",
                0, 0, 0, 0, 0, 0, 0, 0, "", "", "", false, 0L);
    }

    CockpitSimulatedScenarioState idle() {
        return empty(
                runtimeAvailable,
                runtimeAvailable ? Lifecycle.IDLE : Lifecycle.UNAVAILABLE,
                failureCode);
    }

    CockpitSimulatedScenarioState snapshot(Projection projection) {
        Projection required = Objects.requireNonNull(projection, "projection");
        if (!uiScenarioId.equals(required.uiScenarioId)) {
            return this;
        }
        return new CockpitSimulatedScenarioState(
                true,
                required.uiScenarioId,
                required.canonicalScenarioId,
                required.drivingProfile,
                required.lifecycle,
                required.pendingStage,
                required.pendingCapabilityId,
                required.planRevision,
                required.graphRevision,
                required.projectedEventCount,
                required.effectDispatchCount,
                required.readbackAttemptCount,
                required.readbackMatchCount,
                required.approvalInputCount,
                required.failureCount,
                "",
                required.assistantDisplayText,
                required.modelProviderId,
                required.modelInferenceCompleted,
                required.modelLatencyMs);
    }

    CockpitSimulatedScenarioState failure(String scenarioId, String code) {
        if (!uiScenarioId.isEmpty() && !uiScenarioId.equals(scenarioId)) {
            return this;
        }
        String effectiveScenario = uiScenarioId.isEmpty() ? requireScenario(scenarioId) : uiScenarioId;
        return new CockpitSimulatedScenarioState(
                false,
                effectiveScenario,
                canonicalScenarioId,
                drivingProfile,
                Lifecycle.FAILED,
                PendingStage.NONE,
                "",
                planRevision,
                graphRevision,
                projectedEventCount,
                effectDispatchCount,
                readbackAttemptCount,
                readbackMatchCount,
                approvalInputCount,
                Math.max(1, failureCount),
                bounded(code, 64, "failureCode"),
                assistantDisplayText,
                modelProviderId,
                modelInferenceCompleted,
                modelLatencyMs);
    }

    CockpitSimulatedScenarioState detached() {
        return copy(false, lifecycle, "CB_SIM_SCENARIO_DETACHED");
    }

    public boolean isRuntimeAvailable() {
        return runtimeAvailable;
    }

    public boolean hasScenario() {
        return !uiScenarioId.isEmpty();
    }

    public boolean hasSnapshot() {
        return planRevision > 0;
    }

    public boolean isApprovalInputEnabled() {
        return runtimeAvailable
                && lifecycle == Lifecycle.WAITING_APPROVAL
                && pendingStage == PendingStage.APPROVAL;
    }

    public boolean isSimulatedOnly() {
        return true;
    }

    public boolean isHardwareAccessed() {
        return false;
    }

    public boolean isProductionReady() {
        return false;
    }

    public boolean isTargetHardwareValidated() {
        return false;
    }

    public String getUiScenarioId() { return uiScenarioId; }
    public String getCanonicalScenarioId() { return canonicalScenarioId; }
    public String getDrivingProfile() { return drivingProfile; }
    public Lifecycle getLifecycle() { return lifecycle; }
    public PendingStage getPendingStage() { return pendingStage; }
    public String getPendingCapabilityId() { return pendingCapabilityId; }
    public int getPlanRevision() { return planRevision; }
    public int getGraphRevision() { return graphRevision; }
    public int getProjectedEventCount() { return projectedEventCount; }
    public int getEffectDispatchCount() { return effectDispatchCount; }
    public int getReadbackAttemptCount() { return readbackAttemptCount; }
    public int getReadbackMatchCount() { return readbackMatchCount; }
    public int getApprovalInputCount() { return approvalInputCount; }
    public int getFailureCount() { return failureCount; }
    public String getFailureCode() { return failureCode; }
    public String getAssistantDisplayText() { return assistantDisplayText; }
    public String getModelProviderId() { return modelProviderId; }
    public boolean isModelInferenceCompleted() { return modelInferenceCompleted; }
    public long getModelLatencyMs() { return modelLatencyMs; }

    public static boolean isSupported(String uiScenarioId) {
        return "care.cold".equals(uiScenarioId)
                || "care.fatigue".equals(uiScenarioId)
                || "cabin.multimodal".equals(uiScenarioId);
    }

    private CockpitSimulatedScenarioState copy(
            boolean available,
            Lifecycle nextLifecycle,
            String nextFailureCode) {
        return new CockpitSimulatedScenarioState(
                available,
                uiScenarioId,
                canonicalScenarioId,
                drivingProfile,
                nextLifecycle,
                pendingStage,
                pendingCapabilityId,
                planRevision,
                graphRevision,
                projectedEventCount,
                effectDispatchCount,
                readbackAttemptCount,
                readbackMatchCount,
                approvalInputCount,
                failureCount,
                nextFailureCode,
                assistantDisplayText,
                modelProviderId,
                modelInferenceCompleted,
                modelLatencyMs);
    }

    private static CockpitSimulatedScenarioState empty(
            boolean available,
            Lifecycle lifecycle,
            String failureCode) {
        return new CockpitSimulatedScenarioState(
                available, "", "", "", lifecycle, PendingStage.NONE, "",
                0, 0, 0, 0, 0, 0, 0, 0, failureCode, "", "", false, 0L);
    }

    private static boolean isTerminal(Lifecycle value) {
        return value == Lifecycle.COMPLETED
                || value == Lifecycle.PARTIAL
                || value == Lifecycle.FAILED
                || value == Lifecycle.CANCELLED
                || value == Lifecycle.STUCK;
    }

    private static String requireScenario(String value) {
        if (!isSupported(value)) {
            throw new IllegalArgumentException("unsupported simulated scenario");
        }
        return value;
    }

    private static String requireCanonical(String uiScenarioId, String value) {
        String expected = "care.cold".equals(uiScenarioId)
                ? "scene.comfort.cold.v1"
                : "care.fatigue".equals(uiScenarioId)
                        ? "scene.fatigue.assist.v1"
                        : "scene.cabin.multimodal.assist.v1";
        if (!expected.equals(value)) {
            throw new IllegalArgumentException("simulated scenario catalog mismatch");
        }
        return value;
    }

    private static String requireDriving(String value) {
        if (!"PARKED".equals(value) && !"MOVING_RESTRICTED".equals(value)) {
            throw new IllegalArgumentException("invalid simulated driving profile");
        }
        return value;
    }

    private static String requireCapability(String value, PendingStage pendingStage) {
        String safe = value == null ? "" : value;
        if (pendingStage == PendingStage.NONE) {
            if (!safe.isEmpty()) {
                throw new IllegalArgumentException("terminal projection exposes capability");
            }
            return safe;
        }
        if (!safe.matches("[a-z][a-z0-9_.]{2,127}")) {
            throw new IllegalArgumentException("invalid pending capability");
        }
        return safe;
    }

    private static int positiveBounded(int value, int max, String field) {
        if (value < 1 || value > max) {
            throw new IllegalArgumentException(field + " is out of bounds");
        }
        return value;
    }

    private static int count(int value, int max, String field) {
        if (value < 0 || value > max) {
            throw new IllegalArgumentException(field + " is out of bounds");
        }
        return value;
    }

    private static String bounded(String value, int max, String field) {
        if (value == null || value.length() > max) {
            throw new IllegalArgumentException("invalid simulated scenario " + field);
        }
        return value;
    }
}
