package com.centralbrain.runtime.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public final class SmokingDetectionResultTest {
    @Test
    public void acceptsAndCanonicalizesPositiveDetection() {
        SmokingDetectionResult result = SmokingDetectionResult.parse(
                "{\"smoking_detected\":1,\"person_count\":1,"
                        + "\"location\":\"IMAGE_ROW_1_RIGHT\",\"confidence\":0.88,"
                        + "\"description\":\"可见烟支靠近嘴部。\"}");

        assertEquals(SmokingDetectionResult.DecisionStatus.DETECTED,
                result.getStatus());
        assertEquals(1, result.getPersonCount());
        assertEquals("IMAGE_ROW_1_RIGHT", result.getLocation());
        assertEquals(
                "{\"smoking_detected\":1,\"person_count\":1,"
                        + "\"location\":\"IMAGE_ROW_1_RIGHT\",\"confidence\":0.88,"
                        + "\"description\":\"可见烟支靠近嘴部。\"}",
                result.toCompactJson());
    }

    @Test
    public void acceptsOnlyNormalizedUncertainResult() {
        SmokingDetectionResult result = SmokingDetectionResult.parse(
                "{\"smoking_detected\":0,\"person_count\":0,"
                        + "\"location\":\"UNKNOWN\",\"confidence\":0.42,"
                        + "\"description\":\"uncertain：画面证据不足。\"}");
        assertEquals(SmokingDetectionResult.DecisionStatus.UNCERTAIN,
                result.getStatus());

        assertThrows(IllegalArgumentException.class, () ->
                SmokingDetectionResult.parse(
                        "{\"smoking_detected\":1,\"person_count\":1,"
                                + "\"location\":\"UNKNOWN\",\"confidence\":0.42,"
                                + "\"description\":\"uncertain\"}"));
    }

    @Test
    public void rejectsUnknownDuplicateAndInconsistentFields() {
        assertThrows(IllegalArgumentException.class, () ->
                SmokingDetectionResult.parse(
                        "{\"smoking_detected\":0,\"smoking_detected\":1,"
                                + "\"person_count\":1,\"location\":\"IMAGE_ROW_2_LEFT\","
                                + "\"confidence\":0.9,\"description\":\"检测到。\"}"));
        assertThrows(IllegalArgumentException.class, () ->
                SmokingDetectionResult.parse(
                        "{\"smoking_detected\":0,\"person_count\":1,"
                                + "\"location\":\"IMAGE_ROW_2_LEFT\","
                                + "\"confidence\":0.9,\"description\":\"未检测到。\"}"));
        assertThrows(IllegalArgumentException.class, () ->
                SmokingDetectionResult.parse(
                        "{\"smoking_detected\":1,\"person_count\":1,"
                                + "\"location\":\"ROW2_LEFT\",\"confidence\":0.9,"
                                + "\"description\":\"检测到。\"}"));
        assertThrows(IllegalArgumentException.class, () ->
                SmokingDetectionResult.parse(
                        "{\"smoking_detected\":1,\"person_count\":1,"
                                + "\"location\":\"IMAGE_ROW_1_RIGHT\","
                                + "\"confidence\":0.95,"
                                + "\"description\":\"副驾驶乘客正在吸烟。\"}"));
    }

    @Test
    public void compactWireExpandsToCanonicalFiveFieldResult() {
        SmokingDetectionResult detected =
                SmokingDetectionResult.parseCompactWire("[1,1,2,95]");
        assertEquals(SmokingDetectionResult.DecisionStatus.DETECTED,
                detected.getStatus());
        assertEquals("IMAGE_ROW_2_RIGHT", detected.getLocation());
        assertEquals(0.95, detected.getConfidence(), 0.0001);
        assertEquals("检测到吸烟行为。", detected.getDescription());

        SmokingDetectionResult uncertain =
                SmokingDetectionResult.parseCompactWire("[2,0,0,45]");
        assertEquals(SmokingDetectionResult.DecisionStatus.UNCERTAIN,
                uncertain.getStatus());
        assertEquals("UNKNOWN", uncertain.getLocation());
    }

    @Test
    public void compactWireRejectsMalformedOrInconsistentResults() {
        assertThrows(IllegalArgumentException.class, () ->
                SmokingDetectionResult.parseCompactWire("[1,1,1]"));
        assertThrows(IllegalArgumentException.class, () ->
                SmokingDetectionResult.parseCompactWire("[1,1,1,95,0]"));
        assertThrows(IllegalArgumentException.class, () ->
                SmokingDetectionResult.parseCompactWire("[1,0,0,95]"));
        assertThrows(IllegalArgumentException.class, () ->
                SmokingDetectionResult.parseCompactWire("[2,1,1,45]"));
        assertThrows(IllegalArgumentException.class, () ->
                SmokingDetectionResult.parseCompactWire("[0,0,0,45]"));
        assertThrows(IllegalArgumentException.class, () ->
                SmokingDetectionResult.parseCompactWire("[1,1,1,95.0]"));
    }
}
