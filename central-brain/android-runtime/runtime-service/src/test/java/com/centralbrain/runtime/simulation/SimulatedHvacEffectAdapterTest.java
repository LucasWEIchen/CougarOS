package com.centralbrain.runtime.simulation;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

import com.centralbrain.runtime.effects.EffectAdapter;
import com.centralbrain.runtime.vehicle.capability.VehicleCapability;
import com.centralbrain.runtime.vehicle.schema.SignalSource;
import com.centralbrain.runtime.vehicle.schema.VehicleSignalPath;
import com.centralbrain.runtime.vehicle.twin.DigitalTwinSnapshot;

import org.junit.Test;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public final class SimulatedHvacEffectAdapterTest {
    private static final byte[] ENVELOPE = "hvac-envelope-v1".getBytes();

    @Test
    public void typedTargetRoundTripsAndDescriptorStaysDebugOnly() {
        SimulatedHvacEffectAdapter adapter = adapter(FaultInjectionProfile.none());
        assertEquals(SimulatedHvacEffectAdapter.ADAPTER_ID, adapter.descriptor().getAdapterId());
        assertEquals(SimulatedHvacEffectAdapter.DESTINATION,
                adapter.descriptor().getDestination());
        assertTrue(adapter.simulationDescriptor().isSimulation());
        assertFalse(adapter.simulationDescriptor().isProductionAuthorized());
        assertEquals(3, adapter.getSupportedCapabilities().size());

        for (SimulatedHvacEffectAdapter.HvacTarget target : new SimulatedHvacEffectAdapter.HvacTarget[] {
                SimulatedHvacEffectAdapter.HvacTarget.power(true),
                SimulatedHvacEffectAdapter.HvacTarget.targetTemperature(
                        "row1.driver", 22.5),
                SimulatedHvacEffectAdapter.HvacTarget.fanLevel("cabin", 4)
        }) {
            byte[] payload = target.toCanonicalPayload();
            SimulatedHvacEffectAdapter.HvacTarget decoded =
                    SimulatedHvacEffectAdapter.HvacTarget.fromCanonicalPayload(payload);
            assertEquals(target.getCapabilityId(), decoded.getCapabilityId());
            assertEquals(target.getArea(), decoded.getArea());
            assertArrayEquals(payload, decoded.toCanonicalPayload());
        }
    }

    @Test
    public void immediateTargetsUpdateDesiredAndReportedAbsoluteState() {
        SimulatedHvacEffectAdapter adapter = adapter(FaultInjectionProfile.none());
        apply(adapter, token('a'), SimulatedHvacEffectAdapter.HvacTarget.power(true));
        apply(adapter, token('b'), SimulatedHvacEffectAdapter.HvacTarget.targetTemperature(
                "row1.driver", 22.5));
        apply(adapter, token('c'), SimulatedHvacEffectAdapter.HvacTarget.fanLevel(
                "cabin", 4));

        DigitalTwinSnapshot snapshot = adapter.getDigitalTwinSnapshot();
        assertEquals(DigitalTwinSnapshot.ReconciliationState.MATCHED,
                snapshot.reconcile(VehicleSignalPath.HVAC_ACTIVE, "cabin"));
        assertEquals(DigitalTwinSnapshot.ReconciliationState.MATCHED,
                snapshot.reconcile(
                        VehicleSignalPath.HVAC_TARGET_TEMPERATURE,
                        "row1.driver"));
        assertEquals(DigitalTwinSnapshot.ReconciliationState.MATCHED,
                snapshot.reconcile(VehicleSignalPath.HVAC_FAN_LEVEL, "cabin"));
        assertTrue(snapshot.reported(VehicleSignalPath.HVAC_ACTIVE, "cabin")
                .orElseThrow().getValue().getBooleanValue());
        assertEquals(22.5, snapshot.reported(
                VehicleSignalPath.HVAC_TARGET_TEMPERATURE,
                "row1.driver").orElseThrow().getValue().getDecimalValue(), 0.0);
        assertEquals(4, snapshot.reported(
                VehicleSignalPath.HVAC_FAN_LEVEL,
                "cabin").orElseThrow().getValue().getIntegerValue());
        assertEquals(SignalSource.SIMULATED, snapshot.reported(
                VehicleSignalPath.HVAC_FAN_LEVEL,
                "cabin").orElseThrow().getValue().getSource());
    }

    @Test
    public void rangeZoneActionAndCanonicalPayloadFailClosed() {
        SimulatedHvacEffectAdapter adapter = adapter(FaultInjectionProfile.none());
        assertThrows(IllegalArgumentException.class, () -> apply(
                adapter,
                token('d'),
                SimulatedHvacEffectAdapter.HvacTarget.targetTemperature(
                        "row1.driver", 15.5)));
        assertThrows(IllegalArgumentException.class, () -> apply(
                adapter,
                token('e'),
                SimulatedHvacEffectAdapter.HvacTarget.fanLevel("cabin", 8)));
        assertThrows(IllegalArgumentException.class, () -> apply(
                adapter,
                token('f'),
                SimulatedHvacEffectAdapter.HvacTarget.targetTemperature(
                        "cabin", 22.0)));

        SimulatedHvacEffectAdapter.HvacTarget target =
                SimulatedHvacEffectAdapter.HvacTarget.power(true);
        assertThrows(IllegalArgumentException.class, () -> adapter.apply(invocation(
                token('1'),
                VehicleCapability.CapabilityId.HVAC_FAN_LEVEL.getCanonicalId(),
                target.toCanonicalPayload())));
        byte[] trailing = Arrays.copyOf(
                target.toCanonicalPayload(),
                target.toCanonicalPayload().length + 1);
        assertThrows(IllegalArgumentException.class, () -> adapter.apply(invocation(
                token('2'),
                target.getCapabilityId().getCanonicalId(),
                trailing)));
        assertEquals(0, adapter.getRecordCount());
        assertEquals(0, adapter.getDigitalTwinRevision());
    }

    @Test
    public void manualDelayPublishesDesiredBeforeReportedAndRemainsIdempotent() {
        SimulationClock clock = new SimulationClock(1_000);
        SimulatedHvacEffectAdapter adapter = new SimulatedHvacEffectAdapter(
                clock, FaultInjectionProfile.delay(100));
        SimulatedHvacEffectAdapter.HvacTarget target =
                SimulatedHvacEffectAdapter.HvacTarget.targetTemperature(
                        "row1.driver", 23.0);
        EffectAdapter.Invocation invocation = invocation(token('3'), target);

        assertEquals(EffectAdapter.ApplyState.UNKNOWN, adapter.apply(invocation).getState());
        assertEquals(DigitalTwinSnapshot.ReconciliationState.PENDING_REPORTED,
                adapter.getDigitalTwinSnapshot().reconcile(
                        VehicleSignalPath.HVAC_TARGET_TEMPERATURE,
                        "row1.driver"));
        assertEquals(1, adapter.getDigitalTwinRevision());
        clock.advanceBy(99);
        assertEquals(EffectAdapter.DeliveryState.UNKNOWN,
                adapter.queryStatus(token('3')).getState());
        clock.advanceBy(1);
        assertEquals(EffectAdapter.DeliveryState.APPLIED,
                adapter.queryStatus(token('3')).getState());
        assertEquals(DigitalTwinSnapshot.ReconciliationState.MATCHED,
                adapter.getDigitalTwinSnapshot().reconcile(
                        VehicleSignalPath.HVAC_TARGET_TEMPERATURE,
                        "row1.driver"));
        long completedRevision = adapter.getDigitalTwinRevision();
        adapter.apply(invocation);
        adapter.queryStatus(token('3'));
        assertEquals(completedRevision, adapter.getDigitalTwinRevision());
    }

    @Test
    public void timeoutKeepsDesiredPendingWithoutFabricatedReport() {
        SimulationClock clock = new SimulationClock(2_000);
        SimulatedHvacEffectAdapter adapter = new SimulatedHvacEffectAdapter(
                clock, FaultInjectionProfile.timeout(50));
        SimulatedHvacEffectAdapter.HvacTarget target =
                SimulatedHvacEffectAdapter.HvacTarget.fanLevel("cabin", 5);
        apply(adapter, token('4'), target);
        clock.advanceBy(50);

        assertEquals(EffectAdapter.DeliveryState.UNKNOWN,
                adapter.queryStatus(token('4')).getState());
        assertEquals(SimulatedEffectAdapter.ReadbackState.TIMED_OUT,
                adapter.querySimulationObservation(token('4')).getState());
        DigitalTwinSnapshot snapshot = adapter.getDigitalTwinSnapshot();
        assertEquals(DigitalTwinSnapshot.ReconciliationState.PENDING_REPORTED,
                snapshot.reconcile(VehicleSignalPath.HVAC_FAN_LEVEL, "cabin"));
        assertTrue(snapshot.reported(VehicleSignalPath.HVAC_FAN_LEVEL, "cabin")
                .isEmpty());
    }

    @Test
    public void retryableAndTerminalFailureNeverWriteReportedState() {
        SimulatedHvacEffectAdapter retryable =
                adapter(FaultInjectionProfile.retryableFailure());
        SimulatedHvacEffectAdapter terminal =
                adapter(FaultInjectionProfile.terminalFailure());
        SimulatedHvacEffectAdapter.HvacTarget target =
                SimulatedHvacEffectAdapter.HvacTarget.power(true);

        assertEquals(EffectAdapter.ApplyState.RETRYABLE_FAILURE,
                apply(retryable, token('5'), target).getState());
        assertEquals(EffectAdapter.ApplyState.TERMINAL_FAILURE,
                apply(terminal, token('6'), target).getState());
        assertTrue(retryable.getDigitalTwinSnapshot()
                .reported(VehicleSignalPath.HVAC_ACTIVE, "cabin").isEmpty());
        assertTrue(terminal.getDigitalTwinSnapshot()
                .reported(VehicleSignalPath.HVAC_ACTIVE, "cabin").isEmpty());
        assertEquals(DigitalTwinSnapshot.ReconciliationState.PENDING_REPORTED,
                retryable.getDigitalTwinSnapshot().reconcile(
                        VehicleSignalPath.HVAC_ACTIVE, "cabin"));
        assertEquals(DigitalTwinSnapshot.ReconciliationState.PENDING_REPORTED,
                terminal.getDigitalTwinSnapshot().reconcile(
                        VehicleSignalPath.HVAC_ACTIVE, "cabin"));
    }

    @Test
    public void readbackMismatchIsVisibleInBaseObservationAndDigitalTwin() {
        SimulatedHvacEffectAdapter adapter =
                adapter(FaultInjectionProfile.readbackMismatch());
        SimulatedHvacEffectAdapter.HvacTarget target =
                SimulatedHvacEffectAdapter.HvacTarget.targetTemperature(
                        "row1.passenger", 22.0);
        assertEquals(EffectAdapter.ApplyState.APPLIED,
                apply(adapter, token('7'), target).getState());

        assertEquals(SimulatedEffectAdapter.ReadbackState.MISMATCH,
                adapter.querySimulationObservation(token('7')).getState());
        DigitalTwinSnapshot snapshot = adapter.getDigitalTwinSnapshot();
        assertEquals(DigitalTwinSnapshot.ReconciliationState.MISMATCH,
                snapshot.reconcile(
                        VehicleSignalPath.HVAC_TARGET_TEMPERATURE,
                        "row1.passenger"));
        assertEquals(22.5, snapshot.reported(
                VehicleSignalPath.HVAC_TARGET_TEMPERATURE,
                "row1.passenger").orElseThrow().getValue().getDecimalValue(), 0.0);
        adapter.reset();
        assertEquals(0, adapter.getDigitalTwinRevision());
        assertEquals(DigitalTwinSnapshot.ReconciliationState.NO_DESIRED,
                adapter.getDigitalTwinSnapshot().reconcile(
                        VehicleSignalPath.HVAC_TARGET_TEMPERATURE,
                        "row1.passenger"));
    }

    private static SimulatedHvacEffectAdapter adapter(FaultInjectionProfile profile) {
        return new SimulatedHvacEffectAdapter(new SimulationClock(1_000), profile);
    }

    private static EffectAdapter.ApplyResult apply(
            SimulatedHvacEffectAdapter adapter,
            String token,
            SimulatedHvacEffectAdapter.HvacTarget target) {
        return adapter.apply(invocation(token, target));
    }

    private static EffectAdapter.Invocation invocation(
            String token,
            SimulatedHvacEffectAdapter.HvacTarget target) {
        return invocation(
                token,
                target.getCapabilityId().getCanonicalId(),
                target.toCanonicalPayload());
    }

    private static EffectAdapter.Invocation invocation(
            String token,
            String actionId,
            byte[] payload) {
        return new EffectAdapter.Invocation(
                "effect-hvac",
                "outbox-hvac",
                token,
                SimulatedHvacEffectAdapter.DESTINATION,
                actionId,
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
}
