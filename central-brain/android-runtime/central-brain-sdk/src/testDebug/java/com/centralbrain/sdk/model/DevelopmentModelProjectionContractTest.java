package com.centralbrain.sdk.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public final class DevelopmentModelProjectionContractTest {
    @Test
    public void validProjectionIsDigestBound() {
        DevelopmentModelProjection projection = valid();
        projection.projectionDigest =
                DevelopmentModelProjectionContract.calculateDigest(projection);

        DevelopmentModelProjectionContract.validate(projection);
        assertEquals(64, projection.projectionDigest.length());
    }

    @Test
    public void controlCharactersAndDigestDriftAreRejected() {
        DevelopmentModelProjection withControl = valid();
        withControl.assistantDisplayText = "unsafe\nreply";
        withControl.projectionDigest =
                DevelopmentModelProjectionContract.calculateDigest(withControl);
        assertThrows(
                IllegalArgumentException.class,
                () -> DevelopmentModelProjectionContract.validate(withControl));

        DevelopmentModelProjection drifted = valid();
        drifted.projectionDigest = "f".repeat(64);
        assertThrows(
                IllegalArgumentException.class,
                () -> DevelopmentModelProjectionContract.validate(drifted));
    }

    private static DevelopmentModelProjection valid() {
        DevelopmentModelProjection projection = new DevelopmentModelProjection();
        projection.schemaVersion = DevelopmentModelProjectionContract.SCHEMA_VERSION;
        projection.sessionId = "8d595630-2255-4f4d-ac0f-26a20ee96f29";
        projection.scenarioId = "scene.fatigue.assist.v1";
        projection.providerId = "android.local.development";
        projection.assistantDisplayText = "A bounded fatigue-care plan is ready.";
        projection.latencyMs = 1_234L;
        projection.inputAggregateDigest = "b".repeat(64);
        projection.imageConsumed = true;
        projection.admittedActions = new String[] {"hvac.ventilate"};
        projection.outputDigest = "a".repeat(64);
        projection.completedAtEpochMs = 1_750_000_000_000L;
        return projection;
    }
}
