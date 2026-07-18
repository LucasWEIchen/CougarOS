package com.centralbrain.runtime.scenario;

import android.os.Parcel;
import android.os.Parcelable;

/** Parcelable debug metadata view. It carries no raw request, Context or vehicle payload. */
public final class SimulatedScenarioBinderSnapshot implements Parcelable {
    public int schemaVersion;
    public String runId = "";
    public String sessionId = "";
    public String scenarioId = "";
    public String planDigest = "";
    public int planRevision;
    public int sessionState;
    public long graphRevision;
    public int automaticProjectionCount;
    public int suppliedOutcomeCount;
    public int pendingStage;
    public String pendingNodeId = "";
    public String pendingCapabilityId = "";
    public long lastEventSequence;
    public int projectedEventCount;
    public String projectionDigest = "";
    public int simulatedEffectDispatchCount;
    public int simulatedReadbackAttemptCount;
    public int simulatedReadbackMatchCount;
    public int simulatedApprovalInputCount;
    public int simulatedFailureCount;
    public boolean effectDispatchEnabled;
    public boolean readbackAccessed;
    public boolean approvalAuthorityAvailable;
    public boolean hardwareAccessed;
    public boolean productionReady;
    public boolean targetHardwareValidated;

    public SimulatedScenarioBinderSnapshot() {
        schemaVersion = 2;
    }

    private SimulatedScenarioBinderSnapshot(Parcel input) {
        schemaVersion = input.readInt();
        runId = nonNull(input.readString());
        sessionId = nonNull(input.readString());
        scenarioId = nonNull(input.readString());
        planDigest = nonNull(input.readString());
        planRevision = input.readInt();
        sessionState = input.readInt();
        graphRevision = input.readLong();
        automaticProjectionCount = input.readInt();
        suppliedOutcomeCount = input.readInt();
        pendingStage = input.readInt();
        pendingNodeId = nonNull(input.readString());
        pendingCapabilityId = nonNull(input.readString());
        lastEventSequence = input.readLong();
        projectedEventCount = input.readInt();
        projectionDigest = nonNull(input.readString());
        simulatedEffectDispatchCount = input.readInt();
        simulatedReadbackAttemptCount = input.readInt();
        simulatedReadbackMatchCount = input.readInt();
        simulatedApprovalInputCount = input.readInt();
        simulatedFailureCount = input.readInt();
        effectDispatchEnabled = input.readInt() != 0;
        readbackAccessed = input.readInt() != 0;
        approvalAuthorityAvailable = input.readInt() != 0;
        hardwareAccessed = input.readInt() != 0;
        productionReady = input.readInt() != 0;
        targetHardwareValidated = input.readInt() != 0;
    }

    static SimulatedScenarioBinderSnapshot from(SimulatedScenarioRuntime.Snapshot source) {
        SimulatedScenarioBinderSnapshot result = new SimulatedScenarioBinderSnapshot();
        result.schemaVersion = 1;
        result.runId = source.getRunId();
        result.sessionId = source.getSessionId();
        result.scenarioId = source.getScenarioId();
        result.planDigest = source.getPlanDigest();
        result.planRevision = source.getPlanRevision();
        result.sessionState = sessionState(source.getSessionState());
        result.graphRevision = source.getGraphRevision();
        result.automaticProjectionCount = source.getAutomaticProjectionCount();
        result.suppliedOutcomeCount = source.getSuppliedOutcomeCount();
        result.pendingStage = pendingStage(source.getPendingNode());
        result.pendingNodeId = source.getPendingNode() == null
                ? "" : source.getPendingNode().getNodeId();
        result.pendingCapabilityId = source.getPendingNode() == null
                ? "" : source.getPendingNode().getCapabilityId();
        result.lastEventSequence = source.getLastEventSequence();
        result.projectedEventCount = source.getProjectedEventCount();
        result.projectionDigest = source.getProjectionDigest();
        result.effectDispatchEnabled = source.isEffectDispatchEnabled();
        result.readbackAccessed = source.isReadbackAccessed();
        result.approvalAuthorityAvailable = source.isApprovalAuthorityAvailable();
        result.hardwareAccessed = source.isHardwareAccessed();
        result.productionReady = source.isProductionReady();
        result.targetHardwareValidated = source.isTargetHardwareValidated();
        return result;
    }

    static SimulatedScenarioBinderSnapshot from(
            SimulatedScenarioEffectComposition.Snapshot source) {
        SimulatedScenarioBinderSnapshot result = from(source.getRuntimeSnapshot());
        result.schemaVersion = 2;
        result.projectionDigest = source.getProjectionDigest();
        result.simulatedEffectDispatchCount = source.getEffectDispatchCount();
        result.simulatedReadbackAttemptCount = source.getReadbackAttemptCount();
        result.simulatedReadbackMatchCount = source.getReadbackMatchCount();
        result.simulatedApprovalInputCount = source.getApprovalInputCount();
        result.simulatedFailureCount = source.getFailureCount();
        result.effectDispatchEnabled = source.isSimulatedEffectDispatchEnabled();
        result.readbackAccessed = source.isReadbackAccessed();
        result.approvalAuthorityAvailable = source.isApprovalAuthorityAvailable();
        result.hardwareAccessed = source.isHardwareAccessed();
        result.productionReady = source.isProductionReady();
        result.targetHardwareValidated = source.isTargetHardwareValidated();
        return result;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel output, int flags) {
        output.writeInt(schemaVersion);
        output.writeString(runId);
        output.writeString(sessionId);
        output.writeString(scenarioId);
        output.writeString(planDigest);
        output.writeInt(planRevision);
        output.writeInt(sessionState);
        output.writeLong(graphRevision);
        output.writeInt(automaticProjectionCount);
        output.writeInt(suppliedOutcomeCount);
        output.writeInt(pendingStage);
        output.writeString(pendingNodeId);
        output.writeString(pendingCapabilityId);
        output.writeLong(lastEventSequence);
        output.writeInt(projectedEventCount);
        output.writeString(projectionDigest);
        output.writeInt(simulatedEffectDispatchCount);
        output.writeInt(simulatedReadbackAttemptCount);
        output.writeInt(simulatedReadbackMatchCount);
        output.writeInt(simulatedApprovalInputCount);
        output.writeInt(simulatedFailureCount);
        output.writeInt(effectDispatchEnabled ? 1 : 0);
        output.writeInt(readbackAccessed ? 1 : 0);
        output.writeInt(approvalAuthorityAvailable ? 1 : 0);
        output.writeInt(hardwareAccessed ? 1 : 0);
        output.writeInt(productionReady ? 1 : 0);
        output.writeInt(targetHardwareValidated ? 1 : 0);
    }

    public static final Creator<SimulatedScenarioBinderSnapshot> CREATOR =
            new Creator<SimulatedScenarioBinderSnapshot>() {
                @Override
                public SimulatedScenarioBinderSnapshot createFromParcel(Parcel input) {
                    return new SimulatedScenarioBinderSnapshot(input);
                }

                @Override
                public SimulatedScenarioBinderSnapshot[] newArray(int size) {
                    return new SimulatedScenarioBinderSnapshot[size];
                }
            };

    private static int sessionState(SimulatedScenarioRuntime.SessionState state) {
        switch (state) {
            case WAITING_APPROVAL:
                return ISimulatedScenarioRuntime.SESSION_WAITING_APPROVAL;
            case WAITING_EFFECT:
                return ISimulatedScenarioRuntime.SESSION_WAITING_EFFECT;
            case WAITING_READBACK:
                return ISimulatedScenarioRuntime.SESSION_WAITING_READBACK;
            case COMPLETED:
                return ISimulatedScenarioRuntime.SESSION_COMPLETED;
            case PARTIAL:
                return ISimulatedScenarioRuntime.SESSION_PARTIAL;
            case FAILED:
                return ISimulatedScenarioRuntime.SESSION_FAILED;
            case CANCELLED:
                return ISimulatedScenarioRuntime.SESSION_CANCELLED;
            case STUCK:
                return ISimulatedScenarioRuntime.SESSION_STUCK;
            default:
                throw new IllegalArgumentException("unknown simulated session state");
        }
    }

    private static int pendingStage(SimulatedScenarioGraph.PendingNode pending) {
        if (pending == null) {
            return ISimulatedScenarioRuntime.PENDING_NONE;
        }
        switch (pending.getStage()) {
            case APPROVAL:
                return ISimulatedScenarioRuntime.PENDING_APPROVAL;
            case EFFECT:
                return ISimulatedScenarioRuntime.PENDING_EFFECT;
            case READBACK:
                return ISimulatedScenarioRuntime.PENDING_READBACK;
            default:
                return ISimulatedScenarioRuntime.PENDING_NONE;
        }
    }

    private static String nonNull(String value) {
        return value == null ? "" : value;
    }
}
