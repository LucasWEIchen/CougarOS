package com.centralbrain.runtime.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

/** Deterministic fail-closed rule intersection. It never grants approval or invokes a Tool. */
public final class ToolRuleSolver {
    public static final int MAX_MODEL_SELECTIONS = 128;
    public static final int MAX_COMPLETED_FAMILIES = 128;
    public static final int MAX_CONDITIONS = 128;
    public static final int MAX_RESOLUTIONS = 128;

    public enum ConditionState {
        TRUE,
        FALSE,
        UNKNOWN
    }

    public enum FailureCode {
        NONE,
        CURRENT_TOOL_NOT_IN_RULE_SET,
        TERMINAL_REACHED,
        NO_RULE_CANDIDATE,
        CONDITION_UNSATISFIED,
        REQUIRED_BEFORE_EXIT_INCOMPLETE,
        MODEL_INTERSECTION_EMPTY,
        NO_USABLE_TOOL
    }

    public static final class ConditionObservation {
        private final String conditionId;
        private final ConditionState state;

        public ConditionObservation(String conditionId, ConditionState state) {
            this.conditionId = ToolRuleSet.requireConditionId(conditionId);
            this.state = Objects.requireNonNull(state, "state");
        }

        public String getConditionId() {
            return conditionId;
        }

        public ConditionState getState() {
            return state;
        }
    }

    public static final class ConditionSnapshot {
        private final NavigableMap<String, ConditionState> observations;

        public ConditionSnapshot(List<ConditionObservation> source) {
            Objects.requireNonNull(source, "source");
            if (source.size() > MAX_CONDITIONS) {
                throw new IllegalArgumentException(
                        "CB_TOOL_RULE_SOLVER: condition limit exceeded");
            }
            TreeMap<String, ConditionState> collected = new TreeMap<>();
            for (ConditionObservation observation : source) {
                ConditionObservation nonNull = Objects.requireNonNull(
                        observation, "conditionObservation");
                if (collected.putIfAbsent(nonNull.conditionId, nonNull.state) != null) {
                    throw new IllegalArgumentException(
                            "CB_TOOL_RULE_SOLVER: duplicate condition");
                }
            }
            observations = Collections.unmodifiableNavigableMap(collected);
        }

        public static ConditionSnapshot empty() {
            return new ConditionSnapshot(List.of());
        }

        public ConditionState getState(String conditionId) {
            ConditionState state = observations.get(
                    ToolRuleSet.requireConditionId(conditionId));
            return state == null ? ConditionState.UNKNOWN : state;
        }

        public int size() {
            return observations.size();
        }
    }

    public static final class Request {
        private final String currentFamilyId;
        private final NavigableSet<String> modelSelectedFamilies;
        private final NavigableSet<String> completedFamilies;
        private final ConditionSnapshot conditions;

        public Request(
                String currentFamilyId,
                List<String> modelSelectedFamilies,
                List<String> completedFamilies,
                ConditionSnapshot conditions) {
            this.currentFamilyId = currentFamilyId == null
                    ? null : ToolRegistry.requireFamilyId(currentFamilyId);
            this.modelSelectedFamilies = boundedFamilySet(
                    modelSelectedFamilies, MAX_MODEL_SELECTIONS, "model selection");
            this.completedFamilies = boundedFamilySet(
                    completedFamilies, MAX_COMPLETED_FAMILIES, "completed family");
            this.conditions = Objects.requireNonNull(conditions, "conditions");
        }

        public String getCurrentFamilyId() {
            return currentFamilyId;
        }

        public List<String> getModelSelectedFamilies() {
            return List.copyOf(modelSelectedFamilies);
        }

        public List<String> getCompletedFamilies() {
            return List.copyOf(completedFamilies);
        }

        public ConditionSnapshot getConditions() {
            return conditions;
        }
    }

    public static final class Selection {
        private final ToolManifest manifest;
        private final boolean approvalRequired;

        private Selection(ToolManifest manifest, boolean approvalRequired) {
            this.manifest = manifest;
            this.approvalRequired = approvalRequired;
        }

        public ToolManifest getManifest() {
            return manifest;
        }

        public boolean isApprovalRequired() {
            return approvalRequired;
        }

        /** P5-W03 has no trusted approval authority. */
        public boolean isApprovalGranted() {
            return false;
        }

        /** A solved selection still requires P5-W04 and Runtime Governance admission. */
        public boolean isExecutionEnabled() {
            return false;
        }
    }

    public static final class Result {
        private final FailureCode failureCode;
        private final List<Selection> selections;

        private Result(FailureCode failureCode, List<Selection> selections) {
            this.failureCode = failureCode;
            this.selections = List.copyOf(selections);
        }

        public FailureCode getFailureCode() {
            return failureCode;
        }

        public List<Selection> getSelections() {
            return selections;
        }

        public boolean isAllowed() {
            return failureCode == FailureCode.NONE && !selections.isEmpty();
        }

        public boolean isExecutionEnabled() {
            return false;
        }
    }

    private final ToolRuleSet rules;

    public ToolRuleSolver(ToolRuleSet rules) {
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    public Result solve(Request request, List<ToolResolver.Resolution> resolutions) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(resolutions, "resolutions");
        if (resolutions.size() > MAX_RESOLUTIONS) {
            throw new IllegalArgumentException(
                    "CB_TOOL_RULE_SOLVER: resolution limit exceeded");
        }
        if (request.currentFamilyId != null
                && !rules.containsFamily(request.currentFamilyId)) {
            return denied(FailureCode.CURRENT_TOOL_NOT_IN_RULE_SET);
        }
        if (request.currentFamilyId != null
                && rules.isTerminal(request.currentFamilyId)) {
            return denied(FailureCode.TERMINAL_REACHED);
        }

        TreeSet<String> allowed = new TreeSet<>(request.currentFamilyId == null
                ? rules.initialFamilies()
                : rules.childrenOf(request.currentFamilyId));
        if (allowed.isEmpty()) {
            return denied(FailureCode.NO_RULE_CANDIDATE);
        }

        allowed.removeIf(familyId -> !conditionsSatisfied(
                rules.conditionsFor(familyId), request.conditions));
        if (allowed.isEmpty()) {
            return denied(FailureCode.CONDITION_UNSATISFIED);
        }

        boolean exitRequirementsMet = rules.exitRequirementsMet(request.completedFamilies);
        allowed.removeIf(familyId -> rules.isTerminal(familyId) && !exitRequirementsMet);
        if (allowed.isEmpty()) {
            return denied(FailureCode.REQUIRED_BEFORE_EXIT_INCOMPLETE);
        }

        allowed.retainAll(request.modelSelectedFamilies);
        if (allowed.isEmpty()) {
            return denied(FailureCode.MODEL_INTERSECTION_EMPTY);
        }

        NavigableMap<String, ToolManifest> usable = usableManifests(resolutions);
        allowed.retainAll(usable.navigableKeySet());
        if (allowed.isEmpty()) {
            return denied(FailureCode.NO_USABLE_TOOL);
        }

        List<Selection> selections = new ArrayList<>();
        for (String familyId : allowed) {
            selections.add(new Selection(
                    usable.get(familyId), rules.requiresApproval(familyId)));
        }
        return new Result(FailureCode.NONE, selections);
    }

    private static boolean conditionsSatisfied(
            List<ToolRuleSet.ConditionalRule> conditions,
            ConditionSnapshot snapshot) {
        for (ToolRuleSet.ConditionalRule condition : conditions) {
            ConditionState actual = snapshot.getState(condition.getConditionId());
            if (actual == ConditionState.UNKNOWN
                    || (actual == ConditionState.TRUE) != condition.getExpectedValue()) {
                return false;
            }
        }
        return true;
    }

    private static NavigableMap<String, ToolManifest> usableManifests(
            List<ToolResolver.Resolution> resolutions) {
        TreeMap<String, ToolManifest> result = new TreeMap<>();
        for (ToolResolver.Resolution resolution : resolutions) {
            ToolResolver.Resolution nonNull = Objects.requireNonNull(
                    resolution, "resolution");
            if (nonNull.getResolutionState() != ToolResolver.ResolutionState.RESOLVED
                    || nonNull.getUsabilityState() != ToolResolver.UsabilityState.USABLE) {
                continue;
            }
            ToolManifest manifest = nonNull.getManifest();
            if (result.putIfAbsent(manifest.getFamilyId(), manifest) != null) {
                throw new IllegalArgumentException(
                        "CB_TOOL_RULE_SOLVER: duplicate usable family");
            }
        }
        return Collections.unmodifiableNavigableMap(result);
    }

    private static NavigableSet<String> boundedFamilySet(
            List<String> source, int maximum, String label) {
        Objects.requireNonNull(source, label);
        if (source.size() > maximum) {
            throw new IllegalArgumentException(
                    "CB_TOOL_RULE_SOLVER: " + label + " limit exceeded");
        }
        TreeSet<String> result = new TreeSet<>();
        for (String familyId : source) {
            if (!result.add(ToolRegistry.requireFamilyId(familyId))) {
                throw new IllegalArgumentException(
                        "CB_TOOL_RULE_SOLVER: duplicate " + label);
            }
        }
        return Collections.unmodifiableNavigableSet(result);
    }

    private static Result denied(FailureCode failureCode) {
        return new Result(failureCode, List.of());
    }
}
