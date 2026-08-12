package com.centralbrain.runtime.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public final class VllmEndpointConfigTest {
    @Test
    public void prototypeProfileIsFixedToAdbReverseLoopbackAndExpectedModel() {
        VllmEndpointConfig config = VllmEndpointConfig
                .ty1100EthernetViaAdbReverse();

        assertEquals(VllmEndpointConfig.Profile.TY1100_ETHERNET_VIA_ADB_REVERSE,
                config.getProfile());
        assertEquals("http://127.0.0.1:10030", config.getBaseUri().toString());
        assertEquals("http://127.0.0.1:10030/v1/chat/completions",
                config.getChatCompletionsUri().toString());
        assertEquals("http://127.0.0.1:10030/v1/models",
                config.getModelsUri().toString());
        assertEquals("http://127.0.0.1:10030/health",
                config.getHealthUri().toString());
        assertEquals("Qwen3.5-9B-AWQ", config.getModelName());
        assertFalse(config.isProductionProfile());
    }
}
