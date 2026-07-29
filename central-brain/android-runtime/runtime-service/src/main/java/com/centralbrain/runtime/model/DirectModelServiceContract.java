package com.centralbrain.runtime.model;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Provider-neutral contract for AIOS-owned direct access to a model service.
 *
 * <p>The contract carries identity, modality, bounds and digests. Raw text and image bytes remain
 * in the bounded input owner and are handed to a protocol adapter only after routing. The model
 * service never receives authority to invoke tools or vehicle effects.</p>
 *
 * <p>Req IDs: APP-002, APP-004, S2-MDL-001/002/003/004/005/006,
 * S2-OBS-001/002, S2-SAF-001.</p>
 */
public final class DirectModelServiceContract {
    public static final int SCHEMA_VERSION = 1;
    public static final String PRODUCTION_PROFILE_ID =
            "production.direct-model-service.ollama-chat-v1";
    public static final String DEVELOPMENT_PROFILE_ID =
            "development.direct-model-service.ollama-chat-v1";
    public static final int MAX_TEXT_BYTES = 16_384;
    public static final int MAX_IMAGE_COUNT = 1;
    public static final long MAX_IMAGE_BYTES = 6L * 1024L * 1024L;
    public static final int MAX_RESPONSE_BYTES = 65_536;

    private static final Pattern IDENTIFIER =
            Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private static final Pattern MODEL_NAME =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}");
    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern MIME = Pattern.compile("image/(?:png|jpeg)");

    private DirectModelServiceContract() {
    }

    public enum WireProtocol {
        OLLAMA_CHAT_V1,
        OPENAI_COMPATIBLE_CHAT_V1,
        VENDOR_NATIVE_V1
    }

    public enum Modality {
        TEXT,
        TEXT_IMAGE
    }

    public enum Stage {
        ADMITTED,
        INPUT_VALIDATED,
        NETWORK_CONNECTING,
        REQUEST_SENT,
        STREAMING,
        TERMINAL,
        CANCELLED,
        FAILED
    }

    /** Build-owned endpoint descriptor; callers cannot construct one from an arbitrary URL. */
    public static final class Endpoint {
        private final String profileId;
        private final WireProtocol wireProtocol;
        private final URI chatUri;
        private final String modelName;
        private final int connectTimeoutMs;
        private final int readTimeoutMs;
        private final boolean textSupported;
        private final boolean imageSupported;
        private final boolean streamingSupported;
        private final boolean structuredJsonSupported;

        private Endpoint(
                String profileId,
                WireProtocol wireProtocol,
                URI chatUri,
                String modelName,
                int connectTimeoutMs,
                int readTimeoutMs,
                boolean textSupported,
                boolean imageSupported,
                boolean streamingSupported,
                boolean structuredJsonSupported) {
            this.profileId = requireIdentifier(profileId, "profileId");
            this.wireProtocol = Objects.requireNonNull(wireProtocol, "wireProtocol");
            this.chatUri = Objects.requireNonNull(chatUri, "chatUri");
            this.modelName = requireModelName(modelName);
            if (connectTimeoutMs < 1 || connectTimeoutMs > 30_000) {
                throw new IllegalArgumentException("connectTimeoutMs is out of range");
            }
            if (readTimeoutMs < 1_000 || readTimeoutMs > 120_000) {
                throw new IllegalArgumentException("readTimeoutMs is out of range");
            }
            if (!textSupported || !structuredJsonSupported) {
                throw new IllegalArgumentException(
                        "direct model service must support text and structured JSON");
            }
            this.connectTimeoutMs = connectTimeoutMs;
            this.readTimeoutMs = readTimeoutMs;
            this.textSupported = textSupported;
            this.imageSupported = imageSupported;
            this.streamingSupported = streamingSupported;
            this.structuredJsonSupported = structuredJsonSupported;
        }

        public String getProfileId() {
            return profileId;
        }

        public WireProtocol getWireProtocol() {
            return wireProtocol;
        }

        public URI getChatUri() {
            return chatUri;
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

        public boolean isTextSupported() {
            return textSupported;
        }

        public boolean isImageSupported() {
            return imageSupported;
        }

        public boolean isStreamingSupported() {
            return streamingSupported;
        }

        public boolean isStructuredJsonSupported() {
            return structuredJsonSupported;
        }

        public boolean isAgentGatewayRequired() {
            return false;
        }

        public boolean isArbitraryEndpointOverrideAllowed() {
            return false;
        }
    }

    /** Digest-only description of one bounded image owned by the runtime input boundary. */
    public static final class ImageDescriptor {
        private final String mimeType;
        private final long byteCount;
        private final String sha256;

        public ImageDescriptor(String mimeType, long byteCount, String sha256) {
            if (mimeType == null || !MIME.matcher(mimeType).matches()) {
                throw new IllegalArgumentException("image mimeType is not allowlisted");
            }
            if (byteCount < 1 || byteCount > MAX_IMAGE_BYTES) {
                throw new IllegalArgumentException("image byteCount is out of range");
            }
            this.mimeType = mimeType;
            this.byteCount = byteCount;
            this.sha256 = requireDigest(sha256, "imageSha256");
        }

        public String getMimeType() {
            return mimeType;
        }

        public long getByteCount() {
            return byteCount;
        }

        public String getSha256() {
            return sha256;
        }

        private String canonicalForm() {
            return mimeType + "|" + byteCount + "|" + sha256;
        }
    }

    /**
     * Request metadata bound to the ModelContractV2 request. It intentionally contains no raw
     * prompt, image bytes, credential or vehicle payload.
     */
    public static final class Request {
        private final String requestId;
        private final String sessionId;
        private final String idempotencyKey;
        private final Modality modality;
        private final String textDigest;
        private final String contextDigest;
        private final String responseSchemaDigest;
        private final ImageDescriptor image;
        private final long deadlineElapsedRealtimeMs;
        private final String requestFingerprint;

        public Request(
                Endpoint endpoint,
                String requestId,
                String sessionId,
                String idempotencyKey,
                Modality modality,
                String textDigest,
                String contextDigest,
                String responseSchemaDigest,
                ImageDescriptor image,
                long deadlineElapsedRealtimeMs) {
            Endpoint checkedEndpoint = Objects.requireNonNull(endpoint, "endpoint");
            this.requestId = requireIdentifier(requestId, "requestId");
            this.sessionId = requireIdentifier(sessionId, "sessionId");
            this.idempotencyKey = requireIdentifier(idempotencyKey, "idempotencyKey");
            this.modality = Objects.requireNonNull(modality, "modality");
            this.textDigest = requireDigest(textDigest, "textDigest");
            this.contextDigest = requireDigest(contextDigest, "contextDigest");
            this.responseSchemaDigest =
                    requireDigest(responseSchemaDigest, "responseSchemaDigest");
            if (deadlineElapsedRealtimeMs < 1) {
                throw new IllegalArgumentException(
                        "deadlineElapsedRealtimeMs must be positive");
            }
            if (modality == Modality.TEXT && image != null) {
                throw new IllegalArgumentException("text request cannot carry an image");
            }
            if (modality == Modality.TEXT_IMAGE
                    && (image == null || !checkedEndpoint.isImageSupported())) {
                throw new IllegalArgumentException(
                        "text-image request requires an image-capable endpoint");
            }
            this.image = image;
            this.deadlineElapsedRealtimeMs = deadlineElapsedRealtimeMs;
            this.requestFingerprint = sha256(
                    SCHEMA_VERSION
                            + "|" + checkedEndpoint.getProfileId()
                            + "|" + checkedEndpoint.getWireProtocol().name()
                            + "|" + checkedEndpoint.getModelName()
                            + "|" + this.requestId
                            + "|" + this.sessionId
                            + "|" + this.idempotencyKey
                            + "|" + this.modality.name()
                            + "|" + this.textDigest
                            + "|" + this.contextDigest
                            + "|" + this.responseSchemaDigest
                            + "|" + (image == null ? "-" : image.canonicalForm())
                            + "|" + this.deadlineElapsedRealtimeMs);
        }

        public int getSchemaVersion() {
            return SCHEMA_VERSION;
        }

        public String getRequestId() {
            return requestId;
        }

        public String getSessionId() {
            return sessionId;
        }

        public String getIdempotencyKey() {
            return idempotencyKey;
        }

        public Modality getModality() {
            return modality;
        }

        public String getTextDigest() {
            return textDigest;
        }

        public String getContextDigest() {
            return contextDigest;
        }

        public String getResponseSchemaDigest() {
            return responseSchemaDigest;
        }

        public ImageDescriptor getImage() {
            return image;
        }

        public long getDeadlineElapsedRealtimeMs() {
            return deadlineElapsedRealtimeMs;
        }

        public String getRequestFingerprint() {
            return requestFingerprint;
        }

        public boolean containsRawModelInput() {
            return false;
        }

        public boolean grantsToolAuthority() {
            return false;
        }

        public boolean grantsEffectAuthority() {
            return false;
        }
    }

    public static Endpoint productionOllama(String modelName) {
        OllamaEndpointConfig config = OllamaEndpointConfig.productionLinkLocal(modelName);
        return new Endpoint(
                PRODUCTION_PROFILE_ID,
                WireProtocol.OLLAMA_CHAT_V1,
                config.getChatUri(),
                config.getModelName(),
                config.getConnectTimeoutMs(),
                config.getReadTimeoutMs(),
                true,
                true,
                true,
                true);
    }

    /**
     * Fixed debug endpoint reached from an Android test device through ADB reverse.
     *
     * <p>This profile is constructed only by debug composition. It does not make the provider
     * production eligible and it cannot redirect to an arbitrary host.</p>
     */
    public static Endpoint developmentOllama(String modelName) {
        OllamaEndpointConfig config =
                OllamaEndpointConfig.developmentWslAdbReverse(modelName);
        return new Endpoint(
                DEVELOPMENT_PROFILE_ID,
                WireProtocol.OLLAMA_CHAT_V1,
                config.getChatUri(),
                config.getModelName(),
                config.getConnectTimeoutMs(),
                config.getReadTimeoutMs(),
                true,
                true,
                true,
                true);
    }

    private static String requireIdentifier(String value, String name) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static String requireDigest(String value, String name) {
        if (value == null || !DIGEST.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a lowercase SHA-256");
        }
        return value;
    }

    private static String requireModelName(String value) {
        if (value == null || !MODEL_NAME.matcher(value).matches()) {
            throw new IllegalArgumentException("modelName is invalid");
        }
        return value;
    }

    private static String sha256(String value) {
        try {
            byte[] encoded = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(encoded.length * 2);
            for (byte item : encoded) {
                builder.append(String.format("%02x", item & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
