package com.centralbrain.runtime.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public final class ContextBudgetManagerTest {
    @Test
    public void allocatesAllCategoriesInStablePriorityOrder() {
        ContextBudgetManager manager = ContextBudgetManager.createForContractTest();
        List<ContextBudgetManager.ContextDescriptor> input = Arrays.asList(
                item(ContextBudgetManager.Category.HISTORY, "history.a", 4, 8, false, true, 10),
                item(ContextBudgetManager.Category.SYSTEM, "system.a", 3, 6, true, false, 100),
                item(ContextBudgetManager.Category.PROFILE, "profile.a", 2, 4, false, false, 30),
                item(ContextBudgetManager.Category.CONTEXT, "context.low", 2, 4, false, false, 20),
                item(ContextBudgetManager.Category.CONTEXT, "context.high", 2, 4, false, false, 80),
                item(ContextBudgetManager.Category.EPISODE, "episode.a", 2, 4, false, true, 40));

        ContextBudgetManager.AllocationResult first = manager.allocate(policy(32, 64, 8), input);
        List<ContextBudgetManager.ContextDescriptor> reversed = new ArrayList<>(input);
        Collections.reverse(reversed);
        ContextBudgetManager.AllocationResult second = manager.allocate(
                policy(32, 64, 8), reversed);

        assertEquals(ContextBudgetManager.AllocationOutcome.ALLOCATED, first.getOutcome());
        assertEquals(6, first.getIncludeCount());
        assertEquals(15, first.getAllocatedTokens());
        assertEquals(30, first.getAllocatedBytes());
        assertEquals(ids(first), ids(second));
        assertEquals(Arrays.asList(
                "system.a", "context.high", "context.low", "profile.a", "episode.a", "history.a"),
                ids(first));
    }

    @Test
    public void requiredBudgetFailureReturnsNoPartialPlan() {
        ContextBudgetManager manager = ContextBudgetManager.createForContractTest();
        ContextBudgetManager.AllocationResult result = manager.allocate(
                policy(6, 12, 4),
                Arrays.asList(
                        item(ContextBudgetManager.Category.SYSTEM, "system.a", 4, 8, true, false, 100),
                        item(ContextBudgetManager.Category.CONTEXT, "context.a", 4, 8, true, false, 100)));

        assertEquals(ContextBudgetManager.AllocationOutcome.REQUIRED_BUDGET_EXCEEDED,
                result.getOutcome());
        assertEquals(ContextBudgetManager.Category.CONTEXT,
                result.getFailedRequiredCategory());
        assertTrue(result.getDecisions().isEmpty());
        assertEquals(0, result.getAllocatedTokens());
        assertEquals(0, result.getAllocatedBytes());
    }

    @Test
    public void overBudgetItemsGetDeterministicDirectivesOnly() {
        ContextBudgetManager manager = ContextBudgetManager.createForContractTest();
        ContextBudgetManager.BudgetPolicy constrained =
                ContextBudgetManager.BudgetPolicy.fixed(
                        10,
                        20,
                        8,
                        limit(10, 20),
                        limit(2, 4),
                        limit(10, 20),
                        limit(10, 20),
                        limit(10, 20));
        ContextBudgetManager.AllocationResult result = manager.allocate(
                constrained,
                Arrays.asList(
                        item(ContextBudgetManager.Category.SYSTEM, "system.a", 4, 8, true, false, 100),
                        item(ContextBudgetManager.Category.CONTEXT, "context.a", 4, 8, false, false, 90),
                        item(ContextBudgetManager.Category.PROFILE, "profile.a", 5, 10, false, true, 80),
                        item(ContextBudgetManager.Category.HISTORY, "history.a", 4, 8, false, true, 70)));

        assertEquals(ContextBudgetManager.Handling.INCLUDE, decision(result, "system.a").getHandling());
        assertEquals(ContextBudgetManager.Handling.TRUNCATE_TO_BUDGET,
                decision(result, "context.a").getHandling());
        assertEquals(2, decision(result, "context.a").getTargetTokens());
        assertEquals(4, decision(result, "context.a").getTargetBytes());
        assertEquals(ContextBudgetManager.Handling.SUMMARIZE_TO_BUDGET,
                decision(result, "profile.a").getHandling());
        assertEquals(ContextBudgetManager.Handling.DROP,
                decision(result, "history.a").getHandling());
        assertEquals(1, result.getSummarizeCount());
        assertEquals(1, result.getTruncateCount());
        assertEquals(1, result.getDropCount());
        assertFalse(result.isSummaryGenerated());
        assertFalse(result.isContentTruncated());
    }

    @Test
    public void categoryEnvelopesDoNotBorrowOrSilentlyEvict() {
        ContextBudgetManager manager = ContextBudgetManager.createForContractTest();
        ContextBudgetManager.BudgetPolicy policy = ContextBudgetManager.BudgetPolicy.fixed(
                100,
                200,
                8,
                limit(20, 40),
                limit(3, 6),
                limit(20, 40),
                limit(20, 40),
                limit(20, 40));
        ContextBudgetManager.AllocationResult result = manager.allocate(
                policy,
                Arrays.asList(
                        item(ContextBudgetManager.Category.CONTEXT, "context.high", 2, 4, false, false, 90),
                        item(ContextBudgetManager.Category.CONTEXT, "context.low", 2, 4, false, false, 10),
                        item(ContextBudgetManager.Category.PROFILE, "profile.a", 10, 20, false, false, 10)));

        assertEquals(ContextBudgetManager.Handling.INCLUDE,
                decision(result, "context.high").getHandling());
        assertEquals(ContextBudgetManager.Handling.TRUNCATE_TO_BUDGET,
                decision(result, "context.low").getHandling());
        assertEquals(1, decision(result, "context.low").getTargetTokens());
        assertEquals(2, decision(result, "context.low").getTargetBytes());
        assertEquals(ContextBudgetManager.Handling.INCLUDE,
                decision(result, "profile.a").getHandling());
        assertEquals(13, result.getAllocatedTokens());
    }

    @Test
    public void malformedAndDuplicateMetadataAreRejected() {
        ContextBudgetManager manager = ContextBudgetManager.createForContractTest();
        ContextBudgetManager.ContextDescriptor duplicate = item(
                ContextBudgetManager.Category.SYSTEM, "same.id", 1, 1, false, false, 1);
        expectIllegal(() -> manager.allocate(policy(8, 16, 2), Arrays.asList(duplicate, duplicate)));
        expectIllegal(() -> item(
                ContextBudgetManager.Category.SYSTEM, "Not Canonical", 1, 1, false, false, 1));
        expectIllegal(() -> item(
                ContextBudgetManager.Category.SYSTEM, "system.a", 0, 1, false, false, 1));
        expectIllegal(() -> new ContextBudgetManager.CategoryLimit(
                ContextBudgetManager.MAX_TOTAL_TOKENS + 1, 1));
        expectIllegal(() -> ContextBudgetManager.BudgetPolicy.fixed(
                1, 1, 1, limit(1, 1), null, limit(1, 1), limit(1, 1), limit(1, 1)));
    }

    @Test
    public void outputIsImmutableAndProductionBoundariesRemainClosed() {
        ContextBudgetManager manager = ContextBudgetManager.createForContractTest();
        ContextBudgetManager.AllocationResult result = manager.allocate(
                policy(8, 16, 2),
                Collections.singletonList(item(
                        ContextBudgetManager.Category.SYSTEM,
                        "system.a",
                        2,
                        4,
                        true,
                        false,
                        100)));
        try {
            result.getDecisions().clear();
            fail("budget decisions must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
        assertNull(result.getFailedRequiredCategory());
        assertTrue(manager.isDecisionOnly());
        assertFalse(manager.isTextPayloadAccepted());
        assertFalse(manager.isTokenizerWired());
        assertFalse(manager.isSummarizerWired());
        assertFalse(manager.isProductionBudgetAuthorityWired());
        assertFalse(manager.isRuntimeWired());
        assertFalse(manager.isContentLoggingEnabled());
        assertFalse(manager.isModelInvoked());
        assertFalse(manager.isHardwareAccessed());
    }

    private static ContextBudgetManager.BudgetPolicy policy(
            int tokens, int bytes, int maxItems) {
        ContextBudgetManager.CategoryLimit limit = limit(tokens, bytes);
        return ContextBudgetManager.BudgetPolicy.fixed(
                tokens, bytes, maxItems, limit, limit, limit, limit, limit);
    }

    private static ContextBudgetManager.CategoryLimit limit(int tokens, int bytes) {
        return new ContextBudgetManager.CategoryLimit(tokens, bytes);
    }

    private static ContextBudgetManager.ContextDescriptor item(
            ContextBudgetManager.Category category,
            String id,
            int tokens,
            int bytes,
            boolean required,
            boolean summaryAllowed,
            int priority) {
        return ContextBudgetManager.ContextDescriptor.fromTrustedMetadata(
                category, id, tokens, bytes, required, summaryAllowed, priority);
    }

    private static List<String> ids(ContextBudgetManager.AllocationResult result) {
        List<String> ids = new ArrayList<>();
        for (ContextBudgetManager.Decision decision : result.getDecisions()) {
            ids.add(decision.getId());
        }
        return ids;
    }

    private static ContextBudgetManager.Decision decision(
            ContextBudgetManager.AllocationResult result,
            String id) {
        for (ContextBudgetManager.Decision decision : result.getDecisions()) {
            if (decision.getId().equals(id)) {
                return decision;
            }
        }
        throw new AssertionError("missing decision " + id);
    }

    private static void expectIllegal(Runnable runnable) {
        try {
            runnable.run();
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}
