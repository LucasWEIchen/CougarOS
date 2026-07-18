package com.centralbrain.client2;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

import com.centralbrain.runtime.scenario.ISimulatedScenarioRuntime;
import com.centralbrain.runtime.scenario.SimulatedScenarioBinderSnapshot;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Debug-only, metadata-only Client2 connection to the simulated scenario Runtime. */
public final class SimulatedScenarioRuntimeClient implements AutoCloseable {
    private static final String TAG = "CbClient2Scenario";
    private static final String RUNTIME_PACKAGE = "com.centralbrain.runtime";
    private static final String SERVICE_CLASS =
            "com.centralbrain.runtime.scenario.SimulatedScenarioRuntimeService";
    private static final String ACTION =
            "com.centralbrain.runtime.action.BIND_SIMULATED_SCENARIO_RUNTIME";

    public interface Callback {
        void onSimulatedRuntimeAvailability(boolean available, String failureCode);

        void onSimulatedScenarioSnapshot(
                CockpitSimulatedScenarioState.Projection projection);

        void onSimulatedScenarioFailure(String uiScenarioId, String failureCode);
    }

    private static final class PendingStart {
        private final long generation;
        private final String uiScenarioId;
        private final String drivingProfile;
        private final int scenario;
        private final int drivingState;

        private PendingStart(
                long generation,
                String uiScenarioId,
                String drivingProfile,
                int scenario,
                int drivingState) {
            this.generation = generation;
            this.uiScenarioId = uiScenarioId;
            this.drivingProfile = drivingProfile;
            this.scenario = scenario;
            this.drivingState = drivingState;
        }
    }

    private final Context context;
    private final Callback callback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService binderExecutor = Executors.newSingleThreadExecutor();
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            ISimulatedScenarioRuntime candidate =
                    ISimulatedScenarioRuntime.Stub.asInterface(binder);
            synchronized (SimulatedScenarioRuntimeClient.this) {
                if (closed) {
                    return;
                }
                runtime = candidate;
                bound = true;
                protocolVerified = false;
            }
            binderExecutor.execute(() -> verifyProtocol(candidate));
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            clearRuntime("CB_SIM_SCENARIO_DISCONNECTED");
        }

        @Override
        public void onBindingDied(ComponentName name) {
            clearRuntime("CB_SIM_SCENARIO_BINDING_DIED");
        }

        @Override
        public void onNullBinding(ComponentName name) {
            clearRuntime("CB_SIM_SCENARIO_NULL_BINDING");
        }
    };

    private ISimulatedScenarioRuntime runtime;
    private PendingStart pendingStart;
    private SimulatedScenarioBinderSnapshot latestSnapshot;
    private String currentUiScenarioId = "";
    private String currentDrivingProfile = "";
    private long generation;
    private boolean bound;
    private boolean protocolVerified;
    private boolean closed;

    public SimulatedScenarioRuntimeClient(Context context, Callback callback) {
        this.context = Objects.requireNonNull(context, "context").getApplicationContext();
        this.callback = Objects.requireNonNull(callback, "callback");
    }

    public void connect() {
        synchronized (this) {
            if (closed || bound) {
                return;
            }
        }
        post(() -> callback.onSimulatedRuntimeAvailability(false, "CONNECTING"));
        Intent intent = new Intent(ACTION).setComponent(
                new ComponentName(RUNTIME_PACKAGE, SERVICE_CLASS));
        boolean accepted;
        try {
            accepted = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        } catch (RuntimeException failure) {
            accepted = false;
        }
        if (!accepted) {
            clearRuntime("CB_SIM_SCENARIO_BIND_REJECTED");
        } else {
            Log.i(TAG, "simulated_scenario_client_bind_accepted=true raw_payload_logged=false");
        }
    }

    public void startScenario(
            String uiScenarioId,
            CockpitSeatState.DrivingState drivingState) {
        String scenarioId = requireScenario(uiScenarioId);
        Objects.requireNonNull(drivingState, "drivingState");
        PendingStart start;
        boolean ready;
        synchronized (this) {
            if (closed) {
                return;
            }
            generation++;
            start = new PendingStart(
                    generation,
                    scenarioId,
                    drivingProfile(drivingState),
                    scenario(scenarioId),
                    driving(drivingState));
            pendingStart = start;
            ready = protocolVerified && runtime != null;
        }
        if (ready) {
            binderExecutor.execute(this::drainPendingStart);
        }
    }

    public void approvePending() {
        supplyPendingOutcome(ISimulatedScenarioRuntime.OUTCOME_SUCCEEDED);
    }

    public void skipPending() {
        supplyPendingOutcome(ISimulatedScenarioRuntime.OUTCOME_SKIPPED);
    }

    private void verifyProtocol(ISimulatedScenarioRuntime candidate) {
        try {
            if (candidate.getProtocolVersion() != ISimulatedScenarioRuntime.INTERFACE_VERSION
                    || !ISimulatedScenarioRuntime.INTERFACE_HASH.equals(
                    candidate.getProtocolHash())) {
                throw new IllegalStateException("simulated scenario protocol mismatch");
            }
            synchronized (this) {
                if (closed || runtime != candidate) {
                    return;
                }
                protocolVerified = true;
            }
            Log.i(TAG, "simulated_scenario_client_protocol_version=2"
                    + " metadata_only=true hardware_accessed=false");
            post(() -> callback.onSimulatedRuntimeAvailability(true, ""));
            drainPendingStart();
        } catch (RemoteException | RuntimeException failure) {
            clearRuntime("CB_SIM_SCENARIO_PROTOCOL");
        }
    }

    private void drainPendingStart() {
        PendingStart start;
        ISimulatedScenarioRuntime service;
        SimulatedScenarioBinderSnapshot previous;
        synchronized (this) {
            if (closed || !protocolVerified || runtime == null || pendingStart == null) {
                return;
            }
            service = runtime;
            start = pendingStart;
            pendingStart = null;
            previous = latestSnapshot;
        }
        try {
            if (previous != null && !terminal(previous.sessionState)) {
                service.cancel(previous.runId);
            }
            SimulatedScenarioBinderSnapshot snapshot = service.startScenario(
                    start.scenario, start.drivingState);
            publish(start, snapshot);
        } catch (RemoteException failure) {
            fail(start.uiScenarioId, "CB_SIM_SCENARIO_START_REMOTE");
        } catch (IllegalArgumentException failure) {
            fail(start.uiScenarioId, "CB_SIM_SCENARIO_START_PROJECTION");
        } catch (RuntimeException failure) {
            fail(start.uiScenarioId, "CB_SIM_SCENARIO_START_RUNTIME");
        }
    }

    private void supplyPendingOutcome(int outcome) {
        ISimulatedScenarioRuntime service;
        SimulatedScenarioBinderSnapshot snapshot;
        String uiScenarioId;
        String profile;
        long currentGeneration;
        synchronized (this) {
            service = closed || !protocolVerified ? null : runtime;
            snapshot = latestSnapshot;
            uiScenarioId = currentUiScenarioId;
            profile = currentDrivingProfile;
            currentGeneration = generation;
        }
        if (service == null || snapshot == null
                || snapshot.sessionState != ISimulatedScenarioRuntime.SESSION_WAITING_APPROVAL) {
            fail(uiScenarioId, "CB_SIM_SCENARIO_APPROVAL_UNAVAILABLE");
            return;
        }
        binderExecutor.execute(() -> {
            try {
                SimulatedScenarioBinderSnapshot updated = service.supplyPendingOutcome(
                        snapshot.runId, outcome);
                publish(new PendingStart(
                        currentGeneration,
                        uiScenarioId,
                        profile,
                        scenario(uiScenarioId),
                        "PARKED".equals(profile)
                                ? ISimulatedScenarioRuntime.DRIVING_PARKED
                                : ISimulatedScenarioRuntime.DRIVING_MOVING), updated);
            } catch (RemoteException failure) {
                fail(uiScenarioId, "CB_SIM_SCENARIO_APPROVAL_REMOTE");
            } catch (IllegalArgumentException failure) {
                fail(uiScenarioId, "CB_SIM_SCENARIO_APPROVAL_PROJECTION");
            } catch (RuntimeException failure) {
                fail(uiScenarioId, "CB_SIM_SCENARIO_APPROVAL_RUNTIME");
            }
        });
    }

    private void publish(PendingStart start, SimulatedScenarioBinderSnapshot snapshot) {
        CockpitSimulatedScenarioState.Projection projection = validateAndProject(
                start.uiScenarioId, start.drivingProfile, snapshot);
        synchronized (this) {
            if (closed || start.generation != generation) {
                return;
            }
            latestSnapshot = snapshot;
            currentUiScenarioId = start.uiScenarioId;
            currentDrivingProfile = start.drivingProfile;
        }
        Log.i(TAG, "simulated_scenario_client_snapshot=true"
                + " session_state=" + snapshot.sessionState
                + " effect_dispatch_count=" + snapshot.simulatedEffectDispatchCount
                + " readback_match_count=" + snapshot.simulatedReadbackMatchCount
                + " approval_input_count=" + snapshot.simulatedApprovalInputCount
                + " failure_count=" + snapshot.simulatedFailureCount
                + " simulated_only=true hardware_accessed=false");
        post(() -> callback.onSimulatedScenarioSnapshot(projection));
    }

    private CockpitSimulatedScenarioState.Projection validateAndProject(
            String uiScenarioId,
            String profile,
            SimulatedScenarioBinderSnapshot snapshot) {
        if (snapshot == null || snapshot.schemaVersion != 2) {
            throw new IllegalArgumentException("invalid simulated scenario schema");
        }
        if (!snapshot.runId.matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")) {
            throw new IllegalArgumentException("invalid simulated run id");
        }
        if (!snapshot.planDigest.matches("[0-9a-f]{64}")
                || !snapshot.projectionDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid simulated projection digest");
        }
        if (!snapshot.effectDispatchEnabled
                || snapshot.readbackAccessed
                        != (snapshot.simulatedReadbackAttemptCount > 0)
                || snapshot.approvalAuthorityAvailable
                || snapshot.hardwareAccessed
                || snapshot.productionReady
                || snapshot.targetHardwareValidated) {
            throw new IllegalArgumentException("invalid simulated authority flags");
        }
        return CockpitSimulatedScenarioState.Projection.create(
                uiScenarioId,
                snapshot.scenarioId,
                profile,
                lifecycle(snapshot.sessionState),
                pendingStage(snapshot.pendingStage),
                pendingTarget(uiScenarioId, snapshot),
                snapshot.planRevision,
                Math.toIntExact(snapshot.graphRevision),
                snapshot.projectedEventCount,
                snapshot.simulatedEffectDispatchCount,
                snapshot.simulatedReadbackAttemptCount,
                snapshot.simulatedReadbackMatchCount,
                snapshot.simulatedApprovalInputCount,
                snapshot.simulatedFailureCount);
    }

    private static String pendingTarget(
            String uiScenarioId,
            SimulatedScenarioBinderSnapshot snapshot) {
        if (snapshot.pendingStage == ISimulatedScenarioRuntime.PENDING_NONE) {
            if (!snapshot.pendingNodeId.isEmpty() || !snapshot.pendingCapabilityId.isEmpty()) {
                throw new IllegalArgumentException("terminal simulated pending target mismatch");
            }
            return "";
        }
        if (snapshot.pendingStage == ISimulatedScenarioRuntime.PENDING_APPROVAL) {
            if (!"care.fatigue".equals(uiScenarioId)
                    || !"request_seat_approval".equals(snapshot.pendingNodeId)
                    || !snapshot.pendingCapabilityId.isEmpty()) {
                throw new IllegalArgumentException("simulated approval target mismatch");
            }
            return "vehicle.seat.recline";
        }
        if (!snapshot.pendingNodeId.matches("[a-z][a-z0-9_]{2,63}")
                || !snapshot.pendingCapabilityId.matches("[a-z][a-z0-9_.]{2,127}")) {
            throw new IllegalArgumentException("simulated pending target is invalid");
        }
        return snapshot.pendingCapabilityId;
    }

    private void clearRuntime(String code) {
        synchronized (this) {
            runtime = null;
            protocolVerified = false;
            if (closed) {
                return;
            }
        }
        Log.w(TAG, "simulated_scenario_client_failed=true error_code=" + code
                + " raw_payload_logged=false hardware_accessed=false");
        post(() -> callback.onSimulatedRuntimeAvailability(false, code));
    }

    private void fail(String uiScenarioId, String code) {
        String safeScenario = CockpitSimulatedScenarioState.isSupported(uiScenarioId)
                ? uiScenarioId : "care.cold";
        Log.w(TAG, "simulated_scenario_client_command_failed=true error_code=" + code
                + " raw_payload_logged=false hardware_accessed=false");
        post(() -> callback.onSimulatedScenarioFailure(safeScenario, code));
    }

    private void post(Runnable runnable) {
        mainHandler.post(() -> {
            synchronized (SimulatedScenarioRuntimeClient.this) {
                if (closed) {
                    return;
                }
            }
            runnable.run();
        });
    }

    @Override
    public void close() {
        boolean shouldUnbind;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            runtime = null;
            pendingStart = null;
            latestSnapshot = null;
            protocolVerified = false;
            shouldUnbind = bound;
            bound = false;
        }
        if (shouldUnbind) {
            try {
                context.unbindService(serviceConnection);
            } catch (RuntimeException ignored) {
                // Binder death can race lifecycle teardown; local state is already closed.
            }
        }
        binderExecutor.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
    }

    private static String requireScenario(String uiScenarioId) {
        if (!CockpitSimulatedScenarioState.isSupported(uiScenarioId)) {
            throw new IllegalArgumentException("unsupported simulated scenario");
        }
        return uiScenarioId;
    }

    private static int scenario(String uiScenarioId) {
        return "care.cold".equals(requireScenario(uiScenarioId))
                ? ISimulatedScenarioRuntime.SCENARIO_COLD
                : ISimulatedScenarioRuntime.SCENARIO_FATIGUE;
    }

    private static int driving(CockpitSeatState.DrivingState state) {
        return state == CockpitSeatState.DrivingState.PARKED
                ? ISimulatedScenarioRuntime.DRIVING_PARKED
                : ISimulatedScenarioRuntime.DRIVING_MOVING;
    }

    private static String drivingProfile(CockpitSeatState.DrivingState state) {
        return state == CockpitSeatState.DrivingState.PARKED
                ? "PARKED" : "MOVING_RESTRICTED";
    }

    private static CockpitSimulatedScenarioState.Lifecycle lifecycle(int state) {
        switch (state) {
            case ISimulatedScenarioRuntime.SESSION_WAITING_APPROVAL:
                return CockpitSimulatedScenarioState.Lifecycle.WAITING_APPROVAL;
            case ISimulatedScenarioRuntime.SESSION_WAITING_EFFECT:
            case ISimulatedScenarioRuntime.SESSION_WAITING_READBACK:
                return CockpitSimulatedScenarioState.Lifecycle.RUNNING;
            case ISimulatedScenarioRuntime.SESSION_COMPLETED:
                return CockpitSimulatedScenarioState.Lifecycle.COMPLETED;
            case ISimulatedScenarioRuntime.SESSION_PARTIAL:
                return CockpitSimulatedScenarioState.Lifecycle.PARTIAL;
            case ISimulatedScenarioRuntime.SESSION_FAILED:
                return CockpitSimulatedScenarioState.Lifecycle.FAILED;
            case ISimulatedScenarioRuntime.SESSION_CANCELLED:
                return CockpitSimulatedScenarioState.Lifecycle.CANCELLED;
            case ISimulatedScenarioRuntime.SESSION_STUCK:
                return CockpitSimulatedScenarioState.Lifecycle.STUCK;
            default:
                throw new IllegalArgumentException("invalid simulated session state");
        }
    }

    private static CockpitSimulatedScenarioState.PendingStage pendingStage(int stage) {
        switch (stage) {
            case ISimulatedScenarioRuntime.PENDING_NONE:
                return CockpitSimulatedScenarioState.PendingStage.NONE;
            case ISimulatedScenarioRuntime.PENDING_APPROVAL:
                return CockpitSimulatedScenarioState.PendingStage.APPROVAL;
            case ISimulatedScenarioRuntime.PENDING_EFFECT:
                return CockpitSimulatedScenarioState.PendingStage.EFFECT;
            case ISimulatedScenarioRuntime.PENDING_READBACK:
                return CockpitSimulatedScenarioState.PendingStage.READBACK;
            default:
                throw new IllegalArgumentException("invalid simulated pending stage");
        }
    }

    private static boolean terminal(int state) {
        return state == ISimulatedScenarioRuntime.SESSION_COMPLETED
                || state == ISimulatedScenarioRuntime.SESSION_PARTIAL
                || state == ISimulatedScenarioRuntime.SESSION_FAILED
                || state == ISimulatedScenarioRuntime.SESSION_CANCELLED
                || state == ISimulatedScenarioRuntime.SESSION_STUCK;
    }
}
