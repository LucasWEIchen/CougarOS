package com.centralbrain.runtime.orchestration;

import android.os.SystemClock;

import com.centralbrain.runtime.memory.ContextBudgetManager;
import com.centralbrain.runtime.memory.WorkingMemoryStore;
import com.centralbrain.runtime.skills.BoundedBuiltInSkillRuntime;
import com.centralbrain.runtime.tools.InProcessBuiltInToolExecutor;
import com.centralbrain.runtime.tools.ToolExecutor;
import com.centralbrain.runtime.tools.ToolHealthSnapshot;
import com.centralbrain.runtime.tools.ToolInvocationContext;
import com.centralbrain.runtime.tools.ToolManifest;
import com.centralbrain.runtime.tools.ToolRegistry;
import com.centralbrain.runtime.tools.ToolResolver;
import com.centralbrain.runtime.tools.ToolRuleSet;
import com.centralbrain.runtime.tools.ToolRuleSolver;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Debug-only composition of bounded Tool, Skill, Working Memory, and context contracts. */
final class DebugRuntimeCompositionBoundary {
    private static final int MAX_SESSIONS = 16;
    private static final long WORKING_MEMORY_TTL_MS = 5L * 60L * 1_000L;
    private static final String TOOL_FAMILY = "tool.runtime.composition";
    private static final String TOOL_CAPABILITY = "runtime.tool.composition";
    private static final String TOOL_SIGNER = "2".repeat(64);
    private static final String TOOL_ARTIFACT = "3".repeat(64);

    interface Clock extends InProcessBuiltInToolExecutor.ElapsedRealtimeClock {}

    private final Clock clock;
    private final BoundedBuiltInSkillRuntime skills;
    private final WorkingMemoryStore workingMemory;
    private final ContextBudgetManager contextBudget;
    private final ToolManifest toolManifest;
    private final InProcessBuiltInToolExecutor toolExecutor;
    private final Map<String, Entry> bySession = new LinkedHashMap<>();

    DebugRuntimeCompositionBoundary() {
        this(SystemClock::elapsedRealtime, () -> UUID.randomUUID().toString());
    }

    DebugRuntimeCompositionBoundary(Clock clock, Supplier<String> invocationIdSource) {
        this.clock = Objects.requireNonNull(clock, "clock");
        skills = BoundedBuiltInSkillRuntime.createForContractTest(
                new BoundedBuiltInSkillRuntime.Limits(MAX_SESSIONS, MAX_SESSIONS, 32),
                Objects.requireNonNull(invocationIdSource, "invocationIdSource"));
        workingMemory = new WorkingMemoryStore(
                new WorkingMemoryStore.Limits(
                        MAX_SESSIONS, 4, 512, 32, 128, 8, 32, 4,
                        WORKING_MEMORY_TTL_MS),
                clock::nowMs);
        contextBudget = ContextBudgetManager.createForContractTest();
        toolManifest = createToolManifest();
        toolExecutor = new InProcessBuiltInToolExecutor(
                List.of(new InProcessBuiltInToolExecutor.AllowlistEntry(
                        toolManifest.getFamilyId(),
                        toolManifest.getContractDigest(),
                        TOOL_SIGNER,
                        TOOL_ARTIFACT)),
                List.of(new InProcessBuiltInToolExecutor.Registration(
                        toolManifest,
                        TOOL_SIGNER,
                        TOOL_ARTIFACT,
                        (input, control) -> {
                            control.checkpoint();
                            return Map.of("status", "ready");
                        })),
                TOOL_SIGNER,
                clock);
    }

    synchronized Evidence prepare(
            OrchestrationBackend.SessionDescriptor session,
            String scenarioId,
            String requestDigest) {
        Objects.requireNonNull(session, "session");
        requireDigest(requestDigest, "requestDigest");
        Entry existing = bySession.get(session.getSessionId());
        if (existing != null) {
            if (!existing.requestDigest.equals(requestDigest)) {
                throw violation("session composition request conflict");
            }
            return existing.evidence;
        }
        if (bySession.size() >= MAX_SESSIONS) {
            throw violation("composition capacity exhausted");
        }

        String skillId = skillForScenario(scenarioId);
        BoundedBuiltInSkillRuntime.SkillManifest skillManifest =
                skills.findManifest(skillId);
        if (skillManifest == null) {
            throw violation("scenario Skill is unavailable");
        }
        BoundedBuiltInSkillRuntime.AdmissionResult skillAdmission = skills.admit(
                BoundedBuiltInSkillRuntime.TrustedInvocation.fromRuntimePolicy(
                        session.getOwnerFingerprint(),
                        "composition." + requestDigest.substring(0, 24),
                        skillManifest.getSkillId(),
                        skillManifest.getVersion(),
                        skillManifest.getInputSchemaId(),
                        requestDigest,
                        EnumSet.of(
                                BoundedBuiltInSkillRuntime.Capability.VEHICLE_READ,
                                BoundedBuiltInSkillRuntime.Capability.VEHICLE_CONTROL),
                        BoundedBuiltInSkillRuntime.SafetyState.NORMAL));
        if (skillAdmission.getOutcome()
                        != BoundedBuiltInSkillRuntime.AdmissionOutcome.ADMITTED
                || skillAdmission.getInvocation() == null
                || skillAdmission.getInvocation().isDispatchAllowed()) {
            throw violation("Skill governance admission failed closed");
        }

        ContextBudgetManager.AllocationResult allocation = contextBudget.allocate(
                budgetPolicy(),
                List.of(
                        ContextBudgetManager.ContextDescriptor.fromTrustedMetadata(
                                ContextBudgetManager.Category.SYSTEM,
                                "runtime.composition.policy",
                                4, 64, true, false, 100),
                        ContextBudgetManager.ContextDescriptor.fromTrustedMetadata(
                                ContextBudgetManager.Category.CONTEXT,
                                "runtime.composition.request",
                                4, 64, true, false, 90),
                        ContextBudgetManager.ContextDescriptor.fromTrustedMetadata(
                                ContextBudgetManager.Category.HISTORY,
                                "runtime.composition.skill",
                                4, 64, false, false, 80)));
        if (allocation.getOutcome()
                        != ContextBudgetManager.AllocationOutcome.ALLOCATED
                || allocation.getIncludeCount() != 3) {
            cancelSkill(skillAdmission.getInvocation(), session.getOwnerFingerprint());
            throw violation("context metadata budget failed closed");
        }

        ToolExecutor.ExecutionResult toolResult = toolExecutor.execute(
                resolveTool(toolManifest, clock.nowMs()),
                invocationContext(session, scenarioId, requestDigest),
                Map.of("requestDigest", requestDigest),
                () -> false);
        if (!toolResult.isSuccess()
                || !"ready".equals(toolResult.getOutput().get("status"))) {
            cancelSkill(skillAdmission.getInvocation(), session.getOwnerFingerprint());
            throw violation("metadata Tool boundary failed closed");
        }

        String evidenceDigest = digest(
                "central-brain-debug-runtime-composition-v1",
                requestDigest,
                skillAdmission.getInvocation().getInvocationId(),
                skillAdmission.getInvocation().getArtifactDigest(),
                toolManifest.getContractDigest(),
                toolResult.getAudit().getAuditDigest(),
                Integer.toString(allocation.getAllocatedTokens()),
                Integer.toString(allocation.getAllocatedBytes()));
        WorkingMemoryStore.PutResult memoryResult = workingMemory.put(
                WorkingMemoryStore.PutRequest.fromRuntimePolicy(
                        session.getOwnerFingerprint(),
                        session.getSessionId(),
                        "runtime.composition.digest",
                        "central.working-memory.v1",
                        evidenceDigest.getBytes(StandardCharsets.UTF_8),
                        1,
                        WORKING_MEMORY_TTL_MS));
        if (memoryResult.getOutcome() != WorkingMemoryStore.PutOutcome.CREATED
                || memoryResult.getItem() == null) {
            cancelSkill(skillAdmission.getInvocation(), session.getOwnerFingerprint());
            throw violation("Working Memory admission failed closed");
        }

        Evidence evidence = new Evidence(
                evidenceDigest,
                skillAdmission.getInvocation().getInvocationId(),
                skillAdmission.getInvocation().getSkillId(),
                toolResult.getAudit().getAuditDigest(),
                allocation.getAllocatedTokens(),
                allocation.getAllocatedBytes(),
                memoryResult.getItem().getByteCount());
        bySession.put(
                session.getSessionId(),
                new Entry(
                        session.getOwnerFingerprint(),
                        requestDigest,
                        skillAdmission.getInvocation(),
                        evidence));
        return evidence;
    }

    synchronized Completion complete(
            OrchestrationBackend.SessionDescriptor session,
            Evidence evidence) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(evidence, "evidence");
        Entry entry = bySession.get(session.getSessionId());
        if (entry == null
                || !entry.ownerFingerprint.equals(session.getOwnerFingerprint())
                || !entry.evidence.digest.equals(evidence.digest)) {
            throw violation("composition completion is not owner/session bound");
        }
        if (entry.completed) {
            return new Completion(true, 0, 0, 0);
        }
        BoundedBuiltInSkillRuntime.CancelOutcome skillOutcome = skills.cancelOwned(
                entry.skillInvocation.getInvocationId(), entry.ownerFingerprint);
        if (skillOutcome != BoundedBuiltInSkillRuntime.CancelOutcome.APPLIED) {
            throw violation("Skill terminal cleanup failed closed");
        }
        WorkingMemoryStore.TerminalResult memoryResult =
                workingMemory.terminateSessionOwned(
                        entry.ownerFingerprint, session.getSessionId());
        if (memoryResult.getOutcome() != WorkingMemoryStore.TerminalOutcome.APPLIED) {
            throw violation("Working Memory terminal cleanup failed closed");
        }
        entry.completed = true;
        return new Completion(
                false,
                memoryResult.getCleanedItemCount(),
                memoryResult.getCleanedByteCount(),
                memoryResult.getCleanedTokenCount());
    }

    synchronized Snapshot snapshot() {
        BoundedBuiltInSkillRuntime.Snapshot skillSnapshot = skills.snapshot();
        WorkingMemoryStore.Snapshot memorySnapshot = workingMemory.snapshot();
        int completed = 0;
        for (Entry entry : bySession.values()) {
            if (entry.completed) {
                completed++;
            }
        }
        return new Snapshot(
                bySession.size(),
                completed,
                skillSnapshot.getActiveInvocationCount(),
                memorySnapshot.getActiveItemCount(),
                toolExecutor.recentAudits(128).size());
    }

    synchronized void close() {
        for (Map.Entry<String, Entry> record : bySession.entrySet()) {
            Entry entry = record.getValue();
            if (entry.completed) {
                continue;
            }
            skills.cancelOwned(
                    entry.skillInvocation.getInvocationId(), entry.ownerFingerprint);
            workingMemory.terminateSessionOwned(
                    entry.ownerFingerprint, record.getKey());
            entry.completed = true;
        }
    }

    private ToolInvocationContext invocationContext(
            OrchestrationBackend.SessionDescriptor session,
            String scenarioId,
            String requestDigest) {
        long now = clock.nowMs();
        return new ToolInvocationContext(
                ToolInvocationContext.SCHEMA_VERSION,
                requestDigest,
                digest("central-brain-debug-session-v1", session.getSessionId()),
                digest("central-brain-debug-plan-request-v1", requestDigest),
                digest("central-brain-debug-scenario-v1", scenarioId),
                digest("central-brain-debug-audit-v1", session.getSessionId(), requestDigest),
                toolManifest.getFamilyId(),
                toolManifest.getContractDigest(),
                toolManifest.getCapabilityId(),
                requestDigest,
                now,
                now + 1_000L,
                256);
    }

    private static ContextBudgetManager.BudgetPolicy budgetPolicy() {
        return ContextBudgetManager.BudgetPolicy.fixed(
                16,
                256,
                4,
                new ContextBudgetManager.CategoryLimit(4, 64),
                new ContextBudgetManager.CategoryLimit(4, 64),
                new ContextBudgetManager.CategoryLimit(0, 0),
                new ContextBudgetManager.CategoryLimit(0, 0),
                new ContextBudgetManager.CategoryLimit(8, 128));
    }

    private static ToolManifest createToolManifest() {
        return new ToolManifest(
                ToolManifest.SCHEMA_VERSION,
                TOOL_FAMILY + ".v1",
                1,
                "runtime.builtin",
                new ToolManifest.ObjectSchema(
                        "tool.input.runtime-composition.v1",
                        1,
                        256,
                        List.of(ToolManifest.FieldSchema.sha256DigestField(
                                "requestDigest", true))),
                new ToolManifest.ObjectSchema(
                        "tool.output.runtime-composition.v1",
                        1,
                        256,
                        List.of(ToolManifest.FieldSchema.stringField(
                                "status", true, 64))),
                TOOL_CAPABILITY,
                ToolManifest.RiskClass.LOW,
                2_500L,
                ToolManifest.IdempotencyMode.TOKEN_REQUIRED,
                new ToolManifest.HealthContract(
                        "health.runtime.composition.v1", 5_000L, true));
    }

    private static ToolRuleSolver.Selection resolveTool(
            ToolManifest manifest, long now) {
        ToolResolver.Resolution resolution = new ToolResolver(
                new ToolRegistry(List.of(manifest))).resolve(
                new ToolResolver.Query(
                        manifest.getFamilyId(),
                        1,
                        1,
                        manifest.getCapabilityId(),
                        manifest.getContractDigest()),
                new ToolHealthSnapshot(List.of(new ToolHealthSnapshot.Observation(
                        manifest.getHealthContract().getCheckId(),
                        ToolHealthSnapshot.State.HEALTHY,
                        now,
                        1L))),
                now);
        ToolRuleSet rules = new ToolRuleSet(
                List.of(manifest.getFamilyId()),
                List.of(manifest.getFamilyId()),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
        ToolRuleSolver.Result result = new ToolRuleSolver(rules).solve(
                new ToolRuleSolver.Request(
                        null,
                        List.of(manifest.getFamilyId()),
                        List.of(),
                        ToolRuleSolver.ConditionSnapshot.empty()),
                List.of(resolution));
        if (!result.isAllowed() || result.getSelections().size() != 1) {
            throw violation("metadata Tool resolution failed closed");
        }
        return result.getSelections().get(0);
    }

    private static String skillForScenario(String scenarioId) {
        if ("scene.comfort.cold.v1".equals(scenarioId)) {
            return BoundedBuiltInSkillRuntime.SKILL_CABIN_PRECONDITION;
        }
        if ("scene.fatigue.assist.v1".equals(scenarioId)) {
            return BoundedBuiltInSkillRuntime.SKILL_CABIN_SCENE_NAP;
        }
        if ("scene.cabin.multimodal.assist.v1".equals(scenarioId)) {
            return BoundedBuiltInSkillRuntime.SKILL_CABIN_PRECONDITION;
        }
        throw violation("scenario has no built-in Skill binding");
    }

    private void cancelSkill(
            BoundedBuiltInSkillRuntime.InvocationSnapshot invocation,
            String ownerFingerprint) {
        skills.cancelOwned(invocation.getInvocationId(), ownerFingerprint);
    }

    private static String digest(String domain, String... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateDigest(digest, domain);
            for (String value : values) {
                updateDigest(digest, value);
            }
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static void updateDigest(MessageDigest digest, String value) {
        byte[] bytes = Objects.requireNonNull(value, "digest value")
                .getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
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

    private static void requireDigest(String value, String name) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw violation(name + " is not a canonical digest");
        }
    }

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_DEBUG_RUNTIME_COMPOSITION: " + message);
    }

    static final class Evidence {
        private final String digest;
        private final String skillInvocationId;
        private final String skillId;
        private final String toolAuditDigest;
        private final int allocatedTokens;
        private final int allocatedBytes;
        private final int workingMemoryBytes;

        private Evidence(
                String digest,
                String skillInvocationId,
                String skillId,
                String toolAuditDigest,
                int allocatedTokens,
                int allocatedBytes,
                int workingMemoryBytes) {
            this.digest = digest;
            this.skillInvocationId = skillInvocationId;
            this.skillId = skillId;
            this.toolAuditDigest = toolAuditDigest;
            this.allocatedTokens = allocatedTokens;
            this.allocatedBytes = allocatedBytes;
            this.workingMemoryBytes = workingMemoryBytes;
        }

        String getDigest() { return digest; }
        String getSkillInvocationId() { return skillInvocationId; }
        String getSkillId() { return skillId; }
        String getToolAuditDigest() { return toolAuditDigest; }
        int getAllocatedTokens() { return allocatedTokens; }
        int getAllocatedBytes() { return allocatedBytes; }
        int getWorkingMemoryBytes() { return workingMemoryBytes; }
        boolean isSkillDispatchEnabled() { return false; }
        boolean isProfileMemoryWritten() { return false; }
        boolean isEpisodicMemoryWritten() { return false; }
        boolean isProductionAuthority() { return false; }
        boolean isHardwareAccessed() { return false; }
    }

    static final class Completion {
        private final boolean replayed;
        private final int cleanedItems;
        private final int cleanedBytes;
        private final int cleanedTokens;

        private Completion(
                boolean replayed,
                int cleanedItems,
                int cleanedBytes,
                int cleanedTokens) {
            this.replayed = replayed;
            this.cleanedItems = cleanedItems;
            this.cleanedBytes = cleanedBytes;
            this.cleanedTokens = cleanedTokens;
        }

        boolean isReplayed() { return replayed; }
        int getCleanedItems() { return cleanedItems; }
        int getCleanedBytes() { return cleanedBytes; }
        int getCleanedTokens() { return cleanedTokens; }
    }

    static final class Snapshot {
        private final int sessionCount;
        private final int completedSessionCount;
        private final int activeSkillCount;
        private final int activeWorkingMemoryItemCount;
        private final int toolAuditCount;

        private Snapshot(
                int sessionCount,
                int completedSessionCount,
                int activeSkillCount,
                int activeWorkingMemoryItemCount,
                int toolAuditCount) {
            this.sessionCount = sessionCount;
            this.completedSessionCount = completedSessionCount;
            this.activeSkillCount = activeSkillCount;
            this.activeWorkingMemoryItemCount = activeWorkingMemoryItemCount;
            this.toolAuditCount = toolAuditCount;
        }

        int getSessionCount() { return sessionCount; }
        int getCompletedSessionCount() { return completedSessionCount; }
        int getActiveSkillCount() { return activeSkillCount; }
        int getActiveWorkingMemoryItemCount() { return activeWorkingMemoryItemCount; }
        int getToolAuditCount() { return toolAuditCount; }
    }

    private static final class Entry {
        private final String ownerFingerprint;
        private final String requestDigest;
        private final BoundedBuiltInSkillRuntime.InvocationSnapshot skillInvocation;
        private final Evidence evidence;
        private boolean completed;

        private Entry(
                String ownerFingerprint,
                String requestDigest,
                BoundedBuiltInSkillRuntime.InvocationSnapshot skillInvocation,
                Evidence evidence) {
            this.ownerFingerprint = ownerFingerprint;
            this.requestDigest = requestDigest;
            this.skillInvocation = skillInvocation;
            this.evidence = evidence;
        }
    }
}
