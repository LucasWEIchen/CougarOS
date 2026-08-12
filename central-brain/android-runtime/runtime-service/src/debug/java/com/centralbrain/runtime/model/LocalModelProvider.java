package com.centralbrain.runtime.model;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

/** Debug-build provider lifecycle used by bounded development and target-integration engines. */
public final class LocalModelProvider implements ModelProvider {
    public static final int ABSOLUTE_MAX_STREAM_CHUNKS = 32;
    public static final int ABSOLUTE_MAX_TOTAL_BYTES = 262_144;
    private static final int MAX_TERMINAL_HISTORY = 64;
    private static final String EMPTY_DIGEST = sha256(new byte[0]);

    public interface ElapsedRealtimeClock {
        long now();
    }

    public interface CancellationSignal {
        boolean isCancellationRequested();

        boolean isDeadlineExceeded();
    }

    @FunctionalInterface
    public interface LocalInferenceEngine extends AutoCloseable {
        EngineOutput infer(
                ModelSpec modelSpec,
                InferenceRequest request,
                CancellationSignal cancellationSignal);

        default void warmup(ModelSpec modelSpec) {
        }

        @Override
        default void close() {
        }
    }

    public static final class StreamLimits {
        private final int maxChunks;
        private final int maxChunkBytes;
        private final int maxTotalBytes;

        public StreamLimits(int maxChunks, int maxChunkBytes, int maxTotalBytes) {
            if (maxChunks < 1 || maxChunks > ABSOLUTE_MAX_STREAM_CHUNKS) {
                throw new IllegalArgumentException("maxChunks is out of range");
            }
            if (maxChunkBytes < 1 || maxChunkBytes > MAX_STREAM_CHUNK_BYTES) {
                throw new IllegalArgumentException("maxChunkBytes is out of range");
            }
            if (maxTotalBytes < maxChunkBytes
                    || maxTotalBytes > ABSOLUTE_MAX_TOTAL_BYTES) {
                throw new IllegalArgumentException("maxTotalBytes is out of range");
            }
            this.maxChunks = maxChunks;
            this.maxChunkBytes = maxChunkBytes;
            this.maxTotalBytes = maxTotalBytes;
        }

        public int getMaxChunks() {
            return maxChunks;
        }

        public int getMaxChunkBytes() {
            return maxChunkBytes;
        }

        public int getMaxTotalBytes() {
            return maxTotalBytes;
        }

        public static StreamLimits defaults() {
            return new StreamLimits(8, 16_384, 65_536);
        }
    }

    public static final class EngineOutput {
        private final List<byte[]> chunks;

        public EngineOutput(List<byte[]> chunks) {
            if (chunks == null || chunks.isEmpty()
                    || chunks.size() > ABSOLUTE_MAX_STREAM_CHUNKS) {
                throw new IllegalArgumentException("engine chunk count is invalid");
            }
            List<byte[]> copies = new ArrayList<>();
            int totalBytes = 0;
            for (byte[] chunk : chunks) {
                if (chunk == null || chunk.length == 0
                        || chunk.length > MAX_STREAM_CHUNK_BYTES) {
                    throw new IllegalArgumentException("engine chunk size is invalid");
                }
                totalBytes = Math.addExact(totalBytes, chunk.length);
                if (totalBytes > ABSOLUTE_MAX_TOTAL_BYTES) {
                    throw new IllegalArgumentException("engine output is too large");
                }
                copies.add(Arrays.copyOf(chunk, chunk.length));
            }
            this.chunks = Collections.unmodifiableList(copies);
        }

        public static EngineOutput of(byte[]... chunks) {
            if (chunks == null) {
                throw new IllegalArgumentException("chunks must not be null");
            }
            return new EngineOutput(Arrays.asList(chunks));
        }

        public List<byte[]> getChunks() {
            List<byte[]> copies = new ArrayList<>();
            for (byte[] chunk : chunks) {
                copies.add(Arrays.copyOf(chunk, chunk.length));
            }
            return Collections.unmodifiableList(copies);
        }
    }

    private final Descriptor descriptor;
    private final ModelSpec allowedModel;
    private final LocalInferenceEngine engine;
    private final Executor executor;
    private final ElapsedRealtimeClock clock;
    private final StreamLimits limits;
    private final Map<String, ActiveRecord> active = new LinkedHashMap<>();
    private final LinkedHashMap<String, TerminalResult> terminalHistory =
            new LinkedHashMap<>();

    private LifecycleState lifecycleState = LifecycleState.COLD;
    private HealthState healthState = HealthState.HEALTHY;
    private FaultSnapshot lastFault;
    private boolean modelLoaded;
    private boolean closed;
    private long acceptedCount;
    private long completedCount;
    private long cancelledCount;
    private long failureCount;

    private LocalModelProvider(
            ModelProviderProfiles.Profile profile,
            ModelSpec allowedModel,
            LocalInferenceEngine engine,
            Executor executor,
            ElapsedRealtimeClock clock,
            StreamLimits limits) {
        this.descriptor = Objects.requireNonNull(profile, "profile").getDescriptor();
        this.allowedModel = Objects.requireNonNull(allowedModel, "allowedModel");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.lastFault = new FaultSnapshot(descriptor.getProviderId(), "NONE", false, false);
        boolean developmentProfile = descriptor.getBackendKind()
                        == BackendKind.VLLM_OPENAI_COMPATIBLE
                && descriptor.getAssurance() == Assurance.DEBUG_ONLY;
        boolean targetIntegrationProfile = descriptor.getBackendKind()
                        == BackendKind.OPENCLAW_GATEWAY
                && descriptor.getAssurance() == Assurance.TARGET_INTEGRATION;
        if ((!developmentProfile && !targetIntegrationProfile)
                || descriptor.getFallbackClass() != FallbackClass.NEVER
                || descriptor.isHardwareBacked()
                || descriptor.isProductionEligible()) {
            throw new IllegalStateException(
                    "network provider profile must remain non-production and non-hardware");
        }
    }

    public static LocalModelProvider createForDevelopment(
            ModelSpec allowedModel,
            LocalInferenceEngine engine,
            Executor executor,
            ElapsedRealtimeClock clock,
            StreamLimits limits) {
        return new LocalModelProvider(
                ModelProviderProfiles.androidLocalDevelopment(),
                allowedModel,
                engine,
                executor,
                clock,
                limits);
    }

    public static LocalModelProvider createForTargetOpenClawIntegration(
            ModelSpec allowedModel,
            LocalInferenceEngine engine,
            Executor executor,
            ElapsedRealtimeClock clock,
            StreamLimits limits) {
        return new LocalModelProvider(
                ModelProviderProfiles.targetOpenClawTransitional(),
                allowedModel,
                engine,
                executor,
                clock,
                limits);
    }

    @Override
    public Descriptor descriptor() {
        return descriptor;
    }

    @Override
    public synchronized Snapshot snapshot() {
        return snapshotLocked();
    }

    @Override
    public Snapshot warmup(ModelSpec modelSpec) {
        synchronized (this) {
            requireOpenLocked();
            requireAllowedModel(modelSpec);
            if (!active.isEmpty()) {
                throw new ProviderBusyException("local provider has active work");
            }
            lifecycleState = LifecycleState.WARMING;
        }
        try {
            engine.warmup(modelSpec);
        } catch (RuntimeException exception) {
            synchronized (this) {
                lifecycleState = LifecycleState.DEGRADED;
                healthState = HealthState.DEGRADED;
                lastFault = new FaultSnapshot(
                        descriptor.getProviderId(),
                        "LOCAL_WARMUP_FAILURE",
                        true,
                        false);
            }
            throw new ProviderUnavailableException("local engine warmup failed", exception);
        }
        synchronized (this) {
            requireOpenLocked();
            modelLoaded = true;
            lifecycleState = LifecycleState.READY;
            healthState = HealthState.HEALTHY;
            return snapshotLocked();
        }
    }

    @Override
    public InferenceHandle infer(InferenceRequest request, StreamObserver observer) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(observer, "observer");
        synchronized (this) {
            requireOpenLocked();
            if (!modelLoaded
                    || lifecycleState != LifecycleState.READY
                    || healthState != HealthState.HEALTHY) {
                throw new ProviderUnavailableException("local provider is not ready");
            }
            if (!allowedModel.getModelId().equals(request.getModelId())) {
                throw new IllegalArgumentException("request model is not warmed");
            }
            if (request.getDeadlineElapsedRealtimeMs() <= clock.now()) {
                throw new DeadlineExceededException("inference deadline has expired");
            }
            if (active.containsKey(request.getRequestId())
                    || terminalHistory.containsKey(request.getRequestId())) {
                throw new DuplicateRequestException("request ID already exists");
            }
            if (active.size() >= descriptor.getMaxConcurrentRequests()) {
                throw new ProviderBusyException("local provider slot is occupied");
            }
            active.put(request.getRequestId(), new ActiveRecord(request, observer));
            acceptedCount++;
        }
        try {
            executor.execute(() -> runInference(request.getRequestId()));
        } catch (RuntimeException exception) {
            Completion completion;
            synchronized (this) {
                ActiveRecord record = active.get(request.getRequestId());
                completion = record == null ? null : failLocked(
                        record,
                        TerminalState.RETRYABLE_FAILURE,
                        "LOCAL_EXECUTOR_REJECTED",
                        true);
            }
            deliverTerminal(completion);
            throw new ProviderUnavailableException("local executor rejected request", exception);
        }
        return new InferenceHandle(descriptor.getProviderId(), request.getRequestId());
    }

    @Override
    public synchronized CancelState cancel(String requestId, String reason) {
        requireRequestId(requestId);
        requireReason(reason);
        ActiveRecord record = active.get(requestId);
        if (record == null) {
            return terminalHistory.containsKey(requestId)
                    ? CancelState.ALREADY_TERMINAL
                    : CancelState.NOT_FOUND;
        }
        record.cancelRequested = true;
        return CancelState.PENDING_PROVIDER_ACK;
    }

    @Override
    public synchronized Metrics metrics() {
        return new Metrics(
                descriptor.getProviderId(),
                acceptedCount,
                completedCount,
                cancelledCount,
                failureCount);
    }

    @Override
    public synchronized FaultSnapshot lastFault() {
        return lastFault;
    }

    @Override
    public void close() {
        List<Completion> completions = new ArrayList<>();
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            lifecycleState = LifecycleState.STOPPED;
            healthState = HealthState.UNAVAILABLE;
            modelLoaded = false;
            for (ActiveRecord record : new ArrayList<>(active.values())) {
                completions.add(finishLocked(
                        record,
                        TerminalState.CANCELLED,
                        EMPTY_DIGEST,
                        "LOCAL_PROVIDER_CLOSED"));
            }
        }
        for (Completion completion : completions) {
            deliverTerminal(completion);
        }
        try {
            engine.close();
        } catch (RuntimeException ignored) {
            // Provider state is already closed and cannot be reopened by engine cleanup failure.
        }
    }

    private void runInference(String requestId) {
        ActiveRecord record;
        Completion interrupted;
        synchronized (this) {
            record = active.get(requestId);
            if (record == null) {
                return;
            }
            interrupted = interruptionLocked(record, "BEFORE_ENGINE");
        }
        if (interrupted != null) {
            deliverTerminal(interrupted);
            return;
        }

        EngineOutput engineOutput;
        try {
            engineOutput = Objects.requireNonNull(
                    engine.infer(allowedModel, record.request, signalFor(requestId)),
                    "engine output");
        } catch (RuntimeException exception) {
            Completion completion;
            synchronized (this) {
                ActiveRecord current = active.get(requestId);
                if (current == null) {
                    return;
                }
                completion = interruptionLocked(current, "DURING_ENGINE");
                if (completion == null) {
                    completion = failLocked(
                            current,
                            TerminalState.RETRYABLE_FAILURE,
                            "LOCAL_ENGINE_FAILURE",
                            true);
                }
            }
            deliverTerminal(completion);
            return;
        }

        BoundedOutput output;
        try {
            output = boundOutput(engineOutput, record.request.isStreamingRequested());
        } catch (OutputLimitException exception) {
            Completion completion;
            synchronized (this) {
                ActiveRecord current = active.get(requestId);
                if (current == null) {
                    return;
                }
                completion = failLocked(
                        current,
                        TerminalState.TERMINAL_FAILURE,
                        "LOCAL_OUTPUT_LIMIT_EXCEEDED",
                        false);
            }
            deliverTerminal(completion);
            return;
        }

        long sequence = 1;
        for (byte[] content : output.deliveryChunks) {
            StreamObserver observer;
            StreamChunk chunk;
            synchronized (this) {
                ActiveRecord current = active.get(requestId);
                if (current == null) {
                    return;
                }
                interrupted = interruptionLocked(current, "DURING_STREAM");
                if (interrupted != null) {
                    observer = null;
                    chunk = null;
                } else {
                    observer = current.observer;
                    chunk = new StreamChunk(requestId, sequence, content);
                }
            }
            if (interrupted != null) {
                deliverTerminal(interrupted);
                return;
            }
            if (!deliverChunk(observer, chunk, requestId)) {
                return;
            }
            sequence++;
        }

        Completion completion;
        synchronized (this) {
            ActiveRecord current = active.get(requestId);
            if (current == null) {
                return;
            }
            completion = interruptionLocked(current, "BEFORE_TERMINAL");
            if (completion == null) {
                completion = finishLocked(
                        current,
                        TerminalState.COMPLETED,
                        output.outputDigest,
                        "LOCAL_DEVELOPMENT_COMPLETED");
            }
        }
        deliverTerminal(completion);
    }

    private CancellationSignal signalFor(String requestId) {
        return new CancellationSignal() {
            @Override
            public boolean isCancellationRequested() {
                synchronized (LocalModelProvider.this) {
                    ActiveRecord record = active.get(requestId);
                    return record == null || record.cancelRequested;
                }
            }

            @Override
            public boolean isDeadlineExceeded() {
                synchronized (LocalModelProvider.this) {
                    ActiveRecord record = active.get(requestId);
                    return record == null
                            || clock.now() >= record.request.getDeadlineElapsedRealtimeMs();
                }
            }
        };
    }

    private BoundedOutput boundOutput(EngineOutput engineOutput, boolean streaming) {
        List<byte[]> chunks = engineOutput.getChunks();
        if (chunks.size() > limits.getMaxChunks()) {
            throw new OutputLimitException();
        }
        int totalBytes = 0;
        for (byte[] chunk : chunks) {
            if (chunk.length > limits.getMaxChunkBytes()) {
                throw new OutputLimitException();
            }
            totalBytes = Math.addExact(totalBytes, chunk.length);
            if (totalBytes > limits.getMaxTotalBytes()) {
                throw new OutputLimitException();
            }
        }
        byte[] joined = new byte[totalBytes];
        int offset = 0;
        for (byte[] chunk : chunks) {
            System.arraycopy(chunk, 0, joined, offset, chunk.length);
            offset += chunk.length;
        }
        if (!streaming && joined.length > MAX_STREAM_CHUNK_BYTES) {
            throw new OutputLimitException();
        }
        List<byte[]> delivery = streaming
                ? chunks
                : Collections.singletonList(joined);
        return new BoundedOutput(delivery, sha256(joined));
    }

    private Completion interruptionLocked(ActiveRecord record, String phase) {
        if (record.cancelRequested) {
            return finishLocked(
                    record,
                    TerminalState.CANCELLED,
                    EMPTY_DIGEST,
                    "LOCAL_CANCELLED_" + phase);
        }
        if (clock.now() >= record.request.getDeadlineElapsedRealtimeMs()) {
            return finishLocked(
                    record,
                    TerminalState.DEADLINE_EXCEEDED,
                    EMPTY_DIGEST,
                    "LOCAL_DEADLINE_EXCEEDED_" + phase);
        }
        return null;
    }

    private boolean deliverChunk(
            StreamObserver observer,
            StreamChunk chunk,
            String requestId) {
        try {
            observer.onChunk(chunk);
            return true;
        } catch (RuntimeException exception) {
            Completion completion;
            synchronized (this) {
                ActiveRecord current = active.get(requestId);
                if (current == null) {
                    return false;
                }
                completion = failLocked(
                        current,
                        TerminalState.TERMINAL_FAILURE,
                        "LOCAL_OBSERVER_CALLBACK_FAILURE",
                        false);
            }
            deliverTerminal(completion);
            return false;
        }
    }

    private static void deliverTerminal(Completion completion) {
        if (completion == null) {
            return;
        }
        try {
            completion.observer.onTerminal(completion.result);
        } catch (RuntimeException ignored) {
            // Terminal state is committed before callback delivery.
        }
    }

    private Completion failLocked(
            ActiveRecord record,
            TerminalState terminalState,
            String faultCode,
            boolean retryable) {
        lastFault = new FaultSnapshot(
                descriptor.getProviderId(),
                faultCode,
                retryable,
                false);
        return finishLocked(record, terminalState, EMPTY_DIGEST, faultCode);
    }

    private Completion finishLocked(
            ActiveRecord record,
            TerminalState terminalState,
            String outputDigest,
            String detailCode) {
        TerminalResult result = new TerminalResult(
                record.request.getRequestId(),
                terminalState,
                outputDigest,
                detailCode);
        active.remove(record.request.getRequestId());
        terminalHistory.put(record.request.getRequestId(), result);
        while (terminalHistory.size() > MAX_TERMINAL_HISTORY) {
            terminalHistory.remove(terminalHistory.keySet().iterator().next());
        }
        if (terminalState == TerminalState.COMPLETED) {
            completedCount++;
        } else if (terminalState == TerminalState.CANCELLED) {
            cancelledCount++;
        } else {
            failureCount++;
        }
        return new Completion(record.observer, result);
    }

    private Snapshot snapshotLocked() {
        return new Snapshot(
                descriptor,
                lifecycleState,
                healthState,
                modelLoaded ? 1 : 0,
                active.size(),
                0,
                false,
                lifecycleDetailLocked());
    }

    private String lifecycleDetailLocked() {
        if (closed) {
            return "LOCAL_DEVELOPMENT_STOPPED";
        }
        if (lifecycleState == LifecycleState.DEGRADED) {
            return "LOCAL_DEVELOPMENT_DEGRADED";
        }
        return modelLoaded ? "LOCAL_DEVELOPMENT_READY" : "LOCAL_DEVELOPMENT_COLD";
    }

    private void requireOpenLocked() {
        if (closed || lifecycleState == LifecycleState.STOPPED) {
            throw new ProviderUnavailableException("local provider is closed");
        }
    }

    private void requireAllowedModel(ModelSpec modelSpec) {
        Objects.requireNonNull(modelSpec, "modelSpec");
        if (!allowedModel.getModelId().equals(modelSpec.getModelId())
                || !allowedModel.getVersion().equals(modelSpec.getVersion())
                || !allowedModel.getArtifactDigest().equals(modelSpec.getArtifactDigest())) {
            throw new IllegalArgumentException("model spec is not allowed");
        }
    }

    private static void requireRequestId(String requestId) {
        if (requestId == null || requestId.trim().isEmpty() || requestId.length() > 256) {
            throw new IllegalArgumentException("requestId is invalid");
        }
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.trim().isEmpty() || reason.length() > 256) {
            throw new IllegalArgumentException("cancel reason is invalid");
        }
    }

    private static String sha256(byte[] value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value);
            char[] output = new char[bytes.length * 2];
            char[] digits = "0123456789abcdef".toCharArray();
            for (int index = 0; index < bytes.length; index++) {
                int current = bytes[index] & 0xff;
                output[index * 2] = digits[current >>> 4];
                output[index * 2 + 1] = digits[current & 0x0f];
            }
            return new String(output);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static final class ActiveRecord {
        final InferenceRequest request;
        final StreamObserver observer;
        boolean cancelRequested;

        ActiveRecord(InferenceRequest request, StreamObserver observer) {
            this.request = request;
            this.observer = observer;
        }
    }

    private static final class BoundedOutput {
        final List<byte[]> deliveryChunks;
        final String outputDigest;

        BoundedOutput(List<byte[]> deliveryChunks, String outputDigest) {
            this.deliveryChunks = deliveryChunks;
            this.outputDigest = outputDigest;
        }
    }

    private static final class Completion {
        final StreamObserver observer;
        final TerminalResult result;

        Completion(StreamObserver observer, TerminalResult result) {
            this.observer = observer;
            this.result = result;
        }
    }

    private static final class OutputLimitException extends RuntimeException {
    }

    public static final class ProviderUnavailableException extends IllegalStateException {
        public ProviderUnavailableException(String message) {
            super(message);
        }

        public ProviderUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static final class ProviderBusyException extends IllegalStateException {
        public ProviderBusyException(String message) {
            super(message);
        }
    }

    public static final class DeadlineExceededException extends IllegalStateException {
        public DeadlineExceededException(String message) {
            super(message);
        }
    }

    public static final class DuplicateRequestException extends IllegalStateException {
        public DuplicateRequestException(String message) {
            super(message);
        }
    }
}
