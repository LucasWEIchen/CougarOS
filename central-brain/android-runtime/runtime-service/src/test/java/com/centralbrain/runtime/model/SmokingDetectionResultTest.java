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
}
