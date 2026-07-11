package com.centralbrain.sdk;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class CentralBrainSdkTest {
    @Test
    public void exposesCurrentAndroidMaturity() {
        assertEquals("R4_DURABLE_WORKFLOW", CentralBrainSdk.EVOLUTION_STAGE);
        assertEquals("android_integrated", CentralBrainSdk.MATURITY);
    }
}
