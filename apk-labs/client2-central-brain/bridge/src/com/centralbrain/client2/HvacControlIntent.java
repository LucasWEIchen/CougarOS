package com.centralbrain.client2;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Bounded, deterministic manual HVAC target carried by the frozen Session V1 envelope. */
public final class HvacControlIntent {
    public static final String WIRE_PREFIX = "HVAC1";
    public static final int MIN_TEMP_DECI_C = 160;
    public static final int MAX_TEMP_DECI_C = 300;
    public static final int TEMP_STEP_DECI_C = 5;
    public static final int MIN_FAN_LEVEL = 0;
    public static final int MAX_FAN_LEVEL = 7;

    public enum Zone { DRIVER, FRONT_PASSENGER, CABIN }

    public enum Airflow { AUTO, FACE, FEET, DEFROST }

    public enum Preset { CUSTOM, WARM, COOL, CLEAR }

    private final Zone zone;
    private final boolean power;
    private final int temperatureDeciC;
    private final int fanLevel;
    private final boolean autoMode;
    private final boolean acEnabled;
    private final boolean syncEnabled;
    private final Airflow airflow;
    private final Preset preset;

    private HvacControlIntent(
            Zone zone,
            boolean power,
            int temperatureDeciC,
            int fanLevel,
            boolean autoMode,
            boolean acEnabled,
            boolean syncEnabled,
            Airflow airflow,
            Preset preset) {
        this.zone = Objects.requireNonNull(zone, "zone");
        this.power = power;
        this.temperatureDeciC = requireTemperature(temperatureDeciC);
        this.fanLevel = requireFanLevel(fanLevel);
        this.autoMode = autoMode;
        this.acEnabled = acEnabled;
        this.syncEnabled = syncEnabled;
        this.airflow = Objects.requireNonNull(airflow, "airflow");
        this.preset = Objects.requireNonNull(preset, "preset");
    }

    public static HvacControlIntent defaults() {
        return new HvacControlIntent(
                Zone.DRIVER,
                true,
                225,
                2,
                true,
                true,
                false,
                Airflow.AUTO,
                Preset.CUSTOM);
    }

    public Zone getZone() {
        return zone;
    }

    public boolean isPowerOn() {
        return power;
    }

    public int getTemperatureDeciC() {
        return temperatureDeciC;
    }

    public int getFanLevel() {
        return fanLevel;
    }

    public boolean isAutoMode() {
        return autoMode;
    }

    public boolean isAcEnabled() {
        return acEnabled;
    }

    public boolean isSyncEnabled() {
        return syncEnabled;
    }

    public Airflow getAirflow() {
        return airflow;
    }

    public Preset getPreset() {
        return preset;
    }

    public HvacControlIntent withZone(Zone value) {
        return copy(value, power, temperatureDeciC, fanLevel, autoMode, acEnabled,
                syncEnabled, airflow, Preset.CUSTOM);
    }

    public HvacControlIntent withPower(boolean value) {
        return copy(zone, value, temperatureDeciC, fanLevel, autoMode, acEnabled,
                syncEnabled, airflow, Preset.CUSTOM);
    }

    public HvacControlIntent withTemperatureDeciC(int value) {
        return copy(zone, power, value, fanLevel, autoMode, acEnabled,
                syncEnabled, airflow, Preset.CUSTOM);
    }

    public HvacControlIntent stepTemperature(int steps) {
        long requested = (long) temperatureDeciC + (long) steps * TEMP_STEP_DECI_C;
        int bounded = (int) Math.max(MIN_TEMP_DECI_C, Math.min(MAX_TEMP_DECI_C, requested));
        return withTemperatureDeciC(bounded);
    }

    public HvacControlIntent withFanLevel(int value) {
        return copy(zone, power, temperatureDeciC, value, autoMode, acEnabled,
                syncEnabled, airflow, Preset.CUSTOM);
    }

    public HvacControlIntent stepFan(int steps) {
        long requested = (long) fanLevel + steps;
        int bounded = (int) Math.max(MIN_FAN_LEVEL, Math.min(MAX_FAN_LEVEL, requested));
        return withFanLevel(bounded);
    }

    public HvacControlIntent withAutoMode(boolean value) {
        return copy(zone, power, temperatureDeciC, fanLevel, value, acEnabled,
                syncEnabled, airflow, Preset.CUSTOM);
    }

    public HvacControlIntent withAcEnabled(boolean value) {
        return copy(zone, power, temperatureDeciC, fanLevel, autoMode, value,
                syncEnabled, airflow, Preset.CUSTOM);
    }

    public HvacControlIntent withSyncEnabled(boolean value) {
        return copy(zone, power, temperatureDeciC, fanLevel, autoMode, acEnabled,
                value, airflow, Preset.CUSTOM);
    }

    public HvacControlIntent withAirflow(Airflow value) {
        return copy(zone, power, temperatureDeciC, fanLevel, autoMode, acEnabled,
                syncEnabled, value, Preset.CUSTOM);
    }

    public HvacControlIntent nextAirflow() {
        Airflow[] values = Airflow.values();
        return withAirflow(values[(airflow.ordinal() + 1) % values.length]);
    }

    public HvacControlIntent applyPreset(Preset value) {
        Objects.requireNonNull(value, "value");
        switch (value) {
            case WARM:
                return copy(zone, true, 240, 3, true, false, syncEnabled,
                        Airflow.FEET, Preset.WARM);
            case COOL:
                return copy(zone, true, 210, 4, true, true, syncEnabled,
                        Airflow.FACE, Preset.COOL);
            case CLEAR:
                return copy(zone, true, 225, 5, false, true, syncEnabled,
                        Airflow.DEFROST, Preset.CLEAR);
            case CUSTOM:
            default:
                return copy(zone, power, temperatureDeciC, fanLevel, autoMode, acEnabled,
                        syncEnabled, airflow, Preset.CUSTOM);
        }
    }

    public String toWireValue() {
        return WIRE_PREFIX
                + "|zone=" + zone
                + "|power=" + flag(power)
                + "|temp_deci_c=" + temperatureDeciC
                + "|fan=" + fanLevel
                + "|auto=" + flag(autoMode)
                + "|ac=" + flag(acEnabled)
                + "|sync=" + flag(syncEnabled)
                + "|airflow=" + airflow
                + "|preset=" + preset;
    }

    public static HvacControlIntent parseWireValue(String value) {
        if (value == null || value.length() > 256) {
            throw new IllegalArgumentException("invalid HVAC wire value");
        }
        String[] parts = value.split("\\|", -1);
        if (parts.length != 10 || !WIRE_PREFIX.equals(parts[0])) {
            throw new IllegalArgumentException("invalid HVAC wire schema");
        }
        Map<String, String> fields = new LinkedHashMap<>();
        for (int index = 1; index < parts.length; index++) {
            int separator = parts[index].indexOf('=');
            if (separator <= 0 || separator == parts[index].length() - 1) {
                throw new IllegalArgumentException("invalid HVAC wire field");
            }
            String key = parts[index].substring(0, separator);
            String fieldValue = parts[index].substring(separator + 1);
            if (fields.put(key, fieldValue) != null) {
                throw new IllegalArgumentException("duplicate HVAC wire field");
            }
        }
        requireExactKeys(fields);
        HvacControlIntent parsed = new HvacControlIntent(
                parseEnum(Zone.class, fields.get("zone")),
                parseFlag(fields.get("power")),
                parseInteger(fields.get("temp_deci_c"), "temperature"),
                parseInteger(fields.get("fan"), "fan"),
                parseFlag(fields.get("auto")),
                parseFlag(fields.get("ac")),
                parseFlag(fields.get("sync")),
                parseEnum(Airflow.class, fields.get("airflow")),
                parseEnum(Preset.class, fields.get("preset")));
        if (!parsed.toWireValue().equals(value)) {
            throw new IllegalArgumentException("non-canonical HVAC wire value");
        }
        return parsed;
    }

    public String temperatureLabel() {
        return String.format(Locale.ROOT, "%.1f C", temperatureDeciC / 10.0d);
    }

    public String desiredSummary() {
        return zone + " · " + (power ? "ON" : "OFF")
                + " · " + temperatureLabel()
                + " · FAN " + fanLevel
                + " · " + airflow;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof HvacControlIntent)) {
            return false;
        }
        HvacControlIntent that = (HvacControlIntent) other;
        return power == that.power
                && temperatureDeciC == that.temperatureDeciC
                && fanLevel == that.fanLevel
                && autoMode == that.autoMode
                && acEnabled == that.acEnabled
                && syncEnabled == that.syncEnabled
                && zone == that.zone
                && airflow == that.airflow
                && preset == that.preset;
    }

    @Override
    public int hashCode() {
        return Objects.hash(zone, power, temperatureDeciC, fanLevel, autoMode,
                acEnabled, syncEnabled, airflow, preset);
    }

    private HvacControlIntent copy(
            Zone nextZone,
            boolean nextPower,
            int nextTemperatureDeciC,
            int nextFanLevel,
            boolean nextAutoMode,
            boolean nextAcEnabled,
            boolean nextSyncEnabled,
            Airflow nextAirflow,
            Preset nextPreset) {
        HvacControlIntent next = new HvacControlIntent(
                nextZone,
                nextPower,
                nextTemperatureDeciC,
                nextFanLevel,
                nextAutoMode,
                nextAcEnabled,
                nextSyncEnabled,
                nextAirflow,
                nextPreset);
        return equals(next) ? this : next;
    }

    private static int requireTemperature(int value) {
        if (value < MIN_TEMP_DECI_C
                || value > MAX_TEMP_DECI_C
                || value % TEMP_STEP_DECI_C != 0) {
            throw new IllegalArgumentException("temperature outside HVAC contract");
        }
        return value;
    }

    private static int requireFanLevel(int value) {
        if (value < MIN_FAN_LEVEL || value > MAX_FAN_LEVEL) {
            throw new IllegalArgumentException("fan outside HVAC contract");
        }
        return value;
    }

    private static String flag(boolean value) {
        return value ? "1" : "0";
    }

    private static boolean parseFlag(String value) {
        if ("1".equals(value)) {
            return true;
        }
        if ("0".equals(value)) {
            return false;
        }
        throw new IllegalArgumentException("invalid HVAC boolean");
    }

    private static int parseInteger(String value, String name) {
        try {
            if (value == null || value.isEmpty() || value.startsWith("+")
                    || (value.length() > 1 && value.startsWith("0"))) {
                throw new NumberFormatException(name);
            }
            return Integer.parseInt(value);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("invalid HVAC " + name, failure);
        }
    }

    private static <T extends Enum<T>> T parseEnum(Class<T> type, String value) {
        try {
            return Enum.valueOf(type, value);
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("invalid HVAC enum", failure);
        }
    }

    private static void requireExactKeys(Map<String, String> fields) {
        String[] expected = {
            "zone", "power", "temp_deci_c", "fan", "auto",
            "ac", "sync", "airflow", "preset"
        };
        if (fields.size() != expected.length) {
            throw new IllegalArgumentException("HVAC wire field count changed");
        }
        for (String key : expected) {
            if (!fields.containsKey(key)) {
                throw new IllegalArgumentException("missing HVAC wire field");
            }
        }
    }
}
