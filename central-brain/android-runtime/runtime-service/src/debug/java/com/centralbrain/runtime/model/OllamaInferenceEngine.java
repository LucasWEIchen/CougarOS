package com.centralbrain.runtime.model;

import android.os.SystemClock;
import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Debug-only HTTP implementation of the local model engine backed by Ollama. */
public final class OllamaInferenceEngine implements LocalModelProvider.LocalInferenceEngine {
    private static final String TAG = "CentralBrainOllama";
    private static final int MAX_PENDING_PROMPTS = 16;
    private static final int MAX_TEXT_REQUEST_BYTES = 16_384;
    private static final int MAX_MULTIMODAL_REQUEST_BYTES = 8_500_000;
    static final int MAX_IMAGE_BYTES = 6 * 1024 * 1024;
    private static final int MAX_PENDING_IMAGE_BYTES = 12 * 1024 * 1024;
    private static final int MAX_REPLY_CHARS = 256;
    private static final int MAX_ACTIONS = 4;

    interface Transport {
        Response execute(Request request);
    }

    interface ElapsedClock {
        long nowMs();
    }

    static final class Request {
        final URI uri;
        final byte[] body;
        final int connectTimeoutMs;
        final int readTimeoutMs;
        final int maxResponseBytes;

        Request(
                URI uri,
                byte[] body,
                int connectTimeoutMs,
                int readTimeoutMs,
                int maxResponseBytes) {
            this.uri = Objects.requireNonNull(uri, "uri");
            this.body = Objects.requireNonNull(body, "body").clone();
            this.connectTimeoutMs = connectTimeoutMs;
            this.readTimeoutMs = readTimeoutMs;
            this.maxResponseBytes = maxResponseBytes;
        }
    }

    static final class Response {
        final int statusCode;
        final byte[] body;

        Response(int statusCode, byte[] body) {
            this.statusCode = statusCode;
            this.body = Objects.requireNonNull(body, "body").clone();
        }
    }

    private static final class ImageAttachment {
        private final String mimeType;
        private final String fileName;
        private final byte[] content;
        private final String sha256;

        private ImageAttachment(String mimeType, String fileName, byte[] content) {
            if (!"image/png".equals(mimeType) && !"image/jpeg".equals(mimeType)) {
                throw new IllegalArgumentException("Ollama image MIME is not allowlisted");
            }
            if (fileName == null || !fileName.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,95}")) {
                throw new IllegalArgumentException("Ollama image filename is invalid");
            }
            if (content == null || content.length == 0 || content.length > MAX_IMAGE_BYTES) {
                throw new IllegalArgumentException("Ollama image size is invalid");
            }
            OpenClawInferenceEngine.requireImageSignature(mimeType, content);
            this.mimeType = mimeType;
            this.fileName = fileName;
            this.content = content.clone();
            this.sha256 = sha256(this.content);
        }

        private boolean matches(ImageAttachment other) {
            return other != null
                    && mimeType.equals(other.mimeType)
                    && fileName.equals(other.fileName)
                    && sha256.equals(other.sha256)
                    && content.length == other.content.length;
        }

        private void clear() {
            Arrays.fill(content, (byte) 0);
        }
    }

    private final OllamaEndpointConfig endpoint;
    private final Transport transport;
    private final ElapsedClock clock;
    private final Map<String, CockpitModelPrompt> pending = new LinkedHashMap<>();
    private final Map<String, ImageAttachment> pendingImages = new LinkedHashMap<>();
    private int pendingImageBytes;
    private ModelProvider.ModelSpec warmedModel;
    private boolean closed;
    private long invocationCount;
    private long completedCount;
    private long failureCount;
    private long lastLatencyMs;

    public OllamaInferenceEngine(OllamaEndpointConfig endpoint) {
        this(endpoint, new UrlConnectionTransport(), SystemClock::elapsedRealtime);
    }

    OllamaInferenceEngine(OllamaEndpointConfig endpoint, Transport transport) {
        this(endpoint, transport, SystemClock::elapsedRealtime);
    }

    OllamaInferenceEngine(
            OllamaEndpointConfig endpoint,
            Transport transport,
            ElapsedClock clock) {
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        if (endpoint.getProfile()
                != OllamaEndpointConfig.Profile.DEVELOPMENT_WSL_ADB_REVERSE) {
            throw new IllegalArgumentException(
                    "debug Ollama engine accepts only the WSL ADB-reverse profile");
        }
        this.transport = Objects.requireNonNull(transport, "transport");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized void registerScenarioPrompt(String inputDigest, String scenarioId) {
        requireDigest(inputDigest);
        registerPrompt(CockpitModelPrompt.forScenario(inputDigest, scenarioId));
    }

    public synchronized void registerPrompt(CockpitModelPrompt prompt) {
        Objects.requireNonNull(prompt, "prompt");
        String inputDigest = prompt.getInputDigest();
        requireDigest(inputDigest);
        CockpitModelPrompt existing = pending.get(inputDigest);
        if (existing != null) {
            if (!existing.matches(prompt)) {
                throw new IllegalArgumentException("Ollama input digest prompt conflict");
            }
            return;
        }
        if (pending.size() >= MAX_PENDING_PROMPTS) {
            throw new IllegalStateException("Ollama prompt registry capacity exhausted");
        }
        pending.put(inputDigest, prompt);
    }

    /** Registers one digest-bound image for Ollama's chat message images array. */
    public synchronized void registerScenarioImageAttachment(
            String inputDigest,
            String mimeType,
            String fileName,
            byte[] content) {
        requireOpen();
        requireDigest(inputDigest);
        ImageAttachment attachment = new ImageAttachment(mimeType, fileName, content);
        ImageAttachment existing = pendingImages.get(inputDigest);
        if (existing != null) {
            if (!existing.matches(attachment)) {
                throw new IllegalArgumentException("Ollama input digest image conflict");
            }
            attachment.clear();
            return;
        }
        if (pendingImages.size() >= MAX_PENDING_PROMPTS
                || pendingImageBytes + attachment.content.length > MAX_PENDING_IMAGE_BYTES) {
            attachment.clear();
            throw new IllegalStateException("Ollama image registry capacity exhausted");
        }
        pendingImages.put(inputDigest, attachment);
        pendingImageBytes += attachment.content.length;
    }

    @Override
    public synchronized void warmup(ModelProvider.ModelSpec modelSpec) {
        requireOpen();
        warmedModel = Objects.requireNonNull(modelSpec, "modelSpec");
    }

    @Override
    public LocalModelProvider.EngineOutput infer(
            ModelProvider.ModelSpec modelSpec,
            ModelProvider.InferenceRequest request,
            LocalModelProvider.CancellationSignal cancellationSignal) {
        CockpitModelPrompt prompt;
        ImageAttachment imageAttachment;
        synchronized (this) {
            requireOpen();
            if (warmedModel == null
                    || !warmedModel.getModelId().equals(modelSpec.getModelId())
                    || !warmedModel.getVersion().equals(modelSpec.getVersion())
                    || !warmedModel.getArtifactDigest().equals(modelSpec.getArtifactDigest())) {
                throw new IllegalStateException("Ollama model is not warmed");
            }
            prompt = pending.remove(request.getInputDigest());
            imageAttachment = pendingImages.remove(request.getInputDigest());
            if (imageAttachment != null) {
                pendingImageBytes -= imageAttachment.content.length;
            }
            if (prompt == null) {
                if (imageAttachment != null) {
                    imageAttachment.clear();
                }
                throw new IllegalArgumentException("Ollama prompt material is unavailable");
            }
            invocationCount++;
        }
        long startedAt = clock.nowMs();
        boolean networkAccessed = false;
        try {
            if (cancellationSignal.isCancellationRequested()
                    || cancellationSignal.isDeadlineExceeded()) {
                throw new IllegalStateException(
                        "Ollama request was cancelled before transport");
            }
            if (prompt.getOutputContract()
                            == CockpitModelPrompt.OutputContract.SMOKING_DETECTION_V1
                    && imageAttachment == null) {
                throw new IllegalStateException(
                        "Ollama smoking detection requires an image attachment");
            }
            byte[] requestBody = buildRequest(prompt, imageAttachment);
            int remainingMs = remainingDeadlineMs(request);
            networkAccessed = true;
            Response response = transport.execute(new Request(
                    endpoint.getChatUri(),
                    requestBody,
                    Math.min(endpoint.getConnectTimeoutMs(), remainingMs),
                    Math.min(endpoint.getReadTimeoutMs(), remainingMs),
                    OllamaEndpointConfig.MAX_RESPONSE_BYTES));
            if (cancellationSignal.isCancellationRequested()
                    || cancellationSignal.isDeadlineExceeded()) {
                throw new IllegalStateException("Ollama request crossed its deadline");
            }
            byte[] canonical = parseAndValidate(response, prompt);
            long latencyMs = Math.max(0L, clock.nowMs() - startedAt);
            synchronized (this) {
                completedCount++;
                lastLatencyMs = latencyMs;
            }
            logInfo("ollama_inference_completed=true"
                    + " endpoint_profile=development_wsl_adb_reverse"
                    + " model=" + safeToken(endpoint.getModelName())
                    + " image_present=" + (imageAttachment != null)
                    + " image_bytes="
                    + (imageAttachment == null ? 0 : imageAttachment.content.length)
                    + " image_sha256="
                    + (imageAttachment == null ? "none" : imageAttachment.sha256)
                    + " latency_ms=" + latencyMs
                    + " response_bytes=" + canonical.length
                    + " network_accessed=true"
                    + " npu_accessed=false"
                    + " hardware_accessed=false"
                    + " raw_prompt_logged=false"
                    + " raw_response_logged=false");
            return LocalModelProvider.EngineOutput.of(canonical);
        } catch (RuntimeException failure) {
            synchronized (this) {
                failureCount++;
                lastLatencyMs = Math.max(0L, clock.nowMs() - startedAt);
            }
            logError("ollama_inference_completed=false"
                    + " endpoint_profile=development_wsl_adb_reverse"
                    + " failure_code=" + safeFailureCode(failure)
                    + " network_accessed=" + networkAccessed
                    + " raw_prompt_logged=false"
                    + " raw_response_logged=false");
            throw failure;
        } finally {
            if (imageAttachment != null) {
                imageAttachment.clear();
            }
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        warmedModel = null;
        pending.clear();
        for (ImageAttachment attachment : pendingImages.values()) {
            attachment.clear();
        }
        pendingImages.clear();
        pendingImageBytes = 0;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(
                invocationCount,
                completedCount,
                failureCount,
                pending.size(),
                lastLatencyMs);
    }

    private int remainingDeadlineMs(ModelProvider.InferenceRequest request) {
        long remaining = request.getDeadlineElapsedRealtimeMs() - clock.nowMs();
        if (remaining <= 0) {
            throw new IllegalStateException("Ollama request deadline has expired");
        }
        return (int) Math.min(Integer.MAX_VALUE, remaining);
    }

    private byte[] buildRequest(
            CockpitModelPrompt prompt,
            ImageAttachment imageAttachment) {
        JsonObject root = new JsonObject();
        root.addProperty("model", endpoint.getModelName());
        root.addProperty("stream", false);
        root.addProperty("think", false);
        root.addProperty("keep_alive", "5m");

        JsonArray messages = new JsonArray();
        if (prompt.getOutputContract()
                == CockpitModelPrompt.OutputContract.SMOKING_DETECTION_V1) {
            messages.add(message("system", prompt.systemInstruction()));
            JsonObject user = message("user", prompt.userInstruction());
            JsonArray images = new JsonArray();
            images.add(Base64.getEncoder().encodeToString(imageAttachment.content));
            user.add("images", images);
            messages.add(user);
        } else {
            String exactShape = "唯一合法输出形状：{\"scenario_id\":\""
                    + prompt.getScenarioId()
                    + "\",\"reply\":\"简短中文回复\",\"actions\":[\""
                    + String.join("\",\"", prompt.getRequiredActions())
                    + "\"]}。键名scenario_id、reply、actions必须完全一致；"
                    + "actions的每一项必须是可用动作中的字符串，禁止输出对象。";
            messages.add(message(
                    "system",
                    prompt.systemInstruction() + exactShape));
            messages.add(message(
                    "user",
                    prompt.userInstruction()
                            + "\n可用动作：" + String.join(",", prompt.getAllowedActions())
                            + "\n必要动作：" + String.join(",", prompt.getRequiredActions())
                            + "\n" + exactShape));
        }
        root.add("messages", messages);
        root.add("format", responseSchema(prompt));

        JsonObject options = new JsonObject();
        options.addProperty("temperature", 0);
        options.addProperty("num_predict", 192);
        root.add("options", options);
        byte[] encoded = root.toString().getBytes(StandardCharsets.UTF_8);
        int maximumRequestBytes = imageAttachment == null
                ? MAX_TEXT_REQUEST_BYTES : MAX_MULTIMODAL_REQUEST_BYTES;
        if (encoded.length > maximumRequestBytes) {
            throw new IllegalStateException("Ollama request exceeds the bounded envelope");
        }
        return encoded;
    }

    private byte[] parseAndValidate(Response response, CockpitModelPrompt prompt) {
        if (response.statusCode < 200 || response.statusCode >= 300) {
            throw new IllegalStateException("Ollama returned HTTP " + response.statusCode);
        }
        if (response.body.length == 0
                || response.body.length > OllamaEndpointConfig.MAX_RESPONSE_BYTES) {
            throw new IllegalStateException("Ollama response size is invalid");
        }
        try {
            JsonObject envelope = JsonParser.parseString(
                    decodeUtf8(response.body)).getAsJsonObject();
            if (!requiredString(envelope, "model").equals(endpoint.getModelName())) {
                throw new IllegalStateException("Ollama response model does not match");
            }
            if (!envelope.has("done") || !envelope.get("done").getAsBoolean()) {
                throw new IllegalStateException("Ollama response is not terminal");
            }
            JsonObject message = envelope.getAsJsonObject("message");
            String rawContent = requiredString(message, "content");
            if (prompt.getOutputContract()
                    == CockpitModelPrompt.OutputContract.SMOKING_DETECTION_V1) {
                SmokingDetectionResult result = SmokingDetectionResult.parse(rawContent);
                return smokingProjection(prompt, result);
            }
            JsonObject content = JsonParser.parseString(rawContent).getAsJsonObject();
            if (content.size() != 3
                    || !content.has("scenario_id")
                    || !content.has("reply")
                    || !content.has("actions")) {
                throw new IllegalStateException("Ollama content shape is not exact");
            }
            if (!prompt.getScenarioId().equals(requiredString(content, "scenario_id"))) {
                throw new IllegalStateException("Ollama scenario binding does not match");
            }
            String reply = requiredString(content, "reply").trim();
            if (reply.isEmpty() || reply.length() > MAX_REPLY_CHARS) {
                throw new IllegalStateException("Ollama reply is outside the bounded contract");
            }
            for (int index = 0; index < reply.length(); index++) {
                if (Character.isISOControl(reply.charAt(index))) {
                    throw new IllegalStateException("Ollama reply contains control characters");
                }
            }
            JsonArray actions = content.getAsJsonArray("actions");
            if (actions == null || actions.isEmpty() || actions.size() > MAX_ACTIONS) {
                throw new IllegalStateException("Ollama action count is invalid");
            }
            List<String> admitted = new ArrayList<>();
            for (JsonElement element : actions) {
                String action = element.getAsString();
                if (!prompt.getAllowedActions().contains(action) || admitted.contains(action)) {
                    throw new IllegalStateException("Ollama returned an untrusted action");
                }
                admitted.add(action);
            }
            prompt.validateAdmittedActions(admitted);
            JsonObject canonical = new JsonObject();
            canonical.addProperty("scenario_id", prompt.getScenarioId());
            canonical.addProperty("reply", reply);
            JsonArray canonicalActions = new JsonArray();
            for (String action : admitted) {
                canonicalActions.add(action);
            }
            canonical.add("actions", canonicalActions);
            return canonical.toString().getBytes(StandardCharsets.UTF_8);
        } catch (RuntimeException failure) {
            throw new IllegalStateException("Ollama response violates the structured contract", failure);
        }
    }

    private static JsonObject responseSchema(CockpitModelPrompt prompt) {
        if (prompt.getOutputContract()
                == CockpitModelPrompt.OutputContract.SMOKING_DETECTION_V1) {
            return smokingResponseSchema();
        }
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.addProperty("additionalProperties", false);
        JsonObject properties = new JsonObject();

        JsonObject scenario = new JsonObject();
        scenario.addProperty("type", "string");
        JsonArray scenarioEnum = new JsonArray();
        scenarioEnum.add(prompt.getScenarioId());
        scenario.add("enum", scenarioEnum);
        properties.add("scenario_id", scenario);

        JsonObject reply = new JsonObject();
        reply.addProperty("type", "string");
        reply.addProperty("minLength", 1);
        reply.addProperty("maxLength", MAX_REPLY_CHARS);
        properties.add("reply", reply);

        JsonObject actions = new JsonObject();
        actions.addProperty("type", "array");
        actions.addProperty("minItems", 1);
        actions.addProperty("maxItems", MAX_ACTIONS);
        actions.addProperty("uniqueItems", true);
        JsonObject item = new JsonObject();
        item.addProperty("type", "string");
        JsonArray actionEnum = new JsonArray();
        for (String action : prompt.getAllowedActions()) {
            actionEnum.add(action);
        }
        item.add("enum", actionEnum);
        actions.add("items", item);
        properties.add("actions", actions);
        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("scenario_id");
        required.add("reply");
        required.add("actions");
        schema.add("required", required);
        return schema;
    }

    private static JsonObject smokingResponseSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.addProperty("additionalProperties", false);
        JsonObject properties = new JsonObject();
        properties.add("smoking_detected", integerSchema(0, 1));
        properties.add("person_count", integerSchema(0, 2));

        JsonObject location = new JsonObject();
        location.addProperty("type", "string");
        JsonArray locations = new JsonArray();
        for (String value : List.of(
                "IMAGE_ROW_2_LEFT",
                "IMAGE_ROW_2_RIGHT",
                "IMAGE_ROW_1_LEFT",
                "IMAGE_ROW_1_RIGHT",
                "UNKNOWN")) {
            locations.add(value);
        }
        location.add("enum", locations);
        properties.add("location", location);

        JsonObject confidence = new JsonObject();
        confidence.addProperty("type", "number");
        confidence.addProperty("minimum", 0);
        confidence.addProperty("maximum", 1);
        properties.add("confidence", confidence);

        JsonObject description = new JsonObject();
        description.addProperty("type", "string");
        description.addProperty("minLength", 1);
        description.addProperty("maxLength", SmokingDetectionResult.MAX_DESCRIPTION_CHARS);
        properties.add("description", description);
        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        for (String field : List.of(
                "smoking_detected",
                "person_count",
                "location",
                "confidence",
                "description")) {
            required.add(field);
        }
        schema.add("required", required);
        return schema;
    }

    private static JsonObject integerSchema(int minimum, int maximum) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "integer");
        schema.addProperty("minimum", minimum);
        schema.addProperty("maximum", maximum);
        return schema;
    }

    private static byte[] smokingProjection(
            CockpitModelPrompt prompt,
            SmokingDetectionResult result) {
        List<String> admitted = List.of("assistant.respond");
        prompt.validateAdmittedActions(admitted);
        JsonObject canonical = new JsonObject();
        canonical.addProperty("scenario_id", prompt.getScenarioId());
        canonical.addProperty("reply", result.toCompactJson());
        JsonArray actions = new JsonArray();
        actions.add("assistant.respond");
        canonical.add("actions", actions);
        return canonical.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static JsonObject message(String role, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        return message;
    }

    private static String requiredString(JsonObject object, String field) {
        if (object == null || !object.has(field) || object.get(field).isJsonNull()) {
            throw new IllegalStateException("Ollama field is missing: " + field);
        }
        String value = object.get(field).getAsString();
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Ollama field is empty: " + field);
        }
        return value;
    }

    private static String decodeUtf8(byte[] value) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(value))
                    .toString();
        } catch (CharacterCodingException failure) {
            throw new IllegalStateException("Ollama response is not valid UTF-8", failure);
        }
    }

    private static String sha256(byte[] input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input);
            char[] output = new char[digest.length * 2];
            char[] alphabet = "0123456789abcdef".toCharArray();
            for (int index = 0; index < digest.length; index++) {
                int value = digest[index] & 0xff;
                output[index * 2] = alphabet[value >>> 4];
                output[index * 2 + 1] = alphabet[value & 0x0f];
            }
            return new String(output);
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }

    private synchronized void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Ollama engine is closed");
        }
    }

    private static void requireDigest(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("inputDigest must be a lowercase SHA-256");
        }
    }

    private static String safeToken(String value) {
        if (value == null) {
            return "unknown";
        }
        return value.replaceAll("[^A-Za-z0-9._:-]", "_");
    }

    private static String safeFailureCode(RuntimeException failure) {
        Throwable current = failure;
        boolean transportFailure = false;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                if (message.startsWith("Ollama transport failed")) {
                    transportFailure = true;
                }
                if (message.startsWith("Ollama returned HTTP")) {
                    return "HTTP_STATUS_REJECTED";
                }
                if (message.contains("response size")
                        || message.contains("response exceeds maximum bytes")) {
                    return "RESPONSE_SIZE_REJECTED";
                }
                if (message.contains("response model")) {
                    return "MODEL_IDENTITY_REJECTED";
                }
                if (message.contains("not terminal")) {
                    return "NON_TERMINAL_RESPONSE";
                }
                if (message.contains("scenario binding")) {
                    return "SCENARIO_BINDING_REJECTED";
                }
                if (message.contains("reply is outside")
                        || message.contains("reply contains control")) {
                    return "REPLY_BOUNDS_REJECTED";
                }
                if (message.contains("action count")) {
                    return "ACTION_COUNT_REJECTED";
                }
                if (message.contains("untrusted action")) {
                    return "ACTION_ALLOWLIST_REJECTED";
                }
                if (message.contains("structured contract")
                        || message.contains("CB_SMOKING_RESULT")
                        || message.startsWith("Ollama field is")
                        || message.contains("content shape")
                        || message.contains("valid UTF-8")) {
                    return "STRUCTURED_OUTPUT_REJECTED";
                }
                if (message.contains("deadline")) {
                    return "DEADLINE_EXCEEDED";
                }
                if (message.contains("cancel")) {
                    return "CANCELLED";
                }
            }
            current = current.getCause();
        }
        return transportFailure ? "TRANSPORT_FAILURE" : "INTERNAL_GATEWAY_FAILURE";
    }

    private static void logInfo(String message) {
        try {
            Log.i(TAG, message);
        } catch (RuntimeException ignored) {
            // Host unit tests use the Android stub; logging is non-functional there.
        }
    }

    private static void logError(String message) {
        try {
            Log.e(TAG, message);
        } catch (RuntimeException ignored) {
            // Host unit tests use the Android stub; logging is non-functional there.
        }
    }

    public static final class Snapshot {
        private final long invocationCount;
        private final long completedCount;
        private final long failureCount;
        private final int pendingPromptCount;
        private final long lastLatencyMs;

        Snapshot(
                long invocationCount,
                long completedCount,
                long failureCount,
                int pendingPromptCount,
                long lastLatencyMs) {
            this.invocationCount = invocationCount;
            this.completedCount = completedCount;
            this.failureCount = failureCount;
            this.pendingPromptCount = pendingPromptCount;
            this.lastLatencyMs = lastLatencyMs;
        }

        public long getInvocationCount() { return invocationCount; }
        public long getCompletedCount() { return completedCount; }
        public long getFailureCount() { return failureCount; }
        public int getPendingPromptCount() { return pendingPromptCount; }
        public long getLastLatencyMs() { return lastLatencyMs; }
    }

    private static final class UrlConnectionTransport implements Transport {
        @Override
        public Response execute(Request request) {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) request.uri.toURL().openConnection();
                connection.setRequestMethod("POST");
                connection.setInstanceFollowRedirects(false);
                connection.setDoOutput(true);
                connection.setConnectTimeout(request.connectTimeoutMs);
                connection.setReadTimeout(request.readTimeoutMs);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestProperty("Accept", "application/json");
                connection.setFixedLengthStreamingMode(request.body.length);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(request.body);
                }
                int status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 400
                        ? connection.getInputStream() : connection.getErrorStream();
                return new Response(status, readBounded(stream, request.maxResponseBytes));
            } catch (IOException failure) {
                throw new IllegalStateException("Ollama transport failed", failure);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        private static byte[] readBounded(InputStream stream, int maximumBytes)
                throws IOException {
            if (stream == null) {
                return new byte[0];
            }
            try (InputStream input = stream;
                    ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4_096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > maximumBytes) {
                        throw new IOException("Ollama response exceeds maximum bytes");
                    }
                    output.write(buffer, 0, count);
                }
                return output.toByteArray();
            }
        }
    }
}
