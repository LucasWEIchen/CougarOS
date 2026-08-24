package com.centralbrain.runtime.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public final class VllmEndpointConfigTest {
    @Test
    public void generalProfileIsFixedTo9bLoopbackAnd8192Context() {
        VllmEndpointConfig config = VllmEndpointConfig.ty1100General9bViaAdbReverse();

        assertEquals(VllmEndpointConfig.Profile.TY1100_GENERAL_9B_VIA_ADB_REVERSE,
                config.getProfile());
        assertEquals("http://127.0.0.1:10030", config.getBaseUri().toString());
        assertEquals("http://127.0.0.1:10030/v1/chat/completions",
                config.getChatCompletionsUri().toString());
        assertEquals("http://127.0.0.1:10030/v1/models",
                config.getModelsUri().toString());
        assertEquals("http://127.0.0.1:10030/health",
                config.getHealthUri().toString());
        assertEquals("Qwen3.5-9B-AWQ", config.getModelName());
        assertEquals(8_192, config.getMaximumContextTokens());
        assertFalse(config.isProductionProfile());
        assertFalse(config.isTargetEthernetProfile());
    }

    @Test
    public void smokingProfileIsFixedTo2bLoopbackAnd4096Context() {
        VllmEndpointConfig config = VllmEndpointConfig.ty1100Smoking2bViaAdbReverse();

        assertEquals(VllmEndpointConfig.Profile.TY1100_SMOKING_2B_VIA_ADB_REVERSE,
                config.getProfile());
        assertEquals("http://127.0.0.1:10031", config.getBaseUri().toString());
        assertEquals("http://127.0.0.1:10031/v1/chat/completions",
                config.getChatCompletionsUri().toString());
        assertEquals("http://127.0.0.1:10031/v1/models",
                config.getModelsUri().toString());
        assertEquals("http://127.0.0.1:10031/health",
                config.getHealthUri().toString());
        assertEquals("Qwen3.5-2B-AWQ", config.getModelName());
        assertEquals(4_096, config.getMaximumContextTokens());
        assertFalse(config.isProductionProfile());
        assertFalse(config.isTargetEthernetProfile());
    }

    @Test
    public void targetGeneralProfileIsFixedToNewEthernetAddress() {
        VllmEndpointConfig config =
                VllmEndpointConfig.ty1100General2bViaTargetEthernet();

        assertEquals(VllmEndpointConfig.Profile.TY1100_GENERAL_2B_VIA_TARGET_ETHERNET,
                config.getProfile());
        assertEquals("http://169.254.202.110:8000", config.getBaseUri().toString());
        assertEquals("http://169.254.202.110:8000/v1/chat/completions",
                config.getChatCompletionsUri().toString());
        assertEquals("Qwen3.5-2B-AWQ", config.getModelName());
        assertEquals(8_192, config.getMaximumContextTokens());
        assertFalse(config.isProductionProfile());
        org.junit.Assert.assertTrue(config.isTargetEthernetProfile());
    }

    @Test
    public void targetSmokingProfileIsFixedToNewEthernetAddress() {
        VllmEndpointConfig config =
                VllmEndpointConfig.ty1100Smoking2bViaTargetEthernet();

        assertEquals(VllmEndpointConfig.Profile.TY1100_SMOKING_2B_VIA_TARGET_ETHERNET,
                config.getProfile());
        assertEquals("http://169.254.202.110:8000", config.getBaseUri().toString());
        assertEquals("http://169.254.202.110:8000/v1/chat/completions",
                config.getChatCompletionsUri().toString());
        assertEquals("Qwen3.5-2B-AWQ", config.getModelName());
        assertEquals(4_096, config.getMaximumContextTokens());
        assertFalse(config.isProductionProfile());
        org.junit.Assert.assertTrue(config.isTargetEthernetProfile());
    }
}
