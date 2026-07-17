package com.centralbrain.runtime.simulation;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.centralbrain.runtime.BuildConfig;
import com.centralbrain.runtime.R;
import com.centralbrain.runtime.context.ContextSnapshot.DrivingState;
import com.centralbrain.runtime.identity.AndroidCallerIdentityResolver;
import com.centralbrain.runtime.identity.CallerIdentitySnapshot;
import com.centralbrain.runtime.policy.AndroidCapabilityPolicyLoader;
import com.centralbrain.runtime.policy.CallerCapabilityPolicy;
import com.centralbrain.runtime.policy.CallerCapabilityPolicy.Capability;
import com.centralbrain.runtime.vehicle.schema.SignalValue;
import com.centralbrain.runtime.vehicle.schema.VehicleSignalPath;

/** Signature- and capability-protected debug-only simulation Binder endpoint. */
public final class DebugSimulationControllerService extends Service {
    public static final String ACTION =
            "com.centralbrain.runtime.action.BIND_DEBUG_SIMULATION_CONTROLLER";
    public static final String CONTROL_PERMISSION =
            "com.centralbrain.permission.CONTROL_DEBUG_SIMULATION";
    private static final String TAG = "CbDebugSimService";

    private final DebugSimulationController controller = new DebugSimulationController();
    private AndroidCallerIdentityResolver identityResolver;
    private CallerCapabilityPolicy capabilityPolicy;

    private final IDebugSimulationController.Stub binder =
            new IDebugSimulationController.Stub() {
                @Override
                public int getProtocolVersion() {
                    authorize();
                    return IDebugSimulationController.INTERFACE_VERSION;
                }

                @Override
                public String getProtocolHash() {
                    authorize();
                    return IDebugSimulationController.INTERFACE_HASH;
                }

                @Override
                public long setDrivingState(int drivingState) {
                    authorize();
                    return audited("setDrivingState", () -> controller.setDrivingState(
                            fromDrivingState(drivingState)));
                }

                @Override
                public long setSignal(
                        String canonicalPath,
                        String area,
                        int scalarType,
                        boolean booleanValue,
                        long integerValue,
                        double decimalValue,
                        String textValue) {
                    authorize();
                    return audited("setSignal", () -> controller.setSignal(
                            VehicleSignalPath.fromCanonicalPath(canonicalPath),
                            area,
                            fromScalarType(scalarType),
                            booleanValue,
                            integerValue,
                            decimalValue,
                            textValue));
                }

                @Override
                public long setAdapterFault(String adapterId, int faultMode, long durationMs) {
                    authorize();
                    return audited("setAdapterFault", () -> controller.setAdapterFault(
                            adapterId, fromFault(faultMode, durationMs)));
                }

                @Override
                public long advanceSimulationClock(long durationMs) {
                    authorize();
                    return audited("advanceSimulationClock",
                            () -> controller.advanceSimulationClock(durationMs));
                }

                @Override
                public long reset() {
                    authorize();
                    return audited("reset", controller::reset);
                }

                @Override
                public long getRevision() {
                    authorize();
                    return controller.snapshot().getRevision();
                }

                @Override
                public int getDrivingState() {
                    authorize();
                    return toDrivingState(controller.snapshot().getDrivingState());
                }

                @Override
                public int getSignalCount() {
                    authorize();
                    return controller.snapshot().getSignals().size();
                }

                @Override
                public int getFaultCount() {
                    authorize();
                    return controller.snapshot().getFaults().size();
                }

                @Override
                public long getSimulationElapsedRealtimeMs() {
                    authorize();
                    return controller.snapshot().getElapsedRealtimeMs();
                }

                @Override
                public int getAuditEntryCount() {
                    authorize();
                    return controller.snapshot().getAuditEntryCount();
                }

                @Override
                public String getSnapshotDigest() {
                    authorize();
                    return controller.snapshot().getDigest();
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();
        if (!BuildConfig.DEBUG) {
            throw new IllegalStateException("debug simulation service in non-debug build");
        }
        identityResolver = new AndroidCallerIdentityResolver(this);
        capabilityPolicy = AndroidCapabilityPolicyLoader.load(
                this,
                R.xml.central_brain_capability_policy,
                identityResolver.resolveOwnIdentity());
        Log.i(TAG, "created debug_only=true production_authorized=false"
                + " capability_default=deny hardware_accessed=false");
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) {
            Log.w(TAG, "bind rejected reason=ACTION_MISMATCH hardware_accessed=false");
            return null;
        }
        return binder;
    }

    private void authorize() {
        enforceCallingOrSelfPermission(
                CONTROL_PERMISSION,
                "debug simulation signature permission required");
        CallerIdentitySnapshot caller = identityResolver.resolveCallingIdentity();
        CallerCapabilityPolicy.Decision decision = capabilityPolicy.evaluate(
                caller,
                Capability.SIMULATION_CONTROL);
        if (!decision.isAllowed()) {
            Log.w(TAG, "capability denied capability=" + Capability.SIMULATION_CONTROL.getId()
                    + " reason=" + decision.getReason()
                    + " matchedPackage=" + decision.getMatchedPackage()
                    + " " + caller.auditSummary()
                    + " hardware_accessed=false");
            throw new SecurityException("Central Brain capability denied: "
                    + Capability.SIMULATION_CONTROL.getId());
        }
    }

    private long audited(String command, RevisionCommand operation) {
        try {
            long result = operation.run();
            Log.i(TAG, "debug_simulation_audit_event=true command=" + command
                    + " outcome=APPLIED"
                    + " revision=" + result
                    + " snapshot_digest=" + controller.snapshot().getDigest()
                    + " debug_only=true hardware_accessed=false");
            return result;
        } catch (RuntimeException exception) {
            Log.w(TAG, "debug_simulation_audit_event=true command=" + command
                    + " outcome=REJECTED"
                    + " error=" + exception.getClass().getSimpleName()
                    + " revision=" + controller.snapshot().getRevision()
                    + " snapshot_digest=" + controller.snapshot().getDigest()
                    + " debug_only=true hardware_accessed=false");
            throw exception;
        }
    }

    private static DrivingState fromDrivingState(int value) {
        switch (value) {
            case IDebugSimulationController.DRIVING_STATE_UNKNOWN:
                return DrivingState.UNKNOWN;
            case IDebugSimulationController.DRIVING_STATE_PARKED:
                return DrivingState.PARKED;
            case IDebugSimulationController.DRIVING_STATE_MOVING:
                return DrivingState.MOVING;
            default:
                throw new IllegalArgumentException("CB_DEBUG_SIM: driving state is unknown");
        }
    }

    private static int toDrivingState(DrivingState value) {
        switch (value) {
            case UNKNOWN:
                return IDebugSimulationController.DRIVING_STATE_UNKNOWN;
            case PARKED:
                return IDebugSimulationController.DRIVING_STATE_PARKED;
            case MOVING:
                return IDebugSimulationController.DRIVING_STATE_MOVING;
            default:
                throw new IllegalStateException("CB_DEBUG_SIM: driving state is unknown");
        }
    }

    private static SignalValue.ScalarType fromScalarType(int value) {
        switch (value) {
            case IDebugSimulationController.SCALAR_BOOLEAN:
                return SignalValue.ScalarType.BOOLEAN;
            case IDebugSimulationController.SCALAR_INTEGER:
                return SignalValue.ScalarType.INTEGER;
            case IDebugSimulationController.SCALAR_DECIMAL:
                return SignalValue.ScalarType.DECIMAL;
            case IDebugSimulationController.SCALAR_TEXT:
                return SignalValue.ScalarType.TEXT;
            default:
                throw new IllegalArgumentException("CB_DEBUG_SIM: scalar type is unknown");
        }
    }

    private static FaultInjectionProfile fromFault(int mode, long durationMs) {
        switch (mode) {
            case IDebugSimulationController.FAULT_NONE:
                requireZeroDuration(durationMs);
                return FaultInjectionProfile.none();
            case IDebugSimulationController.FAULT_DELAY:
                return FaultInjectionProfile.delay(durationMs);
            case IDebugSimulationController.FAULT_TIMEOUT:
                return FaultInjectionProfile.timeout(durationMs);
            case IDebugSimulationController.FAULT_RETRYABLE_FAILURE:
                requireZeroDuration(durationMs);
                return FaultInjectionProfile.retryableFailure();
            case IDebugSimulationController.FAULT_TERMINAL_FAILURE:
                requireZeroDuration(durationMs);
                return FaultInjectionProfile.terminalFailure();
            case IDebugSimulationController.FAULT_READBACK_MISMATCH:
                requireZeroDuration(durationMs);
                return FaultInjectionProfile.readbackMismatch();
            default:
                throw new IllegalArgumentException("CB_DEBUG_SIM: fault mode is unknown");
        }
    }

    private static void requireZeroDuration(long durationMs) {
        if (durationMs != 0) {
            throw new IllegalArgumentException(
                    "CB_DEBUG_SIM: non-timing fault cannot carry duration");
        }
    }

    private interface RevisionCommand {
        long run();
    }
}
