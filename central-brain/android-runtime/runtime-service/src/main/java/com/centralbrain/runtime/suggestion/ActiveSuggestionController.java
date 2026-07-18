package com.centralbrain.runtime.suggestion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Process-local active-suggestion projection policy.
 *
 * <p>This class owns presentation, merge, cooldown, and "never ask" behavior only. It does not
 * authorize a scenario, execute a graph, dispatch an effect, persist a preference, or synthesize
 * speech.</p>
 */
public final class ActiveSuggestionController {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_ACTIVE_SUGGESTIONS = 16;
    public static final int MAX_REPLAY_ENTRIES = 64;
    public static final int MAX_NEVER_ASK_SCOPES = 64;
    public static final long MAX_SUGGESTION_VALIDITY_MS = 300_000L;
    public static final long MAX_COOLDOWN_MS = 86_400_000L;

    private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern CANONICAL_ID =
            Pattern.compile("^[a-z0-9](?:[a-z0-9._-]{0,94}[a-z0-9])?$");

    public enum DrivingState {
        PARKED,
        MOVING,
        UNKNOWN
    }

    public enum PresentationMode {
        EMPTY,
        FULL_CARD,
        MINIMAL_BANNER
    }

    public enum SuggestedAction {
        REVIEW,
        DISMISS,
        NEVER_ASK
    }

    public enum ReasonCode {
        CABIN_TOO_COLD(30, "suggestion.why.cabin_too_cold", "suggestion.plan.cabin_warmth",
                "suggestion.voice.cabin_comfort"),
        DRIVER_FATIGUE(100, "suggestion.why.driver_fatigue", "suggestion.plan.fatigue_care",
                "suggestion.voice.fatigue_care"),
        DRIVER_ATTENTION_LOW(90, "suggestion.why.driver_attention_low",
                "suggestion.plan.attention_recovery", "suggestion.voice.attention_recovery"),
        CABIN_AIR_QUALITY(40, "suggestion.why.cabin_air_quality",
                "suggestion.plan.air_refresh", "suggestion.voice.air_refresh"),
        RUNTIME_DEGRADED(80, "suggestion.why.runtime_degraded",
                "suggestion.plan.runtime_recovery", "suggestion.voice.runtime_recovery");

        private final int priority;
        private final String whyKey;
        private final String planKey;
        private final String minimalVoiceKey;

        ReasonCode(int priority, String whyKey, String planKey, String minimalVoiceKey) {
            this.priority = priority;
            this.whyKey = whyKey;
            this.planKey = planKey;
            this.minimalVoiceKey = minimalVoiceKey;
        }

        public int getPriority() {
            return priority;
        }

        public String getWhyKey() {
            return whyKey;
        }

        public String getPlanKey() {
            return planKey;
        }

        public String getMinimalVoiceKey() {
            return minimalVoiceKey;
        }
    }

    public enum IngestCode {
        ADDED,
        MERGED,
        REPLAYED,
        SUPPRESSED_COOLDOWN,
        SUPPRESSED_NEVER_ASK,
        EXPIRED,
        REJECTED_FUTURE,
        REQUEST_CONFLICT,
        CAPACITY_EXCEEDED
    }

    public enum ActionCode {
        APPLIED,
        NOT_FOUND,
        DRIVING_RESTRICTED,
        CAPACITY_EXCEEDED
    }

    public interface ElapsedRealtimeClock {
        long nowMs();
    }

    public static final class Candidate {
        private final String suggestionId;
        private final String payloadFingerprint;
        private final String ownerFingerprint;
        private final String scenarioId;
        private final String zoneId;
        private final ReasonCode reason;
        private final long observedAtElapsedMs;
        private final long expiresAtElapsedMs;
        private final long cooldownMs;
        private final String evidenceFingerprint;

        private Candidate(
                String suggestionId,
                String payloadFingerprint,
                String ownerFingerprint,
                String scenarioId,
                String zoneId,
                ReasonCode reason,
                long observedAtElapsedMs,
                long expiresAtElapsedMs,
                long cooldownMs,
                String evidenceFingerprint) {
            this.suggestionId = requireCanonicalId(suggestionId, "suggestionId");
            this.payloadFingerprint = requireDigest(payloadFingerprint, "payloadFingerprint");
            this.ownerFingerprint = requireDigest(ownerFingerprint, "ownerFingerprint");
            this.scenarioId = requireCanonicalId(scenarioId, "scenarioId");
            this.zoneId = requireCanonicalId(zoneId, "zoneId");
            this.reason = Objects.requireNonNull(reason, "reason");
            if (observedAtElapsedMs < 0
                    || expiresAtElapsedMs < observedAtElapsedMs
                    || expiresAtElapsedMs - observedAtElapsedMs > MAX_SUGGESTION_VALIDITY_MS) {
                throw new IllegalArgumentException("invalid suggestion validity");
            }
            if (cooldownMs < 0 || cooldownMs > MAX_COOLDOWN_MS) {
                throw new IllegalArgumentException("invalid cooldown");
            }
            this.observedAtElapsedMs = observedAtElapsedMs;
            this.expiresAtElapsedMs = expiresAtElapsedMs;
            this.cooldownMs = cooldownMs;
            this.evidenceFingerprint = requireDigest(
                    evidenceFingerprint, "evidenceFingerprint");
        }

        public static Candidate create(
                String suggestionId,
                String payloadFingerprint,
                String ownerFingerprint,
                String scenarioId,
                String zoneId,
                ReasonCode reason,
                long observedAtElapsedMs,
                long expiresAtElapsedMs,
                long cooldownMs,
                String evidenceFingerprint) {
            return new Candidate(
                    suggestionId,
                    payloadFingerprint,
                    ownerFingerprint,
                    scenarioId,
                    zoneId,
                    reason,
                    observedAtElapsedMs,
                    expiresAtElapsedMs,
                    cooldownMs,
                    evidenceFingerprint);
        }

        public String getSuggestionId() {
            return suggestionId;
        }

        private String mergeKey() {
            return ownerFingerprint + ":" + scenarioId + ":" + zoneId;
        }

        private String digestMaterial() {
            return suggestionId + ":" + payloadFingerprint + ":" + ownerFingerprint + ":"
                    + scenarioId + ":" + zoneId + ":" + reason + ":"
                    + observedAtElapsedMs + ":" + expiresAtElapsedMs + ":" + cooldownMs + ":"
                    + evidenceFingerprint;
        }
    }

    public static final class IngestResult {
        private final IngestCode code;
        private final long revision;
        private final int mergedCount;
        private final long cooldownRemainingMs;

        private IngestResult(
                IngestCode code,
                long revision,
                int mergedCount,
                long cooldownRemainingMs) {
            this.code = code;
            this.revision = revision;
            this.mergedCount = mergedCount;
            this.cooldownRemainingMs = cooldownRemainingMs;
        }

        public IngestCode getCode() {
            return code;
        }

        public long getRevision() {
            return revision;
        }

        public int getMergedCount() {
            return mergedCount;
        }

        public long getCooldownRemainingMs() {
            return cooldownRemainingMs;
        }

        private IngestResult asReplay() {
            return new IngestResult(
                    IngestCode.REPLAYED, revision, mergedCount, cooldownRemainingMs);
        }
    }

    public static final class ActionResult {
        private final ActionCode code;
        private final long revision;

        private ActionResult(ActionCode code, long revision) {
            this.code = code;
            this.revision = revision;
        }

        public ActionCode getCode() {
            return code;
        }

        public long getRevision() {
            return revision;
        }

        public boolean isPreferencePersisted() {
            return false;
        }

        public boolean isEffectDispatched() {
            return false;
        }
    }

    public static final class SuggestionCard {
        private final String suggestionId;
        private final ReasonCode reason;
        private final int mergedCount;
        private final long cooldownMs;
        private final List<SuggestedAction> actions;

        private SuggestionCard(
                String suggestionId,
                ReasonCode reason,
                int mergedCount,
                long cooldownMs,
                List<SuggestedAction> actions) {
            this.suggestionId = suggestionId;
            this.reason = reason;
            this.mergedCount = mergedCount;
            this.cooldownMs = cooldownMs;
            this.actions = Collections.unmodifiableList(new ArrayList<>(actions));
        }

        public String getSuggestionId() {
            return suggestionId;
        }

        public ReasonCode getReason() {
            return reason;
        }

        public String getWhyKey() {
            return reason.getWhyKey();
        }

        public String getPlanKey() {
            return reason.getPlanKey();
        }

        public int getMergedCount() {
            return mergedCount;
        }

        public long getCooldownMs() {
            return cooldownMs;
        }

        public List<SuggestedAction> getActions() {
            return actions;
        }
    }

    public static final class HmiSnapshot {
        private final long revision;
        private final DrivingState drivingState;
        private final PresentationMode presentationMode;
        private final List<SuggestionCard> cards;
        private final String minimalVoiceKey;

        private HmiSnapshot(
                long revision,
                DrivingState drivingState,
                PresentationMode presentationMode,
                List<SuggestionCard> cards,
                String minimalVoiceKey) {
            this.revision = revision;
            this.drivingState = drivingState;
            this.presentationMode = presentationMode;
            this.cards = Collections.unmodifiableList(new ArrayList<>(cards));
            this.minimalVoiceKey = minimalVoiceKey;
        }

        public long getRevision() {
            return revision;
        }

        public DrivingState getDrivingState() {
            return drivingState;
        }

        public PresentationMode getPresentationMode() {
            return presentationMode;
        }

        public List<SuggestionCard> getCards() {
            return cards;
        }

        public String getMinimalVoiceKey() {
            return minimalVoiceKey;
        }

        public boolean isVoiceSynthesisRequested() {
            return false;
        }
    }

    private static final class ActiveState {
        final String mergeKey;
        final String ownerFingerprint;
        final String scenarioId;
        final String zoneId;
        final Set<String> memberIds = new HashSet<>();
        String primarySuggestionId;
        ReasonCode reason;
        long observedAtElapsedMs;
        long expiresAtElapsedMs;
        long cooldownMs;
        int mergedCount;

        ActiveState(Candidate candidate) {
            mergeKey = candidate.mergeKey();
            ownerFingerprint = candidate.ownerFingerprint;
            scenarioId = candidate.scenarioId;
            zoneId = candidate.zoneId;
            primarySuggestionId = candidate.suggestionId;
            reason = candidate.reason;
            observedAtElapsedMs = candidate.observedAtElapsedMs;
            expiresAtElapsedMs = candidate.expiresAtElapsedMs;
            cooldownMs = candidate.cooldownMs;
            mergedCount = 1;
            memberIds.add(candidate.suggestionId);
        }

        void merge(Candidate candidate) {
            memberIds.add(candidate.suggestionId);
            mergedCount++;
            if (candidate.reason.getPriority() > reason.getPriority()
                    || (candidate.reason.getPriority() == reason.getPriority()
                    && candidate.observedAtElapsedMs > observedAtElapsedMs)) {
                primarySuggestionId = candidate.suggestionId;
                reason = candidate.reason;
            }
            observedAtElapsedMs = Math.max(observedAtElapsedMs, candidate.observedAtElapsedMs);
            expiresAtElapsedMs = Math.max(expiresAtElapsedMs, candidate.expiresAtElapsedMs);
            cooldownMs = Math.max(cooldownMs, candidate.cooldownMs);
        }
    }

    private static final class ReplayEntry {
        final String digestMaterial;
        final IngestResult result;

        ReplayEntry(String digestMaterial, IngestResult result) {
            this.digestMaterial = digestMaterial;
            this.result = result;
        }
    }

    private final ElapsedRealtimeClock clock;
    private final Map<String, ActiveState> activeByMergeKey = new LinkedHashMap<>();
    private final Map<String, ReplayEntry> replayBySuggestionId = new LinkedHashMap<>();
    private final Map<String, Long> cooldownByMergeKey = new LinkedHashMap<>();
    private final Set<String> neverAskScopes = new HashSet<>();
    private long revision;

    private ActiveSuggestionController(ElapsedRealtimeClock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static ActiveSuggestionController createForContractTest(ElapsedRealtimeClock clock) {
        return new ActiveSuggestionController(clock);
    }

    public synchronized IngestResult ingest(Candidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        ReplayEntry replay = replayBySuggestionId.get(candidate.suggestionId);
        if (replay != null) {
            if (replay.digestMaterial.equals(candidate.digestMaterial())) {
                return replay.result.asReplay();
            }
            return new IngestResult(IngestCode.REQUEST_CONFLICT, revision, 0, 0);
        }

        long now = clock.nowMs();
        removeExpired(now);
        IngestResult result;
        if (candidate.observedAtElapsedMs > now) {
            result = new IngestResult(IngestCode.REJECTED_FUTURE, revision, 0, 0);
        } else if (candidate.expiresAtElapsedMs <= now) {
            result = new IngestResult(IngestCode.EXPIRED, revision, 0, 0);
        } else if (neverAskScopes.contains(candidate.mergeKey())) {
            result = new IngestResult(IngestCode.SUPPRESSED_NEVER_ASK, revision, 0, 0);
        } else {
            long cooldownUntil = cooldownByMergeKey.containsKey(candidate.mergeKey())
                    ? cooldownByMergeKey.get(candidate.mergeKey()) : 0L;
            if (cooldownUntil > now) {
                result = new IngestResult(
                        IngestCode.SUPPRESSED_COOLDOWN,
                        revision,
                        0,
                        cooldownUntil - now);
            } else {
                cooldownByMergeKey.remove(candidate.mergeKey());
                ActiveState active = activeByMergeKey.get(candidate.mergeKey());
                if (active != null) {
                    active.merge(candidate);
                    revision++;
                    result = new IngestResult(
                            IngestCode.MERGED, revision, active.mergedCount, 0);
                } else if (activeByMergeKey.size() >= MAX_ACTIVE_SUGGESTIONS) {
                    result = new IngestResult(IngestCode.CAPACITY_EXCEEDED, revision, 0, 0);
                } else {
                    active = new ActiveState(candidate);
                    activeByMergeKey.put(active.mergeKey, active);
                    revision++;
                    result = new IngestResult(IngestCode.ADDED, revision, 1, 0);
                }
            }
        }
        remember(candidate, result);
        return result;
    }

    public synchronized ActionResult dismiss(
            String ownerFingerprint, String suggestionId, DrivingState drivingState) {
        requireDigest(ownerFingerprint, "ownerFingerprint");
        requireCanonicalId(suggestionId, "suggestionId");
        Objects.requireNonNull(drivingState, "drivingState");
        long now = clock.nowMs();
        removeExpired(now);
        ActiveState active = find(ownerFingerprint, suggestionId);
        if (active == null) {
            return new ActionResult(ActionCode.NOT_FOUND, revision);
        }
        activeByMergeKey.remove(active.mergeKey);
        cooldownByMergeKey.put(active.mergeKey, now + active.cooldownMs);
        revision++;
        return new ActionResult(ActionCode.APPLIED, revision);
    }

    public synchronized ActionResult neverAsk(
            String ownerFingerprint, String suggestionId, DrivingState drivingState) {
        requireDigest(ownerFingerprint, "ownerFingerprint");
        requireCanonicalId(suggestionId, "suggestionId");
        Objects.requireNonNull(drivingState, "drivingState");
        if (drivingState != DrivingState.PARKED) {
            return new ActionResult(ActionCode.DRIVING_RESTRICTED, revision);
        }
        removeExpired(clock.nowMs());
        ActiveState active = find(ownerFingerprint, suggestionId);
        if (active == null) {
            return new ActionResult(ActionCode.NOT_FOUND, revision);
        }
        if (!neverAskScopes.contains(active.mergeKey)
                && neverAskScopes.size() >= MAX_NEVER_ASK_SCOPES) {
            return new ActionResult(ActionCode.CAPACITY_EXCEEDED, revision);
        }
        neverAskScopes.add(active.mergeKey);
        activeByMergeKey.remove(active.mergeKey);
        cooldownByMergeKey.remove(active.mergeKey);
        revision++;
        return new ActionResult(ActionCode.APPLIED, revision);
    }

    public synchronized HmiSnapshot snapshot(
            String ownerFingerprint, DrivingState drivingState) {
        requireDigest(ownerFingerprint, "ownerFingerprint");
        Objects.requireNonNull(drivingState, "drivingState");
        removeExpired(clock.nowMs());
        List<ActiveState> visible = new ArrayList<>();
        for (ActiveState active : activeByMergeKey.values()) {
            if (active.ownerFingerprint.equals(ownerFingerprint)) {
                visible.add(active);
            }
        }
        visible.sort(Comparator
                .comparingInt((ActiveState value) -> value.reason.getPriority()).reversed()
                .thenComparing(Comparator
                        .comparingLong((ActiveState value) -> value.observedAtElapsedMs)
                        .reversed())
                .thenComparing(value -> value.primarySuggestionId));
        if (visible.isEmpty()) {
            return new HmiSnapshot(
                    revision, drivingState, PresentationMode.EMPTY,
                    Collections.emptyList(), null);
        }

        boolean minimal = drivingState != DrivingState.PARKED;
        int count = minimal ? 1 : visible.size();
        List<SuggestionCard> cards = new ArrayList<>(count);
        List<SuggestedAction> actions = minimal
                ? Collections.singletonList(SuggestedAction.DISMISS)
                : List.of(
                        SuggestedAction.REVIEW,
                        SuggestedAction.DISMISS,
                        SuggestedAction.NEVER_ASK);
        for (int index = 0; index < count; index++) {
            ActiveState active = visible.get(index);
            cards.add(new SuggestionCard(
                    active.primarySuggestionId,
                    active.reason,
                    active.mergedCount,
                    active.cooldownMs,
                    actions));
        }
        return new HmiSnapshot(
                revision,
                drivingState,
                minimal ? PresentationMode.MINIMAL_BANNER : PresentationMode.FULL_CARD,
                cards,
                minimal ? cards.get(0).reason.getMinimalVoiceKey() : null);
    }

    public boolean isHmiProjectionOnly() {
        return true;
    }

    public boolean isProductionSuggestionSourceWired() {
        return false;
    }

    public boolean isTriggerEngineWired() {
        return false;
    }

    public boolean isGraphRuntimeWired() {
        return false;
    }

    public boolean isEffectDispatchWired() {
        return false;
    }

    public boolean isVoiceEngineWired() {
        return false;
    }

    public boolean isPreferenceRepositoryWired() {
        return false;
    }

    public boolean isHardwareAccessed() {
        return false;
    }

    private ActiveState find(String ownerFingerprint, String suggestionId) {
        for (ActiveState active : activeByMergeKey.values()) {
            if (active.ownerFingerprint.equals(ownerFingerprint)
                    && active.memberIds.contains(suggestionId)) {
                return active;
            }
        }
        return null;
    }

    private void removeExpired(long now) {
        List<String> expired = new ArrayList<>();
        for (ActiveState active : activeByMergeKey.values()) {
            if (active.expiresAtElapsedMs <= now) {
                expired.add(active.mergeKey);
            }
        }
        for (String key : expired) {
            activeByMergeKey.remove(key);
            revision++;
        }
    }

    private void remember(Candidate candidate, IngestResult result) {
        if (replayBySuggestionId.size() >= MAX_REPLAY_ENTRIES) {
            String first = replayBySuggestionId.keySet().iterator().next();
            replayBySuggestionId.remove(first);
        }
        replayBySuggestionId.put(
                candidate.suggestionId,
                new ReplayEntry(candidate.digestMaterial(), result));
    }

    private static String requireCanonicalId(String value, String field) {
        if (value == null || !CANONICAL_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be canonical");
        }
        return value;
    }

    private static String requireDigest(String value, String field) {
        if (value == null || !SHA256.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a lowercase SHA-256");
        }
        return value;
    }
}
