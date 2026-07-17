package com.centralbrain.runtime.effects;

import com.centralbrain.runtime.effects.AdapterRegistry.Profile;
import com.centralbrain.runtime.vehicle.capability.CapabilityCatalog;
import com.centralbrain.runtime.vehicle.capability.VehicleCapability;
import com.centralbrain.runtime.vehicle.schema.SignalValue;
import com.centralbrain.runtime.vehicle.schema.VehicleSignalPath;
import com.centralbrain.sdk.effect.EffectContract;
import com.centralbrain.sdk.effect.EffectIntent;
import com.centralbrain.sdk.effect.EffectObservation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Process-local Effect verification state machine. It owns no clock, scheduler,
 * persistence, adapter, or vehicle interface.
 */
public final class EffectVerifier {
    public static final int MAX_COMPOSITE_FIELDS = 8;

    private static final Pattern SOURCE_ID =
            Pattern.compile("[a-z][a-z0-9_-]*(?:[.][a-z0-9][a-z0-9_-]*){1,7}");
    public enum Decision {
        VERIFIED,
        PENDING,
        FAILED_TERMINAL,
        ALREADY_VERIFIED
    }

    public enum EvidenceKind {
        CALLBACK,
        READBACK,
        UNAVAILABLE,
        TERMINAL
    }

    private final CapabilityCatalog capabilityCatalog;

    public EffectVerifier(CapabilityCatalog capabilityCatalog) {
        this.capabilityCatalog = Objects.requireNonNull(
                capabilityCatalog, "capabilityCatalog");
    }

    public Result verify(
            EffectIntent sourceIntent,
            EffectObservation sourcePrevious,
            VerificationEvidence evidence,
            Profile profile,
            long nowEpochMs) {
        EffectIntent intent = EffectBatch.copyIntent(sourceIntent);
        EffectObservation previous = copyObservation(sourcePrevious);
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(profile, "profile");
        validateStaticIntent(intent);
        EffectContract.validateObservation(previous);
        requireBinding(intent, previous);
        if (nowEpochMs <= 0L || nowEpochMs < previous.occurredAtEpochMs) {
            throw violation("verification time is invalid or moves backwards");
        }
        if (evidence.observedAtEpochMs < previous.occurredAtEpochMs
                || evidence.observedAtEpochMs > nowEpochMs) {
            throw violation("verification evidence time is outside the observation window");
        }
        if (previous.state == EffectContract.STATE_VERIFIED) {
            return Result.alreadyVerified(previous);
        }
        requireVerifiableState(previous.state);

        VehicleCapability capability = requireCapability(intent.capabilityId);
        String catalogArea = requireCatalogArea(capability, intent.targetArea);
        validateCapabilityBinding(intent, capability);
        validateEvidenceSource(previous, evidence, profile);

        if (evidence.kind == EvidenceKind.TERMINAL) {
            return terminal(
                    intent,
                    previous,
                    evidence.failureCode,
                    evidence.evidenceDigest,
                    nowEpochMs);
        }

        validatePolicyShape(intent, capability, catalogArea, evidence);
        if (nowEpochMs >= intent.deadlineEpochMs) {
            return terminal(
                    intent,
                    previous,
                    "VERIFICATION_DEADLINE_EXCEEDED",
                    evidence.evidenceDigest,
                    nowEpochMs);
        }
        if (profile == Profile.PRODUCTION && !evidence.productionTrusted) {
            return pendingUnknown(
                    intent,
                    previous,
                    "UNTRUSTED_READBACK",
                    evidence.evidenceDigest,
                    nowEpochMs);
        }
        if (evidence.kind == EvidenceKind.UNAVAILABLE) {
            return pendingUnknown(
                    intent,
                    previous,
                    "READBACK_UNAVAILABLE",
                    evidence.evidenceDigest,
                    nowEpochMs);
        }

        boolean matched = matches(intent, evidence);
        List<EffectObservation> transitions = new ArrayList<>();
        EffectObservation applied = ensureApplied(
                intent, previous, evidence, nowEpochMs, transitions);
        if (!matched) {
            return new Result(
                    Decision.PENDING,
                    "VERIFICATION_MISMATCH",
                    transitions,
                    applied);
        }
        EffectObservation verified = observation(
                intent,
                applied,
                evidence,
                EffectContract.STATE_VERIFIED,
                "",
                nowEpochMs);
        transitions.add(verified);
        return new Result(Decision.VERIFIED, "", transitions, verified);
    }

    public Result fail(
            EffectIntent sourceIntent,
            EffectObservation sourcePrevious,
            String failureCode,
            String evidenceDigest,
            long nowEpochMs) {
        EffectIntent intent = EffectBatch.copyIntent(sourceIntent);
        EffectObservation previous = copyObservation(sourcePrevious);
        validateStaticIntent(intent);
        EffectContract.validateObservation(previous);
        requireBinding(intent, previous);
        requireVerifiableState(previous.state);
        if (nowEpochMs <= 0L || nowEpochMs < previous.occurredAtEpochMs) {
            throw violation("terminal verification time is invalid");
        }
        return terminal(
                intent,
                previous,
                requireFailureCode(failureCode),
                EffectBatch.requireDigest(evidenceDigest, "evidenceDigest"),
                nowEpochMs);
    }

    /** Computes the target digest required by this verifier for the selected policy. */
    public static String expectedTargetDigest(
            EffectIntent intent,
            List<VerificationField> expectedFields) {
        Objects.requireNonNull(intent, "intent");
        TypedValue primaryTarget = TypedValue.fromIntent(intent);
        List<VerificationField> fields = immutableFields(expectedFields);
        List<String> parts = new ArrayList<>();
        parts.add(intent.capabilityId == null ? "" : intent.capabilityId);
        parts.add(intent.targetArea == null ? "" : intent.targetArea);
        parts.add(Integer.toString(intent.verificationPolicy));
        parts.add(Double.toHexString(intent.verificationTolerance));
        parts.add(primaryTarget.getDigest());
        if (intent.verificationPolicy == EffectContract.VERIFY_COMPOSITE) {
            if (fields.size() < 2 || fields.size() > MAX_COMPOSITE_FIELDS) {
                throw violation("composite target requires 2..8 typed fields");
            }
            boolean primaryFound = false;
            for (VerificationField field : fields) {
                parts.add(field.signalPath.getCanonicalPath());
                parts.add(field.area);
                parts.add(field.expected.getDigest());
                parts.add(Double.toHexString(field.tolerance));
                if (field.primary) {
                    if (primaryFound || !field.expected.sameValue(primaryTarget)) {
                        throw violation("composite primary target is missing or inconsistent");
                    }
                    primaryFound = true;
                }
            }
            if (!primaryFound) {
                throw violation("composite target has no primary field");
            }
        } else if (!fields.isEmpty()) {
            if (fields.size() != 1
                    || !fields.get(0).primary
                    || !fields.get(0).expected.sameValue(primaryTarget)) {
                throw violation("single-field target is inconsistent with EffectIntent");
            }
        }
        return EffectBatch.digest(
                "effect.verifier.target.v1", parts.toArray(new String[0]));
    }

    private Result pendingUnknown(
            EffectIntent intent,
            EffectObservation previous,
            String reason,
            String evidenceDigest,
            long nowEpochMs) {
        if (previous.state == EffectContract.STATE_UNKNOWN) {
            return new Result(Decision.PENDING, reason, List.of(), previous);
        }
        VerificationEvidence unavailable = VerificationEvidence.unavailable(
                previous.source,
                previous.sourceId,
                false,
                nowEpochMs,
                evidenceDigest);
        EffectObservation unknown = observation(
                intent,
                previous,
                unavailable,
                EffectContract.STATE_UNKNOWN,
                reason,
                nowEpochMs);
        return new Result(
                Decision.PENDING,
                reason,
                List.of(unknown),
                unknown);
    }

    private Result terminal(
            EffectIntent intent,
            EffectObservation previous,
            String failureCode,
            String evidenceDigest,
            long nowEpochMs) {
        VerificationEvidence terminal = VerificationEvidence.terminal(
                previous.source,
                previous.sourceId,
                false,
                nowEpochMs,
                evidenceDigest,
                failureCode);
        EffectObservation failed = observation(
                intent,
                previous,
                terminal,
                EffectContract.STATE_FAILED_TERMINAL,
                failureCode,
                nowEpochMs);
        return new Result(
                Decision.FAILED_TERMINAL,
                failureCode,
                List.of(failed),
                failed);
    }

    private EffectObservation ensureApplied(
            EffectIntent intent,
            EffectObservation previous,
            VerificationEvidence evidence,
            long nowEpochMs,
            List<EffectObservation> transitions) {
        if (previous.state == EffectContract.STATE_APPLIED) {
            return previous;
        }
        EffectObservation applied = observation(
                intent,
                previous,
                evidence,
                EffectContract.STATE_APPLIED,
                "",
                nowEpochMs);
        transitions.add(applied);
        return applied;
    }

    private static EffectObservation observation(
            EffectIntent intent,
            EffectObservation previous,
            VerificationEvidence evidence,
            int state,
            String failureCode,
            long nowEpochMs) {
        String reportedDigest = state == EffectContract.STATE_APPLIED
                        || state == EffectContract.STATE_VERIFIED
                ? evidence.reportedValueDigest()
                : "";
        String evidenceDigest = EffectBatch.digest(
                "effect.verifier.evidence.v1",
                previous.observationDigest,
                evidence.evidenceDigest,
                Integer.toString(state),
                reportedDigest,
                failureCode);
        String idSeed = EffectBatch.digest(
                "effect.verifier.observation.id.v1",
                previous.observationId,
                previous.observationDigest,
                Integer.toString(state),
                evidenceDigest,
                Long.toString(nowEpochMs));
        EffectObservation next = new EffectObservation();
        next.schemaVersion = EffectContract.SCHEMA_VERSION;
        next.observationId = uuidFromDigest(idSeed);
        next.effectId = intent.effectId;
        next.sessionId = intent.sessionId;
        next.actionId = intent.actionId;
        next.planDigest = intent.planDigest;
        next.contextVersion = intent.contextVersion;
        next.state = state;
        next.source = evidence.source;
        next.sourceId = evidence.sourceId;
        next.attempt = previous.attempt;
        next.targetValueDigest = intent.targetValueDigest;
        next.reportedValueDigest = reportedDigest;
        next.evidenceDigest = evidenceDigest;
        next.failureCode = failureCode;
        next.occurredAtEpochMs = nowEpochMs;
        next.terminal = state == EffectContract.STATE_VERIFIED
                || state == EffectContract.STATE_FAILED_TERMINAL;
        next.retryable = false;
        next.simulated = evidence.source == EffectContract.SOURCE_SIMULATION;
        next.observationDigest = EffectBatch.digest(
                "effect.verifier.observation.v1",
                next.observationId,
                next.effectId,
                next.sessionId,
                next.actionId,
                next.planDigest,
                Long.toString(next.contextVersion),
                Integer.toString(next.state),
                Integer.toString(next.source),
                next.sourceId,
                Integer.toString(next.attempt),
                next.targetValueDigest,
                next.reportedValueDigest,
                next.evidenceDigest,
                next.failureCode,
                Long.toString(next.occurredAtEpochMs),
                Boolean.toString(next.terminal),
                Boolean.toString(next.retryable),
                Boolean.toString(next.simulated));
        EffectContract.validateTransition(previous, next);
        return next;
    }

    private static boolean matches(EffectIntent intent, VerificationEvidence evidence) {
        switch (intent.verificationPolicy) {
            case EffectContract.VERIFY_CALLBACK_ONLY:
                return evidence.kind == EvidenceKind.CALLBACK;
            case EffectContract.VERIFY_REPORTED_EQUALS:
                return evidence.fields.get(0).matchesExactly();
            case EffectContract.VERIFY_REPORTED_TOLERANCE:
                return evidence.fields.get(0).matchesWithin(intent.verificationTolerance);
            case EffectContract.VERIFY_STATE_TRANSITION:
                return evidence.fields.get(0).matchesStateTransition();
            case EffectContract.VERIFY_COMPOSITE:
                for (VerificationField field : evidence.fields) {
                    if (!field.matchesWithin(field.tolerance)) {
                        return false;
                    }
                }
                return true;
            default:
                throw violation("verification policy is unknown");
        }
    }

    private static void validatePolicyShape(
            EffectIntent intent,
            VehicleCapability capability,
            String catalogArea,
            VerificationEvidence evidence) {
        boolean hasVehicleReadback = capability.getReportedSignalPath().isPresent();
        if (intent.verificationPolicy == EffectContract.VERIFY_CALLBACK_ONLY) {
            if (hasVehicleReadback
                    || capability.getRiskClass() != VehicleCapability.RiskClass.LOW) {
                throw violation("callback-only is forbidden for readback or elevated-risk effects");
            }
            if (evidence.kind != EvidenceKind.CALLBACK
                    && evidence.kind != EvidenceKind.UNAVAILABLE) {
                throw violation("callback-only policy received non-callback evidence");
            }
            requireTargetDigest(intent, List.of());
            return;
        }
        if (!hasVehicleReadback) {
            throw violation("selected policy requires a cataloged readback signal");
        }
        if (evidence.kind == EvidenceKind.UNAVAILABLE) {
            if (intent.verificationPolicy != EffectContract.VERIFY_COMPOSITE) {
                requireTargetDigest(intent, List.of());
            }
            return;
        }
        if (evidence.kind != EvidenceKind.READBACK) {
            throw violation("reported verification requires typed readback evidence");
        }
        if (intent.verificationPolicy == EffectContract.VERIFY_COMPOSITE) {
            if (evidence.fields.size() < 2
                    || evidence.fields.size() > MAX_COMPOSITE_FIELDS) {
                throw violation("composite verification requires 2..8 fields");
            }
        } else if (evidence.fields.size() != 1) {
            throw violation("verification policy requires exactly one field");
        }

        VehicleSignalPath expectedPath = capability.getReportedSignalPath().get();
        int primaryCount = 0;
        for (VerificationField field : evidence.fields) {
            if (field.primary) {
                primaryCount++;
                if (field.signalPath != expectedPath
                        || !field.area.equals(catalogArea)
                        || !field.expected.sameValue(TypedValue.fromIntent(intent))) {
                    throw violation("primary readback is not bound to the Effect target");
                }
            }
        }
        if (primaryCount != 1) {
            throw violation("verification evidence must contain one primary field");
        }
        if (intent.verificationPolicy == EffectContract.VERIFY_STATE_TRANSITION
                && evidence.fields.get(0).before == null) {
            throw violation("state-transition verification requires a before value");
        }
        requireTargetDigest(intent, evidence.fields);
    }

    private static void requireTargetDigest(
            EffectIntent intent,
            List<VerificationField> fields) {
        String expected = expectedTargetDigest(intent, fields);
        if (!expected.equals(intent.targetValueDigest)) {
            throw violation("targetValueDigest does not bind the verification target");
        }
    }

    private static void validateEvidenceSource(
            EffectObservation previous,
            VerificationEvidence evidence,
            Profile profile) {
        if (evidence.source != previous.source || !evidence.sourceId.equals(previous.sourceId)) {
            throw violation("verification evidence changes adapter provenance");
        }
        boolean simulated = evidence.source == EffectContract.SOURCE_SIMULATION;
        if (simulated != (profile == Profile.DEBUG_SIMULATION)) {
            throw violation("verification profile and evidence source disagree");
        }
        if (simulated && evidence.productionTrusted) {
            throw violation("simulation evidence cannot be production-trusted");
        }
    }

    private static void validateStaticIntent(EffectIntent intent) {
        EffectContract.validateIntent(intent, intent.createdAtEpochMs);
    }

    private static void validateCapabilityBinding(
            EffectIntent intent,
            VehicleCapability capability) {
        if (!capability.getAvailability().isWritable()) {
            throw violation("capability is not writable");
        }
        int expectedRisk;
        switch (capability.getRiskClass()) {
            case LOW:
                expectedRisk = EffectContract.RISK_LOW;
                break;
            case MEDIUM:
                expectedRisk = EffectContract.RISK_MEDIUM;
                break;
            case HIGH:
                expectedRisk = EffectContract.RISK_HIGH;
                break;
            default:
                throw violation("capability risk is unsupported");
        }
        if (intent.riskClass != expectedRisk) {
            throw violation("Effect risk does not match the capability catalog");
        }
        VehicleCapability.TargetRange range = capability.getTargetRange();
        switch (intent.valueKind) {
            case EffectContract.VALUE_BOOLEAN:
                range.validateBoolean(intent.booleanValue);
                break;
            case EffectContract.VALUE_INTEGER:
                range.validateInteger(intent.integerValue);
                break;
            case EffectContract.VALUE_DECIMAL:
                range.validateDecimal(intent.decimalValue);
                break;
            case EffectContract.VALUE_TEXT:
                range.validateText(intent.textValue);
                break;
            default:
                throw violation("Effect value kind is unsupported");
        }
        if (!capability.getUnit().equals(intent.unit)) {
            throw violation("Effect unit does not match the capability catalog");
        }
    }

    VehicleCapability requireCapability(String canonicalId) {
        for (VehicleCapability capability : capabilityCatalog.all()) {
            if (capability.getId().getCanonicalId().equals(canonicalId)) {
                return capability;
            }
        }
        throw violation("Effect capability is not cataloged");
    }

    static String requireCatalogArea(VehicleCapability capability, String targetArea) {
        if (capability.getAreas().contains(targetArea)) {
            return targetArea;
        }
        if (targetArea != null && targetArea.startsWith("vehicle.")) {
            String unqualified = targetArea.substring("vehicle.".length());
            if (capability.getAreas().contains(unqualified)) {
                return unqualified;
            }
        }
        throw violation("Effect target area is not cataloged for the capability");
    }

    private static void requireBinding(EffectIntent intent, EffectObservation observation) {
        if (!intent.effectId.equals(observation.effectId)
                || !intent.sessionId.equals(observation.sessionId)
                || !intent.actionId.equals(observation.actionId)
                || !intent.planDigest.equals(observation.planDigest)
                || intent.contextVersion != observation.contextVersion
                || !intent.targetValueDigest.equals(observation.targetValueDigest)) {
            throw violation("EffectIntent and previous observation binding disagree");
        }
    }

    private static void requireVerifiableState(int state) {
        if (state != EffectContract.STATE_DELIVERED
                && state != EffectContract.STATE_APPLIED
                && state != EffectContract.STATE_UNKNOWN) {
            throw violation("previous observation is not verifiable");
        }
    }

    private static String requireFailureCode(String value) {
        if (value == null || !value.matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw violation("failure code is not canonical");
        }
        return value;
    }

    private static String uuidFromDigest(String digest) {
        EffectBatch.requireDigest(digest, "UUID digest");
        char[] value = digest.substring(0, 32).toCharArray();
        value[12] = '5';
        value[16] = '8';
        String compact = new String(value);
        return compact.substring(0, 8)
                + "-" + compact.substring(8, 12)
                + "-" + compact.substring(12, 16)
                + "-" + compact.substring(16, 20)
                + "-" + compact.substring(20, 32);
    }

    private static List<VerificationField> immutableFields(List<VerificationField> source) {
        Objects.requireNonNull(source, "fields");
        List<VerificationField> copy = new ArrayList<>(source.size());
        Set<String> keys = new HashSet<>();
        for (VerificationField field : source) {
            VerificationField checked = Objects.requireNonNull(field, "field");
            String key = checked.signalPath.name() + "\u0000" + checked.area;
            if (!keys.add(key)) {
                throw violation("verification evidence contains duplicate fields");
            }
            copy.add(checked);
        }
        copy.sort(Comparator.comparing((VerificationField field) ->
                field.signalPath.getCanonicalPath()).thenComparing(field -> field.area));
        return Collections.unmodifiableList(copy);
    }

    private static EffectObservation copyObservation(EffectObservation source) {
        Objects.requireNonNull(source, "observation");
        EffectObservation copy = new EffectObservation();
        copy.schemaVersion = source.schemaVersion;
        copy.observationId = source.observationId;
        copy.effectId = source.effectId;
        copy.sessionId = source.sessionId;
        copy.actionId = source.actionId;
        copy.planDigest = source.planDigest;
        copy.contextVersion = source.contextVersion;
        copy.state = source.state;
        copy.source = source.source;
        copy.sourceId = source.sourceId;
        copy.attempt = source.attempt;
        copy.targetValueDigest = source.targetValueDigest;
        copy.reportedValueDigest = source.reportedValueDigest;
        copy.evidenceDigest = source.evidenceDigest;
        copy.observationDigest = source.observationDigest;
        copy.failureCode = source.failureCode;
        copy.occurredAtEpochMs = source.occurredAtEpochMs;
        copy.terminal = source.terminal;
        copy.retryable = source.retryable;
        copy.simulated = source.simulated;
        return copy;
    }

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_EFFECT_VERIFIER: " + message);
    }

    public static final class TypedValue {
        private final int kind;
        private final boolean booleanValue;
        private final long integerValue;
        private final double decimalValue;
        private final String textValue;
        private final String unit;
        private final String digest;

        private TypedValue(
                int kind,
                boolean booleanValue,
                long integerValue,
                double decimalValue,
                String textValue,
                String unit) {
            this.kind = kind;
            this.booleanValue = booleanValue;
            this.integerValue = integerValue;
            this.decimalValue = decimalValue;
            this.textValue = Objects.requireNonNull(textValue, "textValue");
            this.unit = Objects.requireNonNull(unit, "unit");
            String scalar;
            switch (kind) {
                case EffectContract.VALUE_BOOLEAN:
                    scalar = Boolean.toString(booleanValue);
                    break;
                case EffectContract.VALUE_INTEGER:
                    scalar = Long.toString(integerValue);
                    break;
                case EffectContract.VALUE_DECIMAL:
                    if (!Double.isFinite(decimalValue)) {
                        throw violation("typed decimal is not finite");
                    }
                    scalar = Double.toHexString(decimalValue);
                    break;
                case EffectContract.VALUE_TEXT:
                    if (textValue.isEmpty() || textValue.length() > 128) {
                        throw violation("typed text is invalid");
                    }
                    for (int index = 0; index < textValue.length(); index++) {
                        if (Character.isISOControl(textValue.charAt(index))) {
                            throw violation("typed text contains a control character");
                        }
                    }
                    scalar = textValue;
                    break;
                default:
                    throw violation("typed value kind is unknown");
            }
            this.digest = EffectBatch.digest(
                    "effect.verifier.value.v1",
                    Integer.toString(kind), unit, scalar);
        }

        public static TypedValue ofBoolean(boolean value, String unit) {
            return new TypedValue(
                    EffectContract.VALUE_BOOLEAN, value, 0L, 0.0, "", unit);
        }

        public static TypedValue ofInteger(long value, String unit) {
            return new TypedValue(
                    EffectContract.VALUE_INTEGER, false, value, 0.0, "", unit);
        }

        public static TypedValue ofDecimal(double value, String unit) {
            return new TypedValue(
                    EffectContract.VALUE_DECIMAL, false, 0L, value, "", unit);
        }

        public static TypedValue ofText(String value, String unit) {
            return new TypedValue(
                    EffectContract.VALUE_TEXT, false, 0L, 0.0, value, unit);
        }

        public static TypedValue fromIntent(EffectIntent intent) {
            Objects.requireNonNull(intent, "intent");
            switch (intent.valueKind) {
                case EffectContract.VALUE_BOOLEAN:
                    return ofBoolean(intent.booleanValue, intent.unit);
                case EffectContract.VALUE_INTEGER:
                    return ofInteger(intent.integerValue, intent.unit);
                case EffectContract.VALUE_DECIMAL:
                    return ofDecimal(intent.decimalValue, intent.unit);
                case EffectContract.VALUE_TEXT:
                    return ofText(intent.textValue, intent.unit);
                default:
                    throw violation("EffectIntent value kind is unknown");
            }
        }

        public static TypedValue fromSignal(SignalValue value) {
            Objects.requireNonNull(value, "value");
            if (!value.hasValue()) {
                throw violation("readback signal has no scalar value");
            }
            switch (value.getScalarType()) {
                case BOOLEAN:
                    return ofBoolean(value.getBooleanValue(), value.getUnit());
                case INTEGER:
                    return ofInteger(value.getIntegerValue(), value.getUnit());
                case DECIMAL:
                    return ofDecimal(value.getDecimalValue(), value.getUnit());
                case TEXT:
                    return ofText(value.getTextValue(), value.getUnit());
                default:
                    throw violation("readback scalar type is unknown");
            }
        }

        public String getDigest() {
            return digest;
        }

        public int getKind() {
            return kind;
        }

        private boolean sameValue(TypedValue other) {
            if (other == null || kind != other.kind || !unit.equals(other.unit)) {
                return false;
            }
            switch (kind) {
                case EffectContract.VALUE_BOOLEAN:
                    return booleanValue == other.booleanValue;
                case EffectContract.VALUE_INTEGER:
                    return integerValue == other.integerValue;
                case EffectContract.VALUE_DECIMAL:
                    return Double.compare(decimalValue, other.decimalValue) == 0;
                case EffectContract.VALUE_TEXT:
                    return textValue.equals(other.textValue);
                default:
                    return false;
            }
        }

        private boolean within(TypedValue other, double tolerance) {
            if (other == null || kind != other.kind || !unit.equals(other.unit)) {
                return false;
            }
            if (tolerance < 0.0 || !Double.isFinite(tolerance)) {
                throw violation("field tolerance is invalid");
            }
            if (kind == EffectContract.VALUE_INTEGER) {
                return Math.abs((double) integerValue - other.integerValue) <= tolerance;
            }
            if (kind == EffectContract.VALUE_DECIMAL) {
                return Math.abs(decimalValue - other.decimalValue) <= tolerance;
            }
            return tolerance == 0.0 && sameValue(other);
        }
    }

    public static final class VerificationField {
        private final VehicleSignalPath signalPath;
        private final String area;
        private final TypedValue expected;
        private final TypedValue before;
        private final TypedValue reported;
        private final double tolerance;
        private final boolean primary;

        public VerificationField(
                VehicleSignalPath signalPath,
                String area,
                TypedValue expected,
                TypedValue before,
                TypedValue reported,
                double tolerance,
                boolean primary) {
            this.signalPath = Objects.requireNonNull(signalPath, "signalPath");
            this.area = Objects.requireNonNull(area, "area");
            signalPath.validateUnitAndArea(
                    Objects.requireNonNull(expected, "expected").unit, area);
            signalPath.validateUnitAndArea(
                    Objects.requireNonNull(reported, "reported").unit, area);
            if (before != null) {
                signalPath.validateUnitAndArea(before.unit, area);
            }
            int expectedKind = effectKind(signalPath.getScalarType());
            if (expected.kind != expectedKind
                    || reported.kind != expectedKind
                    || before != null && before.kind != expectedKind) {
                throw violation("verification field scalar type does not match its signal path");
            }
            if (!Double.isFinite(tolerance) || tolerance < 0.0) {
                throw violation("verification field tolerance is invalid");
            }
            this.expected = expected;
            this.before = before;
            this.reported = reported;
            this.tolerance = tolerance;
            this.primary = primary;
        }

        public static VerificationField primary(
                VehicleSignalPath path,
                String area,
                EffectIntent intent,
                SignalValue reported) {
            Objects.requireNonNull(reported, "reported");
            if (reported.getPath() != path || !reported.getArea().equals(area)) {
                throw violation("primary SignalValue path/area does not match the field");
            }
            return new VerificationField(
                    path,
                    area,
                    TypedValue.fromIntent(intent),
                    null,
                    TypedValue.fromSignal(reported),
                    0.0,
                    true);
        }

        public VehicleSignalPath getSignalPath() {
            return signalPath;
        }

        public String getArea() {
            return area;
        }

        private boolean matchesExactly() {
            return expected.sameValue(reported);
        }

        private boolean matchesWithin(double allowedTolerance) {
            return expected.within(reported, allowedTolerance);
        }

        private boolean matchesStateTransition() {
            return before != null
                    && !before.sameValue(expected)
                    && expected.sameValue(reported);
        }

        private static int effectKind(SignalValue.ScalarType type) {
            switch (type) {
                case BOOLEAN:
                    return EffectContract.VALUE_BOOLEAN;
                case INTEGER:
                    return EffectContract.VALUE_INTEGER;
                case DECIMAL:
                    return EffectContract.VALUE_DECIMAL;
                case TEXT:
                    return EffectContract.VALUE_TEXT;
                default:
                    throw violation("signal scalar type is unsupported");
            }
        }
    }

    public static final class VerificationEvidence {
        private final EvidenceKind kind;
        private final int source;
        private final String sourceId;
        private final boolean productionTrusted;
        private final long observedAtEpochMs;
        private final String evidenceDigest;
        private final String failureCode;
        private final List<VerificationField> fields;

        private VerificationEvidence(
                EvidenceKind kind,
                int source,
                String sourceId,
                boolean productionTrusted,
                long observedAtEpochMs,
                String evidenceDigest,
                String failureCode,
                List<VerificationField> fields) {
            this.kind = Objects.requireNonNull(kind, "kind");
            if (source != EffectContract.SOURCE_ADAPTER
                    && source != EffectContract.SOURCE_SIMULATION) {
                throw violation("verification evidence source is invalid");
            }
            if (sourceId == null || !SOURCE_ID.matcher(sourceId).matches()) {
                throw violation("verification evidence sourceId is invalid");
            }
            if (observedAtEpochMs <= 0L) {
                throw violation("verification evidence time is invalid");
            }
            this.source = source;
            this.sourceId = sourceId;
            this.productionTrusted = productionTrusted;
            this.observedAtEpochMs = observedAtEpochMs;
            this.evidenceDigest = EffectBatch.requireDigest(
                    evidenceDigest, "evidenceDigest");
            this.failureCode = failureCode == null ? "" : failureCode;
            this.fields = immutableFields(fields);
            if (kind == EvidenceKind.READBACK && this.fields.isEmpty()) {
                throw violation("readback evidence has no typed fields");
            }
            if (kind != EvidenceKind.READBACK && !this.fields.isEmpty()) {
                throw violation("non-readback evidence cannot carry typed fields");
            }
            if (kind == EvidenceKind.TERMINAL) {
                requireFailureCode(this.failureCode);
            } else if (!this.failureCode.isEmpty()) {
                throw violation("non-terminal evidence cannot carry a failure code");
            }
        }

        public static VerificationEvidence callbackApplied(
                int source,
                String sourceId,
                boolean productionTrusted,
                long observedAtEpochMs,
                String evidenceDigest) {
            return new VerificationEvidence(
                    EvidenceKind.CALLBACK,
                    source,
                    sourceId,
                    productionTrusted,
                    observedAtEpochMs,
                    evidenceDigest,
                    "",
                    List.of());
        }

        public static VerificationEvidence readback(
                int source,
                String sourceId,
                boolean productionTrusted,
                long observedAtEpochMs,
                String evidenceDigest,
                List<VerificationField> fields) {
            return new VerificationEvidence(
                    EvidenceKind.READBACK,
                    source,
                    sourceId,
                    productionTrusted,
                    observedAtEpochMs,
                    evidenceDigest,
                    "",
                    fields);
        }

        public static VerificationEvidence unavailable(
                int source,
                String sourceId,
                boolean productionTrusted,
                long observedAtEpochMs,
                String evidenceDigest) {
            return new VerificationEvidence(
                    EvidenceKind.UNAVAILABLE,
                    source,
                    sourceId,
                    productionTrusted,
                    observedAtEpochMs,
                    evidenceDigest,
                    "",
                    List.of());
        }

        public static VerificationEvidence terminal(
                int source,
                String sourceId,
                boolean productionTrusted,
                long observedAtEpochMs,
                String evidenceDigest,
                String failureCode) {
            return new VerificationEvidence(
                    EvidenceKind.TERMINAL,
                    source,
                    sourceId,
                    productionTrusted,
                    observedAtEpochMs,
                    evidenceDigest,
                    failureCode,
                    List.of());
        }

        private String reportedValueDigest() {
            if (kind == EvidenceKind.CALLBACK) {
                return evidenceDigest;
            }
            if (kind != EvidenceKind.READBACK) {
                throw violation("unavailable evidence has no reported value digest");
            }
            List<String> parts = new ArrayList<>();
            for (VerificationField field : fields) {
                parts.add(field.signalPath.getCanonicalPath());
                parts.add(field.area);
                parts.add(field.reported.getDigest());
            }
            return EffectBatch.digest(
                    "effect.verifier.reported.v1", parts.toArray(new String[0]));
        }
    }

    public static final class Result {
        private final Decision decision;
        private final String reasonCode;
        private final List<EffectObservation> transitions;
        private final EffectObservation latest;

        private Result(
                Decision decision,
                String reasonCode,
                List<EffectObservation> transitions,
                EffectObservation latest) {
            this.decision = Objects.requireNonNull(decision, "decision");
            this.reasonCode = reasonCode == null ? "" : reasonCode;
            List<EffectObservation> copies = new ArrayList<>();
            for (EffectObservation transition : transitions) {
                copies.add(copyObservation(transition));
            }
            this.transitions = Collections.unmodifiableList(copies);
            this.latest = copyObservation(latest);
        }

        private static Result alreadyVerified(EffectObservation observation) {
            return new Result(
                    Decision.ALREADY_VERIFIED,
                    "",
                    List.of(),
                    observation);
        }

        public Decision getDecision() {
            return decision;
        }

        public String getReasonCode() {
            return reasonCode;
        }

        public List<EffectObservation> getTransitions() {
            List<EffectObservation> copies = new ArrayList<>();
            for (EffectObservation transition : transitions) {
                copies.add(copyObservation(transition));
            }
            return Collections.unmodifiableList(copies);
        }

        public EffectObservation getLatestObservation() {
            return copyObservation(latest);
        }

        public boolean requiresReconciliation() {
            return decision == Decision.PENDING;
        }
    }
}
