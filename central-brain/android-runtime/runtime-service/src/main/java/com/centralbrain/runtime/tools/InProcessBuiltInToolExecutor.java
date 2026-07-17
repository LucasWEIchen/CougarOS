package com.centralbrain.runtime.tools;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/** Synchronous cooperative executor for exactly pinned application built-ins. */
public final class InProcessBuiltInToolExecutor implements ToolExecutor {
    public static final int MAX_BINDINGS = 64;
    public static final int MAX_AUDIT_RECORDS = 128;

    public enum ConstructionError {
        BINDING_LIMIT_EXCEEDED,
        DUPLICATE_ALLOWLIST_FAMILY,
        DUPLICATE_REGISTRATION_FAMILY,
        CURRENT_SIGNER_NOT_ALLOWLISTED,
        TOOL_NOT_ALLOWLISTED,
        BUILT_IN_OWNER_REQUIRED,
        CONTRACT_BINDING_MISMATCH,
        SIGNER_BINDING_MISMATCH,
        ARTIFACT_BINDING_MISMATCH
    }

    public static final class ConstructionException extends IllegalArgumentException {
        private final ConstructionError errorCode;

        ConstructionException(ConstructionError errorCode) {
            super("CB_BUILT_IN_TOOL_EXECUTOR: " + errorCode.name());
            this.errorCode = errorCode;
        }

        public ConstructionError getErrorCode() {
            return errorCode;
        }
    }

    public interface ElapsedRealtimeClock {
        long nowMs();
    }

    public static final class AllowlistEntry {
        private final String familyId;
        private final String contractDigest;
        private final String signerDigest;
        private final String artifactDigest;

        public AllowlistEntry(
                String familyId,
                String contractDigest,
                String signerDigest,
                String artifactDigest) {
            this.familyId = ToolRegistry.requireFamilyId(familyId);
            this.contractDigest = ToolInvocationContext.requireDigest(
                    contractDigest, "allowlist.contractDigest");
            this.signerDigest = ToolInvocationContext.requireDigest(
                    signerDigest, "allowlist.signerDigest");
            this.artifactDigest = ToolInvocationContext.requireDigest(
                    artifactDigest, "allowlist.artifactDigest");
        }

        public String getFamilyId() {
            return familyId;
        }

        public String getContractDigest() {
            return contractDigest;
        }

        public String getSignerDigest() {
            return signerDigest;
        }

        public String getArtifactDigest() {
            return artifactDigest;
        }
    }

    public static final class Registration {
        private final ToolManifest manifest;
        private final String signerDigest;
        private final String artifactDigest;
        private final BuiltInTool implementation;

        public Registration(
                ToolManifest manifest,
                String signerDigest,
                String artifactDigest,
                BuiltInTool implementation) {
            this.manifest = Objects.requireNonNull(manifest, "manifest");
            this.signerDigest = ToolInvocationContext.requireDigest(
                    signerDigest, "registration.signerDigest");
            this.artifactDigest = ToolInvocationContext.requireDigest(
                    artifactDigest, "registration.artifactDigest");
            this.implementation = Objects.requireNonNull(implementation, "implementation");
        }

        public ToolManifest getManifest() {
            return manifest;
        }

        public String getSignerDigest() {
            return signerDigest;
        }

        public String getArtifactDigest() {
            return artifactDigest;
        }
    }

    private static final class Binding {
        private final ToolManifest manifest;
        private final BuiltInTool implementation;

        private Binding(ToolManifest manifest, BuiltInTool implementation) {
            this.manifest = manifest;
            this.implementation = implementation;
        }
    }

    private final NavigableMap<String, Binding> bindings;
    private final ElapsedRealtimeClock clock;
    private final ToolSchemaValidator schemaValidator = new ToolSchemaValidator();
    private final List<AuditRecord> audits = new ArrayList<>();
    private long auditSequence;
    private long auditEvictionCount;

    public InProcessBuiltInToolExecutor(
            List<AllowlistEntry> allowlist,
            List<Registration> registrations,
            String currentApplicationSignerDigest,
            ElapsedRealtimeClock clock) {
        Objects.requireNonNull(allowlist, "allowlist");
        Objects.requireNonNull(registrations, "registrations");
        if (allowlist.size() > MAX_BINDINGS || registrations.size() > MAX_BINDINGS) {
            throw new ConstructionException(ConstructionError.BINDING_LIMIT_EXCEEDED);
        }
        String currentSigner = ToolInvocationContext.requireDigest(
                currentApplicationSignerDigest, "currentApplicationSignerDigest");
        this.clock = Objects.requireNonNull(clock, "clock");

        TreeMap<String, AllowlistEntry> allowed = new TreeMap<>();
        for (AllowlistEntry candidate : allowlist) {
            AllowlistEntry entry = Objects.requireNonNull(candidate, "allowlistEntry");
            if (!entry.signerDigest.equals(currentSigner)) {
                throw new ConstructionException(
                        ConstructionError.CURRENT_SIGNER_NOT_ALLOWLISTED);
            }
            if (allowed.putIfAbsent(entry.familyId, entry) != null) {
                throw new ConstructionException(
                        ConstructionError.DUPLICATE_ALLOWLIST_FAMILY);
            }
        }

        TreeMap<String, Binding> collected = new TreeMap<>();
        for (Registration candidate : registrations) {
            Registration registration = Objects.requireNonNull(
                    candidate, "registration");
            ToolManifest manifest = registration.manifest;
            if (!"runtime.builtin".equals(manifest.getOwnerId())) {
                throw new ConstructionException(
                        ConstructionError.BUILT_IN_OWNER_REQUIRED);
            }
            AllowlistEntry entry = allowed.get(manifest.getFamilyId());
            if (entry == null) {
                throw new ConstructionException(ConstructionError.TOOL_NOT_ALLOWLISTED);
            }
            if (!entry.contractDigest.equals(manifest.getContractDigest())) {
                throw new ConstructionException(
                        ConstructionError.CONTRACT_BINDING_MISMATCH);
            }
            if (!entry.signerDigest.equals(registration.signerDigest)) {
                throw new ConstructionException(
                        ConstructionError.SIGNER_BINDING_MISMATCH);
            }
            if (!entry.artifactDigest.equals(registration.artifactDigest)) {
                throw new ConstructionException(
                        ConstructionError.ARTIFACT_BINDING_MISMATCH);
            }
            if (collected.putIfAbsent(
                    manifest.getFamilyId(),
                    new Binding(manifest, registration.implementation)) != null) {
                throw new ConstructionException(
                        ConstructionError.DUPLICATE_REGISTRATION_FAMILY);
            }
        }
        bindings = Collections.unmodifiableNavigableMap(collected);
    }

    @Override
    public synchronized ExecutionResult execute(
            ToolRuleSolver.Selection selection,
            ToolInvocationContext context,
            Map<?, ?> input,
            CancellationSignal cancellationSignal) {
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(cancellationSignal, "cancellationSignal");
        long startedAt = clock.nowMs();
        ToolManifest selected = selection.getManifest();
        Binding binding = bindings.get(selected.getFamilyId());
        if (binding == null) {
            return finish(
                    context, selected, startedAt, Outcome.REJECTED,
                    FailureCode.TOOL_NOT_ALLOWLISTED, Map.of(), 0);
        }
        if (!binding.manifest.getContractDigest().equals(selected.getContractDigest())) {
            return finish(
                    context, selected, startedAt, Outcome.REJECTED,
                    FailureCode.MANIFEST_BINDING_MISMATCH, Map.of(), 0);
        }
        if (!context.getToolFamilyId().equals(selected.getFamilyId())
                || !context.getToolContractDigest().equals(selected.getContractDigest())
                || !context.getRequiredCapabilityId().equals(selected.getCapabilityId())) {
            return finish(
                    context, selected, startedAt, Outcome.REJECTED,
                    FailureCode.CONTEXT_BINDING_MISMATCH, Map.of(), 0);
        }
        if (selection.isApprovalRequired()) {
            return finish(
                    context, selected, startedAt, Outcome.REJECTED,
                    FailureCode.APPROVAL_REQUIRED, Map.of(), 0);
        }
        if (selected.getIdempotencyMode() == ToolManifest.IdempotencyMode.TOKEN_REQUIRED
                && context.getIdempotencyTokenDigest() == null) {
            return finish(
                    context, selected, startedAt, Outcome.REJECTED,
                    FailureCode.IDEMPOTENCY_TOKEN_REQUIRED, Map.of(), 0);
        }
        if (context.getDeadlineElapsedRealtimeMs()
                - context.getIssuedAtElapsedRealtimeMs() > selected.getTimeoutMs()) {
            return finish(
                    context, selected, startedAt, Outcome.REJECTED,
                    FailureCode.DEADLINE_POLICY_VIOLATION, Map.of(), 0);
        }
        FailureCode admissionClock = clockFailure(context, startedAt);
        if (admissionClock != FailureCode.NONE) {
            return finish(
                    context, selected, startedAt,
                    admissionClock == FailureCode.DEADLINE_EXCEEDED
                            ? Outcome.TIMED_OUT : Outcome.REJECTED,
                    admissionClock, Map.of(), 0);
        }
        if (cancellationSignal.isCancellationRequested()) {
            return finish(
                    context, selected, startedAt, Outcome.CANCELLED,
                    FailureCode.CANCELLED, Map.of(), 0);
        }

        final Map<String, Object> validatedInput;
        try {
            validatedInput = schemaValidator.validateInput(selected, input);
        } catch (ToolSchemaValidator.ValidationException exception) {
            return finish(
                    context, selected, startedAt, Outcome.REJECTED,
                    FailureCode.INPUT_INVALID, Map.of(), 0);
        }

        ExecutionControl control = new Control(context, cancellationSignal);
        final Map<String, Object> rawOutput;
        try {
            control.checkpoint();
            rawOutput = binding.implementation.invoke(validatedInput, control);
            control.checkpoint();
        } catch (ExecutionAbortedException exception) {
            return finish(
                    context,
                    selected,
                    startedAt,
                    abortOutcome(exception.getReason()),
                    abortFailure(exception.getReason()),
                    Map.of(),
                    0);
        } catch (RuntimeException exception) {
            return finish(
                    context, selected, startedAt, Outcome.FAILED,
                    FailureCode.IMPLEMENTATION_FAILURE, Map.of(), 0);
        }

        final Map<String, Object> output;
        try {
            output = schemaValidator.validateOutput(selected, rawOutput);
        } catch (ToolSchemaValidator.ValidationException exception) {
            return finish(
                    context, selected, startedAt, Outcome.FAILED,
                    FailureCode.OUTPUT_INVALID, Map.of(), 0);
        }
        int outputBytes = encodedBytes(output);
        if (outputBytes > context.getMaximumOutputBytes()) {
            return finish(
                    context, selected, startedAt, Outcome.FAILED,
                    FailureCode.OUTPUT_TOO_LARGE, Map.of(), 0);
        }
        return finish(
                context, selected, startedAt, Outcome.SUCCEEDED,
                FailureCode.NONE, output, outputBytes);
    }

    @Override
    public synchronized List<AuditRecord> recentAudits(int limit) {
        if (limit < 1 || limit > MAX_AUDIT_RECORDS) {
            throw new IllegalArgumentException("CB_TOOL_EXECUTOR: invalid audit limit");
        }
        int first = Math.max(0, audits.size() - limit);
        return Collections.unmodifiableList(new ArrayList<>(audits.subList(first, audits.size())));
    }

    @Override
    public synchronized long getAuditEvictionCount() {
        return auditEvictionCount;
    }

    @Override
    public boolean isProductionWired() {
        return false;
    }

    @Override
    public boolean isOsVirtualizationEnabled() {
        return false;
    }

    private ExecutionResult finish(
            ToolInvocationContext context,
            ToolManifest manifest,
            long startedAt,
            Outcome requestedOutcome,
            FailureCode requestedFailure,
            Map<String, Object> requestedOutput,
            int requestedOutputBytes) {
        long observedFinish = clock.nowMs();
        Outcome outcome = requestedOutcome;
        FailureCode failureCode = requestedFailure;
        Map<String, Object> output = requestedOutput;
        int outputBytes = requestedOutputBytes;
        long finishedAt = observedFinish;
        if (observedFinish < startedAt) {
            outcome = Outcome.REJECTED;
            failureCode = FailureCode.CLOCK_INVALID;
            output = Map.of();
            outputBytes = 0;
            finishedAt = startedAt;
        } else if (requestedOutcome == Outcome.SUCCEEDED
                && observedFinish >= context.getDeadlineElapsedRealtimeMs()) {
            outcome = Outcome.TIMED_OUT;
            failureCode = FailureCode.DEADLINE_EXCEEDED;
            output = Map.of();
            outputBytes = 0;
        }
        long sequence = ++auditSequence;
        String auditDigest = digestAudit(
                sequence, context, manifest, outcome, failureCode,
                startedAt, finishedAt, outputBytes);
        AuditRecord audit = new AuditRecord(
                sequence,
                context.getInvocationDigest(),
                context.getAuditCorrelationDigest(),
                manifest.getFamilyId(),
                manifest.getContractDigest(),
                outcome,
                failureCode,
                startedAt,
                finishedAt,
                outputBytes,
                auditDigest);
        audits.add(audit);
        while (audits.size() > MAX_AUDIT_RECORDS) {
            audits.remove(0);
            auditEvictionCount++;
        }
        return new ExecutionResult(outcome, failureCode, output, audit);
    }

    private FailureCode clockFailure(ToolInvocationContext context, long now) {
        if (now < 0L || now < context.getIssuedAtElapsedRealtimeMs()) {
            return FailureCode.CLOCK_INVALID;
        }
        if (now >= context.getDeadlineElapsedRealtimeMs()) {
            return FailureCode.DEADLINE_EXCEEDED;
        }
        return FailureCode.NONE;
    }

    private final class Control implements ExecutionControl {
        private final ToolInvocationContext context;
        private final CancellationSignal cancellationSignal;

        private Control(
                ToolInvocationContext context, CancellationSignal cancellationSignal) {
            this.context = context;
            this.cancellationSignal = cancellationSignal;
        }

        @Override
        public ToolInvocationContext getContext() {
            return context;
        }

        @Override
        public long remainingTimeMs() {
            long now = clock.nowMs();
            if (now < context.getIssuedAtElapsedRealtimeMs()) {
                return 0L;
            }
            return Math.max(0L, context.getDeadlineElapsedRealtimeMs() - now);
        }

        @Override
        public void checkpoint() {
            if (cancellationSignal.isCancellationRequested()) {
                throw new ExecutionAbortedException(AbortReason.CANCELLED);
            }
            long now = clock.nowMs();
            if (now < context.getIssuedAtElapsedRealtimeMs()) {
                throw new ExecutionAbortedException(AbortReason.CLOCK_INVALID);
            }
            if (now >= context.getDeadlineElapsedRealtimeMs()) {
                throw new ExecutionAbortedException(AbortReason.DEADLINE_EXCEEDED);
            }
        }
    }

    private static FailureCode abortFailure(AbortReason reason) {
        switch (reason) {
            case CANCELLED:
                return FailureCode.CANCELLED;
            case DEADLINE_EXCEEDED:
                return FailureCode.DEADLINE_EXCEEDED;
            case CLOCK_INVALID:
                return FailureCode.CLOCK_INVALID;
            default:
                throw new IllegalStateException("CB_TOOL_EXECUTOR: unknown abort reason");
        }
    }

    private static Outcome abortOutcome(AbortReason reason) {
        switch (reason) {
            case CANCELLED:
                return Outcome.CANCELLED;
            case DEADLINE_EXCEEDED:
                return Outcome.TIMED_OUT;
            case CLOCK_INVALID:
                return Outcome.REJECTED;
            default:
                throw new IllegalStateException("CB_TOOL_EXECUTOR: unknown abort reason");
        }
    }

    private static int encodedBytes(Map<String, Object> values) {
        int encodedBytes = 2;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            encodedBytes += entry.getKey().getBytes(StandardCharsets.UTF_8).length + 6;
            Object value = entry.getValue();
            encodedBytes += value instanceof String
                    ? ((String) value).getBytes(StandardCharsets.UTF_8).length + 2
                    : value.toString().getBytes(StandardCharsets.US_ASCII).length;
        }
        return encodedBytes;
    }

    private static String digestAudit(
            long sequence,
            ToolInvocationContext context,
            ToolManifest manifest,
            Outcome outcome,
            FailureCode failureCode,
            long startedAt,
            long finishedAt,
            int outputBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, "central-brain-tool-executor-audit-v1");
            update(digest, Long.toString(sequence));
            update(digest, context.getInvocationDigest());
            update(digest, context.getAuditCorrelationDigest());
            update(digest, manifest.getFamilyId());
            update(digest, manifest.getContractDigest());
            update(digest, outcome.name());
            update(digest, failureCode.name());
            update(digest, Long.toString(startedAt));
            update(digest, Long.toString(finishedAt));
            update(digest, Integer.toString(outputBytes));
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(Character.forDigit((value >>> 4) & 0xf, 16));
            result.append(Character.forDigit(value & 0xf, 16));
        }
        return result.toString();
    }
}
