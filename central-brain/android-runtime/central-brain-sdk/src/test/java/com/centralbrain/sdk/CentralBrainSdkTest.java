package com.centralbrain.sdk;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class CentralBrainSdkTest {
    @Test
    public void exposesR1ContractMaturity() {
        assertEquals("R1_GRADLE_FOUNDATION", CentralBrainSdk.EVOLUTION_STAGE);
        assertEquals("contract_defined", CentralBrainSdk.MATURITY);
    }
}
