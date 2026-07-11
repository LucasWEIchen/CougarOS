package com.centralbrain.runtime.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

/** Test-only executable provider. It performs no network, vendor or hardware access. */
public final class DeterministicStubModelProvider implements ModelProvider {
    private static final int MAX_TERMINAL_HISTORY = 64;
    private static final String EMPTY_DIGEST = sha256(new byte[0]);

    public interface ElapsedRealtimeClock {
        long now();
    }

    public enum FaultMode {
        NONE,
        RETRYABLE_BEFORE_FIRST_CHUNK,
        TERMINAL_AFTER_FIRST_CHUNK,
        FAULT_ISOLATE_BEFORE_FIRST_CHUNK
    }

    private final Descriptor descriptor = ModelProviderProfiles
            .deterministicStub()
            .getDescriptor();
    private final ModelSpec allowedModel;
    private final Executor executor;
    private final ElapsedRealtimeClock clock;
    private final Map<String, ActiveRecord> active = new LinkedHashMap<>();
    private final LinkedHashMap<String, TerminalResult> terminalHistory =
            new LinkedHashMap<>();

    private LifecycleState lifecycleState = LifecycleState.COLD;
    private HealthState healthState = HealthState.HEALTHY;
    private FaultSnapshot lastFault;
    private FaultMode faultMode = FaultMode.NONE;
    private boolean modelLoaded;
    private boolean closed;
    private long acceptedCount;
    private long completedCount;
    private long cancelledCount;
    private long failureCount;

    public DeterministicStubModelProvider(
            ModelSpec allowedModel,
            Executor executor,
            ElapsedRealtimeClock clock) {
        this.allowedModel = Objects.requireNonNull(allowedModel, "allowedModel");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.clock = Objects.requireNonNull(clock, "clock");
        lastFault = new FaultSnapshot(
                descriptor.getProviderId(),
                "NONE",
                false,
                false);
        if (descriptor.getAssurance() != Assurance.TEST_ONLY
                || descriptor.isHardwareBacked()
                || descriptor.isProductionEligible()) {
            throw new IllegalStateException("deterministic stub profile must remain test-only");
        }
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
    public synchronized Snapshot warmup(ModelSpec modelSpec) {
        requireOpenLocked();
        requireAllowedModel(modelSpec);
        if (lifecycleState == LifecycleState.FAULT_ISOLATED) {
            throw new IllegalStateException("fault-isolated provider cannot warm up");
        }
        lifecycleState = LifecycleState.WARMING;
        modelLoaded = true;
        lifecycleState = LifecycleState.READY;
        healthState = HealthState.HEALTHY;
        return snapshotLocked();
    }

    @Override
    public synchronized InferenceHandle infer(
            InferenceRequest request,
            StreamObserver observer) {
        requireOpenLocked();
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(observer, "observer");
        if (lifecycleState != LifecycleState.READY
                || healthState != HealthState.HEALTHY
                || !modelLoaded) {
            throw new ProviderUnavailableException("deterministic stub is not ready");
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
            throw new ProviderBusyException("deterministic stub slot is occupied");
        }

        byte[] output = deterministicOutput(request);
        ActiveRecord record = new ActiveRecord(request, observer, output, faultMode);
        active.put(request.getRequestId(), record);
        acceptedCount++;
        executor.execute(() -> runFirstPhase(request.getRequestId()));
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
        if (!descriptor.isSupportsCancellation()) {
            return CancelState.UNSUPPORTED;
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
            List<ActiveRecord> activeRecords = new ArrayList<>(active.values());
            for (ActiveRecord record : activeRecords) {
                completions.add(finishLocked(
                        record,
                        TerminalState.CANCELLED,
                        EMPTY_DIGEST,
                        "PROVIDER_CLOSED"));
            }
        }
        for (Completion completion : completions) {
            deliverTerminal(completion);
        }
    }

    public synchronized void setFaultModeForTest(FaultMode faultMode) {
        requireOpenLocked();
        this.faultMode = Objects.requireNonNull(faultMode, "faultMode");
    }

    public synchronized TerminalResult findTerminalForTest(String requestId) {
        requireRequestId(requestId);
        return terminalHistory.get(requestId);
    }

    private void runFirstPhase(String requestId) {
        Completion completion = null;
        StreamChunk chunk = null;
        StreamObserver observer = null;
        boolean scheduleSecond = false;
        synchronized (this) {
            ActiveRecord record = active.get(requestId);
            if (record == null) {
                return;
            }
            if (record.cancelRequested) {
                completion = finishLocked(
                        record,
                        TerminalState.CANCELLED,
                        EMPTY_DIGEST,
                        "CANCELLED_BEFORE_FIRST_CHUNK");
            } else if (record.faultMode == FaultMode.RETRYABLE_BEFORE_FIRST_CHUNK) {
                completion = failLocked(
                        record,
                        TerminalState.RETRYABLE_FAILURE,
                        "STUB_RETRYABLE_FAULT",
                        true,
                        false);
            } else if (record.faultMode == FaultMode.FAULT_ISOLATE_BEFORE_FIRST_CHUNK) {
                lifecycleState = LifecycleState.FAULT_ISOLATED;
                healthState = HealthState.UNHEALTHY;
                completion = failLocked(
                        record,
                        TerminalState.TERMINAL_FAILURE,
                        "STUB_FAULT_ISOLATED",
                        false,
                        true);
            } else {
                observer = record.observer;
                if (record.request.isStreamingRequested()) {
                    chunk = new StreamChunk(
                            requestId,
                            1,
                            firstHalf(record.output));
                }
                scheduleSecond = true;
            }
        }
        if (completion != null) {
            deliverTerminal(completion);
            return;
        }
        if (chunk != null && !deliverChunk(observer, chunk, requestId)) {
            return;
        }
        if (scheduleSecond) {
            executor.execute(() -> runSecondPhase(requestId));
        }
    }

    private void runSecondPhase(String requestId) {
        Completion completion = null;
        StreamChunk chunk = null;
        StreamObserver observer = null;
        boolean completeAfterChunk = false;
        synchronized (this) {
            ActiveRecord record = active.get(requestId);
            if (record == null) {
                return;
            }
            observer = record.observer;
            if (record.cancelRequested) {
                completion = finishLocked(
                        record,
                        TerminalState.CANCELLED,
                        EMPTY_DIGEST,
                        "CANCELLED_DURING_STREAM");
            } else if (record.faultMode == FaultMode.TERMINAL_AFTER_FIRST_CHUNK) {
                completion = failLocked(
                        record,
                        TerminalState.TERMINAL_FAILURE,
                        "STUB_TERMINAL_FAULT",
                        false,
                        false);
            } else {
                chunk = new StreamChunk(
                        requestId,
                        record.request.isStreamingRequested() ? 2 : 1,
                        record.request.isStreamingRequested()
                                ? secondHalf(record.output)
                                : record.output);
                completeAfterChunk = true;
            }
        }
        if (completion != null) {
            deliverTerminal(completion);
            return;
        }
        if (chunk != null && !deliverChunk(observer, chunk, requestId)) {
            return;
        }
        if (completeAfterChunk) {
            synchronized (this) {
                ActiveRecord record = active.get(requestId);
                if (record == null) {
                    return;
                }
                if (record.cancelRequested) {
                    completion = finishLocked(
                            record,
                            TerminalState.CANCELLED,
                            EMPTY_DIGEST,
                            "CANCELLED_AFTER_FINAL_CHUNK");
                } else {
                    completion = finishLocked(
                            record,
                            TerminalState.COMPLETED,
                            sha256(record.output),
                            "STUB_COMPLETED");
                }
            }
        }
        deliverTerminal(completion);
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
                ActiveRecord record = active.get(requestId);
                if (record == null) {
                    return false;
                }
                completion = failLocked(
                        record,
                        TerminalState.TERMINAL_FAILURE,
                        "OBSERVER_CALLBACK_FAILURE",
                        false,
                        false);
            }
            deliverTerminal(completion);
            return false;
        }
    }

    private static void deliverTerminal(Completion completion) {
        try {
            completion.observer.onTerminal(completion.result);
        } catch (RuntimeException ignored) {
            // The provider state is already terminal; observer failure cannot reopen it.
        }
    }

    private Completion failLocked(
            ActiveRecord record,
            TerminalState terminalState,
            String faultCode,
            boolean retryable,
            boolean isolated) {
        lastFault = new FaultSnapshot(
                descriptor.getProviderId(),
                faultCode,
                retryable,
                isolated);
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
            String oldest = terminalHistory.keySet().iterator().next();
            terminalHistory.remove(oldest);
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
            return "STUB_STOPPED";
        }
        if (lifecycleState == LifecycleState.FAULT_ISOLATED) {
            return "STUB_FAULT_ISOLATED";
        }
        return modelLoaded ? "STUB_READY" : "STUB_COLD";
    }

    private void requireOpenLocked() {
        if (closed || lifecycleState == LifecycleState.STOPPED) {
            throw new ProviderUnavailableException("deterministic stub is closed");
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

    private static byte[] deterministicOutput(InferenceRequest request) {
        return ("stub:" + request.getModelId() + ":" + request.getInputDigest())
                .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] firstHalf(byte[] value) {
        int split = Math.max(1, value.length / 2);
        byte[] output = new byte[split];
        System.arraycopy(value, 0, output, 0, split);
        return output;
    }

    private static byte[] secondHalf(byte[] value) {
        int split = Math.max(1, value.length / 2);
        byte[] output = new byte[value.length - split];
        System.arraycopy(value, split, output, 0, output.length);
        return output;
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

    private static final class ActiveRecord {
        final InferenceRequest request;
        final StreamObserver observer;
        final byte[] output;
        final FaultMode faultMode;
        boolean cancelRequested;

        ActiveRecord(
                InferenceRequest request,
                StreamObserver observer,
                byte[] output,
                FaultMode faultMode) {
            this.request = request;
            this.observer = observer;
            this.output = output;
            this.faultMode = faultMode;
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

    public static final class ProviderUnavailableException extends IllegalStateException {
        public ProviderUnavailableException(String message) {
            super(message);
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
