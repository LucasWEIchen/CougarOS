package com.centralbrain.runtime.simulation;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

import com.centralbrain.runtime.effects.EffectAdapter;
import com.centralbrain.runtime.governance.SafetyVehicleStateSnapshot;
import com.centralbrain.runtime.vehicle.capability.VehicleCapability;
import com.centralbrain.runtime.vehicle.schema.SignalSource;
import com.centralbrain.runtime.vehicle.schema.VehicleSignalPath;
import com.centralbrain.runtime.vehicle.twin.DigitalTwinSnapshot;

import org.junit.Test;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public final class SimulatedSeatEffectAdapterTest {
    private static final byte[] ENVELOPE = "seat-envelope-v1".getBytes();
    private static final String APPROVAL = token('a');

    @Test
    public void typedTargetRoundTripsAndProvidersStaySimulationOnly() {
        Fixture fixture = fixture(FaultInjectionProfile.none());
        assertEquals(SimulatedSeatEffectAdapter.ADAPTER_ID,
                fixture.adapter.descriptor().getAdapterId());
        assertEquals(3, fixture.adapter.getSupportedCapabilities().size());
        assertTrue(fixture.approval.isSimulationOnly());
        assertFalse(fixture.approval.isProductionAuthorized());

        for (SimulatedSeatEffectAdapter.SeatTarget target :
                new SimulatedSeatEffectAdapter.SeatTarget[] {
                        SimulatedSeatEffectAdapter.SeatTarget.heatingLevel(
                                "row1.driver", 2),
                        SimulatedSeatEffectAdapter.SeatTarget.ventilationLevel(
                                "row1.passenger", 3),
                        SimulatedSeatEffectAdapter.SeatTarget.reclineAngle(
                                "row1.driver", 30.0, APPROVAL)
                }) {
            byte[] payload = target.toCanonicalPayload();
            SimulatedSeatEffectAdapter.SeatTarget decoded =
                    SimulatedSeatEffectAdapter.SeatTarget.fromCanonicalPayload(payload);
            assertEquals(target.getCapabilityId(), decoded.getCapabilityId());
            assertEquals(target.getArea(), decoded.getArea());
            assertArrayEquals(payload, decoded.toCanonicalPayload());
        }
    }

    @Test
    public void heatingAndVentilationUpdateAbsoluteDesiredAndReported() {
        Fixture fixture = fixture(FaultInjectionProfile.none());
        apply(fixture.adapter, token('b'),
                SimulatedSeatEffectAdapter.SeatTarget.heatingLevel(
                        "row1.driver", 2));
        apply(fixture.adapter, token('c'),
                SimulatedSeatEffectAdapter.SeatTarget.ventilationLevel(
                        "row1.driver", 3));

        DigitalTwinSnapshot snapshot = fixture.adapter.getDigitalTwinSnapshot();
        assertEquals(DigitalTwinSnapshot.ReconciliationState.MATCHED,
                snapshot.reconcile(
                        VehicleSignalPath.SEAT_HEATING_LEVEL,
                        "row1.driver"));
        assertEquals(DigitalTwinSnapshot.ReconciliationState.MATCHED,
                snapshot.reconcile(
                        VehicleSignalPath.SEAT_VENTILATION_LEVEL,
                        "row1.driver"));
        assertEquals(2, snapshot.reported(
                VehicleSignalPath.SEAT_HEATING_LEVEL,
                "row1.driver").orElseThrow().getValue().getIntegerValue());
        assertEquals(SignalSource.SIMULATED, snapshot.reported(
                VehicleSignalPath.SEAT_VENTILATION_LEVEL,
                "row1.driver").orElseThrow().getValue().getSource());
    }

    @Test
    public void parkedApprovedReclinePublishesBoundedProgressAndReadback() {
        Fixture fixture = fixture(FaultInjectionProfile.delay(100));
        SimulatedSeatEffectAdapter.SeatTarget target =
                SimulatedSeatEffectAdapter.SeatTarget.reclineAngle(
                        "row1.driver", 30.0, APPROVAL);
        assertEquals(EffectAdapter.ApplyState.UNKNOWN,
                apply(fixture.adapter, token('d'), target).getState());
        assertEquals(DigitalTwinSnapshot.ReconciliationState.PENDING_REPORTED,
                fixture.adapter.getDigitalTwinSnapshot().reconcile(
                        VehicleSignalPath.SEAT_RECLINE_ANGLE,
                        "row1.driver"));

        fixture.clock.advanceBy(50);
        SimulatedSeatEffectAdapter.SeatProgressObservation halfway =
                fixture.adapter.querySeatProgress(token('d'));
        assertEquals(50, halfway.getPercent());
        assertEquals(15.0, halfway.getProjectedAngle(), 0.0);
        assertFalse(halfway.isTerminal());
        assertEquals(SignalSource.SIMULATED, halfway.getSource());
        assertFalse(halfway.isProductionTrusted());

        fixture.clock.advanceBy(50);
        SimulatedSeatEffectAdapter.SeatProgressObservation complete =
                fixture.adapter.querySeatProgress(token('d'));
        assertEquals(100, complete.getPercent());
        assertEquals(30.0, complete.getProjectedAngle(), 0.0);
        assertTrue(complete.isTerminal());
        assertEquals(DigitalTwinSnapshot.ReconciliationState.MATCHED,
                fixture.adapter.getDigitalTwinSnapshot().reconcile(
                        VehicleSignalPath.SEAT_RECLINE_ANGLE,
                        "row1.driver"));
    }

    @Test
    public void movingUnknownBeltedOrUnoccupiedReclineRejectsBeforeAdmission() {
        Fixture moving = fixture(FaultInjectionProfile.none());
        moving.safety.motion = SafetyVehicleStateSnapshot.MotionState.MOVING;
        assertReclineRejected(moving, token('e'));

        Fixture unknown = fixture(FaultInjectionProfile.none());
        unknown.safety.motion = SafetyVehicleStateSnapshot.MotionState.UNKNOWN;
        assertReclineRejected(unknown, token('f'));

        Fixture belted = fixture(FaultInjectionProfile.none());
        belted.occupant.belted = true;
        assertReclineRejected(belted, token('1'));

        Fixture unoccupied = fixture(FaultInjectionProfile.none());
        unoccupied.occupant.occupied = false;
        assertReclineRejected(unoccupied, token('2'));
    }

    @Test
    public void beltChangeRacePermanentlyRejectsAtDispatchWithoutReportedState() {
        Fixture fixture = fixture(FaultInjectionProfile.delay(100));
        apply(fixture.adapter, token('3'),
                SimulatedSeatEffectAdapter.SeatTarget.reclineAngle(
                        "row1.driver", 40.0, APPROVAL));
        fixture.occupant.belted = true;
        fixture.occupant.revision++;
        fixture.occupant.capturedAt = fixture.clock.nowElapsedRealtimeMs();
        fixture.clock.advanceBy(100);

        assertEquals(EffectAdapter.DeliveryState.REJECTED,
                fixture.adapter.queryStatus(token('3')).getState());
        assertEquals(SimulatedEffectAdapter.ReadbackState.TERMINAL_FAILURE,
                fixture.adapter.querySimulationObservation(token('3')).getState());
        assertTrue(fixture.adapter.getDigitalTwinSnapshot().reported(
                VehicleSignalPath.SEAT_RECLINE_ANGLE,
                "row1.driver").isEmpty());
        SimulatedSeatEffectAdapter.SeatProgressObservation progress =
                fixture.adapter.querySeatProgress(token('3'));
        assertTrue(progress.isTerminal());
        assertEquals(99, progress.getPercent());
    }

    @Test
    public void motionAndApprovalChangesAreRevalidatedAtDispatch() {
        Fixture motion = fixture(FaultInjectionProfile.delay(50));
        apply(motion.adapter, token('4'),
                SimulatedSeatEffectAdapter.SeatTarget.reclineAngle(
                        "row1.driver", 20.0, APPROVAL));
        motion.safety.motion = SafetyVehicleStateSnapshot.MotionState.MOVING;
        motion.safety.revision++;
        motion.safety.capturedAt = motion.clock.nowElapsedRealtimeMs();
        motion.clock.advanceBy(50);
        assertEquals(EffectAdapter.DeliveryState.REJECTED,
                motion.adapter.queryStatus(token('4')).getState());

        Fixture approval = fixture(FaultInjectionProfile.delay(50));
        apply(approval.adapter, token('5'),
                SimulatedSeatEffectAdapter.SeatTarget.reclineAngle(
                        "row1.driver", 20.0, APPROVAL));
        approval.approval.approved = false;
        approval.clock.advanceBy(50);
        assertEquals(EffectAdapter.DeliveryState.REJECTED,
                approval.adapter.queryStatus(token('5')).getState());
    }

    @Test
    public void rangeAreaApprovalAndCanonicalPayloadFailClosed() {
        Fixture fixture = fixture(FaultInjectionProfile.none());
        assertThrows(IllegalArgumentException.class, () -> apply(
                fixture.adapter,
                token('6'),
                SimulatedSeatEffectAdapter.SeatTarget.heatingLevel(
                        "row1.driver", 4)));
        assertThrows(IllegalArgumentException.class, () -> apply(
                fixture.adapter,
                token('7'),
                SimulatedSeatEffectAdapter.SeatTarget.reclineAngle(
                        "cabin", 20.0, APPROVAL)));
        assertThrows(IllegalArgumentException.class, () ->
                SimulatedSeatEffectAdapter.SeatTarget.reclineAngle(
                        "row1.driver", 20.0, "bad"));

        SimulatedSeatEffectAdapter.SeatTarget heat =
                SimulatedSeatEffectAdapter.SeatTarget.heatingLevel(
                        "row1.driver", 2);
        byte[] trailing = Arrays.copyOf(
                heat.toCanonicalPayload(),
                heat.toCanonicalPayload().length + 1);
        assertThrows(IllegalArgumentException.class, () -> fixture.adapter.apply(
                invocation(token('8'), heat.getCapabilityId(), trailing)));
        assertEquals(0, fixture.adapter.getRecordCount());
        assertEquals(0, fixture.adapter.getDigitalTwinRevision());
    }

    @Test
    public void timeoutRetryMismatchAndDuplicateStayObservableAndIdempotent() {
        Fixture timeout = fixture(FaultInjectionProfile.timeout(50));
        apply(timeout.adapter, token('9'),
                SimulatedSeatEffectAdapter.SeatTarget.heatingLevel(
                        "row1.driver", 2));
        timeout.clock.advanceBy(50);
        assertEquals(SimulatedEffectAdapter.ReadbackState.TIMED_OUT,
                timeout.adapter.querySimulationObservation(token('9')).getState());
        assertTrue(timeout.adapter.getDigitalTwinSnapshot().reported(
                VehicleSignalPath.SEAT_HEATING_LEVEL,
                "row1.driver").isEmpty());

        Fixture retry = fixture(FaultInjectionProfile.retryableFailure());
        assertEquals(EffectAdapter.ApplyState.RETRYABLE_FAILURE,
                apply(retry.adapter, token('0'),
                        SimulatedSeatEffectAdapter.SeatTarget.ventilationLevel(
                                "row1.driver", 2)).getState());

        Fixture mismatch = fixture(FaultInjectionProfile.readbackMismatch());
        EffectAdapter.Invocation invocation = invocation(
                token('a'),
                SimulatedSeatEffectAdapter.SeatTarget.heatingLevel(
                        "row1.driver", 2));
        mismatch.adapter.apply(invocation);
        assertEquals(DigitalTwinSnapshot.ReconciliationState.MISMATCH,
                mismatch.adapter.getDigitalTwinSnapshot().reconcile(
                        VehicleSignalPath.SEAT_HEATING_LEVEL,
                        "row1.driver"));
        long revision = mismatch.adapter.getDigitalTwinRevision();
        mismatch.adapter.apply(invocation);
        assertEquals(revision, mismatch.adapter.getDigitalTwinRevision());
    }

    private static void assertReclineRejected(Fixture fixture, String token) {
        assertThrows(IllegalStateException.class, () -> apply(
                fixture.adapter,
                token,
                SimulatedSeatEffectAdapter.SeatTarget.reclineAngle(
                        "row1.driver", 30.0, APPROVAL)));
        assertEquals(0, fixture.adapter.getRecordCount());
        assertEquals(0, fixture.adapter.getDigitalTwinRevision());
    }

    private static Fixture fixture(FaultInjectionProfile profile) {
        SimulationClock clock = new SimulationClock(1_000);
        MutableSafetyProvider safety = new MutableSafetyProvider(clock);
        MutableOccupantProvider occupant = new MutableOccupantProvider(clock);
        MutableApprovalVerifier approval = new MutableApprovalVerifier();
        return new Fixture(
                clock,
                safety,
                occupant,
                approval,
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
        return invocation(token, target.getCapabilityId(), target.toCanonicalPayload());
    }

    private static EffectAdapter.Invocation invocation(
            String token,
            VehicleCapability.CapabilityId capabilityId,
            byte[] payload) {
        return new EffectAdapter.Invocation(
                "effect-seat",
                "outbox-seat",
                token,
                SimulatedSeatEffectAdapter.DESTINATION,
                capabilityId.getCanonicalId(),
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

    private static final class Fixture {
        private final SimulationClock clock;
        private final MutableSafetyProvider safety;
        private final MutableOccupantProvider occupant;
        private final MutableApprovalVerifier approval;
        private final SimulatedSeatEffectAdapter adapter;

        private Fixture(
                SimulationClock clock,
                MutableSafetyProvider safety,
                MutableOccupantProvider occupant,
                MutableApprovalVerifier approval,
                SimulatedSeatEffectAdapter adapter) {
            this.clock = clock;
            this.safety = safety;
            this.occupant = occupant;
            this.approval = approval;
            this.adapter = adapter;
        }
    }

    private static final class MutableSafetyProvider
            implements com.centralbrain.runtime.governance.SafetyVehicleStateProvider {
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

    private static final class MutableApprovalVerifier
            implements SimulatedSeatEffectAdapter.SeatApprovalVerifier {
        private boolean approved = true;

        @Override
        public boolean isApproved(
                String approvalDigest,
                String actionId,
                String area,
                long safetyRevision,
                long occupantRevision) {
            return approved && APPROVAL.equals(approvalDigest);
        }
    }
}
