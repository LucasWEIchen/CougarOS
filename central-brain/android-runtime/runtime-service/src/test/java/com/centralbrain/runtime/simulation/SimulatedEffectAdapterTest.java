package com.centralbrain.runtime.simulation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.centralbrain.runtime.effects.EffectAdapter;
import com.centralbrain.runtime.effects.EffectAdapterContract;
import com.centralbrain.runtime.vehicle.schema.SignalSource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.junit.Test;

public final class SimulatedEffectAdapterTest {
    private static final String DESTINATION = "UIB_ACTION";
    private static final String TOKEN_A = "a".repeat(64);
    private static final String TOKEN_B = "b".repeat(64);
    private static final String TOKEN_C = "c".repeat(64);
    private static final byte[] PAYLOAD = "payload".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ENVELOPE = "envelope".getBytes(StandardCharsets.UTF_8);

    @Test
    public void descriptorIsSafeExplicitlySimulatedAndNeverProductionAuthorized() {
        TrackingAdapter adapter = adapter(FaultInjectionProfile.none());

        assertSame(adapter.descriptor(),
                EffectAdapterContract.requireSafe(adapter, DESTINATION));
        assertEquals("debug.simulated.base", adapter.simulationDescriptor().getAdapterId());
        assertEquals(DESTINATION, adapter.simulationDescriptor().getDestination());
        assertEquals(SignalSource.SIMULATED,
                adapter.simulationDescriptor().getObservationSource());
        assertTrue(adapter.simulationDescriptor().isSimulation());
        assertFalse(adapter.simulationDescriptor().isProductionAuthorized());
        assertTrue(adapter.getFaultInjectionProfile().isSimulationOnly());
        assertFalse(adapter.getFaultInjectionProfile().isProductionAuthorized());
    }

    @Test
    public void immediateApplyIsIdempotentAndTokenConflictFailsClosed() {
        TrackingAdapter adapter = adapter(FaultInjectionProfile.none());
        EffectAdapter.Invocation invocation = invocation(TOKEN_A, "climate.setPower");

        EffectAdapter.ApplyResult first = adapter.apply(invocation);
        EffectAdapter.ApplyResult replay = adapter.apply(invocation);

        assertSame(first, replay);
        assertEquals(EffectAdapter.ApplyState.APPLIED, first.getState());
        assertEquals(1, adapter.getRecordCount());
        assertEquals(1, adapter.appliedCallbacks);
        assertEquals(EffectAdapter.DeliveryState.APPLIED,
                adapter.queryStatus(TOKEN_A).getState());
        assertEquals(SimulatedEffectAdapter.ReadbackState.MATCHED,
                adapter.querySimulationObservation(TOKEN_A).getState());
        assertThrows(IllegalStateException.class,
                () -> adapter.apply(invocation(TOKEN_A, "climate.setFan")));
        assertThrows(IllegalArgumentException.class,
                () -> adapter.apply(invocation(TOKEN_B, "climate.setPower", "OTHER")));
    }

    @Test
    public void manualClockCompletesDelayWithoutSleepingOrDuplicateCallback() {
        SimulationClock clock = new SimulationClock(10_000);
        TrackingAdapter adapter = new TrackingAdapter(
                clock, FaultInjectionProfile.delay(1_000));
        EffectAdapter.Invocation invocation = invocation(TOKEN_A, "climate.setPower");

        EffectAdapter.ApplyResult result = adapter.apply(invocation);
        assertEquals(EffectAdapter.ApplyState.UNKNOWN, result.getState());
        assertEquals(EffectAdapter.DeliveryState.UNKNOWN,
                adapter.queryStatus(TOKEN_A).getState());
        assertEquals(SimulatedEffectAdapter.ReadbackState.PENDING,
                adapter.querySimulationObservation(TOKEN_A).getState());
        assertEquals(0, adapter.appliedCallbacks);

        clock.advanceBy(999);
        assertEquals(EffectAdapter.DeliveryState.UNKNOWN,
                adapter.queryStatus(TOKEN_A).getState());
        clock.advanceBy(1);
        assertEquals(EffectAdapter.DeliveryState.APPLIED,
                adapter.queryStatus(TOKEN_A).getState());
        assertEquals(SimulatedEffectAdapter.ReadbackState.MATCHED,
                adapter.querySimulationObservation(TOKEN_A).getState());
        assertEquals(1, adapter.appliedCallbacks);
        assertSame(result, adapter.apply(invocation));
        assertEquals(1, adapter.appliedCallbacks);
    }

    @Test
    public void timeoutRemainsUnknownAndReadbackBecomesTimedOut() {
        SimulationClock clock = new SimulationClock(20_000);
        TrackingAdapter adapter = new TrackingAdapter(
                clock, FaultInjectionProfile.timeout(2_000));

        assertEquals(EffectAdapter.ApplyState.UNKNOWN,
                adapter.apply(invocation(TOKEN_A, "seat.setHeating")).getState());
        assertEquals(SimulatedEffectAdapter.ReadbackState.PENDING,
                adapter.querySimulationObservation(TOKEN_A).getState());
        clock.advanceBy(2_000);
        assertEquals(EffectAdapter.DeliveryState.UNKNOWN,
                adapter.queryStatus(TOKEN_A).getState());
        assertEquals(SimulatedEffectAdapter.ReadbackState.TIMED_OUT,
                adapter.querySimulationObservation(TOKEN_A).getState());
        assertEquals(0, adapter.appliedCallbacks);
    }

    @Test
    public void retryableAndTerminalFailuresStayDistinct() {
        TrackingAdapter adapter = adapter(FaultInjectionProfile.retryableFailure());

        assertEquals(EffectAdapter.ApplyState.RETRYABLE_FAILURE,
                adapter.apply(invocation(TOKEN_A, "media.pause")).getState());
        assertEquals(EffectAdapter.DeliveryState.NOT_APPLIED,
                adapter.queryStatus(TOKEN_A).getState());
        assertEquals(SimulatedEffectAdapter.ReadbackState.RETRYABLE_FAILURE,
                adapter.querySimulationObservation(TOKEN_A).getState());

        adapter.setFaultInjectionProfile(FaultInjectionProfile.terminalFailure());
        assertEquals(EffectAdapter.ApplyState.TERMINAL_FAILURE,
                adapter.apply(invocation(TOKEN_B, "media.pause")).getState());
        assertEquals(EffectAdapter.DeliveryState.REJECTED,
                adapter.queryStatus(TOKEN_B).getState());
        assertEquals(SimulatedEffectAdapter.ReadbackState.TERMINAL_FAILURE,
                adapter.querySimulationObservation(TOKEN_B).getState());
        assertEquals(0, adapter.appliedCallbacks);
    }

    @Test
    public void readbackMismatchDoesNotRewriteAppliedDeliveryState() {
        TrackingAdapter adapter = adapter(FaultInjectionProfile.readbackMismatch());
        EffectAdapter.ApplyResult applied = adapter.apply(
                invocation(TOKEN_C, "seat.setRecline"));
        EffectAdapter.StatusResult delivery = adapter.queryStatus(TOKEN_C);
        SimulatedEffectAdapter.SimulationObservation readback =
                adapter.querySimulationObservation(TOKEN_C);

        assertEquals(EffectAdapter.ApplyState.APPLIED, applied.getState());
        assertEquals(EffectAdapter.DeliveryState.APPLIED, delivery.getState());
        assertEquals(applied.getEvidenceDigest(), delivery.getEvidenceDigest());
        assertEquals(SimulatedEffectAdapter.ReadbackState.MISMATCH,
                readback.getState());
        assertEquals(SignalSource.SIMULATED, readback.getSource());
        assertFalse(readback.isProductionTrusted());
        assertEquals(1, adapter.appliedCallbacks);
    }

    @Test
    public void clockFaultBoundsAndResetFailClosed() {
        SimulationClock clock = new SimulationClock(0);
        assertThrows(IllegalArgumentException.class, () -> clock.advanceBy(0));
        assertThrows(IllegalArgumentException.class,
                () -> clock.advanceBy(SimulationClock.MAX_ADVANCE_MS + 1));
        assertThrows(IllegalArgumentException.class,
                () -> FaultInjectionProfile.delay(0));
        assertThrows(IllegalArgumentException.class,
                () -> FaultInjectionProfile.timeout(
                        FaultInjectionProfile.MAX_DURATION_MS + 1));
        assertNotEquals(FaultInjectionProfile.none().getDigest(),
                FaultInjectionProfile.readbackMismatch().getDigest());

        TrackingAdapter adapter = new TrackingAdapter(
                clock, FaultInjectionProfile.none());
        adapter.apply(invocation(TOKEN_A, "climate.setPower"));
        adapter.reset();
        assertEquals(0, adapter.getRecordCount());
        assertEquals(1, adapter.resetCallbacks);
        assertEquals(EffectAdapter.DeliveryState.NOT_APPLIED,
                adapter.queryStatus(TOKEN_A).getState());
    }

    private static TrackingAdapter adapter(FaultInjectionProfile profile) {
        return new TrackingAdapter(new SimulationClock(1_000), profile);
    }

    private static EffectAdapter.Invocation invocation(String token, String actionId) {
        return invocation(token, actionId, DESTINATION);
    }

    private static EffectAdapter.Invocation invocation(
            String token, String actionId, String destination) {
        return new EffectAdapter.Invocation(
                "effect-1",
                "outbox-1",
                token,
                destination,
                actionId,
                sha256(PAYLOAD),
                sha256(ENVELOPE),
                1,
                3,
                PAYLOAD,
                ENVELOPE);
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

    private static final class TrackingAdapter extends SimulatedEffectAdapter {
        private int appliedCallbacks;
        private int resetCallbacks;

        private TrackingAdapter(
                SimulationClock clock, FaultInjectionProfile profile) {
            super("debug.simulated.base", DESTINATION, clock, profile);
        }

        @Override
        protected void onSimulationApplied(
                Invocation invocation, FaultInjectionProfile profile) {
            appliedCallbacks++;
        }

        @Override
        protected void onSimulationReset() {
            resetCallbacks++;
        }
    }
}
