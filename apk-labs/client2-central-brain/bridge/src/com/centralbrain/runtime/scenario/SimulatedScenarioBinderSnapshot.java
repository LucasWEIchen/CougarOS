package com.centralbrain.runtime.scenario;

import android.os.Parcel;
import android.os.Parcelable;

/** Client-side wire DTO for the debug-only simulated scenario Binder v2. */
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

    @Override
    public int describeContents() {
        return 0;
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

    private static String nonNull(String value) {
        return value == null ? "" : value;
    }
}
