package com.centralbrain.runtime.tools;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Immutable, bounded Tool workflow rules. It contains no model or execution state. */
public final class ToolRuleSet {
    public static final int MAX_FAMILIES = 128;
    public static final int MAX_CHILD_RULES = 256;
    public static final int MAX_CONDITIONAL_RULES = 256;

    private static final Pattern CONDITION_ID = Pattern.compile(
            "condition[.][a-z][a-z0-9_-]*(?:[.][a-z0-9][a-z0-9_-]*){1,6}");

    public enum RuleType {
        INIT,
        CHILD,
        CONDITIONAL,
        TERMINAL,
        REQUIRED_BEFORE_EXIT,
        REQUIRES_APPROVAL
    }

    public enum ErrorCode {
        FAMILY_LIMIT_EXCEEDED,
        CHILD_RULE_LIMIT_EXCEEDED,
        CONDITIONAL_RULE_LIMIT_EXCEEDED,
        EMPTY_CATALOG,
        EMPTY_INIT_SET,
        DUPLICATE_FAMILY,
        DUPLICATE_RULE,
        UNKNOWN_FAMILY,
        TERMINAL_HAS_CHILD,
        TERMINAL_REQUIRED_BEFORE_EXIT
    }

    public static final class RuleException extends IllegalArgumentException {
        private final ErrorCode errorCode;

        RuleException(ErrorCode errorCode) {
            super("CB_TOOL_RULE_SET: " + errorCode.name());
            this.errorCode = errorCode;
        }

        public ErrorCode getErrorCode() {
            return errorCode;
        }
    }

    public static final class ChildRule {
        private final String parentFamilyId;
        private final String childFamilyId;

        public ChildRule(String parentFamilyId, String childFamilyId) {
            this.parentFamilyId = ToolRegistry.requireFamilyId(parentFamilyId);
            this.childFamilyId = ToolRegistry.requireFamilyId(childFamilyId);
            if (this.parentFamilyId.equals(this.childFamilyId)) {
                throw new RuleException(ErrorCode.DUPLICATE_RULE);
            }
        }

        public String getParentFamilyId() {
            return parentFamilyId;
        }

        public String getChildFamilyId() {
            return childFamilyId;
        }

        private String key() {
            return parentFamilyId + '\u0000' + childFamilyId;
        }
    }

    public static final class ConditionalRule {
        private final String toolFamilyId;
        private final String conditionId;
        private final boolean expectedValue;

        public ConditionalRule(
                String toolFamilyId, String conditionId, boolean expectedValue) {
            this.toolFamilyId = ToolRegistry.requireFamilyId(toolFamilyId);
            this.conditionId = requireConditionId(conditionId);
            this.expectedValue = expectedValue;
        }

        public String getToolFamilyId() {
            return toolFamilyId;
        }

        public String getConditionId() {
            return conditionId;
        }

        public boolean getExpectedValue() {
            return expectedValue;
        }

        private String key() {
            return toolFamilyId + '\u0000' + conditionId;
        }
    }

    private final NavigableSet<String> catalogFamilies;
    private final NavigableSet<String> initFamilies;
    private final NavigableMap<String, NavigableSet<String>> childFamilies;
    private final NavigableMap<String, List<ConditionalRule>> conditionalRules;
    private final NavigableSet<String> terminalFamilies;
    private final NavigableSet<String> requiredBeforeExitFamilies;
    private final NavigableSet<String> requiresApprovalFamilies;
    private final Set<RuleType> definedRuleTypes;
    private final String ruleSetDigest;

    public ToolRuleSet(
            List<String> catalogFamilies,
            List<String> initFamilies,
            List<ChildRule> childRules,
            List<ConditionalRule> conditionalRules,
            List<String> terminalFamilies,
            List<String> requiredBeforeExitFamilies,
            List<String> requiresApprovalFamilies) {
        Objects.requireNonNull(catalogFamilies, "catalogFamilies");
        Objects.requireNonNull(initFamilies, "initFamilies");
        Objects.requireNonNull(childRules, "childRules");
        Objects.requireNonNull(conditionalRules, "conditionalRules");
        Objects.requireNonNull(terminalFamilies, "terminalFamilies");
        Objects.requireNonNull(requiredBeforeExitFamilies, "requiredBeforeExitFamilies");
        Objects.requireNonNull(requiresApprovalFamilies, "requiresApprovalFamilies");
        if (catalogFamilies.size() > MAX_FAMILIES) {
            throw new RuleException(ErrorCode.FAMILY_LIMIT_EXCEEDED);
        }
        if (childRules.size() > MAX_CHILD_RULES) {
            throw new RuleException(ErrorCode.CHILD_RULE_LIMIT_EXCEEDED);
        }
        if (conditionalRules.size() > MAX_CONDITIONAL_RULES) {
            throw new RuleException(ErrorCode.CONDITIONAL_RULE_LIMIT_EXCEEDED);
        }
        this.catalogFamilies = immutableFamilySet(catalogFamilies);
        if (this.catalogFamilies.isEmpty()) {
            throw new RuleException(ErrorCode.EMPTY_CATALOG);
        }
        this.initFamilies = checkedSubset(initFamilies, this.catalogFamilies);
        if (this.initFamilies.isEmpty()) {
            throw new RuleException(ErrorCode.EMPTY_INIT_SET);
        }
        this.terminalFamilies = checkedSubset(terminalFamilies, this.catalogFamilies);
        this.requiredBeforeExitFamilies = checkedSubset(
                requiredBeforeExitFamilies, this.catalogFamilies);
        this.requiresApprovalFamilies = checkedSubset(
                requiresApprovalFamilies, this.catalogFamilies);
        if (!Collections.disjoint(
                this.terminalFamilies, this.requiredBeforeExitFamilies)) {
            throw new RuleException(ErrorCode.TERMINAL_REQUIRED_BEFORE_EXIT);
        }
        this.childFamilies = collectChildren(childRules, this.catalogFamilies);
        for (String terminal : this.terminalFamilies) {
            if (!childrenOf(terminal).isEmpty()) {
                throw new RuleException(ErrorCode.TERMINAL_HAS_CHILD);
            }
        }
        this.conditionalRules = collectConditions(conditionalRules, this.catalogFamilies);
        EnumSet<RuleType> types = EnumSet.of(
                RuleType.INIT,
                RuleType.CHILD,
                RuleType.CONDITIONAL,
                RuleType.TERMINAL,
                RuleType.REQUIRED_BEFORE_EXIT,
                RuleType.REQUIRES_APPROVAL);
        definedRuleTypes = Collections.unmodifiableSet(types);
        ruleSetDigest = digest();
    }

    public Set<RuleType> getDefinedRuleTypes() {
        return definedRuleTypes;
    }

    public List<String> getCatalogFamilies() {
        return List.copyOf(catalogFamilies);
    }

    public List<String> getInitFamilies() {
        return List.copyOf(initFamilies);
    }

    public List<String> getTerminalFamilies() {
        return List.copyOf(terminalFamilies);
    }

    public List<String> getRequiredBeforeExitFamilies() {
        return List.copyOf(requiredBeforeExitFamilies);
    }

    public List<String> getRequiresApprovalFamilies() {
        return List.copyOf(requiresApprovalFamilies);
    }

    public String getRuleSetDigest() {
        return ruleSetDigest;
    }

    boolean containsFamily(String familyId) {
        return catalogFamilies.contains(familyId);
    }

    NavigableSet<String> initialFamilies() {
        return initFamilies;
    }

    NavigableSet<String> childrenOf(String parentFamilyId) {
        NavigableSet<String> children = childFamilies.get(parentFamilyId);
        return children == null ? Collections.emptyNavigableSet() : children;
    }

    List<ConditionalRule> conditionsFor(String familyId) {
        List<ConditionalRule> rules = conditionalRules.get(familyId);
        return rules == null ? List.of() : rules;
    }

    boolean isTerminal(String familyId) {
        return terminalFamilies.contains(familyId);
    }

    boolean requiresApproval(String familyId) {
        return requiresApprovalFamilies.contains(familyId);
    }

    boolean exitRequirementsMet(Set<String> completedFamilies) {
        return completedFamilies.containsAll(requiredBeforeExitFamilies);
    }

    static String requireConditionId(String conditionId) {
        if (conditionId == null || !CONDITION_ID.matcher(conditionId).matches()) {
            throw new IllegalArgumentException(
                    "CB_TOOL_RULE_SET: conditionId is not canonical");
        }
        return conditionId;
    }

    private static NavigableSet<String> immutableFamilySet(List<String> source) {
        TreeSet<String> result = new TreeSet<>();
        for (String familyId : source) {
            if (!result.add(ToolRegistry.requireFamilyId(familyId))) {
                throw new RuleException(ErrorCode.DUPLICATE_FAMILY);
            }
        }
        return Collections.unmodifiableNavigableSet(result);
    }

    private static NavigableSet<String> checkedSubset(
            List<String> source, NavigableSet<String> catalog) {
        NavigableSet<String> result = immutableFamilySet(source);
        if (!catalog.containsAll(result)) {
            throw new RuleException(ErrorCode.UNKNOWN_FAMILY);
        }
        return result;
    }

    private static NavigableMap<String, NavigableSet<String>> collectChildren(
            List<ChildRule> source, NavigableSet<String> catalog) {
        TreeMap<String, TreeSet<String>> collected = new TreeMap<>();
        TreeSet<String> keys = new TreeSet<>();
        for (ChildRule rule : source) {
            ChildRule nonNull = Objects.requireNonNull(rule, "childRule");
            if (!catalog.contains(nonNull.parentFamilyId)
                    || !catalog.contains(nonNull.childFamilyId)) {
                throw new RuleException(ErrorCode.UNKNOWN_FAMILY);
            }
            if (!keys.add(nonNull.key())) {
                throw new RuleException(ErrorCode.DUPLICATE_RULE);
            }
            collected.computeIfAbsent(nonNull.parentFamilyId, ignored -> new TreeSet<>())
                    .add(nonNull.childFamilyId);
        }
        TreeMap<String, NavigableSet<String>> immutable = new TreeMap<>();
        for (Map.Entry<String, TreeSet<String>> entry : collected.entrySet()) {
            immutable.put(
                    entry.getKey(),
                    Collections.unmodifiableNavigableSet(new TreeSet<>(entry.getValue())));
        }
        return Collections.unmodifiableNavigableMap(immutable);
    }

    private static NavigableMap<String, List<ConditionalRule>> collectConditions(
            List<ConditionalRule> source, NavigableSet<String> catalog) {
        TreeMap<String, List<ConditionalRule>> collected = new TreeMap<>();
        TreeSet<String> keys = new TreeSet<>();
        List<ConditionalRule> sorted = new ArrayList<>();
        for (ConditionalRule rule : source) {
            ConditionalRule nonNull = Objects.requireNonNull(rule, "conditionalRule");
            if (!catalog.contains(nonNull.toolFamilyId)) {
                throw new RuleException(ErrorCode.UNKNOWN_FAMILY);
            }
            if (!keys.add(nonNull.key())) {
                throw new RuleException(ErrorCode.DUPLICATE_RULE);
            }
            sorted.add(nonNull);
        }
        sorted.sort((left, right) -> left.key().compareTo(right.key()));
        for (ConditionalRule rule : sorted) {
            collected.computeIfAbsent(rule.toolFamilyId, ignored -> new ArrayList<>())
                    .add(rule);
        }
        TreeMap<String, List<ConditionalRule>> immutable = new TreeMap<>();
        for (Map.Entry<String, List<ConditionalRule>> entry : collected.entrySet()) {
            immutable.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableNavigableMap(immutable);
    }

    private String digest() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, "central-brain-tool-rule-set-v1");
            for (String family : catalogFamilies) {
                update(digest, "catalog:" + family);
            }
            for (String family : initFamilies) {
                update(digest, "init:" + family);
            }
            for (Map.Entry<String, NavigableSet<String>> entry : childFamilies.entrySet()) {
                for (String child : entry.getValue()) {
                    update(digest, "child:" + entry.getKey() + ':' + child);
                }
            }
            for (List<ConditionalRule> rules : conditionalRules.values()) {
                for (ConditionalRule rule : rules) {
                    update(digest, "condition:" + rule.toolFamilyId + ':'
                            + rule.conditionId + ':' + rule.expectedValue);
                }
            }
            for (String family : terminalFamilies) {
                update(digest, "terminal:" + family);
            }
            for (String family : requiredBeforeExitFamilies) {
                update(digest, "required:" + family);
            }
            for (String family : requiresApprovalFamilies) {
                update(digest, "approval:" + family);
            }
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
