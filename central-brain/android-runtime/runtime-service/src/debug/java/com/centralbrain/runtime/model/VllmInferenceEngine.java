package com.centralbrain.runtime.model;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

/** Debug-only OpenAI-compatible HTTP engine backed by the fixed TY1100 vLLM service. */
public final class VllmInferenceEngine implements LocalModelProvider.LocalInferenceEngine {
    private static final String TAG = "CentralBrainVllm";
    private static final int MAX_PENDING_PROMPTS = 16;
    private static final int MAX_TEXT_REQUEST_BYTES = 16_384;
    private static final int MAX_MULTIMODAL_REQUEST_BYTES = 8_500_000;
    static final int MAX_IMAGE_BYTES = 6 * 1024 * 1024;
    private static final int MAX_PENDING_IMAGE_BYTES = 12 * 1024 * 1024;
    private static final int MAX_REPLY_CHARS = 256;
    private static final int MAX_ACTIONS = 4;
    private static final int DEFAULT_MAX_OUTPUT_TOKENS = 192;
    static final int SMOKING_FAST_MAX_OUTPUT_TOKENS = 24;
    static final int SMOKING_FALLBACK_MAX_OUTPUT_TOKENS = 64;
    static final int SMOKING_FAST_IMAGE_WIDTH = 1_280;
    static final int SMOKING_FAST_IMAGE_HEIGHT = 720;
    static final int SMOKING_FAST_JPEG_QUALITY = 85;
    static final double SMOKING_FAST_ACCEPT_CONFIDENCE = 0.80;
    private static final int MAX_DECODED_IMAGE_DIMENSION = 8_192;
    private static final long MAX_DECODED_IMAGE_PIXELS = 32L * 1024L * 1024L;

    private enum RequestProfile {
        STANDARD,
        SMOKING_FAST,
        SMOKING_FALLBACK
    }

    interface Transport {
        Response execute(Request request);
    }

    interface ElapsedClock {
        long nowMs();
    }

    interface ImagePreprocessor {
        byte[] prepareFastJpeg(
                byte[] source,
                String mimeType,
                int maximumWidth,
                int maximumHeight,
                int jpegQuality);
    }

    private static final class AndroidImagePreprocessor implements ImagePreprocessor {
        @Override
        public byte[] prepareFastJpeg(
                byte[] source,
                String mimeType,
                int maximumWidth,
                int maximumHeight,
                int jpegQuality) {
            requireImageSignature(mimeType, source);
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(source, 0, source.length, bounds);
            int sourceWidth = bounds.outWidth;
            int sourceHeight = bounds.outHeight;
            if (sourceWidth <= 0
                    || sourceHeight <= 0
                    || sourceWidth > MAX_DECODED_IMAGE_DIMENSION
                    || sourceHeight > MAX_DECODED_IMAGE_DIMENSION
                    || (long) sourceWidth * sourceHeight > MAX_DECODED_IMAGE_PIXELS) {
                throw new IllegalArgumentException(
                        "Vllm decoded image dimensions are invalid");
            }

            BitmapFactory.Options decode = new BitmapFactory.Options();
            decode.inPreferredConfig = Bitmap.Config.ARGB_8888;
            decode.inSampleSize = sampleSize(
                    sourceWidth, sourceHeight, maximumWidth, maximumHeight);
            Bitmap decoded = BitmapFactory.decodeByteArray(
                    source, 0, source.length, decode);
            if (decoded == null) {
                throw new IllegalArgumentException("Vllm image decode failed");
            }
            Bitmap scaled = decoded;
            try {
                double scale = Math.min(
                        1.0,
                        Math.min(
                                maximumWidth / (double) decoded.getWidth(),
                                maximumHeight / (double) decoded.getHeight()));
                int targetWidth = Math.max(1, (int) Math.round(decoded.getWidth() * scale));
                int targetHeight = Math.max(1, (int) Math.round(decoded.getHeight() * scale));
                if (targetWidth != decoded.getWidth() || targetHeight != decoded.getHeight()) {
                    scaled = Bitmap.createScaledBitmap(
                            decoded, targetWidth, targetHeight, true);
                }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality, output)) {
                    throw new IllegalStateException("Vllm fast image encode failed");
                }
                byte[] encoded = output.toByteArray();
                if (encoded.length == 0 || encoded.length > MAX_IMAGE_BYTES) {
                    Arrays.fill(encoded, (byte) 0);
                    throw new IllegalStateException("Vllm fast image size is invalid");
                }
                requireImageSignature("image/jpeg", encoded);
                return encoded;
            } finally {
                if (scaled != decoded) {
                    scaled.recycle();
                }
                decoded.recycle();
            }
        }

        private static int sampleSize(
                int width, int height, int maximumWidth, int maximumHeight) {
            int sample = 1;
            while (width / (sample * 2) >= maximumWidth
                    && height / (sample * 2) >= maximumHeight) {
                sample *= 2;
            }
            return sample;
        }
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
                throw new IllegalArgumentException("Vllm image MIME is not allowlisted");
            }
            if (fileName == null || !fileName.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,95}")) {
                throw new IllegalArgumentException("Vllm image filename is invalid");
            }
            if (content == null || content.length == 0 || content.length > MAX_IMAGE_BYTES) {
                throw new IllegalArgumentException("Vllm image size is invalid");
            }
            requireImageSignature(mimeType, content);
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

    private final VllmEndpointConfig endpoint;
    private final Transport transport;
    private final ElapsedClock clock;
    private final ImagePreprocessor imagePreprocessor;
    private final Map<String, CockpitModelPrompt> pending = new LinkedHashMap<>();
    private final Map<String, ImageAttachment> pendingImages = new LinkedHashMap<>();
    private int pendingImageBytes;
    private ModelProvider.ModelSpec warmedModel;
    private boolean closed;
    private long invocationCount;
    private long completedCount;
    private long failureCount;
    private long lastLatencyMs;

    public VllmInferenceEngine(VllmEndpointConfig endpoint) {
        this(
                endpoint,
                new UrlConnectionTransport(),
                SystemClock::elapsedRealtime,
                new AndroidImagePreprocessor());
    }

    VllmInferenceEngine(VllmEndpointConfig endpoint, Transport transport) {
        this(
                endpoint,
                transport,
                SystemClock::elapsedRealtime,
                new AndroidImagePreprocessor());
    }

    VllmInferenceEngine(
            VllmEndpointConfig endpoint,
            Transport transport,
            ElapsedClock clock) {
        this(endpoint, transport, clock, new AndroidImagePreprocessor());
    }

    VllmInferenceEngine(
            VllmEndpointConfig endpoint,
            Transport transport,
            ElapsedClock clock,
            ImagePreprocessor imagePreprocessor) {
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        if (endpoint.getProfile()
                != VllmEndpointConfig.Profile.TY1100_ETHERNET_VIA_ADB_REVERSE) {
            throw new IllegalArgumentException(
                    "debug vLLM engine accepts only the TY1100 prototype profile");
        }
        this.transport = Objects.requireNonNull(transport, "transport");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.imagePreprocessor = Objects.requireNonNull(
                imagePreprocessor, "imagePreprocessor");
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
                throw new IllegalArgumentException("Vllm input digest prompt conflict");
            }
            return;
        }
        if (pending.size() >= MAX_PENDING_PROMPTS) {
            throw new IllegalStateException("Vllm prompt registry capacity exhausted");
        }
        pending.put(inputDigest, prompt);
    }

    /** Registers one digest-bound image for an OpenAI-compatible image_url content item. */
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
                throw new IllegalArgumentException("Vllm input digest image conflict");
            }
            attachment.clear();
            return;
        }
        if (pendingImages.size() >= MAX_PENDING_PROMPTS
                || pendingImageBytes + attachment.content.length > MAX_PENDING_IMAGE_BYTES) {
            attachment.clear();
            throw new IllegalStateException("Vllm image registry capacity exhausted");
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
                throw new IllegalStateException("Vllm model is not warmed");
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
                throw new IllegalArgumentException("Vllm prompt material is unavailable");
            }
            invocationCount++;
        }
        long startedAt = clock.nowMs();
        boolean networkAccessed = false;
        boolean fallbackUsed = false;
        int passCount = 1;
        int fastImageBytes = 0;
        try {
            if (cancellationSignal.isCancellationRequested()
                    || cancellationSignal.isDeadlineExceeded()) {
                throw new IllegalStateException(
                        "Vllm request was cancelled before transport");
            }
            if (prompt.getOutputContract()
                            == CockpitModelPrompt.OutputContract.SMOKING_DETECTION_V1
                    && imageAttachment == null) {
                throw new IllegalStateException(
                        "Vllm smoking detection requires an image attachment");
            }
            byte[] canonical;
            if (prompt.getOutputContract()
                    == CockpitModelPrompt.OutputContract.SMOKING_DETECTION_V1) {
                byte[] fastImage = imagePreprocessor.prepareFastJpeg(
                        imageAttachment.content,
                        imageAttachment.mimeType,
                        SMOKING_FAST_IMAGE_WIDTH,
                        SMOKING_FAST_IMAGE_HEIGHT,
                        SMOKING_FAST_JPEG_QUALITY);
                fastImageBytes = fastImage.length;
                try {
                    networkAccessed = true;
                    Response fastResponse = executeRequest(
                            buildRequest(
                                    prompt,
                                    "image/jpeg",
                                    fastImage,
                                    RequestProfile.SMOKING_FAST),
                            request,
                            cancellationSignal);
                    SmokingDetectionResult fastResult =
                            parseSmokingCompactResponse(fastResponse);
                    if (requiresSmokingFallback(fastResult)) {
                        fallbackUsed = true;
                        passCount = 2;
                        Response fallbackResponse = executeRequest(
                                buildRequest(
                                        prompt,
                                        imageAttachment.mimeType,
                                        imageAttachment.content,
                                        RequestProfile.SMOKING_FALLBACK),
                                request,
                                cancellationSignal);
                        canonical = parseAndValidate(
                                fallbackResponse,
                                prompt,
                                RequestProfile.SMOKING_FALLBACK);
                    } else {
                        canonical = smokingProjection(prompt, fastResult);
                    }
                } finally {
                    Arrays.fill(fastImage, (byte) 0);
                }
            } else {
                networkAccessed = true;
                Response response = executeRequest(
                        buildRequest(prompt, null, null, RequestProfile.STANDARD),
                        request,
                        cancellationSignal);
                canonical = parseAndValidate(
                        response, prompt, RequestProfile.STANDARD);
            }
            long latencyMs = Math.max(0L, clock.nowMs() - startedAt);
            synchronized (this) {
                completedCount++;
                lastLatencyMs = latencyMs;
            }
            logInfo("vllm_inference_completed=true"
                    + " endpoint_profile=ty1100_ethernet_via_adb_reverse"
                    + " model=" + safeToken(endpoint.getModelName())
                    + " image_present=" + (imageAttachment != null)
                    + " image_bytes="
                    + (imageAttachment == null ? 0 : imageAttachment.content.length)
                    + " image_sha256="
                    + (imageAttachment == null ? "none" : imageAttachment.sha256)
                    + " thinking_enabled=false"
                    + " pass_count=" + passCount
                    + " fallback_used=" + fallbackUsed
                    + " fast_image_bytes=" + fastImageBytes
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
            logError("vllm_inference_completed=false"
                    + " endpoint_profile=ty1100_ethernet_via_adb_reverse"
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

    private Response executeRequest(
            byte[] requestBody,
            ModelProvider.InferenceRequest request,
            LocalModelProvider.CancellationSignal cancellationSignal) {
        if (cancellationSignal.isCancellationRequested()
                || cancellationSignal.isDeadlineExceeded()) {
            throw new IllegalStateException("Vllm request was cancelled before transport");
        }
        int remainingMs = remainingDeadlineMs(request);
        Response response = transport.execute(new Request(
                endpoint.getChatCompletionsUri(),
                requestBody,
                Math.min(endpoint.getConnectTimeoutMs(), remainingMs),
                Math.min(endpoint.getReadTimeoutMs(), remainingMs),
                VllmEndpointConfig.MAX_RESPONSE_BYTES));
        if (cancellationSignal.isCancellationRequested()
                || cancellationSignal.isDeadlineExceeded()) {
            throw new IllegalStateException("Vllm request crossed its deadline");
        }
        return response;
    }

    private static boolean requiresSmokingFallback(SmokingDetectionResult result) {
        return result.getStatus() != SmokingDetectionResult.DecisionStatus.DETECTED
                || result.getConfidence() < SMOKING_FAST_ACCEPT_CONFIDENCE;
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
            throw new IllegalStateException("Vllm request deadline has expired");
        }
        return (int) Math.min(Integer.MAX_VALUE, remaining);
    }

    private byte[] buildRequest(
            CockpitModelPrompt prompt,
            String imageMimeType,
            byte[] imageContent,
            RequestProfile profile) {
        boolean smoking = prompt.getOutputContract()
                == CockpitModelPrompt.OutputContract.SMOKING_DETECTION_V1;
        if ((smoking && profile == RequestProfile.STANDARD)
                || (!smoking && profile != RequestProfile.STANDARD)
                || (smoking && imageContent == null)
                || (imageContent == null) != (imageMimeType == null)) {
            throw new IllegalArgumentException("Vllm request profile is inconsistent");
        }
        JsonObject root = new JsonObject();
        root.addProperty("model", endpoint.getModelName());
        root.addProperty("stream", false);
        root.addProperty("temperature", 0);
        int maximumOutputTokens = DEFAULT_MAX_OUTPUT_TOKENS;
        if (profile == RequestProfile.SMOKING_FAST) {
            maximumOutputTokens = SMOKING_FAST_MAX_OUTPUT_TOKENS;
        } else if (profile == RequestProfile.SMOKING_FALLBACK) {
            maximumOutputTokens = SMOKING_FALLBACK_MAX_OUTPUT_TOKENS;
        }
        root.addProperty("max_tokens", maximumOutputTokens);
        JsonObject chatTemplateKwargs = new JsonObject();
        chatTemplateKwargs.addProperty("enable_thinking", false);
        root.add("chat_template_kwargs", chatTemplateKwargs);

        JsonArray messages = new JsonArray();
        if (smoking) {
            messages.add(message(
                    "system",
                    profile == RequestProfile.SMOKING_FAST
                            ? smokingFastSystemInstruction(prompt)
                            : prompt.systemInstruction()));
            JsonObject user = new JsonObject();
            user.addProperty("role", "user");
            JsonArray content = new JsonArray();
            JsonObject text = new JsonObject();
            text.addProperty("type", "text");
            text.addProperty(
                    "text",
                    profile == RequestProfile.SMOKING_FAST
                            ? smokingFastUserInstruction(prompt)
                            : prompt.userInstruction());
            content.add(text);
            JsonObject image = new JsonObject();
            image.addProperty("type", "image_url");
            JsonObject imageUrl = new JsonObject();
            imageUrl.addProperty(
                    "url",
                    "data:" + imageMimeType + ";base64,"
                            + Base64.getEncoder().encodeToString(imageContent));
            imageUrl.addProperty("detail", "auto");
            image.add("image_url", imageUrl);
            content.add(image);
            user.add("content", content);
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
        JsonObject responseFormat = new JsonObject();
        responseFormat.addProperty("type", "json_schema");
        JsonObject jsonSchema = new JsonObject();
        jsonSchema.addProperty(
                "name",
                profile == RequestProfile.SMOKING_FAST
                        ? "central_brain_smoking_wire_v2"
                        : smoking
                                ? "central_brain_smoking_detection_v1"
                                : "central_brain_scenario_result_v1");
        jsonSchema.addProperty("strict", true);
        jsonSchema.add("schema", responseSchema(prompt, profile));
        responseFormat.add("json_schema", jsonSchema);
        root.add("response_format", responseFormat);
        byte[] encoded = root.toString().getBytes(StandardCharsets.UTF_8);
        int maximumRequestBytes = imageContent == null
                ? MAX_TEXT_REQUEST_BYTES : MAX_MULTIMODAL_REQUEST_BYTES;
        if (encoded.length > maximumRequestBytes) {
            throw new IllegalStateException("Vllm request exceeds the bounded envelope");
        }
        return encoded;
    }

    private static String smokingFastSystemInstruction(CockpitModelPrompt prompt) {
        String instruction = prompt.systemInstruction();
        int outputSection = instruction.indexOf("## 输出字段");
        if (outputSection <= 0) {
            throw new IllegalStateException(
                    "Vllm smoking Agent output section is unavailable");
        }
        return instruction.substring(0, outputSection).trim()
                + "\n运行时补充约束：如果输入图像不符合标准后排摄像头几何，"
                + "仍可判断直接可见的客观吸烟事实，但座位无法可靠确定时必须使用UNKNOWN。"
                + "你没有工具、车辆执行或业务处置权限。"
                + "\n## Provider内部传输格式\n"
                + "只输出四元素紧凑JSON数组[s,n,l,c]，不得输出其他文字："
                + "s为0未吸烟、1吸烟、2不确定；n为吸烟人数0..2；"
                + "l为0 UNKNOWN、1 IMAGE_ROW_2_LEFT、2 IMAGE_ROW_2_RIGHT、"
                + "3 IMAGE_ROW_1_LEFT、4 IMAGE_ROW_1_RIGHT；"
                + "c为整数置信度百分比0..100。"
                + "s=2时必须输出[2,0,0,c]且c<50。";
    }

    private static String smokingFastUserInstruction(CockpitModelPrompt prompt) {
        String instruction = prompt.userInstruction();
        int outputLine = instruction.lastIndexOf("\n请只返回");
        if (outputLine <= 0) {
            throw new IllegalStateException(
                    "Vllm smoking Agent user output line is unavailable");
        }
        return instruction.substring(0, outputLine)
                + "\n只返回四元素JSON数组。";
    }

    private SmokingDetectionResult parseSmokingCompactResponse(Response response) {
        try {
            return SmokingDetectionResult.parseCompactWire(
                    requireAssistantContent(response));
        } catch (RuntimeException failure) {
            throw new IllegalStateException(
                    "Vllm response violates the structured contract", failure);
        }
    }

    private byte[] parseAndValidate(
            Response response,
            CockpitModelPrompt prompt,
            RequestProfile profile) {
        if (profile == RequestProfile.SMOKING_FAST) {
            throw new IllegalArgumentException(
                    "Vllm compact response requires the dedicated parser");
        }
        String rawContent = requireAssistantContent(response);
        try {
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
                throw new IllegalStateException("Vllm content shape is not exact");
            }
            if (!prompt.getScenarioId().equals(requiredString(content, "scenario_id"))) {
                throw new IllegalStateException("Vllm scenario binding does not match");
            }
            String reply = requiredString(content, "reply").trim();
            if (reply.isEmpty() || reply.length() > MAX_REPLY_CHARS) {
                throw new IllegalStateException("Vllm reply is outside the bounded contract");
            }
            for (int index = 0; index < reply.length(); index++) {
                if (Character.isISOControl(reply.charAt(index))) {
                    throw new IllegalStateException("Vllm reply contains control characters");
                }
            }
            JsonArray actions = content.getAsJsonArray("actions");
            if (actions == null || actions.isEmpty() || actions.size() > MAX_ACTIONS) {
                throw new IllegalStateException("Vllm action count is invalid");
            }
            List<String> admitted = new ArrayList<>();
            for (JsonElement element : actions) {
                String action = element.getAsString();
                if (!prompt.getAllowedActions().contains(action) || admitted.contains(action)) {
                    throw new IllegalStateException("Vllm returned an untrusted action");
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
            throw new IllegalStateException("Vllm response violates the structured contract", failure);
        }
    }

    private String requireAssistantContent(Response response) {
        if (response.statusCode < 200 || response.statusCode >= 300) {
            throw new IllegalStateException("Vllm returned HTTP " + response.statusCode);
        }
        if (response.body.length == 0
                || response.body.length > VllmEndpointConfig.MAX_RESPONSE_BYTES) {
            throw new IllegalStateException("Vllm response size is invalid");
        }
        try {
            JsonObject envelope = JsonParser.parseString(
                    decodeUtf8(response.body)).getAsJsonObject();
            if (!requiredString(envelope, "model").equals(endpoint.getModelName())) {
                throw new IllegalStateException("Vllm response model does not match");
            }
            JsonArray choices = envelope.getAsJsonArray("choices");
            if (choices == null || choices.size() != 1) {
                throw new IllegalStateException("Vllm response choice count is invalid");
            }
            JsonObject choice = choices.get(0).getAsJsonObject();
            if (!"stop".equals(requiredString(choice, "finish_reason"))) {
                throw new IllegalStateException("Vllm response is not terminal");
            }
            JsonObject message = choice.getAsJsonObject("message");
            if (!"assistant".equals(requiredString(message, "role"))) {
                throw new IllegalStateException("Vllm response role is invalid");
            }
            return requiredString(message, "content");
        } catch (RuntimeException failure) {
            throw new IllegalStateException("Vllm response violates the structured contract", failure);
        }
    }

    private static JsonObject responseSchema(
            CockpitModelPrompt prompt,
            RequestProfile profile) {
        if (profile == RequestProfile.SMOKING_FAST) {
            return smokingCompactResponseSchema();
        }
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
        // This vLLM/xgrammar build rejects uniqueItems; duplicate actions are rejected locally.
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

    private static JsonObject smokingCompactResponseSchema() {
        JsonObject schema = new JsonObject();
        JsonArray branches = new JsonArray();
        branches.add(smokingCompactResponseBranch(0, 0, 0, 0, 0, 50, 100));
        branches.add(smokingCompactResponseBranch(1, 1, 2, 1, 4, 50, 100));
        branches.add(smokingCompactResponseBranch(2, 0, 0, 0, 0, 0, 49));
        schema.add("oneOf", branches);
        return schema;
    }

    private static JsonObject smokingCompactResponseBranch(
            int status,
            int countMinimum,
            int countMaximum,
            int locationMinimum,
            int locationMaximum,
            int confidenceMinimum,
            int confidenceMaximum) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "array");
        JsonArray prefixItems = new JsonArray();
        JsonObject statusSchema = new JsonObject();
        statusSchema.addProperty("const", status);
        prefixItems.add(statusSchema);
        prefixItems.add(integerSchema(countMinimum, countMaximum));
        prefixItems.add(integerSchema(locationMinimum, locationMaximum));
        prefixItems.add(integerSchema(confidenceMinimum, confidenceMaximum));
        schema.add("prefixItems", prefixItems);
        schema.addProperty("minItems", 4);
        schema.addProperty("maxItems", 4);
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
        JsonArray descriptions = new JsonArray();
        descriptions.add("检测到吸烟行为。");
        descriptions.add("未检测到吸烟行为。");
        descriptions.add("uncertain: 图像不足以可靠判断。");
        description.add("enum", descriptions);
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
            throw new IllegalStateException("Vllm field is missing: " + field);
        }
        String value = object.get(field).getAsString();
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Vllm field is empty: " + field);
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
            throw new IllegalStateException("Vllm response is not valid UTF-8", failure);
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
            throw new IllegalArgumentException("vLLM image signature does not match MIME");
        }
    }

    private synchronized void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Vllm engine is closed");
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
                if (message.startsWith("Vllm transport failed")) {
                    transportFailure = true;
                }
                if (message.startsWith("Vllm returned HTTP")) {
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
                        || message.startsWith("Vllm field is")
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
                throw new IllegalStateException("Vllm transport failed", failure);
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
                        throw new IOException("Vllm response exceeds maximum bytes");
                    }
                    output.write(buffer, 0, count);
                }
                return output.toByteArray();
            }
        }
    }
}
