package com.centralbrain.runtime.events;

import com.centralbrain.runtime.scenario.ScenarioManifest;
import com.centralbrain.runtime.vehicle.capability.VehicleCapability;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/** Process-local consent policy for proactive suggestion admission. */
public final class ProactiveConsentPolicy {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_GRANTS = 128;
    public static final int MAX_REPLAY_ENTRIES = 256;
    public static final long MAX_GRANT_TTL_MS = 30L * 24L * 60L * 60L * 1_000L;
    public static final long MAX_EVIDENCE_VALIDITY_MS = 300_000L;

    private static final Pattern CANONICAL_ID =
            Pattern.compile("[a-z][a-z0-9]*(?:[._-][a-z0-9]+){1,7}");
    private static final Pattern SCENARIO_ID =
            Pattern.compile("scene[.][a-z0-9][a-z0-9_.-]{2,95}");

    public enum RiskClass {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL;

        boolean isGenericGrantAllowed() {
            return this == LOW || this == MEDIUM;
        }

        boolean includes(RiskClass candidate) {
            return ordinal() >= candidate.ordinal();
        }
    }

    public enum DrivingState {
        PARKED,
        MOVING,
        UNKNOWN
    }

    public enum Operation {
        GRANT_AUTO_EXECUTE,
        REVOKE_AUTO_EXECUTE
    }

    public enum AuthorityDecision {
        ALLOWED,
        DENIED
    }

    public enum MutationCode {
        APPLIED,
        NO_CHANGE,
        REPLAYED,
        REQUEST_CONFLICT,
        INVALID_EVIDENCE,
        DRIVING_RESTRICTED,
        HIGH_RISK_GENERIC_GRANT_FORBIDDEN,
        AUTHORITY_DENIED,
        AUTHORITY_UNAVAILABLE,
        GRANT_ID_CONFLICT,
        NOT_FOUND,
        CAPACITY_EXCEEDED
    }

    public enum AdmissionCode {
        POLICY_ELIGIBLE,
        EXPLICIT_APPROVAL_REQUIRED,
        NO_ACTIVE_GRANT,
        RISK_EXCEEDS_GRANT
    }

    /** Separate owner for creation and revocation of proactive consent. */
    public interface ConsentAuthority {
        AuthorityDecision authorize(ConsentMutation mutation, ConsentEvidence evidence);
    }

    public static final class ConsentMutation {
        private final Operation operation;
        private final String grantId;
        private final String ownerScopeDigest;
        private final String scenarioId;
        private final String scenarioManifestDigest;
        private final VehicleCapability.CapabilityId capabilityId;
        private final ScenarioManifest.Zone zone;
        private final RiskClass maximumRisk;
        private final long ttlMs;
        private final String mutationDigest;

        private ConsentMutation(
                Operation operation,
                String grantId,
                String ownerScopeDigest,
                String scenarioId,
                String scenarioManifestDigest,
                VehicleCapability.CapabilityId capabilityId,
                ScenarioManifest.Zone zone,
                RiskClass maximumRisk,
                long ttlMs) {
            this.operation = Objects.requireNonNull(operation, "operation");
            this.grantId = requireCanonicalId(grantId, "grantId");
            this.ownerScopeDigest = EventBroker.requireDigest(
                    ownerScopeDigest,
                    "ownerScopeDigest");
            if (operation == Operation.GRANT_AUTO_EXECUTE) {
                this.scenarioId = requireScenarioId(scenarioId);
                this.scenarioManifestDigest = EventBroker.requireDigest(
                        scenarioManifestDigest,
                        "scenarioManifestDigest");
                this.capabilityId = Objects.requireNonNull(capabilityId, "capabilityId");
                this.zone = Objects.requireNonNull(zone, "zone");
                this.maximumRisk = Objects.requireNonNull(maximumRisk, "maximumRisk");
                if (ttlMs < 1 || ttlMs > MAX_GRANT_TTL_MS) {
                    throw new IllegalArgumentException("ttlMs is outside the grant bound");
                }
                this.ttlMs = ttlMs;
            } else {
                if (scenarioId != null || scenarioManifestDigest != null
                        || capabilityId != null || zone != null || maximumRisk != null
                        || ttlMs != 0) {
                    throw new IllegalArgumentException("revocation cannot carry grant fields");
                }
                this.scenarioId = null;
                this.scenarioManifestDigest = null;
                this.capabilityId = null;
                this.zone = null;
                this.maximumRisk = null;
                this.ttlMs = 0;
            }
            this.mutationDigest = EventBroker.digest(canonical());
        }

        public static ConsentMutation grant(
                String grantId,
                String ownerScopeDigest,
                String scenarioId,
                String scenarioManifestDigest,
                VehicleCapability.CapabilityId capabilityId,
                ScenarioManifest.Zone zone,
                RiskClass maximumRisk,
                long ttlMs) {
            return new ConsentMutation(
                    Operation.GRANT_AUTO_EXECUTE,
                    grantId,
                    ownerScopeDigest,
                    scenarioId,
                    scenarioManifestDigest,
                    capabilityId,
                    zone,
                    maximumRisk,
                    ttlMs);
        }

        public static ConsentMutation revoke(
                String grantId,
                String ownerScopeDigest) {
            return new ConsentMutation(
                    Operation.REVOKE_AUTO_EXECUTE,
                    grantId,
                    ownerScopeDigest,
                    null,
                    null,
                    null,
                    null,
                    null,
                    0);
        }

        public Operation getOperation() {
            return operation;
        }

        public String getGrantId() {
            return grantId;
        }

        public String getOwnerScopeDigest() {
            return ownerScopeDigest;
        }

        public String getScenarioId() {
            return scenarioId;
        }

        public String getScenarioManifestDigest() {
            return scenarioManifestDigest;
        }

        public VehicleCapability.CapabilityId getCapabilityId() {
            return capabilityId;
        }

        public ScenarioManifest.Zone getZone() {
            return zone;
        }

        public RiskClass getMaximumRisk() {
            return maximumRisk;
        }

        public long getTtlMs() {
            return ttlMs;
        }

        public String getMutationDigest() {
            return mutationDigest;
        }

        private String canonical() {
            return SCHEMA_VERSION + "|" + operation + "|" + grantId + "|"
                    + ownerScopeDigest + "|" + value(scenarioId) + "|"
                    + value(scenarioManifestDigest) + "|" + value(capabilityId) + "|"
                    + value(zone) + "|" + value(maximumRisk) + "|" + ttlMs;
        }
    }

    /** Digest-only proof that one exact mutation was presented to the consent owner. */
    public static final class ConsentEvidence {
        private final String requestId;
        private final String mutationDigest;
        private final String consentReceiptDigest;
        private final String privacyPolicyDigest;
        private final long issuedAtElapsedMs;
        private final long expiresAtElapsedMs;

        public ConsentEvidence(
                String requestId,
                String mutationDigest,
                String consentReceiptDigest,
                String privacyPolicyDigest,
                long issuedAtElapsedMs,
                long expiresAtElapsedMs) {
            this.requestId = requireCanonicalId(requestId, "requestId");
            this.mutationDigest = EventBroker.requireDigest(
                    mutationDigest,
                    "mutationDigest");
            this.consentReceiptDigest = EventBroker.requireDigest(
                    consentReceiptDigest,
                    "consentReceiptDigest");
            this.privacyPolicyDigest = EventBroker.requireDigest(
                    privacyPolicyDigest,
                    "privacyPolicyDigest");
            if (issuedAtElapsedMs < 0 || expiresAtElapsedMs < issuedAtElapsedMs
                    || expiresAtElapsedMs - issuedAtElapsedMs
                    > MAX_EVIDENCE_VALIDITY_MS) {
                throw new IllegalArgumentException("consent evidence validity is invalid");
            }
            this.issuedAtElapsedMs = issuedAtElapsedMs;
            this.expiresAtElapsedMs = expiresAtElapsedMs;
        }

        public String getRequestId() {
            return requestId;
        }

        public String getMutationDigest() {
            return mutationDigest;
        }

        public String getConsentReceiptDigest() {
            return consentReceiptDigest;
        }

        public String getPrivacyPolicyDigest() {
            return privacyPolicyDigest;
        }

        public long getIssuedAtElapsedMs() {
            return issuedAtElapsedMs;
        }

        public long getExpiresAtElapsedMs() {
            return expiresAtElapsedMs;
        }
    }

    public static final class MutationResult {
        private final MutationCode code;
        private final long revision;
        private final String grantDigest;
        private final long grantExpiresAtElapsedMs;

        MutationResult(
                MutationCode code,
                long revision,
                String grantDigest,
                long grantExpiresAtElapsedMs) {
            this.code = Objects.requireNonNull(code, "code");
            this.revision = revision;
            this.grantDigest = grantDigest;
            this.grantExpiresAtElapsedMs = grantExpiresAtElapsedMs;
        }

        public MutationCode getCode() {
            return code;
        }

        public long getRevision() {
            return revision;
        }

        public String getGrantDigest() {
            return grantDigest;
        }

        public long getGrantExpiresAtElapsedMs() {
            return grantExpiresAtElapsedMs;
        }
    }

    /** Exact candidate produced after suggestion and scenario capability resolution. */
    public static final class AutoExecutionCandidate {
        private final String suggestionDigest;
        private final String ownerScopeDigest;
        private final String scenarioId;
        private final String scenarioManifestDigest;
        private final VehicleCapability.CapabilityId capabilityId;
        private final ScenarioManifest.Zone zone;
        private final RiskClass riskClass;

        public AutoExecutionCandidate(
                String suggestionDigest,
                String ownerScopeDigest,
                String scenarioId,
                String scenarioManifestDigest,
                VehicleCapability.CapabilityId capabilityId,
                ScenarioManifest.Zone zone,
                RiskClass riskClass) {
            this.suggestionDigest = EventBroker.requireDigest(
                    suggestionDigest,
                    "suggestionDigest");
            this.ownerScopeDigest = EventBroker.requireDigest(
                    ownerScopeDigest,
                    "ownerScopeDigest");
            this.scenarioId = requireScenarioId(scenarioId);
            this.scenarioManifestDigest = EventBroker.requireDigest(
                    scenarioManifestDigest,
                    "scenarioManifestDigest");
            this.capabilityId = Objects.requireNonNull(capabilityId, "capabilityId");
            this.zone = Objects.requireNonNull(zone, "zone");
            this.riskClass = Objects.requireNonNull(riskClass, "riskClass");
        }

        public String getSuggestionDigest() {
            return suggestionDigest;
        }

        public String getOwnerScopeDigest() {
            return ownerScopeDigest;
        }

        public String getScenarioId() {
            return scenarioId;
        }

        public String getScenarioManifestDigest() {
            return scenarioManifestDigest;
        }

        public VehicleCapability.CapabilityId getCapabilityId() {
            return capabilityId;
        }

        public ScenarioManifest.Zone getZone() {
            return zone;
        }

        public RiskClass getRiskClass() {
            return riskClass;
        }
    }

    public static final class AdmissionDecision {
        private final AdmissionCode code;
        private final String grantDigest;
        private final long grantExpiresAtElapsedMs;

        AdmissionDecision(
                AdmissionCode code,
                String grantDigest,
                long grantExpiresAtElapsedMs) {
            this.code = Objects.requireNonNull(code, "code");
            this.grantDigest = grantDigest;
            this.grantExpiresAtElapsedMs = grantExpiresAtElapsedMs;
        }

        public AdmissionCode getCode() {
            return code;
        }

        public String getGrantDigest() {
            return grantDigest;
        }

        public long getGrantExpiresAtElapsedMs() {
            return grantExpiresAtElapsedMs;
        }

        public boolean isPolicyEligible() {
            return code == AdmissionCode.POLICY_ELIGIBLE;
        }

        public boolean isEffectDispatchAuthorized() {
            return false;
        }

        public boolean isSafetyRevalidationRequired() {
            return true;
        }
    }

    public static final class Snapshot {
        private final long revision;
        private final int activeGrantCount;
        private final long appliedGrantCount;
        private final long revokedGrantCount;
        private final long eligibleDecisionCount;
        private final long rejectedDecisionCount;

        Snapshot(
                long revision,
                int activeGrantCount,
                long appliedGrantCount,
                long revokedGrantCount,
                long eligibleDecisionCount,
                long rejectedDecisionCount) {
            this.revision = revision;
            this.activeGrantCount = activeGrantCount;
            this.appliedGrantCount = appliedGrantCount;
            this.revokedGrantCount = revokedGrantCount;
            this.eligibleDecisionCount = eligibleDecisionCount;
            this.rejectedDecisionCount = rejectedDecisionCount;
        }

        public long getRevision() {
            return revision;
        }

        public int getActiveGrantCount() {
            return activeGrantCount;
        }

        public long getAppliedGrantCount() {
            return appliedGrantCount;
        }

        public long getRevokedGrantCount() {
            return revokedGrantCount;
        }

        public long getEligibleDecisionCount() {
            return eligibleDecisionCount;
        }

        public long getRejectedDecisionCount() {
            return rejectedDecisionCount;
        }

        public boolean isPolicyOnly() {
            return true;
        }

        public boolean isProcessLocal() {
            return true;
        }

        public boolean isGrantPersistenceWired() {
            return false;
        }

        public boolean isProductionConsentAuthorityWired() {
            return false;
        }

        public boolean isAutoExecutionEnabled() {
            return false;
        }

        public boolean isEffectDispatchEnabled() {
            return false;
        }

        public boolean isRuntimeWired() {
            return false;
        }

        public boolean isHardwareAccessed() {
            return false;
        }
    }

    private static final class GrantRecord {
        final ConsentMutation mutation;
        final long issuedAtElapsedMs;
        final long expiresAtElapsedMs;
        final String grantDigest;

        GrantRecord(ConsentMutation mutation, long issuedAtElapsedMs) {
            this.mutation = mutation;
            this.issuedAtElapsedMs = issuedAtElapsedMs;
            this.expiresAtElapsedMs = issuedAtElapsedMs + mutation.ttlMs;
            this.grantDigest = EventBroker.digest(
                    "proactive-grant|" + mutation.mutationDigest + "|"
                            + issuedAtElapsedMs + "|" + expiresAtElapsedMs);
        }

        boolean sameBinding(ConsentMutation other) {
            return mutation.mutationDigest.equals(other.mutationDigest);
        }

        boolean matches(AutoExecutionCandidate candidate) {
            return mutation.ownerScopeDigest.equals(candidate.ownerScopeDigest)
                    && mutation.scenarioId.equals(candidate.scenarioId)
                    && mutation.scenarioManifestDigest.equals(
                    candidate.scenarioManifestDigest)
                    && mutation.capabilityId == candidate.capabilityId
                    && mutation.zone == candidate.zone;
        }
    }

    private static final class ReplayEntry {
        final String mutationDigest;
        final MutationResult result;

        ReplayEntry(String mutationDigest, MutationResult result) {
            this.mutationDigest = mutationDigest;
            this.result = result;
        }
    }

    private final int grantCapacity;
    private final LongSupplier elapsedRealtimeMs;
    private final ConsentAuthority consentAuthority;
    private final LinkedHashMap<String, GrantRecord> grants = new LinkedHashMap<>();
    private final LinkedHashMap<String, ReplayEntry> replays = new LinkedHashMap<>();
    private long revision;
    private long appliedGrantCount;
    private long revokedGrantCount;
    private long eligibleDecisionCount;
    private long rejectedDecisionCount;

    private ProactiveConsentPolicy(
            int grantCapacity,
            LongSupplier elapsedRealtimeMs,
            ConsentAuthority consentAuthority) {
        if (grantCapacity < 1 || grantCapacity > MAX_GRANTS) {
            throw new IllegalArgumentException("grant capacity is invalid");
        }
        this.grantCapacity = grantCapacity;
        this.elapsedRealtimeMs = Objects.requireNonNull(
                elapsedRealtimeMs,
                "elapsedRealtimeMs");
        this.consentAuthority = Objects.requireNonNull(
                consentAuthority,
                "consentAuthority");
    }

    public static ProactiveConsentPolicy createForContractTest(
            int grantCapacity,
            LongSupplier elapsedRealtimeMs,
            ConsentAuthority consentAuthority) {
        return new ProactiveConsentPolicy(
                grantCapacity,
                elapsedRealtimeMs,
                consentAuthority);
    }

    public synchronized MutationResult mutate(
            ConsentMutation mutation,
            DrivingState drivingState,
            ConsentEvidence evidence) {
        Objects.requireNonNull(mutation, "mutation");
        Objects.requireNonNull(drivingState, "drivingState");
        long now = now();
        if (!evidenceMatches(mutation, evidence, now)) {
            return mutationResult(MutationCode.INVALID_EVIDENCE, null);
        }
        ReplayEntry replay = replays.get(evidence.requestId);
        if (replay != null) {
            return replay.mutationDigest.equals(mutation.mutationDigest)
                    ? new MutationResult(
                    MutationCode.REPLAYED,
                    replay.result.revision,
                    replay.result.grantDigest,
                    replay.result.grantExpiresAtElapsedMs)
                    : mutationResult(MutationCode.REQUEST_CONFLICT, null);
        }
        if (drivingState != DrivingState.PARKED) {
            return remember(evidence.requestId, mutation,
                    mutationResult(MutationCode.DRIVING_RESTRICTED, null));
        }
        if (mutation.operation == Operation.GRANT_AUTO_EXECUTE
                && !mutation.maximumRisk.isGenericGrantAllowed()) {
            return remember(evidence.requestId, mutation,
                    mutationResult(
                            MutationCode.HIGH_RISK_GENERIC_GRANT_FORBIDDEN,
                            null));
        }
        AuthorityDecision authorityDecision;
        try {
            authorityDecision = consentAuthority.authorize(mutation, evidence);
        } catch (RuntimeException unavailable) {
            return remember(evidence.requestId, mutation,
                    mutationResult(MutationCode.AUTHORITY_UNAVAILABLE, null));
        }
        if (authorityDecision == null) {
            return remember(evidence.requestId, mutation,
                    mutationResult(MutationCode.AUTHORITY_UNAVAILABLE, null));
        }
        if (authorityDecision != AuthorityDecision.ALLOWED) {
            return remember(evidence.requestId, mutation,
                    mutationResult(MutationCode.AUTHORITY_DENIED, null));
        }

        pruneExpired(now);
        MutationResult result = mutation.operation == Operation.GRANT_AUTO_EXECUTE
                ? applyGrant(mutation, now)
                : applyRevocation(mutation);
        return remember(evidence.requestId, mutation, result);
    }

    public synchronized AdmissionDecision evaluate(AutoExecutionCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        long now = now();
        pruneExpired(now);
        if (!candidate.riskClass.isGenericGrantAllowed()) {
            rejectedDecisionCount++;
            return new AdmissionDecision(
                    AdmissionCode.EXPLICIT_APPROVAL_REQUIRED,
                    null,
                    0);
        }
        boolean exactBindingFound = false;
        for (GrantRecord grant : grants.values()) {
            if (!grant.matches(candidate)) {
                continue;
            }
            exactBindingFound = true;
            if (!grant.mutation.maximumRisk.includes(candidate.riskClass)) {
                continue;
            }
            eligibleDecisionCount++;
            return new AdmissionDecision(
                    AdmissionCode.POLICY_ELIGIBLE,
                    grant.grantDigest,
                    grant.expiresAtElapsedMs);
        }
        rejectedDecisionCount++;
        return new AdmissionDecision(
                exactBindingFound
                        ? AdmissionCode.RISK_EXCEEDS_GRANT
                        : AdmissionCode.NO_ACTIVE_GRANT,
                null,
                0);
    }

    public synchronized Snapshot snapshot() {
        pruneExpired(now());
        return new Snapshot(
                revision,
                grants.size(),
                appliedGrantCount,
                revokedGrantCount,
                eligibleDecisionCount,
                rejectedDecisionCount);
    }

    public synchronized List<String> activeGrantDigests() {
        pruneExpired(now());
        List<String> result = new ArrayList<>(grants.size());
        for (GrantRecord grant : grants.values()) {
            result.add(grant.grantDigest);
        }
        Collections.sort(result);
        return Collections.unmodifiableList(result);
    }

    private MutationResult applyGrant(ConsentMutation mutation, long now) {
        GrantRecord existing = grants.get(mutation.grantId);
        if (existing != null) {
            return existing.sameBinding(mutation)
                    ? mutationResult(MutationCode.NO_CHANGE, existing)
                    : mutationResult(MutationCode.GRANT_ID_CONFLICT, null);
        }
        if (grants.size() >= grantCapacity) {
            return mutationResult(MutationCode.CAPACITY_EXCEEDED, null);
        }
        if (now > Long.MAX_VALUE - mutation.ttlMs) {
            return mutationResult(MutationCode.INVALID_EVIDENCE, null);
        }
        GrantRecord grant = new GrantRecord(mutation, now);
        grants.put(mutation.grantId, grant);
        incrementRevision();
        appliedGrantCount++;
        return mutationResult(MutationCode.APPLIED, grant);
    }

    private MutationResult applyRevocation(ConsentMutation mutation) {
        GrantRecord existing = grants.get(mutation.grantId);
        if (existing == null
                || !existing.mutation.ownerScopeDigest.equals(
                mutation.ownerScopeDigest)) {
            return mutationResult(MutationCode.NOT_FOUND, null);
        }
        grants.remove(mutation.grantId);
        incrementRevision();
        revokedGrantCount++;
        return mutationResult(MutationCode.APPLIED, existing);
    }

    private MutationResult mutationResult(MutationCode code, GrantRecord grant) {
        return new MutationResult(
                code,
                revision,
                grant == null ? null : grant.grantDigest,
                grant == null ? 0 : grant.expiresAtElapsedMs);
    }

    private MutationResult remember(
            String requestId,
            ConsentMutation mutation,
            MutationResult result) {
        if (replays.size() >= MAX_REPLAY_ENTRIES) {
            Iterator<Map.Entry<String, ReplayEntry>> iterator =
                    replays.entrySet().iterator();
            iterator.next();
            iterator.remove();
        }
        replays.put(
                requestId,
                new ReplayEntry(mutation.mutationDigest, result));
        return result;
    }

    private void pruneExpired(long now) {
        Iterator<Map.Entry<String, GrantRecord>> iterator = grants.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().expiresAtElapsedMs <= now) {
                iterator.remove();
            }
        }
    }

    private void incrementRevision() {
        revision = revision == Long.MAX_VALUE ? Long.MAX_VALUE : revision + 1;
    }

    private long now() {
        long now = elapsedRealtimeMs.getAsLong();
        if (now < 0) {
            throw new IllegalStateException("elapsed realtime must be non-negative");
        }
        return now;
    }

    private static boolean evidenceMatches(
            ConsentMutation mutation,
            ConsentEvidence evidence,
            long now) {
        return evidence != null
                && now >= evidence.issuedAtElapsedMs
                && now <= evidence.expiresAtElapsedMs
                && mutation.mutationDigest.equals(evidence.mutationDigest);
    }

    private static String requireCanonicalId(String value, String field) {
        if (value == null || !CANONICAL_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }

    private static String requireScenarioId(String value) {
        if (value == null || !SCENARIO_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("scenarioId is invalid");
        }
        return value;
    }

    private static String value(Object value) {
        return value == null ? "none" : value.toString();
    }
}
