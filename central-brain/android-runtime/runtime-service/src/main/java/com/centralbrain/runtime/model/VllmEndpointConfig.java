package com.centralbrain.runtime.model;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Fixed vLLM endpoints used by Android validation builds.
 *
 * <p>The development profile connects to Android loopback through ADB reverse. The target
 * integration profile connects directly from the Android Ethernet interface to the fixed TY1100
 * address. Neither profile grants production assurance or may alter the TY1100 service.</p>
 *
 * <p>Req IDs: S2-MDL-001, XSC-001/005/006, DEL-001/003/004.</p>
 */
public final class VllmEndpointConfig {
    public static final int GENERAL_VLLM_PORT = 10_030;
    public static final int SMOKING_VLLM_PORT = 10_031;
    public static final int TARGET_ETHERNET_VLLM_PORT = 8_000;
    public static final int VLLM_PORT = GENERAL_VLLM_PORT;
    public static final String DEVELOPMENT_HOST = "127.0.0.1";
    public static final String TARGET_ETHERNET_HOST = "169.254.202.110";
    public static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";
    public static final String MODELS_PATH = "/v1/models";
    public static final String HEALTH_PATH = "/health";
    public static final String GENERAL_MODEL = "Qwen3.5-9B-AWQ";
    public static final String SMOKING_MODEL = "Qwen3.5-2B-AWQ";
    public static final String TARGET_ETHERNET_MODEL = SMOKING_MODEL;
    public static final String EXPECTED_MODEL = GENERAL_MODEL;
    public static final int GENERAL_MAX_CONTEXT_TOKENS = 8_192;
    public static final int SMOKING_MAX_CONTEXT_TOKENS = 4_096;
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 3_000;
    public static final int DEFAULT_READ_TIMEOUT_MS = 120_000;
    public static final int MAX_RESPONSE_BYTES = 65_536;

    private static final Pattern MODEL_NAME =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}");

    public enum Profile {
        TY1100_GENERAL_9B_VIA_ADB_REVERSE,
        TY1100_SMOKING_2B_VIA_ADB_REVERSE,
        TY1100_GENERAL_2B_VIA_TARGET_ETHERNET,
        TY1100_SMOKING_2B_VIA_TARGET_ETHERNET
    }

    private final Profile profile;
    private final URI baseUri;
    private final String modelName;
    private final int maximumContextTokens;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    private VllmEndpointConfig(
            Profile profile,
            String host,
            int port,
            String modelName,
            int maximumContextTokens,
            int connectTimeoutMs,
            int readTimeoutMs) {
        this.profile = Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(host, "host");
        this.modelName = requireModelName(modelName);
        if (maximumContextTokens < 1 || maximumContextTokens > 32_768) {
            throw new IllegalArgumentException("maximumContextTokens is out of range");
        }
        this.maximumContextTokens = maximumContextTokens;
        requireTimeout(connectTimeoutMs, 1, 30_000, "connectTimeoutMs");
        requireTimeout(readTimeoutMs, 1_000, 120_000, "readTimeoutMs");
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        try {
            baseUri = new URI("http", null, host, port, null, null, null);
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("fixed vLLM endpoint is invalid", exception);
        }
        validateFixedEndpoint();
    }

    public static VllmEndpointConfig ty1100EthernetViaAdbReverse() {
        return ty1100General9bViaAdbReverse();
    }

    public static VllmEndpointConfig ty1100General9bViaAdbReverse() {
        return new VllmEndpointConfig(
                Profile.TY1100_GENERAL_9B_VIA_ADB_REVERSE,
                DEVELOPMENT_HOST,
                GENERAL_VLLM_PORT,
                GENERAL_MODEL,
                GENERAL_MAX_CONTEXT_TOKENS,
                DEFAULT_CONNECT_TIMEOUT_MS,
                DEFAULT_READ_TIMEOUT_MS);
    }

    public static VllmEndpointConfig ty1100Smoking2bViaAdbReverse() {
        return new VllmEndpointConfig(
                Profile.TY1100_SMOKING_2B_VIA_ADB_REVERSE,
                DEVELOPMENT_HOST,
                SMOKING_VLLM_PORT,
                SMOKING_MODEL,
                SMOKING_MAX_CONTEXT_TOKENS,
                DEFAULT_CONNECT_TIMEOUT_MS,
                DEFAULT_READ_TIMEOUT_MS);
    }

    public static VllmEndpointConfig ty1100General2bViaTargetEthernet() {
        return new VllmEndpointConfig(
                Profile.TY1100_GENERAL_2B_VIA_TARGET_ETHERNET,
                TARGET_ETHERNET_HOST,
                TARGET_ETHERNET_VLLM_PORT,
                TARGET_ETHERNET_MODEL,
                GENERAL_MAX_CONTEXT_TOKENS,
                DEFAULT_CONNECT_TIMEOUT_MS,
                DEFAULT_READ_TIMEOUT_MS);
    }

    public static VllmEndpointConfig ty1100Smoking2bViaTargetEthernet() {
        return new VllmEndpointConfig(
                Profile.TY1100_SMOKING_2B_VIA_TARGET_ETHERNET,
                TARGET_ETHERNET_HOST,
                TARGET_ETHERNET_VLLM_PORT,
                TARGET_ETHERNET_MODEL,
                SMOKING_MAX_CONTEXT_TOKENS,
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

    public int getMaximumContextTokens() {
        return maximumContextTokens;
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

    public boolean isTargetEthernetProfile() {
        return profile == Profile.TY1100_GENERAL_2B_VIA_TARGET_ETHERNET
                || profile == Profile.TY1100_SMOKING_2B_VIA_TARGET_ETHERNET;
    }

    private void validateFixedEndpoint() {
        boolean general = profile == Profile.TY1100_GENERAL_9B_VIA_ADB_REVERSE
                && baseUri.getPort() == GENERAL_VLLM_PORT
                && GENERAL_MODEL.equals(modelName)
                && maximumContextTokens == GENERAL_MAX_CONTEXT_TOKENS;
        boolean smoking = profile == Profile.TY1100_SMOKING_2B_VIA_ADB_REVERSE
                && baseUri.getPort() == SMOKING_VLLM_PORT
                && SMOKING_MODEL.equals(modelName)
                && maximumContextTokens == SMOKING_MAX_CONTEXT_TOKENS;
        boolean targetGeneral = profile == Profile.TY1100_GENERAL_2B_VIA_TARGET_ETHERNET
                && baseUri.getPort() == TARGET_ETHERNET_VLLM_PORT
                && TARGET_ETHERNET_MODEL.equals(modelName)
                && maximumContextTokens == GENERAL_MAX_CONTEXT_TOKENS;
        boolean targetSmoking = profile == Profile.TY1100_SMOKING_2B_VIA_TARGET_ETHERNET
                && baseUri.getPort() == TARGET_ETHERNET_VLLM_PORT
                && TARGET_ETHERNET_MODEL.equals(modelName)
                && maximumContextTokens == SMOKING_MAX_CONTEXT_TOKENS;
        String expectedHost = isTargetEthernetProfile()
                ? TARGET_ETHERNET_HOST : DEVELOPMENT_HOST;
        if (!"http".equals(baseUri.getScheme())
                || !expectedHost.equals(baseUri.getHost())
                || baseUri.getUserInfo() != null
                || baseUri.getQuery() != null
                || baseUri.getFragment() != null
                || (!general && !smoking && !targetGeneral && !targetSmoking)) {
            throw new IllegalArgumentException("vLLM endpoint violates the fixed prototype profile");
        }
    }

    private static String requireModelName(String value) {
        if (value == null
                || !MODEL_NAME.matcher(value).matches()
                || value.contains("://")
                || value.contains("..")
                || (!GENERAL_MODEL.equals(value) && !SMOKING_MODEL.equals(value))) {
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
