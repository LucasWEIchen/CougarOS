package com.centralbrain.runtime.model;

import android.os.SystemClock;
import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Transitional target OpenClaw WebSocket v3 engine for controlled hardware-test builds. */
public final class OpenClawInferenceEngine implements LocalModelProvider.LocalInferenceEngine {
    private static final String TAG = "CentralBrainOpenClaw";
    private static final String PROFILE = "target_openclaw_transitional";
    private static final String SESSION_PREFIX = "agent:main:cougaros-";
    private static final int MAX_PENDING_PROMPTS = 16;
    private static final int MAX_REPLY_CHARS = 256;
    private static final int MAX_ACTIONS = 4;

    interface CredentialSource {
        String requireToken();
    }

    interface Transport {
        Result execute(Request request);
    }

    interface ElapsedClock {
        long nowMs();
    }

    static final class Request {
        final OpenClawEndpointConfig endpoint;
        final String token;
        final String sessionKey;
        final String idempotencyKey;
        final String message;
        final int remainingDeadlineMs;

        Request(
                OpenClawEndpointConfig endpoint,
                String token,
                String sessionKey,
                String idempotencyKey,
                String message,
                int remainingDeadlineMs) {
            this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
            this.token = requireCredential(token);
            this.sessionKey = Objects.requireNonNull(sessionKey, "sessionKey");
            this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
            this.message = Objects.requireNonNull(message, "message");
            this.remainingDeadlineMs = remainingDeadlineMs;
        }
    }

    static final class Result {
        final String assistantText;
        final int protocolVersion;
        final boolean historyFallbackUsed;

        Result(String assistantText, int protocolVersion, boolean historyFallbackUsed) {
            this.assistantText = Objects.requireNonNull(assistantText, "assistantText");
            this.protocolVersion = protocolVersion;
            this.historyFallbackUsed = historyFallbackUsed;
        }
    }

    private final OpenClawEndpointConfig endpoint;
    private final CredentialSource credentialSource;
    private final Transport transport;
    private final ElapsedClock clock;
    private final Map<String, CockpitModelPrompt> pending = new LinkedHashMap<>();
    private ModelProvider.ModelSpec warmedModel;
    private boolean closed;
    private long invocationCount;
    private long completedCount;
    private long failureCount;
    private long historyFallbackCount;
    private long lastLatencyMs;
    private String lastFailureCode = "";

    public OpenClawInferenceEngine(OpenClawEndpointConfig endpoint) {
        this(
                endpoint,
                endpoint::getEmbeddedToken,
                new SocketTransport(),
                SystemClock::elapsedRealtime);
    }

    OpenClawInferenceEngine(
            OpenClawEndpointConfig endpoint,
            CredentialSource credentialSource,
            Transport transport,
            ElapsedClock clock) {
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.credentialSource = Objects.requireNonNull(credentialSource, "credentialSource");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (!"ws://169.254.208.110:18789/".equals(
                endpoint.getWebSocketUri().toString())
                || endpoint.getProtocolVersion() != 3) {
            throw new IllegalArgumentException("OpenClaw target endpoint is not fixed");
        }
    }

    public synchronized void registerScenarioPrompt(String inputDigest, String scenarioId) {
        requireDigest(inputDigest);
        CockpitModelPrompt prompt = CockpitModelPrompt.forScenario(inputDigest, scenarioId);
        CockpitModelPrompt existing = pending.get(inputDigest);
        if (existing != null) {
            if (!existing.matches(prompt)) {
                throw new IllegalArgumentException("OpenClaw input digest prompt conflict");
            }
            return;
        }
        if (pending.size() >= MAX_PENDING_PROMPTS) {
            throw new IllegalStateException("OpenClaw prompt registry capacity exhausted");
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
        CockpitModelPrompt prompt;
        synchronized (this) {
            requireOpen();
            if (warmedModel == null
                    || !sameModel(warmedModel, modelSpec)) {
                throw new IllegalStateException("OpenClaw model gateway is not warmed");
            }
            prompt = pending.remove(request.getInputDigest());
            if (prompt == null) {
                throw new IllegalArgumentException("OpenClaw prompt material is unavailable");
            }
            invocationCount++;
        }
        if (cancellationSignal.isCancellationRequested()
                || cancellationSignal.isDeadlineExceeded()) {
            throw new IllegalStateException("OpenClaw request was cancelled before transport");
        }

        String message = buildPrompt(prompt);
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        if (messageBytes.length == 0
                || messageBytes.length > OpenClawEndpointConfig.MAX_REQUEST_BYTES) {
            throw new IllegalStateException("OpenClaw request exceeds the bounded envelope");
        }
        long startedAt = clock.nowMs();
        try {
            String token = credentialSource.requireToken();
            int remainingMs = remainingDeadlineMs(request);
            logInfo("openclaw_inference_started=true"
                    + " endpoint_profile=" + PROFILE
                    + " protocol=3"
                    + " raw_prompt_logged=false"
                    + " credential_logged=false");
            Result result = transport.execute(new Request(
                    endpoint,
                    token,
                    sessionKey(request),
                    idempotencyKey(request),
                    message,
                    remainingMs));
            if (cancellationSignal.isCancellationRequested()
                    || cancellationSignal.isDeadlineExceeded()) {
                throw new IllegalStateException("OpenClaw request crossed its deadline");
            }
            if (result.protocolVersion != endpoint.getProtocolVersion()) {
                throw new IllegalStateException("OpenClaw protocol negotiation mismatch");
            }
            byte[] canonical = parseAndValidate(result.assistantText, prompt);
            long latencyMs = Math.max(0L, clock.nowMs() - startedAt);
            synchronized (this) {
                completedCount++;
                if (result.historyFallbackUsed) {
                    historyFallbackCount++;
                }
                lastLatencyMs = latencyMs;
                lastFailureCode = "";
            }
            logInfo("openclaw_inference_completed=true"
                    + " endpoint_profile=" + PROFILE
                    + " protocol=3"
                    + " latency_ms=" + latencyMs
                    + " response_bytes=" + canonical.length
                    + " history_fallback_used=" + result.historyFallbackUsed
                    + " network_accessed=true"
                    + " external_compute_accessed=true"
                    + " direct_npu_accessed=false"
                    + " raw_prompt_logged=false"
                    + " raw_response_logged=false"
                    + " credential_logged=false");
            return LocalModelProvider.EngineOutput.of(canonical);
        } catch (RuntimeException failure) {
            String failureCode = safeFailureCode(failure);
            synchronized (this) {
                failureCount++;
                lastLatencyMs = Math.max(0L, clock.nowMs() - startedAt);
                lastFailureCode = failureCode;
            }
            logError("openclaw_inference_completed=false"
                    + " endpoint_profile=" + PROFILE
                    + " failure_code=" + failureCode
                    + " network_accessed=true"
                    + " raw_prompt_logged=false"
                    + " raw_response_logged=false"
                    + " credential_logged=false");
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
                historyFallbackCount,
                pending.size(),
                lastLatencyMs,
                lastFailureCode);
    }

    private int remainingDeadlineMs(ModelProvider.InferenceRequest request) {
        long remaining = request.getDeadlineElapsedRealtimeMs() - clock.nowMs();
        if (remaining <= 0) {
            throw new IllegalStateException("OpenClaw request deadline has expired");
        }
        return (int) Math.min(Integer.MAX_VALUE, remaining);
    }

    private static String buildPrompt(CockpitModelPrompt prompt) {
        String example = "{\"scenario_id\":\"" + prompt.getScenarioId()
                + "\",\"reply\":\"简短中文回复\",\"actions\":[\""
                + String.join("\",\"", prompt.getRequiredActions()) + "\"]}";
        return prompt.systemInstruction()
                + "键只能是scenario_id、reply、actions。"
                + "scenario_id必须是" + prompt.getScenarioId() + "。"
                + "reply必须是1到256个字符的简短中文。"
                + "actions必须包含1到4个不重复字符串，且只能来自："
                + String.join(",", prompt.getAllowedActions()) + "。"
                + "actions必须包含必要动作："
                + String.join(",", prompt.getRequiredActions()) + "。"
                + prompt.userInstruction() + "。"
                + "输出示例：" + example;
    }

    private static byte[] parseAndValidate(String raw, CockpitModelPrompt prompt) {
        if (raw == null) {
            throw new IllegalStateException("OpenClaw response is missing");
        }
        byte[] encoded = raw.getBytes(StandardCharsets.UTF_8);
        if (encoded.length == 0
                || encoded.length > OpenClawEndpointConfig.MAX_RESPONSE_BYTES) {
            throw new IllegalStateException("OpenClaw response size is invalid");
        }
        try {
            JsonObject content = JsonParser.parseString(decodeUtf8(encoded)).getAsJsonObject();
            if (content.size() != 3
                    || !content.has("scenario_id")
                    || !content.has("reply")
                    || !content.has("actions")) {
                throw new IllegalStateException("OpenClaw content shape is not exact");
            }
            if (!prompt.getScenarioId().equals(requiredString(content, "scenario_id"))) {
                throw new IllegalStateException("OpenClaw scenario binding does not match");
            }
            String reply = requiredString(content, "reply").trim();
            if (reply.isEmpty() || reply.length() > MAX_REPLY_CHARS) {
                throw new IllegalStateException("OpenClaw reply is outside the bounded contract");
            }
            for (int index = 0; index < reply.length(); index++) {
                if (Character.isISOControl(reply.charAt(index))) {
                    throw new IllegalStateException("OpenClaw reply contains control characters");
                }
            }
            JsonArray actions = content.getAsJsonArray("actions");
            if (actions == null || actions.isEmpty() || actions.size() > MAX_ACTIONS) {
                throw new IllegalStateException("OpenClaw action count is invalid");
            }
            List<String> admitted = new ArrayList<>();
            for (JsonElement element : actions) {
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                    throw new IllegalStateException("OpenClaw action must be a string");
                }
                String action = element.getAsString();
                if (!prompt.getAllowedActions().contains(action) || admitted.contains(action)) {
                    throw new IllegalStateException("OpenClaw returned an untrusted action");
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
            throw new IllegalStateException(
                    "OpenClaw response violates the structured contract", failure);
        }
    }

    private static String sessionKey(ModelProvider.InferenceRequest request) {
        return SESSION_PREFIX + request.getInputDigest().substring(0, 32);
    }

    private static String idempotencyKey(ModelProvider.InferenceRequest request) {
        return UUID.nameUUIDFromBytes(
                request.getRequestId().getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static boolean sameModel(
            ModelProvider.ModelSpec left,
            ModelProvider.ModelSpec right) {
        return left.getModelId().equals(right.getModelId())
                && left.getVersion().equals(right.getVersion())
                && left.getArtifactDigest().equals(right.getArtifactDigest());
    }

    private synchronized void requireOpen() {
        if (closed) {
            throw new IllegalStateException("OpenClaw engine is closed");
        }
    }

    private static void requireDigest(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("inputDigest must be a lowercase SHA-256");
        }
    }

    private static String requireCredential(String value) {
        if (value == null || value.length() < 8 || value.length() > 256) {
            throw new IllegalArgumentException("OpenClaw credential is invalid");
        }
        for (int index = 0; index < value.length(); index++) {
            char item = value.charAt(index);
            if (item < 0x21 || item > 0x7e) {
                throw new IllegalArgumentException("OpenClaw credential is invalid");
            }
        }
        return value;
    }

    private static String requiredString(JsonObject object, String field) {
        if (object == null || !object.has(field) || object.get(field).isJsonNull()) {
            throw new IllegalStateException("OpenClaw field is missing: " + field);
        }
        String value = object.get(field).getAsString();
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("OpenClaw field is empty: " + field);
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
            throw new IllegalStateException("OpenClaw response is not valid UTF-8", failure);
        }
    }

    private static String safeFailureCode(RuntimeException failure) {
        StringBuilder messages = new StringBuilder();
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 4; depth++) {
            if (current.getMessage() != null) {
                messages.append(' ').append(current.getMessage());
            }
            current = current.getCause();
        }
        String message = messages.toString();
        if (message.contains("credential")) return "CREDENTIAL_UNAVAILABLE";
        if (message.contains("handshake")) return "HANDSHAKE_REJECTED";
        if (message.contains("protocol")) return "PROTOCOL_REJECTED";
        if (message.contains("authentication") || message.contains("AUTH_")) {
            return "AUTHENTICATION_REJECTED";
        }
        if (message.contains("timeout") || message.contains("deadline")) {
            return "DEADLINE_EXCEEDED";
        }
        if (message.contains("cancel") || message.contains("abort")) return "CANCELLED";
        if (message.contains("scenario binding")) return "SCENARIO_BINDING_REJECTED";
        if (message.contains("reply")) return "REPLY_BOUNDS_REJECTED";
        if (message.contains("action")) return "ACTION_ALLOWLIST_REJECTED";
        if (message.contains("structured contract") || message.contains("content shape")) {
            return "STRUCTURED_OUTPUT_REJECTED";
        }
        if (message.contains("transport") || failure.getCause() instanceof IOException) {
            return "TRANSPORT_FAILURE";
        }
        return "INTERNAL_GATEWAY_FAILURE";
    }

    private static void logInfo(String message) {
        try {
            Log.i(TAG, message);
        } catch (RuntimeException ignored) {
            // Android logging is unavailable in host tests.
        }
    }

    private static void logError(String message) {
        try {
            Log.e(TAG, message);
        } catch (RuntimeException ignored) {
            // Android logging is unavailable in host tests.
        }
    }

    public static final class Snapshot {
        private final long invocationCount;
        private final long completedCount;
        private final long failureCount;
        private final long historyFallbackCount;
        private final int pendingPromptCount;
        private final long lastLatencyMs;
        private final String lastFailureCode;

        Snapshot(
                long invocationCount,
                long completedCount,
                long failureCount,
                long historyFallbackCount,
                int pendingPromptCount,
                long lastLatencyMs,
                String lastFailureCode) {
            this.invocationCount = invocationCount;
            this.completedCount = completedCount;
            this.failureCount = failureCount;
            this.historyFallbackCount = historyFallbackCount;
            this.pendingPromptCount = pendingPromptCount;
            this.lastLatencyMs = lastLatencyMs;
            this.lastFailureCode = lastFailureCode;
        }

        public long getInvocationCount() { return invocationCount; }
        public long getCompletedCount() { return completedCount; }
        public long getFailureCount() { return failureCount; }
        public long getHistoryFallbackCount() { return historyFallbackCount; }
        public int getPendingPromptCount() { return pendingPromptCount; }
        public long getLastLatencyMs() { return lastLatencyMs; }
        public String getLastFailureCode() { return lastFailureCode; }
    }

    private static final class SocketTransport implements Transport {
        private static final SecureRandom RANDOM = new SecureRandom();

        @Override
        public Result execute(Request request) {
            long deadlineNanos = System.nanoTime()
                    + request.remainingDeadlineMs * 1_000_000L;
            try (Socket socket = new Socket()) {
                socket.connect(
                        new InetSocketAddress(
                                request.endpoint.getWebSocketUri().getHost(),
                                request.endpoint.getWebSocketUri().getPort()),
                        Math.min(
                                request.endpoint.getConnectTimeoutMs(),
                                remainingMs(deadlineNanos)));
                socket.setTcpNoDelay(true);
                socket.setSoTimeout(Math.min(
                        request.endpoint.getReadTimeoutMs(), remainingMs(deadlineNanos)));
                logInfo("openclaw_protocol_stage=socket_connected");
                WebSocketConnection connection = new WebSocketConnection(
                        socket,
                        request.endpoint,
                        deadlineNanos);
                connection.handshake();
                logInfo("openclaw_protocol_stage=websocket_handshake_complete");
                JsonObject challenge = connection.readJson();
                if (!isEvent(challenge, "connect.challenge")) {
                    throw new IllegalStateException("OpenClaw connect challenge is missing");
                }
                String nonce = requiredString(challenge.getAsJsonObject("payload"), "nonce");
                if (nonce.isEmpty()) {
                    throw new IllegalStateException("OpenClaw connect nonce is missing");
                }
                logInfo("openclaw_protocol_stage=connect_challenge_received");
                int protocol = connect(connection, request);
                return chat(connection, request, protocol);
            } catch (SocketTimeoutException failure) {
                throw new IllegalStateException("OpenClaw transport timeout", failure);
            } catch (IOException failure) {
                throw new IllegalStateException("OpenClaw transport failed", failure);
            }
        }

        private static int connect(WebSocketConnection connection, Request request)
                throws IOException {
            String requestId = UUID.randomUUID().toString();
            JsonObject client = new JsonObject();
            client.addProperty("id", "openclaw-control-ui");
            client.addProperty("version", "cougaros-target-integration");
            client.addProperty("platform", "android");
            client.addProperty("mode", "webchat");

            JsonObject auth = new JsonObject();
            auth.addProperty("token", request.token);
            JsonObject params = new JsonObject();
            params.addProperty("minProtocol", request.endpoint.getProtocolVersion());
            params.addProperty("maxProtocol", request.endpoint.getProtocolVersion());
            params.add("client", client);
            params.addProperty("role", "operator");
            JsonArray scopes = new JsonArray();
            scopes.add("operator.read");
            scopes.add("operator.write");
            params.add("scopes", scopes);
            params.add("caps", new JsonArray());
            params.add("auth", auth);
            params.addProperty("locale", "zh-CN");
            params.addProperty("userAgent", "CougarOS-Android/0.3");
            connection.sendJson(rpcRequest(requestId, "connect", params));
            while (true) {
                JsonObject frame = connection.readJson();
                if (!isResponse(frame, requestId)) {
                    continue;
                }
                requireOk(frame, "OpenClaw authentication rejected");
                JsonObject payload = frame.getAsJsonObject("payload");
                int protocol = payload.get("protocol").getAsInt();
                if (protocol != request.endpoint.getProtocolVersion()) {
                    throw new IllegalStateException("OpenClaw protocol negotiation mismatch");
                }
                logInfo("openclaw_protocol_stage=connect_authenticated");
                return protocol;
            }
        }

        private static Result chat(
                WebSocketConnection connection,
                Request request,
                int protocol) throws IOException {
            String requestId = UUID.randomUUID().toString();
            JsonObject params = new JsonObject();
            params.addProperty("sessionKey", request.sessionKey);
            params.addProperty("message", request.message);
            params.addProperty("deliver", false);
            params.addProperty("idempotencyKey", request.idempotencyKey);
            connection.sendJson(rpcRequest(requestId, "chat.send", params));
            logInfo("openclaw_protocol_stage=chat_sent");

            boolean acknowledged = false;
            boolean finalSeen = false;
            String expectedRunId = request.idempotencyKey;
            String streamedText = "";
            try {
                while (true) {
                    JsonObject frame = connection.readJson();
                    if (isResponse(frame, requestId)) {
                        requireOk(frame, "OpenClaw chat request rejected");
                        acknowledged = true;
                        logInfo("openclaw_protocol_stage=chat_acknowledged");
                        JsonObject payload = objectOrNull(frame, "payload");
                        if (payload != null && hasString(payload, "runId")) {
                            expectedRunId = payload.get("runId").getAsString();
                        }
                        if (finalSeen) {
                            String history = readHistory(connection, request);
                            return new Result(history, protocol, true);
                        }
                        continue;
                    }
                    if (!isEvent(frame, "chat")) {
                        continue;
                    }
                    JsonObject event = objectOrNull(frame, "payload");
                    if (event == null
                            || !request.sessionKey.equals(optionalString(event, "sessionKey"))) {
                        continue;
                    }
                    String runId = optionalString(event, "runId");
                    if (!runId.isEmpty() && !expectedRunId.equals(runId)) {
                        continue;
                    }
                    String state = optionalString(event, "state");
                    if ("delta".equals(state)) {
                        String delta = extractText(event.get("message"));
                        if (delta.length() >= streamedText.length()) {
                            streamedText = delta;
                        }
                    } else if ("error".equals(state)) {
                        throw new IllegalStateException("OpenClaw chat terminal error");
                    } else if ("final".equals(state)) {
                        finalSeen = true;
                        logInfo("openclaw_protocol_stage=chat_final_received");
                        String terminal = extractText(event.get("message"));
                        String candidate = terminal.isEmpty() ? streamedText : terminal;
                        if (acknowledged && !candidate.isEmpty()) {
                            return new Result(candidate, protocol, false);
                        }
                        if (acknowledged) {
                            String history = readHistory(connection, request);
                            return new Result(history, protocol, true);
                        }
                    }
                }
            } catch (RuntimeException | IOException failure) {
                abortBestEffort(connection, request.sessionKey, expectedRunId);
                throw failure;
            }
        }

        private static String readHistory(
                WebSocketConnection connection,
                Request request) throws IOException {
            String requestId = UUID.randomUUID().toString();
            JsonObject params = new JsonObject();
            params.addProperty("sessionKey", request.sessionKey);
            params.addProperty("limit", 6);
            connection.sendJson(rpcRequest(requestId, "chat.history", params));
            while (true) {
                JsonObject frame = connection.readJson();
                if (!isResponse(frame, requestId)) {
                    continue;
                }
                requireOk(frame, "OpenClaw history request rejected");
                logInfo("openclaw_protocol_stage=history_received");
                JsonObject payload = frame.getAsJsonObject("payload");
                JsonArray messages = payload.getAsJsonArray("messages");
                if (messages == null || messages.isEmpty() || messages.size() > 6) {
                    throw new IllegalStateException("OpenClaw history envelope is invalid");
                }
                for (int index = messages.size() - 1; index >= 1; index--) {
                    JsonObject assistant = messages.get(index).getAsJsonObject();
                    if (!"assistant".equals(optionalString(assistant, "role"))) {
                        continue;
                    }
                    String assistantText = extractText(assistant);
                    if (assistantText.isEmpty()) {
                        continue;
                    }
                    for (int userIndex = index - 1; userIndex >= 0; userIndex--) {
                        JsonObject user = messages.get(userIndex).getAsJsonObject();
                        if (!"user".equals(optionalString(user, "role"))) {
                            continue;
                        }
                        if (!request.message.equals(extractText(user))) {
                            throw new IllegalStateException(
                                    "OpenClaw history is not bound to the current request");
                        }
                        return assistantText;
                    }
                }
                throw new IllegalStateException("OpenClaw history has no bounded assistant reply");
            }
        }

        private static void abortBestEffort(
                WebSocketConnection connection,
                String sessionKey,
                String runId) {
            try {
                JsonObject params = new JsonObject();
                params.addProperty("sessionKey", sessionKey);
                if (runId != null && !runId.isEmpty()) {
                    params.addProperty("runId", runId);
                }
                connection.sendJson(rpcRequest(
                        UUID.randomUUID().toString(), "chat.abort", params));
            } catch (RuntimeException | IOException ignored) {
                // The original bounded failure remains authoritative.
            }
        }

        private static JsonObject rpcRequest(
                String id,
                String method,
                JsonObject params) {
            JsonObject request = new JsonObject();
            request.addProperty("type", "req");
            request.addProperty("id", id);
            request.addProperty("method", method);
            request.add("params", params);
            return request;
        }

        private static boolean isResponse(JsonObject frame, String id) {
            return "res".equals(optionalString(frame, "type"))
                    && id.equals(optionalString(frame, "id"));
        }

        private static boolean isEvent(JsonObject frame, String event) {
            return "event".equals(optionalString(frame, "type"))
                    && event.equals(optionalString(frame, "event"));
        }

        private static void requireOk(JsonObject frame, String message) {
            if (!frame.has("ok") || !frame.get("ok").getAsBoolean()) {
                JsonObject error = objectOrNull(frame, "error");
                String code = error == null ? "UNKNOWN" : optionalString(error, "code");
                throw new IllegalStateException(message + " code=" + safeCode(code));
            }
        }

        private static String safeCode(String value) {
            if (value == null || value.isEmpty()) {
                return "UNKNOWN";
            }
            return value.replaceAll("[^A-Za-z0-9_.:-]", "_");
        }

        private static int remainingMs(long deadlineNanos) {
            long remaining = (deadlineNanos - System.nanoTime()) / 1_000_000L;
            if (remaining <= 0) {
                throw new IllegalStateException("OpenClaw transport deadline exceeded");
            }
            return (int) Math.min(Integer.MAX_VALUE, remaining);
        }

        private static final class WebSocketConnection {
            private final Socket socket;
            private final OpenClawEndpointConfig endpoint;
            private final long deadlineNanos;
            private final DataInputStream input;
            private final DataOutputStream output;

            WebSocketConnection(
                    Socket socket,
                    OpenClawEndpointConfig endpoint,
                    long deadlineNanos) throws IOException {
                this.socket = socket;
                this.endpoint = endpoint;
                this.deadlineNanos = deadlineNanos;
                input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            }

            void handshake() throws IOException {
                byte[] nonce = new byte[16];
                RANDOM.nextBytes(nonce);
                String key = Base64.getEncoder().encodeToString(nonce);
                String host = endpoint.getWebSocketUri().getHost()
                        + ":" + endpoint.getWebSocketUri().getPort();
                String request = "GET " + endpoint.getWebSocketUri().getPath()
                        + " HTTP/1.1\r\n"
                        + "Host: " + host + "\r\n"
                        + "Upgrade: websocket\r\n"
                        + "Connection: Upgrade\r\n"
                        + "Sec-WebSocket-Key: " + key + "\r\n"
                        + "Sec-WebSocket-Version: 13\r\n"
                        + "Origin: http://" + host + "\r\n\r\n";
                output.write(request.getBytes(StandardCharsets.US_ASCII));
                output.flush();
                byte[] headers = readHeaders();
                String decoded = new String(headers, StandardCharsets.ISO_8859_1);
                if (!decoded.startsWith("HTTP/1.1 101")) {
                    throw new IllegalStateException("OpenClaw WebSocket handshake rejected");
                }
                String expected = Base64.getEncoder().encodeToString(
                        sha1((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
                                .getBytes(StandardCharsets.US_ASCII)));
                boolean matched = false;
                for (String line : decoded.split("\\r\\n")) {
                    int delimiter = line.indexOf(':');
                    if (delimiter > 0
                            && "sec-websocket-accept".equals(
                                    line.substring(0, delimiter).trim()
                                            .toLowerCase(Locale.ROOT))
                            && expected.equals(line.substring(delimiter + 1).trim())) {
                        matched = true;
                    }
                }
                if (!matched) {
                    throw new IllegalStateException(
                            "OpenClaw WebSocket handshake accept mismatch");
                }
            }

            JsonObject readJson() throws IOException {
                while (true) {
                    socket.setSoTimeout(Math.min(
                            endpoint.getReadTimeoutMs(), remainingMs(deadlineNanos)));
                    Frame frame = readFrame();
                    if (frame.opcode == 0x8) {
                        throw new EOFException("OpenClaw WebSocket closed");
                    }
                    if (frame.opcode == 0x9) {
                        sendFrame(0xA, frame.payload);
                        continue;
                    }
                    if (frame.opcode != 0x1) {
                        continue;
                    }
                    try {
                        return JsonParser.parseString(decodeUtf8(frame.payload))
                                .getAsJsonObject();
                    } catch (RuntimeException failure) {
                        throw new IllegalStateException(
                                "OpenClaw WebSocket JSON frame is invalid", failure);
                    }
                }
            }

            void sendJson(JsonObject value) throws IOException {
                byte[] payload = value.toString().getBytes(StandardCharsets.UTF_8);
                if (payload.length == 0
                        || payload.length > OpenClawEndpointConfig.MAX_PREAUTH_FRAME_BYTES) {
                    throw new IllegalStateException("OpenClaw outbound frame is too large");
                }
                sendFrame(0x1, payload);
            }

            private byte[] readHeaders() throws IOException {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                int matched = 0;
                byte[] delimiter = {'\r', '\n', '\r', '\n'};
                while (matched < delimiter.length) {
                    int value = input.read();
                    if (value < 0) {
                        throw new EOFException("OpenClaw handshake closed");
                    }
                    buffer.write(value);
                    if (buffer.size() > OpenClawEndpointConfig.MAX_HANDSHAKE_BYTES) {
                        throw new IllegalStateException("OpenClaw handshake headers are too large");
                    }
                    matched = value == delimiter[matched]
                            ? matched + 1
                            : value == delimiter[0] ? 1 : 0;
                }
                return buffer.toByteArray();
            }

            private Frame readFrame() throws IOException {
                int first = input.readUnsignedByte();
                int second = input.readUnsignedByte();
                if ((first & 0x70) != 0 || (second & 0x80) != 0) {
                    throw new IllegalStateException("OpenClaw WebSocket frame flags are invalid");
                }
                boolean finalFrame = (first & 0x80) != 0;
                int opcode = first & 0x0f;
                long length = second & 0x7f;
                if (length == 126) {
                    length = input.readUnsignedShort();
                } else if (length == 127) {
                    length = input.readLong();
                }
                if (length < 0 || length > OpenClawEndpointConfig.MAX_FRAME_BYTES) {
                    throw new IllegalStateException("OpenClaw WebSocket frame is too large");
                }
                if ((opcode & 0x8) != 0 && (!finalFrame || length > 125)) {
                    throw new IllegalStateException("OpenClaw control frame is invalid");
                }
                byte[] payload = new byte[(int) length];
                input.readFully(payload);
                if (finalFrame || opcode >= 0x8) {
                    return new Frame(opcode, payload);
                }
                if (opcode != 0x1) {
                    throw new IllegalStateException("OpenClaw fragmented frame type is invalid");
                }
                ByteArrayOutputStream combined = new ByteArrayOutputStream();
                combined.write(payload);
                while (true) {
                    int continuationFirst = input.readUnsignedByte();
                    int continuationSecond = input.readUnsignedByte();
                    if ((continuationFirst & 0x0f) != 0
                            || (continuationSecond & 0x80) != 0) {
                        throw new IllegalStateException(
                                "OpenClaw continuation frame is invalid");
                    }
                    long continuationLength = continuationSecond & 0x7f;
                    if (continuationLength == 126) {
                        continuationLength = input.readUnsignedShort();
                    } else if (continuationLength == 127) {
                        continuationLength = input.readLong();
                    }
                    if (continuationLength < 0
                            || combined.size() + continuationLength
                                    > OpenClawEndpointConfig.MAX_FRAME_BYTES) {
                        throw new IllegalStateException(
                                "OpenClaw fragmented message is too large");
                    }
                    byte[] part = new byte[(int) continuationLength];
                    input.readFully(part);
                    combined.write(part);
                    if ((continuationFirst & 0x80) != 0) {
                        return new Frame(0x1, combined.toByteArray());
                    }
                }
            }

            private void sendFrame(int opcode, byte[] payload) throws IOException {
                byte[] mask = new byte[4];
                RANDOM.nextBytes(mask);
                output.writeByte(0x80 | opcode);
                if (payload.length < 126) {
                    output.writeByte(0x80 | payload.length);
                } else if (payload.length <= 65_535) {
                    output.writeByte(0x80 | 126);
                    output.writeShort(payload.length);
                } else {
                    output.writeByte(0x80 | 127);
                    output.writeLong(payload.length);
                }
                output.write(mask);
                for (int index = 0; index < payload.length; index++) {
                    output.writeByte(payload[index] ^ mask[index % 4]);
                }
                output.flush();
            }
        }

        private static final class Frame {
            final int opcode;
            final byte[] payload;

            Frame(int opcode, byte[] payload) {
                this.opcode = opcode;
                this.payload = payload;
            }
        }
    }

    private static String extractText(JsonElement message) {
        if (message == null || message.isJsonNull()) {
            return "";
        }
        if (message.isJsonPrimitive() && message.getAsJsonPrimitive().isString()) {
            return message.getAsString();
        }
        if (!message.isJsonObject()) {
            return "";
        }
        JsonObject object = message.getAsJsonObject();
        if (hasString(object, "text")) {
            return object.get("text").getAsString();
        }
        JsonElement content = object.get("content");
        if (content == null || content.isJsonNull()) {
            return "";
        }
        if (content.isJsonPrimitive() && content.getAsJsonPrimitive().isString()) {
            return content.getAsString();
        }
        if (!content.isJsonArray()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (JsonElement item : content.getAsJsonArray()) {
            if (item.isJsonObject()) {
                JsonObject part = item.getAsJsonObject();
                if ("text".equals(optionalString(part, "type")) && hasString(part, "text")) {
                    builder.append(part.get("text").getAsString());
                }
            }
        }
        return builder.toString();
    }

    private static JsonObject objectOrNull(JsonObject object, String field) {
        JsonElement element = object.get(field);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static boolean hasString(JsonObject object, String field) {
        JsonElement value = object.get(field);
        return value != null
                && value.isJsonPrimitive()
                && value.getAsJsonPrimitive().isString();
    }

    private static String optionalString(JsonObject object, String field) {
        return object != null && hasString(object, field)
                ? object.get(field).getAsString() : "";
    }

    private static byte[] sha1(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(input);
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-1 is unavailable", failure);
        }
    }
}
