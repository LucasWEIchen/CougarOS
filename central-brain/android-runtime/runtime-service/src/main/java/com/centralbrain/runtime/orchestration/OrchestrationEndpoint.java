package com.centralbrain.runtime.orchestration;

import android.os.IBinder;

import com.centralbrain.runtime.persistence.CentralBrainDatabase;
import com.centralbrain.runtime.persistence.RuntimeStateDao;
import com.centralbrain.runtime.persistence.SessionEntity;
import com.centralbrain.runtime.persistence.DurableOrchestrationProjectionRepository;
import com.centralbrain.runtime.persistence.DurableOrchestrationProjectionRepository.Commit;
import com.centralbrain.runtime.persistence.DurableOrchestrationProjectionRepository.RecoveryReport;
import com.centralbrain.runtime.orchestration.OrchestrationBackend.SessionDescriptor;
import com.centralbrain.sdk.event.RuntimeEvent;
import com.centralbrain.sdk.orchestration.ApprovalResponse;
import com.centralbrain.sdk.orchestration.ICentralBrainOrchestration;
import com.centralbrain.sdk.orchestration.OrchestrationContract;
import com.centralbrain.sdk.orchestration.OrchestrationSnapshot;
import com.centralbrain.sdk.orchestration.OrchestrationStartRequest;
import com.centralbrain.sdk.orchestration.UndoRequest;
import com.centralbrain.sdk.plan.ScenarioPlan;
import com.centralbrain.sdk.session.ICentralBrainSessionRuntime;

import java.util.Objects;

/** Owner-scoped Binder endpoint around a build-variant orchestration backend. */
public final class OrchestrationEndpoint implements AutoCloseable {
    public enum Operation {
        PROTOCOL_READ,
        START_OWN,
        READ_OWN,
        APPROVAL_RESPOND_OWN,
        UNDO_REQUEST_OWN,
        CANCEL_OWN
    }

    public interface OwnerResolver {
        String resolve(Operation operation);
    }

    public interface CommitListener {
        void onCommitted(String owner, String sessionId, RuntimeEvent event);
    }

    private final RuntimeStateDao dao;
    private final OwnerResolver ownerResolver;
    private final OrchestrationBackend backend;
    private final DurableOrchestrationProjectionRepository repository;
    private final CommitListener commitListener;

    private final ICentralBrainOrchestration.Stub binder =
            new ICentralBrainOrchestration.Stub() {
                @Override
                public int getProtocolVersion() {
                    ownerResolver.resolve(Operation.PROTOCOL_READ);
                    return ICentralBrainOrchestration.INTERFACE_VERSION;
                }

                @Override
                public String getProtocolHash() {
                    ownerResolver.resolve(Operation.PROTOCOL_READ);
                    return ICentralBrainOrchestration.INTERFACE_HASH;
                }

                @Override
                public OrchestrationSnapshot start(OrchestrationStartRequest request) {
                    long now = System.currentTimeMillis();
                    OrchestrationContract.validateStartRequest(request, now);
                    SessionDescriptor session = owned(
                            ownerResolver.resolve(Operation.START_OWN),
                            request.sessionId);
                    if (!session.getScenarioId().equals(request.scenarioId)) {
                        throw violation("scenario differs from durable Session");
                    }
                    if (isTerminal(session.getSessionState()) || session.getDeadlineEpochMs() <= now) {
                        throw violation("Session is not eligible for orchestration");
                    }
                    return committed(session, backend.start(session, request)).getSnapshot();
                }

                @Override
                public OrchestrationSnapshot getSnapshot(String sessionId) {
                    SessionDescriptor session = owned(
                            ownerResolver.resolve(Operation.READ_OWN), sessionId);
                    return committed(session, backend.get(session)).getSnapshot();
                }

                @Override
                public ScenarioPlan getPlan(String sessionId) {
                    SessionDescriptor session = owned(
                            ownerResolver.resolve(Operation.READ_OWN), sessionId);
                    OrchestrationBackend.Result result = committed(session, backend.get(session));
                    return OrchestrationProjectionFactory.copyPlan(result.getPlan());
                }

                @Override
                public OrchestrationSnapshot respondToApproval(ApprovalResponse response) {
                    long now = System.currentTimeMillis();
                    OrchestrationContract.validateApprovalResponse(response, now);
                    SessionDescriptor session = owned(
                            ownerResolver.resolve(Operation.APPROVAL_RESPOND_OWN),
                            response.sessionId);
                    return committed(
                            session,
                            backend.respondToApproval(session, response)).getSnapshot();
                }

                @Override
                public OrchestrationSnapshot requestUndo(UndoRequest request) {
                    long now = System.currentTimeMillis();
                    OrchestrationContract.validateUndoRequest(request, now);
                    SessionDescriptor session = owned(
                            ownerResolver.resolve(Operation.UNDO_REQUEST_OWN),
                            request.sessionId);
                    return committed(session, backend.requestUndo(session, request)).getSnapshot();
                }

                @Override
                public OrchestrationSnapshot cancel(String sessionId, int reasonCode) {
                    if (reasonCode < ICentralBrainSessionRuntime.CANCEL_REASON_USER
                            || reasonCode
                                    > ICentralBrainSessionRuntime.CANCEL_REASON_CALLER_GONE) {
                        throw violation("cancel reason is unknown");
                    }
                    SessionDescriptor session = owned(
                            ownerResolver.resolve(Operation.CANCEL_OWN), sessionId);
                    return committed(session, backend.cancel(session, reasonCode)).getSnapshot();
                }
            };

    public OrchestrationEndpoint(
            CentralBrainDatabase database,
            OwnerResolver ownerResolver,
            OrchestrationBackend backend,
            CommitListener commitListener) {
        dao = Objects.requireNonNull(database, "database").runtimeStateDao();
        repository = new DurableOrchestrationProjectionRepository(database);
        this.ownerResolver = Objects.requireNonNull(ownerResolver, "ownerResolver");
        this.backend = Objects.requireNonNull(backend, "backend");
        this.commitListener = Objects.requireNonNull(commitListener, "commitListener");
    }

    public IBinder binder() {
        return binder;
    }

    public RecoveryReport reconcileInterrupted() {
        return repository.reconcileInterrupted();
    }

    @Override
    public void close() {
        backend.close();
    }

    private SessionDescriptor owned(String owner, String sessionId) {
        if (owner == null || owner.isEmpty()) {
            throw new SecurityException("Central Brain orchestration owner is unresolved");
        }
        SessionEntity row = dao.findSessionOwned(sessionId, owner);
        if (row == null) {
            throw violation("Session not found for caller");
        }
        return new SessionDescriptor(
                row.ownerFingerprint,
                row.sessionId,
                row.scenarioId,
                row.state,
                row.activePlanRevision,
                row.deadlineWallMs,
                row.revision);
    }

    private static OrchestrationBackend.Result validated(OrchestrationBackend.Result result) {
        if (result == null) {
            throw new IllegalStateException("orchestration backend returned null result");
        }
        OrchestrationSnapshot snapshot = result.getSnapshot();
        OrchestrationContract.validatePlanForSnapshot(result.getPlan(), snapshot);
        return new OrchestrationBackend.Result(
                OrchestrationProjectionFactory.copySnapshot(snapshot),
                OrchestrationProjectionFactory.copyPlan(result.getPlan()),
                result.getManifestDigest());
    }

    private OrchestrationBackend.Result committed(
            SessionDescriptor session,
            OrchestrationBackend.Result source) {
        OrchestrationBackend.Result result = validated(source);
        Commit commit = repository.commitOwned(session, result);
        if (commit.getEvent() != null) {
            commitListener.onCommitted(
                    session.getOwnerFingerprint(),
                    session.getSessionId(),
                    commit.getEvent());
        }
        return result;
    }

    private static boolean isTerminal(int state) {
        return state == ICentralBrainSessionRuntime.SESSION_STATE_COMPLETED
                || state == ICentralBrainSessionRuntime.SESSION_STATE_FAILED
                || state == ICentralBrainSessionRuntime.SESSION_STATE_CANCELLED;
    }

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_ORCHESTRATION_RUNTIME: " + message);
    }
}
