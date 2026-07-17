package com.centralbrain.runtime.vehicle.twin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.centralbrain.runtime.vehicle.schema.SignalQuality;
import com.centralbrain.runtime.vehicle.schema.SignalSource;
import com.centralbrain.runtime.vehicle.schema.SignalTimestamp;
import com.centralbrain.runtime.vehicle.schema.SignalValue;
import com.centralbrain.runtime.vehicle.schema.VehicleSignalPath;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

public final class VehicleDigitalTwinStoreTest {
    private static final long SOURCE_EPOCH_MS = 1_750_000_000_000L;

    @Test
    public void separatesDesiredReportedAndReconcilesMatchedState() {
        VehicleDigitalTwinStore store = new VehicleDigitalTwinStore();
        DesiredStateRecord desired = desiredSpeed(42.5, 1_000, 10_000);
        assertEquals(1, store.setDesired(desired, 1_000));
        assertEquals(2, store.updateReported(speed(42.5, 1_100, 1), 1_100));

        DigitalTwinSnapshot snapshot = store.snapshot(
                Set.of(VehicleSignalPath.VEHICLE_SPEED),
                1_200);
        assertEquals(2, snapshot.getRevision());
        assertEquals(1, snapshot.getDesiredRecords().size());
        assertEquals(1, snapshot.getReportedRecords().size());
        assertEquals(1, snapshot.desired(
                VehicleSignalPath.VEHICLE_SPEED, "global").orElseThrow()
                .getStoreRevision());
        assertEquals(2, snapshot.reported(
                VehicleSignalPath.VEHICLE_SPEED, "global").orElseThrow()
                .getStoreRevision());
        assertEquals(DigitalTwinSnapshot.ReconciliationState.MATCHED,
                snapshot.reconcile(VehicleSignalPath.VEHICLE_SPEED, "global"));
    }

    @Test
    public void rejectsStaleAndConflictingReportedUpdatesAndReplaysIdempotently() {
        VehicleDigitalTwinStore store = new VehicleDigitalTwinStore();
        SignalValue current = speed(10.0, 2_000, 2);
        assertEquals(1, store.updateReported(current, 2_000));
        assertEquals(1, store.updateReported(current, 2_010));
        assertThrows(
                IllegalArgumentException.class,
                () -> store.updateReported(speed(9.0, 1_999, 1), 2_010));
        assertThrows(
                IllegalArgumentException.class,
                () -> store.updateReported(speed(11.0, 2_000, 3), 2_010));
        assertEquals(1, store.updateReported(current, 3_000));
        assertEquals(1, store.getRevision());
    }

    @Test
    public void appliesReportedFreshnessAndDesiredTtlAtSnapshotTime() {
        VehicleDigitalTwinStore store = new VehicleDigitalTwinStore();
        store.setDesired(desiredSpeed(20.0, 1_000, 2_000), 1_000);
        store.updateReported(speed(20.0, 1_000, 1), 1_000);

        DigitalTwinSnapshot fresh = store.snapshot(
                Set.of(VehicleSignalPath.VEHICLE_SPEED),
                1_500);
        assertEquals(SignalQuality.VALID, fresh.reported(
                VehicleSignalPath.VEHICLE_SPEED, "global").orElseThrow()
                .getEffectiveQuality());
        assertEquals(DigitalTwinSnapshot.ReconciliationState.MATCHED,
                fresh.reconcile(VehicleSignalPath.VEHICLE_SPEED, "global"));

        DigitalTwinSnapshot stale = store.snapshot(
                Set.of(VehicleSignalPath.VEHICLE_SPEED),
                1_501);
        assertEquals(SignalQuality.STALE, stale.reported(
                VehicleSignalPath.VEHICLE_SPEED, "global").orElseThrow()
                .getEffectiveQuality());
        assertEquals(DigitalTwinSnapshot.ReconciliationState.REPORTED_STALE,
                stale.reconcile(VehicleSignalPath.VEHICLE_SPEED, "global"));

        DigitalTwinSnapshot expired = store.snapshot(
                Set.of(VehicleSignalPath.VEHICLE_SPEED),
                2_000);
        assertTrue(expired.desired(
                VehicleSignalPath.VEHICLE_SPEED, "global").isEmpty());
        assertTrue(expired.desiredIncludingExpired(
                VehicleSignalPath.VEHICLE_SPEED, "global").isPresent());
        assertEquals(DigitalTwinSnapshot.ReconciliationState.DESIRED_EXPIRED,
                expired.reconcile(VehicleSignalPath.VEHICLE_SPEED, "global"));
    }

    @Test
    public void compareAndSetDesiredIsThreadSafeAndMonotonic() throws Exception {
        VehicleDigitalTwinStore store = new VehicleDigitalTwinStore();
        int workers = 24;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int index = 0; index < workers; index++) {
            final double value = index;
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return store.compareAndSetDesired(
                        0,
                        desiredSpeed(value, 1_000, 10_000),
                        1_000);
            }));
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        int successes = 0;
        for (Future<Boolean> future : futures) {
            if (future.get(5, TimeUnit.SECONDS)) {
                successes++;
            }
        }
        executor.shutdownNow();
        assertEquals(1, successes);
        assertEquals(1, store.getRevision());
        DesiredStateRecord winner = store.desired(
                VehicleSignalPath.VEHICLE_SPEED,
                "global",
                1_100).orElseThrow();
        assertTrue(store.clearDesired(
                VehicleSignalPath.VEHICLE_SPEED,
                "global",
                winner.getStoreRevision()));
        assertEquals(2, store.getRevision());
        assertFalse(store.clearDesired(
                VehicleSignalPath.VEHICLE_SPEED,
                "global",
                winner.getStoreRevision()));
    }

    @Test
    public void snapshotIsAtomicFilteredAndImmutable() {
        VehicleDigitalTwinStore store = new VehicleDigitalTwinStore();
        store.setDesired(desiredSpeed(30.0, 1_000, 10_000), 1_000);
        store.updateReported(speed(29.0, 1_100, 1), 1_100);
        store.updateReported(cabinTemperature(22.0, 1_100, 1), 1_100);

        DigitalTwinSnapshot snapshot = store.snapshot(
                Set.of(VehicleSignalPath.VEHICLE_SPEED),
                1_200);
        assertEquals(store.getRevision(), snapshot.getRevision());
        assertEquals(1, snapshot.getReportedRecords().size());
        assertEquals(1, snapshot.getDesiredRecords().size());
        assertEquals(DigitalTwinSnapshot.ReconciliationState.MISMATCH,
                snapshot.reconcile(VehicleSignalPath.VEHICLE_SPEED, "global"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> snapshot.getReportedRecords().clear());
        assertTrue(snapshot.reported(
                VehicleSignalPath.CABIN_TEMPERATURE, "cabin").isEmpty());
        assertThrows(
                IllegalArgumentException.class,
                () -> snapshot.reported(VehicleSignalPath.VEHICLE_SPEED, "cabin"));
    }

    @Test
    public void reportsPendingAndUnavailableReconciliationWithoutConflatingState() {
        VehicleDigitalTwinStore pendingStore = new VehicleDigitalTwinStore();
        pendingStore.setDesired(desiredSpeed(0, 1_000, 10_000), 1_000);
        assertEquals(DigitalTwinSnapshot.ReconciliationState.PENDING_REPORTED,
                pendingStore.snapshot(Set.of(VehicleSignalPath.VEHICLE_SPEED), 1_100)
                        .reconcile(VehicleSignalPath.VEHICLE_SPEED, "global"));

        VehicleDigitalTwinStore unavailableStore = new VehicleDigitalTwinStore();
        unavailableStore.setDesired(desiredSpeed(0, 1_000, 10_000), 1_000);
        unavailableStore.updateReported(SignalValue.withoutValue(
                VehicleSignalPath.VEHICLE_SPEED,
                "km/h",
                "global",
                new SignalTimestamp(SOURCE_EPOCH_MS, 1_100),
                SignalQuality.UNAVAILABLE,
                SignalSource.SIMULATED,
                1), 1_100);
        assertEquals(DigitalTwinSnapshot.ReconciliationState.REPORTED_UNAVAILABLE,
                unavailableStore.snapshot(Set.of(VehicleSignalPath.VEHICLE_SPEED), 1_200)
                        .reconcile(VehicleSignalPath.VEHICLE_SPEED, "global"));
    }

    private static DesiredStateRecord desiredSpeed(
            double value,
            long requested,
            long expires) {
        return DesiredStateRecord.ofDecimal(
                VehicleSignalPath.VEHICLE_SPEED,
                value,
                "km/h",
                "global",
                requested,
                expires);
    }

    private static SignalValue speed(double value, long received, long sourceRevision) {
        return SignalValue.ofDecimal(
                VehicleSignalPath.VEHICLE_SPEED,
                value,
                "km/h",
                "global",
                new SignalTimestamp(SOURCE_EPOCH_MS + received, received),
                SignalQuality.VALID,
                SignalSource.SIMULATED,
                sourceRevision);
    }

    private static SignalValue cabinTemperature(
            double value,
            long received,
            long sourceRevision) {
        return SignalValue.ofDecimal(
                VehicleSignalPath.CABIN_TEMPERATURE,
                value,
                "celsius",
                "cabin",
                new SignalTimestamp(SOURCE_EPOCH_MS + received, received),
                SignalQuality.VALID,
                SignalSource.SIMULATED,
                sourceRevision);
    }
}
