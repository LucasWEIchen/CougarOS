package com.centralbrain.sdk.effect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public final class EffectContractTest {
    private static final long NOW = 1_750_000_000_000L;
    private static final String SESSION_ID = "9bffbb6a-5a0b-41b5-a924-bcfb45be4f26";
    private static final String PLAN_ID = "c9c1ec9f-4d04-4c49-9156-d8e1be2e60a0";
    private static final String EFFECT_ID = "08fc7600-d9b7-4c50-8b46-eabe4a8055ad";
    private static final String ACTION_ID = "b246f4de-bec4-4b22-9019-1b94dbaf08de";
    private static final String PLAN_DIGEST = digest('a');
    private static final String ACTION_DIGEST = digest('b');
    private static final String TARGET_DIGEST = digest('c');
    private static final String CONTEXT_DIGEST = digest('d');

    @Test
    public void acceptsTypedEffectApprovalAndUndoContracts() {
        EffectIntent intent = validIntent();
        EffectContract.validateIntent(intent, NOW);

        ApprovalPrompt prompt = validPrompt();
        EffectContract.validateApprovalPrompt(prompt);
        EffectContract.validateApprovalResume(
                prompt,
                PLAN_DIGEST,
                ACTION_DIGEST,
                CONTEXT_DIGEST,
                7,
                NOW);

        UndoHandle handle = validUndoHandle();
        EffectContract.validateUndoHandle(handle);
        EffectContract.validateUndoRequest(
                handle,
                PLAN_DIGEST,
                digest('8'),
                8,
                NOW);
    }

    @Test
    public void rejectsInactiveValueAndUnsafeVerification() {
        EffectIntent inactive = validIntent();
        inactive.booleanValue = true;
        expectViolation(() -> EffectContract.validateIntent(inactive, NOW));

        EffectIntent unsafeTolerance = validIntent();
        unsafeTolerance.verificationTolerance = 0.0;
        expectViolation(() -> EffectContract.validateIntent(unsafeTolerance, NOW));

        EffectIntent irreversible = validIntent();
        irreversible.reversible = false;
        expectViolation(() -> EffectContract.validateIntent(irreversible, NOW));

        EffectIntent unknownVersion = validIntent();
        unknownVersion.schemaVersion = 2;
        expectViolation(() -> EffectContract.validateIntent(unknownVersion, NOW));
    }

    @Test
    public void distinguishesDispatchDeliveryApplyAndVerify() {
        EffectObservation proposed = observation(EffectContract.STATE_PROPOSED, 0, 1);
        EffectObservation authorized = observation(EffectContract.STATE_AUTHORIZED, 0, 2);
        EffectObservation prepared = observation(EffectContract.STATE_PREPARED, 1, 3);
        EffectObservation dispatched = observation(EffectContract.STATE_DISPATCHED, 1, 4);
        EffectObservation delivered = observation(EffectContract.STATE_DELIVERED, 1, 5);
        EffectObservation applied = observation(EffectContract.STATE_APPLIED, 1, 6);
        EffectObservation verified = observation(EffectContract.STATE_VERIFIED, 1, 7);

        EffectContract.validateTransition(proposed, authorized);
        EffectContract.validateTransition(authorized, prepared);
        EffectContract.validateTransition(prepared, dispatched);
        EffectContract.validateTransition(dispatched, delivered);
        EffectContract.validateTransition(delivered, applied);
        EffectContract.validateTransition(applied, verified);

        assertEquals(5, dispatched.state);
        assertEquals(6, delivered.state);
        assertEquals(7, applied.state);
        assertEquals(8, verified.state);
    }

    @Test
    public void rejectsIllegalTerminalAndRetryTransitions() {
        EffectObservation dispatched = observation(EffectContract.STATE_DISPATCHED, 1, 1);
        EffectObservation verified = observation(EffectContract.STATE_VERIFIED, 1, 2);
        expectViolation(() -> EffectContract.validateTransition(dispatched, verified));

        EffectObservation terminal = observation(EffectContract.STATE_VERIFIED, 1, 3);
        EffectObservation later = observation(EffectContract.STATE_COMPENSATING, 1, 4);
        expectViolation(() -> EffectContract.validateTransition(terminal, later));

        EffectObservation retryable = observation(EffectContract.STATE_FAILED_RETRYABLE, 1, 5);
        EffectObservation retryPrepared = observation(EffectContract.STATE_PREPARED, 2, 6);
        EffectContract.validateTransition(retryable, retryPrepared);

        EffectObservation exhausted = observation(
                EffectContract.STATE_FAILED_RETRYABLE,
                EffectContract.MAX_ATTEMPTS,
                7);
        EffectObservation extraAttempt = observation(
                EffectContract.STATE_PREPARED,
                EffectContract.MAX_ATTEMPTS,
                8);
        expectViolation(() -> EffectContract.validateTransition(exhausted, extraAttempt));
    }

    @Test
    public void rejectsStaleOrExpiredApproval() {
        ApprovalPrompt prompt = validPrompt();
        expectViolation(() -> EffectContract.validateApprovalResume(
                prompt,
                PLAN_DIGEST,
                ACTION_DIGEST,
                CONTEXT_DIGEST,
                8,
                NOW));
        expectViolation(() -> EffectContract.validateApprovalResume(
                prompt,
                PLAN_DIGEST,
                ACTION_DIGEST,
                CONTEXT_DIGEST,
                7,
                prompt.expiresAtEpochMs));

        ApprovalPrompt oversizedTtl = validPrompt();
        oversizedTtl.expiresAtEpochMs = oversizedTtl.createdAtEpochMs
                + EffectContract.MAX_APPROVAL_TTL_MS + 1;
        expectViolation(() -> EffectContract.validateApprovalPrompt(oversizedTtl));
    }

    @Test
    public void rejectsUnsafeUndoAndSimulationMarker() {
        UndoHandle unavailable = validUndoHandle();
        unavailable.state = EffectContract.UNDO_REQUESTED;
        expectViolation(() -> EffectContract.validateUndoRequest(
                unavailable,
                PLAN_DIGEST,
                digest('8'),
                8,
                NOW));

        UndoHandle regressed = validUndoHandle();
        expectViolation(() -> EffectContract.validateUndoRequest(
                regressed,
                PLAN_DIGEST,
                digest('8'),
                6,
                NOW));

        EffectObservation unsafeSimulation = observation(
                EffectContract.STATE_DISPATCHED,
                1,
                9);
        unsafeSimulation.source = EffectContract.SOURCE_SIMULATION;
        unsafeSimulation.sourceId = "simulated.vehicle.twin";
        expectViolation(() -> EffectContract.validateObservation(unsafeSimulation));
    }

    static EffectIntent validIntent() {
        EffectIntent intent = new EffectIntent();
        intent.effectId = EFFECT_ID;
        intent.sessionId = SESSION_ID;
        intent.planId = PLAN_ID;
        intent.nodeId = "apply-hvac";
        intent.actionId = ACTION_ID;
        intent.capabilityId = "vehicle.hvac.temperature";
        intent.targetArea = "vehicle.cabin.row1.driver";
        intent.valueKind = EffectContract.VALUE_DECIMAL;
        intent.decimalValue = 21.5;
        intent.unit = "celsius";
        intent.targetValueDigest = TARGET_DIGEST;
        intent.idempotencyKey = "device-plan:apply-hvac";
        intent.planDigest = PLAN_DIGEST;
        intent.contextDigest = CONTEXT_DIGEST;
        intent.contextVersion = 7;
        intent.riskClass = EffectContract.RISK_LOW;
        intent.required = true;
        intent.verificationPolicy = EffectContract.VERIFY_REPORTED_TOLERANCE;
        intent.verificationTolerance = 0.5;
        intent.reversible = true;
        intent.compensationDigest = digest('e');
        intent.createdAtEpochMs = NOW - 1_000;
        intent.deadlineEpochMs = NOW + 60_000;
        return intent;
    }

    static ApprovalPrompt validPrompt() {
        ApprovalPrompt prompt = new ApprovalPrompt();
        prompt.approvalId = "7bd72ec6-8a04-41d5-a8c4-fe450d80877f";
        prompt.sessionId = SESSION_ID;
        prompt.planId = PLAN_ID;
        prompt.nodeId = "recline-driver-seat";
        prompt.actionId = ACTION_ID;
        prompt.effectId = EFFECT_ID;
        prompt.planDigest = PLAN_DIGEST;
        prompt.actionDigest = ACTION_DIGEST;
        prompt.targetValueDigest = TARGET_DIGEST;
        prompt.contextDigest = CONTEXT_DIGEST;
        prompt.contextVersion = 7;
        prompt.policyId = "policy.cabin.default";
        prompt.policyVersion = 3;
        prompt.riskClass = EffectContract.RISK_HIGH;
        prompt.reasonCode = "CB_APPROVAL_REQUIRED";
        prompt.promptCode = "approval.driver_seat_recline";
        prompt.approvalDigest = digest('f');
        prompt.createdAtEpochMs = NOW - 1_000;
        prompt.expiresAtEpochMs = NOW + 60_000;
        return prompt;
    }

    static UndoHandle validUndoHandle() {
        UndoHandle handle = new UndoHandle();
        handle.undoId = "de5875c9-468a-4c67-9d0f-0d30a82b9268";
        handle.sessionId = SESSION_ID;
        handle.effectId = EFFECT_ID;
        handle.sourceObservationId = "00000000-0000-4000-8000-000000000008";
        handle.capabilityId = "vehicle.hvac.temperature";
        handle.planDigest = PLAN_DIGEST;
        handle.verifiedObservationDigest = digest('8');
        handle.compensationDigest = digest('e');
        handle.handleDigest = digest('9');
        handle.issuedContextVersion = 7;
        handle.state = EffectContract.UNDO_AVAILABLE;
        handle.createdAtEpochMs = NOW - 1_000;
        handle.expiresAtEpochMs = NOW + 5 * 60_000;
        return handle;
    }

    static EffectObservation observation(int state, int attempt, int serial) {
        EffectObservation observation = new EffectObservation();
        observation.observationId = String.format(
                "00000000-0000-4000-8000-%012d",
                serial);
        observation.effectId = EFFECT_ID;
        observation.sessionId = SESSION_ID;
        observation.actionId = ACTION_ID;
        observation.planDigest = PLAN_DIGEST;
        observation.contextVersion = 7;
        observation.state = state;
        observation.source = EffectContract.SOURCE_RUNTIME;
        observation.sourceId = "runtime.effect.coordinator";
        observation.attempt = attempt;
        observation.targetValueDigest = TARGET_DIGEST;
        if (state == EffectContract.STATE_APPLIED
                || state == EffectContract.STATE_VERIFIED
                || state == EffectContract.STATE_COMPENSATING
                || state == EffectContract.STATE_COMPENSATED) {
            observation.reportedValueDigest = digest('7');
        }
        observation.evidenceDigest = digest('6');
        observation.observationDigest = digest(Character.forDigit(serial % 16, 16));
        if (state == EffectContract.STATE_REJECTED) {
            observation.failureCode = "CB_EFFECT_REJECTED";
        } else if (state == EffectContract.STATE_UNKNOWN) {
            observation.failureCode = "CB_EFFECT_OUTCOME_UNKNOWN";
        } else if (state == EffectContract.STATE_FAILED_RETRYABLE) {
            observation.failureCode = "CB_EFFECT_RETRY";
        } else if (state == EffectContract.STATE_FAILED_TERMINAL) {
            observation.failureCode = "CB_EFFECT_FAILED";
        } else if (state == EffectContract.STATE_CANCELLED) {
            observation.failureCode = "CB_EFFECT_CANCELLED";
        }
        observation.occurredAtEpochMs = NOW + serial;
        observation.terminal = state == EffectContract.STATE_REJECTED
                || state == EffectContract.STATE_VERIFIED
                || state == EffectContract.STATE_FAILED_TERMINAL
                || state == EffectContract.STATE_COMPENSATED
                || state == EffectContract.STATE_CANCELLED;
        observation.retryable = state == EffectContract.STATE_FAILED_RETRYABLE;
        return observation;
    }

    private static String digest(char value) {
        return String.valueOf(value).repeat(64);
    }

    private static void expectViolation(Runnable operation) {
        try {
            operation.run();
            fail("expected an Effect contract violation");
        } catch (IllegalArgumentException expected) {
            if (!expected.getMessage().startsWith("CB_EFFECT_CONTRACT:")) {
                throw expected;
            }
        }
    }
}
