package com.centralbrain.runtime.scenario;

import com.centralbrain.runtime.vehicle.capability.VehicleCapability.CapabilityId;
import com.centralbrain.runtime.vehicle.schema.VehicleSignalPath;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable, digest-bound resolver decision. It is not an executable plan. */
public final class ScenarioResolution {
    public static final int SCHEMA_VERSION = 1;

    public enum Decision {
        ACCEPTED,
        DEGRADED,
        REJECTED
    }

    public enum MatchType {
        EXPLICIT_ID,
        DETERMINISTIC_TEXT,
        NONE
    }

    public enum ReasonCode {
        SELECTED_EXPLICIT_ID,
        SELECTED_DETERMINISTIC_TEXT,
        UNKNOWN_INTENT,
        AMBIGUOUS_INTENT,
        SCENARIO_NOT_REGISTERED,
        SOURCE_UNSUPPORTED,
        ZONE_UNSUPPORTED,
        CONTEXT_ZONE_MISMATCH,
        CONTEXT_POLICY_MISMATCH,
        CONTEXT_RESTRICTED,
        CONTEXT_NOT_PRODUCTION_TRUSTED,
        REQUIRED_CONTEXT_UNAVAILABLE,
        REQUIRED_CAPABILITY_UNAVAILABLE,
        REQUIRED_CAPABILITY_POLICY_BLOCKED,
        OPTIONAL_CAPABILITY_UNAVAILABLE,
        OPTIONAL_CAPABILITY_POLICY_BLOCKED
    }

    private final Decision decision;
    private final MatchType matchType;
    private final String scenarioId;
    private final String matchedRuleId;
    private final String requestDigest;
    private final String contextDigest;
    private final String capabilityDigest;
    private final ScenarioManifest manifest;
    private final List<ReasonCode> reasons;
    private final List<String> candidateScenarioIds;
    private final List<VehicleSignalPath> unavailableRequiredContext;
    private final List<CapabilityId> unavailableRequiredCapabilities;
    private final List<CapabilityId> unavailableOptionalCapabilities;
    private final String resolutionDigest;

    ScenarioResolution(
            Decision decision,
            MatchType matchType,
            String scenarioId,
            String matchedRuleId,
            String requestDigest,
            String contextDigest,
            String capabilityDigest,
            ScenarioManifest manifest,
            Set<ReasonCode> reasons,
            Set<String> candidateScenarioIds,
            Set<VehicleSignalPath> unavailableRequiredContext,
            Set<CapabilityId> unavailableRequiredCapabilities,
            Set<CapabilityId> unavailableOptionalCapabilities) {
        this.decision = Objects.requireNonNull(decision, "decision");
        this.matchType = Objects.requireNonNull(matchType, "matchType");
        this.scenarioId = bounded(scenarioId, 96, "scenarioId");
        this.matchedRuleId = bounded(matchedRuleId, 64, "matchedRuleId");
        this.requestDigest = requireDigest(requestDigest, "requestDigest");
        this.contextDigest = requireDigest(contextDigest, "contextDigest");
        this.capabilityDigest = requireDigest(capabilityDigest, "capabilityDigest");
        this.manifest = manifest;
        if (decision == Decision.REJECTED && manifest != null
                || decision != Decision.REJECTED && manifest == null) {
            throw new IllegalArgumentException(
                    "CB_SCENARIO_RESOLVE: decision and selected manifest do not match");
        }
        if (manifest != null && !manifest.getScenarioId().equals(this.scenarioId)) {
            throw new IllegalArgumentException(
                    "CB_SCENARIO_RESOLVE: selected manifest ID does not match");
        }
        this.reasons = immutableEnums(reasons, ReasonCode.class, "reasons");
        this.candidateScenarioIds = immutableStrings(candidateScenarioIds);
        this.unavailableRequiredContext = immutableEnums(
                unavailableRequiredContext, VehicleSignalPath.class, "requiredContext");
        this.unavailableRequiredCapabilities = immutableEnums(
                unavailableRequiredCapabilities, CapabilityId.class, "requiredCapabilities");
        this.unavailableOptionalCapabilities = immutableEnums(
                unavailableOptionalCapabilities, CapabilityId.class, "optionalCapabilities");
        this.resolutionDigest = calculateDigest();
    }

    public Decision getDecision() {
        return decision;
    }

    public MatchType getMatchType() {
        return matchType;
    }

    public String getScenarioId() {
        return scenarioId;
    }

    public String getMatchedRuleId() {
        return matchedRuleId;
    }

    public String getRequestDigest() {
        return requestDigest;
    }

    public String getContextDigest() {
        return contextDigest;
    }

    public String getCapabilityDigest() {
        return capabilityDigest;
    }

    public Optional<ScenarioManifest> getSelectedManifest() {
        return Optional.ofNullable(manifest);
    }

    public List<ReasonCode> getReasons() {
        return reasons;
    }

    public List<String> getCandidateScenarioIds() {
        return candidateScenarioIds;
    }

    public List<VehicleSignalPath> getUnavailableRequiredContext() {
        return unavailableRequiredContext;
    }

    public List<CapabilityId> getUnavailableRequiredCapabilities() {
        return unavailableRequiredCapabilities;
    }

    public List<CapabilityId> getUnavailableOptionalCapabilities() {
        return unavailableOptionalCapabilities;
    }

    public String getResolutionDigest() {
        return resolutionDigest;
    }

    public boolean isExecutable() {
        return false;
    }

    public boolean isProductionTrusted() {
        return false;
    }

    private String calculateDigest() {
        MessageDigest digest = ScenarioResolver.sha256();
        ScenarioResolver.update(digest, "central-brain-scenario-resolution-v1");
        ScenarioResolver.update(digest, decision.name());
        ScenarioResolver.update(digest, matchType.name());
        ScenarioResolver.update(digest, scenarioId);
        ScenarioResolver.update(digest, matchedRuleId);
        ScenarioResolver.update(digest, requestDigest);
        ScenarioResolver.update(digest, contextDigest);
        ScenarioResolver.update(digest, capabilityDigest);
        ScenarioResolver.update(digest, manifest == null ? "" : manifest.getArtifactDigest());
        updateList(digest, reasons);
        updateList(digest, candidateScenarioIds);
        updateList(digest, unavailableRequiredContext);
        updateList(digest, unavailableRequiredCapabilities);
        updateList(digest, unavailableOptionalCapabilities);
        return ScenarioResolver.toHex(digest.digest());
    }

    private static void updateList(MessageDigest digest, List<?> values) {
        ScenarioResolver.update(digest, Integer.toString(values.size()));
        for (Object value : values) {
            ScenarioResolver.update(digest, value.toString());
        }
    }

    private static <E extends Enum<E>> List<E> immutableEnums(
            Set<E> values, Class<E> type, String field) {
        Objects.requireNonNull(values, field);
        for (E value : values) {
            if (value == null) {
                throw new IllegalArgumentException(
                        "CB_SCENARIO_RESOLVE: " + field + " contains null");
            }
        }
        EnumSet<E> sorted = values.isEmpty()
                ? EnumSet.noneOf(type) : EnumSet.copyOf(values);
        if (field.equals("reasons") && sorted.isEmpty()) {
            throw new IllegalArgumentException(
                    "CB_SCENARIO_RESOLVE: reasons must not be empty");
        }
        return Collections.unmodifiableList(new ArrayList<>(sorted));
    }

    private static List<String> immutableStrings(Set<String> values) {
        Objects.requireNonNull(values, "candidateScenarioIds");
        Set<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            unique.add(bounded(value, 96, "candidateScenarioId"));
        }
        List<String> result = new ArrayList<>(unique);
        result.sort(Comparator.naturalOrder());
        return Collections.unmodifiableList(result);
    }

    private static String bounded(String value, int maximum, String field) {
        if (value == null || value.length() > maximum) {
            throw new IllegalArgumentException(
                    "CB_SCENARIO_RESOLVE: " + field + " is invalid");
        }
        return value;
    }

    private static String requireDigest(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "CB_SCENARIO_RESOLVE: " + field + " is invalid");
        }
        return value;
    }
}
