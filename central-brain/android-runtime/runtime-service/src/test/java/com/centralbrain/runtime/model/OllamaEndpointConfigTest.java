package com.centralbrain.runtime.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class OllamaEndpointConfigTest {
    @Test
    public void developmentProfileIsRestrictedToAdbReverseLoopback() {
        OllamaEndpointConfig config = OllamaEndpointConfig
                .developmentWslAdbReverse("qwen3.5:27b-optimized");

        assertEquals(OllamaEndpointConfig.Profile.DEVELOPMENT_WSL_ADB_REVERSE,
                config.getProfile());
        assertEquals("http://127.0.0.1:11434", config.getBaseUri().toString());
        assertEquals("http://127.0.0.1:11434/api/chat", config.getChatUri().toString());
        assertFalse(config.isProductionProfile());
        assertFalse(config.targetsExternalComputeBase());
    }

    @Test
    public void productionProfileIsRestrictedToDedicatedLinkLocalBase() {
        OllamaEndpointConfig config = OllamaEndpointConfig
                .productionLinkLocal("central-brain-model:v1");

        assertEquals(OllamaEndpointConfig.Profile.PRODUCTION_LINK_LOCAL,
                config.getProfile());
        assertEquals("http://169.254.208.110:11434", config.getBaseUri().toString());
        assertEquals("http://169.254.208.110:11434/api/chat",
                config.getChatUri().toString());
        assertTrue(config.isProductionProfile());
        assertTrue(config.targetsExternalComputeBase());
    }

    @Test
    public void missingOrUnsafeModelNamesFailClosed() {
        assertThrows(IllegalArgumentException.class, () ->
                OllamaEndpointConfig.productionLinkLocal("UNCONFIGURED"));
        assertThrows(IllegalArgumentException.class, () ->
                OllamaEndpointConfig.developmentWslAdbReverse("http://other-host/model"));
        assertThrows(IllegalArgumentException.class, () ->
                OllamaEndpointConfig.developmentWslAdbReverse(""));
    }
}
