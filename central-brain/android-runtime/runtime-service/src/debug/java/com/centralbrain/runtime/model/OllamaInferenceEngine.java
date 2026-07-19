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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Debug-only HTTP implementation of the local model engine backed by Ollama. */
public final class OllamaInferenceEngine implements LocalModelProvider.LocalInferenceEngine {
    private static final String TAG = "CentralBrainOllama";
    private static final int MAX_PENDING_PROMPTS = 16;
    private static final int MAX_REQUEST_BYTES = 16_384;
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

    private static final class Prompt {
        final String inputDigest;
        final String scenarioId;
        final String utterance;
        final List<String> allowedActions;

        Prompt(
                String inputDigest,
                String scenarioId,
                String utterance,
                List<String> allowedActions) {
            this.inputDigest = inputDigest;
            this.scenarioId = scenarioId;
            this.utterance = utterance;
            this.allowedActions = allowedActions;
        }

        boolean matches(Prompt other) {
            return scenarioId.equals(other.scenarioId)
                    && utterance.equals(other.utterance)
                    && allowedActions.equals(other.allowedActions);
        }
    }

    private final OllamaEndpointConfig endpoint;
    private final Transport transport;
    private final ElapsedClock clock;
    private final Map<String, Prompt> pending = new LinkedHashMap<>();
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
        Prompt prompt = prompt(inputDigest, scenarioId);
        Prompt existing = pending.get(inputDigest);
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
        Prompt prompt;
        synchronized (this) {
            requireOpen();
            if (warmedModel == null
                    || !warmedModel.getModelId().equals(modelSpec.getModelId())
                    || !warmedModel.getVersion().equals(modelSpec.getVersion())
                    || !warmedModel.getArtifactDigest().equals(modelSpec.getArtifactDigest())) {
                throw new IllegalStateException("Ollama model is not warmed");
            }
            prompt = pending.remove(request.getInputDigest());
            if (prompt == null) {
                throw new IllegalArgumentException("Ollama prompt material is unavailable");
            }
            invocationCount++;
        }
        if (cancellationSignal.isCancellationRequested()
                || cancellationSignal.isDeadlineExceeded()) {
            throw new IllegalStateException("Ollama request was cancelled before transport");
        }

        byte[] requestBody = buildRequest(prompt);
        int remainingMs = remainingDeadlineMs(request);
        long startedAt = clock.nowMs();
        try {
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
                    + " network_accessed=true"
                    + " raw_prompt_logged=false"
                    + " raw_response_logged=false");
            throw failure;
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        warmedModel = null;
        pending.clear();
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

    private byte[] buildRequest(Prompt prompt) {
        JsonObject root = new JsonObject();
        root.addProperty("model", endpoint.getModelName());
        root.addProperty("stream", false);
        root.addProperty("think", false);
        root.addProperty("keep_alive", "5m");

        JsonArray messages = new JsonArray();
        String exactShape = "唯一合法输出形状：{\"scenario_id\":\""
                + prompt.scenarioId
                + "\",\"reply\":\"简短中文回复\",\"actions\":[\""
                + prompt.allowedActions.get(0)
                + "\"]}。键名scenario_id、reply、actions必须完全一致；"
                + "actions的每一项必须是可用动作中的字符串，禁止输出对象。";
        messages.add(message(
                "system",
                "你是车载AIOS的场景规划器。只输出一个符合JSON Schema的对象，不输出解释。"
                        + "不得创建未列出的动作，不得声明动作已经在真实车辆上执行。"
                        + exactShape));
        messages.add(message(
                "user",
                "用户表达：" + prompt.utterance
                        + "\n场景ID：" + prompt.scenarioId
                        + "\n可用动作：" + String.join(",", prompt.allowedActions)
                        + "\n" + exactShape));
        root.add("messages", messages);
        root.add("format", responseSchema(prompt));

        JsonObject options = new JsonObject();
        options.addProperty("temperature", 0);
        options.addProperty("num_predict", 192);
        root.add("options", options);
        byte[] encoded = root.toString().getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_REQUEST_BYTES) {
            throw new IllegalStateException("Ollama request exceeds the bounded envelope");
        }
        return encoded;
    }

    private byte[] parseAndValidate(Response response, Prompt prompt) {
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
            JsonObject content = JsonParser.parseString(
                    requiredString(message, "content")).getAsJsonObject();
            if (content.size() != 3
                    || !content.has("scenario_id")
                    || !content.has("reply")
                    || !content.has("actions")) {
                throw new IllegalStateException("Ollama content shape is not exact");
            }
            if (!prompt.scenarioId.equals(requiredString(content, "scenario_id"))) {
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
                if (!prompt.allowedActions.contains(action) || admitted.contains(action)) {
                    throw new IllegalStateException("Ollama returned an untrusted action");
                }
                admitted.add(action);
            }
            JsonObject canonical = new JsonObject();
            canonical.addProperty("scenario_id", prompt.scenarioId);
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

    private static JsonObject responseSchema(Prompt prompt) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.addProperty("additionalProperties", false);
        JsonObject properties = new JsonObject();

        JsonObject scenario = new JsonObject();
        scenario.addProperty("type", "string");
        JsonArray scenarioEnum = new JsonArray();
        scenarioEnum.add(prompt.scenarioId);
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
        for (String action : prompt.allowedActions) {
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

    private static Prompt prompt(String inputDigest, String scenarioId) {
        if ("scene.comfort.cold.v1".equals(scenarioId)) {
            return new Prompt(
                    inputDigest,
                    scenarioId,
                    "车里有点冷",
                    List.of("hvac.warm_cabin", "media.keep_playing"));
        }
        if ("scene.fatigue.assist.v1".equals(scenarioId)) {
            return new Prompt(
                    inputDigest,
                    scenarioId,
                    "我有些疲惫",
                    List.of(
                            "seat.recline",
                            "hvac.ventilate",
                            "media.pause",
                            "navigation.find_rest_area"));
        }
        throw new IllegalArgumentException("Ollama scenario is not allowlisted");
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
