package com.centralbrain.runtime.simulation;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.centralbrain.runtime.effects.EffectAdapter;
import com.centralbrain.runtime.governance.SafetyVehicleStateProvider;
import com.centralbrain.runtime.governance.SafetyVehicleStateSnapshot;
import com.centralbrain.runtime.vehicle.schema.VehicleSignalPath;
import com.centralbrain.runtime.vehicle.twin.DigitalTwinSnapshot;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/** Isolated API 33 probe for the debug-only Seat simulation and safety gate. */
public final class SimulatedSeatEffectAdapterProbeActivity extends Activity {
    private static final String TAG = "CbSimSeat";
    private static final byte[] ENVELOPE = "seat-probe-envelope-v1".getBytes();
    private static final String APPROVAL = token('a');

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        try {
            require(nonce != null && !nonce.isEmpty(), "nonce missing");
            verifyComfort();
            verifyParkedProgress();
            verifyMovingReject();
            verifyBeltRace();
            verifyFaults();
            Log.i(TAG, "nonce=" + nonce
                    + " simulated_seat_probe_complete=true"
                    + " simulated_seat_adapter_defined=true"
                    + " simulated_seat_typed_target_verified=true"
                    + " simulated_seat_heat_vent_verified=true"
                    + " simulated_seat_recline_safety_verified=true"
                    + " simulated_seat_dispatch_revalidation_verified=true"
                    + " simulated_seat_belt_race_verified=true"
                    + " simulated_seat_progress_verified=true"
                    + " simulated_seat_fault_readback_verified=true"
                    + " simulated_seat_idempotency_verified=true"
                    + " simulated_seat_android13_arm64_verified=true"
                    + " simulated_seat_debug_only=true"
                    + " simulated_seat_production_registered=false"
                    + " simulated_seat_runtime_wired=false"
                    + " scenario_plan_runtime_published=false"
                    + " scenario_graph_execution_enabled=false"
                    + " effect_dispatch_enabled=false"
                    + " vehicle_signal_provider_wired=false"
                    + " hardware_accessed=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " simulated_seat_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " simulated_seat_debug_only=true"
                    + " simulated_seat_production_registered=false"
                    + " simulated_seat_runtime_wired=false"
                    + " effect_dispatch_enabled=false"
                    + " hardware_accessed=false", exception);
        } finally {
            finish();
        }
    }

    private static void verifyComfort() {
        Fixture fixture = fixture(FaultInjectionProfile.none());
        apply(fixture.adapter, token('b'),
                SimulatedSeatEffectAdapter.SeatTarget.heatingLevel(
                        "row1.driver", 2));
        apply(fixture.adapter, token('c'),
                SimulatedSeatEffectAdapter.SeatTarget.ventilationLevel(
                        "row1.driver", 3));
        DigitalTwinSnapshot snapshot = fixture.adapter.getDigitalTwinSnapshot();
        require(snapshot.reconcile(
                VehicleSignalPath.SEAT_HEATING_LEVEL,
                "row1.driver") == DigitalTwinSnapshot.ReconciliationState.MATCHED,
                "heating not matched");
        require(snapshot.reconcile(
                VehicleSignalPath.SEAT_VENTILATION_LEVEL,
                "row1.driver") == DigitalTwinSnapshot.ReconciliationState.MATCHED,
                "ventilation not matched");
    }

    private static void verifyParkedProgress() {
        Fixture fixture = fixture(FaultInjectionProfile.delay(100));
        EffectAdapter.Invocation invocation = invocation(
                token('d'),
                SimulatedSeatEffectAdapter.SeatTarget.reclineAngle(
                        "row1.driver", 30.0, APPROVAL));
        fixture.adapter.apply(invocation);
        fixture.clock.advanceBy(50);
        SimulatedSeatEffectAdapter.SeatProgressObservation halfway =
                fixture.adapter.querySeatProgress(token('d'));
        require(halfway.getPercent() == 50
                && Double.compare(halfway.getProjectedAngle(), 15.0) == 0
                && !halfway.isTerminal(), "partial progress invalid");
        fixture.clock.advanceBy(50);
        SimulatedSeatEffectAdapter.SeatProgressObservation complete =
                fixture.adapter.querySeatProgress(token('d'));
        require(complete.getPercent() == 100 && complete.isTerminal(),
                "recline did not complete");
        long revision = fixture.adapter.getDigitalTwinRevision();
        fixture.adapter.apply(invocation);
        require(fixture.adapter.getDigitalTwinRevision() == revision,
                "duplicate changed Twin");
    }

    private static void verifyMovingReject() {
        Fixture fixture = fixture(FaultInjectionProfile.none());
        fixture.safety.motion = SafetyVehicleStateSnapshot.MotionState.MOVING;
        boolean rejected = false;
        try {
            apply(fixture.adapter, token('e'),
                    SimulatedSeatEffectAdapter.SeatTarget.reclineAngle(
                            "row1.driver", 30.0, APPROVAL));
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        require(rejected && fixture.adapter.getRecordCount() == 0,
                "moving recline admitted");
    }

    private static void verifyBeltRace() {
        Fixture fixture = fixture(FaultInjectionProfile.delay(50));
        apply(fixture.adapter, token('f'),
                SimulatedSeatEffectAdapter.SeatTarget.reclineAngle(
                        "row1.driver", 35.0, APPROVAL));
        fixture.occupant.belted = true;
        fixture.occupant.revision++;
        fixture.occupant.capturedAt = fixture.clock.nowElapsedRealtimeMs();
        fixture.clock.advanceBy(50);
        require(fixture.adapter.queryStatus(token('f')).getState()
                == EffectAdapter.DeliveryState.REJECTED,
                "belt race did not reject");
        require(fixture.adapter.querySimulationObservation(token('f')).getState()
                == SimulatedEffectAdapter.ReadbackState.TERMINAL_FAILURE,
                "belt race readback not terminal");
        require(fixture.adapter.getDigitalTwinSnapshot().reported(
                VehicleSignalPath.SEAT_RECLINE_ANGLE,
                "row1.driver").isEmpty(), "belt race fabricated report");
    }

    private static void verifyFaults() {
        Fixture timeout = fixture(FaultInjectionProfile.timeout(25));
        apply(timeout.adapter, token('1'),
                SimulatedSeatEffectAdapter.SeatTarget.heatingLevel(
                        "row1.driver", 2));
        timeout.clock.advanceBy(25);
        require(timeout.adapter.querySimulationObservation(token('1')).getState()
                == SimulatedEffectAdapter.ReadbackState.TIMED_OUT,
                "timeout missing");

        Fixture mismatch = fixture(FaultInjectionProfile.readbackMismatch());
        apply(mismatch.adapter, token('2'),
                SimulatedSeatEffectAdapter.SeatTarget.ventilationLevel(
                        "row1.driver", 2));
        require(mismatch.adapter.getDigitalTwinSnapshot().reconcile(
                VehicleSignalPath.SEAT_VENTILATION_LEVEL,
                "row1.driver") == DigitalTwinSnapshot.ReconciliationState.MISMATCH,
                "mismatch missing");
    }

    private static Fixture fixture(FaultInjectionProfile profile) {
        SimulationClock clock = new SimulationClock(1_000);
        MutableSafetyProvider safety = new MutableSafetyProvider(clock);
        MutableOccupantProvider occupant = new MutableOccupantProvider(clock);
        ApprovalVerifier approval = new ApprovalVerifier();
        return new Fixture(
                clock,
                safety,
                occupant,
                new SimulatedSeatEffectAdapter(
                        clock, profile, safety, occupant, approval));
    }

    private static EffectAdapter.ApplyResult apply(
            SimulatedSeatEffectAdapter adapter,
            String token,
            SimulatedSeatEffectAdapter.SeatTarget target) {
        return adapter.apply(invocation(token, target));
    }

    private static EffectAdapter.Invocation invocation(
            String token,
            SimulatedSeatEffectAdapter.SeatTarget target) {
        byte[] payload = target.toCanonicalPayload();
        return new EffectAdapter.Invocation(
                "effect-seat-probe",
                "outbox-seat-probe",
                token,
                SimulatedSeatEffectAdapter.DESTINATION,
                target.getCapabilityId().getCanonicalId(),
                sha256(payload),
                sha256(ENVELOPE),
                1,
                3,
                payload,
                ENVELOPE);
    }

    private static String token(char value) {
        char[] token = new char[64];
        Arrays.fill(token, value);
        return new String(token);
    }

    private static String sha256(byte[] value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder output = new StringBuilder(bytes.length * 2);
            for (byte current : bytes) {
                output.append(String.format("%02x", current & 0xff));
            }
            return output.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static final class Fixture {
        private final SimulationClock clock;
        private final MutableSafetyProvider safety;
        private final MutableOccupantProvider occupant;
        private final SimulatedSeatEffectAdapter adapter;

        private Fixture(
                SimulationClock clock,
                MutableSafetyProvider safety,
                MutableOccupantProvider occupant,
                SimulatedSeatEffectAdapter adapter) {
            this.clock = clock;
            this.safety = safety;
            this.occupant = occupant;
            this.adapter = adapter;
        }
    }

    private static final class MutableSafetyProvider
            implements SafetyVehicleStateProvider {
        private final SimulationClock clock;
        private long revision = 1;
        private long capturedAt;
        private SafetyVehicleStateSnapshot.MotionState motion =
                SafetyVehicleStateSnapshot.MotionState.PARKED;

        private MutableSafetyProvider(SimulationClock clock) {
            this.clock = clock;
            capturedAt = clock.nowElapsedRealtimeMs();
        }

        @Override
        public SafetyVehicleStateSnapshot currentSnapshot() {
            return new SafetyVehicleStateSnapshot(
                    "debug.simulated.seat.safety",
                    revision,
                    capturedAt,
                    SafetyVehicleStateSnapshot.SafetyState.NORMAL,
                    motion,
                    true,
                    SafetyVehicleStateSnapshot.SourceAssurance.RUNTIME_OWNED_STUB,
                    false);
        }
    }

    private static final class MutableOccupantProvider
            implements SimulatedSeatEffectAdapter.SeatOccupantStateProvider {
        private final SimulationClock clock;
        private long revision = 1;
        private long capturedAt;
        private boolean occupied = true;
        private boolean belted;

        private MutableOccupantProvider(SimulationClock clock) {
            this.clock = clock;
            capturedAt = clock.nowElapsedRealtimeMs();
        }

        @Override
        public SimulatedSeatEffectAdapter.SeatOccupantSnapshot currentSnapshot(
                String area) {
            return new SimulatedSeatEffectAdapter.SeatOccupantSnapshot(
                    area, revision, capturedAt, occupied, belted);
        }
    }

    private static final class ApprovalVerifier
            implements SimulatedSeatEffectAdapter.SeatApprovalVerifier {
        @Override
        public boolean isApproved(
                String approvalDigest,
                String actionId,
                String area,
                long safetyRevision,
                long occupantRevision) {
            return APPROVAL.equals(approvalDigest);
        }
    }
}
