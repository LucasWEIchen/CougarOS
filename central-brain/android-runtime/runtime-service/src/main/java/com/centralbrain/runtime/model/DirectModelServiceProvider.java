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

/**
 * AIOS-owned ModelProvider that composes bounded input resolution with a direct protocol adapter.
 *
 * <p>The implementation is in the production source set but is not registered by the release
 * composition root. Registration remains fail-closed until the production input owner, service
 * health owner and target qualification are available.</p>
 *
 * <p>Req IDs: APP-002, APP-004, S2-MDL-001/002/003/004/005/006,
 * S2-OBS-001/002, S2-SAF-001.</p>
 */
public final class DirectModelServiceProvider implements ModelProvider {
    private static final int MAX_TERMINAL_HISTORY = 64;
    private static final String EMPTY_DIGEST = sha256(new byte[0]);

    private final Descriptor descriptor;
    private final ModelSpec allowedModel;
    private final DirectModelServiceContract.Endpoint endpoint;
    private final InputResolver inputResolver;
    private final ModelAvailabilityProbe availabilityProbe;
    private final OutputValidator outputValidator;
    private final OllamaChatProtocolAdapter adapter;
    private final Executor executor;
    private final ElapsedRealtimeClock clock;
    private final Map<String, ActiveRecord> active = new LinkedHashMap<>();
    private final LinkedHashMap<String, TerminalResult> terminalHistory =
            new LinkedHashMap<>();

    private LifecycleState lifecycleState = LifecycleState.COLD;
    private HealthState healthState = HealthState.UNAVAILABLE;
    private FaultSnapshot lastFault;
    private boolean modelAvailable;
    private boolean closed;
    private long acceptedCount;
    private long completedCount;
    private long cancelledCount;
    private long failureCount;

    public DirectModelServiceProvider(
            ModelSpec allowedModel,
            DirectModelServiceContract.Endpoint endpoint,
            InputResolver inputResolver,
            ModelAvailabilityProbe availabilityProbe,
            OutputValidator outputValidator,
            OllamaChatProtocolAdapter adapter,
            Executor executor,
            ElapsedRealtimeClock clock) {
        this.descriptor = ModelProviderProfiles.directModelService().getDescriptor();
        this.allowedModel = Objects.requireNonNull(allowedModel, "allowedModel");
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.inputResolver = Objects.requireNonNull(inputResolver, "inputResolver");
        this.availabilityProbe =
                Objects.requireNonNull(availabilityProbe, "availabilityProbe");
        this.outputValidator = Objects.requireNonNull(outputValidator, "outputValidator");
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.lastFault = new FaultSnapshot(
                descriptor.getProviderId(),
                "NONE",
                false,
                false);
        if (descriptor.getBackendKind() != BackendKind.DIRECT_MODEL_SERVICE
                || descriptor.getAssurance() != Assurance.TARGET_INTEGRATION
                || descriptor.getFallbackClass() != FallbackClass.NEVER
                || descriptor.isHardwareBacked()
                || descriptor.isProductionEligible()
                || endpoint.isAgentGatewayRequired()) {
            throw new IllegalStateException(
                    "direct model provider profile violates target-integration boundaries");
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
    public Snapshot warmup(ModelSpec modelSpec) {
        synchronized (this) {
            requireOpenLocked();
            requireAllowedModel(modelSpec);
            if (!active.isEmpty()) {
                throw new ProviderBusyException("direct model provider has active work");
            }
            lifecycleState = LifecycleState.WARMING;
            healthState = HealthState.UNAVAILABLE;
        }

        boolean available;
        try {
            available = availabilityProbe.isAvailable(endpoint, modelSpec);
        } catch (RuntimeException failure) {
            return failWarmup("DIRECT_MODEL_HEALTH_PROBE_FAILED", failure);
        }
        if (!available) {
            return failWarmup("DIRECT_MODEL_UNAVAILABLE", null);
        }

        synchronized (this) {
            requireOpenLocked();
            modelAvailable = true;
            lifecycleState = LifecycleState.READY;
            healthState = HealthState.HEALTHY;
            lastFault = new FaultSnapshot(
                    descriptor.getProviderId(),
                    "NONE",
                    false,
                    false);
            return snapshotLocked();
        }
    }

    private Snapshot failWarmup(String faultCode, RuntimeException cause) {
        synchronized (this) {
            modelAvailable = false;
            lifecycleState = LifecycleState.DEGRADED;
            healthState = HealthState.DEGRADED;
            lastFault = new FaultSnapshot(
                    descriptor.getProviderId(),
                    faultCode,
                    true,
                    false);
        }
        if (cause == null) {
            throw new ProviderUnavailableException("direct model service is unavailable");
        }
        throw new ProviderUnavailableException(
                "direct model service health probe failed",
                cause);
    }

    @Override
    public InferenceHandle infer(InferenceRequest request, StreamObserver observer) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(observer, "observer");
        synchronized (this) {
            requireOpenLocked();
            if (!modelAvailable
                    || lifecycleState != LifecycleState.READY
                    || healthState != HealthState.HEALTHY) {
                throw new ProviderUnavailableException(
                        "direct model provider is not ready");
            }
            if (!allowedModel.getModelId().equals(request.getModelId())) {
                throw new IllegalArgumentException("request model is not warmed");
            }
            if (request.getRequestId().length() > 128) {
                throw new IllegalArgumentException(
                        "requestId exceeds the direct model contract");
            }
            if (request.getDeadlineElapsedRealtimeMs() <= clock.now()) {
                throw new DeadlineExceededException("inference deadline has expired");
            }
            if (active.containsKey(request.getRequestId())
                    || terminalHistory.containsKey(request.getRequestId())) {
                throw new DuplicateRequestException("request ID already exists");
            }
            if (active.size() >= descriptor.getMaxConcurrentRequests()) {
                throw new ProviderBusyException("direct model provider slot is occupied");
            }
            active.put(request.getRequestId(), new ActiveRecord(request, observer));
            acceptedCount++;
        }
        try {
            executor.execute(() -> runInference(request.getRequestId()));
        } catch (RuntimeException failure) {
            Completion completion;
            synchronized (this) {
                ActiveRecord record = active.get(request.getRequestId());
                completion = record == null
                        ? null
                        : failLocked(
                                record,
                                TerminalState.RETRYABLE_FAILURE,
                                "DIRECT_MODEL_EXECUTOR_REJECTED",
                                true);
            }
            deliverTerminal(completion);
            throw new ProviderUnavailableException(
                    "direct model executor rejected request",
                    failure);
        }
        return new InferenceHandle(descriptor.getProviderId(), request.getRequestId());
    }

    @Override
    public CancelState cancel(String requestId, String reason) {
        requireRequestId(requestId);
        requireReason(reason);
        synchronized (this) {
            ActiveRecord record = active.get(requestId);
            if (record == null) {
                return terminalHistory.containsKey(requestId)
                        ? CancelState.ALREADY_TERMINAL
                        : CancelState.NOT_FOUND;
            }
            record.cancelRequested = true;
        }
        adapter.cancel(requestId);
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
        List<String> requestIds = new ArrayList<>();
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            lifecycleState = LifecycleState.STOPPED;
            healthState = HealthState.UNAVAILABLE;
            modelAvailable = false;
            for (ActiveRecord record : new ArrayList<>(active.values())) {
                requestIds.add(record.request.getRequestId());
                completions.add(finishLocked(
                        record,
                        TerminalState.CANCELLED,
                        EMPTY_DIGEST,
                        "DIRECT_MODEL_PROVIDER_CLOSED"));
            }
        }
        for (String requestId : requestIds) {
            adapter.cancel(requestId);
        }
        for (Completion completion : completions) {
            deliverTerminal(completion);
        }
    }

    private void runInference(String requestId) {
        ActiveRecord record;
        Completion interruption;
        synchronized (this) {
            record = active.get(requestId);
            if (record == null) {
                return;
            }
            interruption = interruptionLocked(record, "BEFORE_INPUT");
        }
        if (interruption != null) {
            deliverTerminal(interruption);
            return;
        }

        ResolvedInput resolved;
        try {
            resolved = Objects.requireNonNull(
                    inputResolver.resolve(record.request),
                    "resolved input");
            validateResolvedInput(record.request, resolved);
        } catch (RuntimeException failure) {
            Completion completion;
            synchronized (this) {
                ActiveRecord current = active.get(requestId);
                completion = current == null
                        ? null
                        : failLocked(
                                current,
                                TerminalState.TERMINAL_FAILURE,
                                "DIRECT_MODEL_INPUT_REJECTED",
                                false);
            }
            deliverTerminal(completion);
            return;
        }

        OllamaChatProtocolAdapter.Result result;
        try {
            result = adapter.execute(
                    endpoint,
                    resolved.getProtocolRequest(),
                    resolved.getPayload(),
                    protocolObserverFor(requestId),
                    cancellationSignalFor(requestId));
        } catch (OllamaChatProtocolAdapter.AdapterException failure) {
            completeAdapterFailure(requestId, failure);
            return;
        } catch (RuntimeException failure) {
            Completion completion;
            synchronized (this) {
                ActiveRecord current = active.get(requestId);
                completion = current == null
                        ? null
                        : failLocked(
                                current,
                                TerminalState.TERMINAL_FAILURE,
                                "DIRECT_MODEL_INTERNAL_FAILURE",
                                false);
            }
            deliverTerminal(completion);
            return;
        }

        try {
            outputValidator.validate(
                    record.request,
                    resolved,
                    result.getStructuredContent());
        } catch (RuntimeException failure) {
            Completion completion;
            synchronized (this) {
                ActiveRecord current = active.get(requestId);
                completion = current == null
                        ? null
                        : failLocked(
                                current,
                                TerminalState.TERMINAL_FAILURE,
                                "DIRECT_MODEL_OUTPUT_REJECTED",
                                false);
            }
            deliverTerminal(completion);
            return;
        }

        if (!record.request.isStreamingRequested()) {
            byte[] content = result.getStructuredContent();
            if (!deliverChunk(requestId, content)) {
                return;
            }
        }

        Completion completion;
        synchronized (this) {
            ActiveRecord current = active.get(requestId);
            if (current == null) {
                return;
            }
            interruption = interruptionLocked(current, "BEFORE_TERMINAL");
            completion = interruption == null
                    ? finishLocked(
                            current,
                            TerminalState.COMPLETED,
                            sha256(result.getStructuredContent()),
                            "DIRECT_MODEL_COMPLETED")
                    : interruption;
        }
        deliverTerminal(completion);
    }

    private OllamaChatProtocolAdapter.StreamObserver protocolObserverFor(String requestId) {
        return new OllamaChatProtocolAdapter.StreamObserver() {
            @Override
            public void onStage(DirectModelServiceContract.Stage stage) {
                synchronized (DirectModelServiceProvider.this) {
                    ActiveRecord record = active.get(requestId);
                    if (record != null) {
                        record.lastStage = stage;
                    }
                }
            }

            @Override
            public void onTextDelta(String textDelta) {
                boolean streaming;
                synchronized (DirectModelServiceProvider.this) {
                    ActiveRecord record = active.get(requestId);
                    streaming = record != null
                            && record.request.isStreamingRequested();
                }
                if (streaming
                        && !deliverChunk(
                                requestId,
                                textDelta.getBytes(StandardCharsets.UTF_8))) {
                    throw new ObserverCallbackException();
                }
            }
        };
    }

    private OllamaChatProtocolAdapter.CancellationSignal cancellationSignalFor(
            String requestId) {
        return new OllamaChatProtocolAdapter.CancellationSignal() {
            @Override
            public boolean isCancellationRequested() {
                synchronized (DirectModelServiceProvider.this) {
                    ActiveRecord record = active.get(requestId);
                    return record == null || record.cancelRequested;
                }
            }

            @Override
            public boolean isDeadlineExceeded() {
                synchronized (DirectModelServiceProvider.this) {
                    ActiveRecord record = active.get(requestId);
                    return record == null
                            || clock.now()
                                    >= record.request.getDeadlineElapsedRealtimeMs();
                }
            }
        };
    }

    private boolean deliverChunk(String requestId, byte[] content) {
        StreamObserver observer;
        StreamChunk chunk;
        Completion interruption;
        synchronized (this) {
            ActiveRecord record = active.get(requestId);
            if (record == null) {
                return false;
            }
            interruption = interruptionLocked(record, "DURING_STREAM");
            if (interruption == null) {
                observer = record.observer;
                chunk = new StreamChunk(requestId, record.nextSequence++, content);
            } else {
                observer = null;
                chunk = null;
            }
        }
        if (interruption != null) {
            deliverTerminal(interruption);
            return false;
        }
        try {
            observer.onChunk(chunk);
            return true;
        } catch (RuntimeException failure) {
            Completion completion;
            synchronized (this) {
                ActiveRecord record = active.get(requestId);
                completion = record == null
                        ? null
                        : failLocked(
                                record,
                                TerminalState.TERMINAL_FAILURE,
                                "DIRECT_MODEL_OBSERVER_FAILURE",
                                false);
            }
            adapter.cancel(requestId);
            deliverTerminal(completion);
            return false;
        }
    }

    private void completeAdapterFailure(
            String requestId,
            OllamaChatProtocolAdapter.AdapterException failure) {
        Completion completion;
        synchronized (this) {
            ActiveRecord record = active.get(requestId);
            if (record == null) {
                return;
            }
            OllamaChatProtocolAdapter.FailureCode code = failure.getFailureCode();
            if (code == OllamaChatProtocolAdapter.FailureCode.CANCELLED) {
                completion = finishLocked(
                        record,
                        TerminalState.CANCELLED,
                        EMPTY_DIGEST,
                        "DIRECT_MODEL_CANCELLED");
            } else if (code == OllamaChatProtocolAdapter.FailureCode.DEADLINE_EXCEEDED) {
                completion = finishLocked(
                        record,
                        TerminalState.DEADLINE_EXCEEDED,
                        EMPTY_DIGEST,
                        "DIRECT_MODEL_DEADLINE_EXCEEDED");
            } else {
                boolean retryable = code
                                == OllamaChatProtocolAdapter.FailureCode.TRANSPORT_FAILURE
                        || code == OllamaChatProtocolAdapter.FailureCode.HTTP_STATUS_REJECTED
                        || code == OllamaChatProtocolAdapter.FailureCode.MODEL_ERROR;
                completion = failLocked(
                        record,
                        retryable
                                ? TerminalState.RETRYABLE_FAILURE
                                : TerminalState.TERMINAL_FAILURE,
                        "DIRECT_MODEL_" + code.name(),
                        retryable);
            }
        }
        deliverTerminal(completion);
    }

    private void validateResolvedInput(
            InferenceRequest request,
            ResolvedInput resolved) {
        DirectModelServiceContract.Request protocolRequest =
                resolved.getProtocolRequest();
        if (!request.getRequestId().equals(protocolRequest.getRequestId())
                || request.getDeadlineElapsedRealtimeMs()
                        != protocolRequest.getDeadlineElapsedRealtimeMs()
                || !request.getInputDigest().equals(resolved.getSourceInputDigest())) {
            throw new IllegalArgumentException(
                    "resolved input is not bound to the provider request");
        }
    }

    private Completion interruptionLocked(ActiveRecord record, String phase) {
        if (record.cancelRequested) {
            return finishLocked(
                    record,
                    TerminalState.CANCELLED,
                    EMPTY_DIGEST,
                    "DIRECT_MODEL_CANCELLED_" + phase);
        }
        if (clock.now() >= record.request.getDeadlineElapsedRealtimeMs()) {
            return finishLocked(
                    record,
                    TerminalState.DEADLINE_EXCEEDED,
                    EMPTY_DIGEST,
                    "DIRECT_MODEL_DEADLINE_EXCEEDED_" + phase);
        }
        return null;
    }

    private Completion failLocked(
            ActiveRecord record,
            TerminalState state,
            String faultCode,
            boolean retryable) {
        lastFault = new FaultSnapshot(
                descriptor.getProviderId(),
                faultCode,
                retryable,
                false);
        return finishLocked(record, state, EMPTY_DIGEST, faultCode);
    }

    private Completion finishLocked(
            ActiveRecord record,
            TerminalState state,
            String outputDigest,
            String detailCode) {
        TerminalResult result = new TerminalResult(
                record.request.getRequestId(),
                state,
                outputDigest,
                detailCode);
        active.remove(record.request.getRequestId());
        terminalHistory.put(record.request.getRequestId(), result);
        while (terminalHistory.size() > MAX_TERMINAL_HISTORY) {
            terminalHistory.remove(terminalHistory.keySet().iterator().next());
        }
        if (state == TerminalState.COMPLETED) {
            completedCount++;
        } else if (state == TerminalState.CANCELLED) {
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
                modelAvailable ? 1 : 0,
                active.size(),
                0,
                false,
                lifecycleDetailLocked());
    }

    private String lifecycleDetailLocked() {
        if (closed) {
            return "DIRECT_MODEL_STOPPED";
        }
        if (lifecycleState == LifecycleState.DEGRADED) {
            return lastFault.getFaultCode();
        }
        return modelAvailable ? "DIRECT_MODEL_READY" : "DIRECT_MODEL_COLD";
    }

    private void requireOpenLocked() {
        if (closed || lifecycleState == LifecycleState.STOPPED) {
            throw new ProviderUnavailableException("direct model provider is closed");
        }
    }

    private void requireAllowedModel(ModelSpec modelSpec) {
        Objects.requireNonNull(modelSpec, "modelSpec");
        if (!allowedModel.getModelId().equals(modelSpec.getModelId())
                || !allowedModel.getVersion().equals(modelSpec.getVersion())
                || !allowedModel.getArtifactDigest().equals(
                        modelSpec.getArtifactDigest())
                || !endpoint.getModelName().equals(modelSpec.getModelId())) {
            throw new IllegalArgumentException("model spec is not allowed");
        }
    }

    private static void requireRequestId(String requestId) {
        if (requestId == null
                || !requestId.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new IllegalArgumentException("requestId is invalid");
        }
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.trim().isEmpty() || reason.length() > 256) {
            throw new IllegalArgumentException("cancel reason is invalid");
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

    private static String sha256(byte[] value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(String.format("%02x", item & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public interface InputResolver {
        ResolvedInput resolve(InferenceRequest request);
    }

    public interface ModelAvailabilityProbe {
        boolean isAvailable(
                DirectModelServiceContract.Endpoint endpoint,
                ModelSpec modelSpec);
    }

    public interface OutputValidator {
        void validate(
                InferenceRequest request,
                ResolvedInput resolvedInput,
                byte[] structuredContent);
    }

    public interface ElapsedRealtimeClock {
        long now();
    }

    public static final class ResolvedInput {
        private final String sourceInputDigest;
        private final DirectModelServiceContract.Request protocolRequest;
        private final OllamaChatProtocolAdapter.Payload payload;

        public ResolvedInput(
                String sourceInputDigest,
                DirectModelServiceContract.Request protocolRequest,
                OllamaChatProtocolAdapter.Payload payload) {
            if (sourceInputDigest == null
                    || !sourceInputDigest.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException(
                        "sourceInputDigest must be a lowercase SHA-256");
            }
            this.sourceInputDigest = sourceInputDigest;
            this.protocolRequest =
                    Objects.requireNonNull(protocolRequest, "protocolRequest");
            this.payload = Objects.requireNonNull(payload, "payload");
        }

        public String getSourceInputDigest() {
            return sourceInputDigest;
        }

        public DirectModelServiceContract.Request getProtocolRequest() {
            return protocolRequest;
        }

        public OllamaChatProtocolAdapter.Payload getPayload() {
            return payload;
        }
    }

    private static final class ActiveRecord {
        final InferenceRequest request;
        final StreamObserver observer;
        long nextSequence = 1;
        boolean cancelRequested;
        DirectModelServiceContract.Stage lastStage =
                DirectModelServiceContract.Stage.ADMITTED;

        ActiveRecord(InferenceRequest request, StreamObserver observer) {
            this.request = request;
            this.observer = observer;
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

    private static final class ObserverCallbackException extends RuntimeException {
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
