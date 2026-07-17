package com.centralbrain.runtime.effects;

import com.centralbrain.runtime.effects.AdapterRegistry.Profile;
import com.centralbrain.sdk.effect.EffectContract;
import com.centralbrain.sdk.effect.EffectIntent;
import com.centralbrain.sdk.effect.EffectObservation;
import com.centralbrain.sdk.effect.UndoHandle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Process-local admission for a new governed compensation task. It does not
 * dispatch the plan and is not an Android Service or a production authority.
 */
public final class UndoService {
    public static final int MAX_PROCESS_RECORDS = 64;

    public enum Decision {
        ADMITTED,
        REJECTED
    }

    private static final Pattern IDEMPOTENCY_KEY =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

    private final Map<String, AdmissionRecord> records = new LinkedHashMap<>();

    public List<UndoHandle> issueHandles(
            CompensationPlanner.Plan plan,
            List<String> undoIds,
            long createdAtEpochMs,
            long requestedTtlMs) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(undoIds, "undoIds");
        if (undoIds.size() != plan.getOrderedSteps().size()) {
            throw violation("one Undo handle ID is required for every compensation step");
        }
        if (createdAtEpochMs <= 0L
                || requestedTtlMs <= 0L
                || requestedTtlMs > EffectContract.MAX_UNDO_TTL_MS) {
            throw violation("Undo handle time-to-live is invalid");
        }
        long requestedExpiry = saturatingAdd(createdAtEpochMs, requestedTtlMs);
        long expiresAt = Math.min(requestedExpiry, plan.getCompensationDeadlineEpochMs());
        if (expiresAt <= createdAtEpochMs) {
            throw violation("Undo handle has no execution window");
        }

        Set<String> uniqueIds = new HashSet<>();
        List<UndoHandle> handles = new ArrayList<>();
        for (int index = 0; index < plan.getOrderedSteps().size(); index++) {
            CompensationPlanner.Step step = plan.getOrderedSteps().get(index);
            String undoId = EffectBatch.canonicalUuid(undoIds.get(index), "undoId");
            if (!uniqueIds.add(undoId)) {
                throw violation("Undo handle IDs must be unique");
            }
            EffectIntent source = step.getSourceIntent();
            EffectObservation verified = step.getVerifiedObservation();
            UndoHandle handle = new UndoHandle();
            handle.schemaVersion = EffectContract.SCHEMA_VERSION;
            handle.undoId = undoId;
            handle.sessionId = source.sessionId;
            handle.effectId = source.effectId;
            handle.sourceObservationId = verified.observationId;
            handle.capabilityId = source.capabilityId;
            handle.planDigest = source.planDigest;
            handle.verifiedObservationDigest = verified.observationDigest;
            handle.compensationDigest = step.getDescriptorDigest();
            handle.issuedContextVersion = source.contextVersion;
            handle.state = EffectContract.UNDO_AVAILABLE;
            handle.reasonCode = "";
            handle.createdAtEpochMs = createdAtEpochMs;
            handle.expiresAtEpochMs = expiresAt;
            handle.handleDigest = digestHandle(handle);
            EffectContract.validateUndoHandle(handle);
            handles.add(copyHandle(handle));
        }
        return Collections.unmodifiableList(handles);
    }

    public synchronized Admission requestUndo(
            String taskId,
            String taskIdempotencyKey,
            CompensationPlanner.Plan plan,
            List<UndoHandle> sourceHandles,
            GovernanceSnapshot governance,
            Profile profile,
            long nowEpochMs) {
        String canonicalTaskId = EffectBatch.canonicalUuid(taskId, "taskId");
        String canonicalIdempotency = requireIdempotencyKey(taskIdempotencyKey);
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(sourceHandles, "sourceHandles");
        Objects.requireNonNull(governance, "governance");
        Objects.requireNonNull(profile, "profile");
        if (nowEpochMs <= 0L) {
            throw violation("Undo request time must be positive");
        }
        if (sourceHandles.size() != plan.getOrderedSteps().size()) {
            throw violation("Undo handle set does not cover the compensation plan");
        }

        List<UndoHandle> handles = validateAndOrderHandles(plan, sourceHandles);
        String requestDigest = requestDigest(
                canonicalTaskId,
                canonicalIdempotency,
                plan,
                handles,
                governance,
                profile);
        String recordKey = governance.principalFingerprint + "\u0000" + canonicalIdempotency;
        AdmissionRecord existing = records.get(recordKey);
        if (existing != null) {
            if (!existing.requestDigest.equals(requestDigest)) {
                throw violation("Undo idempotency key was reused with different material");
            }
            return existing.admission.asReplay();
        }

        if (profile == Profile.PRODUCTION) {
            return Admission.rejected(
                    "PRODUCTION_COMPENSATION_UNAVAILABLE", plan.getPlanDigest());
        }
        if (!governance.authorityTrusted) {
            return Admission.rejected(
                    "GOVERNANCE_AUTHORITY_UNTRUSTED", plan.getPlanDigest());
        }
        if (!governance.contextFresh) {
            return Admission.rejected("CONTEXT_STALE", plan.getPlanDigest());
        }
        if (!governance.policyAuthorized) {
            return Admission.rejected("POLICY_DENIED", plan.getPlanDigest());
        }
        if (!governance.safetyTrusted) {
            return Admission.rejected("SAFETY_UNTRUSTED", plan.getPlanDigest());
        }
        if (!governance.safetySafe) {
            return Admission.rejected("SAFETY_UNSAFE", plan.getPlanDigest());
        }
        if (!governance.contextDigest.equals(plan.getCompensationContextDigest())
                || governance.contextVersion != plan.getCompensationContextVersion()) {
            return Admission.rejected("CONTEXT_BINDING_CHANGED", plan.getPlanDigest());
        }
        for (CompensationPlanner.Step step : plan.getOrderedSteps()) {
            if (!governance.allowedCapabilityIds.contains(
                    step.getCompensationIntent().capabilityId)) {
                return Admission.rejected("CAPABILITY_DENIED", plan.getPlanDigest());
            }
        }
        if (nowEpochMs >= plan.getCompensationDeadlineEpochMs()) {
            return Admission.rejected("COMPENSATION_DEADLINE_EXCEEDED", plan.getPlanDigest());
        }

        for (int index = 0; index < handles.size(); index++) {
            UndoHandle handle = handles.get(index);
            CompensationPlanner.Step step = plan.getOrderedSteps().get(index);
            if (handle.state != EffectContract.UNDO_AVAILABLE) {
                return Admission.rejected("UNDO_HANDLE_NOT_AVAILABLE", plan.getPlanDigest());
            }
            if (nowEpochMs < handle.createdAtEpochMs
                    || nowEpochMs >= handle.expiresAtEpochMs) {
                return Admission.rejected("UNDO_HANDLE_EXPIRED", plan.getPlanDigest());
            }
            if (governance.contextVersion < handle.issuedContextVersion) {
                return Admission.rejected("CONTEXT_VERSION_REGRESSED", plan.getPlanDigest());
            }
            EffectContract.validateUndoRequest(
                    handle,
                    step.getSourceIntent().planDigest,
                    step.getVerifiedObservation().observationDigest,
                    governance.contextVersion,
                    nowEpochMs);
        }

        if (records.size() >= MAX_PROCESS_RECORDS) {
            throw violation("process-local Undo admission capacity is exhausted");
        }
        List<UndoHandle> requestedHandles = new ArrayList<>();
        for (UndoHandle handle : handles) {
            requestedHandles.add(transitionToRequested(handle));
        }
        GovernedTask task = new GovernedTask(
                canonicalTaskId,
                canonicalIdempotency,
                plan,
                governance,
                nowEpochMs);
        Admission admitted = Admission.admitted(task, requestedHandles);
        records.put(recordKey, new AdmissionRecord(requestDigest, admitted));
        return admitted;
    }

    public synchronized int processRecordCount() {
        return records.size();
    }

    public static String digestHandle(UndoHandle handle) {
        Objects.requireNonNull(handle, "handle");
        return EffectBatch.digest(
                "effect.undo.handle.v1",
                Integer.toString(handle.schemaVersion),
                nullToEmpty(handle.undoId),
                nullToEmpty(handle.sessionId),
                nullToEmpty(handle.effectId),
                nullToEmpty(handle.sourceObservationId),
                nullToEmpty(handle.capabilityId),
                nullToEmpty(handle.planDigest),
                nullToEmpty(handle.verifiedObservationDigest),
                nullToEmpty(handle.compensationDigest),
                Long.toString(handle.issuedContextVersion),
                Integer.toString(handle.state),
                nullToEmpty(handle.reasonCode),
                Long.toString(handle.createdAtEpochMs),
                Long.toString(handle.expiresAtEpochMs));
    }

    private static List<UndoHandle> validateAndOrderHandles(
            CompensationPlanner.Plan plan,
            List<UndoHandle> sourceHandles) {
        Map<String, UndoHandle> byEffectId = new LinkedHashMap<>();
        for (UndoHandle source : sourceHandles) {
            UndoHandle handle = copyHandle(Objects.requireNonNull(source, "undoHandle"));
            EffectContract.validateUndoHandle(handle);
            if (!digestHandle(handle).equals(handle.handleDigest)) {
                throw violation("Undo handle digest is invalid");
            }
            if (byEffectId.put(handle.effectId, handle) != null) {
                throw violation("Undo handle set contains a duplicate Effect");
            }
        }
        List<UndoHandle> ordered = new ArrayList<>();
        for (CompensationPlanner.Step step : plan.getOrderedSteps()) {
            EffectIntent source = step.getSourceIntent();
            EffectObservation verified = step.getVerifiedObservation();
            UndoHandle handle = byEffectId.remove(source.effectId);
            if (handle == null) {
                throw violation("Undo handle set omits a compensation step");
            }
            if (!handle.sessionId.equals(source.sessionId)
                    || !handle.sourceObservationId.equals(verified.observationId)
                    || !handle.capabilityId.equals(source.capabilityId)
                    || !handle.planDigest.equals(source.planDigest)
                    || !handle.verifiedObservationDigest.equals(verified.observationDigest)
                    || !handle.compensationDigest.equals(step.getDescriptorDigest())) {
                throw violation("Undo handle binding differs from the compensation step");
            }
            ordered.add(handle);
        }
        if (!byEffectId.isEmpty()) {
            throw violation("Undo handle set references an Effect outside the plan");
        }
        return ordered;
    }

    private static String requestDigest(
            String taskId,
            String taskIdempotencyKey,
            CompensationPlanner.Plan plan,
            List<UndoHandle> handles,
            GovernanceSnapshot governance,
            Profile profile) {
        List<String> parts = new ArrayList<>();
        parts.add(taskId);
        parts.add(taskIdempotencyKey);
        parts.add(plan.getPlanDigest());
        parts.add(governance.snapshotDigest);
        parts.add(profile.name());
        parts.add(Integer.toString(handles.size()));
        for (UndoHandle handle : handles) {
            parts.add(handle.handleDigest);
        }
        return EffectBatch.digest(
                "effect.undo.request.v1", parts.toArray(new String[0]));
    }

    private static UndoHandle transitionToRequested(UndoHandle source) {
        UndoHandle result = copyHandle(source);
        result.state = EffectContract.UNDO_REQUESTED;
        result.reasonCode = "";
        result.handleDigest = digestHandle(result);
        EffectContract.validateUndoHandle(result);
        return result;
    }

    private static String requireIdempotencyKey(String value) {
        if (value == null || !IDEMPOTENCY_KEY.matcher(value).matches()) {
            throw violation("task idempotency key is not canonical");
        }
        return value;
    }

    private static long saturatingAdd(long left, long right) {
        if (left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static UndoHandle copyHandle(UndoHandle source) {
        UndoHandle copy = new UndoHandle();
        copy.schemaVersion = source.schemaVersion;
        copy.undoId = source.undoId;
        copy.sessionId = source.sessionId;
        copy.effectId = source.effectId;
        copy.sourceObservationId = source.sourceObservationId;
        copy.capabilityId = source.capabilityId;
        copy.planDigest = source.planDigest;
        copy.verifiedObservationDigest = source.verifiedObservationDigest;
        copy.compensationDigest = source.compensationDigest;
        copy.handleDigest = source.handleDigest;
        copy.issuedContextVersion = source.issuedContextVersion;
        copy.state = source.state;
        copy.reasonCode = source.reasonCode;
        copy.createdAtEpochMs = source.createdAtEpochMs;
        copy.expiresAtEpochMs = source.expiresAtEpochMs;
        return copy;
    }

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_UNDO_SERVICE: " + message);
    }

    private static final class AdmissionRecord {
        private final String requestDigest;
        private final Admission admission;

        private AdmissionRecord(String requestDigest, Admission admission) {
            this.requestDigest = requestDigest;
            this.admission = admission;
        }
    }

    public static final class GovernanceSnapshot {
        private final String principalFingerprint;
        private final String contextDigest;
        private final long contextVersion;
        private final String policyDigest;
        private final String safetyDigest;
        private final Set<String> allowedCapabilityIds;
        private final boolean authorityTrusted;
        private final boolean contextFresh;
        private final boolean policyAuthorized;
        private final boolean safetyTrusted;
        private final boolean safetySafe;
        private final String snapshotDigest;

        public GovernanceSnapshot(
                String principalFingerprint,
                String contextDigest,
                long contextVersion,
                String policyDigest,
                String safetyDigest,
                Set<String> allowedCapabilityIds,
                boolean authorityTrusted,
                boolean contextFresh,
                boolean policyAuthorized,
                boolean safetyTrusted,
                boolean safetySafe) {
            this.principalFingerprint = EffectBatch.requireDigest(
                    principalFingerprint, "principalFingerprint");
            this.contextDigest = EffectBatch.requireDigest(
                    contextDigest, "contextDigest");
            if (contextVersion < 1L) {
                throw violation("Governance Context version must be positive");
            }
            this.contextVersion = contextVersion;
            this.policyDigest = EffectBatch.requireDigest(policyDigest, "policyDigest");
            this.safetyDigest = EffectBatch.requireDigest(safetyDigest, "safetyDigest");
            Objects.requireNonNull(allowedCapabilityIds, "allowedCapabilityIds");
            if (allowedCapabilityIds.isEmpty()
                    || allowedCapabilityIds.size() > MAX_COMPENSATION_CAPABILITIES) {
                throw violation("Governance capability set is outside the bound");
            }
            List<String> ordered = new ArrayList<>();
            for (String capabilityId : allowedCapabilityIds) {
                ordered.add(EffectBatch.requireQualifiedId(
                        capabilityId,
                        EffectContract.MAX_CAPABILITY_ID_CHARS,
                        "allowedCapabilityId"));
            }
            Collections.sort(ordered);
            if (new HashSet<>(ordered).size() != ordered.size()) {
                throw violation("Governance capability set contains a duplicate");
            }
            this.allowedCapabilityIds = Collections.unmodifiableSet(
                    new java.util.LinkedHashSet<>(ordered));
            this.authorityTrusted = authorityTrusted;
            this.contextFresh = contextFresh;
            this.policyAuthorized = policyAuthorized;
            this.safetyTrusted = safetyTrusted;
            this.safetySafe = safetySafe;
            List<String> digestParts = new ArrayList<>();
            digestParts.add(this.principalFingerprint);
            digestParts.add(this.contextDigest);
            digestParts.add(Long.toString(this.contextVersion));
            digestParts.add(this.policyDigest);
            digestParts.add(this.safetyDigest);
            digestParts.add(Boolean.toString(this.authorityTrusted));
            digestParts.add(Boolean.toString(this.contextFresh));
            digestParts.add(Boolean.toString(this.policyAuthorized));
            digestParts.add(Boolean.toString(this.safetyTrusted));
            digestParts.add(Boolean.toString(this.safetySafe));
            digestParts.addAll(ordered);
            this.snapshotDigest = EffectBatch.digest(
                    "effect.undo.governance.v1", digestParts.toArray(new String[0]));
        }

        private static final int MAX_COMPENSATION_CAPABILITIES =
                CompensationPlanner.MAX_COMPENSATION_EFFECTS;

        public String getSnapshotDigest() {
            return snapshotDigest;
        }
    }

    public static final class GovernedTask {
        private final String taskId;
        private final String taskIdempotencyKey;
        private final String sourceBatchId;
        private final String sourceSessionId;
        private final String compensationSessionId;
        private final String compensationPlanId;
        private final String compensationActionId;
        private final String compensationPlanDigest;
        private final String principalFingerprint;
        private final String contextDigest;
        private final String policyDigest;
        private final String safetyDigest;
        private final long createdAtEpochMs;
        private final long deadlineEpochMs;
        private final String taskDigest;

        private GovernedTask(
                String taskId,
                String taskIdempotencyKey,
                CompensationPlanner.Plan plan,
                GovernanceSnapshot governance,
                long createdAtEpochMs) {
            this.taskId = taskId;
            this.taskIdempotencyKey = taskIdempotencyKey;
            this.sourceBatchId = plan.getSourceBatchId();
            this.sourceSessionId = plan.getSourceSessionId();
            this.compensationSessionId = plan.getCompensationSessionId();
            this.compensationPlanId = plan.getCompensationPlanId();
            this.compensationActionId = plan.getCompensationActionId();
            this.compensationPlanDigest = plan.getPlanDigest();
            this.principalFingerprint = governance.principalFingerprint;
            this.contextDigest = governance.contextDigest;
            this.policyDigest = governance.policyDigest;
            this.safetyDigest = governance.safetyDigest;
            this.createdAtEpochMs = createdAtEpochMs;
            this.deadlineEpochMs = plan.getCompensationDeadlineEpochMs();
            this.taskDigest = EffectBatch.digest(
                    "effect.undo.task.v1",
                    this.taskId,
                    this.taskIdempotencyKey,
                    this.sourceBatchId,
                    this.sourceSessionId,
                    this.compensationSessionId,
                    this.compensationPlanId,
                    this.compensationActionId,
                    this.compensationPlanDigest,
                    this.principalFingerprint,
                    this.contextDigest,
                    this.policyDigest,
                    this.safetyDigest,
                    Long.toString(this.createdAtEpochMs),
                    Long.toString(this.deadlineEpochMs));
        }

        public String getTaskId() {
            return taskId;
        }

        public String getSourceBatchId() {
            return sourceBatchId;
        }

        public String getCompensationSessionId() {
            return compensationSessionId;
        }

        public String getCompensationPlanId() {
            return compensationPlanId;
        }

        public String getCompensationPlanDigest() {
            return compensationPlanDigest;
        }

        public long getDeadlineEpochMs() {
            return deadlineEpochMs;
        }

        public String getTaskDigest() {
            return taskDigest;
        }
    }

    public static final class Admission {
        private final Decision decision;
        private final String reasonCode;
        private final String planDigest;
        private final GovernedTask task;
        private final List<UndoHandle> requestedHandles;
        private final boolean idempotentReplay;

        private Admission(
                Decision decision,
                String reasonCode,
                String planDigest,
                GovernedTask task,
                List<UndoHandle> requestedHandles,
                boolean idempotentReplay) {
            this.decision = Objects.requireNonNull(decision, "decision");
            this.reasonCode = reasonCode;
            this.planDigest = EffectBatch.requireDigest(planDigest, "planDigest");
            this.task = task;
            List<UndoHandle> copies = new ArrayList<>();
            for (UndoHandle handle : requestedHandles) {
                copies.add(copyHandle(handle));
            }
            this.requestedHandles = Collections.unmodifiableList(copies);
            this.idempotentReplay = idempotentReplay;
        }

        private static Admission admitted(
                GovernedTask task,
                List<UndoHandle> handles) {
            return new Admission(
                    Decision.ADMITTED,
                    "",
                    task.compensationPlanDigest,
                    task,
                    handles,
                    false);
        }

        private static Admission rejected(String reasonCode, String planDigest) {
            if (reasonCode == null || !reasonCode.matches("[A-Z][A-Z0-9_]{0,63}")) {
                throw violation("Undo rejection reason is not canonical");
            }
            return new Admission(
                    Decision.REJECTED,
                    reasonCode,
                    planDigest,
                    null,
                    List.of(),
                    false);
        }

        private Admission asReplay() {
            return new Admission(
                    decision,
                    reasonCode,
                    planDigest,
                    task,
                    requestedHandles,
                    true);
        }

        public Decision getDecision() {
            return decision;
        }

        public String getReasonCode() {
            return reasonCode;
        }

        public boolean isIdempotentReplay() {
            return idempotentReplay;
        }

        public GovernedTask getTask() {
            if (task == null) {
                throw new IllegalStateException("CB_UNDO_SERVICE: rejected admission has no task");
            }
            return task;
        }

        public List<UndoHandle> getRequestedHandles() {
            List<UndoHandle> copies = new ArrayList<>();
            for (UndoHandle handle : requestedHandles) {
                copies.add(copyHandle(handle));
            }
            return Collections.unmodifiableList(copies);
        }
    }
}
