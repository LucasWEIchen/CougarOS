package com.centralbrain.sdk.effect;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Bounded validation for the Stage 2 Effect/Approval AIDL V1 contract. */
public final class EffectContract {
    public static final int SCHEMA_VERSION = 1;
    public static final String AIDL_CONTRACT_HASH =
            "709828114422595f1889dad58e8e60daf4d5e4f98c962a6145f2f8a39b0c178d";

    public static final int MAX_NODE_ID_CHARS = 64;
    public static final int MAX_CAPABILITY_ID_CHARS = 96;
    public static final int MAX_TARGET_AREA_CHARS = 96;
    public static final int MAX_TARGET_TEXT_CHARS = 128;
    public static final int MAX_UNIT_CHARS = 32;
    public static final int MAX_IDEMPOTENCY_KEY_CHARS = 128;
    public static final int MAX_SOURCE_ID_CHARS = 96;
    public static final int MAX_POLICY_ID_CHARS = 96;
    public static final int MAX_CODE_CHARS = 64;
    public static final int MAX_ATTEMPTS = 3;
    public static final long MAX_EFFECT_DEADLINE_MS = 15 * 60 * 1000L;
    public static final long MAX_APPROVAL_TTL_MS = 5 * 60 * 1000L;
    public static final long MAX_UNDO_TTL_MS = 15 * 60 * 1000L;

    public static final int VALUE_BOOLEAN = 1;
    public static final int VALUE_INTEGER = 2;
    public static final int VALUE_DECIMAL = 3;
    public static final int VALUE_TEXT = 4;

    public static final int RISK_LOW = 1;
    public static final int RISK_MEDIUM = 2;
    public static final int RISK_HIGH = 3;
    public static final int RISK_CRITICAL = 4;

    public static final int VERIFY_CALLBACK_ONLY = 1;
    public static final int VERIFY_REPORTED_EQUALS = 2;
    public static final int VERIFY_REPORTED_TOLERANCE = 3;
    public static final int VERIFY_STATE_TRANSITION = 4;
    public static final int VERIFY_COMPOSITE = 5;

    public static final int STATE_PROPOSED = 1;
    public static final int STATE_AUTHORIZED = 2;
    public static final int STATE_REJECTED = 3;
    public static final int STATE_PREPARED = 4;
    public static final int STATE_DISPATCHED = 5;
    public static final int STATE_DELIVERED = 6;
    public static final int STATE_APPLIED = 7;
    public static final int STATE_VERIFIED = 8;
    public static final int STATE_UNKNOWN = 9;
    public static final int STATE_FAILED_RETRYABLE = 10;
    public static final int STATE_FAILED_TERMINAL = 11;
    public static final int STATE_COMPENSATING = 12;
    public static final int STATE_COMPENSATED = 13;
    public static final int STATE_CANCELLED = 14;

    public static final int SOURCE_RUNTIME = 1;
    public static final int SOURCE_ADAPTER = 2;
    public static final int SOURCE_SIMULATION = 3;

    public static final int UNDO_AVAILABLE = 1;
    public static final int UNDO_REQUESTED = 2;
    public static final int UNDO_EXPIRED = 3;
    public static final int UNDO_REJECTED = 4;
    public static final int UNDO_COMPLETED = 5;

    private static final Pattern LOCAL_ID =
            Pattern.compile("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*");
    private static final Pattern QUALIFIED_ID =
            Pattern.compile("[a-z][a-z0-9_-]*(?:[.][a-z0-9][a-z0-9_-]*){1,7}");
    private static final Pattern IDEMPOTENCY_KEY =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
    private static final Pattern TARGET_TEXT =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/ -]{0,127}");
    private static final Pattern UNIT = Pattern.compile("[A-Za-z0-9%/._-]{0,31}");
    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    private EffectContract() {}

    public static void validateIntent(EffectIntent intent, long nowEpochMs) {
        requireNotNull(intent, "intent");
        requireVersion(intent.schemaVersion, "intent.schemaVersion");
        requireUuid(intent.effectId, "intent.effectId");
        requireUuid(intent.sessionId, "intent.sessionId");
        requireUuid(intent.planId, "intent.planId");
        requireIdentifier(intent.nodeId, MAX_NODE_ID_CHARS, LOCAL_ID, "intent.nodeId");
        requireUuid(intent.actionId, "intent.actionId");
        requireIdentifier(
                intent.capabilityId,
                MAX_CAPABILITY_ID_CHARS,
                QUALIFIED_ID,
                "intent.capabilityId");
        requireIdentifier(
                intent.targetArea,
                MAX_TARGET_AREA_CHARS,
                QUALIFIED_ID,
                "intent.targetArea");
        validateTargetValue(intent);
        requireDigest(intent.targetValueDigest, "intent.targetValueDigest");
        requireIdentifier(
                intent.idempotencyKey,
                MAX_IDEMPOTENCY_KEY_CHARS,
                IDEMPOTENCY_KEY,
                "intent.idempotencyKey");
        requireDigest(intent.planDigest, "intent.planDigest");
        requireDigest(intent.contextDigest, "intent.contextDigest");
        if (intent.contextVersion < 1) {
            throw violation("intent.contextVersion must be positive");
        }
        requireRisk(intent.riskClass, "intent.riskClass");
        if (intent.verificationPolicy < VERIFY_CALLBACK_ONLY
                || intent.verificationPolicy > VERIFY_COMPOSITE) {
            throw violation("intent.verificationPolicy is unknown");
        }
        if (intent.verificationPolicy == VERIFY_REPORTED_TOLERANCE) {
            if (!Double.isFinite(intent.verificationTolerance)
                    || intent.verificationTolerance <= 0.0) {
                throw violation("tolerance verification requires a positive finite tolerance");
            }
        } else if (Double.compare(intent.verificationTolerance, 0.0) != 0) {
            throw violation("non-tolerance verification must not carry a tolerance");
        }
        if (intent.reversible) {
            requireDigest(intent.compensationDigest, "intent.compensationDigest");
        } else if (!isEmpty(intent.compensationDigest)) {
            throw violation("irreversible intent must not carry compensation material");
        }
        requireNow(nowEpochMs);
        if (intent.createdAtEpochMs <= 0 || intent.createdAtEpochMs > nowEpochMs) {
            throw violation("intent.createdAtEpochMs is invalid");
        }
        if (intent.deadlineEpochMs <= nowEpochMs
                || intent.deadlineEpochMs <= intent.createdAtEpochMs
                || intent.deadlineEpochMs - intent.createdAtEpochMs
                        > MAX_EFFECT_DEADLINE_MS) {
            throw violation("intent.deadlineEpochMs is outside the bounded execution window");
        }
    }

    public static void validateObservation(EffectObservation observation) {
        requireNotNull(observation, "observation");
        requireVersion(observation.schemaVersion, "observation.schemaVersion");
        requireUuid(observation.observationId, "observation.observationId");
        requireUuid(observation.effectId, "observation.effectId");
        requireUuid(observation.sessionId, "observation.sessionId");
        requireUuid(observation.actionId, "observation.actionId");
        requireDigest(observation.planDigest, "observation.planDigest");
        if (observation.contextVersion < 1) {
            throw violation("observation.contextVersion must be positive");
        }
        if (observation.state < STATE_PROPOSED || observation.state > STATE_CANCELLED) {
            throw violation("observation.state is unknown");
        }
        if (observation.source < SOURCE_RUNTIME || observation.source > SOURCE_SIMULATION) {
            throw violation("observation.source is unknown");
        }
        requireIdentifier(
                observation.sourceId,
                MAX_SOURCE_ID_CHARS,
                QUALIFIED_ID,
                "observation.sourceId");
        if ((observation.source == SOURCE_SIMULATION) != observation.simulated) {
            throw violation("simulation source and marker must agree");
        }
        validateAttempt(observation);
        requireDigest(observation.targetValueDigest, "observation.targetValueDigest");
        if (!isEmpty(observation.reportedValueDigest)) {
            requireDigest(observation.reportedValueDigest, "observation.reportedValueDigest");
        }
        if (requiresReportedValue(observation.state)
                && isEmpty(observation.reportedValueDigest)) {
            throw violation("applied or verified observation requires reported value evidence");
        }
        requireDigest(observation.evidenceDigest, "observation.evidenceDigest");
        requireDigest(observation.observationDigest, "observation.observationDigest");
        validateFailureCode(observation);
        if (observation.occurredAtEpochMs <= 0) {
            throw violation("observation.occurredAtEpochMs is invalid");
        }
        if (observation.terminal != isTerminalState(observation.state)) {
            throw violation("observation terminal marker does not match state");
        }
        if (observation.retryable != (observation.state == STATE_FAILED_RETRYABLE)) {
            throw violation("observation retryable marker does not match state");
        }
    }

    public static void validateTransition(
            EffectObservation previous,
            EffectObservation next) {
        validateObservation(previous);
        validateObservation(next);
        if (!sameEffectBinding(previous, next)) {
            throw violation("effect transition changes immutable operation binding");
        }
        if (previous.observationId.equals(next.observationId)
                || previous.observationDigest.equals(next.observationDigest)) {
            throw violation("effect transition reuses observation identity");
        }
        if (previous.terminal) {
            throw violation("terminal effect state cannot transition");
        }
        if (next.occurredAtEpochMs < previous.occurredAtEpochMs) {
            throw violation("effect transition timestamp moves backwards");
        }
        if (!isAllowedTransition(previous.state, next.state)) {
            throw violation("effect state transition is not allowed");
        }
        validateAttemptTransition(previous, next);
    }

    public static void validateApprovalPrompt(ApprovalPrompt prompt) {
        requireNotNull(prompt, "prompt");
        requireVersion(prompt.schemaVersion, "prompt.schemaVersion");
        requireUuid(prompt.approvalId, "prompt.approvalId");
        requireUuid(prompt.sessionId, "prompt.sessionId");
        requireUuid(prompt.planId, "prompt.planId");
        requireIdentifier(prompt.nodeId, MAX_NODE_ID_CHARS, LOCAL_ID, "prompt.nodeId");
        requireUuid(prompt.actionId, "prompt.actionId");
        requireUuid(prompt.effectId, "prompt.effectId");
        requireDigest(prompt.planDigest, "prompt.planDigest");
        requireDigest(prompt.actionDigest, "prompt.actionDigest");
        requireDigest(prompt.targetValueDigest, "prompt.targetValueDigest");
        requireDigest(prompt.contextDigest, "prompt.contextDigest");
        if (prompt.contextVersion < 1) {
            throw violation("prompt.contextVersion must be positive");
        }
        requireIdentifier(
                prompt.policyId,
                MAX_POLICY_ID_CHARS,
                QUALIFIED_ID,
                "prompt.policyId");
        if (prompt.policyVersion < 1) {
            throw violation("prompt.policyVersion must be positive");
        }
        requireRisk(prompt.riskClass, "prompt.riskClass");
        requireIdentifier(prompt.reasonCode, MAX_CODE_CHARS, CODE, "prompt.reasonCode");
        requireIdentifier(
                prompt.promptCode,
                MAX_POLICY_ID_CHARS,
                QUALIFIED_ID,
                "prompt.promptCode");
        requireDigest(prompt.approvalDigest, "prompt.approvalDigest");
        validateTtl(
                prompt.createdAtEpochMs,
                prompt.expiresAtEpochMs,
                MAX_APPROVAL_TTL_MS,
                "prompt");
    }

    public static void validateApprovalResume(
            ApprovalPrompt prompt,
            String expectedPlanDigest,
            String expectedActionDigest,
            String expectedContextDigest,
            long expectedContextVersion,
            long nowEpochMs) {
        validateApprovalPrompt(prompt);
        requireDigest(expectedPlanDigest, "expectedPlanDigest");
        requireDigest(expectedActionDigest, "expectedActionDigest");
        requireDigest(expectedContextDigest, "expectedContextDigest");
        requireNow(nowEpochMs);
        if (expectedContextVersion < 1) {
            throw violation("expectedContextVersion must be positive");
        }
        if (nowEpochMs < prompt.createdAtEpochMs || nowEpochMs >= prompt.expiresAtEpochMs) {
            throw violation("approval is not active at resume time");
        }
        if (!prompt.planDigest.equals(expectedPlanDigest)
                || !prompt.actionDigest.equals(expectedActionDigest)
                || !prompt.contextDigest.equals(expectedContextDigest)
                || prompt.contextVersion != expectedContextVersion) {
            throw violation("stale approval binding rejected");
        }
    }

    public static void validateUndoHandle(UndoHandle handle) {
        requireNotNull(handle, "handle");
        requireVersion(handle.schemaVersion, "handle.schemaVersion");
        requireUuid(handle.undoId, "handle.undoId");
        requireUuid(handle.sessionId, "handle.sessionId");
        requireUuid(handle.effectId, "handle.effectId");
        requireUuid(handle.sourceObservationId, "handle.sourceObservationId");
        requireIdentifier(
                handle.capabilityId,
                MAX_CAPABILITY_ID_CHARS,
                QUALIFIED_ID,
                "handle.capabilityId");
        requireDigest(handle.planDigest, "handle.planDigest");
        requireDigest(
                handle.verifiedObservationDigest,
                "handle.verifiedObservationDigest");
        requireDigest(handle.compensationDigest, "handle.compensationDigest");
        requireDigest(handle.handleDigest, "handle.handleDigest");
        if (handle.issuedContextVersion < 1) {
            throw violation("handle.issuedContextVersion must be positive");
        }
        if (handle.state < UNDO_AVAILABLE || handle.state > UNDO_COMPLETED) {
            throw violation("handle.state is unknown");
        }
        boolean reasonRequired = handle.state == UNDO_EXPIRED || handle.state == UNDO_REJECTED;
        if (reasonRequired) {
            requireIdentifier(handle.reasonCode, MAX_CODE_CHARS, CODE, "handle.reasonCode");
        } else if (!isEmpty(handle.reasonCode)) {
            throw violation("undo state must not carry a reason code");
        }
        validateTtl(
                handle.createdAtEpochMs,
                handle.expiresAtEpochMs,
                MAX_UNDO_TTL_MS,
                "handle");
    }

    public static void validateUndoRequest(
            UndoHandle handle,
            String expectedPlanDigest,
            String expectedVerifiedObservationDigest,
            long currentContextVersion,
            long nowEpochMs) {
        validateUndoHandle(handle);
        requireDigest(expectedPlanDigest, "expectedPlanDigest");
        requireDigest(
                expectedVerifiedObservationDigest,
                "expectedVerifiedObservationDigest");
        requireNow(nowEpochMs);
        if (currentContextVersion < handle.issuedContextVersion) {
            throw violation("undo current Context version regressed");
        }
        if (handle.state != UNDO_AVAILABLE
                || nowEpochMs < handle.createdAtEpochMs
                || nowEpochMs >= handle.expiresAtEpochMs) {
            throw violation("undo handle is not currently available");
        }
        if (!handle.planDigest.equals(expectedPlanDigest)
                || !handle.verifiedObservationDigest.equals(
                        expectedVerifiedObservationDigest)) {
            throw violation("undo handle binding does not match verified effect");
        }
    }

    private static void validateTargetValue(EffectIntent intent) {
        requireBounded(intent.textValue, MAX_TARGET_TEXT_CHARS, "intent.textValue");
        requireBounded(intent.unit, MAX_UNIT_CHARS, "intent.unit");
        if (!UNIT.matcher(intent.unit).matches()) {
            throw violation("intent.unit has an invalid shape");
        }
        switch (intent.valueKind) {
            case VALUE_BOOLEAN:
                requireUnusedScalars(intent, true, false, false);
                break;
            case VALUE_INTEGER:
                requireUnusedScalars(intent, false, true, false);
                break;
            case VALUE_DECIMAL:
                if (!Double.isFinite(intent.decimalValue)) {
                    throw violation("intent.decimalValue must be finite");
                }
                requireUnusedScalars(intent, false, false, true);
                break;
            case VALUE_TEXT:
                if (intent.textValue.isEmpty() || !TARGET_TEXT.matcher(intent.textValue).matches()) {
                    throw violation("intent.textValue has an invalid bounded scalar shape");
                }
                requireUnusedScalars(intent, false, false, false);
                break;
            default:
                throw violation("intent.valueKind is unknown");
        }
    }

    private static void requireUnusedScalars(
            EffectIntent intent,
            boolean booleanUsed,
            boolean integerUsed,
            boolean decimalUsed) {
        if ((!booleanUsed && intent.booleanValue)
                || (!integerUsed && intent.integerValue != 0)
                || (!decimalUsed && Double.compare(intent.decimalValue, 0.0) != 0)
                || (intent.valueKind != VALUE_TEXT && !intent.textValue.isEmpty())) {
            throw violation("intent carries data in an inactive typed-value field");
        }
    }

    private static void validateAttempt(EffectObservation observation) {
        if (observation.attempt < 0 || observation.attempt > MAX_ATTEMPTS) {
            throw violation("observation.attempt is outside the bounded range");
        }
        if ((observation.state == STATE_PROPOSED
                        || observation.state == STATE_AUTHORIZED
                        || observation.state == STATE_REJECTED)
                && observation.attempt != 0) {
            throw violation("preparation-independent state must use attempt zero");
        }
        if (observation.state >= STATE_PREPARED
                && observation.state <= STATE_COMPENSATED
                && observation.attempt < 1) {
            throw violation("prepared or later state requires a positive attempt");
        }
    }

    private static void validateAttemptTransition(
            EffectObservation previous,
            EffectObservation next) {
        if (previous.state == STATE_AUTHORIZED && next.state == STATE_PREPARED) {
            if (previous.attempt != 0 || next.attempt != 1) {
                throw violation("initial prepare must start attempt one");
            }
            return;
        }
        if (previous.state == STATE_FAILED_RETRYABLE && next.state == STATE_PREPARED) {
            if (previous.attempt >= MAX_ATTEMPTS
                    || next.attempt != previous.attempt + 1) {
                throw violation("bounded retry must increment the attempt exactly once");
            }
            return;
        }
        if (next.attempt != previous.attempt) {
            throw violation("effect transition changes attempt unexpectedly");
        }
    }

    private static boolean isAllowedTransition(int previous, int next) {
        switch (previous) {
            case STATE_PROPOSED:
                return next == STATE_AUTHORIZED
                        || next == STATE_REJECTED
                        || next == STATE_CANCELLED;
            case STATE_AUTHORIZED:
                return next == STATE_PREPARED
                        || next == STATE_REJECTED
                        || next == STATE_CANCELLED;
            case STATE_PREPARED:
                return next == STATE_DISPATCHED
                        || next == STATE_FAILED_RETRYABLE
                        || next == STATE_FAILED_TERMINAL
                        || next == STATE_CANCELLED;
            case STATE_DISPATCHED:
                return next == STATE_DELIVERED
                        || next == STATE_UNKNOWN
                        || next == STATE_FAILED_RETRYABLE
                        || next == STATE_FAILED_TERMINAL;
            case STATE_DELIVERED:
                return next == STATE_APPLIED
                        || next == STATE_UNKNOWN
                        || next == STATE_FAILED_RETRYABLE
                        || next == STATE_FAILED_TERMINAL;
            case STATE_APPLIED:
                return next == STATE_VERIFIED
                        || next == STATE_UNKNOWN
                        || next == STATE_FAILED_TERMINAL;
            case STATE_UNKNOWN:
                return next == STATE_APPLIED
                        || next == STATE_VERIFIED
                        || next == STATE_FAILED_TERMINAL;
            case STATE_FAILED_RETRYABLE:
                return next == STATE_PREPARED
                        || next == STATE_FAILED_TERMINAL
                        || next == STATE_CANCELLED;
            case STATE_COMPENSATING:
                return next == STATE_COMPENSATED || next == STATE_FAILED_TERMINAL;
            default:
                return false;
        }
    }

    private static boolean sameEffectBinding(
            EffectObservation left,
            EffectObservation right) {
        return left.schemaVersion == right.schemaVersion
                && left.contextVersion == right.contextVersion
                && Objects.equals(left.effectId, right.effectId)
                && Objects.equals(left.sessionId, right.sessionId)
                && Objects.equals(left.actionId, right.actionId)
                && Objects.equals(left.planDigest, right.planDigest)
                && Objects.equals(left.targetValueDigest, right.targetValueDigest);
    }

    private static boolean requiresReportedValue(int state) {
        return state == STATE_APPLIED
                || state == STATE_VERIFIED
                || state == STATE_COMPENSATING
                || state == STATE_COMPENSATED;
    }

    private static boolean isTerminalState(int state) {
        return state == STATE_REJECTED
                || state == STATE_VERIFIED
                || state == STATE_FAILED_TERMINAL
                || state == STATE_COMPENSATED
                || state == STATE_CANCELLED;
    }

    private static void validateFailureCode(EffectObservation observation) {
        boolean reasonRequired = observation.state == STATE_REJECTED
                || observation.state == STATE_UNKNOWN
                || observation.state == STATE_FAILED_RETRYABLE
                || observation.state == STATE_FAILED_TERMINAL
                || observation.state == STATE_CANCELLED;
        if (reasonRequired) {
            requireIdentifier(
                    observation.failureCode,
                    MAX_CODE_CHARS,
                    CODE,
                    "observation.failureCode");
        } else if (!isEmpty(observation.failureCode)) {
            throw violation("successful effect state must not carry a failure code");
        }
    }

    private static void validateTtl(
            long createdAtEpochMs,
            long expiresAtEpochMs,
            long maxTtlMs,
            String fieldPrefix) {
        if (createdAtEpochMs <= 0
                || expiresAtEpochMs <= createdAtEpochMs
                || expiresAtEpochMs - createdAtEpochMs > maxTtlMs) {
            throw violation(fieldPrefix + " TTL is invalid or exceeds the bounded window");
        }
    }

    private static void requireRisk(int riskClass, String field) {
        if (riskClass < RISK_LOW || riskClass > RISK_CRITICAL) {
            throw violation(field + " is unknown");
        }
    }

    private static void requireVersion(int version, String field) {
        if (version != SCHEMA_VERSION) {
            throw violation(field + " is unsupported");
        }
    }

    private static void requireUuid(String value, String field) {
        requireBounded(value, 36, field);
        if (value.length() != 36) {
            throw violation(field + " is not a canonical UUID");
        }
        try {
            if (!UUID.fromString(value).toString().equals(value.toLowerCase(Locale.ROOT))) {
                throw violation(field + " is not a canonical UUID");
            }
        } catch (IllegalArgumentException invalid) {
            throw violation(field + " is not a canonical UUID");
        }
    }

    private static void requireDigest(String value, String field) {
        requireBounded(value, 64, field);
        if (!SHA_256.matcher(value).matches()) {
            throw violation(field + " is not a lowercase SHA-256 digest");
        }
    }

    private static void requireIdentifier(
            String value,
            int maxChars,
            Pattern pattern,
            String field) {
        requireBounded(value, maxChars, field);
        if (value.isEmpty() || !pattern.matcher(value).matches()) {
            throw violation(field + " has an invalid identifier shape");
        }
    }

    private static void requireBounded(String value, int maxChars, String field) {
        if (value == null) {
            throw violation(field + " is null");
        }
        if (value.length() > maxChars) {
            throw violation(field + " exceeds " + maxChars + " characters");
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isISOControl(character)) {
                throw violation(field + " contains a forbidden control character");
            }
        }
    }

    private static void requireNotNull(Object value, String field) {
        if (value == null) {
            throw violation(field + " is null");
        }
    }

    private static void requireNow(long nowEpochMs) {
        if (nowEpochMs <= 0) {
            throw violation("nowEpochMs is invalid");
        }
    }

    private static boolean isEmpty(String value) {
        return value != null && value.isEmpty();
    }

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_EFFECT_CONTRACT: " + message);
    }
}
