package com.centralbrain.client2;

import com.centralbrain.sdk.event.ActionEvent;
import com.centralbrain.sdk.event.EventContract;
import com.centralbrain.sdk.event.ObservationEvent;
import com.centralbrain.sdk.event.RuntimeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable, evidence-preserving projection of the AIOS execution chain. */
public final class CockpitExecutionTimeline {
    public static final int MAX_TRACE_ITEMS = 8;

    public enum Phase { INTENT, CONTEXT, PLAN, POLICY, GRAPH, EFFECT, READBACK }

    public enum Status {
        WAITING,
        REQUESTED,
        SESSION_ACCEPTED,
        CAPTURED,
        PUBLISHED,
        COMPILED,
        ACTIVE,
        PROPOSED,
        AUTHORIZED,
        REJECTED,
        APPROVAL_REQUIRED,
        APPROVAL_RESOLVED,
        PREPARED,
        DISPATCHED,
        APPLIED,
        VERIFIED,
        FAILED,
        SKIPPED,
        COMPENSATING,
        COMPENSATED,
        MISMATCH,
        UNAVAILABLE,
        NOT_PUBLISHED,
        NOT_WIRED,
        NOT_DISPATCHED,
        NO_EVIDENCE
    }

    public static final class Stage {
        private final Phase phase;
        private final Status status;
        private final String target;
        private final String source;
        private final String result;

        private Stage(Phase phase, Status status, String target, String source, String result) {
            this.phase = Objects.requireNonNull(phase, "phase");
            this.status = Objects.requireNonNull(status, "status");
            this.target = bounded(target, 96, "target");
            this.source = bounded(source, 32, "source");
            this.result = bounded(result, 96, "result");
        }

        public Phase getPhase() {
            return phase;
        }

        public Status getStatus() {
            return status;
        }

        public String getTarget() {
            return target;
        }

        public String getSource() {
            return source;
        }

        public String getResult() {
            return result;
        }
    }

    public static final class TraceItem {
        private final long sequence;
        private final String eventType;
        private final Status status;
        private final String target;
        private final String source;
        private final String result;

        private TraceItem(
                long sequence,
                String eventType,
                Status status,
                String target,
                String source,
                String result) {
            if (sequence < 1) {
                throw new IllegalArgumentException("trace sequence must be positive");
            }
            this.sequence = sequence;
            this.eventType = bounded(eventType, 64, "eventType");
            this.status = Objects.requireNonNull(status, "status");
            this.target = bounded(target, 96, "target");
            this.source = bounded(source, 32, "source");
            this.result = bounded(result, 96, "result");
        }

        public long getSequence() {
            return sequence;
        }

        public String getEventType() {
            return eventType;
        }

        public Status getStatus() {
            return status;
        }

        public String getTarget() {
            return target;
        }

        public String getSource() {
            return source;
        }

        public String getResult() {
            return result;
        }
    }

    /** Defensive, text-free projection built while the validated AIDL event is in scope. */
    static final class ProjectedEvent {
        final long sequence;
        final String type;
        final String source;
        final String target;
        final String result;
        final boolean optionalAction;
        final int observationOutcome;
        final int observationQuality;

        private ProjectedEvent(
                long sequence,
                String type,
                String source,
                String target,
                String result,
                boolean optionalAction,
                int observationOutcome,
                int observationQuality) {
            this.sequence = sequence;
            this.type = bounded(type, 64, "type");
            this.source = bounded(source, 32, "source");
            this.target = bounded(target, 96, "target");
            this.result = bounded(result, 96, "result");
            this.optionalAction = optionalAction;
            this.observationOutcome = observationOutcome;
            this.observationQuality = observationQuality;
        }

        static ProjectedEvent from(RuntimeEvent event) {
            EventContract.validateEvent(event);
            String target = "TYPED_EVENT";
            String result = event.type;
            boolean optional = false;
            int outcome = 0;
            int quality = 0;
            ActionEvent action = event.action;
            ObservationEvent observation = event.observation;
            if (event.payloadKind == EventContract.PAYLOAD_ACTION && action != null) {
                target = action.capabilityId;
                result = actionState(action.state);
                optional = !action.required;
            } else if (event.payloadKind == EventContract.PAYLOAD_OBSERVATION
                    && observation != null) {
                target = subjectType(observation.subjectType);
                result = observationOutcome(observation.outcome)
                        + "/" + observationQuality(observation.quality);
                outcome = observation.outcome;
                quality = observation.quality;
            }
            return new ProjectedEvent(
                    event.sequence,
                    event.type,
                    eventSource(event.source),
                    target,
                    result,
                    optional,
                    outcome,
                    quality);
        }
    }

    private final Map<Phase, Stage> stages;
    private final List<TraceItem> traceItems;
    private final int sessionState;
    private final long lastSequence;

    private CockpitExecutionTimeline(
            Map<Phase, Stage> stages,
            List<TraceItem> traceItems,
            int sessionState,
            long lastSequence) {
        EnumMap<Phase, Stage> copy = new EnumMap<>(Phase.class);
        copy.putAll(stages);
        for (Phase phase : Phase.values()) {
            Stage stage = copy.get(phase);
            if (stage == null || stage.phase != phase) {
                throw new IllegalArgumentException("execution timeline phase missing: " + phase);
            }
        }
        if (traceItems.size() > MAX_TRACE_ITEMS) {
            throw new IllegalArgumentException("execution trace exceeds capacity");
        }
        this.stages = Collections.unmodifiableMap(copy);
        this.traceItems = Collections.unmodifiableList(new ArrayList<>(traceItems));
        this.sessionState = Math.max(0, sessionState);
        this.lastSequence = Math.max(0, lastSequence);
    }

    public static CockpitExecutionTimeline initial() {
        EnumMap<Phase, Stage> stages = new EnumMap<>(Phase.class);
        stages.put(Phase.INTENT, stage(Phase.INTENT, Status.WAITING,
                "NONE", "HMI", "NO_REQUEST"));
        stages.put(Phase.CONTEXT, stage(Phase.CONTEXT, Status.UNAVAILABLE,
                "VEHICLE_CONTEXT", "UNAVAILABLE", "UNKNOWN_RESTRICTED"));
        stages.put(Phase.PLAN, stage(Phase.PLAN, Status.NOT_PUBLISHED,
                "PLAN", "UNAVAILABLE", "NO_PLAN_REVISION"));
        stages.put(Phase.POLICY, stage(Phase.POLICY, Status.WAITING,
                "GOVERNANCE", "RUNTIME", "NO_ACTION"));
        stages.put(Phase.GRAPH, stage(Phase.GRAPH, Status.NOT_WIRED,
                "AGENT_GRAPH", "UNAVAILABLE", "NO_RUNTIME_WIRING"));
        stages.put(Phase.EFFECT, stage(Phase.EFFECT, Status.NOT_DISPATCHED,
                "VEHICLE_EFFECT", "UNAVAILABLE", "NO_DISPATCH"));
        stages.put(Phase.READBACK, stage(Phase.READBACK, Status.UNAVAILABLE,
                "DEVICE_OBSERVATION", "UNAVAILABLE", "NO_EVIDENCE"));
        return new CockpitExecutionTimeline(stages, Collections.emptyList(), 0, 0);
    }

    public Stage getStage(Phase phase) {
        return stages.get(Objects.requireNonNull(phase, "phase"));
    }

    public List<TraceItem> getTraceItems() {
        return traceItems;
    }

    public int getSessionState() {
        return sessionState;
    }

    public long getLastSequence() {
        return lastSequence;
    }

    CockpitExecutionTimeline scenarioRequested(String scenarioId) {
        CockpitExecutionTimeline reset = initial();
        return reset.replace(Phase.INTENT, Status.REQUESTED,
                bounded(scenarioId, 96, "scenarioId"), "HMI", "SESSION_PENDING");
    }

    CockpitExecutionTimeline sessionOpened(String canonicalScenarioId) {
        CockpitExecutionTimeline next = replace(
                Phase.INTENT,
                Status.SESSION_ACCEPTED,
                bounded(canonicalScenarioId, 96, "canonicalScenarioId"),
                "RUNTIME",
                "ADMISSION_ONLY");
        return next.replace(
                Phase.POLICY,
                Status.SESSION_ACCEPTED,
                "SESSION_ADMISSION",
                "RUNTIME",
                "NO_ACTION_AUTHORITY");
    }

    CockpitExecutionTimeline snapshot(int state, int activePlanRevision) {
        CockpitExecutionTimeline next = new CockpitExecutionTimeline(
                stages, traceItems, state, lastSequence);
        if (activePlanRevision > 0) {
            next = next.replace(
                    Phase.PLAN,
                    Status.PUBLISHED,
                    "PLAN_REV_" + activePlanRevision,
                    "RUNTIME",
                    "TYPED_SNAPSHOT");
        }
        return next;
    }

    CockpitExecutionTimeline simulatedScenario(CockpitSimulatedScenarioState simulated) {
        Objects.requireNonNull(simulated, "simulated");
        if (!simulated.hasScenario()) {
            return this;
        }
        CockpitExecutionTimeline next = this;
        if (!simulated.hasSnapshot()) {
            if (simulated.getLifecycle() == CockpitSimulatedScenarioState.Lifecycle.FAILED) {
                next = next.replace(
                        Phase.GRAPH,
                        Status.FAILED,
                        "DEBUG_SCENARIO_BINDER",
                        "RUNTIME_DEBUG",
                        simulated.getFailureCode());
            }
            return next;
        }

        next = next.replace(
                Phase.INTENT,
                Status.SESSION_ACCEPTED,
                simulated.getCanonicalScenarioId(),
                "RUNTIME_DEBUG",
                "FIXED_SCENARIO");
        next = next.replace(
                Phase.CONTEXT,
                Status.CAPTURED,
                "BUILD_OWNED_CONTEXT",
                "SIMULATED",
                simulated.getDrivingProfile());
        next = next.replace(
                Phase.PLAN,
                Status.PUBLISHED,
                "PLAN_REV_" + simulated.getPlanRevision(),
                "RUNTIME_DEBUG",
                "GRAPH_REV_" + simulated.getGraphRevision());

        CockpitExecutionTimeline.Status policyStatus =
                simulated.getLifecycle()
                        == CockpitSimulatedScenarioState.Lifecycle.WAITING_APPROVAL
                        ? Status.APPROVAL_REQUIRED : Status.ACTIVE;
        String target = simulated.getPendingCapabilityId().isEmpty()
                ? "FIXED_SCENARIO_TARGETS" : simulated.getPendingCapabilityId();
        next = next.replace(
                Phase.POLICY,
                policyStatus,
                target,
                "RUNTIME_DEBUG",
                "SIMULATION_ONLY");
        next = next.replace(
                Phase.GRAPH,
                graphStatus(simulated.getLifecycle()),
                target,
                "GRAPH_DEBUG",
                simulated.getLifecycle().name());
        next = next.replace(
                Phase.EFFECT,
                effectStatus(simulated),
                "FIXED_SCENARIO_TARGETS",
                "SIMULATED_ADAPTER",
                "DISPATCH_COUNT_" + simulated.getEffectDispatchCount());
        return next.replace(
                Phase.READBACK,
                readbackStatus(simulated),
                "SIMULATED_OBSERVATION",
                "SIMULATED_ADAPTER",
                "MATCH_" + simulated.getReadbackMatchCount()
                        + "_OF_" + simulated.getReadbackAttemptCount());
    }

    CockpitExecutionTimeline runtimeEvent(ProjectedEvent event) {
        Objects.requireNonNull(event, "event");
        if (event.sequence <= lastSequence) {
            return this;
        }
        CockpitExecutionTimeline next = this;
        Status status = statusFor(event);
        String target = targetFor(event);
        switch (event.type) {
            case "UserMessageReceived":
            case "ScenarioRequested":
                if (getStage(Phase.INTENT).getStatus() != Status.SESSION_ACCEPTED) {
                    next = next.replace(Phase.INTENT, Status.REQUESTED,
                            event.target, event.source, event.result);
                }
                break;
            case "ContextCaptured":
                next = next.replace(Phase.CONTEXT, Status.CAPTURED,
                        "VEHICLE_CONTEXT", event.source, event.result);
                break;
            case "PlanCompiled":
                next = next.replace(Phase.PLAN, Status.COMPILED,
                        "TYPED_PLAN", event.source, event.result);
                break;
            case "ActionProposed":
            case "ActionAuthorized":
            case "ActionRejected":
            case "ApprovalRequested":
            case "ApprovalResolved":
            case "ApprovalExpired":
                next = next.replace(Phase.POLICY, status,
                        target, event.source, event.result);
                next = next.replace(Phase.GRAPH, Status.ACTIVE,
                        target, "GRAPH", "TYPED_EVENT_ONLY");
                break;
            case "EffectPrepared":
            case "EffectDispatched":
            case "EffectObserved":
            case "EffectVerified":
            case "EffectFailed":
            case "CompensationStarted":
            case "CompensationObserved":
                next = next.replace(Phase.EFFECT, status,
                        target, event.source, event.result);
                if ("EffectObserved".equals(event.type)
                        || "EffectVerified".equals(event.type)
                        || "EffectFailed".equals(event.type)
                        || "CompensationObserved".equals(event.type)) {
                    next = next.replace(Phase.READBACK, status,
                            target, event.source, event.result);
                }
                break;
            default:
                break;
        }
        return next.appendTrace(event, status, target);
    }

    private CockpitExecutionTimeline replace(
            Phase phase,
            Status status,
            String target,
            String source,
            String result) {
        EnumMap<Phase, Stage> updated = new EnumMap<>(Phase.class);
        updated.putAll(stages);
        updated.put(phase, stage(phase, status, target, source, result));
        return new CockpitExecutionTimeline(updated, traceItems, sessionState, lastSequence);
    }

    private CockpitExecutionTimeline appendTrace(
            ProjectedEvent event, Status status, String target) {
        ArrayList<TraceItem> updated = new ArrayList<>(traceItems);
        updated.add(new TraceItem(
                event.sequence,
                event.type,
                status,
                target,
                event.source,
                event.result));
        while (updated.size() > MAX_TRACE_ITEMS) {
            updated.remove(0);
        }
        return new CockpitExecutionTimeline(stages, updated, sessionState, event.sequence);
    }

    private String targetFor(ProjectedEvent event) {
        if (!"TYPED_EVENT".equals(event.target) && !event.target.endsWith("_EVIDENCE")) {
            return event.target;
        }
        if (event.type.startsWith("Approval")) {
            String graphTarget = getStage(Phase.GRAPH).getTarget();
            if (!"AGENT_GRAPH".equals(graphTarget)) {
                return graphTarget;
            }
            String policyTarget = getStage(Phase.POLICY).getTarget();
            if (!"GOVERNANCE".equals(policyTarget)
                    && !"SESSION_ADMISSION".equals(policyTarget)) {
                return policyTarget;
            }
        } else if (event.type.startsWith("Effect")) {
            String effectTarget = getStage(Phase.EFFECT).getTarget();
            if (!"VEHICLE_EFFECT".equals(effectTarget)) {
                return effectTarget;
            }
            String graphTarget = getStage(Phase.GRAPH).getTarget();
            if (!"AGENT_GRAPH".equals(graphTarget)) {
                return graphTarget;
            }
        } else if (event.type.startsWith("Compensation")) {
            String effectTarget = getStage(Phase.EFFECT).getTarget();
            if (!"VEHICLE_EFFECT".equals(effectTarget)) {
                return effectTarget;
            }
        }
        return event.target;
    }

    private static Status statusFor(ProjectedEvent event) {
        switch (event.type) {
            case "ActionProposed":
                return Status.PROPOSED;
            case "ActionAuthorized":
                return Status.AUTHORIZED;
            case "ActionRejected":
                return event.optionalAction ? Status.SKIPPED : Status.REJECTED;
            case "ApprovalRequested":
                return Status.APPROVAL_REQUIRED;
            case "ApprovalResolved":
                return Status.APPROVAL_RESOLVED;
            case "ApprovalExpired":
                return Status.FAILED;
            case "EffectPrepared":
                return Status.PREPARED;
            case "EffectDispatched":
                return Status.DISPATCHED;
            case "EffectVerified":
                return observationStatus(event);
            case "EffectFailed":
                return Status.FAILED;
            case "EffectObserved":
                return observationStatus(event);
            case "CompensationStarted":
                return Status.COMPENSATING;
            case "CompensationObserved":
                if ((event.observationOutcome == EventContract.OUTCOME_OBSERVED
                                || event.observationOutcome == EventContract.OUTCOME_VERIFIED)
                        && event.observationQuality == EventContract.QUALITY_FRESH) {
                    return Status.COMPENSATED;
                }
                return observationStatus(event);
            case "ContextCaptured":
                return Status.CAPTURED;
            case "PlanCompiled":
                return Status.COMPILED;
            case "ScenarioRequested":
            case "UserMessageReceived":
                return Status.REQUESTED;
            default:
                return Status.ACTIVE;
        }
    }

    private static Status observationStatus(ProjectedEvent event) {
        switch (event.observationOutcome) {
            case EventContract.OUTCOME_OBSERVED:
                if (event.observationQuality == EventContract.QUALITY_FRESH) {
                    return Status.APPLIED;
                }
                if (event.observationQuality == EventContract.QUALITY_CONFLICT) {
                    return Status.MISMATCH;
                }
                return event.observationQuality == EventContract.QUALITY_UNAVAILABLE
                        ? Status.UNAVAILABLE : Status.NO_EVIDENCE;
            case EventContract.OUTCOME_VERIFIED:
                if (event.observationQuality == EventContract.QUALITY_FRESH) {
                    return Status.VERIFIED;
                }
                if (event.observationQuality == EventContract.QUALITY_CONFLICT) {
                    return Status.MISMATCH;
                }
                return event.observationQuality == EventContract.QUALITY_UNAVAILABLE
                        ? Status.UNAVAILABLE : Status.NO_EVIDENCE;
            case EventContract.OUTCOME_MISMATCH:
                return Status.MISMATCH;
            case EventContract.OUTCOME_FAILED:
                return Status.FAILED;
            case EventContract.OUTCOME_UNAVAILABLE:
            default:
                return Status.UNAVAILABLE;
        }
    }

    private static Status graphStatus(CockpitSimulatedScenarioState.Lifecycle lifecycle) {
        switch (lifecycle) {
            case COMPLETED:
                return Status.VERIFIED;
            case PARTIAL:
            case CANCELLED:
                return Status.SKIPPED;
            case FAILED:
            case STUCK:
                return Status.FAILED;
            case WAITING_APPROVAL:
            case RUNNING:
            case CONNECTING:
            default:
                return Status.ACTIVE;
        }
    }

    private static Status effectStatus(CockpitSimulatedScenarioState state) {
        if (state.getEffectDispatchCount() == 0) {
            return Status.NOT_DISPATCHED;
        }
        return state.getFailureCount() > 0 ? Status.FAILED : Status.APPLIED;
    }

    private static Status readbackStatus(CockpitSimulatedScenarioState state) {
        if (state.getReadbackAttemptCount() == 0) {
            return Status.NO_EVIDENCE;
        }
        if (state.getFailureCount() > 0) {
            return Status.FAILED;
        }
        return state.getReadbackMatchCount() == state.getReadbackAttemptCount()
                ? Status.VERIFIED : Status.MISMATCH;
    }

    private static Stage stage(
            Phase phase, Status status, String target, String source, String result) {
        return new Stage(phase, status, target, source, result);
    }

    private static String eventSource(int source) {
        switch (source) {
            case EventContract.SOURCE_USER:
                return "USER";
            case EventContract.SOURCE_HMI:
                return "HMI";
            case EventContract.SOURCE_RUNTIME:
                return "RUNTIME";
            case EventContract.SOURCE_GOVERNANCE:
                return "GOVERNANCE";
            case EventContract.SOURCE_SCENARIO:
                return "SCENARIO";
            case EventContract.SOURCE_GRAPH:
                return "GRAPH";
            case EventContract.SOURCE_ADAPTER:
                return "ADAPTER";
            case EventContract.SOURCE_MODEL:
                return "MODEL";
            case EventContract.SOURCE_TOOL:
                return "TOOL";
            case EventContract.SOURCE_SYSTEM:
            default:
                return "SYSTEM";
        }
    }

    private static String actionState(int state) {
        switch (state) {
            case EventContract.ACTION_PROPOSED:
                return "PROPOSED";
            case EventContract.ACTION_AUTHORIZED:
                return "AUTHORIZED";
            case EventContract.ACTION_REJECTED:
            default:
                return "REJECTED";
        }
    }

    private static String subjectType(int subjectType) {
        switch (subjectType) {
            case EventContract.SUBJECT_CONTEXT:
                return "CONTEXT_EVIDENCE";
            case EventContract.SUBJECT_PLAN:
                return "PLAN_EVIDENCE";
            case EventContract.SUBJECT_ACTION:
                return "ACTION_EVIDENCE";
            case EventContract.SUBJECT_EFFECT:
                return "EFFECT_EVIDENCE";
            case EventContract.SUBJECT_TOOL:
                return "TOOL_EVIDENCE";
            case EventContract.SUBJECT_MODEL:
                return "MODEL_EVIDENCE";
            case EventContract.SUBJECT_COMPENSATION:
            default:
                return "COMPENSATION_EVIDENCE";
        }
    }

    private static String observationOutcome(int outcome) {
        switch (outcome) {
            case EventContract.OUTCOME_OBSERVED:
                return "OBSERVED";
            case EventContract.OUTCOME_VERIFIED:
                return "VERIFIED";
            case EventContract.OUTCOME_MISMATCH:
                return "MISMATCH";
            case EventContract.OUTCOME_UNAVAILABLE:
                return "UNAVAILABLE";
            case EventContract.OUTCOME_FAILED:
            default:
                return "FAILED";
        }
    }

    private static String observationQuality(int quality) {
        switch (quality) {
            case EventContract.QUALITY_FRESH:
                return "FRESH";
            case EventContract.QUALITY_STALE:
                return "STALE";
            case EventContract.QUALITY_CONFLICT:
                return "CONFLICT";
            case EventContract.QUALITY_UNAVAILABLE:
            default:
                return "UNAVAILABLE";
        }
    }

    private static String bounded(String value, int max, String field) {
        if (value == null || value.length() > max) {
            throw new IllegalArgumentException("invalid timeline " + field);
        }
        return value;
    }
}
