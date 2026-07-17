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

import com.centralbrain.runtime.simulation.IDebugSimulationController;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Debug-only client for the signature- and capability-protected simulation controller. */
public final class DebugSimulationControllerClient implements AutoCloseable {
    private static final String TAG = "CbClient2Sim";
    private static final String RUNTIME_PACKAGE = "com.centralbrain.runtime";
    private static final String SERVICE_CLASS =
            "com.centralbrain.runtime.simulation.DebugSimulationControllerService";
    private static final String ACTION =
            "com.centralbrain.runtime.action.BIND_DEBUG_SIMULATION_CONTROLLER";
    private static final String OCCUPANCY_PATH = "Vehicle.Cabin.Seat.IsOccupied";
    private static final String BELT_PATH = "Vehicle.Cabin.Seat.IsBelted";
    private static final String DRIVER_AREA = "row1.driver";
    private static final long DELAY_DURATION_MS = 1_000L;
    private static final long TIMEOUT_DURATION_MS = 3_000L;

    public interface Callback {
        void onConnecting();

        void onConnected(long revision, CockpitSeatState.DrivingState drivingState);

        void onDrivingApplied(CockpitSeatState.DrivingState value, long revision);

        void onOccupancyApplied(CockpitSeatState.OccupancyState value, long revision);

        void onBeltApplied(CockpitSeatState.BeltState value, long revision);

        void onFaultApplied(CockpitEngineerState.FaultMode value, long revision);

        void onResetApplied(long revision);

        void onFailure(String code);
    }

    private final Context context;
    private final Callback callback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService binderExecutor = Executors.newSingleThreadExecutor();
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            IDebugSimulationController candidate =
                    IDebugSimulationController.Stub.asInterface(binder);
            synchronized (DebugSimulationControllerClient.this) {
                if (closed) {
                    return;
                }
                controller = candidate;
                bound = true;
            }
            Log.i(TAG, "debug_simulation_service_connected=true raw_payload_logged=false");
            execute("CB_SIM_PROTOCOL", service -> {
                if (service.getProtocolVersion()
                        != IDebugSimulationController.INTERFACE_VERSION
                        || !IDebugSimulationController.INTERFACE_HASH.equals(
                        service.getProtocolHash())) {
                    throw new IllegalStateException("debug simulation protocol mismatch");
                }
                long revision = service.getRevision();
                CockpitSeatState.DrivingState driving = fromDrivingState(
                        service.getDrivingState());
                Log.i(TAG, "debug_simulation_protocol_verified=true"
                        + " controller_revision=" + revision
                        + " effect_authorization_source=false");
                post(() -> callback.onConnected(revision, driving));
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            clearController("CB_SIM_DISCONNECTED");
        }

        @Override
        public void onBindingDied(ComponentName name) {
            clearController("CB_SIM_BINDING_DIED");
        }

        @Override
        public void onNullBinding(ComponentName name) {
            clearController("CB_SIM_NULL_BINDING");
        }
    };

    private IDebugSimulationController controller;
    private boolean bound;
    private boolean closed;

    public DebugSimulationControllerClient(Context context, Callback callback) {
        this.context = Objects.requireNonNull(context, "context").getApplicationContext();
        this.callback = Objects.requireNonNull(callback, "callback");
    }

    public void connect() {
        synchronized (this) {
            if (closed || bound) {
                return;
            }
        }
        callback.onConnecting();
        Intent intent = new Intent(ACTION).setComponent(
                new ComponentName(RUNTIME_PACKAGE, SERVICE_CLASS));
        boolean accepted;
        try {
            accepted = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        } catch (RuntimeException exception) {
            accepted = false;
        }
        if (!accepted) {
            clearController("CB_SIM_BIND_REJECTED");
        } else {
            Log.i(TAG, "debug_simulation_bind_accepted=true");
        }
    }

    public void setDrivingState(CockpitSeatState.DrivingState value) {
        Objects.requireNonNull(value, "value");
        execute("CB_SIM_DRIVING", service -> {
            long revision = service.setDrivingState(toDrivingState(value));
            post(() -> callback.onDrivingApplied(value, revision));
        });
    }

    public void setOccupancy(CockpitSeatState.OccupancyState value) {
        Objects.requireNonNull(value, "value");
        if (value == CockpitSeatState.OccupancyState.UNKNOWN) {
            throw new IllegalArgumentException("occupancy command cannot be unknown");
        }
        execute("CB_SIM_OCCUPANCY", service -> {
            long revision = service.setSignal(
                    OCCUPANCY_PATH,
                    DRIVER_AREA,
                    IDebugSimulationController.SCALAR_BOOLEAN,
                    value == CockpitSeatState.OccupancyState.OCCUPIED,
                    0,
                    0,
                    "");
            post(() -> callback.onOccupancyApplied(value, revision));
        });
    }

    public void setBelt(CockpitSeatState.BeltState value) {
        Objects.requireNonNull(value, "value");
        if (value == CockpitSeatState.BeltState.UNKNOWN) {
            throw new IllegalArgumentException("belt command cannot be unknown");
        }
        execute("CB_SIM_BELT", service -> {
            long revision = service.setSignal(
                    BELT_PATH,
                    DRIVER_AREA,
                    IDebugSimulationController.SCALAR_BOOLEAN,
                    value == CockpitSeatState.BeltState.BELTED,
                    0,
                    0,
                    "");
            post(() -> callback.onBeltApplied(value, revision));
        });
    }

    public void setAdapterFault(
            CockpitEngineerState.AdapterTarget adapter,
            CockpitEngineerState.FaultMode faultMode) {
        Objects.requireNonNull(adapter, "adapter");
        Objects.requireNonNull(faultMode, "faultMode");
        execute("CB_SIM_FAULT", service -> {
            long revision = service.setAdapterFault(
                    adapter.getAdapterId(),
                    toFaultMode(faultMode),
                    faultDuration(faultMode));
            post(() -> callback.onFaultApplied(faultMode, revision));
        });
    }

    public void reset() {
        execute("CB_SIM_RESET", service -> {
            long revision = service.reset();
            post(() -> callback.onResetApplied(revision));
        });
    }

    private void execute(String failureCode, RemoteCommand command) {
        IDebugSimulationController snapshot;
        synchronized (this) {
            snapshot = closed ? null : controller;
        }
        if (snapshot == null) {
            post(() -> callback.onFailure("CB_SIM_NOT_CONNECTED"));
            return;
        }
        binderExecutor.execute(() -> {
            try {
                command.run(snapshot);
            } catch (RemoteException | RuntimeException exception) {
                clearController(failureCode);
            }
        });
    }

    private void clearController(String code) {
        synchronized (this) {
            controller = null;
            if (closed) {
                return;
            }
        }
        Log.w(TAG, "debug_simulation_client_failed=true error_code=" + code
                + " raw_payload_logged=false effect_authorization_source=false");
        post(() -> callback.onFailure(code));
    }

    private void post(Runnable runnable) {
        mainHandler.post(() -> {
            synchronized (DebugSimulationControllerClient.this) {
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
            controller = null;
            shouldUnbind = bound;
            bound = false;
        }
        if (shouldUnbind) {
            try {
                context.unbindService(serviceConnection);
            } catch (RuntimeException ignored) {
                // The service may already have died; local state is still fail-closed.
            }
        }
        binderExecutor.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
    }

    private static int toDrivingState(CockpitSeatState.DrivingState value) {
        switch (value) {
            case PARKED:
                return IDebugSimulationController.DRIVING_STATE_PARKED;
            case MOVING:
                return IDebugSimulationController.DRIVING_STATE_MOVING;
            case UNKNOWN_RESTRICTED:
            default:
                return IDebugSimulationController.DRIVING_STATE_UNKNOWN;
        }
    }

    private static CockpitSeatState.DrivingState fromDrivingState(int value) {
        switch (value) {
            case IDebugSimulationController.DRIVING_STATE_PARKED:
                return CockpitSeatState.DrivingState.PARKED;
            case IDebugSimulationController.DRIVING_STATE_MOVING:
                return CockpitSeatState.DrivingState.MOVING;
            case IDebugSimulationController.DRIVING_STATE_UNKNOWN:
            default:
                return CockpitSeatState.DrivingState.UNKNOWN_RESTRICTED;
        }
    }

    private static int toFaultMode(CockpitEngineerState.FaultMode value) {
        switch (value) {
            case DELAY:
                return IDebugSimulationController.FAULT_DELAY;
            case TIMEOUT:
                return IDebugSimulationController.FAULT_TIMEOUT;
            case RETRYABLE_FAILURE:
                return IDebugSimulationController.FAULT_RETRYABLE_FAILURE;
            case TERMINAL_FAILURE:
                return IDebugSimulationController.FAULT_TERMINAL_FAILURE;
            case READBACK_MISMATCH:
                return IDebugSimulationController.FAULT_READBACK_MISMATCH;
            case NONE:
            default:
                return IDebugSimulationController.FAULT_NONE;
        }
    }

    private static long faultDuration(CockpitEngineerState.FaultMode value) {
        if (value == CockpitEngineerState.FaultMode.DELAY) {
            return DELAY_DURATION_MS;
        }
        if (value == CockpitEngineerState.FaultMode.TIMEOUT) {
            return TIMEOUT_DURATION_MS;
        }
        return 0;
    }

    private interface RemoteCommand {
        void run(IDebugSimulationController controller) throws RemoteException;
    }
}
