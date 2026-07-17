package com.centralbrain.runtime.tools;

import java.util.List;
import java.util.Map;

/** Boundary for bounded built-in Tool invocation. It does not imply Runtime publication. */
public interface ToolExecutor {
    enum Outcome {
        SUCCEEDED,
        REJECTED,
        CANCELLED,
        TIMED_OUT,
        FAILED
    }

    enum FailureCode {
        NONE,
        TOOL_NOT_ALLOWLISTED,
        MANIFEST_BINDING_MISMATCH,
        CONTEXT_BINDING_MISMATCH,
        APPROVAL_REQUIRED,
        IDEMPOTENCY_TOKEN_REQUIRED,
        DEADLINE_POLICY_VIOLATION,
        DEADLINE_EXCEEDED,
        CLOCK_INVALID,
        CANCELLED,
        INPUT_INVALID,
        OUTPUT_INVALID,
        OUTPUT_TOO_LARGE,
        IMPLEMENTATION_FAILURE
    }

    enum AbortReason {
        CANCELLED,
        DEADLINE_EXCEEDED,
        CLOCK_INVALID
    }

    interface CancellationSignal {
        boolean isCancellationRequested();
    }

    interface ExecutionControl {
        ToolInvocationContext getContext();

        long remainingTimeMs();

        void checkpoint();
    }

    interface BuiltInTool {
        Map<String, Object> invoke(
                Map<String, Object> input, ExecutionControl control);
    }

    final class ExecutionAbortedException extends RuntimeException {
        private final AbortReason reason;

        ExecutionAbortedException(AbortReason reason) {
            super("CB_TOOL_EXECUTION_ABORTED: " + reason.name());
            this.reason = reason;
        }

        public AbortReason getReason() {
            return reason;
        }
    }

    final class AuditRecord {
        private final long sequence;
        private final String invocationDigest;
        private final String auditCorrelationDigest;
        private final String toolFamilyId;
        private final String toolContractDigest;
        private final Outcome outcome;
        private final FailureCode failureCode;
        private final long startedAtElapsedRealtimeMs;
        private final long finishedAtElapsedRealtimeMs;
        private final int outputBytes;
        private final String auditDigest;

        AuditRecord(
                long sequence,
                String invocationDigest,
                String auditCorrelationDigest,
                String toolFamilyId,
                String toolContractDigest,
                Outcome outcome,
                FailureCode failureCode,
                long startedAtElapsedRealtimeMs,
                long finishedAtElapsedRealtimeMs,
                int outputBytes,
                String auditDigest) {
            this.sequence = sequence;
            this.invocationDigest = invocationDigest;
            this.auditCorrelationDigest = auditCorrelationDigest;
            this.toolFamilyId = toolFamilyId;
            this.toolContractDigest = toolContractDigest;
            this.outcome = outcome;
            this.failureCode = failureCode;
            this.startedAtElapsedRealtimeMs = startedAtElapsedRealtimeMs;
            this.finishedAtElapsedRealtimeMs = finishedAtElapsedRealtimeMs;
            this.outputBytes = outputBytes;
            this.auditDigest = auditDigest;
        }

        public long getSequence() {
            return sequence;
        }

        public String getInvocationDigest() {
            return invocationDigest;
        }

        public String getAuditCorrelationDigest() {
            return auditCorrelationDigest;
        }

        public String getToolFamilyId() {
            return toolFamilyId;
        }

        public String getToolContractDigest() {
            return toolContractDigest;
        }

        public Outcome getOutcome() {
            return outcome;
        }

        public FailureCode getFailureCode() {
            return failureCode;
        }

        public long getStartedAtElapsedRealtimeMs() {
            return startedAtElapsedRealtimeMs;
        }

        public long getFinishedAtElapsedRealtimeMs() {
            return finishedAtElapsedRealtimeMs;
        }

        public int getOutputBytes() {
            return outputBytes;
        }

        public String getAuditDigest() {
            return auditDigest;
        }
    }

    final class ExecutionResult {
        private final Outcome outcome;
        private final FailureCode failureCode;
        private final Map<String, Object> output;
        private final AuditRecord audit;

        ExecutionResult(
                Outcome outcome,
                FailureCode failureCode,
                Map<String, Object> output,
                AuditRecord audit) {
            this.outcome = outcome;
            this.failureCode = failureCode;
            this.output = Map.copyOf(output);
            this.audit = audit;
        }

        public Outcome getOutcome() {
            return outcome;
        }

        public FailureCode getFailureCode() {
            return failureCode;
        }

        public Map<String, Object> getOutput() {
            return output;
        }

        public AuditRecord getAudit() {
            return audit;
        }

        public boolean isSuccess() {
            return outcome == Outcome.SUCCEEDED && failureCode == FailureCode.NONE;
        }
    }

    ExecutionResult execute(
            ToolRuleSolver.Selection selection,
            ToolInvocationContext context,
            Map<?, ?> input,
            CancellationSignal cancellationSignal);

    List<AuditRecord> recentAudits(int limit);

    long getAuditEvictionCount();

    boolean isProductionWired();

    boolean isOsVirtualizationEnabled();
}
