package com.centralbrain.runtime.model;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Fixed Ollama endpoint profiles for the Android runtime.
 *
 * <p>The development profile reaches WSL only through an explicit ADB reverse tunnel. The
 * production profile is restricted to the dedicated link-local AI base. Callers cannot supply an
 * arbitrary URL.</p>
 *
 * <p>Req IDs: S2-MDL-001, XSC-001/005/006, DEL-001/003/004.</p>
 */
public final class OllamaEndpointConfig {
    public static final int OLLAMA_PORT = 11_434;
    public static final String DEVELOPMENT_HOST = "127.0.0.1";
    public static final String PRODUCTION_HOST = "169.254.208.110";
    public static final String CHAT_PATH = "/api/chat";
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 3_000;
    public static final int DEFAULT_READ_TIMEOUT_MS = 120_000;
    public static final int MAX_RESPONSE_BYTES = 65_536;

    private static final Pattern MODEL_NAME =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}");

    public enum Profile {
        DEVELOPMENT_WSL_ADB_REVERSE,
        PRODUCTION_LINK_LOCAL
    }

    private final Profile profile;
    private final URI baseUri;
    private final String modelName;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    private OllamaEndpointConfig(
            Profile profile,
            String host,
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
            baseUri = new URI("http", null, host, OLLAMA_PORT, null, null, null);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Ollama endpoint is invalid", exception);
        }
        validateFixedHost();
    }

    public static OllamaEndpointConfig developmentWslAdbReverse(String modelName) {
        return new OllamaEndpointConfig(
                Profile.DEVELOPMENT_WSL_ADB_REVERSE,
                DEVELOPMENT_HOST,
                modelName,
                DEFAULT_CONNECT_TIMEOUT_MS,
                DEFAULT_READ_TIMEOUT_MS);
    }

    public static OllamaEndpointConfig productionLinkLocal(String modelName) {
        return new OllamaEndpointConfig(
                Profile.PRODUCTION_LINK_LOCAL,
                PRODUCTION_HOST,
                modelName,
                DEFAULT_CONNECT_TIMEOUT_MS,
                DEFAULT_READ_TIMEOUT_MS);
    }

    public Profile getProfile() {
        return profile;
    }

    public URI getBaseUri() {
        return baseUri;
    }

    public URI getChatUri() {
        return baseUri.resolve(CHAT_PATH);
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
        return profile == Profile.PRODUCTION_LINK_LOCAL;
    }

    public boolean targetsExternalComputeBase() {
        return isProductionProfile();
    }

    private void validateFixedHost() {
        String expected = profile == Profile.DEVELOPMENT_WSL_ADB_REVERSE
                ? DEVELOPMENT_HOST : PRODUCTION_HOST;
        if (!"http".equals(baseUri.getScheme())
                || !expected.equals(baseUri.getHost())
                || baseUri.getPort() != OLLAMA_PORT
                || baseUri.getUserInfo() != null
                || baseUri.getQuery() != null
                || baseUri.getFragment() != null) {
            throw new IllegalArgumentException("Ollama endpoint violates the fixed profile");
        }
    }

    private static String requireModelName(String value) {
        if (value == null
                || !MODEL_NAME.matcher(value).matches()
                || value.contains("://")
                || value.contains("..")
                || "UNCONFIGURED".equals(value)) {
            throw new IllegalArgumentException("Ollama model name is not configured");
        }
        return value;
    }

    private static void requireTimeout(int value, int minimum, int maximum, String field) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(field + " is out of range");
        }
    }
}
