package com.centralbrain.runtime.context;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.centralbrain.runtime.context.ContextSnapshot.DrivingState;
import com.centralbrain.runtime.context.ContextSnapshot.FieldState;
import com.centralbrain.runtime.context.ContextSnapshot.SeatZone;
import com.centralbrain.runtime.context.ContextSnapshot.SourceMode;
import com.centralbrain.runtime.context.ContextSnapshot.TrustLevel;
import com.centralbrain.runtime.governance.SafetyVehicleStateSnapshot;
import com.centralbrain.runtime.governance.SafetyVehicleStateSnapshot.MotionState;
import com.centralbrain.runtime.governance.SafetyVehicleStateSnapshot.SafetyState;
import com.centralbrain.runtime.governance.SafetyVehicleStateSnapshot.SourceAssurance;
import com.centralbrain.runtime.vehicle.schema.SignalQuality;
import com.centralbrain.runtime.vehicle.schema.SignalSource;
import com.centralbrain.runtime.vehicle.schema.SignalTimestamp;
import com.centralbrain.runtime.vehicle.schema.SignalValue;
import com.centralbrain.runtime.vehicle.schema.VehicleSignalPath;
import com.centralbrain.runtime.vehicle.twin.DigitalTwinSnapshot;
import com.centralbrain.runtime.vehicle.twin.VehicleDigitalTwinStore;

import java.util.Set;

import org.junit.Test;

public final class ContextSnapshotBuilderTest {
    private static final long SOURCE_EPOCH_MS = 1_750_000_000_000L;
    private final ContextSnapshotBuilder builder = new ContextSnapshotBuilder();

    @Test
    public void buildsDeterministicVersionedParkedContextFromOneTwinRevision() {
        DigitalTwinSnapshot twin = generalTwin(
                0,
                "P",
                true,
                1_000,
                SignalSource.SIMULATED);
        SafetyVehicleStateSnapshot runtime = runtimeState(
                1_000,
                SafetyState.NORMAL,
                MotionState.PARKED,
                SourceAssurance.RUNTIME_OWNED_STUB,
                false);

        ContextSnapshot first = builder.build(
                twin,
                runtime,
                ContextFieldPolicy.general(),
                SeatZone.ROW1_DRIVER,
                false);
        ContextSnapshot second = builder.build(
                twin,
                runtime,
                ContextFieldPolicy.general(),
                SeatZone.ROW1_DRIVER,
                false);

        assertEquals(ContextSnapshot.SCHEMA_VERSION, first.getSchemaVersion());
        assertEquals(3, first.getTwinRevision());
        assertEquals(5, first.getFields().size());
        assertEquals(DrivingState.PARKED, first.getDrivingState());
        assertEquals(SafetyState.NORMAL, first.getSafetyState());
        assertEquals(SourceMode.SIMULATED, first.getSourceMode());
        assertTrue(first.isRuntimeStateFresh());
        assertTrue(first.isRequiredFreshnessComplete());
        assertFalse(first.hasMotionConflict());
        assertFalse(first.isRestricted());
        assertFalse(first.isProductionTrusted());
        assertTrue(first.getMissingRequiredFields().isEmpty());
        assertEquals(3, first.getNonProductionTrustedFields().size());
        assertTrue(first.getContextId().matches("ctx-[0-9a-f]{24}"));
        assertTrue(first.getDigest().matches("[0-9a-f]{64}"));
        assertEquals(first.getContextId(), second.getContextId());
        assertEquals(first.getDigest(), second.getDigest());
        assertEquals(FieldState.AVAILABLE, first.field(
                VehicleSignalPath.VEHICLE_SPEED,
                "global").orElseThrow().getState());
        assertEquals(TrustLevel.SIMULATED, first.field(
                VehicleSignalPath.VEHICLE_SPEED,
                "global").orElseThrow().getTrustLevel());
        assertThrows(
                UnsupportedOperationException.class,
                () -> first.getFields().clear());
    }

    @Test
    public void missingRequiredFieldFailsClosedWithExplicitFreshnessReport() {
        VehicleDigitalTwinStore store = new VehicleDigitalTwinStore();
        store.updateReported(gear("P", 1_000, SignalSource.SIMULATED), 1_000);
        store.updateReported(brake(true, 1_000, SignalSource.SIMULATED), 1_000);
        DigitalTwinSnapshot twin = store.snapshot(generalPaths(), 1_000);

        ContextSnapshot context = builder.build(
                twin,
                runtimeState(1_000, SafetyState.NORMAL, MotionState.PARKED,
                        SourceAssurance.RUNTIME_OWNED_STUB, false),
                ContextFieldPolicy.general(),
                SeatZone.CABIN,
                false);

        assertTrue(context.isRestricted());
        assertFalse(context.isRequiredFreshnessComplete());
        assertEquals(DrivingState.UNKNOWN, context.getDrivingState());
        assertEquals(1, context.getMissingRequiredFields().size());
        assertEquals(VehicleSignalPath.VEHICLE_SPEED,
                context.getMissingRequiredFields().get(0).getPath());
    }

    @Test
    public void reportsStaleAndConflictWithoutConflatingThemWithMissing() {
        VehicleDigitalTwinStore store = new VehicleDigitalTwinStore();
        store.updateReported(SignalValue.ofDecimal(
                VehicleSignalPath.VEHICLE_SPEED,
                0,
                "km/h",
                "global",
                timestamp(1_000),
                SignalQuality.STALE,
                SignalSource.SIMULATED,
                1), 1_600);
        store.updateReported(SignalValue.withoutValue(
                VehicleSignalPath.CURRENT_GEAR,
                "",
                "global",
                timestamp(1_600),
                SignalQuality.CONFLICT,
                SignalSource.SIMULATED,
                1), 1_600);
        store.updateReported(brake(true, 1_600, SignalSource.SIMULATED), 1_600);

        ContextSnapshot context = builder.build(
                store.snapshot(generalPaths(), 1_600),
                runtimeState(1_600, SafetyState.NORMAL, MotionState.PARKED,
                        SourceAssurance.RUNTIME_OWNED_STUB, false),
                ContextFieldPolicy.general(),
                SeatZone.CABIN,
                false);

        assertTrue(context.isRestricted());
        assertFalse(context.isRequiredFreshnessComplete());
        assertTrue(context.getMissingRequiredFields().isEmpty());
        assertEquals(VehicleSignalPath.VEHICLE_SPEED,
                context.getStaleFields().get(0).getPath());
        assertEquals(VehicleSignalPath.CURRENT_GEAR,
                context.getConflictFields().get(0).getPath());
    }

    @Test
    public void derivesMovingStateButLeavesActionRestrictionToSafetyPolicy() {
        DigitalTwinSnapshot twin = generalTwin(
                35,
                "D",
                false,
                2_000,
                SignalSource.SIMULATED);
        ContextSnapshot context = builder.build(
                twin,
                runtimeState(2_000, SafetyState.NORMAL, MotionState.MOVING,
                        SourceAssurance.RUNTIME_OWNED_STUB, false),
                ContextFieldPolicy.general(),
                SeatZone.ROW1_DRIVER,
                false);

        assertEquals(DrivingState.MOVING, context.getDrivingState());
        assertFalse(context.hasMotionConflict());
        assertFalse(context.isRestricted());
    }

    @Test
    public void motionDisagreementUsesConservativeStateAndFailsClosed() {
        DigitalTwinSnapshot twin = generalTwin(
                35,
                "D",
                false,
                2_000,
                SignalSource.SIMULATED);
        ContextSnapshot context = builder.build(
                twin,
                runtimeState(2_000, SafetyState.NORMAL, MotionState.PARKED,
                        SourceAssurance.RUNTIME_OWNED_STUB, false),
                ContextFieldPolicy.general(),
                SeatZone.ROW1_DRIVER,
                false);

        assertEquals(DrivingState.MOVING, context.getDrivingState());
        assertTrue(context.hasMotionConflict());
        assertTrue(context.isRestricted());
    }

    @Test
    public void seatReclinePolicyRequiresResolvedSeatSafetyFields() {
        VehicleDigitalTwinStore store = baseStore(
                0,
                "P",
                true,
                3_000,
                SignalSource.SIMULATED);
        store.updateReported(seatBoolean(
                VehicleSignalPath.SEAT_OCCUPIED,
                true,
                3_000), 3_000);
        store.updateReported(seatBoolean(
                VehicleSignalPath.SEAT_BELTED,
                true,
                3_000), 3_000);
        store.updateReported(SignalValue.ofDecimal(
                VehicleSignalPath.SEAT_RECLINE_ANGLE,
                15,
                "degree",
                "row1.driver",
                timestamp(3_000),
                SignalQuality.VALID,
                SignalSource.SIMULATED,
                1), 3_000);
        DigitalTwinSnapshot twin = store.snapshot(Set.of(
                VehicleSignalPath.VEHICLE_SPEED,
                VehicleSignalPath.CURRENT_GEAR,
                VehicleSignalPath.PARKING_BRAKE_ENGAGED,
                VehicleSignalPath.HVAC_ACTIVE,
                VehicleSignalPath.CABIN_TEMPERATURE,
                VehicleSignalPath.SEAT_OCCUPIED,
                VehicleSignalPath.SEAT_BELTED,
                VehicleSignalPath.SEAT_RECLINE_ANGLE), 3_000);
        SafetyVehicleStateSnapshot runtime = runtimeState(
                3_000,
                SafetyState.NORMAL,
                MotionState.PARKED,
                SourceAssurance.RUNTIME_OWNED_STUB,
                false);

        ContextSnapshot resolved = builder.build(
                twin,
                runtime,
                ContextFieldPolicy.seatRecline(),
                SeatZone.ROW1_DRIVER,
                false);
        ContextSnapshot unresolved = builder.build(
                twin,
                runtime,
                ContextFieldPolicy.seatRecline(),
                SeatZone.UNSPECIFIED,
                false);

        assertFalse(resolved.isRestricted());
        assertTrue(resolved.getMissingRequiredFields().isEmpty());
        assertTrue(unresolved.isRestricted());
        assertEquals(3, unresolved.getMissingRequiredFields().size());
        assertTrue(unresolved.getMissingRequiredFields().stream()
                .allMatch(key -> key.getArea().isEmpty()));
    }

    @Test
    public void digestBindsPolicyMemoryAndValuesAndCollectionsAreImmutable() {
        DigitalTwinSnapshot twin = generalTwin(
                0,
                "P",
                true,
                4_000,
                SignalSource.SIMULATED);
        SafetyVehicleStateSnapshot runtime = runtimeState(
                4_000,
                SafetyState.NORMAL,
                MotionState.PARKED,
                SourceAssurance.RUNTIME_OWNED_STUB,
                false);
        ContextSnapshot baseline = builder.build(
                twin,
                runtime,
                ContextFieldPolicy.general(),
                SeatZone.ROW1_DRIVER,
                false);
        ContextSnapshot memoryEnabled = builder.build(
                twin,
                runtime,
                ContextFieldPolicy.general(),
                SeatZone.ROW1_DRIVER,
                true);
        ContextSnapshot seatPolicy = builder.build(
                twin,
                runtime,
                ContextFieldPolicy.seatComfort(),
                SeatZone.ROW1_DRIVER,
                false);
        ContextSnapshot changedValue = builder.build(
                generalTwin(0.4, "P", true, 4_000, SignalSource.SIMULATED),
                runtime,
                ContextFieldPolicy.general(),
                SeatZone.ROW1_DRIVER,
                false);

        assertNotEquals(baseline.getDigest(), memoryEnabled.getDigest());
        assertNotEquals(baseline.getDigest(), seatPolicy.getDigest());
        assertNotEquals(baseline.getDigest(), changedValue.getDigest());
        assertThrows(
                UnsupportedOperationException.class,
                () -> seatPolicy.getMissingRequiredFields().clear());
    }

    @Test
    public void staleRuntimeStateRestrictsAndFutureRuntimeStateIsRejected() {
        DigitalTwinSnapshot twin = generalTwin(
                0,
                "P",
                true,
                5_000,
                SignalSource.SIMULATED);
        ContextSnapshot staleRuntime = builder.build(
                twin,
                runtimeState(3_999, SafetyState.NORMAL, MotionState.PARKED,
                        SourceAssurance.RUNTIME_OWNED_STUB, false),
                ContextFieldPolicy.general(),
                SeatZone.CABIN,
                false);

        assertFalse(staleRuntime.isRuntimeStateFresh());
        assertEquals(SafetyState.UNKNOWN, staleRuntime.getSafetyState());
        assertEquals(DrivingState.UNKNOWN, staleRuntime.getDrivingState());
        assertTrue(staleRuntime.isRestricted());
        assertThrows(
                IllegalArgumentException.class,
                () -> builder.build(
                        twin,
                        runtimeState(5_001, SafetyState.NORMAL, MotionState.PARKED,
                                SourceAssurance.RUNTIME_OWNED_STUB, false),
                        ContextFieldPolicy.general(),
                        SeatZone.CABIN,
                        false));
    }

    @Test
    public void platformSourceAndTrustedRuntimeStillCannotClaimProductionTrust() {
        DigitalTwinSnapshot twin = generalTwin(
                0,
                "P",
                true,
                6_000,
                SignalSource.AAOS);
        ContextSnapshot context = builder.build(
                twin,
                runtimeState(6_000, SafetyState.NORMAL, MotionState.PARKED,
                        SourceAssurance.PLATFORM_TRUSTED_ADAPTER, true),
                ContextFieldPolicy.general(),
                SeatZone.CABIN,
                false);

        assertEquals(SourceMode.PLATFORM_UNVERIFIED, context.getSourceMode());
        assertFalse(context.isProductionTrusted());
        assertFalse(context.isRestricted());
    }

    private static DigitalTwinSnapshot generalTwin(
            double speed,
            String gear,
            boolean brake,
            long captured,
            SignalSource source) {
        return baseStore(speed, gear, brake, captured, source)
                .snapshot(generalPaths(), captured);
    }

    private static VehicleDigitalTwinStore baseStore(
            double speed,
            String gear,
            boolean brake,
            long received,
            SignalSource source) {
        VehicleDigitalTwinStore store = new VehicleDigitalTwinStore();
        store.updateReported(speed(speed, received, source), received);
        store.updateReported(gear(gear, received, source), received);
        store.updateReported(brake(brake, received, source), received);
        return store;
    }

    private static Set<VehicleSignalPath> generalPaths() {
        return Set.of(
                VehicleSignalPath.VEHICLE_SPEED,
                VehicleSignalPath.CURRENT_GEAR,
                VehicleSignalPath.PARKING_BRAKE_ENGAGED,
                VehicleSignalPath.HVAC_ACTIVE,
                VehicleSignalPath.CABIN_TEMPERATURE);
    }

    private static SafetyVehicleStateSnapshot runtimeState(
            long captured,
            SafetyState safetyState,
            MotionState motionState,
            SourceAssurance sourceAssurance,
            boolean hardwareBacked) {
        return new SafetyVehicleStateSnapshot(
                "context-test-state",
                7,
                captured,
                safetyState,
                motionState,
                true,
                sourceAssurance,
                hardwareBacked);
    }

    private static SignalValue speed(double value, long received, SignalSource source) {
        return SignalValue.ofDecimal(
                VehicleSignalPath.VEHICLE_SPEED,
                value,
                "km/h",
                "global",
                timestamp(received),
                SignalQuality.VALID,
                source,
                1);
    }

    private static SignalValue gear(String value, long received, SignalSource source) {
        return SignalValue.ofText(
                VehicleSignalPath.CURRENT_GEAR,
                value,
                "",
                "global",
                timestamp(received),
                SignalQuality.VALID,
                source,
                1);
    }

    private static SignalValue brake(boolean value, long received, SignalSource source) {
        return SignalValue.ofBoolean(
                VehicleSignalPath.PARKING_BRAKE_ENGAGED,
                value,
                "",
                "global",
                timestamp(received),
                SignalQuality.VALID,
                source,
                1);
    }

    private static SignalValue seatBoolean(
            VehicleSignalPath path,
            boolean value,
            long received) {
        return SignalValue.ofBoolean(
                path,
                value,
                "",
                "row1.driver",
                timestamp(received),
                SignalQuality.VALID,
                SignalSource.SIMULATED,
                1);
    }

    private static SignalTimestamp timestamp(long received) {
        return new SignalTimestamp(SOURCE_EPOCH_MS + received, received);
    }
}
