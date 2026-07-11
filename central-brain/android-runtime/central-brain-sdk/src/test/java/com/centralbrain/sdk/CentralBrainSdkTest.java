package com.centralbrain.sdk;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class CentralBrainSdkTest {
    @Test
    public void exposesR1ContractMaturity() {
        assertEquals("R2_TYPED_BINDER", CentralBrainSdk.EVOLUTION_STAGE);
        assertEquals("android_integrated", CentralBrainSdk.MATURITY);
    }
}
