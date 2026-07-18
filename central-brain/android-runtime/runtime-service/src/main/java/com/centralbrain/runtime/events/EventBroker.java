package com.centralbrain.runtime.events;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Typed process-local Event Broker contract. It does not publish a middleware transport. */
public interface EventBroker {
    int SCHEMA_VERSION = 1;
    long MAX_EVIDENCE_VALIDITY_MS = 300_000L;

    Topic<TaskStatePayload> TASK_STATE_TOPIC = Topic.create(
            "runtime.task.state.v2",
            "centralbrain.event.task-state.v1",
            TaskStatePayload.class,
            EnumSet.of(
                    EventKind.TASK_CREATED,
                    EventKind.TASK_RUNNING,
                    EventKind.TASK_WAITING,
                    EventKind.TASK_TERMINAL_SUCCESS,
                    EventKind.TASK_TERMINAL_FAILURE));
    Topic<PolicyDecisionPayload> POLICY_DECISION_TOPIC = Topic.create(
            "governance.policy.decision.v2",
            "centralbrain.event.policy-decision.v1",
            PolicyDecisionPayload.class,
            EnumSet.of(
                    EventKind.POLICY_ALLOW,
                    EventKind.POLICY_DENY,
                    EventKind.POLICY_REQUIRE_APPROVAL));
    Topic<ModelHealthPayload> MODEL_HEALTH_TOPIC = Topic.create(
            "model.runtime.health.v2",
            "centralbrain.event.model-health.v1",
            ModelHealthPayload.class,
            EnumSet.of(
                    EventKind.MODEL_HEALTHY,
                    EventKind.MODEL_DEGRADED,
                    EventKind.MODEL_UNAVAILABLE));

    PublishResult publish(PublishRequest<?> request, AccessEvidence evidence);

    SubscribeResult subscribe(
            SubscriptionRequest request,
            EventConsumer consumer,
            AccessEvidence evidence);

    EventPage replay(ReplayRequest request, AccessEvidence evidence);

    CancelResult cancel(EventSubscription.Handle handle, AccessEvidence evidence);

    BrokerSnapshot snapshot();

    enum Operation {
        PUBLISH,
        SUBSCRIBE,
        REPLAY,
        CANCEL
    }

    enum AccessDecision {
        ALLOWED,
        DENIED,
        UNAVAILABLE
    }

    enum EventKind {
        TASK_CREATED,
        TASK_RUNNING,
        TASK_WAITING,
        TASK_TERMINAL_SUCCESS,
        TASK_TERMINAL_FAILURE,
        POLICY_ALLOW,
        POLICY_DENY,
        POLICY_REQUIRE_APPROVAL,
        MODEL_HEALTHY,
        MODEL_DEGRADED,
        MODEL_UNAVAILABLE
    }

    enum TaskState {
        CREATED,
        RUNNING,
        WAITING,
        TERMINAL_SUCCESS,
        TERMINAL_FAILURE
    }

    enum PolicyDecision {
        ALLOW,
        DENY,
        REQUIRE_APPROVAL
    }

    enum ModelHealth {
        HEALTHY,
        DEGRADED,
        UNAVAILABLE
    }

    enum PublishCode {
        PUBLISHED,
        REPLAYED,
        REQUEST_CONFLICT,
        EVIDENCE_INVALID,
        ACCESS_DENIED,
        AUTHORITY_UNAVAILABLE,
        SEQUENCE_EXHAUSTED
    }

    enum SubscribeCode {
        SUBSCRIBED,
        REPLAYED,
        REQUEST_CONFLICT,
        EVIDENCE_INVALID,
        ACCESS_DENIED,
        AUTHORITY_UNAVAILABLE,
        FUTURE_CURSOR,
        CURSOR_GAP,
        SUBSCRIPTION_LIMIT
    }

    enum ReplayCode {
        OK,
        EVIDENCE_INVALID,
        ACCESS_DENIED,
        AUTHORITY_UNAVAILABLE,
        FUTURE_CURSOR,
        CURSOR_GAP
    }

    enum CancelCode {
        CANCELLED,
        ALREADY_CANCELLED,
        EVIDENCE_INVALID,
        ACCESS_DENIED,
        AUTHORITY_UNAVAILABLE,
        NOT_FOUND_OR_NOT_OWNER
    }

    interface Payload {
        String getSubjectDigest();

        EventKind getKind();

        String getPayloadDigest();
    }

    interface EventConsumer {
        void onEvent(EventRecord event);
    }

    interface AccessAuthority {
        AccessDecision authorize(
                Operation operation,
                String topicId,
                AccessEvidence evidence);
    }

    final class Topic<T extends Payload> {
        private final String topicId;
        private final String schemaId;
        private final Class<T> payloadClass;
        private final Set<EventKind> supportedKinds;

        private Topic(
                String topicId,
                String schemaId,
                Class<T> payloadClass,
                Set<EventKind> supportedKinds) {
            this.topicId = requireMetadata(topicId, "topicId", 96);
            this.schemaId = requireMetadata(schemaId, "schemaId", 96);
            this.payloadClass = Objects.requireNonNull(payloadClass, "payloadClass");
            if (supportedKinds == null || supportedKinds.isEmpty()) {
                throw new IllegalArgumentException("supportedKinds must not be empty");
            }
            this.supportedKinds = Collections.unmodifiableSet(EnumSet.copyOf(supportedKinds));
        }

        private static <T extends Payload> Topic<T> create(
                String topicId,
                String schemaId,
                Class<T> payloadClass,
                Set<EventKind> supportedKinds) {
            return new Topic<>(topicId, schemaId, payloadClass, supportedKinds);
        }

        public String getTopicId() {
            return topicId;
        }

        public String getSchemaId() {
            return schemaId;
        }

        public Class<T> getPayloadClass() {
            return payloadClass;
        }

        public Set<EventKind> getSupportedKinds() {
            return supportedKinds;
        }

        void validate(Payload payload) {
            if (!payloadClass.isInstance(payload)
                    || !supportedKinds.contains(payload.getKind())) {
                throw new IllegalArgumentException("payload does not match typed topic");
            }
        }
    }

    final class TaskStatePayload implements Payload {
        private final String subjectDigest;
        private final TaskState state;
        private final String payloadDigest;

        public TaskStatePayload(String subjectDigest, TaskState state) {
            this.subjectDigest = requireDigest(subjectDigest, "subjectDigest");
            this.state = Objects.requireNonNull(state, "state");
            this.payloadDigest = digest("task-state|" + subjectDigest + "|" + state.name());
        }

        @Override
        public String getSubjectDigest() {
            return subjectDigest;
        }

        public TaskState getState() {
            return state;
        }

        @Override
        public EventKind getKind() {
            return EventKind.valueOf("TASK_" + state.name());
        }

        @Override
        public String getPayloadDigest() {
            return payloadDigest;
        }
    }

    final class PolicyDecisionPayload implements Payload {
        private final String subjectDigest;
        private final PolicyDecision decision;
        private final String payloadDigest;

        public PolicyDecisionPayload(String subjectDigest, PolicyDecision decision) {
            this.subjectDigest = requireDigest(subjectDigest, "subjectDigest");
            this.decision = Objects.requireNonNull(decision, "decision");
            this.payloadDigest = digest(
                    "policy-decision|" + subjectDigest + "|" + decision.name());
        }

        @Override
        public String getSubjectDigest() {
            return subjectDigest;
        }

        public PolicyDecision getDecision() {
            return decision;
        }

        @Override
        public EventKind getKind() {
            return EventKind.valueOf("POLICY_" + decision.name());
        }

        @Override
        public String getPayloadDigest() {
            return payloadDigest;
        }
    }

    final class ModelHealthPayload implements Payload {
        private final String subjectDigest;
        private final ModelHealth health;
        private final String payloadDigest;

        public ModelHealthPayload(String subjectDigest, ModelHealth health) {
            this.subjectDigest = requireDigest(subjectDigest, "subjectDigest");
            this.health = Objects.requireNonNull(health, "health");
            this.payloadDigest = digest(
                    "model-health|" + subjectDigest + "|" + health.name());
        }

        @Override
        public String getSubjectDigest() {
            return subjectDigest;
        }

        public ModelHealth getHealth() {
            return health;
        }

        @Override
        public EventKind getKind() {
            return EventKind.valueOf("MODEL_" + health.name());
        }

        @Override
        public String getPayloadDigest() {
            return payloadDigest;
        }
    }

    final class PublishRequest<T extends Payload> {
        private final String requestId;
        private final Topic<T> topic;
        private final T payload;

        public PublishRequest(String requestId, Topic<T> topic, T payload) {
            this.requestId = requireMetadata(requestId, "requestId", 128);
            this.topic = requireKnownTopic(topic);
            this.payload = Objects.requireNonNull(payload, "payload");
            topic.validate(payload);
        }

        public String getRequestId() {
            return requestId;
        }

        public Topic<T> getTopic() {
            return topic;
        }

        public T getPayload() {
            return payload;
        }

        String requestDigest() {
            return digest("publish|" + requestId + "|" + topic.topicId + "|"
                    + payload.getPayloadDigest());
        }
    }

    final class EventFilter {
        private static final int MAX_SUBJECTS = 16;

        private final Set<EventKind> kinds;
        private final Set<String> subjectDigests;

        public EventFilter(Set<EventKind> kinds, Set<String> subjectDigests) {
            if (kinds == null || kinds.isEmpty()) {
                this.kinds = Collections.emptySet();
            } else {
                this.kinds = Collections.unmodifiableSet(EnumSet.copyOf(kinds));
            }
            if (subjectDigests == null || subjectDigests.isEmpty()) {
                this.subjectDigests = Collections.emptySet();
            } else {
                if (subjectDigests.size() > MAX_SUBJECTS) {
                    throw new IllegalArgumentException("subject filter exceeds limit");
                }
                LinkedHashSet<String> copy = new LinkedHashSet<>();
                for (String subjectDigest : subjectDigests) {
                    copy.add(requireDigest(subjectDigest, "subjectDigest"));
                }
                this.subjectDigests = Collections.unmodifiableSet(copy);
            }
        }

        public static EventFilter all() {
            return new EventFilter(Collections.emptySet(), Collections.emptySet());
        }

        public Set<EventKind> getKinds() {
            return kinds;
        }

        public Set<String> getSubjectDigests() {
            return subjectDigests;
        }

        boolean matches(EventRecord record) {
            return (kinds.isEmpty() || kinds.contains(record.getKind()))
                    && (subjectDigests.isEmpty()
                    || subjectDigests.contains(record.getSubjectDigest()));
        }

        void validateFor(Topic<?> topic) {
            if (!topic.supportedKinds.containsAll(kinds)) {
                throw new IllegalArgumentException("filter kind does not match topic");
            }
        }

        String canonicalDigest() {
            List<String> kindNames = new ArrayList<>();
            for (EventKind kind : kinds) {
                kindNames.add(kind.name());
            }
            Collections.sort(kindNames);
            List<String> subjects = new ArrayList<>(subjectDigests);
            Collections.sort(subjects);
            return digest("filter|" + String.join(",", kindNames) + "|"
                    + String.join(",", subjects));
        }
    }

    final class SubscriptionRequest {
        private final String clientSubscriptionId;
        private final Topic<?> topic;
        private final long afterCursor;
        private final EventFilter filter;

        public SubscriptionRequest(
                String clientSubscriptionId,
                Topic<?> topic,
                long afterCursor,
                EventFilter filter) {
            this.clientSubscriptionId = requireMetadata(
                    clientSubscriptionId,
                    "clientSubscriptionId",
                    128);
            this.topic = requireKnownTopic(topic);
            if (afterCursor < 0) {
                throw new IllegalArgumentException("afterCursor must be non-negative");
            }
            this.afterCursor = afterCursor;
            this.filter = Objects.requireNonNull(filter, "filter");
            filter.validateFor(topic);
        }

        public String getClientSubscriptionId() {
            return clientSubscriptionId;
        }

        public Topic<?> getTopic() {
            return topic;
        }

        public long getAfterCursor() {
            return afterCursor;
        }

        public EventFilter getFilter() {
            return filter;
        }

        String requestDigest() {
            return digest("subscribe|" + clientSubscriptionId + "|" + topic.topicId
                    + "|" + afterCursor + "|" + filter.canonicalDigest());
        }
    }

    final class ReplayRequest {
        private final Topic<?> topic;
        private final long afterCursor;
        private final int limit;
        private final EventFilter filter;

        public ReplayRequest(
                Topic<?> topic,
                long afterCursor,
                int limit,
                EventFilter filter) {
            this.topic = requireKnownTopic(topic);
            if (afterCursor < 0 || limit < 1) {
                throw new IllegalArgumentException("replay cursor or limit is invalid");
            }
            this.afterCursor = afterCursor;
            this.limit = limit;
            this.filter = Objects.requireNonNull(filter, "filter");
            filter.validateFor(topic);
        }

        public Topic<?> getTopic() {
            return topic;
        }

        public long getAfterCursor() {
            return afterCursor;
        }

        public int getLimit() {
            return limit;
        }

        public EventFilter getFilter() {
            return filter;
        }
    }

    final class AccessEvidence {
        private final String ownerFingerprint;
        private final Operation operation;
        private final String topicId;
        private final long issuedAtElapsedRealtimeMs;
        private final long expiresAtElapsedRealtimeMs;
        private final String identityEvidenceDigest;
        private final String policyEvidenceDigest;

        public AccessEvidence(
                String ownerFingerprint,
                Operation operation,
                String topicId,
                long issuedAtElapsedRealtimeMs,
                long expiresAtElapsedRealtimeMs,
                String identityEvidenceDigest,
                String policyEvidenceDigest) {
            this.ownerFingerprint = requireDigest(ownerFingerprint, "ownerFingerprint");
            this.operation = Objects.requireNonNull(operation, "operation");
            this.topicId = requireMetadata(topicId, "topicId", 96);
            if (issuedAtElapsedRealtimeMs < 0
                    || expiresAtElapsedRealtimeMs <= issuedAtElapsedRealtimeMs
                    || expiresAtElapsedRealtimeMs - issuedAtElapsedRealtimeMs
                    > MAX_EVIDENCE_VALIDITY_MS) {
                throw new IllegalArgumentException("evidence validity is invalid");
            }
            this.issuedAtElapsedRealtimeMs = issuedAtElapsedRealtimeMs;
            this.expiresAtElapsedRealtimeMs = expiresAtElapsedRealtimeMs;
            this.identityEvidenceDigest = requireDigest(
                    identityEvidenceDigest,
                    "identityEvidenceDigest");
            this.policyEvidenceDigest = requireDigest(
                    policyEvidenceDigest,
                    "policyEvidenceDigest");
        }

        public String getOwnerFingerprint() {
            return ownerFingerprint;
        }

        public Operation getOperation() {
            return operation;
        }

        public String getTopicId() {
            return topicId;
        }

        public long getIssuedAtElapsedRealtimeMs() {
            return issuedAtElapsedRealtimeMs;
        }

        public long getExpiresAtElapsedRealtimeMs() {
            return expiresAtElapsedRealtimeMs;
        }

        public String getIdentityEvidenceDigest() {
            return identityEvidenceDigest;
        }

        public String getPolicyEvidenceDigest() {
            return policyEvidenceDigest;
        }

        boolean matches(Operation expectedOperation, Topic<?> expectedTopic, long now) {
            return operation == expectedOperation
                    && topicId.equals(expectedTopic.topicId)
                    && now >= issuedAtElapsedRealtimeMs
                    && now <= expiresAtElapsedRealtimeMs;
        }
    }

    final class EventRecord {
        private final String topicId;
        private final String schemaId;
        private final long cursor;
        private final long publishedAtElapsedRealtimeMs;
        private final String publisherFingerprint;
        private final String subjectDigest;
        private final EventKind kind;
        private final String payloadDigest;
        private final String eventDigest;

        EventRecord(
                Topic<?> topic,
                long cursor,
                long publishedAtElapsedRealtimeMs,
                String publisherFingerprint,
                Payload payload) {
            this.topicId = topic.topicId;
            this.schemaId = topic.schemaId;
            this.cursor = cursor;
            this.publishedAtElapsedRealtimeMs = publishedAtElapsedRealtimeMs;
            this.publisherFingerprint = publisherFingerprint;
            this.subjectDigest = payload.getSubjectDigest();
            this.kind = payload.getKind();
            this.payloadDigest = payload.getPayloadDigest();
            this.eventDigest = digest("event|" + topicId + "|" + schemaId + "|"
                    + cursor + "|" + publishedAtElapsedRealtimeMs + "|"
                    + publisherFingerprint + "|" + subjectDigest + "|"
                    + kind.name() + "|" + payloadDigest);
        }

        public String getTopicId() {
            return topicId;
        }

        public String getSchemaId() {
            return schemaId;
        }

        public long getCursor() {
            return cursor;
        }

        public long getPublishedAtElapsedRealtimeMs() {
            return publishedAtElapsedRealtimeMs;
        }

        public String getPublisherFingerprint() {
            return publisherFingerprint;
        }

        public String getSubjectDigest() {
            return subjectDigest;
        }

        public EventKind getKind() {
            return kind;
        }

        public String getPayloadDigest() {
            return payloadDigest;
        }

        public String getEventDigest() {
            return eventDigest;
        }
    }

    final class PublishResult {
        private final PublishCode code;
        private final EventRecord event;
        private final int deliveredCount;
        private final int closedConsumerCount;

        PublishResult(
                PublishCode code,
                EventRecord event,
                int deliveredCount,
                int closedConsumerCount) {
            this.code = Objects.requireNonNull(code, "code");
            this.event = event;
            this.deliveredCount = deliveredCount;
            this.closedConsumerCount = closedConsumerCount;
        }

        public PublishCode getCode() {
            return code;
        }

        public EventRecord getEvent() {
            return event;
        }

        public int getDeliveredCount() {
            return deliveredCount;
        }

        public int getClosedConsumerCount() {
            return closedConsumerCount;
        }
    }

    final class SubscribeResult {
        private final SubscribeCode code;
        private final EventSubscription subscription;
        private final long earliestAvailableCursor;
        private final long latestCursor;

        SubscribeResult(
                SubscribeCode code,
                EventSubscription subscription,
                long earliestAvailableCursor,
                long latestCursor) {
            this.code = Objects.requireNonNull(code, "code");
            this.subscription = subscription;
            this.earliestAvailableCursor = earliestAvailableCursor;
            this.latestCursor = latestCursor;
        }

        public SubscribeCode getCode() {
            return code;
        }

        public EventSubscription getSubscription() {
            return subscription;
        }

        public long getEarliestAvailableCursor() {
            return earliestAvailableCursor;
        }

        public long getLatestCursor() {
            return latestCursor;
        }
    }

    final class EventPage {
        private final ReplayCode code;
        private final List<EventRecord> events;
        private final long earliestAvailableCursor;
        private final long latestCursor;
        private final long nextCursor;
        private final boolean hasMore;

        EventPage(
                ReplayCode code,
                List<EventRecord> events,
                long earliestAvailableCursor,
                long latestCursor,
                long nextCursor,
                boolean hasMore) {
            this.code = Objects.requireNonNull(code, "code");
            this.events = Collections.unmodifiableList(new ArrayList<>(events));
            this.earliestAvailableCursor = earliestAvailableCursor;
            this.latestCursor = latestCursor;
            this.nextCursor = nextCursor;
            this.hasMore = hasMore;
        }

        public ReplayCode getCode() {
            return code;
        }

        public List<EventRecord> getEvents() {
            return events;
        }

        public long getEarliestAvailableCursor() {
            return earliestAvailableCursor;
        }

        public long getLatestCursor() {
            return latestCursor;
        }

        public long getNextCursor() {
            return nextCursor;
        }

        public boolean hasMore() {
            return hasMore;
        }
    }

    final class CancelResult {
        private final CancelCode code;
        private final EventSubscription subscription;

        CancelResult(CancelCode code, EventSubscription subscription) {
            this.code = Objects.requireNonNull(code, "code");
            this.subscription = subscription;
        }

        public CancelCode getCode() {
            return code;
        }

        public EventSubscription getSubscription() {
            return subscription;
        }
    }

    final class BrokerSnapshot {
        private final int retainedEventCount;
        private final int activeSubscriptionCount;
        private final long publishedCount;
        private final long deliveredCount;
        private final long callbackFailureCount;
        private final long retentionEvictionCount;

        BrokerSnapshot(
                int retainedEventCount,
                int activeSubscriptionCount,
                long publishedCount,
                long deliveredCount,
                long callbackFailureCount,
                long retentionEvictionCount) {
            this.retainedEventCount = retainedEventCount;
            this.activeSubscriptionCount = activeSubscriptionCount;
            this.publishedCount = publishedCount;
            this.deliveredCount = deliveredCount;
            this.callbackFailureCount = callbackFailureCount;
            this.retentionEvictionCount = retentionEvictionCount;
        }

        public int getRetainedEventCount() {
            return retainedEventCount;
        }

        public int getActiveSubscriptionCount() {
            return activeSubscriptionCount;
        }

        public long getPublishedCount() {
            return publishedCount;
        }

        public long getDeliveredCount() {
            return deliveredCount;
        }

        public long getCallbackFailureCount() {
            return callbackFailureCount;
        }

        public long getRetentionEvictionCount() {
            return retentionEvictionCount;
        }

        public boolean isAppendBeforeNotify() {
            return true;
        }

        public boolean isProcessLocal() {
            return true;
        }

        public boolean isDurablePersistenceWired() {
            return false;
        }

        public boolean isDdsTransportWired() {
            return false;
        }

        public boolean isProductionBrokerPublished() {
            return false;
        }

        public boolean isRuntimeWired() {
            return false;
        }

        public boolean isHardwareAccessed() {
            return false;
        }
    }

    final class Limits {
        private final int retainedEventsPerTopic;
        private final int globalSubscriptions;
        private final int subscriptionsPerOwner;
        private final int maxReplayPage;
        private final int replayTombstones;

        public Limits(
                int retainedEventsPerTopic,
                int globalSubscriptions,
                int subscriptionsPerOwner,
                int maxReplayPage,
                int replayTombstones) {
            if (retainedEventsPerTopic < 1 || retainedEventsPerTopic > 4_096
                    || globalSubscriptions < 1 || globalSubscriptions > 512
                    || subscriptionsPerOwner < 1
                    || subscriptionsPerOwner > globalSubscriptions
                    || maxReplayPage < 1 || maxReplayPage > 256
                    || replayTombstones < 1 || replayTombstones > 1_024) {
                throw new IllegalArgumentException("Event Broker limits are invalid");
            }
            this.retainedEventsPerTopic = retainedEventsPerTopic;
            this.globalSubscriptions = globalSubscriptions;
            this.subscriptionsPerOwner = subscriptionsPerOwner;
            this.maxReplayPage = maxReplayPage;
            this.replayTombstones = replayTombstones;
        }

        public int getRetainedEventsPerTopic() {
            return retainedEventsPerTopic;
        }

        public int getGlobalSubscriptions() {
            return globalSubscriptions;
        }

        public int getSubscriptionsPerOwner() {
            return subscriptionsPerOwner;
        }

        public int getMaxReplayPage() {
            return maxReplayPage;
        }

        public int getReplayTombstones() {
            return replayTombstones;
        }
    }

    @SuppressWarnings("unchecked")
    static <T extends Payload> Topic<T> requireKnownTopic(Topic<T> topic) {
        Objects.requireNonNull(topic, "topic");
        if (topic != TASK_STATE_TOPIC
                && topic != POLICY_DECISION_TOPIC
                && topic != MODEL_HEALTH_TOPIC) {
            throw new IllegalArgumentException("topic is not from the fixed catalog");
        }
        return topic;
    }

    static String requireMetadata(String value, String name, int maxLength) {
        if (value == null
                || value.isEmpty()
                || value.length() > maxLength
                || !value.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    static String requireDigest(String value, String name) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " must be lowercase SHA-256");
        }
        return value;
    }

    static String digest(String value) {
        try {
            byte[] encoded = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : encoded) {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
