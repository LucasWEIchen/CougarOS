package com.centralbrain.client2;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Bounded, deterministic manual seat target carried by the frozen Session V1 envelope. */
public final class SeatControlIntent {
    public static final String WIRE_PREFIX = "SEAT1";
    public static final int MIN_COMFORT_LEVEL = 0;
    public static final int MAX_COMFORT_LEVEL = 3;
    public static final int MIN_RECLINE_DEGREES = 0;
    public static final int MAX_RECLINE_DEGREES = 60;

    public enum Zone { DRIVER, FRONT_PASSENGER, REAR_LEFT, REAR_RIGHT }

    public enum Massage { OFF, RELAX, WAKE }

    public enum Preset { CUSTOM, UPRIGHT, COMFORT, REST }

    private final Zone zone;
    private final int heatLevel;
    private final int ventilationLevel;
    private final Massage massage;
    private final int reclineDegrees;
    private final Preset preset;

    private SeatControlIntent(
            Zone zone,
            int heatLevel,
            int ventilationLevel,
            Massage massage,
            int reclineDegrees,
            Preset preset) {
        this.zone = Objects.requireNonNull(zone, "zone");
        this.heatLevel = requireComfortLevel(heatLevel, "heat");
        this.ventilationLevel = requireComfortLevel(ventilationLevel, "ventilation");
        if (heatLevel > 0 && ventilationLevel > 0) {
            throw new IllegalArgumentException("seat heat and ventilation are mutually exclusive");
        }
        this.massage = Objects.requireNonNull(massage, "massage");
        this.reclineDegrees = requireRecline(reclineDegrees);
        this.preset = Objects.requireNonNull(preset, "preset");
    }

    public static SeatControlIntent defaults() {
        return new SeatControlIntent(
                Zone.DRIVER,
                0,
                0,
                Massage.OFF,
                0,
                Preset.UPRIGHT);
    }

    public Zone getZone() {
        return zone;
    }

    public int getHeatLevel() {
        return heatLevel;
    }

    public int getVentilationLevel() {
        return ventilationLevel;
    }

    public Massage getMassage() {
        return massage;
    }

    public int getReclineDegrees() {
        return reclineDegrees;
    }

    public Preset getPreset() {
        return preset;
    }

    public SeatControlIntent withZone(Zone value) {
        return copy(value, heatLevel, ventilationLevel, massage, reclineDegrees, preset);
    }

    public SeatControlIntent withHeatLevel(int value) {
        return copy(zone, value, value > 0 ? 0 : ventilationLevel,
                massage, reclineDegrees, Preset.CUSTOM);
    }

    public SeatControlIntent stepHeat(int steps) {
        long requested = (long) heatLevel + steps;
        int bounded = (int) Math.max(MIN_COMFORT_LEVEL,
                Math.min(MAX_COMFORT_LEVEL, requested));
        return withHeatLevel(bounded);
    }

    public SeatControlIntent withVentilationLevel(int value) {
        return copy(zone, value > 0 ? 0 : heatLevel, value,
                massage, reclineDegrees, Preset.CUSTOM);
    }

    public SeatControlIntent stepVentilation(int steps) {
        long requested = (long) ventilationLevel + steps;
        int bounded = (int) Math.max(MIN_COMFORT_LEVEL,
                Math.min(MAX_COMFORT_LEVEL, requested));
        return withVentilationLevel(bounded);
    }

    public SeatControlIntent withMassage(Massage value) {
        return copy(zone, heatLevel, ventilationLevel,
                Objects.requireNonNull(value, "value"), reclineDegrees, Preset.CUSTOM);
    }

    public SeatControlIntent nextMassage() {
        Massage[] values = Massage.values();
        return withMassage(values[(massage.ordinal() + 1) % values.length]);
    }

    public SeatControlIntent withReclineDegrees(int value) {
        return copy(zone, heatLevel, ventilationLevel, massage, value, Preset.CUSTOM);
    }

    public SeatControlIntent stepRecline(int steps) {
        long requested = (long) reclineDegrees + steps;
        int bounded = (int) Math.max(MIN_RECLINE_DEGREES,
                Math.min(MAX_RECLINE_DEGREES, requested));
        return withReclineDegrees(bounded);
    }

    public SeatControlIntent applyPreset(Preset value) {
        Objects.requireNonNull(value, "value");
        switch (value) {
            case UPRIGHT:
                return copy(zone, heatLevel, ventilationLevel, Massage.OFF, 0, Preset.UPRIGHT);
            case COMFORT:
                return copy(zone, heatLevel, ventilationLevel, Massage.RELAX, 18, Preset.COMFORT);
            case REST:
                return copy(zone, heatLevel, ventilationLevel, Massage.RELAX, 45, Preset.REST);
            case CUSTOM:
            default:
                return copy(zone, heatLevel, ventilationLevel, massage,
                        reclineDegrees, Preset.CUSTOM);
        }
    }

    public boolean changesPositionComparedTo(SeatControlIntent other) {
        Objects.requireNonNull(other, "other");
        return reclineDegrees != other.reclineDegrees;
    }

    public String toWireValue() {
        return WIRE_PREFIX
                + "|zone=" + zone
                + "|heat=" + heatLevel
                + "|vent=" + ventilationLevel
                + "|massage=" + massage
                + "|recline_deg=" + reclineDegrees
                + "|preset=" + preset;
    }

    public static SeatControlIntent parseWireValue(String value) {
        if (value == null || value.length() > 256) {
            throw new IllegalArgumentException("invalid seat wire value");
        }
        String[] parts = value.split("\\|", -1);
        if (parts.length != 7 || !WIRE_PREFIX.equals(parts[0])) {
            throw new IllegalArgumentException("invalid seat wire schema");
        }
        Map<String, String> fields = new LinkedHashMap<>();
        for (int index = 1; index < parts.length; index++) {
            int separator = parts[index].indexOf('=');
            if (separator <= 0 || separator == parts[index].length() - 1) {
                throw new IllegalArgumentException("invalid seat wire field");
            }
            String key = parts[index].substring(0, separator);
            String fieldValue = parts[index].substring(separator + 1);
            if (fields.put(key, fieldValue) != null) {
                throw new IllegalArgumentException("duplicate seat wire field");
            }
        }
        requireExactKeys(fields);
        SeatControlIntent parsed = new SeatControlIntent(
                parseEnum(Zone.class, fields.get("zone")),
                parseInteger(fields.get("heat"), "heat"),
                parseInteger(fields.get("vent"), "ventilation"),
                parseEnum(Massage.class, fields.get("massage")),
                parseInteger(fields.get("recline_deg"), "recline"),
                parseEnum(Preset.class, fields.get("preset")));
        if (!parsed.toWireValue().equals(value)) {
            throw new IllegalArgumentException("non-canonical seat wire value");
        }
        return parsed;
    }

    public String desiredSummary() {
        return zone
                + " · HEAT " + heatLevel
                + " · VENT " + ventilationLevel
                + " · " + massage
                + " · " + reclineDegrees + " deg";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SeatControlIntent)) {
            return false;
        }
        SeatControlIntent that = (SeatControlIntent) other;
        return heatLevel == that.heatLevel
                && ventilationLevel == that.ventilationLevel
                && reclineDegrees == that.reclineDegrees
                && zone == that.zone
                && massage == that.massage
                && preset == that.preset;
    }

    @Override
    public int hashCode() {
        return Objects.hash(zone, heatLevel, ventilationLevel, massage, reclineDegrees, preset);
    }

    private SeatControlIntent copy(
            Zone nextZone,
            int nextHeatLevel,
            int nextVentilationLevel,
            Massage nextMassage,
            int nextReclineDegrees,
            Preset nextPreset) {
        SeatControlIntent next = new SeatControlIntent(
                nextZone,
                nextHeatLevel,
                nextVentilationLevel,
                nextMassage,
                nextReclineDegrees,
                nextPreset);
        return equals(next) ? this : next;
    }

    private static int requireComfortLevel(int value, String name) {
        if (value < MIN_COMFORT_LEVEL || value > MAX_COMFORT_LEVEL) {
            throw new IllegalArgumentException(name + " outside seat contract");
        }
        return value;
    }

    private static int requireRecline(int value) {
        if (value < MIN_RECLINE_DEGREES || value > MAX_RECLINE_DEGREES) {
            throw new IllegalArgumentException("recline outside seat contract");
        }
        return value;
    }

    private static int parseInteger(String value, String name) {
        try {
            if (value == null || value.isEmpty() || value.startsWith("+")
                    || (value.length() > 1 && value.startsWith("0"))) {
                throw new NumberFormatException(name);
            }
            return Integer.parseInt(value);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("invalid seat " + name, failure);
        }
    }

    private static <T extends Enum<T>> T parseEnum(Class<T> type, String value) {
        try {
            return Enum.valueOf(type, value);
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("invalid seat enum", failure);
        }
    }

    private static void requireExactKeys(Map<String, String> fields) {
        String[] expected = {"zone", "heat", "vent", "massage", "recline_deg", "preset"};
        if (fields.size() != expected.length) {
            throw new IllegalArgumentException("seat wire field count changed");
        }
        for (String key : expected) {
            if (!fields.containsKey(key)) {
                throw new IllegalArgumentException("missing seat wire field");
            }
        }
    }
}
