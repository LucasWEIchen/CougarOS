package com.centralbrain.runtime.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * Internal model-runtime provider contract. Production routing is not wired.
 * Req IDs: APP-004, XSC-001, XSC-004, NV-F-011, NV-G-004, NV-G-006.
 */
public interface ModelProvider extends AutoCloseable {
    int MAX_STREAM_CHUNK_BYTES = 65_536;

    Descriptor descriptor();

    Snapshot snapshot();

    Snapshot warmup(ModelSpec modelSpec);

    InferenceHandle infer(InferenceRequest request, StreamObserver observer);

    CancelState cancel(String requestId, String reason);

    Metrics metrics();

    FaultSnapshot lastFault();

    @Override
    void close();

    interface StreamObserver {
        void onChunk(StreamChunk chunk);

        void onTerminal(TerminalResult result);
    }

    enum BackendKind {
        DETERMINISTIC_STUB,
        ANDROID_LOCAL_DEVELOPMENT,
        OLLAMA_DEBUG,
        VENDOR_NPU
    }

    enum Assurance {
        EMPTY,
        TEST_ONLY,
        DEBUG_ONLY,
        PRODUCTION
    }

    enum FallbackClass {
        NEVER,
        TEST_ONLY,
        POLICY_CONTROLLED
    }

    enum LifecycleState {
        UNAVAILABLE,
        COLD,
        WARMING,
        READY,
        DEGRADED,
        FAULT_ISOLATED,
        STOPPED
    }

    enum HealthState {
        UNAVAILABLE,
        HEALTHY,
        DEGRADED,
        UNHEALTHY
    }

    enum CancelState {
        CANCELLED,
        PENDING_PROVIDER_ACK,
        ALREADY_TERMINAL,
        NOT_FOUND,
        UNSUPPORTED
    }

    enum TerminalState {
        COMPLETED,
        CANCELLED,
        DEADLINE_EXCEEDED,
        RETRYABLE_FAILURE,
        TERMINAL_FAILURE
    }

    final class Descriptor {
        private final String providerId;
        private final BackendKind backendKind;
        private final Assurance assurance;
        private final FallbackClass fallbackClass;
        private final boolean hardwareBacked;
        private final boolean productionEligible;
        private final boolean supportsWarmup;
        private final boolean supportsInference;
        private final boolean supportsStreaming;
        private final boolean supportsCancellation;
        private final boolean supportsMetrics;
        private final boolean supportsFaultReporting;
        private final int maxConcurrentRequests;

        public Descriptor(
                String providerId,
                BackendKind backendKind,
                Assurance assurance,
                FallbackClass fallbackClass,
                boolean hardwareBacked,
                boolean productionEligible,
                boolean supportsWarmup,
                boolean supportsInference,
                boolean supportsStreaming,
                boolean supportsCancellation,
                boolean supportsMetrics,
                boolean supportsFaultReporting,
                int maxConcurrentRequests) {
            this.providerId = requireMetadata(providerId, "providerId");
            this.backendKind = Objects.requireNonNull(backendKind, "backendKind");
            this.assurance = Objects.requireNonNull(assurance, "assurance");
            this.fallbackClass = Objects.requireNonNull(fallbackClass, "fallbackClass");
            if (maxConcurrentRequests < 0 || maxConcurrentRequests > 64) {
                throw new IllegalArgumentException(
                        "maxConcurrentRequests must be in range 0..64");
            }
            if (supportsInference != (maxConcurrentRequests > 0)) {
                throw new IllegalArgumentException(
                        "inference support and concurrency slots must agree");
            }
            if ((supportsWarmup || supportsStreaming || supportsCancellation)
                    && !supportsInference) {
                throw new IllegalArgumentException(
                        "warmup, streaming and cancellation require inference support");
            }
            if (productionEligible && assurance != Assurance.PRODUCTION) {
                throw new IllegalArgumentException(
                        "production eligibility requires production assurance");
            }
            if ((backendKind == BackendKind.DETERMINISTIC_STUB
                    || backendKind == BackendKind.ANDROID_LOCAL_DEVELOPMENT
                    || backendKind == BackendKind.OLLAMA_DEBUG)
                    && (hardwareBacked || productionEligible)) {
                throw new IllegalArgumentException(
                        "development providers cannot claim hardware or production");
            }
            if (assurance == Assurance.EMPTY
                    && (supportsWarmup
                            || supportsInference
                            || supportsStreaming
                            || supportsCancellation
                            || supportsMetrics
                            || maxConcurrentRequests != 0
                            || fallbackClass != FallbackClass.NEVER
                            || productionEligible
                            || hardwareBacked)) {
                throw new IllegalArgumentException(
                        "empty providers must remain unavailable and non-routable");
            }
            this.hardwareBacked = hardwareBacked;
            this.productionEligible = productionEligible;
            this.supportsWarmup = supportsWarmup;
            this.supportsInference = supportsInference;
            this.supportsStreaming = supportsStreaming;
            this.supportsCancellation = supportsCancellation;
            this.supportsMetrics = supportsMetrics;
            this.supportsFaultReporting = supportsFaultReporting;
            this.maxConcurrentRequests = maxConcurrentRequests;
        }

        public String getProviderId() {
            return providerId;
        }

        public BackendKind getBackendKind() {
            return backendKind;
        }

        public Assurance getAssurance() {
            return assurance;
        }

        public FallbackClass getFallbackClass() {
            return fallbackClass;
        }

        public boolean isHardwareBacked() {
            return hardwareBacked;
        }

        public boolean isProductionEligible() {
            return productionEligible;
        }

        public boolean isSupportsWarmup() {
            return supportsWarmup;
        }

        public boolean isSupportsInference() {
            return supportsInference;
        }

        public boolean isSupportsStreaming() {
            return supportsStreaming;
        }

        public boolean isSupportsCancellation() {
            return supportsCancellation;
        }

        public boolean isSupportsMetrics() {
            return supportsMetrics;
        }

        public boolean isSupportsFaultReporting() {
            return supportsFaultReporting;
        }

        public int getMaxConcurrentRequests() {
            return maxConcurrentRequests;
        }
    }

    final class Snapshot {
        private final String providerId;
        private final LifecycleState lifecycleState;
        private final HealthState healthState;
        private final int loadedModelCount;
        private final int activeRequestCount;
        private final int queuedRequestCount;
        private final boolean hardwareAccessed;
        private final String detailCode;

        public Snapshot(
                Descriptor descriptor,
                LifecycleState lifecycleState,
                HealthState healthState,
                int loadedModelCount,
                int activeRequestCount,
                int queuedRequestCount,
                boolean hardwareAccessed,
                String detailCode) {
            Objects.requireNonNull(descriptor, "descriptor");
            this.providerId = descriptor.getProviderId();
            this.lifecycleState = Objects.requireNonNull(
                    lifecycleState,
                    "lifecycleState");
            this.healthState = Objects.requireNonNull(healthState, "healthState");
            if (loadedModelCount < 0 || loadedModelCount > 128) {
                throw new IllegalArgumentException("loadedModelCount is out of range");
            }
            if (activeRequestCount < 0
                    || activeRequestCount > descriptor.getMaxConcurrentRequests()) {
                throw new IllegalArgumentException("activeRequestCount exceeds provider slots");
            }
            if (queuedRequestCount < 0 || queuedRequestCount > 1_024) {
                throw new IllegalArgumentException("queuedRequestCount is out of range");
            }
            if (!descriptor.isSupportsInference()
                    && (loadedModelCount != 0
                            || activeRequestCount != 0
                            || queuedRequestCount != 0)) {
                throw new IllegalArgumentException(
                        "non-inference provider cannot report model or request state");
            }
            if (hardwareAccessed && !descriptor.isHardwareBacked()) {
                throw new IllegalArgumentException(
                        "non-hardware provider cannot report hardware access");
            }
            if (descriptor.getAssurance() == Assurance.EMPTY
                    && (lifecycleState != LifecycleState.UNAVAILABLE
                            || healthState != HealthState.UNAVAILABLE)) {
                throw new IllegalArgumentException(
                        "empty provider snapshot must remain unavailable");
            }
            if (lifecycleState == LifecycleState.UNAVAILABLE
                    && (healthState != HealthState.UNAVAILABLE || activeRequestCount != 0)) {
                throw new IllegalArgumentException(
                        "unavailable provider must have unavailable health and no active work");
            }
            if (lifecycleState == LifecycleState.READY
                    && healthState != HealthState.HEALTHY) {
                throw new IllegalArgumentException("ready provider must be healthy");
            }
            if (lifecycleState == LifecycleState.FAULT_ISOLATED
                    && (healthState != HealthState.UNHEALTHY || activeRequestCount != 0)) {
                throw new IllegalArgumentException(
                        "fault-isolated provider must be unhealthy with no active work");
            }
            this.loadedModelCount = loadedModelCount;
            this.activeRequestCount = activeRequestCount;
            this.queuedRequestCount = queuedRequestCount;
            this.hardwareAccessed = hardwareAccessed;
            this.detailCode = requireMetadata(detailCode, "detailCode");
        }

        public String getProviderId() {
            return providerId;
        }

        public LifecycleState getLifecycleState() {
            return lifecycleState;
        }

        public HealthState getHealthState() {
            return healthState;
        }

        public int getLoadedModelCount() {
            return loadedModelCount;
        }

        public int getActiveRequestCount() {
            return activeRequestCount;
        }

        public int getQueuedRequestCount() {
            return queuedRequestCount;
        }

        public boolean isHardwareAccessed() {
            return hardwareAccessed;
        }

        public String getDetailCode() {
            return detailCode;
        }
    }

    final class ModelSpec {
        private final String modelId;
        private final String version;
        private final String artifactDigest;

        public ModelSpec(String modelId, String version, String artifactDigest) {
            this.modelId = requireMetadata(modelId, "modelId");
            this.version = requireMetadata(version, "version");
            this.artifactDigest = requireDigest(artifactDigest, "artifactDigest");
        }

        public String getModelId() {
            return modelId;
        }

        public String getVersion() {
            return version;
        }

        public String getArtifactDigest() {
            return artifactDigest;
        }
    }

    final class InferenceRequest {
        private final String requestId;
        private final String modelId;
        private final String inputDigest;
        private final long deadlineElapsedRealtimeMs;
        private final boolean streamingRequested;

        public InferenceRequest(
                String requestId,
                String modelId,
                String inputDigest,
                long deadlineElapsedRealtimeMs,
                boolean streamingRequested) {
            this.requestId = requireMetadata(requestId, "requestId");
            this.modelId = requireMetadata(modelId, "modelId");
            this.inputDigest = requireDigest(inputDigest, "inputDigest");
            if (deadlineElapsedRealtimeMs < 1) {
                throw new IllegalArgumentException(
                        "deadlineElapsedRealtimeMs must be positive");
            }
            this.deadlineElapsedRealtimeMs = deadlineElapsedRealtimeMs;
            this.streamingRequested = streamingRequested;
        }

        public String getRequestId() {
            return requestId;
        }

        public String getModelId() {
            return modelId;
        }

        public String getInputDigest() {
            return inputDigest;
        }

        public long getDeadlineElapsedRealtimeMs() {
            return deadlineElapsedRealtimeMs;
        }

        public boolean isStreamingRequested() {
            return streamingRequested;
        }
    }

    final class InferenceHandle {
        private final String providerId;
        private final String requestId;

        public InferenceHandle(String providerId, String requestId) {
            this.providerId = requireMetadata(providerId, "providerId");
            this.requestId = requireMetadata(requestId, "requestId");
        }

        public String getProviderId() {
            return providerId;
        }

        public String getRequestId() {
            return requestId;
        }
    }

    final class StreamChunk {
        private final String requestId;
        private final long sequence;
        private final byte[] content;

        public StreamChunk(String requestId, long sequence, byte[] content) {
            this.requestId = requireMetadata(requestId, "requestId");
            if (sequence < 1) {
                throw new IllegalArgumentException("sequence must be positive");
            }
            if (content == null || content.length == 0
                    || content.length > MAX_STREAM_CHUNK_BYTES) {
                throw new IllegalArgumentException("stream chunk size is invalid");
            }
            this.sequence = sequence;
            this.content = Arrays.copyOf(content, content.length);
        }

        public String getRequestId() {
            return requestId;
        }

        public long getSequence() {
            return sequence;
        }

        public byte[] getContent() {
            return Arrays.copyOf(content, content.length);
        }
    }

    final class TerminalResult {
        private final String requestId;
        private final TerminalState state;
        private final String outputDigest;
        private final String detailCode;

        public TerminalResult(
                String requestId,
                TerminalState state,
                String outputDigest,
                String detailCode) {
            this.requestId = requireMetadata(requestId, "requestId");
            this.state = Objects.requireNonNull(state, "state");
            this.outputDigest = requireDigest(outputDigest, "outputDigest");
            this.detailCode = requireMetadata(detailCode, "detailCode");
        }

        public String getRequestId() {
            return requestId;
        }

        public TerminalState getState() {
            return state;
        }

        public String getOutputDigest() {
            return outputDigest;
        }

        public String getDetailCode() {
            return detailCode;
        }
    }

    final class Metrics {
        private final String providerId;
        private final long acceptedCount;
        private final long completedCount;
        private final long cancelledCount;
        private final long failureCount;

        public Metrics(
                String providerId,
                long acceptedCount,
                long completedCount,
                long cancelledCount,
                long failureCount) {
            this.providerId = requireMetadata(providerId, "providerId");
            if (acceptedCount < 0
                    || completedCount < 0
                    || cancelledCount < 0
                    || failureCount < 0
                    || completedCount + cancelledCount + failureCount > acceptedCount) {
                throw new IllegalArgumentException("provider metrics are inconsistent");
            }
            this.acceptedCount = acceptedCount;
            this.completedCount = completedCount;
            this.cancelledCount = cancelledCount;
            this.failureCount = failureCount;
        }

        public String getProviderId() {
            return providerId;
        }

        public long getAcceptedCount() {
            return acceptedCount;
        }

        public long getCompletedCount() {
            return completedCount;
        }

        public long getCancelledCount() {
            return cancelledCount;
        }

        public long getFailureCount() {
            return failureCount;
        }
    }

    final class FaultSnapshot {
        private final String providerId;
        private final String faultCode;
        private final boolean retryable;
        private final boolean isolated;

        public FaultSnapshot(
                String providerId,
                String faultCode,
                boolean retryable,
                boolean isolated) {
            this.providerId = requireMetadata(providerId, "providerId");
            this.faultCode = requireMetadata(faultCode, "faultCode");
            this.retryable = retryable;
            this.isolated = isolated;
        }

        public String getProviderId() {
            return providerId;
        }

        public String getFaultCode() {
            return faultCode;
        }

        public boolean isRetryable() {
            return retryable;
        }

        public boolean isIsolated() {
            return isolated;
        }
    }

    private static String requireMetadata(String value, String name) {
        if (value == null || value.trim().isEmpty() || value.length() > 256) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static String requireDigest(String value, String name) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " must be a lowercase SHA-256 digest");
        }
        return value;
    }
}
