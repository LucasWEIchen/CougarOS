package com.centralbrain.runtime.model;

import java.net.URI;
import java.net.URISyntaxException;

/** Build-owned OpenClaw endpoints for development and target Android integration. */
public final class OpenClawEndpointConfig {
    public static final String DEVELOPMENT_PROFILE = "development_wsl_openclaw";
    public static final String DEVELOPMENT_HOST = "127.0.0.1";
    public static final String TARGET_PROFILE = "target_openclaw_transitional";
    public static final String TARGET_HOST = "169.254.208.110";
    public static final int TARGET_PORT = 18_789;
    public static final String WEBSOCKET_PATH = "/";
    public static final String CONTROL_UI_PATH = "/chat";
    public static final String TARGET_TOKEN = "Iluvatar1!";
    public static final String CONTROL_UI_QUERY = "token=" + TARGET_TOKEN;
    public static final int DEVELOPMENT_PROTOCOL_VERSION = 4;
    public static final int TARGET_PROTOCOL_VERSION = 3;
    public static final int CONNECT_TIMEOUT_MS = 3_000;
    public static final int READ_TIMEOUT_MS = 120_000;
    public static final int MAX_HANDSHAKE_BYTES = 16_384;
    public static final int MAX_PREAUTH_FRAME_BYTES = 65_536;
    public static final int MAX_FRAME_BYTES = 1_048_576;
    public static final int MAX_REQUEST_BYTES = 16_384;
    public static final int MAX_RESPONSE_BYTES = 65_536;

    private final URI webSocketUri;
    private final URI controlUiUri;
    private final String profile;
    private final int protocolVersion;

    private OpenClawEndpointConfig(String profile, String host, int protocolVersion) {
        this.profile = profile;
        this.protocolVersion = protocolVersion;
        try {
            webSocketUri = new URI(
                    "ws", null, host, TARGET_PORT, WEBSOCKET_PATH, null, null);
            controlUiUri = new URI(
                    "http", null, host, TARGET_PORT, CONTROL_UI_PATH,
                    CONTROL_UI_QUERY, null);
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("fixed OpenClaw endpoint is invalid", exception);
        }
        validate();
    }

    public static OpenClawEndpointConfig developmentWslAdbReverse() {
        return new OpenClawEndpointConfig(
                DEVELOPMENT_PROFILE,
                DEVELOPMENT_HOST,
                DEVELOPMENT_PROTOCOL_VERSION);
    }

    public static OpenClawEndpointConfig targetProductionTransitional() {
        return new OpenClawEndpointConfig(
                TARGET_PROFILE,
                TARGET_HOST,
                TARGET_PROTOCOL_VERSION);
    }

    public String getProfile() {
        return profile;
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
        return protocolVersion;
    }

    public int getConnectTimeoutMs() {
        return CONNECT_TIMEOUT_MS;
    }

    public int getReadTimeoutMs() {
        return READ_TIMEOUT_MS;
    }

    private void validate() {
        String expectedHost;
        if (DEVELOPMENT_PROFILE.equals(profile)
                && protocolVersion == DEVELOPMENT_PROTOCOL_VERSION) {
            expectedHost = DEVELOPMENT_HOST;
        } else if (TARGET_PROFILE.equals(profile)
                && protocolVersion == TARGET_PROTOCOL_VERSION) {
            expectedHost = TARGET_HOST;
        } else {
            throw new IllegalStateException("OpenClaw endpoint profile is not build-owned");
        }
        if (!"ws".equals(webSocketUri.getScheme())
                || !expectedHost.equals(webSocketUri.getHost())
                || webSocketUri.getPort() != TARGET_PORT
                || !WEBSOCKET_PATH.equals(webSocketUri.getPath())
                || webSocketUri.getUserInfo() != null
                || webSocketUri.getQuery() != null
                || webSocketUri.getFragment() != null
                || !"http".equals(controlUiUri.getScheme())
                || !expectedHost.equals(controlUiUri.getHost())
                || controlUiUri.getPort() != TARGET_PORT
                || !CONTROL_UI_PATH.equals(controlUiUri.getPath())
                || !CONTROL_UI_QUERY.equals(controlUiUri.getQuery())
                || controlUiUri.getFragment() != null) {
            throw new IllegalStateException("OpenClaw endpoint violates its fixed profile");
        }
    }
}
