package com.centralbrain.runtime.scheduler;

import com.centralbrain.runtime.model.ModelProvider;
import com.centralbrain.runtime.model.ModelProviderProfiles;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Bounded inference resource-admission state machine. It never invokes a provider.
 * Req IDs: APP-004, XSC-001, XSC-004, NV-F-001, NV-F-011, NV-G-004, NV-G-006.
 */
public final class InferenceResourceScheduler {
    public interface ElapsedRealtimeClock {
        long now();
    }

    public interface LeaseIdSupplier {
        String next();
    }

    public enum EffectivePriority {
        HIGH(0),
        NORMAL(1),
        BACKGROUND(2);

        private final int order;

        EffectivePriority(int order) {
            this.order = order;
        }
    }

    public enum ActiveState {
        QUEUED,
        RUNNING,
        CANCEL_REQUESTED
    }

    public enum AdmissionOutcome {
        ADMITTED,
        REPLAYED,
        DUPLICATE_ACTIVE_REQUEST,
        DEADLINE_EXCEEDED,
        QUEUE_TIMEOUT_OUT_OF_RANGE,
        GLOBAL_QUEUE_QUOTA_EXCEEDED,
        OWNER_QUEUE_QUOTA_EXCEEDED,
        ROUTE_UNAVAILABLE
    }

    public enum CancelOutcome {
        CANCELLED_QUEUED,
        PROVIDER_CANCEL_REQUIRED,
        PROVIDER_CANCEL_UNSUPPORTED,
        ALREADY_REQUESTED,
        NOT_FOUND_OR_NOT_OWNER
    }

    public enum CancelReason {
        NONE,
        OWNER_REQUEST,
        DEADLINE_EXCEEDED
    }

    public enum ProviderTerminalOutcome {
        COMPLETED,
        FAILED,
        CANCELLED
    }

    public enum LocalTerminalState {
        COMPLETED,
        FAILED,
        CANCELLED,
        DEADLINE_EXCEEDED
    }

    public enum SettlementOutcome {
        APPLIED,
        NOT_FOUND,
        STALE_LEASE,
        INVALID_STATE
    }

    private final Limits limits;
    private final ElapsedRealtimeClock clock;
    private final LeaseIdSupplier leaseIds;
    private final Map<String, RouteTarget> routes;
    private final LinkedHashMap<String, Record> records = new LinkedHashMap<>();
    private long admissionSequence;

    public InferenceResourceScheduler(
            Limits limits,
            ElapsedRealtimeClock clock,
            LeaseIdSupplier leaseIds,
            Collection<RouteTarget> routeTargets) {
        this.limits = Objects.requireNonNull(limits, "limits");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.leaseIds = Objects.requireNonNull(leaseIds, "leaseIds");
        Objects.requireNonNull(routeTargets, "routeTargets");
        LinkedHashMap<String, RouteTarget> routeMap = new LinkedHashMap<>();
        for (RouteTarget target : routeTargets) {
            Objects.requireNonNull(target, "route target");
            if (routeMap.put(target.getProviderId(), target) != null) {
                throw new IllegalArgumentException("duplicate provider route");
            }
        }
        if (routeMap.isEmpty()) {
            throw new IllegalArgumentException("at least one provider route is required");
        }
        routes = Collections.unmodifiableMap(routeMap);
    }

    public synchronized Admission admit(TrustedSubmission submission) {
        Objects.requireNonNull(submission, "submission");
        ExpiryReport expiry = sweepDeadlinesLocked(clock.now());
        Record existing = records.get(submission.getRequestId());
        if (existing != null) {
            if (existing.matches(submission)) {
                return new Admission(
                        AdmissionOutcome.REPLAYED,
                        snapshot(existing),
                        expiry);
            }
            return new Admission(
                    AdmissionOutcome.DUPLICATE_ACTIVE_REQUEST,
                    null,
                    expiry);
        }

        long now = clock.now();
        if (submission.getTaskDeadlineElapsedRealtimeMs() <= now) {
            return rejected(AdmissionOutcome.DEADLINE_EXCEEDED, expiry);
        }
        if (submission.getMaxQueueWaitMs() < 1
                || submission.getMaxQueueWaitMs() > limits.getMaxQueueWaitMs()) {
            return rejected(AdmissionOutcome.QUEUE_TIMEOUT_OUT_OF_RANGE, expiry);
        }
        RouteTarget target = routes.get(submission.getProviderId());
        if (target == null || !target.isRoutingEnabled()) {
            return rejected(AdmissionOutcome.ROUTE_UNAVAILABLE, expiry);
        }
        if (queuedCountLocked() >= limits.getMaxQueuedGlobal()) {
            return rejected(AdmissionOutcome.GLOBAL_QUEUE_QUOTA_EXCEEDED, expiry);
        }
        if (queuedCountForOwnerLocked(submission.getOwnerFingerprint())
                >= limits.getMaxQueuedPerOwner()) {
            return rejected(AdmissionOutcome.OWNER_QUEUE_QUOTA_EXCEEDED, expiry);
        }

        long queueDeadline = Math.min(
                submission.getTaskDeadlineElapsedRealtimeMs(),
                saturatedAdd(now, submission.getMaxQueueWaitMs()));
        Record record = new Record(
                submission,
                target,
                ++admissionSequence,
                now,
                queueDeadline);
        records.put(record.requestId, record);
        return new Admission(AdmissionOutcome.ADMITTED, snapshot(record), expiry);
    }

    public synchronized Claim claimNext() {
        ExpiryReport expiry = sweepDeadlinesLocked(clock.now());
        if (runningCountLocked() >= limits.getMaxRunningGlobal()) {
            return new Claim(null, expiry);
        }

        List<Record> candidates = new ArrayList<>();
        for (Record record : records.values()) {
            if (record.state == ActiveState.QUEUED) {
                candidates.add(record);
            }
        }
        candidates.sort(DISPATCH_ORDER);
        for (Record record : candidates) {
            if (runningCountForOwnerLocked(record.ownerFingerprint)
                    >= limits.getMaxRunningPerOwner()) {
                continue;
            }
            if (runningCountForProviderLocked(record.providerId)
                    >= record.routeTarget.getMaxConcurrentRequests()) {
                continue;
            }
            String leaseId = requireMetadata(leaseIds.next(), "leaseId");
            if (containsLeaseIdLocked(leaseId)) {
                throw new IllegalStateException("duplicate active lease ID");
            }
            record.state = ActiveState.RUNNING;
            record.leaseId = leaseId;
            record.startedAtElapsedRealtimeMs = clock.now();
            return new Claim(new Lease(snapshot(record), leaseId), expiry);
        }
        return new Claim(null, expiry);
    }

    public synchronized CancelResult cancelOwned(
            String requestId,
            String ownerFingerprint) {
        requireMetadata(requestId, "requestId");
        requireDigest(ownerFingerprint, "ownerFingerprint");
        Record record = records.get(requestId);
        if (record == null || !record.ownerFingerprint.equals(ownerFingerprint)) {
            return new CancelResult(CancelOutcome.NOT_FOUND_OR_NOT_OWNER, null, null);
        }
        if (record.state == ActiveState.QUEUED) {
            ActiveSnapshot cancelled = snapshot(record);
            records.remove(requestId);
            return new CancelResult(CancelOutcome.CANCELLED_QUEUED, cancelled, null);
        }
        if (record.state == ActiveState.CANCEL_REQUESTED) {
            return new CancelResult(
                    CancelOutcome.ALREADY_REQUESTED,
                    snapshot(record),
                    null);
        }
        if (!record.routeTarget.isSupportsCancellation()) {
            return new CancelResult(
                    CancelOutcome.PROVIDER_CANCEL_UNSUPPORTED,
                    snapshot(record),
                    null);
        }
        record.state = ActiveState.CANCEL_REQUESTED;
        record.cancelReason = CancelReason.OWNER_REQUEST;
        CancellationDirective directive = cancellationDirective(record);
        return new CancelResult(
                CancelOutcome.PROVIDER_CANCEL_REQUIRED,
                snapshot(record),
                directive);
    }

    public synchronized Settlement settle(
            String requestId,
            String leaseId,
            ProviderTerminalOutcome providerOutcome) {
        requireMetadata(requestId, "requestId");
        requireMetadata(leaseId, "leaseId");
        Objects.requireNonNull(providerOutcome, "providerOutcome");
        Record record = records.get(requestId);
        if (record == null) {
            return Settlement.rejected(SettlementOutcome.NOT_FOUND);
        }
        if (record.leaseId == null || !record.leaseId.equals(leaseId)) {
            return Settlement.rejected(SettlementOutcome.STALE_LEASE);
        }
        if (record.state == ActiveState.QUEUED) {
            return Settlement.rejected(SettlementOutcome.INVALID_STATE);
        }
        LocalTerminalState localState;
        if (record.cancelReason == CancelReason.DEADLINE_EXCEEDED) {
            localState = LocalTerminalState.DEADLINE_EXCEEDED;
        } else if (record.cancelReason == CancelReason.OWNER_REQUEST) {
            localState = LocalTerminalState.CANCELLED;
        } else if (providerOutcome == ProviderTerminalOutcome.COMPLETED) {
            localState = LocalTerminalState.COMPLETED;
        } else if (providerOutcome == ProviderTerminalOutcome.CANCELLED) {
            localState = LocalTerminalState.CANCELLED;
        } else {
            localState = LocalTerminalState.FAILED;
        }
        ActiveSnapshot settled = snapshot(record);
        records.remove(requestId);
        return new Settlement(
                SettlementOutcome.APPLIED,
                settled,
                providerOutcome,
                localState);
    }

    public synchronized ExpiryReport sweepDeadlines() {
        return sweepDeadlinesLocked(clock.now());
    }

    public synchronized ActiveSnapshot findOwned(
            String requestId,
            String ownerFingerprint) {
        requireMetadata(requestId, "requestId");
        requireDigest(ownerFingerprint, "ownerFingerprint");
        Record record = records.get(requestId);
        if (record == null || !record.ownerFingerprint.equals(ownerFingerprint)) {
            return null;
        }
        return snapshot(record);
    }

    public synchronized SchedulerSnapshot snapshot() {
        return new SchedulerSnapshot(
                records.size(),
                queuedCountLocked(),
                runningCountLocked());
    }

    private ExpiryReport sweepDeadlinesLocked(long now) {
        List<String> queuedExpired = new ArrayList<>();
        List<CancellationDirective> cancellationDirectives = new ArrayList<>();
        List<String> cancellationUnsupported = new ArrayList<>();
        Iterator<Map.Entry<String, Record>> iterator = records.entrySet().iterator();
        while (iterator.hasNext()) {
            Record record = iterator.next().getValue();
            if (record.state == ActiveState.QUEUED
                    && (record.queueDeadlineElapsedRealtimeMs <= now
                            || record.taskDeadlineElapsedRealtimeMs <= now)) {
                queuedExpired.add(record.requestId);
                iterator.remove();
                continue;
            }
            if (record.state == ActiveState.RUNNING
                    && record.taskDeadlineElapsedRealtimeMs <= now) {
                if (record.routeTarget.isSupportsCancellation()) {
                    record.state = ActiveState.CANCEL_REQUESTED;
                    record.cancelReason = CancelReason.DEADLINE_EXCEEDED;
                    cancellationDirectives.add(cancellationDirective(record));
                } else if (!record.deadlineCancellationBlockedReported) {
                    record.deadlineCancellationBlockedReported = true;
                    cancellationUnsupported.add(record.requestId);
                }
            }
        }
        return new ExpiryReport(
                queuedExpired,
                cancellationDirectives,
                cancellationUnsupported);
    }

    private Admission rejected(AdmissionOutcome outcome, ExpiryReport expiry) {
        return new Admission(outcome, null, expiry);
    }

    private int queuedCountLocked() {
        int count = 0;
        for (Record record : records.values()) {
            if (record.state == ActiveState.QUEUED) {
                count++;
            }
        }
        return count;
    }

    private int queuedCountForOwnerLocked(String ownerFingerprint) {
        int count = 0;
        for (Record record : records.values()) {
            if (record.state == ActiveState.QUEUED
                    && record.ownerFingerprint.equals(ownerFingerprint)) {
                count++;
            }
        }
        return count;
    }

    private int runningCountLocked() {
        int count = 0;
        for (Record record : records.values()) {
            if (record.state != ActiveState.QUEUED) {
                count++;
            }
        }
        return count;
    }

    private int runningCountForOwnerLocked(String ownerFingerprint) {
        int count = 0;
        for (Record record : records.values()) {
            if (record.state != ActiveState.QUEUED
                    && record.ownerFingerprint.equals(ownerFingerprint)) {
                count++;
            }
        }
        return count;
    }

    private int runningCountForProviderLocked(String providerId) {
        int count = 0;
        for (Record record : records.values()) {
            if (record.state != ActiveState.QUEUED
                    && record.providerId.equals(providerId)) {
                count++;
            }
        }
        return count;
    }

    private boolean containsLeaseIdLocked(String leaseId) {
        for (Record record : records.values()) {
            if (leaseId.equals(record.leaseId)) {
                return true;
            }
        }
        return false;
    }

    private static CancellationDirective cancellationDirective(Record record) {
        if (record.leaseId == null) {
            throw new IllegalStateException("running cancellation requires a lease");
        }
        return new CancellationDirective(
                record.requestId,
                record.ownerFingerprint,
                record.providerId,
                record.leaseId,
                record.cancelReason);
    }

    private static ActiveSnapshot snapshot(Record record) {
        return new ActiveSnapshot(
                record.requestId,
                record.ownerFingerprint,
                record.modelId,
                record.providerId,
                record.effectivePriority,
                record.state,
                record.cancelReason,
                record.admittedAtElapsedRealtimeMs,
                record.queueDeadlineElapsedRealtimeMs,
                record.taskDeadlineElapsedRealtimeMs,
                record.startedAtElapsedRealtimeMs);
    }

    private static long saturatedAdd(long left, long right) {
        if (left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
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

    private static final Comparator<Record> DISPATCH_ORDER = (left, right) -> {
        int priority = Integer.compare(
                left.effectivePriority.order,
                right.effectivePriority.order);
        if (priority != 0) {
            return priority;
        }
        int deadline = Long.compare(
                left.queueDeadlineElapsedRealtimeMs,
                right.queueDeadlineElapsedRealtimeMs);
        if (deadline != 0) {
            return deadline;
        }
        int sequence = Long.compare(left.admissionSequence, right.admissionSequence);
        if (sequence != 0) {
            return sequence;
        }
        return left.requestId.compareTo(right.requestId);
    };

    private static final class Record {
        final String requestId;
        final String ownerFingerprint;
        final String modelId;
        final String providerId;
        final EffectivePriority effectivePriority;
        final long taskDeadlineElapsedRealtimeMs;
        final long maxQueueWaitMs;
        final RouteTarget routeTarget;
        final long admissionSequence;
        final long admittedAtElapsedRealtimeMs;
        final long queueDeadlineElapsedRealtimeMs;
        ActiveState state = ActiveState.QUEUED;
        CancelReason cancelReason = CancelReason.NONE;
        String leaseId;
        long startedAtElapsedRealtimeMs = -1;
        boolean deadlineCancellationBlockedReported;

        Record(
                TrustedSubmission submission,
                RouteTarget routeTarget,
                long admissionSequence,
                long admittedAtElapsedRealtimeMs,
                long queueDeadlineElapsedRealtimeMs) {
            requestId = submission.getRequestId();
            ownerFingerprint = submission.getOwnerFingerprint();
            modelId = submission.getModelId();
            providerId = submission.getProviderId();
            effectivePriority = submission.getEffectivePriority();
            taskDeadlineElapsedRealtimeMs =
                    submission.getTaskDeadlineElapsedRealtimeMs();
            maxQueueWaitMs = submission.getMaxQueueWaitMs();
            this.routeTarget = routeTarget;
            this.admissionSequence = admissionSequence;
            this.admittedAtElapsedRealtimeMs = admittedAtElapsedRealtimeMs;
            this.queueDeadlineElapsedRealtimeMs = queueDeadlineElapsedRealtimeMs;
        }

        boolean matches(TrustedSubmission submission) {
            return requestId.equals(submission.getRequestId())
                    && ownerFingerprint.equals(submission.getOwnerFingerprint())
                    && modelId.equals(submission.getModelId())
                    && providerId.equals(submission.getProviderId())
                    && effectivePriority == submission.getEffectivePriority()
                    && taskDeadlineElapsedRealtimeMs
                            == submission.getTaskDeadlineElapsedRealtimeMs()
                    && maxQueueWaitMs == submission.getMaxQueueWaitMs();
        }
    }

    public static final class Limits {
        private final int maxQueuedGlobal;
        private final int maxQueuedPerOwner;
        private final int maxRunningGlobal;
        private final int maxRunningPerOwner;
        private final long maxQueueWaitMs;

        public Limits(
                int maxQueuedGlobal,
                int maxQueuedPerOwner,
                int maxRunningGlobal,
                int maxRunningPerOwner,
                long maxQueueWaitMs) {
            if (maxQueuedGlobal < 1 || maxQueuedGlobal > 1_024
                    || maxQueuedPerOwner < 1
                    || maxQueuedPerOwner > maxQueuedGlobal) {
                throw new IllegalArgumentException("queue limits are invalid");
            }
            if (maxRunningGlobal < 1 || maxRunningGlobal > 64
                    || maxRunningPerOwner < 1
                    || maxRunningPerOwner > maxRunningGlobal) {
                throw new IllegalArgumentException("running limits are invalid");
            }
            if (maxQueueWaitMs < 1 || maxQueueWaitMs > 600_000) {
                throw new IllegalArgumentException(
                        "maxQueueWaitMs must be in range 1..600000");
            }
            this.maxQueuedGlobal = maxQueuedGlobal;
            this.maxQueuedPerOwner = maxQueuedPerOwner;
            this.maxRunningGlobal = maxRunningGlobal;
            this.maxRunningPerOwner = maxRunningPerOwner;
            this.maxQueueWaitMs = maxQueueWaitMs;
        }

        public int getMaxQueuedGlobal() {
            return maxQueuedGlobal;
        }

        public int getMaxQueuedPerOwner() {
            return maxQueuedPerOwner;
        }

        public int getMaxRunningGlobal() {
            return maxRunningGlobal;
        }

        public int getMaxRunningPerOwner() {
            return maxRunningPerOwner;
        }

        public long getMaxQueueWaitMs() {
            return maxQueueWaitMs;
        }
    }

    public static final class RouteTarget {
        private final String providerId;
        private final boolean routingEnabled;
        private final boolean supportsCancellation;
        private final int maxConcurrentRequests;
        private final boolean testOnly;

        private RouteTarget(
                String providerId,
                boolean routingEnabled,
                boolean supportsCancellation,
                int maxConcurrentRequests,
                boolean testOnly) {
            this.providerId = requireMetadata(providerId, "providerId");
            if (maxConcurrentRequests < 0 || maxConcurrentRequests > 64) {
                throw new IllegalArgumentException("provider slots are invalid");
            }
            if (routingEnabled && maxConcurrentRequests < 1) {
                throw new IllegalArgumentException("routable provider requires a slot");
            }
            this.routingEnabled = routingEnabled;
            this.supportsCancellation = supportsCancellation;
            this.maxConcurrentRequests = maxConcurrentRequests;
            this.testOnly = testOnly;
        }

        public static RouteTarget fromProfile(ModelProviderProfiles.Profile profile) {
            Objects.requireNonNull(profile, "profile");
            ModelProvider.Descriptor descriptor = profile.getDescriptor();
            return new RouteTarget(
                    descriptor.getProviderId(),
                    profile.isRoutingEnabled(),
                    descriptor.isSupportsCancellation(),
                    descriptor.getMaxConcurrentRequests(),
                    descriptor.getAssurance() != ModelProvider.Assurance.PRODUCTION);
        }

        public static RouteTarget forContractTest(
                String providerId,
                int maxConcurrentRequests,
                boolean supportsCancellation) {
            if (providerId == null || !providerId.startsWith("test.")) {
                throw new IllegalArgumentException(
                        "contract-test provider ID must start with test.");
            }
            return new RouteTarget(
                    providerId,
                    true,
                    supportsCancellation,
                    maxConcurrentRequests,
                    true);
        }

        public String getProviderId() {
            return providerId;
        }

        public boolean isRoutingEnabled() {
            return routingEnabled;
        }

        public boolean isSupportsCancellation() {
            return supportsCancellation;
        }

        public int getMaxConcurrentRequests() {
            return maxConcurrentRequests;
        }

        public boolean isTestOnly() {
            return testOnly;
        }
    }

    public static final class TrustedSubmission {
        private final String requestId;
        private final String ownerFingerprint;
        private final String modelId;
        private final String providerId;
        private final EffectivePriority effectivePriority;
        private final long taskDeadlineElapsedRealtimeMs;
        private final long maxQueueWaitMs;

        private TrustedSubmission(
                String requestId,
                String ownerFingerprint,
                String modelId,
                String providerId,
                EffectivePriority effectivePriority,
                long taskDeadlineElapsedRealtimeMs,
                long maxQueueWaitMs) {
            this.requestId = requireMetadata(requestId, "requestId");
            this.ownerFingerprint = requireDigest(ownerFingerprint, "ownerFingerprint");
            this.modelId = requireMetadata(modelId, "modelId");
            this.providerId = requireMetadata(providerId, "providerId");
            this.effectivePriority = Objects.requireNonNull(
                    effectivePriority,
                    "effectivePriority");
            if (taskDeadlineElapsedRealtimeMs < 1) {
                throw new IllegalArgumentException("task deadline must be positive");
            }
            this.taskDeadlineElapsedRealtimeMs = taskDeadlineElapsedRealtimeMs;
            this.maxQueueWaitMs = maxQueueWaitMs;
        }

        public static TrustedSubmission fromRuntimePolicy(
                String requestId,
                String ownerFingerprint,
                String modelId,
                String providerId,
                EffectivePriority effectivePriority,
                long taskDeadlineElapsedRealtimeMs,
                long maxQueueWaitMs) {
            return new TrustedSubmission(
                    requestId,
                    ownerFingerprint,
                    modelId,
                    providerId,
                    effectivePriority,
                    taskDeadlineElapsedRealtimeMs,
                    maxQueueWaitMs);
        }

        public String getRequestId() {
            return requestId;
        }

        public String getOwnerFingerprint() {
            return ownerFingerprint;
        }

        public String getModelId() {
            return modelId;
        }

        public String getProviderId() {
            return providerId;
        }

        public EffectivePriority getEffectivePriority() {
            return effectivePriority;
        }

        public long getTaskDeadlineElapsedRealtimeMs() {
            return taskDeadlineElapsedRealtimeMs;
        }

        public long getMaxQueueWaitMs() {
            return maxQueueWaitMs;
        }
    }

    public static final class Admission {
        private final AdmissionOutcome outcome;
        private final ActiveSnapshot active;
        private final ExpiryReport expiryReport;

        Admission(
                AdmissionOutcome outcome,
                ActiveSnapshot active,
                ExpiryReport expiryReport) {
            this.outcome = outcome;
            this.active = active;
            this.expiryReport = expiryReport;
        }

        public AdmissionOutcome getOutcome() {
            return outcome;
        }

        public ActiveSnapshot getActive() {
            return active;
        }

        public ExpiryReport getExpiryReport() {
            return expiryReport;
        }
    }

    public static final class Claim {
        private final Lease lease;
        private final ExpiryReport expiryReport;

        Claim(Lease lease, ExpiryReport expiryReport) {
            this.lease = lease;
            this.expiryReport = expiryReport;
        }

        public Lease getLease() {
            return lease;
        }

        public ExpiryReport getExpiryReport() {
            return expiryReport;
        }
    }

    public static final class Lease {
        private final ActiveSnapshot active;
        private final String leaseId;

        Lease(ActiveSnapshot active, String leaseId) {
            this.active = active;
            this.leaseId = leaseId;
        }

        public ActiveSnapshot getActive() {
            return active;
        }

        public String getLeaseId() {
            return leaseId;
        }
    }

    public static final class CancelResult {
        private final CancelOutcome outcome;
        private final ActiveSnapshot active;
        private final CancellationDirective directive;

        CancelResult(
                CancelOutcome outcome,
                ActiveSnapshot active,
                CancellationDirective directive) {
            this.outcome = outcome;
            this.active = active;
            this.directive = directive;
        }

        public CancelOutcome getOutcome() {
            return outcome;
        }

        public ActiveSnapshot getActive() {
            return active;
        }

        public CancellationDirective getDirective() {
            return directive;
        }
    }

    public static final class CancellationDirective {
        private final String requestId;
        private final String ownerFingerprint;
        private final String providerId;
        private final String leaseId;
        private final CancelReason reason;

        CancellationDirective(
                String requestId,
                String ownerFingerprint,
                String providerId,
                String leaseId,
                CancelReason reason) {
            this.requestId = requestId;
            this.ownerFingerprint = ownerFingerprint;
            this.providerId = providerId;
            this.leaseId = leaseId;
            this.reason = reason;
        }

        public String getRequestId() {
            return requestId;
        }

        public String getOwnerFingerprint() {
            return ownerFingerprint;
        }

        public String getProviderId() {
            return providerId;
        }

        public String getLeaseId() {
            return leaseId;
        }

        public CancelReason getReason() {
            return reason;
        }
    }

    public static final class ExpiryReport {
        private final List<String> queuedExpiredRequestIds;
        private final List<CancellationDirective> cancellationDirectives;
        private final List<String> cancellationUnsupportedRequestIds;

        ExpiryReport(
                List<String> queuedExpiredRequestIds,
                List<CancellationDirective> cancellationDirectives,
                List<String> cancellationUnsupportedRequestIds) {
            this.queuedExpiredRequestIds = Collections.unmodifiableList(
                    new ArrayList<>(queuedExpiredRequestIds));
            this.cancellationDirectives = Collections.unmodifiableList(
                    new ArrayList<>(cancellationDirectives));
            this.cancellationUnsupportedRequestIds = Collections.unmodifiableList(
                    new ArrayList<>(cancellationUnsupportedRequestIds));
        }

        public List<String> getQueuedExpiredRequestIds() {
            return queuedExpiredRequestIds;
        }

        public List<CancellationDirective> getCancellationDirectives() {
            return cancellationDirectives;
        }

        public List<String> getCancellationUnsupportedRequestIds() {
            return cancellationUnsupportedRequestIds;
        }
    }

    public static final class Settlement {
        private final SettlementOutcome outcome;
        private final ActiveSnapshot active;
        private final ProviderTerminalOutcome providerOutcome;
        private final LocalTerminalState localTerminalState;

        Settlement(
                SettlementOutcome outcome,
                ActiveSnapshot active,
                ProviderTerminalOutcome providerOutcome,
                LocalTerminalState localTerminalState) {
            this.outcome = outcome;
            this.active = active;
            this.providerOutcome = providerOutcome;
            this.localTerminalState = localTerminalState;
        }

        static Settlement rejected(SettlementOutcome outcome) {
            return new Settlement(outcome, null, null, null);
        }

        public SettlementOutcome getOutcome() {
            return outcome;
        }

        public ActiveSnapshot getActive() {
            return active;
        }

        public ProviderTerminalOutcome getProviderOutcome() {
            return providerOutcome;
        }

        public LocalTerminalState getLocalTerminalState() {
            return localTerminalState;
        }
    }

    public static final class ActiveSnapshot {
        private final String requestId;
        private final String ownerFingerprint;
        private final String modelId;
        private final String providerId;
        private final EffectivePriority effectivePriority;
        private final ActiveState state;
        private final CancelReason cancelReason;
        private final long admittedAtElapsedRealtimeMs;
        private final long queueDeadlineElapsedRealtimeMs;
        private final long taskDeadlineElapsedRealtimeMs;
        private final long startedAtElapsedRealtimeMs;

        ActiveSnapshot(
                String requestId,
                String ownerFingerprint,
                String modelId,
                String providerId,
                EffectivePriority effectivePriority,
                ActiveState state,
                CancelReason cancelReason,
                long admittedAtElapsedRealtimeMs,
                long queueDeadlineElapsedRealtimeMs,
                long taskDeadlineElapsedRealtimeMs,
                long startedAtElapsedRealtimeMs) {
            this.requestId = requestId;
            this.ownerFingerprint = ownerFingerprint;
            this.modelId = modelId;
            this.providerId = providerId;
            this.effectivePriority = effectivePriority;
            this.state = state;
            this.cancelReason = cancelReason;
            this.admittedAtElapsedRealtimeMs = admittedAtElapsedRealtimeMs;
            this.queueDeadlineElapsedRealtimeMs = queueDeadlineElapsedRealtimeMs;
            this.taskDeadlineElapsedRealtimeMs = taskDeadlineElapsedRealtimeMs;
            this.startedAtElapsedRealtimeMs = startedAtElapsedRealtimeMs;
        }

        public String getRequestId() {
            return requestId;
        }

        public String getOwnerFingerprint() {
            return ownerFingerprint;
        }

        public String getModelId() {
            return modelId;
        }

        public String getProviderId() {
            return providerId;
        }

        public EffectivePriority getEffectivePriority() {
            return effectivePriority;
        }

        public ActiveState getState() {
            return state;
        }

        public CancelReason getCancelReason() {
            return cancelReason;
        }

        public long getAdmittedAtElapsedRealtimeMs() {
            return admittedAtElapsedRealtimeMs;
        }

        public long getQueueDeadlineElapsedRealtimeMs() {
            return queueDeadlineElapsedRealtimeMs;
        }

        public long getTaskDeadlineElapsedRealtimeMs() {
            return taskDeadlineElapsedRealtimeMs;
        }

        public long getStartedAtElapsedRealtimeMs() {
            return startedAtElapsedRealtimeMs;
        }
    }

    public static final class SchedulerSnapshot {
        private final int activeCount;
        private final int queuedCount;
        private final int runningSlotCount;

        SchedulerSnapshot(int activeCount, int queuedCount, int runningSlotCount) {
            this.activeCount = activeCount;
            this.queuedCount = queuedCount;
            this.runningSlotCount = runningSlotCount;
        }

        public int getActiveCount() {
            return activeCount;
        }

        public int getQueuedCount() {
            return queuedCount;
        }

        public int getRunningSlotCount() {
            return runningSlotCount;
        }
    }
}
