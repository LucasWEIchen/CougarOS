package com.centralbrain.runtime.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Process-local Memory consent and HMI projection contract.
 *
 * <p>The controller exposes fixed source metadata and deterministic user-control decisions. It
 * never reads Memory content and does not mutate a production repository.</p>
 */
public final class MemoryConsentController {
    public static final int SCHEMA_VERSION = 1;
    public static final int SOURCE_COUNT = 3;
    public static final int MAX_REPLAY_ENTRIES = 64;
    public static final long MAX_EVIDENCE_VALIDITY_MS = 300_000L;

    private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern CANONICAL_ID =
            Pattern.compile("^[a-z0-9](?:[a-z0-9._-]{0,94}[a-z0-9])?$");

    public enum MemorySource {
        WORKING_SESSION,
        PROFILE_PREFERENCE,
        EPISODIC_SCENARIO
    }

    public enum Purpose {
        CURRENT_SESSION_CONTINUITY,
        USER_APPROVED_PERSONALIZATION,
        BOUNDED_SCENARIO_IMPROVEMENT
    }

    public enum Retention {
        UNTIL_SESSION_TERMINAL,
        UNTIL_USER_CLEAR,
        MAXIMUM_30_DAYS
    }

    public enum StoragePresence {
        PRESENT,
        EMPTY,
        NOT_DISCLOSED
    }

    public enum DrivingState {
        PARKED,
        MOVING,
        UNKNOWN
    }

    public enum Operation {
        SET_RETAINED_MEMORY_ENABLED,
        CLEAR_PROFILE_PREFERENCES
    }

    public enum ResultCode {
        APPLIED,
        NO_CHANGE,
        DRIVING_RESTRICTED,
        INVALID_EVIDENCE,
        REQUEST_CONFLICT,
        AUTHORITY_DENIED,
        AUTHORITY_UNAVAILABLE
    }

    public enum AuthorizationDecision {
        ALLOWED,
        DENIED
    }

    public interface ElapsedRealtimeClock {
        long nowMs();
    }

    /** Fail-closed owner for consent-setting and profile-clear authorization. */
    public interface MutationAuthority {
        AuthorizationDecision authorize(MutationRequest request, MutationEvidence evidence);
    }

    public static final class SourceStatus {
        private final MemorySource source;
        private final Purpose purpose;
        private final Retention retention;
        private final boolean enabled;
        private final boolean userToggleable;
        private final StoragePresence storagePresence;

        private SourceStatus(
                MemorySource source,
                Purpose purpose,
                Retention retention,
                boolean enabled,
                boolean userToggleable,
                StoragePresence storagePresence) {
            this.source = source;
            this.purpose = purpose;
            this.retention = retention;
            this.enabled = enabled;
            this.userToggleable = userToggleable;
            this.storagePresence = storagePresence;
        }

        public MemorySource getSource() {
            return source;
        }

        public Purpose getPurpose() {
            return purpose;
        }

        public Retention getRetention() {
            return retention;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public boolean isUserToggleable() {
            return userToggleable;
        }

        public StoragePresence getStoragePresence() {
            return storagePresence;
        }
    }

    public static final class HmiSnapshot {
        private final long revision;
        private final DrivingState drivingState;
        private final boolean managementAllowed;
        private final boolean retainedMemoryEnabled;
        private final List<SourceStatus> sources;

        private HmiSnapshot(
                long revision,
                DrivingState drivingState,
                boolean managementAllowed,
                boolean retainedMemoryEnabled,
                List<SourceStatus> sources) {
            this.revision = revision;
            this.drivingState = drivingState;
            this.managementAllowed = managementAllowed;
            this.retainedMemoryEnabled = retainedMemoryEnabled;
            this.sources = Collections.unmodifiableList(new ArrayList<>(sources));
        }

        public long getRevision() {
            return revision;
        }

        public DrivingState getDrivingState() {
            return drivingState;
        }

        public boolean isManagementAllowed() {
            return managementAllowed;
        }

        public boolean isRetainedMemoryEnabled() {
            return retainedMemoryEnabled;
        }

        public List<SourceStatus> getSources() {
            return sources;
        }
    }

    public static final class MutationRequest {
        private final String ownerFingerprint;
        private final Operation operation;
        private final Boolean retainedMemoryEnabled;

        private MutationRequest(
                String ownerFingerprint,
                Operation operation,
                Boolean retainedMemoryEnabled) {
            this.ownerFingerprint = requireOwner(ownerFingerprint);
            this.operation = Objects.requireNonNull(operation, "operation");
            this.retainedMemoryEnabled = retainedMemoryEnabled;
            if ((operation == Operation.SET_RETAINED_MEMORY_ENABLED)
                    != (retainedMemoryEnabled != null)) {
                throw new IllegalArgumentException("operation target mismatch");
            }
        }

        public String getOwnerFingerprint() {
            return ownerFingerprint;
        }

        public Operation getOperation() {
            return operation;
        }

        public Boolean getRetainedMemoryEnabled() {
            return retainedMemoryEnabled;
        }

        private String digestMaterial() {
            return ownerFingerprint + ":" + operation + ":" + retainedMemoryEnabled;
        }
    }

    public static final class MutationEvidence {
        private final String requestId;
        private final String ownerFingerprint;
        private final Operation operation;
        private final Boolean retainedMemoryEnabled;
        private final long issuedAtElapsedMs;
        private final long expiresAtElapsedMs;

        private MutationEvidence(
                String requestId,
                String ownerFingerprint,
                Operation operation,
                Boolean retainedMemoryEnabled,
                long issuedAtElapsedMs,
                long expiresAtElapsedMs) {
            this.requestId = requireCanonicalId(requestId, "requestId");
            this.ownerFingerprint = requireOwner(ownerFingerprint);
            this.operation = Objects.requireNonNull(operation, "operation");
            this.retainedMemoryEnabled = retainedMemoryEnabled;
            if ((operation == Operation.SET_RETAINED_MEMORY_ENABLED)
                    != (retainedMemoryEnabled != null)) {
                throw new IllegalArgumentException("evidence target mismatch");
            }
            if (issuedAtElapsedMs < 0 || expiresAtElapsedMs < issuedAtElapsedMs
                    || expiresAtElapsedMs - issuedAtElapsedMs > MAX_EVIDENCE_VALIDITY_MS) {
                throw new IllegalArgumentException("invalid evidence validity");
            }
            this.issuedAtElapsedMs = issuedAtElapsedMs;
            this.expiresAtElapsedMs = expiresAtElapsedMs;
        }

        public static MutationEvidence forRetainedMemory(
                String requestId,
                String ownerFingerprint,
                boolean enabled,
                long issuedAtElapsedMs,
                long expiresAtElapsedMs) {
            return new MutationEvidence(
                    requestId,
                    ownerFingerprint,
                    Operation.SET_RETAINED_MEMORY_ENABLED,
                    enabled,
                    issuedAtElapsedMs,
                    expiresAtElapsedMs);
        }

        public static MutationEvidence forPreferenceClear(
                String requestId,
                String ownerFingerprint,
                long issuedAtElapsedMs,
                long expiresAtElapsedMs) {
            return new MutationEvidence(
                    requestId,
                    ownerFingerprint,
                    Operation.CLEAR_PROFILE_PREFERENCES,
                    null,
                    issuedAtElapsedMs,
                    expiresAtElapsedMs);
        }

        public String getRequestId() {
            return requestId;
        }

        public String getOwnerFingerprint() {
            return ownerFingerprint;
        }

        public Operation getOperation() {
            return operation;
        }

        public Boolean getRetainedMemoryEnabled() {
            return retainedMemoryEnabled;
        }

        public long getIssuedAtElapsedMs() {
            return issuedAtElapsedMs;
        }

        public long getExpiresAtElapsedMs() {
            return expiresAtElapsedMs;
        }
    }

    public static final class MutationResult {
        private final ResultCode code;
        private final long revision;
        private final boolean projectionApplied;
        private final boolean replayed;

        private MutationResult(
                ResultCode code,
                long revision,
                boolean projectionApplied,
                boolean replayed) {
            this.code = code;
            this.revision = revision;
            this.projectionApplied = projectionApplied;
            this.replayed = replayed;
        }

        public ResultCode getCode() {
            return code;
        }

        public long getRevision() {
            return revision;
        }

        public boolean isProjectionApplied() {
            return projectionApplied;
        }

        public boolean isReplayed() {
            return replayed;
        }

        public boolean isRepositoryMutationApplied() {
            return false;
        }

        private MutationResult asReplay() {
            return new MutationResult(code, revision, projectionApplied, true);
        }
    }

    private static final class ReplayEntry {
        final String digestMaterial;
        final MutationResult result;

        ReplayEntry(String digestMaterial, MutationResult result) {
            this.digestMaterial = digestMaterial;
            this.result = result;
        }
    }

    private final ElapsedRealtimeClock clock;
    private final MutationAuthority authority;
    private final Map<String, ReplayEntry> replayEntries = new LinkedHashMap<>();
    private long revision;
    private boolean retainedMemoryEnabled = true;
    private boolean profilePreferencesPresent = true;

    private MemoryConsentController(ElapsedRealtimeClock clock, MutationAuthority authority) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.authority = Objects.requireNonNull(authority, "authority");
    }

    public static MemoryConsentController createForContractTest(
            ElapsedRealtimeClock clock,
            MutationAuthority authority) {
        return new MemoryConsentController(clock, authority);
    }

    public synchronized HmiSnapshot snapshot(String ownerFingerprint, DrivingState drivingState) {
        requireOwner(ownerFingerprint);
        DrivingState state = Objects.requireNonNull(drivingState, "drivingState");
        boolean managementAllowed = state == DrivingState.PARKED;
        StoragePresence profilePresence = managementAllowed
                ? (profilePreferencesPresent ? StoragePresence.PRESENT : StoragePresence.EMPTY)
                : StoragePresence.NOT_DISCLOSED;
        List<SourceStatus> sources = new ArrayList<>(SOURCE_COUNT);
        sources.add(new SourceStatus(
                MemorySource.WORKING_SESSION,
                Purpose.CURRENT_SESSION_CONTINUITY,
                Retention.UNTIL_SESSION_TERMINAL,
                true,
                false,
                StoragePresence.NOT_DISCLOSED));
        sources.add(new SourceStatus(
                MemorySource.PROFILE_PREFERENCE,
                Purpose.USER_APPROVED_PERSONALIZATION,
                Retention.UNTIL_USER_CLEAR,
                retainedMemoryEnabled,
                true,
                profilePresence));
        sources.add(new SourceStatus(
                MemorySource.EPISODIC_SCENARIO,
                Purpose.BOUNDED_SCENARIO_IMPROVEMENT,
                Retention.MAXIMUM_30_DAYS,
                retainedMemoryEnabled,
                true,
                StoragePresence.NOT_DISCLOSED));
        return new HmiSnapshot(
                revision, state, managementAllowed, retainedMemoryEnabled, sources);
    }

    public synchronized MutationResult setRetainedMemoryEnabled(
            String ownerFingerprint,
            boolean enabled,
            DrivingState drivingState,
            MutationEvidence evidence) {
        return mutate(
                new MutationRequest(
                        ownerFingerprint,
                        Operation.SET_RETAINED_MEMORY_ENABLED,
                        enabled),
                Objects.requireNonNull(drivingState, "drivingState"),
                evidence);
    }

    public synchronized MutationResult clearProfilePreferences(
            String ownerFingerprint,
            DrivingState drivingState,
            MutationEvidence evidence) {
        return mutate(
                new MutationRequest(
                        ownerFingerprint,
                        Operation.CLEAR_PROFILE_PREFERENCES,
                        null),
                Objects.requireNonNull(drivingState, "drivingState"),
                evidence);
    }

    private MutationResult mutate(
            MutationRequest request,
            DrivingState drivingState,
            MutationEvidence evidence) {
        if (!matches(request, evidence, clock.nowMs())) {
            return result(ResultCode.INVALID_EVIDENCE, false);
        }
        String digestMaterial = request.digestMaterial();
        ReplayEntry replay = replayEntries.get(evidence.requestId);
        if (replay != null) {
            return replay.digestMaterial.equals(digestMaterial)
                    ? replay.result.asReplay()
                    : result(ResultCode.REQUEST_CONFLICT, false);
        }
        if (drivingState != DrivingState.PARKED) {
            return remember(evidence.requestId, digestMaterial,
                    result(ResultCode.DRIVING_RESTRICTED, false));
        }
        AuthorizationDecision decision;
        try {
            decision = authority.authorize(request, evidence);
        } catch (RuntimeException failure) {
            return remember(evidence.requestId, digestMaterial,
                    result(ResultCode.AUTHORITY_UNAVAILABLE, false));
        }
        if (decision == null) {
            return remember(evidence.requestId, digestMaterial,
                    result(ResultCode.AUTHORITY_UNAVAILABLE, false));
        }
        if (decision != AuthorizationDecision.ALLOWED) {
            return remember(evidence.requestId, digestMaterial,
                    result(ResultCode.AUTHORITY_DENIED, false));
        }
        boolean changed;
        if (request.operation == Operation.SET_RETAINED_MEMORY_ENABLED) {
            changed = retainedMemoryEnabled != request.retainedMemoryEnabled;
            if (changed) {
                retainedMemoryEnabled = request.retainedMemoryEnabled;
            }
        } else {
            changed = profilePreferencesPresent;
            if (changed) {
                profilePreferencesPresent = false;
            }
        }
        if (changed) {
            revision = revision == Long.MAX_VALUE ? Long.MAX_VALUE : revision + 1;
        }
        return remember(evidence.requestId, digestMaterial,
                result(changed ? ResultCode.APPLIED : ResultCode.NO_CHANGE, changed));
    }

    private MutationResult remember(
            String requestId,
            String digestMaterial,
            MutationResult result) {
        if (replayEntries.size() >= MAX_REPLAY_ENTRIES) {
            String eldest = replayEntries.keySet().iterator().next();
            replayEntries.remove(eldest);
        }
        replayEntries.put(requestId, new ReplayEntry(digestMaterial, result));
        return result;
    }

    private MutationResult result(ResultCode code, boolean projectionApplied) {
        return new MutationResult(code, revision, projectionApplied, false);
    }

    private static boolean matches(
            MutationRequest request,
            MutationEvidence evidence,
            long nowMs) {
        if (evidence == null || nowMs < 0
                || nowMs < evidence.issuedAtElapsedMs
                || nowMs > evidence.expiresAtElapsedMs) {
            return false;
        }
        return request.ownerFingerprint.equals(evidence.ownerFingerprint)
                && request.operation == evidence.operation
                && Objects.equals(request.retainedMemoryEnabled, evidence.retainedMemoryEnabled);
    }

    public boolean isHmiProjectionOnly() {
        return true;
    }

    public boolean isProductionAuthorityWired() {
        return false;
    }

    public boolean isRepositoryMutationWired() {
        return false;
    }

    public boolean isRuntimeWired() {
        return false;
    }

    public boolean isModelContextPublished() {
        return false;
    }

    public boolean isContentLoggingEnabled() {
        return false;
    }

    public boolean isHardwareAccessed() {
        return false;
    }

    private static String requireOwner(String value) {
        if (value == null || !SHA256.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid owner fingerprint");
        }
        return value;
    }

    private static String requireCanonicalId(String value, String field) {
        if (value == null || !CANONICAL_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid " + field);
        }
        return value;
    }
}
