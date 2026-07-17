package com.centralbrain.runtime.simulation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.centralbrain.runtime.context.ContextSnapshot.DrivingState;
import com.centralbrain.runtime.vehicle.schema.SignalSource;
import com.centralbrain.runtime.vehicle.schema.SignalValue;
import com.centralbrain.runtime.vehicle.schema.VehicleSignalPath;

import org.junit.Test;

public final class DebugSimulationControllerTest {
    @Test
    public void defaultStateIsBoundedSimulationOnlyAndProductionUnauthorized() {
        DebugSimulationController controller = new DebugSimulationController();
        DebugSimulationController.Snapshot snapshot = controller.snapshot();

        assertEquals(0, snapshot.getRevision());
        assertEquals(DrivingState.UNKNOWN, snapshot.getDrivingState());
        assertEquals(0, snapshot.getSignals().size());
        assertEquals(0, snapshot.getFaults().size());
        assertEquals(4, controller.getAdapterCount());
        assertTrue(snapshot.isSimulationOnly());
        assertFalse(snapshot.isProductionTrusted());
        assertTrue(controller.isSimulationOnly());
        assertFalse(controller.isProductionAuthorized());
        assertTrue(snapshot.getDigest().matches("[0-9a-f]{64}"));
    }

    @Test
    public void drivingAndCanonicalTypedSignalsChangeVersionedDigest() {
        DebugSimulationController controller = new DebugSimulationController(2_000);
        String initialDigest = controller.snapshot().getDigest();

        assertEquals(1, controller.setDrivingState(DrivingState.PARKED));
        assertEquals(2, controller.setSignal(
                VehicleSignalPath.VEHICLE_SPEED,
                "global",
                SignalValue.ScalarType.DECIMAL,
                false, 0, 0.0, ""));
        assertEquals(3, controller.setSignal(
                VehicleSignalPath.CURRENT_GEAR,
                "global",
                SignalValue.ScalarType.TEXT,
                false, 0, 0.0, "P"));

        DebugSimulationController.Snapshot snapshot = controller.snapshot();
        assertEquals(DrivingState.PARKED, snapshot.getDrivingState());
        assertEquals(2, snapshot.getSignals().size());
        assertEquals(SignalSource.SIMULATED, controller.getSignal(
                VehicleSignalPath.CURRENT_GEAR, "global").orElseThrow().getSource());
        assertNotEquals(initialDigest, snapshot.getDigest());
    }

    @Test
    public void scalarUnionAndCanonicalPathAreaFailClosed() {
        DebugSimulationController controller = new DebugSimulationController();

        assertThrows(IllegalArgumentException.class, () -> controller.setSignal(
                VehicleSignalPath.VEHICLE_SPEED,
                "global",
                SignalValue.ScalarType.INTEGER,
                false, 0, 0.0, ""));
        assertThrows(IllegalArgumentException.class, () -> controller.setSignal(
                VehicleSignalPath.VEHICLE_SPEED,
                "row1.driver",
                SignalValue.ScalarType.DECIMAL,
                false, 0, 20.0, ""));
        assertThrows(IllegalArgumentException.class, () -> controller.setSignal(
                VehicleSignalPath.CURRENT_GEAR,
                "global",
                SignalValue.ScalarType.TEXT,
                false, 7, 0.0, "P"));

        assertEquals(0, controller.snapshot().getRevision());
        assertEquals(3, controller.snapshot().getAuditEntryCount());
        assertTrue(controller.auditEntries().stream().allMatch(
                entry -> entry.getOutcome() == DebugSimulationController.Outcome.REJECTED));
    }

    @Test
    public void fixedAdapterFaultRegistryUpdatesActualSimulatedAdapters() {
        DebugSimulationController controller = new DebugSimulationController();
        controller.setAdapterFault(
                SimulatedHvacEffectAdapter.ADAPTER_ID,
                FaultInjectionProfile.delay(50));
        controller.setAdapterFault(
                SimulatedSeatEffectAdapter.ADAPTER_ID,
                FaultInjectionProfile.timeout(75));
        controller.setAdapterFault(
                SimulatedMediaEffectAdapter.ADAPTER_ID,
                FaultInjectionProfile.retryableFailure());
        controller.setAdapterFault(
                SimulatedNavigationEffectAdapter.ADAPTER_ID,
                FaultInjectionProfile.readbackMismatch());

        assertEquals(4, controller.snapshot().getFaults().size());
        assertEquals(FaultInjectionProfile.Mode.DELAY,
                controller.getAdapterFault(SimulatedHvacEffectAdapter.ADAPTER_ID).getMode());
        assertEquals(FaultInjectionProfile.Mode.TIMEOUT,
                controller.getAdapterFault(SimulatedSeatEffectAdapter.ADAPTER_ID).getMode());
        assertEquals(FaultInjectionProfile.Mode.RETRYABLE_FAILURE,
                controller.getAdapterFault(SimulatedMediaEffectAdapter.ADAPTER_ID).getMode());
        assertEquals(FaultInjectionProfile.Mode.READBACK_MISMATCH,
                controller.getAdapterFault(SimulatedNavigationEffectAdapter.ADAPTER_ID).getMode());
        assertThrows(IllegalArgumentException.class, () -> controller.setAdapterFault(
                "debug.simulated.unknown.v1", FaultInjectionProfile.none()));
    }

    @Test
    public void clockAndResetClearStateButRetainBoundedAudit() {
        DebugSimulationController controller = new DebugSimulationController(4_000);
        controller.setDrivingState(DrivingState.MOVING);
        controller.setSignal(
                VehicleSignalPath.HVAC_ACTIVE,
                "cabin",
                SignalValue.ScalarType.BOOLEAN,
                true, 0, 0.0, "");
        controller.setAdapterFault(
                SimulatedMediaEffectAdapter.ADAPTER_ID,
                FaultInjectionProfile.terminalFailure());
        controller.advanceSimulationClock(500);
        assertEquals(4_500, controller.snapshot().getElapsedRealtimeMs());

        long resetRevision = controller.reset();
        DebugSimulationController.Snapshot reset = controller.snapshot();
        assertEquals(5, resetRevision);
        assertEquals(DrivingState.UNKNOWN, reset.getDrivingState());
        assertTrue(reset.getSignals().isEmpty());
        assertTrue(reset.getFaults().isEmpty());
        assertEquals(4_000, reset.getElapsedRealtimeMs());
        assertEquals(5, reset.getAuditEntryCount());
        assertEquals(FaultInjectionProfile.Mode.NONE,
                controller.getAdapterFault(SimulatedMediaEffectAdapter.ADAPTER_ID).getMode());
    }

    @Test
    public void auditEvictsOldestWithoutRetainingRawSignalText() {
        DebugSimulationController controller = new DebugSimulationController();
        controller.setSignal(
                VehicleSignalPath.CURRENT_GEAR,
                "global",
                SignalValue.ScalarType.TEXT,
                false, 0, 0.0, "SENSITIVE_GEAR_FIXTURE");
        for (int index = 0; index < DebugSimulationController.MAX_AUDIT_ENTRIES + 5; index++) {
            controller.setDrivingState((index & 1) == 0
                    ? DrivingState.PARKED : DrivingState.MOVING);
        }

        assertEquals(DebugSimulationController.MAX_AUDIT_ENTRIES,
                controller.snapshot().getAuditEntryCount());
        assertEquals(7, controller.auditEntries().get(0).getSequence());
        assertTrue(controller.auditEntries().stream().allMatch(
                entry -> entry.getTargetDigest().matches("[0-9a-f]{64}")));
        assertFalse(controller.auditEntries().toString().contains("SENSITIVE_GEAR_FIXTURE"));
    }

    @Test
    public void sameCommandSequenceProducesDeterministicSnapshotDigest() {
        DebugSimulationController first = new DebugSimulationController(8_000);
        DebugSimulationController second = new DebugSimulationController(8_000);

        applyDeterministicSequence(first);
        applyDeterministicSequence(second);

        assertEquals(first.snapshot().getDigest(), second.snapshot().getDigest());
    }

    private static void applyDeterministicSequence(DebugSimulationController controller) {
        controller.setDrivingState(DrivingState.PARKED);
        controller.setSignal(
                VehicleSignalPath.PARKING_BRAKE_ENGAGED,
                "global",
                SignalValue.ScalarType.BOOLEAN,
                true, 0, 0.0, "");
        controller.setAdapterFault(
                SimulatedSeatEffectAdapter.ADAPTER_ID,
                FaultInjectionProfile.delay(100));
        controller.advanceSimulationClock(100);
    }
}
