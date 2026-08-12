package com.centralbrain.runtime.model;

import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/** Strict payload contract for the specialized smoking-detection agent. */
public final class SmokingDetectionResult {
    public static final int MAX_OUTPUT_BYTES = 2_048;
    public static final int MAX_DESCRIPTION_CHARS = 120;

    public enum DecisionStatus {
        DETECTED,
        NOT_DETECTED,
        UNCERTAIN
    }

    private static final Set<String> FIELDS = Set.of(
            "smoking_detected",
            "person_count",
            "location",
            "confidence",
            "description");
    private static final Set<String> LOCATIONS = Set.of(
            "IMAGE_ROW_2_LEFT",
            "IMAGE_ROW_2_RIGHT",
            "IMAGE_ROW_1_LEFT",
            "IMAGE_ROW_1_RIGHT",
            "UNKNOWN");
    private static final Set<String> FORBIDDEN_NATURAL_LOCATION_TERMS = Set.of(
            "主驾",
            "副驾驶",
            "驾驶座",
            "前排",
            "后排",
            "左边",
            "右边",
            "左侧",
            "右侧");

    private final int smokingDetected;
    private final int personCount;
    private final String location;
    private final double confidence;
    private final String description;
    private final DecisionStatus status;

    private SmokingDetectionResult(
            int smokingDetected,
            int personCount,
            String location,
            double confidence,
            String description) {
        this.smokingDetected = smokingDetected;
        this.personCount = personCount;
        this.location = location;
        this.confidence = confidence;
        this.description = description;
        this.status = confidence < 0.5
                ? DecisionStatus.UNCERTAIN
                : smokingDetected == 1
                        ? DecisionStatus.DETECTED : DecisionStatus.NOT_DETECTED;
    }

    public static SmokingDetectionResult parse(String raw) {
        if (raw == null) {
            throw violation("payload is missing", null);
        }
        return parse(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static SmokingDetectionResult parse(byte[] raw) {
        if (raw == null || raw.length == 0 || raw.length > MAX_OUTPUT_BYTES) {
            throw violation("payload size is invalid", null);
        }
        try (JsonReader reader = new JsonReader(new InputStreamReader(
                new ByteArrayInputStream(raw),
                StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)))) {
            reader.setStrictness(Strictness.STRICT);
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                throw violation("top level must be an object", null);
            }
            Integer smokingDetected = null;
            Integer personCount = null;
            String location = null;
            Double confidence = null;
            String description = null;
            Set<String> seen = new HashSet<>();
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if (!FIELDS.contains(name)) {
                    throw violation("unknown field: " + name, null);
                }
                if (!seen.add(name)) {
                    throw violation("duplicate field: " + name, null);
                }
                switch (name) {
                    case "smoking_detected":
                        smokingDetected = readInteger(reader, name);
                        break;
                    case "person_count":
                        personCount = readInteger(reader, name);
                        break;
                    case "location":
                        location = readString(reader, name, 32);
                        break;
                    case "confidence":
                        confidence = readDecimal(reader, name);
                        break;
                    case "description":
                        description = readString(
                                reader, name, MAX_DESCRIPTION_CHARS);
                        break;
                    default:
                        throw violation("unreachable field", null);
                }
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT || seen.size() != FIELDS.size()) {
                throw violation("payload shape is not exact", null);
            }
            return validate(
                    smokingDetected,
                    personCount,
                    location,
                    confidence,
                    description);
        } catch (IOException | RuntimeException failure) {
            if (failure instanceof IllegalArgumentException
                    && failure.getMessage() != null
                    && failure.getMessage().startsWith("CB_SMOKING_RESULT:")) {
                throw (IllegalArgumentException) failure;
            }
            throw violation("payload is not strict JSON", failure);
        }
    }

    private static SmokingDetectionResult validate(
            Integer smokingDetected,
            Integer personCount,
            String location,
            Double confidence,
            String description) {
        if (smokingDetected == null
                || personCount == null
                || location == null
                || confidence == null
                || description == null) {
            throw violation("required field is missing", null);
        }
        if (smokingDetected != 0 && smokingDetected != 1) {
            throw violation("smoking_detected must be 0 or 1", null);
        }
        if (personCount < 0 || personCount > 2) {
            throw violation("person_count is outside 0..2", null);
        }
        if (!LOCATIONS.contains(location)) {
            throw violation("location is not allowlisted", null);
        }
        if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw violation("confidence is outside 0..1", null);
        }
        for (String forbidden : FORBIDDEN_NATURAL_LOCATION_TERMS) {
            if (description.contains(forbidden)) {
                throw violation(
                        "description contains a natural-language seat location", null);
            }
        }
        if (confidence < 0.5) {
            if (smokingDetected != 0
                    || personCount != 0
                    || !"UNKNOWN".equals(location)
                    || !description.toLowerCase(java.util.Locale.ROOT)
                            .contains("uncertain")) {
                throw violation("uncertain result is not normalized", null);
            }
        } else if (smokingDetected == 1) {
            if (personCount < 1 || "UNKNOWN".equals(location)) {
                throw violation("positive result lacks count or location", null);
            }
        } else if (personCount != 0 || !"UNKNOWN".equals(location)) {
            throw violation("negative result must not identify a smoking seat", null);
        }
        return new SmokingDetectionResult(
                smokingDetected, personCount, location, confidence, description);
    }

    public String toCompactJson() {
        JsonObject output = new JsonObject();
        output.addProperty("smoking_detected", smokingDetected);
        output.addProperty("person_count", personCount);
        output.addProperty("location", location);
        output.addProperty("confidence", confidence);
        output.addProperty("description", description);
        return output.toString();
    }

    public int getSmokingDetected() { return smokingDetected; }
    public int getPersonCount() { return personCount; }
    public String getLocation() { return location; }
    public double getConfidence() { return confidence; }
    public String getDescription() { return description; }
    public DecisionStatus getStatus() { return status; }

    private static int readInteger(JsonReader reader, String field) throws IOException {
        if (reader.peek() != JsonToken.NUMBER) {
            throw violation(field + " must be an integer", null);
        }
        String token = reader.nextString();
        if (!token.matches("-?(0|[1-9][0-9]*)")) {
            throw violation(field + " must be an integer", null);
        }
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException failure) {
            throw violation(field + " integer is out of range", failure);
        }
    }

    private static double readDecimal(JsonReader reader, String field) throws IOException {
        if (reader.peek() != JsonToken.NUMBER) {
            throw violation(field + " must be numeric", null);
        }
        try {
            return new BigDecimal(reader.nextString()).doubleValue();
        } catch (NumberFormatException failure) {
            throw violation(field + " is not a finite decimal", failure);
        }
    }

    private static String readString(
            JsonReader reader,
            String field,
            int maximumChars) throws IOException {
        if (reader.peek() != JsonToken.STRING) {
            throw violation(field + " must be a string", null);
        }
        String value = reader.nextString().trim();
        if (value.isEmpty() || value.length() > maximumChars) {
            throw violation(field + " is outside bounds", null);
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                throw violation(field + " contains control data", null);
            }
        }
        return value;
    }

    private static IllegalArgumentException violation(
            String message,
            Throwable cause) {
        return new IllegalArgumentException("CB_SMOKING_RESULT: " + message, cause);
    }
}
