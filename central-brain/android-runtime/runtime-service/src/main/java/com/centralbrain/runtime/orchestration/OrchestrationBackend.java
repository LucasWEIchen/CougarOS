package com.centralbrain.runtime.orchestration;

import com.centralbrain.sdk.orchestration.ApprovalResponse;
import com.centralbrain.sdk.orchestration.OrchestrationSnapshot;
import com.centralbrain.sdk.orchestration.OrchestrationStartRequest;
import com.centralbrain.sdk.orchestration.UndoRequest;
import com.centralbrain.sdk.plan.ScenarioPlan;

import java.util.Objects;

/** Build-variant orchestration backend. Production authority remains outside this interface. */
public interface OrchestrationBackend extends AutoCloseable {
    Result start(SessionDescriptor session, OrchestrationStartRequest request);

    Result get(SessionDescriptor session);

    Result respondToApproval(SessionDescriptor session, ApprovalResponse response);

    Result requestUndo(SessionDescriptor session, UndoRequest request);

    Result cancel(SessionDescriptor session, int reasonCode);

    @Override
    default void close() {}

    final class SessionDescriptor {
        private final String ownerFingerprint;
        private final String sessionId;
        private final String scenarioId;
        private final int sessionState;
        private final int activePlanRevision;
        private final long deadlineEpochMs;
        private final long revision;

        public SessionDescriptor(
                String ownerFingerprint,
                String sessionId,
                String scenarioId,
                int sessionState,
                int activePlanRevision,
                long deadlineEpochMs,
                long revision) {
            this.ownerFingerprint = Objects.requireNonNull(ownerFingerprint, "ownerFingerprint");
            this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
            this.scenarioId = Objects.requireNonNull(scenarioId, "scenarioId");
            this.sessionState = sessionState;
            this.activePlanRevision = activePlanRevision;
            this.deadlineEpochMs = deadlineEpochMs;
            this.revision = revision;
        }

        public String getOwnerFingerprint() { return ownerFingerprint; }
        public String getSessionId() { return sessionId; }
        public String getScenarioId() { return scenarioId; }
        public int getSessionState() { return sessionState; }
        public int getActivePlanRevision() { return activePlanRevision; }
        public long getDeadlineEpochMs() { return deadlineEpochMs; }
        public long getRevision() { return revision; }
    }

    final class Result {
        private final OrchestrationSnapshot snapshot;
        private final ScenarioPlan plan;
        private final String manifestDigest;

        public Result(
                OrchestrationSnapshot snapshot,
                ScenarioPlan plan,
                String manifestDigest) {
            this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
            this.plan = plan;
            this.manifestDigest = manifestDigest == null ? "" : manifestDigest;
        }

        public OrchestrationSnapshot getSnapshot() { return snapshot; }
        public ScenarioPlan getPlan() { return plan; }
        public String getManifestDigest() { return manifestDigest; }
    }
}
