package com.example.openclaw;

import android.net.Network;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Closeable;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Business-neutral OpenClaw protocol 3 client bound to one Android Ethernet Network.
 *
 * <p>Callbacks run on OkHttp threads. The caller must switch to the main thread before updating
 * Android UI.
 */
public final class OpenClawEthClient implements Closeable {
    public interface Listener {
        void onStage(String stage);

        void onDelta(String text);
    }

    public static final class ImageInput {
        private static final Pattern FILE_NAME =
                Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,95}");

        final String mimeType;
        final String fileName;
        final byte[] content;

        public ImageInput(String mimeType, String fileName, byte[] content) {
            if (!"image/png".equals(mimeType) && !"image/jpeg".equals(mimeType)) {
                throw new IllegalArgumentException("image MIME must be image/png or image/jpeg");
            }
            if (fileName == null || !FILE_NAME.matcher(fileName).matches()) {
                throw new IllegalArgumentException("image file name is invalid");
            }
            if (content == null || content.length == 0 || content.length > MAX_IMAGE_BYTES) {
                throw new IllegalArgumentException("image must contain 1..6291456 bytes");
            }
            requireImageSignature(mimeType, content);
            this.mimeType = mimeType;
            this.fileName = fileName;
            this.content = content.clone();
        }
    }

    private static final int PROTOCOL_VERSION = 3;
    private static final int MAX_IMAGE_BYTES = 6 * 1024 * 1024;
    private static final int MAX_TEXT_BYTES = 16 * 1024;
    private static final int MAX_PREAUTH_FRAME_BYTES = 65_536;
    private static final int MAX_MULTIMODAL_FRAME_BYTES = 8_500_000;
    private static final int MAX_INBOUND_FRAME_BYTES = 1_048_576;
    private static final Listener NOOP_LISTENER = new Listener() {
        @Override
        public void onStage(String stage) {
        }

        @Override
        public void onDelta(String text) {
        }
    };

    private final String endpoint;
    private final String origin;
    private final String token;
    private final int requestTimeoutMs;
    private final OkHttpClient httpClient;
    private final ScheduledExecutorService timeoutExecutor;
    private final AtomicBoolean closed = new AtomicBoolean();

    public OpenClawEthClient(
            Network ethernetNetwork,
            String endpoint,
            String token,
            int requestTimeoutMs) {
        Objects.requireNonNull(ethernetNetwork, "ethernetNetwork");
        this.endpoint = requireEndpoint(endpoint);
        this.origin = this.endpoint.substring(0, this.endpoint.length() - 1)
                .replaceFirst("^ws://", "http://");
        this.token = requireToken(token);
        if (requestTimeoutMs < 1_000 || requestTimeoutMs > 120_000) {
            throw new IllegalArgumentException("requestTimeoutMs must be in 1000..120000");
        }
        this.requestTimeoutMs = requestTimeoutMs;
        this.httpClient = new OkHttpClient.Builder()
                .socketFactory(ethernetNetwork.getSocketFactory())
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(15, TimeUnit.SECONDS)
                .build();
        ThreadFactory daemonFactory = runnable -> {
            Thread thread = new Thread(runnable, "openclaw-eth-timeout");
            thread.setDaemon(true);
            return thread;
        };
        this.timeoutExecutor = Executors.newSingleThreadScheduledExecutor(daemonFactory);
    }

    public CompletableFuture<String> queryText(String text, Listener listener) {
        return query(text, null, listener);
    }

    public CompletableFuture<String> queryTextAndImage(
            String text,
            ImageInput image,
            Listener listener) {
        return query(text, Objects.requireNonNull(image, "image"), listener);
    }

    private CompletableFuture<String> query(
            String text,
            ImageInput image,
            Listener listener) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("OpenClaw client is closed"));
        }
        String boundedText = requireText(text);
        QuerySession session = new QuerySession(
                boundedText,
                image,
                listener == null ? NOOP_LISTENER : listener);
        Request request = new Request.Builder()
                .url(endpoint)
                .header("Origin", origin)
                .build();
        httpClient.newWebSocket(request, session);
        return session.future;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        timeoutExecutor.shutdownNow();
        httpClient.dispatcher().cancelAll();
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }

    private final class QuerySession extends WebSocketListener {
        final String text;
        final ImageInput image;
        final Listener listener;
        final String sessionKey = "agent:main:eth-debug-"
                + UUID.randomUUID().toString().replace("-", "");
        final String idempotencyKey = UUID.randomUUID().toString();
        final CompletableFuture<String> future = new CompletableFuture<>();
        final AtomicBoolean terminal = new AtomicBoolean();
        final ScheduledFuture<?> timeout;

        WebSocket socket;
        String connectRequestId;
        String chatRequestId;
        String runId = "";
        String eventRunId = "";
        String streamedText = "";
        String finalText = "";
        boolean acknowledged;
        boolean finalSeen;

        QuerySession(String text, ImageInput image, Listener listener) {
            this.text = text;
            this.image = image;
            this.listener = listener;
            this.timeout = timeoutExecutor.schedule(
                    () -> fail(new TimeoutException("OpenClaw request deadline exceeded")),
                    requestTimeoutMs,
                    TimeUnit.MILLISECONDS);
        }

        @Override
        public synchronized void onOpen(WebSocket webSocket, Response response) {
            socket = webSocket;
            if (terminal.get()) {
                webSocket.cancel();
                return;
            }
            stage("WEBSOCKET_OPEN");
        }

        @Override
        public synchronized void onMessage(WebSocket webSocket, String raw) {
            if (terminal.get()) {
                return;
            }
            try {
                if (raw.getBytes(StandardCharsets.UTF_8).length > MAX_INBOUND_FRAME_BYTES) {
                    throw new IllegalStateException("inbound OpenClaw frame is too large");
                }
                JSONObject frame = new JSONObject(raw);
                if (isEvent(frame, "connect.challenge")) {
                    handleChallenge(frame);
                } else if (isResponse(frame, connectRequestId)) {
                    handleConnectResponse(frame);
                } else if (isResponse(frame, chatRequestId)) {
                    handleChatAck(frame);
                } else if (isEvent(frame, "chat")) {
                    handleChatEvent(frame);
                }
            } catch (RuntimeException | JSONException failure) {
                fail(failure);
            }
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable failure, Response response) {
            fail(new IllegalStateException("OpenClaw WebSocket failed", failure));
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            if (!terminal.get()) {
                fail(new IllegalStateException("OpenClaw WebSocket closed before final reply"));
            }
        }

        private void handleChallenge(JSONObject frame) throws JSONException {
            if (connectRequestId != null) {
                throw new IllegalStateException("duplicate OpenClaw challenge");
            }
            String nonce = frame.getJSONObject("payload").optString("nonce", "");
            if (nonce.isEmpty()) {
                throw new IllegalStateException("OpenClaw challenge nonce is missing");
            }
            stage("CHALLENGE_RECEIVED");
            connectRequestId = UUID.randomUUID().toString();
            JSONObject client = new JSONObject()
                    .put("id", "openclaw-control-ui")
                    .put("version", "eth-debug-client/1.0")
                    .put("platform", "android")
                    .put("mode", "webchat");
            JSONObject params = new JSONObject()
                    .put("minProtocol", PROTOCOL_VERSION)
                    .put("maxProtocol", PROTOCOL_VERSION)
                    .put("client", client)
                    .put("role", "operator")
                    .put("scopes", new JSONArray()
                            .put("operator.read")
                            .put("operator.write"))
                    .put("caps", new JSONArray())
                    .put("auth", new JSONObject().put("token", token))
                    .put("locale", "zh-CN")
                    .put("userAgent", "OpenClaw-Eth-Debug/1.0");
            send(rpcRequest(connectRequestId, "connect", params), MAX_PREAUTH_FRAME_BYTES);
            stage("CONNECT_SENT");
        }

        private void handleConnectResponse(JSONObject frame) throws JSONException {
            requireOk(frame, "OpenClaw authentication rejected");
            int protocol = frame.getJSONObject("payload").optInt("protocol", -1);
            if (protocol != PROTOCOL_VERSION) {
                throw new IllegalStateException("OpenClaw protocol mismatch: " + protocol);
            }
            stage("AUTHENTICATED");
            chatRequestId = UUID.randomUUID().toString();
            JSONObject params = new JSONObject()
                    .put("sessionKey", sessionKey)
                    .put("message", text)
                    .put("deliver", false)
                    .put("idempotencyKey", idempotencyKey);
            int maximumBytes = MAX_PREAUTH_FRAME_BYTES;
            if (image != null) {
                JSONObject attachment = new JSONObject()
                        .put("type", "image")
                        .put("mimeType", image.mimeType)
                        .put("fileName", image.fileName)
                        .put("content", Base64.getEncoder().encodeToString(image.content));
                params.put("attachments", new JSONArray().put(attachment));
                maximumBytes = MAX_MULTIMODAL_FRAME_BYTES;
            }
            send(rpcRequest(chatRequestId, "chat.send", params), maximumBytes);
            stage("CHAT_SENT");
        }

        private void handleChatAck(JSONObject frame) throws JSONException {
            requireOk(frame, "OpenClaw chat request rejected");
            acknowledged = true;
            JSONObject payload = frame.optJSONObject("payload");
            runId = payload == null ? "" : payload.optString("runId", "");
            if (!runId.isEmpty() && !eventRunId.isEmpty() && !runId.equals(eventRunId)) {
                throw new IllegalStateException("OpenClaw runId changed during request");
            }
            stage("CHAT_ACKNOWLEDGED");
            completeIfReady();
        }

        private void handleChatEvent(JSONObject frame) throws JSONException {
            JSONObject payload = frame.optJSONObject("payload");
            if (payload == null || !sessionKey.equals(payload.optString("sessionKey", ""))) {
                return;
            }
            String candidateRunId = payload.optString("runId", "");
            if (!candidateRunId.isEmpty()) {
                if (!runId.isEmpty() && !runId.equals(candidateRunId)) {
                    return;
                }
                if (!eventRunId.isEmpty() && !eventRunId.equals(candidateRunId)) {
                    return;
                }
                eventRunId = candidateRunId;
            }
            String state = payload.optString("state", "");
            if ("delta".equals(state)) {
                streamedText = mergeText(streamedText, extractText(payload.opt("message")));
                if (!streamedText.isEmpty()) {
                    listener.onDelta(streamedText);
                }
            } else if ("final".equals(state)) {
                finalSeen = true;
                String terminalText = extractText(payload.opt("message"));
                finalText = terminalText.isEmpty() ? streamedText : terminalText;
                stage("FINAL_RECEIVED");
                completeIfReady();
            } else if ("error".equals(state)) {
                throw new IllegalStateException("OpenClaw returned a terminal chat error");
            }
        }

        private void completeIfReady() {
            if (!acknowledged || !finalSeen) {
                return;
            }
            if (finalText.isEmpty()) {
                fail(new IllegalStateException("OpenClaw final reply is empty"));
                return;
            }
            if (!terminal.compareAndSet(false, true)) {
                return;
            }
            timeout.cancel(false);
            future.complete(finalText);
            if (socket != null) {
                socket.close(1000, "complete");
            }
        }

        private void send(JSONObject frame, int maximumBytes) {
            if (socket == null) {
                throw new IllegalStateException("OpenClaw WebSocket is unavailable");
            }
            String encoded = frame.toString();
            int bytes = encoded.getBytes(StandardCharsets.UTF_8).length;
            if (bytes == 0 || bytes > maximumBytes) {
                throw new IllegalStateException("OpenClaw outbound frame is too large");
            }
            if (!socket.send(encoded)) {
                throw new IllegalStateException("OpenClaw outbound queue is closed");
            }
        }

        private void stage(String stage) {
            listener.onStage(stage);
        }

        private void fail(Throwable failure) {
            if (!terminal.compareAndSet(false, true)) {
                return;
            }
            timeout.cancel(false);
            future.completeExceptionally(failure);
            WebSocket current = socket;
            if (current != null) {
                current.cancel();
            }
        }
    }

    private static JSONObject rpcRequest(String id, String method, JSONObject params)
            throws JSONException {
        return new JSONObject()
                .put("type", "req")
                .put("id", id)
                .put("method", method)
                .put("params", params);
    }

    private static boolean isResponse(JSONObject frame, String id) {
        return id != null
                && "res".equals(frame.optString("type", ""))
                && id.equals(frame.optString("id", ""));
    }

    private static boolean isEvent(JSONObject frame, String event) {
        return "event".equals(frame.optString("type", ""))
                && event.equals(frame.optString("event", ""));
    }

    private static void requireOk(JSONObject frame, String prefix) {
        if (frame.optBoolean("ok", false)) {
            return;
        }
        JSONObject error = frame.optJSONObject("error");
        String code = error == null ? "UNKNOWN" : safeError(error.optString("code", "UNKNOWN"));
        String message = error == null ? "" : safeError(error.optString("message", ""));
        throw new IllegalStateException(prefix + " code=" + code + " message=" + message);
    }

    private static String extractText(Object message) throws JSONException {
        if (message == null || message == JSONObject.NULL) {
            return "";
        }
        if (message instanceof String) {
            return (String) message;
        }
        if (!(message instanceof JSONObject)) {
            return "";
        }
        JSONObject object = (JSONObject) message;
        if (object.opt("text") instanceof String) {
            return object.getString("text");
        }
        Object content = object.opt("content");
        if (content instanceof String) {
            return (String) content;
        }
        if (!(content instanceof JSONArray)) {
            return "";
        }
        JSONArray parts = (JSONArray) content;
        StringBuilder value = new StringBuilder();
        for (int index = 0; index < parts.length(); index++) {
            JSONObject part = parts.optJSONObject(index);
            if (part != null
                    && "text".equals(part.optString("type", ""))
                    && part.opt("text") instanceof String) {
                value.append(part.getString("text"));
            }
        }
        return value.toString();
    }

    private static String mergeText(String accumulated, String incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return accumulated;
        }
        if (incoming.startsWith(accumulated)) {
            return incoming;
        }
        if (accumulated.endsWith(incoming)) {
            return accumulated;
        }
        return accumulated + incoming;
    }

    private static String requireEndpoint(String value) {
        if (!"ws://169.254.208.110:18789/".equals(value)) {
            throw new IllegalArgumentException(
                    "endpoint must be ws://169.254.208.110:18789/");
        }
        return value;
    }

    private static String requireToken(String value) {
        if (value == null || value.length() < 8 || value.length() > 256) {
            throw new IllegalArgumentException("OpenClaw token is invalid");
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < 0x21 || character > 0x7e) {
                throw new IllegalArgumentException("OpenClaw token is invalid");
            }
        }
        return value;
    }

    private static String requireText(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("text query is empty");
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > MAX_TEXT_BYTES) {
            throw new IllegalArgumentException("text query exceeds 16 KiB");
        }
        return value;
    }

    private static String safeError(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value.replaceAll("[^A-Za-z0-9_.: -]", "_");
        return sanitized.length() <= 160 ? sanitized : sanitized.substring(0, 160);
    }

    private static void requireImageSignature(String mimeType, byte[] content) {
        boolean png = content.length >= 8
                && (content[0] & 0xff) == 0x89
                && content[1] == 0x50
                && content[2] == 0x4e
                && content[3] == 0x47
                && content[4] == 0x0d
                && content[5] == 0x0a
                && content[6] == 0x1a
                && content[7] == 0x0a;
        boolean jpeg = content.length >= 4
                && (content[0] & 0xff) == 0xff
                && (content[1] & 0xff) == 0xd8
                && (content[content.length - 2] & 0xff) == 0xff
                && (content[content.length - 1] & 0xff) == 0xd9;
        if (("image/png".equals(mimeType) && !png)
                || ("image/jpeg".equals(mimeType) && !jpeg)) {
            throw new IllegalArgumentException("image signature does not match MIME");
        }
    }
}
