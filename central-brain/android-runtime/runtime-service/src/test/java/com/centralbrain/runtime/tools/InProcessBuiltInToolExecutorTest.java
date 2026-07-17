package com.centralbrain.runtime.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.centralbrain.runtime.tools.InProcessBuiltInToolExecutor.AllowlistEntry;
import com.centralbrain.runtime.tools.InProcessBuiltInToolExecutor.ConstructionError;
import com.centralbrain.runtime.tools.InProcessBuiltInToolExecutor.ConstructionException;
import com.centralbrain.runtime.tools.InProcessBuiltInToolExecutor.Registration;
import com.centralbrain.runtime.tools.ToolHealthSnapshot.Observation;
import com.centralbrain.runtime.tools.ToolHealthSnapshot.State;
import com.centralbrain.runtime.tools.ToolManifest.FieldSchema;
import com.centralbrain.runtime.tools.ToolManifest.HealthContract;
import com.centralbrain.runtime.tools.ToolManifest.IdempotencyMode;
import com.centralbrain.runtime.tools.ToolManifest.ObjectSchema;
import com.centralbrain.runtime.tools.ToolManifest.RiskClass;
import com.centralbrain.runtime.tools.ToolRuleSolver.Request;
import com.centralbrain.runtime.tools.ToolRuleSolver.Selection;

import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class InProcessBuiltInToolExecutorTest {
    private static final String FAMILY = "tool.runtime.echo";
    private static final String OTHER_FAMILY = "tool.runtime.other";
    private static final String CAPABILITY = "runtime.tool.echo";
    private static final String SIGNER = "a".repeat(64);
    private static final String ARTIFACT = "b".repeat(64);
    private static final String REQUEST = "c".repeat(64);

    @Test
    public void invocationContextIsBoundedCanonicalAndNotAuthority() {
        ToolManifest manifest = manifest(FAMILY, "runtime.builtin");
        ToolInvocationContext context = context(manifest, 9_000L, 11_000L, 256, REQUEST);

        assertEquals(ToolInvocationContext.SCHEMA_VERSION, context.getSchemaVersion());
        assertEquals(FAMILY, context.getToolFamilyId());
        assertEquals(manifest.getContractDigest(), context.getToolContractDigest());
        assertEquals(CAPABILITY, context.getRequiredCapabilityId());
        assertEquals(256, context.getMaximumOutputBytes());
        assertFalse(context.isProductionAuthority());
        assertThrows(
                IllegalArgumentException.class,
                () -> context(manifest, 9_000L, 9_000L, 256, REQUEST));
        assertThrows(
                IllegalArgumentException.class,
                () -> context(manifest, 9_000L, 11_000L, 16_385, REQUEST));
        assertThrows(
                IllegalArgumentException.class,
                () -> context(manifest, 9_000L, 11_000L, 256, "not-a-digest"));
    }

    @Test
    public void constructionRequiresExactBuiltInSignerArtifactAndAllowlist() {
        ToolManifest manifest = manifest(FAMILY, "runtime.builtin");
        ManualClock clock = new ManualClock(10_000L);
        AllowlistEntry entry = allowlist(manifest);
        Registration registration = registration(
                manifest, (input, control) -> Map.of("status", "ok"));

        InProcessBuiltInToolExecutor executor = new InProcessBuiltInToolExecutor(
                List.of(entry), List.of(registration), SIGNER, clock);
        assertFalse(executor.isProductionWired());
        assertFalse(executor.isOsVirtualizationEnabled());

        assertConstruction(
                ConstructionError.CURRENT_SIGNER_NOT_ALLOWLISTED,
                () -> new InProcessBuiltInToolExecutor(
                        List.of(entry), List.of(registration), "d".repeat(64), clock));
        assertConstruction(
                ConstructionError.ARTIFACT_BINDING_MISMATCH,
                () -> new InProcessBuiltInToolExecutor(
                        List.of(entry),
                        List.of(new Registration(
                                manifest,
                                SIGNER,
                                "e".repeat(64),
                                (input, control) -> Map.of("status", "ok"))),
                        SIGNER,
                        clock));
        ToolManifest externalOwner = manifest(FAMILY, "vendor.dynamic");
        assertConstruction(
                ConstructionError.BUILT_IN_OWNER_REQUIRED,
                () -> new InProcessBuiltInToolExecutor(
                        List.of(allowlist(externalOwner)),
                        List.of(registration(
                                externalOwner,
                                (input, control) -> Map.of("status", "ok"))),
                        SIGNER,
                        clock));
        assertConstruction(
                ConstructionError.BINDING_LIMIT_EXCEEDED,
                () -> new InProcessBuiltInToolExecutor(
                        Collections.nCopies(65, entry), List.of(), SIGNER, clock));
    }

    @Test
    public void exactSelectionExecutesWithValidatedOutputAndDigestOnlyAudit() {
        ToolManifest manifest = manifest(FAMILY, "runtime.builtin");
        ManualClock clock = new ManualClock(10_000L);
        AtomicInteger invocations = new AtomicInteger();
        InProcessBuiltInToolExecutor executor = executor(
                manifest,
                clock,
                (input, control) -> {
                    control.checkpoint();
                    invocations.incrementAndGet();
                    assertEquals(REQUEST, input.get("requestDigest"));
                    return Map.of("status", "ok");
                });
        ToolExecutor.ExecutionResult result = executor.execute(
                selection(manifest, false),
                context(manifest, 9_000L, 11_000L, 256, REQUEST),
                Map.of("requestDigest", REQUEST),
                () -> false);

        assertTrue(result.isSuccess());
        assertEquals(ToolExecutor.Outcome.SUCCEEDED, result.getOutcome());
        assertEquals(ToolExecutor.FailureCode.NONE, result.getFailureCode());
        assertEquals(Map.of("status", "ok"), result.getOutput());
        assertEquals(1, invocations.get());
        assertEquals(1L, result.getAudit().getSequence());
        assertEquals(FAMILY, result.getAudit().getToolFamilyId());
        assertTrue(result.getAudit().getOutputBytes() > 0);
        assertTrue(result.getAudit().getAuditDigest().matches("[0-9a-f]{64}"));
        assertThrows(UnsupportedOperationException.class, () -> result.getOutput().clear());
    }

    @Test
    public void approvalDeadlineAndCancellationFailClosedWithoutInvocation() {
        ToolManifest manifest = manifest(FAMILY, "runtime.builtin");
        ManualClock clock = new ManualClock(10_000L);
        AtomicInteger invocations = new AtomicInteger();
        InProcessBuiltInToolExecutor executor = executor(
                manifest,
                clock,
                (input, control) -> {
                    invocations.incrementAndGet();
                    return Map.of("status", "ok");
                });

        assertFailure(
                executor.execute(
                        selection(manifest, true),
                        context(manifest, 9_000L, 11_000L, 256, REQUEST),
                        Map.of("requestDigest", REQUEST),
                        () -> false),
                ToolExecutor.Outcome.REJECTED,
                ToolExecutor.FailureCode.APPROVAL_REQUIRED);
        assertFailure(
                executor.execute(
                        selection(manifest, false),
                        context(manifest, 9_000L, 13_000L, 256, REQUEST),
                        Map.of("requestDigest", REQUEST),
                        () -> false),
                ToolExecutor.Outcome.REJECTED,
                ToolExecutor.FailureCode.DEADLINE_POLICY_VIOLATION);
        assertFailure(
                executor.execute(
                        selection(manifest, false),
                        context(manifest, 9_000L, 10_000L, 256, REQUEST),
                        Map.of("requestDigest", REQUEST),
                        () -> false),
                ToolExecutor.Outcome.TIMED_OUT,
                ToolExecutor.FailureCode.DEADLINE_EXCEEDED);
        assertFailure(
                executor.execute(
                        selection(manifest, false),
                        context(manifest, 9_000L, 11_000L, 256, REQUEST),
                        Map.of("requestDigest", REQUEST),
                        () -> true),
                ToolExecutor.Outcome.CANCELLED,
                ToolExecutor.FailureCode.CANCELLED);
        assertEquals(0, invocations.get());

        ToolManifest other = manifest(OTHER_FAMILY, "runtime.builtin");
        assertFailure(
                executor.execute(
                        selection(other, false),
                        context(other, 9_000L, 11_000L, 256, REQUEST),
                        Map.of("requestDigest", REQUEST),
                        () -> false),
                ToolExecutor.Outcome.REJECTED,
                ToolExecutor.FailureCode.TOOL_NOT_ALLOWLISTED);

        AtomicBoolean cancel = new AtomicBoolean(false);
        InProcessBuiltInToolExecutor cooperative = executor(
                manifest,
                clock,
                (input, control) -> {
                    cancel.set(true);
                    control.checkpoint();
                    return Map.of("status", "unreachable");
                });
        assertFailure(
                cooperative.execute(
                        selection(manifest, false),
                        context(manifest, 9_000L, 11_000L, 256, REQUEST),
                        Map.of("requestDigest", REQUEST),
                        cancel::get),
                ToolExecutor.Outcome.CANCELLED,
                ToolExecutor.FailureCode.CANCELLED);

        cancel.set(false);
        clock.set(10_000L);
        InProcessBuiltInToolExecutor cooperativeTimeout = executor(
                manifest,
                clock,
                (input, control) -> {
                    clock.set(11_000L);
                    control.checkpoint();
                    return Map.of("status", "unreachable");
                });
        assertFailure(
                cooperativeTimeout.execute(
                        selection(manifest, false),
                        context(manifest, 9_000L, 11_000L, 256, REQUEST),
                        Map.of("requestDigest", REQUEST),
                        cancel::get),
                ToolExecutor.Outcome.TIMED_OUT,
                ToolExecutor.FailureCode.DEADLINE_EXCEEDED);
        clock.set(10_000L);
    }

    @Test
    public void invalidInputOutputAndImplementationAreAuditedAndBounded() {
        ToolManifest manifest = manifest(FAMILY, "runtime.builtin");
        ManualClock clock = new ManualClock(10_000L);
        Selection selection = selection(manifest, false);
        ToolInvocationContext context = context(
                manifest, 9_000L, 11_000L, 256, REQUEST);

        InProcessBuiltInToolExecutor success = executor(
                manifest, clock, (input, control) -> Map.of("status", "ok"));
        assertFailure(
                success.execute(selection, context, Map.of(), () -> false),
                ToolExecutor.Outcome.REJECTED,
                ToolExecutor.FailureCode.INPUT_INVALID);

        InProcessBuiltInToolExecutor invalidOutput = executor(
                manifest, clock, (input, control) -> Map.of("unknown", "x"));
        assertFailure(
                invalidOutput.execute(
                        selection, context, Map.of("requestDigest", REQUEST), () -> false),
                ToolExecutor.Outcome.FAILED,
                ToolExecutor.FailureCode.OUTPUT_INVALID);

        InProcessBuiltInToolExecutor oversized = executor(
                manifest, clock, (input, control) -> Map.of("status", "0123456789"));
        assertFailure(
                oversized.execute(
                        selection,
                        context(manifest, 9_000L, 11_000L, 10, REQUEST),
                        Map.of("requestDigest", REQUEST),
                        () -> false),
                ToolExecutor.Outcome.FAILED,
                ToolExecutor.FailureCode.OUTPUT_TOO_LARGE);

        InProcessBuiltInToolExecutor failing = executor(
                manifest,
                clock,
                (input, control) -> {
                    throw new IllegalStateException("must not escape");
                });
        assertFailure(
                failing.execute(
                        selection, context, Map.of("requestDigest", REQUEST), () -> false),
                ToolExecutor.Outcome.FAILED,
                ToolExecutor.FailureCode.IMPLEMENTATION_FAILURE);

        InProcessBuiltInToolExecutor boundedAudit = executor(
                manifest, clock, (input, control) -> Map.of("status", "ok"));
        for (int index = 0; index < 129; index++) {
            ToolInvocationContext unique = context(
                    manifest,
                    9_000L,
                    11_000L,
                    256,
                    String.format("%064x", index + 1));
            assertTrue(boundedAudit.execute(
                    selection,
                    unique,
                    Map.of("requestDigest", REQUEST),
                    () -> false).isSuccess());
        }
        assertEquals(128, boundedAudit.recentAudits(128).size());
        assertEquals(2L, boundedAudit.recentAudits(128).get(0).getSequence());
        assertEquals(1L, boundedAudit.getAuditEvictionCount());
        assertThrows(
                UnsupportedOperationException.class,
                () -> boundedAudit.recentAudits(1).clear());
    }

    private static InProcessBuiltInToolExecutor executor(
            ToolManifest manifest,
            ManualClock clock,
            ToolExecutor.BuiltInTool implementation) {
        return new InProcessBuiltInToolExecutor(
                List.of(allowlist(manifest)),
                List.of(registration(manifest, implementation)),
                SIGNER,
                clock);
    }

    private static AllowlistEntry allowlist(ToolManifest manifest) {
        return new AllowlistEntry(
                manifest.getFamilyId(),
                manifest.getContractDigest(),
                SIGNER,
                ARTIFACT);
    }

    private static Registration registration(
            ToolManifest manifest, ToolExecutor.BuiltInTool implementation) {
        return new Registration(manifest, SIGNER, ARTIFACT, implementation);
    }

    private static ToolInvocationContext context(
            ToolManifest manifest,
            long issuedAt,
            long deadline,
            int outputLimit,
            String invocationDigest) {
        return new ToolInvocationContext(
                ToolInvocationContext.SCHEMA_VERSION,
                invocationDigest,
                "1".repeat(64),
                "2".repeat(64),
                "3".repeat(64),
                "4".repeat(64),
                manifest.getFamilyId(),
                manifest.getContractDigest(),
                manifest.getCapabilityId(),
                "5".repeat(64),
                issuedAt,
                deadline,
                outputLimit);
    }

    private static Selection selection(ToolManifest manifest, boolean approvalRequired) {
        ToolResolver resolver = new ToolResolver(new ToolRegistry(List.of(manifest)));
        ToolResolver.Resolution resolution = resolver.resolve(
                new ToolResolver.Query(
                        manifest.getFamilyId(),
                        1,
                        1,
                        manifest.getCapabilityId(),
                        manifest.getContractDigest()),
                new ToolHealthSnapshot(List.of(new Observation(
                        manifest.getHealthContract().getCheckId(),
                        State.HEALTHY,
                        9_000L,
                        1L))),
                10_000L);
        ToolRuleSet rules = new ToolRuleSet(
                List.of(manifest.getFamilyId()),
                List.of(manifest.getFamilyId()),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                approvalRequired ? List.of(manifest.getFamilyId()) : List.of());
        ToolRuleSolver.Result solved = new ToolRuleSolver(rules).solve(
                new Request(
                        null,
                        List.of(manifest.getFamilyId()),
                        List.of(),
                        ToolRuleSolver.ConditionSnapshot.empty()),
                List.of(resolution));
        assertTrue(solved.isAllowed());
        return solved.getSelections().get(0);
    }

    private static ToolManifest manifest(String family, String owner) {
        ObjectSchema input = new ObjectSchema(
                "tool.input.runtime-echo.v1",
                1,
                256,
                List.of(FieldSchema.sha256DigestField("requestDigest", true)));
        ObjectSchema output = new ObjectSchema(
                "tool.output.runtime-echo.v1",
                1,
                256,
                List.of(FieldSchema.stringField("status", true, 64)));
        return new ToolManifest(
                ToolManifest.SCHEMA_VERSION,
                family + ".v1",
                1,
                owner,
                input,
                output,
                CAPABILITY,
                RiskClass.LOW,
                2_500L,
                IdempotencyMode.TOKEN_REQUIRED,
                new HealthContract("health.runtime.echo.v1", 5_000L, true));
    }

    private static void assertFailure(
            ToolExecutor.ExecutionResult result,
            ToolExecutor.Outcome outcome,
            ToolExecutor.FailureCode failureCode) {
        assertFalse(result.isSuccess());
        assertEquals(outcome, result.getOutcome());
        assertEquals(failureCode, result.getFailureCode());
        assertTrue(result.getOutput().isEmpty());
        assertEquals(failureCode, result.getAudit().getFailureCode());
    }

    private static void assertConstruction(
            ConstructionError error,
            org.junit.function.ThrowingRunnable runnable) {
        ConstructionException exception = assertThrows(ConstructionException.class, runnable);
        assertEquals(error, exception.getErrorCode());
    }

    private static final class ManualClock
            implements InProcessBuiltInToolExecutor.ElapsedRealtimeClock {
        private long now;

        private ManualClock(long now) {
            this.now = now;
        }

        @Override
        public long nowMs() {
            return now;
        }

        private void set(long now) {
            this.now = now;
        }
    }
}
