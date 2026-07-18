package com.centralbrain.runtime.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Fixed metadata-only hostile-input corpus for the P9-W03a parser security regression. */
public final class ParserSecurityCorpusContract {
    public static final int SCHEMA_VERSION = 1;
    public static final String PROFILE_ID = "android13-p9-parser-security-v1";
    public static final int SURFACE_COUNT = 3;
    public static final int CASES_PER_SURFACE = 6;
    public static final int CASE_COUNT = SURFACE_COUNT * CASES_PER_SURFACE;

    private static final Pattern CASE_ID =
            Pattern.compile("[a-z][a-z0-9_]*(?:[.][a-z0-9_]+){2,5}");
    private static final Pattern ERROR_CODE = Pattern.compile("[A-Z][A-Z0-9_]{2,63}");
    private static final List<CorpusCase> CASES = buildCases();
    private static final String CORPUS_DIGEST = sha256(canonicalCorpus());

    private ParserSecurityCorpusContract() {
    }

    public enum Surface {
        CHECKPOINT,
        SCENARIO_MANIFEST,
        TOOL_SCHEMA
    }

    public enum ThreatClass {
        MALFORMED_INPUT,
        DUPLICATE_FIELD,
        UNKNOWN_FIELD,
        OVERSIZE,
        REPLAY_OR_TAMPER,
        PATH_TRAVERSAL,
        DEPTH_BOMB,
        MISSING_FIELD,
        NULL_VALUE,
        TYPE_CONFUSION,
        VALUE_BOUNDARY
    }

    public static final class CorpusCase {
        private final String caseId;
        private final Surface surface;
        private final ThreatClass threatClass;
        private final String expectedErrorCode;

        private CorpusCase(
                String caseId,
                Surface surface,
                ThreatClass threatClass,
                String expectedErrorCode) {
            if (caseId == null || !CASE_ID.matcher(caseId).matches()) {
                throw new IllegalArgumentException("security corpus caseId is invalid");
            }
            if (expectedErrorCode == null || !ERROR_CODE.matcher(expectedErrorCode).matches()) {
                throw new IllegalArgumentException("security corpus error code is invalid");
            }
            this.caseId = caseId;
            this.surface = Objects.requireNonNull(surface, "surface");
            this.threatClass = Objects.requireNonNull(threatClass, "threatClass");
            this.expectedErrorCode = expectedErrorCode;
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

        public String getExpectedErrorCode() {
            return expectedErrorCode;
        }

        private String canonicalForm() {
            return caseId
                    + "|" + surface.name()
                    + "|" + threatClass.name()
                    + "|" + expectedErrorCode;
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
        throw new IllegalArgumentException("security corpus case is not registered");
    }

    public static String corpusDigest() {
        return CORPUS_DIGEST;
    }

    public static boolean isDeterministicHostRegressionVerified() {
        return true;
    }

    public static boolean isCoverageGuidedFuzzComplete() {
        return false;
    }

    public static boolean isAidlIdentityReviewComplete() {
        return false;
    }

    public static boolean isSignaturePolicyReviewComplete() {
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
        add(cases, "checkpoint.malformed_json.v1", Surface.CHECKPOINT,
                ThreatClass.MALFORMED_INPUT, "MALFORMED_JSON");
        add(cases, "checkpoint.duplicate_field.v1", Surface.CHECKPOINT,
                ThreatClass.DUPLICATE_FIELD, "DUPLICATE_FIELD");
        add(cases, "checkpoint.unknown_field.v1", Surface.CHECKPOINT,
                ThreatClass.UNKNOWN_FIELD, "UNKNOWN_FIELD");
        add(cases, "checkpoint.oversize.v1", Surface.CHECKPOINT,
                ThreatClass.OVERSIZE, "OVERSIZE");
        add(cases, "checkpoint.digest_tamper.v1", Surface.CHECKPOINT,
                ThreatClass.REPLAY_OR_TAMPER, "DIGEST_MISMATCH");
        add(cases, "checkpoint.privileged_path_key.v1", Surface.CHECKPOINT,
                ThreatClass.PATH_TRAVERSAL, "PAYLOAD_REJECTED");

        add(cases, "scenario.invalid_source_path.v1", Surface.SCENARIO_MANIFEST,
                ThreatClass.PATH_TRAVERSAL, "INVALID_SOURCE");
        add(cases, "scenario.unknown_field.v1", Surface.SCENARIO_MANIFEST,
                ThreatClass.UNKNOWN_FIELD, "UNKNOWN_FIELD");
        add(cases, "scenario.duplicate_field.v1", Surface.SCENARIO_MANIFEST,
                ThreatClass.DUPLICATE_FIELD, "DUPLICATE_FIELD");
        add(cases, "scenario.oversize.v1", Surface.SCENARIO_MANIFEST,
                ThreatClass.OVERSIZE, "OVERSIZE");
        add(cases, "scenario.trailing_json.v1", Surface.SCENARIO_MANIFEST,
                ThreatClass.MALFORMED_INPUT, "MALFORMED_JSON");
        add(cases, "scenario.depth_bomb.v1", Surface.SCENARIO_MANIFEST,
                ThreatClass.DEPTH_BOMB, "MALFORMED_JSON");

        add(cases, "tool_schema.missing_field.v1", Surface.TOOL_SCHEMA,
                ThreatClass.MISSING_FIELD, "MISSING_FIELD");
        add(cases, "tool_schema.unknown_field.v1", Surface.TOOL_SCHEMA,
                ThreatClass.UNKNOWN_FIELD, "UNKNOWN_FIELD");
        add(cases, "tool_schema.null_value.v1", Surface.TOOL_SCHEMA,
                ThreatClass.NULL_VALUE, "NULL_VALUE");
        add(cases, "tool_schema.type_confusion.v1", Surface.TOOL_SCHEMA,
                ThreatClass.TYPE_CONFUSION, "TYPE_MISMATCH");
        add(cases, "tool_schema.value_boundary.v1", Surface.TOOL_SCHEMA,
                ThreatClass.VALUE_BOUNDARY, "VALUE_OUT_OF_RANGE");
        add(cases, "tool_schema.payload_oversize.v1", Surface.TOOL_SCHEMA,
                ThreatClass.OVERSIZE, "PAYLOAD_TOO_LARGE");

        if (cases.size() != CASE_COUNT) {
            throw new IllegalStateException("security corpus case count changed");
        }
        for (Surface surface : Surface.values()) {
            long count = cases.stream().filter(value -> value.getSurface() == surface).count();
            if (count != CASES_PER_SURFACE) {
                throw new IllegalStateException("security corpus surface count changed");
            }
        }
        return Collections.unmodifiableList(cases);
    }

    private static void add(
            List<CorpusCase> cases,
            String caseId,
            Surface surface,
            ThreatClass threatClass,
            String expectedErrorCode) {
        for (CorpusCase existing : cases) {
            if (existing.getCaseId().equals(caseId)) {
                throw new IllegalStateException("duplicate security corpus case");
            }
        }
        cases.add(new CorpusCase(caseId, surface, threatClass, expectedErrorCode));
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
