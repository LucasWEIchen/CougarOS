package com.centralbrain.runtime.model;

import java.net.URI;
import java.net.URISyntaxException;

/** Fixed transitional OpenClaw endpoint for the target Android 13 integration. */
public final class OpenClawEndpointConfig {
    public static final String TARGET_HOST = "169.254.208.110";
    public static final int TARGET_PORT = 18_789;
    public static final String WEBSOCKET_PATH = "/";
    public static final String CONTROL_UI_PATH = "/chat";
    public static final String TARGET_TOKEN = "Iluvatar1!";
    public static final String CONTROL_UI_QUERY = "token=" + TARGET_TOKEN;
    public static final int PROTOCOL_VERSION = 3;
    public static final int CONNECT_TIMEOUT_MS = 3_000;
    public static final int READ_TIMEOUT_MS = 120_000;
    public static final int MAX_HANDSHAKE_BYTES = 16_384;
    public static final int MAX_PREAUTH_FRAME_BYTES = 65_536;
    public static final int MAX_FRAME_BYTES = 1_048_576;
    public static final int MAX_REQUEST_BYTES = 16_384;
    public static final int MAX_RESPONSE_BYTES = 65_536;

    private final URI webSocketUri;
    private final URI controlUiUri;

    private OpenClawEndpointConfig() {
        try {
            webSocketUri = new URI(
                    "ws", null, TARGET_HOST, TARGET_PORT, WEBSOCKET_PATH, null, null);
            controlUiUri = new URI(
                    "http", null, TARGET_HOST, TARGET_PORT, CONTROL_UI_PATH,
                    CONTROL_UI_QUERY, null);
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("fixed OpenClaw endpoint is invalid", exception);
        }
        validate();
    }

    public static OpenClawEndpointConfig targetProductionTransitional() {
        return new OpenClawEndpointConfig();
    }

    public URI getWebSocketUri() {
        return webSocketUri;
    }

    public URI getControlUiUri() {
        return controlUiUri;
    }

    /**
     * Transitional fixed credential requested for the closed target integration build.
     * This value is extractable from both source and APK and is not a production secret store.
     */
    public String getEmbeddedToken() {
        return TARGET_TOKEN;
    }

    public int getProtocolVersion() {
        return PROTOCOL_VERSION;
    }

    public int getConnectTimeoutMs() {
        return CONNECT_TIMEOUT_MS;
    }

    public int getReadTimeoutMs() {
        return READ_TIMEOUT_MS;
    }

    private void validate() {
        if (!"ws".equals(webSocketUri.getScheme())
                || !TARGET_HOST.equals(webSocketUri.getHost())
                || webSocketUri.getPort() != TARGET_PORT
                || !WEBSOCKET_PATH.equals(webSocketUri.getPath())
                || webSocketUri.getUserInfo() != null
                || webSocketUri.getQuery() != null
                || webSocketUri.getFragment() != null
                || !"http".equals(controlUiUri.getScheme())
                || !TARGET_HOST.equals(controlUiUri.getHost())
                || controlUiUri.getPort() != TARGET_PORT
                || !CONTROL_UI_PATH.equals(controlUiUri.getPath())
                || !CONTROL_UI_QUERY.equals(controlUiUri.getQuery())
                || controlUiUri.getFragment() != null) {
            throw new IllegalStateException("OpenClaw endpoint violates the fixed target profile");
        }
    }
}
