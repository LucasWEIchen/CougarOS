package com.centralbrain.runtime.scheduler;

import com.centralbrain.runtime.model.ModelContractV2;
import com.centralbrain.runtime.model.PolicyAwareModelRouter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Freshness-bounded model resource and thermal admission. It may enqueue metadata but never
 * invokes a provider. Req IDs: S2-MDL-001, S2-SAF-001, S2-OBS-001, NV-G-004, DEL-001/004/005.
 */
public final class ModelResourceAdmission {
    public static final int SCHEMA_VERSION = 1;
    public static final long MAX_RESOURCE_VALIDITY_MS = 60_000L;
    public static final int COMPACT_INPUT_TOKEN_LIMIT = 1_024;
    public static final int COMPACT_OUTPUT_TOKEN_LIMIT = 256;
    public static final int MINIMAL_SAFETY_INPUT_TOKEN_LIMIT = 256;
    public static final int MINIMAL_SAFETY_OUTPUT_TOKEN_LIMIT = 64;
    public static final long FOREGROUND_MAX_QUEUE_WAIT_MS = 1_000L;
    public static final long INTERACTIVE_MAX_QUEUE_WAIT_MS = 2_000L;
    public static final long BACKGROUND_MAX_QUEUE_WAIT_MS = 5_000L;

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");

    private ModelResourceAdmission() {
    }

    public enum WorkloadClass {
        FOREGROUND_VEHICLE,
        INTERACTIVE_COCKPIT,
        BACKGROUND_MAINTENANCE
    }

    public enum CapacityState {
        AVAILABLE,
        CONSTRAINED,
        EXHAUSTED,
        UNKNOWN
    }

    public enum SnapshotRejection {
        NONE,
        FROM_FUTURE,
        STALE
    }

    public enum DegradationMode {
        NONE,
        COMPACT_FOREGROUND,
        MINIMAL_SAFETY
    }

    public enum DecisionCode {
        ADMITTED,
        ADMITTED_DEGRADED,
        REPLAYED,
        REPLAYED_DEGRADED,
        ROUTE_NOT_SELECTED,
        REQUEST_ROUTE_MISMATCH,
        POLICY_BINDING_MISMATCH,
        POLICY_SNAPSHOT_REJECTED,
        RESOURCE_SNAPSHOT_REJECTED,
        WORKLOAD_PURPOSE_MISMATCH,
        THERMAL_BLOCKED,
        RESOURCE_BLOCKED,
        SCHEDULER_REJECTED
    }

    public enum SchedulerAdmission {
        NOT_SUBMITTED,
        ADMITTED,
        REPLAYED,
        DUPLICATE_ACTIVE_REQUEST,
        DEADLINE_EXCEEDED,
        QUEUE_TIMEOUT_OUT_OF_RANGE,
        GLOBAL_QUEUE_QUOTA_EXCEEDED,
        OWNER_QUEUE_QUOTA_EXCEEDED,
        ROUTE_UNAVAILABLE
    }

    /** Caller-owned provider capacity metadata; it performs no hardware or system reads. */
    public static final class ResourceSnapshot {
        private final String providerId;
        private final CapacityState capacityState;
        private final int availableExecutionSlots;
        private final long revision;
        private final long observedAtElapsedMs;
        private final long validUntilElapsedMs;
        private final String evidenceDigest;
        private final String policySnapshotDigest;
        private final String snapshotDigest;

        public ResourceSnapshot(
                String providerId,
                CapacityState capacityState,
                int availableExecutionSlots,
                long revision,
                long observedAtElapsedMs,
                long validUntilElapsedMs,
                String evidenceDigest,
                PolicyAwareModelRouter.PolicySnapshot policySnapshot) {
            this.providerId = requireIdentifier(providerId, "providerId");
            this.capacityState = Objects.requireNonNull(capacityState, "capacityState");
            if (availableExecutionSlots < 0 || availableExecutionSlots > 64) {
                throw violation("availableExecutionSlots is out of range");
            }
            if ((capacityState == CapacityState.AVAILABLE
                            || capacityState == CapacityState.CONSTRAINED)
                    != (availableExecutionSlots > 0)) {
                throw violation("capacity state and available slots do not agree");
            }
            if (revision < 1) {
                throw violation("resource revision must be positive");
            }
            if (observedAtElapsedMs < 0
                    || validUntilElapsedMs <= observedAtElapsedMs
                    || validUntilElapsedMs - observedAtElapsedMs
                            > MAX_RESOURCE_VALIDITY_MS) {
                throw violation("resource validity window is invalid");
            }
            this.availableExecutionSlots = availableExecutionSlots;
            this.revision = revision;
            this.observedAtElapsedMs = observedAtElapsedMs;
            this.validUntilElapsedMs = validUntilElapsedMs;
            this.evidenceDigest = requireDigest(evidenceDigest, "evidenceDigest");
            this.policySnapshotDigest = Objects.requireNonNull(
                    policySnapshot, "policySnapshot").getSnapshotDigest();
            this.snapshotDigest = sha256(canonicalForm());
        }

        public String getProviderId() {
            return providerId;
        }

        public CapacityState getCapacityState() {
            return capacityState;
        }

        public int getAvailableExecutionSlots() {
            return availableExecutionSlots;
        }

        public long getRevision() {
            return revision;
        }

        public long getObservedAtElapsedMs() {
            return observedAtElapsedMs;
        }

        public long getValidUntilElapsedMs() {
            return validUntilElapsedMs;
        }

        public String getEvidenceDigest() {
            return evidenceDigest;
        }

        public String getPolicySnapshotDigest() {
            return policySnapshotDigest;
        }

        public String getSnapshotDigest() {
            return snapshotDigest;
        }

        private SnapshotRejection rejectionAt(long nowElapsedMs) {
            if (observedAtElapsedMs > nowElapsedMs) {
                return SnapshotRejection.FROM_FUTURE;
            }
            if (validUntilElapsedMs <= nowElapsedMs) {
                return SnapshotRejection.STALE;
            }
            return SnapshotRejection.NONE;
        }

        private String canonicalForm() {
            return SCHEMA_VERSION
                    + "|" + providerId
                    + "|" + capacityState.name()
                    + "|" + availableExecutionSlots
                    + "|" + revision
                    + "|" + observedAtElapsedMs
                    + "|" + validUntilElapsedMs
                    + "|" + evidenceDigest
                    + "|" + policySnapshotDigest;
        }
    }

    /** Trusted scheduling identity and workload class supplied by a future Runtime policy owner. */
    public static final class AdmissionContext {
        private final String ownerFingerprint;
        private final String modelId;
        private final WorkloadClass workloadClass;
        private final String contextDigest;

        public AdmissionContext(
                String ownerFingerprint,
                String modelId,
                WorkloadClass workloadClass) {
            this.ownerFingerprint = requireDigest(ownerFingerprint, "ownerFingerprint");
            this.modelId = requireIdentifier(modelId, "modelId");
            this.workloadClass = Objects.requireNonNull(workloadClass, "workloadClass");
            this.contextDigest = sha256(
                    ownerFingerprint + "|" + modelId + "|" + workloadClass.name());
        }

        public String getOwnerFingerprint() {
            return ownerFingerprint;
        }

        public String getModelId() {
            return modelId;
        }

        public WorkloadClass getWorkloadClass() {
            return workloadClass;
        }

        public String getContextDigest() {
            return contextDigest;
        }
    }

    /** Immutable decision. Effective limits are future provider constraints, not execution. */
    public static final class AdmissionDecision {
        private final DecisionCode code;
        private final SnapshotRejection policySnapshotRejection;
        private final SnapshotRejection resourceSnapshotRejection;
        private final SchedulerAdmission schedulerAdmission;
        private final DegradationMode degradationMode;
        private final InferenceResourceScheduler.EffectivePriority effectivePriority;
        private final String requestId;
        private final String requestFingerprint;
        private final String traceId;
        private final String providerId;
        private final String routeDecisionDigest;
        private final String policySnapshotDigest;
        private final String resourceSnapshotDigest;
        private final String admissionContextDigest;
        private final int effectiveInputTokenLimit;
        private final int effectiveOutputTokenLimit;
        private final int effectiveTotalTokenLimit;
        private final long effectiveMaxQueueWaitMs;
        private final long taskDeadlineElapsedMs;
        private final InferenceResourceScheduler.ActiveSnapshot activeSnapshot;
        private final String decisionDigest;

        private AdmissionDecision(
                DecisionCode code,
                SnapshotRejection policySnapshotRejection,
                SnapshotRejection resourceSnapshotRejection,
                SchedulerAdmission schedulerAdmission,
                DegradationMode degradationMode,
                InferenceResourceScheduler.EffectivePriority effectivePriority,
                ModelContractV2.ModelRequest request,
                PolicyAwareModelRouter.RouteDecision routeDecision,
                PolicyAwareModelRouter.PolicySnapshot policySnapshot,
                ResourceSnapshot resourceSnapshot,
                AdmissionContext admissionContext,
                EffectiveLimits limits,
                long taskDeadlineElapsedMs,
                InferenceResourceScheduler.ActiveSnapshot activeSnapshot) {
            this.code = Objects.requireNonNull(code, "code");
            this.policySnapshotRejection = Objects.requireNonNull(
                    policySnapshotRejection, "policySnapshotRejection");
            this.resourceSnapshotRejection = Objects.requireNonNull(
                    resourceSnapshotRejection, "resourceSnapshotRejection");
            this.schedulerAdmission = Objects.requireNonNull(
                    schedulerAdmission, "schedulerAdmission");
            this.degradationMode = Objects.requireNonNull(
                    degradationMode, "degradationMode");
            this.effectivePriority = Objects.requireNonNull(
                    effectivePriority, "effectivePriority");
            this.requestId = request.getRequestId();
            this.requestFingerprint = request.getRequestFingerprint();
            this.traceId = request.getTraceId();
            this.providerId = routeDecision.getPrimaryProviderId();
            this.routeDecisionDigest = routeDecision.getDecisionDigest();
            this.policySnapshotDigest = policySnapshot.getSnapshotDigest();
            this.resourceSnapshotDigest = resourceSnapshot.getSnapshotDigest();
            this.admissionContextDigest = admissionContext.getContextDigest();
            this.effectiveInputTokenLimit = limits.inputTokens;
            this.effectiveOutputTokenLimit = limits.outputTokens;
            this.effectiveTotalTokenLimit = limits.totalTokens;
            this.effectiveMaxQueueWaitMs = limits.maxQueueWaitMs;
            this.taskDeadlineElapsedMs = taskDeadlineElapsedMs;
            this.activeSnapshot = activeSnapshot;
            validateState();
            this.decisionDigest = sha256(canonicalForm());
        }

        public DecisionCode getCode() {
            return code;
        }

        public SnapshotRejection getPolicySnapshotRejection() {
            return policySnapshotRejection;
        }

        public SnapshotRejection getResourceSnapshotRejection() {
            return resourceSnapshotRejection;
        }

        public SchedulerAdmission getSchedulerAdmission() {
            return schedulerAdmission;
        }

        public DegradationMode getDegradationMode() {
            return degradationMode;
        }

        public InferenceResourceScheduler.EffectivePriority getEffectivePriority() {
            return effectivePriority;
        }

        public String getRequestId() {
            return requestId;
        }

        public String getRequestFingerprint() {
            return requestFingerprint;
        }

        public String getTraceId() {
            return traceId;
        }

        public String getProviderId() {
            return providerId;
        }

        public String getRouteDecisionDigest() {
            return routeDecisionDigest;
        }

        public String getPolicySnapshotDigest() {
            return policySnapshotDigest;
        }

        public String getResourceSnapshotDigest() {
            return resourceSnapshotDigest;
        }

        public String getAdmissionContextDigest() {
            return admissionContextDigest;
        }

        public int getEffectiveInputTokenLimit() {
            return effectiveInputTokenLimit;
        }

        public int getEffectiveOutputTokenLimit() {
            return effectiveOutputTokenLimit;
        }

        public int getEffectiveTotalTokenLimit() {
            return effectiveTotalTokenLimit;
        }

        public long getEffectiveMaxQueueWaitMs() {
            return effectiveMaxQueueWaitMs;
        }

        public long getTaskDeadlineElapsedMs() {
            return taskDeadlineElapsedMs;
        }

        public InferenceResourceScheduler.ActiveSnapshot getActiveSnapshot() {
            return activeSnapshot;
        }

        public String getDecisionDigest() {
            return decisionDigest;
        }

        public boolean isAdmitted() {
            return code == DecisionCode.ADMITTED
                    || code == DecisionCode.ADMITTED_DEGRADED
                    || code == DecisionCode.REPLAYED
                    || code == DecisionCode.REPLAYED_DEGRADED;
        }

        public boolean isDegraded() {
            return degradationMode != DegradationMode.NONE;
        }

        public boolean isProviderInvoked() {
            return false;
        }

        public boolean isModelInvoked() {
            return false;
        }

        public boolean isActionAuthorizationGranted() {
            return false;
        }

        public boolean isEffectDispatchRequested() {
            return false;
        }

        public boolean isRuntimeWired() {
            return false;
        }

        public boolean isHardwareAccessed() {
            return false;
        }

        public boolean isProductionQualified() {
            return false;
        }

        private void validateState() {
            boolean schedulerSubmitted = schedulerAdmission != SchedulerAdmission.NOT_SUBMITTED;
            boolean expectedSchedulerSubmission = isAdmitted()
                    || code == DecisionCode.SCHEDULER_REJECTED;
            if (schedulerSubmitted != expectedSchedulerSubmission) {
                throw new IllegalArgumentException(
                        "CB_MODEL_RESOURCE: scheduler submission state does not agree");
            }
            if (isAdmitted() != (schedulerAdmission == SchedulerAdmission.ADMITTED
                    || schedulerAdmission == SchedulerAdmission.REPLAYED)) {
                throw new IllegalArgumentException(
                        "CB_MODEL_RESOURCE: decision and scheduler outcome do not agree");
            }
            if ((!schedulerSubmitted || !isAdmitted()) && activeSnapshot != null) {
                throw new IllegalArgumentException(
                        "CB_MODEL_RESOURCE: rejected decision cannot retain active state");
            }
            if (isAdmitted() && activeSnapshot == null) {
                throw new IllegalArgumentException(
                        "CB_MODEL_RESOURCE: admitted decision requires active state");
            }
            boolean admittedAsDegraded = code == DecisionCode.ADMITTED_DEGRADED
                    || code == DecisionCode.REPLAYED_DEGRADED;
            if ((isAdmitted() && isDegraded() != admittedAsDegraded)
                    || (!isAdmitted()
                            && code != DecisionCode.SCHEDULER_REJECTED
                            && isDegraded())) {
                throw new IllegalArgumentException(
                        "CB_MODEL_RESOURCE: degradation and decision code do not agree");
            }
        }

        private String canonicalForm() {
            return SCHEMA_VERSION
                    + "|" + code.name()
                    + "|" + policySnapshotRejection.name()
                    + "|" + resourceSnapshotRejection.name()
                    + "|" + schedulerAdmission.name()
                    + "|" + degradationMode.name()
                    + "|" + effectivePriority.name()
                    + "|" + requestId
                    + "|" + requestFingerprint
                    + "|" + traceId
                    + "|" + providerId
                    + "|" + routeDecisionDigest
                    + "|" + policySnapshotDigest
                    + "|" + resourceSnapshotDigest
                    + "|" + admissionContextDigest
                    + "|" + effectiveInputTokenLimit
                    + "|" + effectiveOutputTokenLimit
                    + "|" + effectiveTotalTokenLimit
                    + "|" + effectiveMaxQueueWaitMs
                    + "|" + taskDeadlineElapsedMs
                    + "|" + canonicalActiveSnapshot(activeSnapshot);
        }

        private static String canonicalActiveSnapshot(
                InferenceResourceScheduler.ActiveSnapshot snapshot) {
            if (snapshot == null) {
                return "none";
            }
            return snapshot.getRequestId()
                    + "|" + snapshot.getOwnerFingerprint()
                    + "|" + snapshot.getModelId()
                    + "|" + snapshot.getProviderId()
                    + "|" + snapshot.getEffectivePriority().name()
                    + "|" + snapshot.getState().name()
                    + "|" + (snapshot.getCancelReason() == null
                            ? "none"
                            : snapshot.getCancelReason().name())
                    + "|" + snapshot.getAdmittedAtElapsedRealtimeMs()
                    + "|" + snapshot.getQueueDeadlineElapsedRealtimeMs()
                    + "|" + snapshot.getTaskDeadlineElapsedRealtimeMs()
                    + "|" + snapshot.getStartedAtElapsedRealtimeMs();
        }
    }

    public static AdmissionDecision admit(
            ModelContractV2.ModelRequest request,
            PolicyAwareModelRouter.RouteDecision routeDecision,
            PolicyAwareModelRouter.PolicySnapshot policySnapshot,
            ResourceSnapshot resourceSnapshot,
            AdmissionContext admissionContext,
            InferenceResourceScheduler scheduler,
            long nowElapsedMs) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(routeDecision, "routeDecision");
        Objects.requireNonNull(policySnapshot, "policySnapshot");
        Objects.requireNonNull(resourceSnapshot, "resourceSnapshot");
        Objects.requireNonNull(admissionContext, "admissionContext");
        Objects.requireNonNull(scheduler, "scheduler");
        if (nowElapsedMs < 0) {
            throw violation("nowElapsedMs must not be negative");
        }

        InferenceResourceScheduler.EffectivePriority priority = priorityFor(
                admissionContext.getWorkloadClass());
        EffectiveLimits requestedLimits = limitsFor(request, priority, DegradationMode.NONE);
        long taskDeadline = saturatedAdd(
                nowElapsedMs, request.getLatencyBudget().getMaximumEndToEndMs());

        if (!routeDecision.isSelected()) {
            return rejected(
                    DecisionCode.ROUTE_NOT_SELECTED, SnapshotRejection.NONE,
                    SnapshotRejection.NONE, priority, request, routeDecision, policySnapshot,
                    resourceSnapshot, admissionContext, requestedLimits, taskDeadline);
        }
        if (!request.getRequestId().equals(routeDecision.getRequestId())
                || !request.getRequestFingerprint().equals(
                        routeDecision.getRequestFingerprint())
                || !request.getTraceId().equals(routeDecision.getTraceId())) {
            return rejected(
                    DecisionCode.REQUEST_ROUTE_MISMATCH, SnapshotRejection.NONE,
                    SnapshotRejection.NONE, priority, request, routeDecision, policySnapshot,
                    resourceSnapshot, admissionContext, requestedLimits, taskDeadline);
        }
        if (!policySnapshot.getSnapshotDigest().equals(
                        routeDecision.getPolicySnapshotDigest())
                || !policySnapshot.getSnapshotDigest().equals(
                        resourceSnapshot.getPolicySnapshotDigest())
                || !routeDecision.getPrimaryProviderId().equals(
                        resourceSnapshot.getProviderId())) {
            return rejected(
                    DecisionCode.POLICY_BINDING_MISMATCH, SnapshotRejection.NONE,
                    SnapshotRejection.NONE, priority, request, routeDecision, policySnapshot,
                    resourceSnapshot, admissionContext, requestedLimits, taskDeadline);
        }

        SnapshotRejection policyRejection = policyRejectionAt(policySnapshot, nowElapsedMs);
        if (policyRejection != SnapshotRejection.NONE) {
            return rejected(
                    DecisionCode.POLICY_SNAPSHOT_REJECTED, policyRejection,
                    SnapshotRejection.NONE, priority, request, routeDecision, policySnapshot,
                    resourceSnapshot, admissionContext, requestedLimits, taskDeadline);
        }
        SnapshotRejection resourceRejection = resourceSnapshot.rejectionAt(nowElapsedMs);
        if (resourceRejection != SnapshotRejection.NONE) {
            return rejected(
                    DecisionCode.RESOURCE_SNAPSHOT_REJECTED, SnapshotRejection.NONE,
                    resourceRejection, priority, request, routeDecision, policySnapshot,
                    resourceSnapshot, admissionContext, requestedLimits, taskDeadline);
        }
        if (!workloadMatchesPurpose(
                admissionContext.getWorkloadClass(), request.getPurpose())) {
            return rejected(
                    DecisionCode.WORKLOAD_PURPOSE_MISMATCH, SnapshotRejection.NONE,
                    SnapshotRejection.NONE, priority, request, routeDecision, policySnapshot,
                    resourceSnapshot, admissionContext, requestedLimits, taskDeadline);
        }

        DegradationMode degradation = degradationFor(
                admissionContext.getWorkloadClass(),
                request.getPurpose(),
                policySnapshot.getThermalState(),
                resourceSnapshot.getCapacityState());
        if (degradation == null) {
            DecisionCode rejection = thermalBlocks(
                    policySnapshot.getThermalState(),
                    admissionContext.getWorkloadClass(),
                    request.getPurpose())
                    ? DecisionCode.THERMAL_BLOCKED
                    : DecisionCode.RESOURCE_BLOCKED;
            return rejected(
                    rejection, SnapshotRejection.NONE, SnapshotRejection.NONE, priority,
                    request, routeDecision, policySnapshot, resourceSnapshot,
                    admissionContext, requestedLimits, taskDeadline);
        }

        EffectiveLimits effectiveLimits = limitsFor(request, priority, degradation);
        InferenceResourceScheduler.Admission schedulerResult = scheduler.admit(
                InferenceResourceScheduler.TrustedSubmission.fromRuntimePolicy(
                        request.getRequestId(),
                        admissionContext.getOwnerFingerprint(),
                        admissionContext.getModelId(),
                        routeDecision.getPrimaryProviderId(),
                        priority,
                        taskDeadline,
                        effectiveLimits.maxQueueWaitMs));
        SchedulerAdmission schedulerAdmission = SchedulerAdmission.valueOf(
                schedulerResult.getOutcome().name());
        DecisionCode code;
        if (schedulerAdmission == SchedulerAdmission.ADMITTED) {
            code = degradation == DegradationMode.NONE
                    ? DecisionCode.ADMITTED : DecisionCode.ADMITTED_DEGRADED;
        } else if (schedulerAdmission == SchedulerAdmission.REPLAYED) {
            code = degradation == DegradationMode.NONE
                    ? DecisionCode.REPLAYED : DecisionCode.REPLAYED_DEGRADED;
        } else {
            code = DecisionCode.SCHEDULER_REJECTED;
        }
        return new AdmissionDecision(
                code,
                SnapshotRejection.NONE,
                SnapshotRejection.NONE,
                schedulerAdmission,
                degradation,
                priority,
                request,
                routeDecision,
                policySnapshot,
                resourceSnapshot,
                admissionContext,
                effectiveLimits,
                taskDeadline,
                schedulerResult.getActive());
    }

    private static AdmissionDecision rejected(
            DecisionCode code,
            SnapshotRejection policyRejection,
            SnapshotRejection resourceRejection,
            InferenceResourceScheduler.EffectivePriority priority,
            ModelContractV2.ModelRequest request,
            PolicyAwareModelRouter.RouteDecision routeDecision,
            PolicyAwareModelRouter.PolicySnapshot policySnapshot,
            ResourceSnapshot resourceSnapshot,
            AdmissionContext admissionContext,
            EffectiveLimits limits,
            long taskDeadline) {
        return new AdmissionDecision(
                code,
                policyRejection,
                resourceRejection,
                SchedulerAdmission.NOT_SUBMITTED,
                DegradationMode.NONE,
                priority,
                request,
                routeDecision,
                policySnapshot,
                resourceSnapshot,
                admissionContext,
                limits,
                taskDeadline,
                null);
    }

    private static SnapshotRejection policyRejectionAt(
            PolicyAwareModelRouter.PolicySnapshot snapshot,
            long nowElapsedMs) {
        if (snapshot.getObservedAtElapsedMs() > nowElapsedMs) {
            return SnapshotRejection.FROM_FUTURE;
        }
        if (snapshot.getValidUntilElapsedMs() <= nowElapsedMs) {
            return SnapshotRejection.STALE;
        }
        return SnapshotRejection.NONE;
    }

    private static boolean workloadMatchesPurpose(
            WorkloadClass workloadClass,
            ModelContractV2.Purpose purpose) {
        switch (workloadClass) {
            case FOREGROUND_VEHICLE:
                return purpose == ModelContractV2.Purpose.SCENARIO_REASONING
                        || purpose == ModelContractV2.Purpose.SAFETY_CLASSIFICATION;
            case INTERACTIVE_COCKPIT:
                return purpose == ModelContractV2.Purpose.USER_DIALOGUE;
            case BACKGROUND_MAINTENANCE:
                return purpose == ModelContractV2.Purpose.CONTEXT_SUMMARY;
            default:
                throw new IllegalStateException("unknown workload class");
        }
    }

    private static InferenceResourceScheduler.EffectivePriority priorityFor(
            WorkloadClass workloadClass) {
        switch (workloadClass) {
            case FOREGROUND_VEHICLE:
                return InferenceResourceScheduler.EffectivePriority.HIGH;
            case INTERACTIVE_COCKPIT:
                return InferenceResourceScheduler.EffectivePriority.NORMAL;
            case BACKGROUND_MAINTENANCE:
                return InferenceResourceScheduler.EffectivePriority.BACKGROUND;
            default:
                throw new IllegalStateException("unknown workload class");
        }
    }

    private static DegradationMode degradationFor(
            WorkloadClass workloadClass,
            ModelContractV2.Purpose purpose,
            PolicyAwareModelRouter.ThermalState thermalState,
            CapacityState capacityState) {
        if (thermalState == PolicyAwareModelRouter.ThermalState.UNKNOWN
                || thermalState == PolicyAwareModelRouter.ThermalState.CRITICAL
                || capacityState == CapacityState.UNKNOWN
                || capacityState == CapacityState.EXHAUSTED) {
            return null;
        }
        if (thermalState == PolicyAwareModelRouter.ThermalState.HOT) {
            return workloadClass == WorkloadClass.FOREGROUND_VEHICLE
                            && purpose == ModelContractV2.Purpose.SAFETY_CLASSIFICATION
                            && capacityState != CapacityState.CONSTRAINED
                    ? DegradationMode.MINIMAL_SAFETY : null;
        }
        if (thermalState == PolicyAwareModelRouter.ThermalState.ELEVATED) {
            if (workloadClass == WorkloadClass.BACKGROUND_MAINTENANCE) {
                return null;
            }
            return DegradationMode.COMPACT_FOREGROUND;
        }
        if (capacityState == CapacityState.CONSTRAINED) {
            return workloadClass == WorkloadClass.FOREGROUND_VEHICLE
                    ? DegradationMode.COMPACT_FOREGROUND : null;
        }
        return DegradationMode.NONE;
    }

    private static boolean thermalBlocks(
            PolicyAwareModelRouter.ThermalState thermalState,
            WorkloadClass workloadClass,
            ModelContractV2.Purpose purpose) {
        if (thermalState == PolicyAwareModelRouter.ThermalState.UNKNOWN
                || thermalState == PolicyAwareModelRouter.ThermalState.CRITICAL) {
            return true;
        }
        if (thermalState == PolicyAwareModelRouter.ThermalState.HOT) {
            return workloadClass != WorkloadClass.FOREGROUND_VEHICLE
                    || purpose != ModelContractV2.Purpose.SAFETY_CLASSIFICATION;
        }
        return thermalState == PolicyAwareModelRouter.ThermalState.ELEVATED
                && workloadClass == WorkloadClass.BACKGROUND_MAINTENANCE;
    }

    private static EffectiveLimits limitsFor(
            ModelContractV2.ModelRequest request,
            InferenceResourceScheduler.EffectivePriority priority,
            DegradationMode degradation) {
        ModelContractV2.TokenBudget budget = request.getTokenBudget();
        int input = budget.getMaximumInputTokens();
        int output = budget.getMaximumOutputTokens();
        if (degradation == DegradationMode.COMPACT_FOREGROUND) {
            input = Math.min(input, COMPACT_INPUT_TOKEN_LIMIT);
            output = Math.min(output, COMPACT_OUTPUT_TOKEN_LIMIT);
        } else if (degradation == DegradationMode.MINIMAL_SAFETY) {
            input = Math.min(input, MINIMAL_SAFETY_INPUT_TOKEN_LIMIT);
            output = Math.min(output, MINIMAL_SAFETY_OUTPUT_TOKEN_LIMIT);
        }
        int total = Math.min(budget.getMaximumTotalTokens(), input + output);
        long queueCap;
        switch (priority) {
            case HIGH:
                queueCap = FOREGROUND_MAX_QUEUE_WAIT_MS;
                break;
            case NORMAL:
                queueCap = INTERACTIVE_MAX_QUEUE_WAIT_MS;
                break;
            case BACKGROUND:
                queueCap = BACKGROUND_MAX_QUEUE_WAIT_MS;
                break;
            default:
                throw new IllegalStateException("unknown effective priority");
        }
        return new EffectiveLimits(
                input,
                output,
                total,
                Math.min(queueCap, request.getLatencyBudget().getMaximumEndToEndMs()));
    }

    private static long saturatedAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static String requireIdentifier(String value, String field) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw violation(field + " is invalid");
        }
        return value;
    }

    private static String requireDigest(String value, String field) {
        if (value == null || !DIGEST.matcher(value).matches()) {
            throw violation(field + " is invalid");
        }
        return value;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(encoded.length * 2);
            for (byte item : encoded) {
                builder.append(String.format("%02x", item & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("CB_MODEL_RESOURCE: SHA-256 unavailable", exception);
        }
    }

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_MODEL_RESOURCE: " + message);
    }

    private static final class EffectiveLimits {
        final int inputTokens;
        final int outputTokens;
        final int totalTokens;
        final long maxQueueWaitMs;

        EffectiveLimits(
                int inputTokens,
                int outputTokens,
                int totalTokens,
                long maxQueueWaitMs) {
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
            this.totalTokens = totalTokens;
            this.maxQueueWaitMs = maxQueueWaitMs;
        }
    }
}
