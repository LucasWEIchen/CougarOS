package com.centralbrain.runtime.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
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
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Production-source Ollama chat protocol adapter used by the direct model service Provider.
 *
 * <p>This class owns only wire mapping, bounded stream parsing and transport cancellation. Session,
 * prompt policy, tool orchestration, structured-output admission and vehicle effects remain owned
 * by the Central Brain runtime.</p>
 *
 * <p>Req IDs: APP-002, APP-004, S2-MDL-001/003/004/005/006, S2-OBS-001/002,
 * S2-SAF-001.</p>
 */
public final class OllamaChatProtocolAdapter {
    public static final int MAX_SYSTEM_PROMPT_BYTES = 32_768;
    public static final int MAX_SCHEMA_BYTES = 16_384;
    public static final int MAX_HTTP_REQUEST_BYTES = 8_500_000;
    public static final int MAX_STREAM_LINE_BYTES = 65_536;

    private final Transport transport;
    private final ElapsedClock clock;
    private final ConcurrentMap<String, Call> activeCalls = new ConcurrentHashMap<>();

    public OllamaChatProtocolAdapter() {
        this(new UrlConnectionTransport(), () -> System.nanoTime() / 1_000_000L);
    }

    OllamaChatProtocolAdapter(Transport transport, ElapsedClock clock) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Result execute(
            DirectModelServiceContract.Endpoint endpoint,
            DirectModelServiceContract.Request request,
            Payload payload,
            StreamObserver observer,
            CancellationSignal cancellationSignal) {
        DirectModelServiceContract.Endpoint checkedEndpoint =
                Objects.requireNonNull(endpoint, "endpoint");
        DirectModelServiceContract.Request checkedRequest =
                Objects.requireNonNull(request, "request");
        Payload checkedPayload = Objects.requireNonNull(payload, "payload");
        StreamObserver checkedObserver = Objects.requireNonNull(observer, "observer");
        CancellationSignal checkedCancellation =
                Objects.requireNonNull(cancellationSignal, "cancellationSignal");

        validateBinding(checkedEndpoint, checkedRequest, checkedPayload);
        checkCancellation(checkedRequest, checkedCancellation, null);
        checkedObserver.onStage(DirectModelServiceContract.Stage.INPUT_VALIDATED);

        byte[] body = buildRequestBody(checkedEndpoint, checkedPayload);
        int remainingMs = remainingDeadlineMs(checkedRequest);
        HttpRequest httpRequest = new HttpRequest(
                checkedEndpoint.getChatUri(),
                body,
                Math.min(checkedEndpoint.getConnectTimeoutMs(), remainingMs),
                Math.min(checkedEndpoint.getReadTimeoutMs(), remainingMs),
                DirectModelServiceContract.MAX_RESPONSE_BYTES);
        Call call = transport.open(httpRequest);
        Call previous = activeCalls.putIfAbsent(checkedRequest.getRequestId(), call);
        if (previous != null) {
            call.cancel();
            call.close();
            throw failure(FailureCode.DUPLICATE_REQUEST, "request is already active");
        }

        try {
            checkedObserver.onStage(DirectModelServiceContract.Stage.NETWORK_CONNECTING);
            try (HttpResponse response = call.execute()) {
                checkedObserver.onStage(DirectModelServiceContract.Stage.REQUEST_SENT);
                checkCancellation(checkedRequest, checkedCancellation, call);
                if (response.getStatusCode() < 200 || response.getStatusCode() >= 300) {
                    throw failure(
                            FailureCode.HTTP_STATUS_REJECTED,
                            "HTTP status was rejected");
                }
                Result result = parseStream(
                        checkedEndpoint,
                        checkedRequest,
                        response.getBody(),
                        checkedObserver,
                        checkedCancellation,
                        call);
                checkedObserver.onStage(DirectModelServiceContract.Stage.TERMINAL);
                return result;
            }
        } catch (AdapterException failure) {
            throw failure;
        } catch (IOException failure) {
            if (call.isCancelled() || checkedCancellation.isCancellationRequested()) {
                throw failure(FailureCode.CANCELLED, "request was cancelled", failure);
            }
            throw failure(FailureCode.TRANSPORT_FAILURE, "model transport failed", failure);
        } finally {
            activeCalls.remove(checkedRequest.getRequestId(), call);
            call.close();
        }
    }

    /** Cancels an active transport without assigning terminal task authority to the adapter. */
    public boolean cancel(String requestId) {
        Call call = activeCalls.get(requireIdentifier(requestId, "requestId"));
        if (call == null) {
            return false;
        }
        call.cancel();
        return true;
    }

    public int activeRequestCount() {
        return activeCalls.size();
    }

    private Result parseStream(
            DirectModelServiceContract.Endpoint endpoint,
            DirectModelServiceContract.Request request,
            InputStream rawInput,
            StreamObserver observer,
            CancellationSignal cancellationSignal,
            Call call) throws IOException {
        if (rawInput == null) {
            throw failure(FailureCode.EMPTY_RESPONSE, "response body is unavailable");
        }
        BufferedInputStream input = new BufferedInputStream(rawInput);
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        StreamBudget budget = new StreamBudget();
        boolean terminal = false;
        boolean streamingStarted = false;
        String doneReason = "";

        while (true) {
            checkCancellation(request, cancellationSignal, call);
            byte[] line = readLine(input, budget);
            if (line == null) {
                break;
            }
            if (line.length == 0) {
                continue;
            }
            if (terminal) {
                throw failure(
                        FailureCode.TRAILING_DATA_REJECTED,
                        "data followed the terminal response");
            }
            JsonObject envelope = parseObject(line);
            if (envelope.has("error")) {
                throw failure(FailureCode.MODEL_ERROR, "model service returned an error");
            }
            String responseModel = requiredString(envelope, "model");
            if (!endpoint.getModelName().equals(responseModel)) {
                throw failure(
                        FailureCode.MODEL_IDENTITY_REJECTED,
                        "response model identity does not match");
            }
            JsonObject message = requiredObject(envelope, "message");
            String delta = optionalString(message, "content");
            if (!delta.isEmpty()) {
                byte[] deltaBytes = delta.getBytes(StandardCharsets.UTF_8);
                if (content.size() + deltaBytes.length
                        > DirectModelServiceContract.MAX_RESPONSE_BYTES) {
                    throw failure(
                            FailureCode.RESPONSE_SIZE_REJECTED,
                            "model content exceeds the response bound");
                }
                content.write(deltaBytes);
                if (!streamingStarted) {
                    observer.onStage(DirectModelServiceContract.Stage.STREAMING);
                    streamingStarted = true;
                }
                observer.onTextDelta(delta);
            }
            terminal = requiredBoolean(envelope, "done");
            if (terminal && envelope.has("done_reason")) {
                doneReason = optionalString(envelope, "done_reason");
            }
        }

        if (!terminal) {
            throw failure(FailureCode.NON_TERMINAL_RESPONSE, "terminal response is missing");
        }
        if (content.size() == 0) {
            throw failure(FailureCode.EMPTY_RESPONSE, "model content is empty");
        }
        return new Result(
                endpoint.getModelName(),
                content.toByteArray(),
                doneReason,
                budget.totalBytes);
    }

    private static byte[] buildRequestBody(
            DirectModelServiceContract.Endpoint endpoint,
            Payload payload) {
        JsonObject root = new JsonObject();
        root.addProperty("model", endpoint.getModelName());
        root.addProperty("stream", true);
        root.addProperty("think", false);
        root.addProperty("keep_alive", "5m");

        JsonArray messages = new JsonArray();
        messages.add(message("system", payload.getSystemPrompt(), null));
        messages.add(message("user", payload.getUserText(), payload.getImageBytes()));
        root.add("messages", messages);
        root.add("format", parseSchema(payload.getResponseSchemaJson()));

        JsonObject options = new JsonObject();
        options.addProperty("temperature", 0);
        options.addProperty("num_predict", 512);
        root.add("options", options);

        byte[] encoded = root.toString().getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_HTTP_REQUEST_BYTES) {
            throw failure(
                    FailureCode.REQUEST_SIZE_REJECTED,
                    "model request exceeds the envelope bound");
        }
        return encoded;
    }

    private static JsonObject message(String role, String content, byte[] imageBytes) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        if (imageBytes != null) {
            JsonArray images = new JsonArray();
            images.add(Base64.getEncoder().encodeToString(imageBytes));
            message.add("images", images);
        }
        return message;
    }

    private static JsonObject parseSchema(String schemaJson) {
        try {
            JsonElement parsed = JsonParser.parseString(schemaJson);
            if (!parsed.isJsonObject()) {
                throw failure(
                        FailureCode.SCHEMA_REJECTED,
                        "response schema must be an object");
            }
            JsonObject schema = parsed.getAsJsonObject();
            if (!"object".equals(requiredString(schema, "type"))) {
                throw failure(
                        FailureCode.SCHEMA_REJECTED,
                        "response schema root type is invalid");
            }
            return schema;
        } catch (AdapterException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw failure(FailureCode.SCHEMA_REJECTED, "response schema is invalid", failure);
        }
    }

    private static void validateBinding(
            DirectModelServiceContract.Endpoint endpoint,
            DirectModelServiceContract.Request request,
            Payload payload) {
        if (endpoint.getWireProtocol()
                != DirectModelServiceContract.WireProtocol.OLLAMA_CHAT_V1) {
            throw failure(
                    FailureCode.PROTOCOL_REJECTED,
                    "endpoint protocol is not supported by this adapter");
        }
        if (!request.getTextDigest().equals(sha256(payload.userTextBytes))
                || !request.getContextDigest().equals(sha256(payload.systemPromptBytes))
                || !request.getResponseSchemaDigest().equals(
                        sha256(payload.responseSchemaBytes))) {
            throw failure(
                    FailureCode.REQUEST_BINDING_REJECTED,
                    "request digests do not bind the payload");
        }
        if (request.getModality() == DirectModelServiceContract.Modality.TEXT) {
            if (payload.imageBytes != null || request.getImage() != null) {
                throw failure(
                        FailureCode.MODALITY_REJECTED,
                        "text request cannot include an image");
            }
            return;
        }
        DirectModelServiceContract.ImageDescriptor descriptor = request.getImage();
        if (descriptor == null
                || payload.imageBytes == null
                || !descriptor.getMimeType().equals(payload.imageMimeType)
                || descriptor.getByteCount() != payload.imageBytes.length
                || !descriptor.getSha256().equals(sha256(payload.imageBytes))) {
            throw failure(
                    FailureCode.REQUEST_BINDING_REJECTED,
                    "image descriptor does not bind the payload");
        }
    }

    private int remainingDeadlineMs(DirectModelServiceContract.Request request) {
        long remaining = request.getDeadlineElapsedRealtimeMs() - clock.nowMs();
        if (remaining <= 0) {
            throw failure(FailureCode.DEADLINE_EXCEEDED, "request deadline has expired");
        }
        return (int) Math.min(Integer.MAX_VALUE, remaining);
    }

    private void checkCancellation(
            DirectModelServiceContract.Request request,
            CancellationSignal cancellationSignal,
            Call call) {
        if (cancellationSignal.isCancellationRequested()
                || (call != null && call.isCancelled())) {
            if (call != null) {
                call.cancel();
            }
            throw failure(FailureCode.CANCELLED, "request was cancelled");
        }
        if (cancellationSignal.isDeadlineExceeded()
                || request.getDeadlineElapsedRealtimeMs() <= clock.nowMs()) {
            if (call != null) {
                call.cancel();
            }
            throw failure(FailureCode.DEADLINE_EXCEEDED, "request deadline was exceeded");
        }
    }

    private static byte[] readLine(BufferedInputStream input, StreamBudget budget)
            throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        while (true) {
            int value = input.read();
            if (value == -1) {
                return line.size() == 0 ? null : line.toByteArray();
            }
            budget.totalBytes++;
            if (budget.totalBytes > DirectModelServiceContract.MAX_RESPONSE_BYTES) {
                throw failure(
                        FailureCode.RESPONSE_SIZE_REJECTED,
                        "wire response exceeds the response bound");
            }
            if (value == '\n') {
                byte[] result = line.toByteArray();
                if (result.length > 0 && result[result.length - 1] == '\r') {
                    byte[] trimmed = new byte[result.length - 1];
                    System.arraycopy(result, 0, trimmed, 0, trimmed.length);
                    return trimmed;
                }
                return result;
            }
            if (line.size() >= MAX_STREAM_LINE_BYTES) {
                throw failure(
                        FailureCode.RESPONSE_SIZE_REJECTED,
                        "stream line exceeds the response bound");
            }
            line.write(value);
        }
    }

    private static JsonObject parseObject(byte[] encoded) {
        try {
            JsonElement parsed = JsonParser.parseString(decodeUtf8(encoded));
            if (!parsed.isJsonObject()) {
                throw failure(
                        FailureCode.STREAM_PARSE_REJECTED,
                        "stream item must be an object");
            }
            return parsed.getAsJsonObject();
        } catch (AdapterException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw failure(
                    FailureCode.STREAM_PARSE_REJECTED,
                    "stream item is invalid",
                    failure);
        }
    }

    private static String decodeUtf8(byte[] value) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(value))
                    .toString();
        } catch (CharacterCodingException failure) {
            throw failure(
                    FailureCode.STREAM_PARSE_REJECTED,
                    "stream item is not valid UTF-8",
                    failure);
        }
    }

    private static String requiredString(JsonObject object, String field) {
        if (object == null
                || !object.has(field)
                || object.get(field).isJsonNull()
                || !object.get(field).isJsonPrimitive()
                || !object.get(field).getAsJsonPrimitive().isString()) {
            throw failure(
                    FailureCode.STREAM_PARSE_REJECTED,
                    "required string field is invalid");
        }
        String value = object.get(field).getAsString();
        if (value.isEmpty()) {
            throw failure(
                    FailureCode.STREAM_PARSE_REJECTED,
                    "required string field is empty");
        }
        return value;
    }

    private static String optionalString(JsonObject object, String field) {
        if (!object.has(field) || object.get(field).isJsonNull()) {
            return "";
        }
        JsonElement value = object.get(field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw failure(
                    FailureCode.STREAM_PARSE_REJECTED,
                    "optional string field is invalid");
        }
        return value.getAsString();
    }

    private static JsonObject requiredObject(JsonObject object, String field) {
        if (!object.has(field) || !object.get(field).isJsonObject()) {
            throw failure(
                    FailureCode.STREAM_PARSE_REJECTED,
                    "required object field is invalid");
        }
        return object.getAsJsonObject(field);
    }

    private static boolean requiredBoolean(JsonObject object, String field) {
        if (!object.has(field)
                || !object.get(field).isJsonPrimitive()
                || !object.get(field).getAsJsonPrimitive().isBoolean()) {
            throw failure(
                    FailureCode.STREAM_PARSE_REJECTED,
                    "required boolean field is invalid");
        }
        return object.get(field).getAsBoolean();
    }

    private static String sha256(byte[] value) {
        try {
            byte[] encoded = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder builder = new StringBuilder(encoded.length * 2);
            for (byte item : encoded) {
                builder.append(String.format("%02x", item & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String requireIdentifier(String value, String name) {
        if (value == null || !value.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static AdapterException failure(FailureCode code, String message) {
        return new AdapterException(code, message, null);
    }

    private static AdapterException failure(
            FailureCode code,
            String message,
            Throwable cause) {
        return new AdapterException(code, message, cause);
    }

    public enum FailureCode {
        CANCELLED,
        DEADLINE_EXCEEDED,
        DUPLICATE_REQUEST,
        EMPTY_RESPONSE,
        HTTP_STATUS_REJECTED,
        MODEL_ERROR,
        MODEL_IDENTITY_REJECTED,
        MODALITY_REJECTED,
        NON_TERMINAL_RESPONSE,
        PROTOCOL_REJECTED,
        REQUEST_BINDING_REJECTED,
        REQUEST_SIZE_REJECTED,
        RESPONSE_SIZE_REJECTED,
        SCHEMA_REJECTED,
        STREAM_PARSE_REJECTED,
        TRAILING_DATA_REJECTED,
        TRANSPORT_FAILURE
    }

    public static final class AdapterException extends IllegalStateException {
        private final FailureCode failureCode;

        AdapterException(FailureCode failureCode, String message, Throwable cause) {
            super(message, cause);
            this.failureCode = Objects.requireNonNull(failureCode, "failureCode");
        }

        public FailureCode getFailureCode() {
            return failureCode;
        }
    }

    public interface CancellationSignal {
        boolean isCancellationRequested();

        boolean isDeadlineExceeded();
    }

    public interface StreamObserver {
        void onStage(DirectModelServiceContract.Stage stage);

        void onTextDelta(String textDelta);
    }

    interface ElapsedClock {
        long nowMs();
    }

    interface Transport {
        Call open(HttpRequest request);
    }

    interface Call {
        HttpResponse execute() throws IOException;

        void cancel();

        boolean isCancelled();

        void close();
    }

    static final class HttpRequest {
        private final URI uri;
        private final byte[] body;
        private final int connectTimeoutMs;
        private final int readTimeoutMs;
        private final int maxResponseBytes;

        HttpRequest(
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

        URI getUri() {
            return uri;
        }

        byte[] getBody() {
            return body.clone();
        }

        int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        int getMaxResponseBytes() {
            return maxResponseBytes;
        }
    }

    static final class HttpResponse implements AutoCloseable {
        private final int statusCode;
        private final InputStream body;
        private final Runnable closer;
        private boolean closed;

        HttpResponse(int statusCode, InputStream body, Runnable closer) {
            this.statusCode = statusCode;
            this.body = body == null ? new ByteArrayInputStream(new byte[0]) : body;
            this.closer = Objects.requireNonNull(closer, "closer");
        }

        int getStatusCode() {
            return statusCode;
        }

        InputStream getBody() {
            return body;
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            try {
                body.close();
            } finally {
                closer.run();
            }
        }
    }

    public static final class Payload {
        private final String systemPrompt;
        private final byte[] systemPromptBytes;
        private final String userText;
        private final byte[] userTextBytes;
        private final String responseSchemaJson;
        private final byte[] responseSchemaBytes;
        private final String imageMimeType;
        private final byte[] imageBytes;

        private Payload(
                String systemPrompt,
                String userText,
                String responseSchemaJson,
                String imageMimeType,
                byte[] imageBytes) {
            this.systemPromptBytes = boundedUtf8(
                    systemPrompt,
                    1,
                    MAX_SYSTEM_PROMPT_BYTES,
                    "systemPrompt");
            this.systemPrompt = systemPrompt;
            this.userTextBytes = boundedUtf8(
                    userText,
                    1,
                    DirectModelServiceContract.MAX_TEXT_BYTES,
                    "userText");
            this.userText = userText;
            this.responseSchemaBytes = boundedUtf8(
                    responseSchemaJson,
                    2,
                    MAX_SCHEMA_BYTES,
                    "responseSchemaJson");
            this.responseSchemaJson = responseSchemaJson;
            parseSchema(responseSchemaJson);
            if ((imageMimeType == null) != (imageBytes == null)) {
                throw new IllegalArgumentException(
                        "image MIME and bytes must be provided together");
            }
            if (imageBytes != null
                    && (imageBytes.length < 1
                            || imageBytes.length
                                    > DirectModelServiceContract.MAX_IMAGE_BYTES)) {
                throw new IllegalArgumentException("image bytes are out of range");
            }
            this.imageMimeType = imageMimeType;
            this.imageBytes = imageBytes == null ? null : imageBytes.clone();
        }

        public static Payload text(
                String systemPrompt,
                String userText,
                String responseSchemaJson) {
            return new Payload(systemPrompt, userText, responseSchemaJson, null, null);
        }

        public static Payload textImage(
                String systemPrompt,
                String userText,
                String responseSchemaJson,
                String imageMimeType,
                byte[] imageBytes) {
            return new Payload(
                    systemPrompt,
                    userText,
                    responseSchemaJson,
                    imageMimeType,
                    imageBytes);
        }

        public String getSystemPrompt() {
            return systemPrompt;
        }

        public String getUserText() {
            return userText;
        }

        public String getResponseSchemaJson() {
            return responseSchemaJson;
        }

        public String getImageMimeType() {
            return imageMimeType;
        }

        public byte[] getImageBytes() {
            return imageBytes == null ? null : imageBytes.clone();
        }

        private static byte[] boundedUtf8(
                String value,
                int minimumBytes,
                int maximumBytes,
                String field) {
            if (value == null) {
                throw new IllegalArgumentException(field + " is required");
            }
            byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
            if (encoded.length < minimumBytes || encoded.length > maximumBytes) {
                throw new IllegalArgumentException(field + " is out of range");
            }
            return encoded;
        }
    }

    public static final class Result {
        private final String modelName;
        private final byte[] structuredContent;
        private final String doneReason;
        private final int wireBytes;

        Result(
                String modelName,
                byte[] structuredContent,
                String doneReason,
                int wireBytes) {
            this.modelName = Objects.requireNonNull(modelName, "modelName");
            this.structuredContent =
                    Objects.requireNonNull(structuredContent, "structuredContent").clone();
            this.doneReason = Objects.requireNonNull(doneReason, "doneReason");
            this.wireBytes = wireBytes;
        }

        public String getModelName() {
            return modelName;
        }

        public byte[] getStructuredContent() {
            return structuredContent.clone();
        }

        public String getDoneReason() {
            return doneReason;
        }

        public int getWireBytes() {
            return wireBytes;
        }

        public boolean grantsToolAuthority() {
            return false;
        }

        public boolean grantsEffectAuthority() {
            return false;
        }
    }

    private static final class StreamBudget {
        int totalBytes;
    }

    private static final class UrlConnectionTransport implements Transport {
        @Override
        public Call open(HttpRequest request) {
            return new UrlConnectionCall(request);
        }
    }

    private static final class UrlConnectionCall implements Call {
        private final HttpRequest request;
        private volatile HttpURLConnection connection;
        private volatile boolean cancelled;

        UrlConnectionCall(HttpRequest request) {
            this.request = request;
        }

        @Override
        public HttpResponse execute() throws IOException {
            if (cancelled) {
                throw new IOException("request was cancelled");
            }
            HttpURLConnection opened =
                    (HttpURLConnection) request.getUri().toURL().openConnection();
            connection = opened;
            opened.setRequestMethod("POST");
            opened.setInstanceFollowRedirects(false);
            opened.setDoOutput(true);
            opened.setConnectTimeout(request.getConnectTimeoutMs());
            opened.setReadTimeout(request.getReadTimeoutMs());
            opened.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=utf-8");
            opened.setRequestProperty(
                    "Accept",
                    "application/x-ndjson, application/json");
            opened.setFixedLengthStreamingMode(request.body.length);
            if (cancelled) {
                opened.disconnect();
                throw new IOException("request was cancelled");
            }
            try (OutputStream output = opened.getOutputStream()) {
                output.write(request.body);
            }
            int statusCode = opened.getResponseCode();
            InputStream body = statusCode >= 200 && statusCode < 400
                    ? opened.getInputStream()
                    : opened.getErrorStream();
            return new HttpResponse(statusCode, body, opened::disconnect);
        }

        @Override
        public void cancel() {
            cancelled = true;
            HttpURLConnection current = connection;
            if (current != null) {
                current.disconnect();
            }
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void close() {
            HttpURLConnection current = connection;
            if (current != null) {
                current.disconnect();
            }
        }
    }
}
