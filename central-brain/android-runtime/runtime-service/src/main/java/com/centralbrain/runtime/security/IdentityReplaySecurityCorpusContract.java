package com.centralbrain.runtime.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Fixed metadata-only hostile-policy corpus for the P9-W03b security regression. */
public final class IdentityReplaySecurityCorpusContract {
    public static final int SCHEMA_VERSION = 1;
    public static final String PROFILE_ID = "android13-p9-identity-replay-security-v1";
    public static final int SURFACE_COUNT = 3;
    public static final int CASES_PER_SURFACE = 6;
    public static final int CASE_COUNT = SURFACE_COUNT * CASES_PER_SURFACE;

    private static final Pattern CASE_ID =
            Pattern.compile("[a-z][a-z0-9_]*(?:[.][a-z0-9_]+){2,5}");
    private static final Pattern OUTCOME_CODE = Pattern.compile("[A-Z][A-Z0-9_]{2,63}");
    private static final List<CorpusCase> CASES = buildCases();
    private static final String CORPUS_DIGEST = sha256(canonicalCorpus());

    private IdentityReplaySecurityCorpusContract() {
    }

    public enum Surface {
        CALLER_POLICY,
        SESSION_REPLAY,
        SIGNER_POLICY
    }

    public enum ThreatClass {
        UNRESOLVED_IDENTITY,
        PACKAGE_SPOOF,
        SIGNER_SPOOF,
        CAPABILITY_ESCALATION,
        SHARED_UID_CONFUSION,
        PRINCIPAL_ROTATION,
        REPLAY,
        REPLAY_CONFLICT,
        CROSS_OWNER_ACCESS,
        MALFORMED_PRINCIPAL,
        UNKNOWN_SIGNER,
        SIGNER_EPOCH,
        RETIRED_SIGNER,
        REVOKED_SIGNER,
        MALFORMED_SIGNER_EVIDENCE
    }

    public static final class CorpusCase {
        private final String caseId;
        private final Surface surface;
        private final ThreatClass threatClass;
        private final String expectedOutcomeCode;

        private CorpusCase(
                String caseId,
                Surface surface,
                ThreatClass threatClass,
                String expectedOutcomeCode) {
            if (caseId == null || !CASE_ID.matcher(caseId).matches()) {
                throw new IllegalArgumentException("identity security caseId is invalid");
            }
            if (expectedOutcomeCode == null
                    || !OUTCOME_CODE.matcher(expectedOutcomeCode).matches()) {
                throw new IllegalArgumentException("identity security outcome code is invalid");
            }
            this.caseId = caseId;
            this.surface = Objects.requireNonNull(surface, "surface");
            this.threatClass = Objects.requireNonNull(threatClass, "threatClass");
            this.expectedOutcomeCode = expectedOutcomeCode;
        }

        public String getCaseId() {
            return caseId;
        }

        public Surface getSurface() {
            return surface;
        }

        public ThreatClass getThreatClass() {
            return threatClass;
        }

        public String getExpectedOutcomeCode() {
            return expectedOutcomeCode;
        }

        private String canonicalForm() {
            return caseId
                    + "|" + surface.name()
                    + "|" + threatClass.name()
                    + "|" + expectedOutcomeCode;
        }
    }

    public static List<CorpusCase> cases() {
        return CASES;
    }

    public static CorpusCase requireCase(String caseId) {
        for (CorpusCase corpusCase : CASES) {
            if (corpusCase.getCaseId().equals(caseId)) {
                return corpusCase;
            }
        }
        throw new IllegalArgumentException("identity security case is not registered");
    }

    public static String corpusDigest() {
        return CORPUS_DIGEST;
    }

    public static boolean isCallerPolicyHostVerified() {
        return true;
    }

    public static boolean isSessionReplayOwnerPolicyHostVerified() {
        return true;
    }

    public static boolean isSignerPolicyHostVerified() {
        return true;
    }

    public static boolean isBinderCallingUidSpoofAndroidVerified() {
        return false;
    }

    public static boolean isPackageSignatureCryptographicallyVerified() {
        return false;
    }

    public static boolean isCoverageGuidedFuzzComplete() {
        return false;
    }

    public static boolean isAndroid13Arm64Verified() {
        return false;
    }

    public static boolean isRuntimeWired() {
        return false;
    }

    public static boolean isHardwareAccessed() {
        return false;
    }

    public static boolean isProductionReady() {
        return false;
    }

    public static boolean isTargetHardwareValidated() {
        return false;
    }

    private static List<CorpusCase> buildCases() {
        List<CorpusCase> cases = new ArrayList<>(CASE_COUNT);
        add(cases, "caller.unresolved_identity.v1", Surface.CALLER_POLICY,
                ThreatClass.UNRESOLVED_IDENTITY, "IDENTITY_UNRESOLVED");
        add(cases, "caller.package_spoof.v1", Surface.CALLER_POLICY,
                ThreatClass.PACKAGE_SPOOF, "PACKAGE_NOT_CONFIGURED");
        add(cases, "caller.current_signer_spoof.v1", Surface.CALLER_POLICY,
                ThreatClass.SIGNER_SPOOF, "CURRENT_SIGNER_MISMATCH");
        add(cases, "caller.capability_escalation.v1", Surface.CALLER_POLICY,
                ThreatClass.CAPABILITY_ESCALATION, "CAPABILITY_NOT_GRANTED");
        add(cases, "caller.shared_uid_signer_confusion.v1", Surface.CALLER_POLICY,
                ThreatClass.SHARED_UID_CONFUSION, "CURRENT_SIGNER_MISMATCH");
        add(cases, "caller.principal_signer_rotation.v1", Surface.CALLER_POLICY,
                ThreatClass.PRINCIPAL_ROTATION, "FINGERPRINT_CHANGED");

        add(cases, "session.same_digest_replay.v1", Surface.SESSION_REPLAY,
                ThreatClass.REPLAY, "SAME_HANDLE");
        add(cases, "session.request_digest_conflict.v1", Surface.SESSION_REPLAY,
                ThreatClass.REPLAY_CONFLICT, "IDEMPOTENCY_CONFLICT");
        add(cases, "session.cross_owner_find.v1", Surface.SESSION_REPLAY,
                ThreatClass.CROSS_OWNER_ACCESS, "NULL_SNAPSHOT");
        add(cases, "session.cross_owner_events.v1", Surface.SESSION_REPLAY,
                ThreatClass.CROSS_OWNER_ACCESS, "SESSION_NOT_FOUND");
        add(cases, "session.cross_owner_cancel.v1", Surface.SESSION_REPLAY,
                ThreatClass.CROSS_OWNER_ACCESS, "NO_CHANGE");
        add(cases, "session.malformed_owner.v1", Surface.SESSION_REPLAY,
                ThreatClass.MALFORMED_PRINCIPAL, "SECURITY_EXCEPTION");

        add(cases, "signer.unknown.v1", Surface.SIGNER_POLICY,
                ThreatClass.UNKNOWN_SIGNER, "UNKNOWN_SIGNER");
        add(cases, "signer.not_yet_active.v1", Surface.SIGNER_POLICY,
                ThreatClass.SIGNER_EPOCH, "SIGNER_NOT_YET_ACTIVE");
        add(cases, "signer.retired.v1", Surface.SIGNER_POLICY,
                ThreatClass.RETIRED_SIGNER, "RETIRED_SIGNER");
        add(cases, "signer.revoked.v1", Surface.SIGNER_POLICY,
                ThreatClass.REVOKED_SIGNER, "REVOKED_SIGNER");
        add(cases, "signer.malformed_digest.v1", Surface.SIGNER_POLICY,
                ThreatClass.MALFORMED_SIGNER_EVIDENCE, "POLICY_VIOLATION");
        add(cases, "signer.nonpositive_epoch.v1", Surface.SIGNER_POLICY,
                ThreatClass.SIGNER_EPOCH, "POLICY_VIOLATION");

        if (cases.size() != CASE_COUNT) {
            throw new IllegalStateException("identity security case count changed");
        }
        for (Surface surface : Surface.values()) {
            long count = cases.stream().filter(value -> value.getSurface() == surface).count();
            if (count != CASES_PER_SURFACE) {
                throw new IllegalStateException("identity security surface count changed");
            }
        }
        return Collections.unmodifiableList(cases);
    }

    private static void add(
            List<CorpusCase> cases,
            String caseId,
            Surface surface,
            ThreatClass threatClass,
            String expectedOutcomeCode) {
        for (CorpusCase existing : cases) {
            if (existing.getCaseId().equals(caseId)) {
                throw new IllegalStateException("duplicate identity security case");
            }
        }
        cases.add(new CorpusCase(caseId, surface, threatClass, expectedOutcomeCode));
    }

    private static String canonicalCorpus() {
        StringBuilder canonical = new StringBuilder()
                .append(SCHEMA_VERSION).append('|').append(PROFILE_ID);
        for (CorpusCase corpusCase : CASES) {
            canonical.append('|').append(corpusCase.canonicalForm());
        }
        return canonical.toString();
    }

    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte current : bytes) {
                result.append(Character.forDigit((current >>> 4) & 0xf, 16));
                result.append(Character.forDigit(current & 0xf, 16));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
