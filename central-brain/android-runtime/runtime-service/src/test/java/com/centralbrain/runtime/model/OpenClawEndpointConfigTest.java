package com.centralbrain.runtime.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public final class OpenClawEndpointConfigTest {
    @Test
    public void targetProfileUsesOnlyTheFixedGatewayWithoutEmbeddedCredentials() {
        OpenClawEndpointConfig config = OpenClawEndpointConfig
                .targetProductionTransitional();

        assertEquals("ws://169.254.208.110:18789/",
                config.getWebSocketUri().toString());
        assertEquals("http://169.254.208.110:18789/chat",
                config.getControlUiUri().toString());
        assertEquals(3, config.getProtocolVersion());
        assertEquals(3_000, config.getConnectTimeoutMs());
        assertEquals(120_000, config.getReadTimeoutMs());
        assertNull(config.getWebSocketUri().getUserInfo());
        assertNull(config.getWebSocketUri().getQuery());
        assertNull(config.getControlUiUri().getQuery());
    }
}
