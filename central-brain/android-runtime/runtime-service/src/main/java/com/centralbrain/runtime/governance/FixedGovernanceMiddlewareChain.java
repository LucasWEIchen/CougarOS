package com.centralbrain.runtime.governance;

import com.centralbrain.runtime.skills.BoundedBuiltInSkillRuntime;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Process-local contract for the fixed Runtime and Governance middleware order. */
public final class FixedGovernanceMiddlewareChain {
    private static final int MAX_ID_LENGTH = 128;
    private static final int MAX_OUTPUT_BYTES = 64 * 1024;
    private static final List<StageId> STAGE_ORDER = Collections.unmodifiableList(
            Arrays.asList(
                    StageId.IDENTITY,
                    StageId.SCHEMA,
                    StageId.PRIVACY,
                    StageId.POLICY,
                    StageId.QOS,
                    StageId.TRACE,
                    StageId.DISPATCH_GATE,
                    StageId.OUTPUT_GUARD,
                    StageId.AUDIT));

    public enum StageId {
        IDENTITY,
        SCHEMA,
        PRIVACY,
        POLICY,
        QOS,
        TRACE,
        DISPATCH_GATE,
        OUTPUT_GUARD,
        AUDIT
    }

    public enum StageStatus {
        PASSED,
        REJECTED,
        SKIPPED,
        RECORDED
    }

    public enum Decision {
        ALLOWED,
        DENIED
    }

    public enum ReasonCode {
        NONE,
        IDENTITY_UNTRUSTED,
        IDENTITY_SIGNER_MISMATCH,
        INPUT_SCHEMA_MISMATCH,
        PRIVACY_EXTERNAL_ROUTE_DENIED,
        PRIVACY_PURPOSE_DENIED,
        PRIVACY_CONSENT_REQUIRED,
        POLICY_CAPABILITY_DENIED,
        POLICY_SAFETY_STATE_DENIED,
        QOS_DEADLINE_EXPIRED,
        QOS_BUDGET_INVALID,
        TRACE_CONTEXT_UNTRUSTED,
        DISPATCH_MANIFEST_UNTRUSTED,
        DISPATCH_ROUTE_UNRESOLVED,
        DISPATCH_POLICY_DENIED,
        OUTPUT_SCHEMA_MISMATCH,
        OUTPUT_SIZE_EXCEEDED,
        OUTPUT_REDACTION_REQUIRED,
        PREVIOUS_STAGE_REJECTED,
        AUDIT_RECORDED
    }

    public enum DataClass {
        PUBLIC,
        VEHICLE_SENSITIVE,
        CABIN_PROFILE
    }

    public enum RouteScope {
        LOCAL_PROCESS,
        EXTERNAL
    }

    public enum QosClass {
        INTERACTIVE,
        BACKGROUND
    }

    private final Limits limits;
    private final List<AuditRecord> audits = new ArrayList<>();

    private long evaluationCount;
    private long allowedCount;
    private long deniedCount;
    private long auditSequence;
    private long auditEvictionCount;

    private FixedGovernanceMiddlewareChain(Limits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    public static FixedGovernanceMiddlewareChain createForContractTest(Limits limits) {
        return new FixedGovernanceMiddlewareChain(limits);
    }

    public List<StageId> stageOrder() {
        return STAGE_ORDER;
    }

    public synchronized EvaluationResult evaluate(TrustedExchange exchange) {
        Objects.requireNonNull(exchange, "exchange");
        String requestFingerprint = requestFingerprint(exchange);
        List<StageEvidence> evidence = new ArrayList<>(STAGE_ORDER.size());
        StageId firstRejectedStage = null;
        ReasonCode rejectionReason = ReasonCode.NONE;
        boolean dispatchContractAllowed = false;

        for (StageId stage : STAGE_ORDER) {
            if (stage == StageId.AUDIT) {
                continue;
            }
            if (firstRejectedStage != null) {
                evidence.add(stageEvidence(
                        requestFingerprint,
                        stage,
                        StageStatus.SKIPPED,
                        ReasonCode.PREVIOUS_STAGE_REJECTED));
                continue;
            }
            ReasonCode outcome = evaluateStage(stage, exchange);
            if (outcome == ReasonCode.NONE) {
                evidence.add(stageEvidence(
                        requestFingerprint,
                        stage,
                        StageStatus.PASSED,
                        ReasonCode.NONE));
                if (stage == StageId.DISPATCH_GATE) {
                    dispatchContractAllowed = true;
                }
            } else {
                firstRejectedStage = stage;
                rejectionReason = outcome;
                evidence.add(stageEvidence(
                        requestFingerprint,
                        stage,
                        StageStatus.REJECTED,
                        outcome));
            }
        }

        Decision decision = firstRejectedStage == null ? Decision.ALLOWED : Decision.DENIED;
        AuditRecord audit = appendAudit(
                requestFingerprint,
                decision,
                firstRejectedStage,
                rejectionReason);
        evidence.add(new StageEvidence(
                StageId.AUDIT,
                StageStatus.RECORDED,
                ReasonCode.AUDIT_RECORDED,
                audit.auditDigest));

        evaluationCount++;
        if (decision == Decision.ALLOWED) {
            allowedCount++;
        } else {
            deniedCount++;
        }
        return new EvaluationResult(
                decision,
                firstRejectedStage,
                rejectionReason,
                evidence,
                audit,
                dispatchContractAllowed,
                false);
    }

    public synchronized List<AuditRecord> recentAudits(int limit) {
        if (limit < 1 || limit > limits.maxAuditRecords) {
            throw new IllegalArgumentException("audit limit is invalid");
        }
        int first = Math.max(0, audits.size() - limit);
        return Collections.unmodifiableList(new ArrayList<>(audits.subList(first, audits.size())));
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(
                evaluationCount,
                allowedCount,
                deniedCount,
                audits.size(),
                auditEvictionCount,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false);
    }

    private ReasonCode evaluateStage(StageId stage, TrustedExchange exchange) {
        switch (stage) {
            case IDENTITY:
                if (!exchange.identity.verified) {
                    return ReasonCode.IDENTITY_UNTRUSTED;
                }
                return exchange.identity.sameSigner
                        ? ReasonCode.NONE
                        : ReasonCode.IDENTITY_SIGNER_MISMATCH;
            case SCHEMA:
                return exchange.manifest.getInputSchemaId().equals(exchange.inputSchemaId)
                        ? ReasonCode.NONE
                        : ReasonCode.INPUT_SCHEMA_MISMATCH;
            case PRIVACY:
                if (exchange.privacy.routeScope == RouteScope.EXTERNAL) {
                    return ReasonCode.PRIVACY_EXTERNAL_ROUTE_DENIED;
                }
                if (!exchange.privacy.purposeAuthorized) {
                    return ReasonCode.PRIVACY_PURPOSE_DENIED;
                }
                if (exchange.privacy.dataClass == DataClass.CABIN_PROFILE
                        && !exchange.privacy.consentSatisfied) {
                    return ReasonCode.PRIVACY_CONSENT_REQUIRED;
                }
                return ReasonCode.NONE;
            case POLICY:
                if (!exchange.grantedCapabilities.containsAll(
                        exchange.manifest.getRequiredCapabilities())) {
                    return ReasonCode.POLICY_CAPABILITY_DENIED;
                }
                return exchange.manifest.getAllowedSafetyStates().contains(exchange.safetyState)
                        ? ReasonCode.NONE
                        : ReasonCode.POLICY_SAFETY_STATE_DENIED;
            case QOS:
                if (exchange.qos.nowElapsedMillis < 0
                        || exchange.qos.deadlineElapsedMillis < 0) {
                    return ReasonCode.QOS_BUDGET_INVALID;
                }
                if (exchange.qos.deadlineElapsedMillis <= exchange.qos.nowElapsedMillis) {
                    return ReasonCode.QOS_DEADLINE_EXPIRED;
                }
                long available = exchange.qos.deadlineElapsedMillis
                        - exchange.qos.nowElapsedMillis;
                if (exchange.qos.executionBudgetMillis < 1
                        || exchange.qos.executionBudgetMillis > available
                        || exchange.qos.maxOutputBytes < 1
                        || exchange.qos.maxOutputBytes > MAX_OUTPUT_BYTES) {
                    return ReasonCode.QOS_BUDGET_INVALID;
                }
                return ReasonCode.NONE;
            case TRACE:
                return exchange.trace.trusted
                        ? ReasonCode.NONE
                        : ReasonCode.TRACE_CONTEXT_UNTRUSTED;
            case DISPATCH_GATE:
                if (!exchange.manifest.isCompiledIn()
                        || !exchange.manifest.isSignerAllowlistMatched()
                        || !exchange.manifest.isArtifactDigestBound()
                        || exchange.manifest.isDynamicLoadingAllowed()) {
                    return ReasonCode.DISPATCH_MANIFEST_UNTRUSTED;
                }
                if (!exchange.dispatch.routeOwnerResolved) {
                    return ReasonCode.DISPATCH_ROUTE_UNRESOLVED;
                }
                return exchange.dispatch.routePolicyMatched
                        ? ReasonCode.NONE
                        : ReasonCode.DISPATCH_POLICY_DENIED;
            case OUTPUT_GUARD:
                if (!exchange.manifest.getOutputSchemaId().equals(
                        exchange.output.outputSchemaId)) {
                    return ReasonCode.OUTPUT_SCHEMA_MISMATCH;
                }
                if (exchange.output.outputSizeBytes > exchange.qos.maxOutputBytes) {
                    return ReasonCode.OUTPUT_SIZE_EXCEEDED;
                }
                if (exchange.privacy.dataClass != DataClass.PUBLIC
                        && !exchange.output.redactionApplied) {
                    return ReasonCode.OUTPUT_REDACTION_REQUIRED;
                }
                return ReasonCode.NONE;
            case AUDIT:
                throw new IllegalStateException("AUDIT is a terminal finalizer");
            default:
                throw new IllegalStateException("unknown middleware stage: " + stage);
        }
    }

    private AuditRecord appendAudit(
            String requestFingerprint,
            Decision decision,
            StageId firstRejectedStage,
            ReasonCode reasonCode) {
        long sequence = ++auditSequence;
        String auditDigest = digest(
                "central-brain-governance-middleware-audit-v1",
                Long.toString(sequence),
                requestFingerprint,
                decision.name(),
                firstRejectedStage == null ? "NONE" : firstRejectedStage.name(),
                reasonCode.name());
        AuditRecord audit = new AuditRecord(
                sequence,
                requestFingerprint,
                auditDigest,
                decision,
                firstRejectedStage,
                reasonCode);
        audits.add(audit);
        while (audits.size() > limits.maxAuditRecords) {
            audits.remove(0);
            auditEvictionCount++;
        }
        return audit;
    }

    private static StageEvidence stageEvidence(
            String requestFingerprint,
            StageId stage,
            StageStatus status,
            ReasonCode reason) {
        return new StageEvidence(
                stage,
                status,
                reason,
                digest(
                        "central-brain-governance-middleware-stage-v1",
                        requestFingerprint,
                        stage.name(),
                        status.name(),
                        reason.name()));
    }

    private static String requestFingerprint(TrustedExchange exchange) {
        List<String> fields = new ArrayList<>();
        fields.add("central-brain-governance-middleware-request-v1");
        fields.add(exchange.requestId);
        fields.add(exchange.identity.ownerFingerprint);
        fields.add(exchange.identity.packageSignerDigest);
        fields.add(Boolean.toString(exchange.identity.verified));
        fields.add(Boolean.toString(exchange.identity.sameSigner));
        fields.add(exchange.manifest.getSkillId());
        fields.add(exchange.manifest.getVersion());
        fields.add(exchange.inputSchemaId);
        fields.add(exchange.inputDigest);
        fields.add(exchange.privacy.dataClass.name());
        fields.add(exchange.privacy.routeScope.name());
        fields.add(exchange.privacy.purposeId);
        fields.add(Boolean.toString(exchange.privacy.purposeAuthorized));
        fields.add(Boolean.toString(exchange.privacy.consentSatisfied));
        for (BoundedBuiltInSkillRuntime.Capability capability
                : exchange.grantedCapabilities) {
            fields.add(capability.name());
        }
        fields.add(exchange.safetyState.name());
        fields.add(exchange.qos.qosClass.name());
        fields.add(Long.toString(exchange.qos.nowElapsedMillis));
        fields.add(Long.toString(exchange.qos.deadlineElapsedMillis));
        fields.add(Long.toString(exchange.qos.executionBudgetMillis));
        fields.add(Integer.toString(exchange.qos.maxOutputBytes));
        fields.add(exchange.trace.traceDigest);
        fields.add(Boolean.toString(exchange.trace.trusted));
        fields.add(Boolean.toString(exchange.dispatch.routeOwnerResolved));
        fields.add(Boolean.toString(exchange.dispatch.routePolicyMatched));
        fields.add(exchange.output.outputSchemaId);
        fields.add(exchange.output.outputDigest);
        fields.add(Integer.toString(exchange.output.outputSizeBytes));
        fields.add(Boolean.toString(exchange.output.redactionApplied));
        return digest(fields.toArray(new String[0]));
    }

    private static String digest(String... fields) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        for (String field : fields) {
            byte[] bytes = field.getBytes(StandardCharsets.UTF_8);
            digest.update((byte) (bytes.length >>> 24));
            digest.update((byte) (bytes.length >>> 16));
            digest.update((byte) (bytes.length >>> 8));
            digest.update((byte) bytes.length);
            digest.update(bytes);
        }
        return toHex(digest.digest());
    }

    private static String toHex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] output = new char[bytes.length * 2];
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xff;
            output[index * 2] = digits[value >>> 4];
            output[index * 2 + 1] = digits[value & 0x0f];
        }
        return new String(output);
    }

    private static String requireId(String value, String name) {
        if (value == null
                || value.isEmpty()
                || value.length() > MAX_ID_LENGTH
                || !value.matches("[A-Za-z0-9._:-]+")) {
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

    public static final class Limits {
        private final int maxAuditRecords;

        public Limits(int maxAuditRecords) {
            if (maxAuditRecords < 1 || maxAuditRecords > 1024) {
                throw new IllegalArgumentException("audit record limit is invalid");
            }
            this.maxAuditRecords = maxAuditRecords;
        }
    }

    public static final class IdentityEvidence {
        private final String ownerFingerprint;
        private final String packageSignerDigest;
        private final boolean verified;
        private final boolean sameSigner;

        private IdentityEvidence(
                String ownerFingerprint,
                String packageSignerDigest,
                boolean verified,
                boolean sameSigner) {
            this.ownerFingerprint = requireDigest(ownerFingerprint, "ownerFingerprint");
            this.packageSignerDigest = requireDigest(
                    packageSignerDigest,
                    "packageSignerDigest");
            this.verified = verified;
            this.sameSigner = sameSigner;
        }

        public static IdentityEvidence fromRuntimeIdentity(
                String ownerFingerprint,
                String packageSignerDigest,
                boolean verified,
                boolean sameSigner) {
            return new IdentityEvidence(
                    ownerFingerprint,
                    packageSignerDigest,
                    verified,
                    sameSigner);
        }
    }

    public static final class PrivacyEvidence {
        private final DataClass dataClass;
        private final RouteScope routeScope;
        private final String purposeId;
        private final boolean purposeAuthorized;
        private final boolean consentSatisfied;

        private PrivacyEvidence(
                DataClass dataClass,
                RouteScope routeScope,
                String purposeId,
                boolean purposeAuthorized,
                boolean consentSatisfied) {
            this.dataClass = Objects.requireNonNull(dataClass, "dataClass");
            this.routeScope = Objects.requireNonNull(routeScope, "routeScope");
            this.purposeId = requireId(purposeId, "purposeId");
            this.purposeAuthorized = purposeAuthorized;
            this.consentSatisfied = consentSatisfied;
        }

        public static PrivacyEvidence fromRuntimePolicy(
                DataClass dataClass,
                RouteScope routeScope,
                String purposeId,
                boolean purposeAuthorized,
                boolean consentSatisfied) {
            return new PrivacyEvidence(
                    dataClass,
                    routeScope,
                    purposeId,
                    purposeAuthorized,
                    consentSatisfied);
        }
    }

    public static final class QosEvidence {
        private final QosClass qosClass;
        private final long nowElapsedMillis;
        private final long deadlineElapsedMillis;
        private final long executionBudgetMillis;
        private final int maxOutputBytes;

        private QosEvidence(
                QosClass qosClass,
                long nowElapsedMillis,
                long deadlineElapsedMillis,
                long executionBudgetMillis,
                int maxOutputBytes) {
            this.qosClass = Objects.requireNonNull(qosClass, "qosClass");
            this.nowElapsedMillis = nowElapsedMillis;
            this.deadlineElapsedMillis = deadlineElapsedMillis;
            this.executionBudgetMillis = executionBudgetMillis;
            this.maxOutputBytes = maxOutputBytes;
        }

        public static QosEvidence fromRuntimePolicy(
                QosClass qosClass,
                long nowElapsedMillis,
                long deadlineElapsedMillis,
                long executionBudgetMillis,
                int maxOutputBytes) {
            return new QosEvidence(
                    qosClass,
                    nowElapsedMillis,
                    deadlineElapsedMillis,
                    executionBudgetMillis,
                    maxOutputBytes);
        }
    }

    public static final class TraceEvidence {
        private final String traceDigest;
        private final boolean trusted;

        private TraceEvidence(String traceDigest, boolean trusted) {
            this.traceDigest = requireDigest(traceDigest, "traceDigest");
            this.trusted = trusted;
        }

        public static TraceEvidence fromRuntimeTrace(String traceDigest, boolean trusted) {
            return new TraceEvidence(traceDigest, trusted);
        }
    }

    public static final class DispatchEvidence {
        private final boolean routeOwnerResolved;
        private final boolean routePolicyMatched;

        private DispatchEvidence(boolean routeOwnerResolved, boolean routePolicyMatched) {
            this.routeOwnerResolved = routeOwnerResolved;
            this.routePolicyMatched = routePolicyMatched;
        }

        public static DispatchEvidence fromRuntimeRegistry(
                boolean routeOwnerResolved,
                boolean routePolicyMatched) {
            return new DispatchEvidence(routeOwnerResolved, routePolicyMatched);
        }
    }

    public static final class OutputEvidence {
        private final String outputSchemaId;
        private final String outputDigest;
        private final int outputSizeBytes;
        private final boolean redactionApplied;

        private OutputEvidence(
                String outputSchemaId,
                String outputDigest,
                int outputSizeBytes,
                boolean redactionApplied) {
            this.outputSchemaId = requireId(outputSchemaId, "outputSchemaId");
            this.outputDigest = requireDigest(outputDigest, "outputDigest");
            if (outputSizeBytes < 0) {
                throw new IllegalArgumentException("outputSizeBytes cannot be negative");
            }
            this.outputSizeBytes = outputSizeBytes;
            this.redactionApplied = redactionApplied;
        }

        public static OutputEvidence fromContractFixture(
                String outputSchemaId,
                String outputDigest,
                int outputSizeBytes,
                boolean redactionApplied) {
            return new OutputEvidence(
                    outputSchemaId,
                    outputDigest,
                    outputSizeBytes,
                    redactionApplied);
        }
    }

    public static final class TrustedExchange {
        private final String requestId;
        private final BoundedBuiltInSkillRuntime.SkillManifest manifest;
        private final IdentityEvidence identity;
        private final String inputSchemaId;
        private final String inputDigest;
        private final PrivacyEvidence privacy;
        private final Set<BoundedBuiltInSkillRuntime.Capability> grantedCapabilities;
        private final BoundedBuiltInSkillRuntime.SafetyState safetyState;
        private final QosEvidence qos;
        private final TraceEvidence trace;
        private final DispatchEvidence dispatch;
        private final OutputEvidence output;

        private TrustedExchange(
                String requestId,
                BoundedBuiltInSkillRuntime.SkillManifest manifest,
                IdentityEvidence identity,
                String inputSchemaId,
                String inputDigest,
                PrivacyEvidence privacy,
                Set<BoundedBuiltInSkillRuntime.Capability> grantedCapabilities,
                BoundedBuiltInSkillRuntime.SafetyState safetyState,
                QosEvidence qos,
                TraceEvidence trace,
                DispatchEvidence dispatch,
                OutputEvidence output) {
            this.requestId = requireId(requestId, "requestId");
            this.manifest = Objects.requireNonNull(manifest, "manifest");
            this.identity = Objects.requireNonNull(identity, "identity");
            this.inputSchemaId = requireId(inputSchemaId, "inputSchemaId");
            this.inputDigest = requireDigest(inputDigest, "inputDigest");
            this.privacy = Objects.requireNonNull(privacy, "privacy");
            this.grantedCapabilities = grantedCapabilities == null
                    || grantedCapabilities.isEmpty()
                    ? Collections.emptySet()
                    : Collections.unmodifiableSet(EnumSet.copyOf(grantedCapabilities));
            this.safetyState = Objects.requireNonNull(safetyState, "safetyState");
            this.qos = Objects.requireNonNull(qos, "qos");
            this.trace = Objects.requireNonNull(trace, "trace");
            this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
            this.output = Objects.requireNonNull(output, "output");
        }

        public static TrustedExchange fromRuntimePolicy(
                String requestId,
                BoundedBuiltInSkillRuntime.SkillManifest manifest,
                IdentityEvidence identity,
                String inputSchemaId,
                String inputDigest,
                PrivacyEvidence privacy,
                Set<BoundedBuiltInSkillRuntime.Capability> grantedCapabilities,
                BoundedBuiltInSkillRuntime.SafetyState safetyState,
                QosEvidence qos,
                TraceEvidence trace,
                DispatchEvidence dispatch,
                OutputEvidence output) {
            return new TrustedExchange(
                    requestId,
                    manifest,
                    identity,
                    inputSchemaId,
                    inputDigest,
                    privacy,
                    grantedCapabilities,
                    safetyState,
                    qos,
                    trace,
                    dispatch,
                    output);
        }
    }

    public static final class StageEvidence {
        private final StageId stage;
        private final StageStatus status;
        private final ReasonCode reasonCode;
        private final String evidenceDigest;

        private StageEvidence(
                StageId stage,
                StageStatus status,
                ReasonCode reasonCode,
                String evidenceDigest) {
            this.stage = stage;
            this.status = status;
            this.reasonCode = reasonCode;
            this.evidenceDigest = evidenceDigest;
        }

        public StageId getStage() {
            return stage;
        }

        public StageStatus getStatus() {
            return status;
        }

        public ReasonCode getReasonCode() {
            return reasonCode;
        }

        public String getEvidenceDigest() {
            return evidenceDigest;
        }
    }

    public static final class AuditRecord {
        private final long sequence;
        private final String requestFingerprint;
        private final String auditDigest;
        private final Decision decision;
        private final StageId firstRejectedStage;
        private final ReasonCode reasonCode;

        private AuditRecord(
                long sequence,
                String requestFingerprint,
                String auditDigest,
                Decision decision,
                StageId firstRejectedStage,
                ReasonCode reasonCode) {
            this.sequence = sequence;
            this.requestFingerprint = requestFingerprint;
            this.auditDigest = auditDigest;
            this.decision = decision;
            this.firstRejectedStage = firstRejectedStage;
            this.reasonCode = reasonCode;
        }

        public long getSequence() {
            return sequence;
        }

        public String getRequestFingerprint() {
            return requestFingerprint;
        }

        public String getAuditDigest() {
            return auditDigest;
        }

        public Decision getDecision() {
            return decision;
        }

        public StageId getFirstRejectedStage() {
            return firstRejectedStage;
        }

        public ReasonCode getReasonCode() {
            return reasonCode;
        }
    }

    public static final class EvaluationResult {
        private final Decision decision;
        private final StageId firstRejectedStage;
        private final ReasonCode reasonCode;
        private final List<StageEvidence> stages;
        private final AuditRecord audit;
        private final boolean dispatchContractAllowed;
        private final boolean serviceDispatchTriggered;

        private EvaluationResult(
                Decision decision,
                StageId firstRejectedStage,
                ReasonCode reasonCode,
                List<StageEvidence> stages,
                AuditRecord audit,
                boolean dispatchContractAllowed,
                boolean serviceDispatchTriggered) {
            this.decision = decision;
            this.firstRejectedStage = firstRejectedStage;
            this.reasonCode = reasonCode;
            this.stages = Collections.unmodifiableList(new ArrayList<>(stages));
            this.audit = audit;
            this.dispatchContractAllowed = dispatchContractAllowed;
            this.serviceDispatchTriggered = serviceDispatchTriggered;
        }

        public Decision getDecision() {
            return decision;
        }

        public StageId getFirstRejectedStage() {
            return firstRejectedStage;
        }

        public ReasonCode getReasonCode() {
            return reasonCode;
        }

        public List<StageEvidence> getStages() {
            return stages;
        }

        public StageEvidence findStage(StageId stage) {
            for (StageEvidence evidence : stages) {
                if (evidence.stage == stage) {
                    return evidence;
                }
            }
            return null;
        }

        public AuditRecord getAudit() {
            return audit;
        }

        public boolean isDispatchContractAllowed() {
            return dispatchContractAllowed;
        }

        public boolean isServiceDispatchTriggered() {
            return serviceDispatchTriggered;
        }
    }

    public static final class Snapshot {
        private final long evaluationCount;
        private final long allowedCount;
        private final long deniedCount;
        private final int retainedAuditCount;
        private final long auditEvictionCount;
        private final boolean productionMiddlewareWired;
        private final boolean dispatchExecutionEnabled;
        private final boolean serviceDispatchTriggered;
        private final boolean rawInputStored;
        private final boolean rawOutputStored;
        private final boolean auditPersistenceWired;
        private final boolean networkAccessEnabled;
        private final boolean hardwareAccessed;

        private Snapshot(
                long evaluationCount,
                long allowedCount,
                long deniedCount,
                int retainedAuditCount,
                long auditEvictionCount,
                boolean productionMiddlewareWired,
                boolean dispatchExecutionEnabled,
                boolean serviceDispatchTriggered,
                boolean rawInputStored,
                boolean rawOutputStored,
                boolean auditPersistenceWired,
                boolean networkAccessEnabled,
                boolean hardwareAccessed) {
            this.evaluationCount = evaluationCount;
            this.allowedCount = allowedCount;
            this.deniedCount = deniedCount;
            this.retainedAuditCount = retainedAuditCount;
            this.auditEvictionCount = auditEvictionCount;
            this.productionMiddlewareWired = productionMiddlewareWired;
            this.dispatchExecutionEnabled = dispatchExecutionEnabled;
            this.serviceDispatchTriggered = serviceDispatchTriggered;
            this.rawInputStored = rawInputStored;
            this.rawOutputStored = rawOutputStored;
            this.auditPersistenceWired = auditPersistenceWired;
            this.networkAccessEnabled = networkAccessEnabled;
            this.hardwareAccessed = hardwareAccessed;
        }

        public List<StageId> getStageOrder() {
            return STAGE_ORDER;
        }

        public long getEvaluationCount() {
            return evaluationCount;
        }

        public long getAllowedCount() {
            return allowedCount;
        }

        public long getDeniedCount() {
            return deniedCount;
        }

        public int getRetainedAuditCount() {
            return retainedAuditCount;
        }

        public long getAuditEvictionCount() {
            return auditEvictionCount;
        }

        public boolean isProductionMiddlewareWired() {
            return productionMiddlewareWired;
        }

        public boolean isDispatchExecutionEnabled() {
            return dispatchExecutionEnabled;
        }

        public boolean isServiceDispatchTriggered() {
            return serviceDispatchTriggered;
        }

        public boolean isRawInputStored() {
            return rawInputStored;
        }

        public boolean isRawOutputStored() {
            return rawOutputStored;
        }

        public boolean isAuditPersistenceWired() {
            return auditPersistenceWired;
        }

        public boolean isNetworkAccessEnabled() {
            return networkAccessEnabled;
        }

        public boolean isHardwareAccessed() {
            return hardwareAccessed;
        }
    }
}
