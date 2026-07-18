package com.centralbrain.runtime.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Deterministic, metadata-only context budget allocator. */
public final class ContextBudgetManager {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_TOTAL_TOKENS = 262_144;
    public static final int MAX_TOTAL_BYTES = 1_048_576;
    public static final int MAX_ITEMS = 512;
    public static final int MAX_ITEM_TOKENS = 65_536;
    public static final int MAX_ITEM_BYTES = 262_144;

    private static final int MAX_ID_LENGTH = 128;
    private static final Pattern CANONICAL_ID = Pattern.compile(
            "[a-z0-9][a-z0-9._-]{0," + (MAX_ID_LENGTH - 1) + "}");

    public enum Category {
        SYSTEM,
        CONTEXT,
        PROFILE,
        EPISODE,
        HISTORY
    }

    public enum Handling {
        INCLUDE,
        SUMMARIZE_TO_BUDGET,
        TRUNCATE_TO_BUDGET,
        DROP
    }

    public enum AllocationOutcome {
        ALLOCATED,
        REQUIRED_BUDGET_EXCEEDED
    }

    private ContextBudgetManager() {}

    public static ContextBudgetManager createForContractTest() {
        return new ContextBudgetManager();
    }

    public AllocationResult allocate(
            BudgetPolicy policy,
            List<ContextDescriptor> descriptors) {
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(descriptors, "descriptors");
        if (descriptors.size() > policy.maxItems || descriptors.size() > MAX_ITEMS) {
            throw new IllegalArgumentException("descriptor count exceeds policy");
        }

        List<ContextDescriptor> ordered = validateAndOrder(descriptors);
        MutableBudget budget = new MutableBudget(policy);
        List<Decision> decisions = new ArrayList<>(ordered.size());

        for (ContextDescriptor descriptor : ordered) {
            if (!descriptor.required) {
                continue;
            }
            if (!budget.canInclude(descriptor)) {
                return AllocationResult.requiredBudgetExceeded(descriptor.category);
            }
            decisions.add(budget.include(descriptor));
        }

        for (ContextDescriptor descriptor : ordered) {
            if (descriptor.required) {
                continue;
            }
            decisions.add(budget.allocateOptional(descriptor));
        }

        decisions.sort(Decision.ORDER);
        return AllocationResult.allocated(
                decisions,
                budget.usedTokens,
                budget.usedBytes,
                budget.count(Handling.INCLUDE),
                budget.count(Handling.SUMMARIZE_TO_BUDGET),
                budget.count(Handling.TRUNCATE_TO_BUDGET),
                budget.count(Handling.DROP));
    }

    private static List<ContextDescriptor> validateAndOrder(
            List<ContextDescriptor> descriptors) {
        List<ContextDescriptor> ordered = new ArrayList<>(descriptors.size());
        Set<String> ids = new HashSet<>();
        for (ContextDescriptor descriptor : descriptors) {
            ContextDescriptor item = Objects.requireNonNull(descriptor, "descriptor");
            if (!ids.add(item.id)) {
                throw new IllegalArgumentException("duplicate context descriptor id");
            }
            ordered.add(item);
        }
        ordered.sort(ContextDescriptor.ORDER);
        return ordered;
    }

    public boolean isDecisionOnly() {
        return true;
    }

    public boolean isTextPayloadAccepted() {
        return false;
    }

    public boolean isTokenizerWired() {
        return false;
    }

    public boolean isSummarizerWired() {
        return false;
    }

    public boolean isProductionBudgetAuthorityWired() {
        return false;
    }

    public boolean isRuntimeWired() {
        return false;
    }

    public boolean isContentLoggingEnabled() {
        return false;
    }

    public boolean isModelInvoked() {
        return false;
    }

    public boolean isHardwareAccessed() {
        return false;
    }

    public static final class CategoryLimit {
        private final int maxTokens;
        private final int maxBytes;

        public CategoryLimit(int maxTokens, int maxBytes) {
            this.maxTokens = requireRange(
                    maxTokens, 0, MAX_TOTAL_TOKENS, "category maxTokens");
            this.maxBytes = requireRange(
                    maxBytes, 0, MAX_TOTAL_BYTES, "category maxBytes");
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public int getMaxBytes() {
            return maxBytes;
        }
    }

    public static final class BudgetPolicy {
        private final int maxTotalTokens;
        private final int maxTotalBytes;
        private final int maxItems;
        private final Map<Category, CategoryLimit> categoryLimits;

        private BudgetPolicy(
                int maxTotalTokens,
                int maxTotalBytes,
                int maxItems,
                Map<Category, CategoryLimit> categoryLimits) {
            this.maxTotalTokens = requireRange(
                    maxTotalTokens, 1, MAX_TOTAL_TOKENS, "maxTotalTokens");
            this.maxTotalBytes = requireRange(
                    maxTotalBytes, 1, MAX_TOTAL_BYTES, "maxTotalBytes");
            this.maxItems = requireRange(maxItems, 1, MAX_ITEMS, "maxItems");
            this.categoryLimits = Collections.unmodifiableMap(
                    new EnumMap<>(categoryLimits));
        }

        public static BudgetPolicy fixed(
                int maxTotalTokens,
                int maxTotalBytes,
                int maxItems,
                CategoryLimit system,
                CategoryLimit context,
                CategoryLimit profile,
                CategoryLimit episode,
                CategoryLimit history) {
            EnumMap<Category, CategoryLimit> limits = new EnumMap<>(Category.class);
            limits.put(Category.SYSTEM, requireLimit(system, "system"));
            limits.put(Category.CONTEXT, requireLimit(context, "context"));
            limits.put(Category.PROFILE, requireLimit(profile, "profile"));
            limits.put(Category.EPISODE, requireLimit(episode, "episode"));
            limits.put(Category.HISTORY, requireLimit(history, "history"));
            return new BudgetPolicy(maxTotalTokens, maxTotalBytes, maxItems, limits);
        }

        public int getMaxTotalTokens() {
            return maxTotalTokens;
        }

        public int getMaxTotalBytes() {
            return maxTotalBytes;
        }

        public int getMaxItems() {
            return maxItems;
        }

        public CategoryLimit getLimit(Category category) {
            return categoryLimits.get(Objects.requireNonNull(category, "category"));
        }

        public Map<Category, CategoryLimit> getCategoryLimits() {
            return categoryLimits;
        }
    }

    public static final class ContextDescriptor {
        private static final Comparator<ContextDescriptor> ORDER = Comparator
                .comparing(ContextDescriptor::getCategory)
                .thenComparing(Comparator.comparingInt(ContextDescriptor::getPriority).reversed())
                .thenComparing(ContextDescriptor::getId);

        private final Category category;
        private final String id;
        private final int requestedTokens;
        private final int requestedBytes;
        private final boolean required;
        private final boolean summaryAllowed;
        private final int priority;

        private ContextDescriptor(
                Category category,
                String id,
                int requestedTokens,
                int requestedBytes,
                boolean required,
                boolean summaryAllowed,
                int priority) {
            this.category = Objects.requireNonNull(category, "category");
            this.id = requireId(id);
            this.requestedTokens = requireRange(
                    requestedTokens, 1, MAX_ITEM_TOKENS, "requestedTokens");
            this.requestedBytes = requireRange(
                    requestedBytes, 1, MAX_ITEM_BYTES, "requestedBytes");
            this.required = required;
            this.summaryAllowed = summaryAllowed;
            this.priority = requireRange(priority, 0, 100, "priority");
        }

        public static ContextDescriptor fromTrustedMetadata(
                Category category,
                String id,
                int requestedTokens,
                int requestedBytes,
                boolean required,
                boolean summaryAllowed,
                int priority) {
            return new ContextDescriptor(
                    category,
                    id,
                    requestedTokens,
                    requestedBytes,
                    required,
                    summaryAllowed,
                    priority);
        }

        public Category getCategory() {
            return category;
        }

        public String getId() {
            return id;
        }

        public int getRequestedTokens() {
            return requestedTokens;
        }

        public int getRequestedBytes() {
            return requestedBytes;
        }

        public boolean isRequired() {
            return required;
        }

        public boolean isSummaryAllowed() {
            return summaryAllowed;
        }

        public int getPriority() {
            return priority;
        }
    }

    public static final class Decision {
        private static final Comparator<Decision> ORDER = Comparator
                .comparing(Decision::getCategory)
                .thenComparing(Comparator.comparingInt(Decision::getPriority).reversed())
                .thenComparing(Decision::getId);

        private final Category category;
        private final String id;
        private final Handling handling;
        private final int requestedTokens;
        private final int requestedBytes;
        private final int targetTokens;
        private final int targetBytes;
        private final boolean required;
        private final int priority;

        private Decision(
                ContextDescriptor descriptor,
                Handling handling,
                int targetTokens,
                int targetBytes) {
            this.category = descriptor.category;
            this.id = descriptor.id;
            this.handling = Objects.requireNonNull(handling, "handling");
            this.requestedTokens = descriptor.requestedTokens;
            this.requestedBytes = descriptor.requestedBytes;
            this.targetTokens = targetTokens;
            this.targetBytes = targetBytes;
            this.required = descriptor.required;
            this.priority = descriptor.priority;
        }

        public Category getCategory() {
            return category;
        }

        public String getId() {
            return id;
        }

        public Handling getHandling() {
            return handling;
        }

        public int getRequestedTokens() {
            return requestedTokens;
        }

        public int getRequestedBytes() {
            return requestedBytes;
        }

        public int getTargetTokens() {
            return targetTokens;
        }

        public int getTargetBytes() {
            return targetBytes;
        }

        public boolean isRequired() {
            return required;
        }

        public int getPriority() {
            return priority;
        }
    }

    public static final class AllocationResult {
        private final AllocationOutcome outcome;
        private final List<Decision> decisions;
        private final int allocatedTokens;
        private final int allocatedBytes;
        private final int includeCount;
        private final int summarizeCount;
        private final int truncateCount;
        private final int dropCount;
        private final Category failedRequiredCategory;

        private AllocationResult(
                AllocationOutcome outcome,
                List<Decision> decisions,
                int allocatedTokens,
                int allocatedBytes,
                int includeCount,
                int summarizeCount,
                int truncateCount,
                int dropCount,
                Category failedRequiredCategory) {
            this.outcome = outcome;
            this.decisions = Collections.unmodifiableList(new ArrayList<>(decisions));
            this.allocatedTokens = allocatedTokens;
            this.allocatedBytes = allocatedBytes;
            this.includeCount = includeCount;
            this.summarizeCount = summarizeCount;
            this.truncateCount = truncateCount;
            this.dropCount = dropCount;
            this.failedRequiredCategory = failedRequiredCategory;
        }

        private static AllocationResult allocated(
                List<Decision> decisions,
                int allocatedTokens,
                int allocatedBytes,
                int includeCount,
                int summarizeCount,
                int truncateCount,
                int dropCount) {
            return new AllocationResult(
                    AllocationOutcome.ALLOCATED,
                    decisions,
                    allocatedTokens,
                    allocatedBytes,
                    includeCount,
                    summarizeCount,
                    truncateCount,
                    dropCount,
                    null);
        }

        private static AllocationResult requiredBudgetExceeded(Category category) {
            return new AllocationResult(
                    AllocationOutcome.REQUIRED_BUDGET_EXCEEDED,
                    Collections.emptyList(),
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    category);
        }

        public AllocationOutcome getOutcome() {
            return outcome;
        }

        public List<Decision> getDecisions() {
            return decisions;
        }

        public int getAllocatedTokens() {
            return allocatedTokens;
        }

        public int getAllocatedBytes() {
            return allocatedBytes;
        }

        public int getIncludeCount() {
            return includeCount;
        }

        public int getSummarizeCount() {
            return summarizeCount;
        }

        public int getTruncateCount() {
            return truncateCount;
        }

        public int getDropCount() {
            return dropCount;
        }

        public Category getFailedRequiredCategory() {
            return failedRequiredCategory;
        }

        public boolean isSummaryGenerated() {
            return false;
        }

        public boolean isContentTruncated() {
            return false;
        }
    }

    private static final class MutableBudget {
        private final BudgetPolicy policy;
        private final EnumMap<Category, Integer> usedCategoryTokens =
                new EnumMap<>(Category.class);
        private final EnumMap<Category, Integer> usedCategoryBytes =
                new EnumMap<>(Category.class);
        private final EnumMap<Handling, Integer> counts = new EnumMap<>(Handling.class);
        private int usedTokens;
        private int usedBytes;

        private MutableBudget(BudgetPolicy policy) {
            this.policy = policy;
            for (Category category : Category.values()) {
                usedCategoryTokens.put(category, 0);
                usedCategoryBytes.put(category, 0);
            }
            for (Handling handling : Handling.values()) {
                counts.put(handling, 0);
            }
        }

        private boolean canInclude(ContextDescriptor descriptor) {
            return descriptor.requestedTokens <= availableTokens(descriptor.category)
                    && descriptor.requestedBytes <= availableBytes(descriptor.category);
        }

        private Decision include(ContextDescriptor descriptor) {
            consume(descriptor.category, descriptor.requestedTokens, descriptor.requestedBytes);
            increment(Handling.INCLUDE);
            return new Decision(
                    descriptor,
                    Handling.INCLUDE,
                    descriptor.requestedTokens,
                    descriptor.requestedBytes);
        }

        private Decision allocateOptional(ContextDescriptor descriptor) {
            if (canInclude(descriptor)) {
                return include(descriptor);
            }
            int targetTokens = Math.min(
                    descriptor.requestedTokens,
                    availableTokens(descriptor.category));
            int targetBytes = Math.min(
                    descriptor.requestedBytes,
                    availableBytes(descriptor.category));
            if (targetTokens < 1 || targetBytes < 1) {
                increment(Handling.DROP);
                return new Decision(descriptor, Handling.DROP, 0, 0);
            }
            Handling handling = descriptor.summaryAllowed
                    ? Handling.SUMMARIZE_TO_BUDGET
                    : Handling.TRUNCATE_TO_BUDGET;
            consume(descriptor.category, targetTokens, targetBytes);
            increment(handling);
            return new Decision(descriptor, handling, targetTokens, targetBytes);
        }

        private int availableTokens(Category category) {
            int total = policy.maxTotalTokens - usedTokens;
            int categoryBudget = policy.getLimit(category).maxTokens
                    - usedCategoryTokens.get(category);
            return Math.max(0, Math.min(total, categoryBudget));
        }

        private int availableBytes(Category category) {
            int total = policy.maxTotalBytes - usedBytes;
            int categoryBudget = policy.getLimit(category).maxBytes
                    - usedCategoryBytes.get(category);
            return Math.max(0, Math.min(total, categoryBudget));
        }

        private void consume(Category category, int tokens, int bytes) {
            usedTokens += tokens;
            usedBytes += bytes;
            usedCategoryTokens.put(category, usedCategoryTokens.get(category) + tokens);
            usedCategoryBytes.put(category, usedCategoryBytes.get(category) + bytes);
        }

        private void increment(Handling handling) {
            counts.put(handling, counts.get(handling) + 1);
        }

        private int count(Handling handling) {
            return counts.get(handling);
        }
    }

    private static String requireId(String value) {
        Objects.requireNonNull(value, "id");
        if (!CANONICAL_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("id must be canonical");
        }
        return value;
    }

    private static int requireRange(int value, int min, int max, String name) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " is out of range");
        }
        return value;
    }

    private static CategoryLimit requireLimit(CategoryLimit limit, String name) {
        if (limit == null) {
            throw new IllegalArgumentException(name + " category limit is required");
        }
        return limit;
    }
}
