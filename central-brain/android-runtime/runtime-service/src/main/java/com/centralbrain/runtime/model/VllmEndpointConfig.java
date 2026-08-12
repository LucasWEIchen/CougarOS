package com.centralbrain.runtime.model;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Fixed vLLM endpoint used by the Android prototype-validation build.
 *
 * <p>The app connects only to Android loopback. The validation harness binds that port through
 * ADB reverse and an Ethernet SSH tunnel to the fixed TY1100 AI compute device. This class has no
 * production profile and cannot alter the release OpenClaw configuration.</p>
 *
 * <p>Req IDs: S2-MDL-001, XSC-001/005/006, DEL-001/003/004.</p>
 */
public final class VllmEndpointConfig {
    public static final int VLLM_PORT = 10_030;
    public static final String DEVELOPMENT_HOST = "127.0.0.1";
    public static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";
    public static final String MODELS_PATH = "/v1/models";
    public static final String HEALTH_PATH = "/health";
    public static final String EXPECTED_MODEL = "Qwen3.5-9B-AWQ";
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 3_000;
    public static final int DEFAULT_READ_TIMEOUT_MS = 120_000;
    public static final int MAX_RESPONSE_BYTES = 65_536;

    private static final Pattern MODEL_NAME =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}");

    public enum Profile {
        TY1100_ETHERNET_VIA_ADB_REVERSE
    }

    private final Profile profile;
    private final URI baseUri;
    private final String modelName;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    private VllmEndpointConfig(
            Profile profile,
            String modelName,
            int connectTimeoutMs,
            int readTimeoutMs) {
        this.profile = Objects.requireNonNull(profile, "profile");
        this.modelName = requireModelName(modelName);
        requireTimeout(connectTimeoutMs, 1, 30_000, "connectTimeoutMs");
        requireTimeout(readTimeoutMs, 1_000, 120_000, "readTimeoutMs");
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        try {
            baseUri = new URI(
                    "http", null, DEVELOPMENT_HOST, VLLM_PORT, null, null, null);
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("fixed vLLM endpoint is invalid", exception);
        }
        validateFixedEndpoint();
    }

    public static VllmEndpointConfig ty1100EthernetViaAdbReverse() {
        return new VllmEndpointConfig(
                Profile.TY1100_ETHERNET_VIA_ADB_REVERSE,
                EXPECTED_MODEL,
                DEFAULT_CONNECT_TIMEOUT_MS,
                DEFAULT_READ_TIMEOUT_MS);
    }

    public Profile getProfile() {
        return profile;
    }

    public URI getBaseUri() {
        return baseUri;
    }

    public URI getChatCompletionsUri() {
        return baseUri.resolve(CHAT_COMPLETIONS_PATH);
    }

    public URI getModelsUri() {
        return baseUri.resolve(MODELS_PATH);
    }

    public URI getHealthUri() {
        return baseUri.resolve(HEALTH_PATH);
    }

    public String getModelName() {
        return modelName;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public boolean isProductionProfile() {
        return false;
    }

    private void validateFixedEndpoint() {
        if (!"http".equals(baseUri.getScheme())
                || !DEVELOPMENT_HOST.equals(baseUri.getHost())
                || baseUri.getPort() != VLLM_PORT
                || baseUri.getUserInfo() != null
                || baseUri.getQuery() != null
                || baseUri.getFragment() != null
                || !EXPECTED_MODEL.equals(modelName)) {
            throw new IllegalArgumentException("vLLM endpoint violates the fixed prototype profile");
        }
    }

    private static String requireModelName(String value) {
        if (value == null
                || !MODEL_NAME.matcher(value).matches()
                || value.contains("://")
                || value.contains("..")
                || !EXPECTED_MODEL.equals(value)) {
            throw new IllegalArgumentException("vLLM model identity is not configured");
        }
        return value;
    }

    private static void requireTimeout(int value, int minimum, int maximum, String field) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(field + " is out of range");
        }
    }
}
