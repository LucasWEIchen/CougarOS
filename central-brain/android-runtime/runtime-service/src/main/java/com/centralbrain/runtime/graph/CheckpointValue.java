package com.centralbrain.runtime.graph;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Immutable primitive tree used by registered checkpoint DTO codecs. */
public final class CheckpointValue {
    public static final long MAX_ABSOLUTE_INTEGER = 1_000_000_000_000L;
    public static final BigDecimal MAX_ABSOLUTE_DECIMAL =
            BigDecimal.valueOf(MAX_ABSOLUTE_INTEGER);
    public static final int MAX_DECIMAL_SCALE = 6;
    public static final int MAX_STRING_CHARS = 1_024;
    public static final int MAX_CONTAINER_ITEMS = 64;
    public static final int MAX_KEY_CHARS = 64;

    private static final Pattern KEY = Pattern.compile("[A-Za-z][A-Za-z0-9._-]{0,63}");

    public enum Kind {
        STRING,
        BOOLEAN,
        INTEGER,
        DECIMAL,
        LIST,
        MAP
    }

    private final Kind kind;
    private final Object value;

    private CheckpointValue(Kind kind, Object value) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.value = Objects.requireNonNull(value, "value");
    }

    public static CheckpointValue string(String value) {
        return new CheckpointValue(Kind.STRING, boundedString(value, "string"));
    }

    /** Enums are encoded as their stable name and decoded by an explicit DTO codec. */
    public static CheckpointValue enumName(Enum<?> value) {
        Objects.requireNonNull(value, "value");
        return string(value.name());
    }

    public static CheckpointValue bool(boolean value) {
        return new CheckpointValue(Kind.BOOLEAN, value);
    }

    public static CheckpointValue integer(long value) {
        if (value < -MAX_ABSOLUTE_INTEGER || value > MAX_ABSOLUTE_INTEGER) {
            throw violation("integer is outside the checkpoint bound");
        }
        return new CheckpointValue(Kind.INTEGER, value);
    }

    public static CheckpointValue decimal(BigDecimal value) {
        Objects.requireNonNull(value, "value");
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.abs().compareTo(MAX_ABSOLUTE_DECIMAL) > 0
                || Math.max(0, normalized.scale()) > MAX_DECIMAL_SCALE) {
            throw violation("decimal is outside the checkpoint bound");
        }
        return new CheckpointValue(Kind.DECIMAL, normalized);
    }

    public static CheckpointValue list(List<CheckpointValue> values) {
        Objects.requireNonNull(values, "values");
        if (values.size() > MAX_CONTAINER_ITEMS) {
            throw violation("list exceeds the checkpoint item bound");
        }
        List<CheckpointValue> copy = new ArrayList<>(values.size());
        for (CheckpointValue value : values) {
            copy.add(Objects.requireNonNull(value, "list value"));
        }
        return new CheckpointValue(Kind.LIST, Collections.unmodifiableList(copy));
    }

    public static CheckpointValue map(Map<String, CheckpointValue> values) {
        Objects.requireNonNull(values, "values");
        if (values.size() > MAX_CONTAINER_ITEMS) {
            throw violation("map exceeds the checkpoint field bound");
        }
        Map<String, CheckpointValue> copy = new LinkedHashMap<>();
        values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String key = requireKey(entry.getKey());
                    if (copy.put(key, Objects.requireNonNull(entry.getValue(), "map value"))
                            != null) {
                        throw violation("duplicate checkpoint map key");
                    }
                });
        return new CheckpointValue(Kind.MAP, Collections.unmodifiableMap(copy));
    }

    public Kind getKind() {
        return kind;
    }

    public String asString() {
        requireKind(Kind.STRING);
        return (String) value;
    }

    public boolean asBoolean() {
        requireKind(Kind.BOOLEAN);
        return (Boolean) value;
    }

    public long asLong() {
        requireKind(Kind.INTEGER);
        return (Long) value;
    }

    public BigDecimal asDecimal() {
        if (kind == Kind.INTEGER) {
            return BigDecimal.valueOf((Long) value);
        }
        requireKind(Kind.DECIMAL);
        return (BigDecimal) value;
    }

    @SuppressWarnings("unchecked")
    public List<CheckpointValue> asList() {
        requireKind(Kind.LIST);
        return (List<CheckpointValue>) value;
    }

    @SuppressWarnings("unchecked")
    public Map<String, CheckpointValue> asMap() {
        requireKind(Kind.MAP);
        return (Map<String, CheckpointValue>) value;
    }

    public CheckpointValue requireField(String field) {
        CheckpointValue result = asMap().get(field);
        if (result == null) {
            throw violation("required checkpoint field is missing: " + field);
        }
        return result;
    }

    public void requireOnlyFields(String... fields) {
        Map<String, CheckpointValue> remaining = new LinkedHashMap<>(asMap());
        for (String field : fields) {
            if (remaining.remove(field) == null) {
                throw violation("required checkpoint field is missing: " + field);
            }
        }
        if (!remaining.isEmpty()) {
            throw violation("unknown checkpoint field: " + remaining.keySet().iterator().next());
        }
    }

    static String requireKey(String key) {
        if (key == null || key.length() > MAX_KEY_CHARS || !KEY.matcher(key).matches()) {
            throw violation("checkpoint map key is invalid");
        }
        String lower = key.toLowerCase(Locale.ROOT);
        if (key.equals("class")
                || key.equals("javaClass")
                || key.equals("serialVersionUID")
                || key.startsWith("@")
                || key.startsWith("$")) {
            throw violation("checkpoint type metadata keys are forbidden");
        }
        if (lower.equals("path")
                || lower.endsWith("filepath")
                || lower.endsWith("fileuri")
                || lower.equals("fd")
                || lower.endsWith("filedescriptor")
                || lower.endsWith("binderobject")
                || lower.endsWith("parcelblob")
                || lower.endsWith("nativepointer")) {
            throw violation("checkpoint privileged material keys are forbidden");
        }
        return key;
    }

    private void requireKind(Kind expected) {
        if (kind != expected) {
            throw violation("checkpoint value is not " + expected.name().toLowerCase());
        }
    }

    private static String boundedString(String value, String label) {
        if (value == null || value.length() > MAX_STRING_CHARS) {
            throw violation(label + " exceeds the checkpoint string bound");
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isHighSurrogate(character)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(++index))) {
                    throw violation(label + " contains an unpaired surrogate");
                }
            } else if (Character.isLowSurrogate(character)) {
                throw violation(label + " contains an unpaired surrogate");
            }
        }
        return value;
    }

    static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_CHECKPOINT_VALUE: " + message);
    }
}
