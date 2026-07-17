package com.centralbrain.runtime.vehicle.schema;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;

import org.junit.Test;

public final class SignalValueTest {
    private static final long SOURCE_EPOCH_MS = 1_750_000_000_000L;
    private static final long RECEIVED_ELAPSED_MS = 50_000L;

    @Test
    public void acceptsAllowlistedPathsTypedScalarsUnitsAreasAndFreshness() {
        SignalValue speed = SignalValue.ofDecimal(
                VehicleSignalPath.fromCanonicalPath("Vehicle.Speed"),
                42.5,
                "km/h",
                "global",
                timestamp(),
                SignalQuality.VALID,
                SignalSource.AAOS,
                1);
        speed.validateFreshness(RECEIVED_ELAPSED_MS + 500);
        assertEquals(42.5, speed.getDecimalValue(), 0.0);
        assertEquals("Vehicle.Speed", speed.getPath().getCanonicalPath());
        assertTrue(speed.getSource().isHardwareBackedCandidate());

        SignalValue occupied = SignalValue.ofBoolean(
                VehicleSignalPath.SEAT_OCCUPIED,
                true,
                "",
                "row1.driver",
                timestamp(),
                SignalQuality.VALID,
                SignalSource.VENDOR,
                2);
        occupied.validateFreshness(RECEIVED_ELAPSED_MS + 1_000);
        assertTrue(occupied.getBooleanValue());

        SignalValue fan = SignalValue.ofInteger(
                VehicleSignalPath.HVAC_FAN_LEVEL,
                3,
                "level",
                "cabin",
                timestamp(),
                SignalQuality.STALE,
                SignalSource.SIMULATED,
                3);
        fan.validateFreshness(RECEIVED_ELAPSED_MS + 2_001);
        assertEquals(3, fan.getIntegerValue());
        assertTrue(fan.getSource().isSimulated());

        SignalValue gear = SignalValue.ofText(
                VehicleSignalPath.CURRENT_GEAR,
                "PARK",
                "",
                "global",
                timestamp(),
                SignalQuality.VALID,
                SignalSource.DERIVED,
                4);
        gear.validateFreshness(RECEIVED_ELAPSED_MS + 20);
        assertEquals("PARK", gear.getTextValue());
    }

    @Test
    public void rejectsUnknownPathAreaUnitAndScalarType() {
        assertThrows(
                IllegalArgumentException.class,
                () -> VehicleSignalPath.fromCanonicalPath("Vehicle.Private.VendorProperty"));
        assertThrows(IllegalArgumentException.class, () -> SignalValue.ofDecimal(
                VehicleSignalPath.VEHICLE_SPEED,
                1,
                "m/s",
                "global",
                timestamp(),
                SignalQuality.VALID,
                SignalSource.AAOS,
                1));
        assertThrows(IllegalArgumentException.class, () -> SignalValue.ofBoolean(
                VehicleSignalPath.SEAT_OCCUPIED,
                true,
                "",
                "trunk",
                timestamp(),
                SignalQuality.VALID,
                SignalSource.AAOS,
                1));
        assertThrows(IllegalArgumentException.class, () -> SignalValue.ofInteger(
                VehicleSignalPath.VEHICLE_SPEED,
                1,
                "km/h",
                "global",
                timestamp(),
                SignalQuality.VALID,
                SignalSource.AAOS,
                1));
    }

    @Test
    public void rejectsFreshnessQualityMismatchAndFutureReceiveTime() {
        SignalValue incorrectlyValid = SignalValue.ofDecimal(
                VehicleSignalPath.VEHICLE_SPEED,
                0,
                "km/h",
                "global",
                timestamp(),
                SignalQuality.VALID,
                SignalSource.AAOS,
                1);
        assertThrows(
                IllegalArgumentException.class,
                () -> incorrectlyValid.validateFreshness(RECEIVED_ELAPSED_MS + 501));

        SignalValue incorrectlyStale = SignalValue.ofDecimal(
                VehicleSignalPath.CABIN_TEMPERATURE,
                21.0,
                "celsius",
                "cabin",
                timestamp(),
                SignalQuality.STALE,
                SignalSource.VENDOR,
                1);
        assertThrows(
                IllegalArgumentException.class,
                () -> incorrectlyStale.validateFreshness(RECEIVED_ELAPSED_MS + 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> incorrectlyValid.validateFreshness(RECEIVED_ELAPSED_MS - 1));
    }

    @Test
    public void unavailableAndErrorStatesCannotCarryScalarValues() {
        SignalValue unavailable = SignalValue.withoutValue(
                VehicleSignalPath.CABIN_TEMPERATURE,
                "celsius",
                "cabin",
                timestamp(),
                SignalQuality.UNAVAILABLE,
                SignalSource.VENDOR,
                1);
        unavailable.validateFreshness(RECEIVED_ELAPSED_MS + 100_000);
        assertFalse(unavailable.hasValue());
        assertFalse(unavailable.getQuality().isUsableForDecision());
        assertThrows(IllegalStateException.class, unavailable::getDecimalValue);

        assertThrows(IllegalArgumentException.class, () -> SignalValue.ofDecimal(
                VehicleSignalPath.CABIN_TEMPERATURE,
                21.0,
                "celsius",
                "cabin",
                timestamp(),
                SignalQuality.ERROR,
                SignalSource.VENDOR,
                1));
        assertThrows(IllegalArgumentException.class, () -> SignalValue.withoutValue(
                VehicleSignalPath.CABIN_TEMPERATURE,
                "celsius",
                "cabin",
                timestamp(),
                SignalQuality.VALID,
                SignalSource.VENDOR,
                1));
    }

    @Test
    public void rejectsInvalidScalarTimestampAndRevisionAndContainsNoObjectField() {
        assertThrows(IllegalArgumentException.class, () -> SignalValue.ofDecimal(
                VehicleSignalPath.VEHICLE_SPEED,
                Double.NaN,
                "km/h",
                "global",
                timestamp(),
                SignalQuality.VALID,
                SignalSource.AAOS,
                1));
        assertThrows(IllegalArgumentException.class, () -> SignalValue.ofText(
                VehicleSignalPath.CURRENT_GEAR,
                "P\nARK",
                "",
                "global",
                timestamp(),
                SignalQuality.VALID,
                SignalSource.AAOS,
                1));
        assertThrows(IllegalArgumentException.class, () -> new SignalTimestamp(0, 0));
        assertThrows(IllegalArgumentException.class, () -> SignalValue.ofBoolean(
                VehicleSignalPath.PARKING_BRAKE_ENGAGED,
                true,
                "",
                "global",
                timestamp(),
                SignalQuality.VALID,
                SignalSource.AAOS,
                0));

        for (Field field : SignalValue.class.getDeclaredFields()) {
            assertFalse("untyped Object field: " + field.getName(), field.getType() == Object.class);
        }
    }

    private static SignalTimestamp timestamp() {
        return new SignalTimestamp(SOURCE_EPOCH_MS, RECEIVED_ELAPSED_MS);
    }
}
