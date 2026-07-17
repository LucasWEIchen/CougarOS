package com.centralbrain.runtime.scenario;

import com.centralbrain.runtime.context.ContextSnapshot;
import com.centralbrain.runtime.context.ContextSnapshot.ContextField;
import com.centralbrain.runtime.context.ContextSnapshot.DrivingState;
import com.centralbrain.runtime.context.ContextSnapshot.SeatZone;
import com.centralbrain.runtime.scenario.ScenarioManifest.DrivingPolicy;
import com.centralbrain.runtime.scenario.ScenarioManifest.NodeTemplate;
import com.centralbrain.runtime.scenario.ScenarioManifest.Source;
import com.centralbrain.runtime.scenario.ScenarioManifest.Zone;
import com.centralbrain.runtime.scenario.ScenarioResolution.Decision;
import com.centralbrain.runtime.scenario.ScenarioResolution.MatchType;
import com.centralbrain.runtime.scenario.ScenarioResolution.ReasonCode;
import com.centralbrain.runtime.vehicle.capability.CapabilityCatalog;
import com.centralbrain.runtime.vehicle.capability.VehicleCapability;
import com.centralbrain.runtime.vehicle.capability.VehicleCapability.CapabilityId;
import com.centralbrain.runtime.vehicle.schema.VehicleSignalPath;

import java.text.Normalizer;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Fixed-rule resolver for the three build-owned Stage 2 scenarios. */
public final class DeterministicScenarioResolver implements ScenarioResolver {
    private static final Map<String, Rule> RULES = rules();

    @Override
    public ScenarioResolution resolve(
            Request request,
            ScenarioCatalog catalog,
            ContextSnapshot context,
            CapabilitySnapshot capabilities) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(capabilities, "capabilities");

        Match match = match(request);
        if (match.reason != null) {
            return rejected(
                    request,
                    context,
                    capabilities,
                    match.matchType,
                    "",
                    "",
                    EnumSet.of(match.reason),
                    match.candidates,
                    Set.of(),
                    Set.of(),
                    Set.of());
        }

        Optional<ScenarioManifest> candidate = catalog.find(match.scenarioId);
        if (candidate.isEmpty()) {
            return rejected(
                    request,
                    context,
                    capabilities,
                    match.matchType,
                    match.scenarioId,
                    match.ruleId,
                    EnumSet.of(ReasonCode.SCENARIO_NOT_REGISTERED),
                    Set.of(match.scenarioId),
                    Set.of(),
                    Set.of(),
                    Set.of());
        }

        ScenarioManifest manifest = candidate.get();
        EnumSet<ReasonCode> reasons = EnumSet.of(match.matchType == MatchType.EXPLICIT_ID
                ? ReasonCode.SELECTED_EXPLICIT_ID
                : ReasonCode.SELECTED_DETERMINISTIC_TEXT);
        Set<VehicleSignalPath> missingContext = new LinkedHashSet<>();
        Set<CapabilityId> missingRequired = new LinkedHashSet<>();
        Set<CapabilityId> missingOptional = new LinkedHashSet<>();
        boolean hardFailure = false;

        if (!manifest.getSupportedSources().contains(request.getSource())) {
            reasons.add(ReasonCode.SOURCE_UNSUPPORTED);
            hardFailure = true;
        }
        if (!manifest.getSupportedZones().contains(request.getZone())) {
            reasons.add(ReasonCode.ZONE_UNSUPPORTED);
            hardFailure = true;
        }
        if (!zoneMatches(request.getZone(), context.getSeatZone())) {
            reasons.add(ReasonCode.CONTEXT_ZONE_MISMATCH);
            hardFailure = true;
        }
        if (!manifest.getContextPolicyId().equals(context.getPolicyId())) {
            reasons.add(ReasonCode.CONTEXT_POLICY_MISMATCH);
            hardFailure = true;
        }
        if (context.isRestricted()) {
            reasons.add(ReasonCode.CONTEXT_RESTRICTED);
            hardFailure = true;
        }
        if (capabilities.getProfile() == CapabilityProfile.PRODUCTION
                && (!context.isProductionTrusted() || !capabilities.isProductionTrusted())) {
            reasons.add(ReasonCode.CONTEXT_NOT_PRODUCTION_TRUSTED);
            hardFailure = true;
        }

        Set<VehicleSignalPath> usableContext = new LinkedHashSet<>();
        for (ContextField field : context.getFields()) {
            if (field.isUsableForDecision()) {
                usableContext.add(field.getKey().getPath());
            }
        }
        for (VehicleSignalPath path : manifest.getRequiredContext()) {
            if (!usableContext.contains(path)) {
                missingContext.add(path);
            }
        }
        if (!missingContext.isEmpty()) {
            reasons.add(ReasonCode.REQUIRED_CONTEXT_UNAVAILABLE);
            hardFailure = true;
        }

        CapabilityCatalog catalogMetadata = CapabilityCatalog.stage2Defaults();
        for (CapabilityId id : manifest.getRequiredCapabilities()) {
            if (!capabilities.isAvailable(id)
                    || !supportsZone(catalogMetadata.require(id), request.getZone())) {
                missingRequired.add(id);
            }
        }
        if (!missingRequired.isEmpty()) {
            reasons.add(ReasonCode.REQUIRED_CAPABILITY_UNAVAILABLE);
            hardFailure = true;
        }
        for (CapabilityId id : manifest.getOptionalCapabilities()) {
            if (!capabilities.isAvailable(id)
                    || !supportsZone(catalogMetadata.require(id), request.getZone())) {
                missingOptional.add(id);
            }
        }
        if (!missingOptional.isEmpty()) {
            reasons.add(ReasonCode.OPTIONAL_CAPABILITY_UNAVAILABLE);
        }

        for (NodeTemplate node : manifest.getPlanTemplate().getNodes()) {
            CapabilityId id = node.getCapabilityId();
            if (id == null
                    || node.getPolicy().getDrivingPolicy() != DrivingPolicy.PARKED_ONLY
                    || context.getDrivingState() == DrivingState.PARKED) {
                continue;
            }
            if (node.isRequired()) {
                missingRequired.add(id);
                reasons.add(ReasonCode.REQUIRED_CAPABILITY_POLICY_BLOCKED);
                hardFailure = true;
            } else {
                missingOptional.add(id);
                reasons.add(ReasonCode.OPTIONAL_CAPABILITY_POLICY_BLOCKED);
            }
        }

        if (hardFailure) {
            return rejected(
                    request,
                    context,
                    capabilities,
                    match.matchType,
                    manifest.getScenarioId(),
                    match.ruleId,
                    reasons,
                    Set.of(manifest.getScenarioId()),
                    missingContext,
                    missingRequired,
                    missingOptional);
        }
        Decision decision = missingOptional.isEmpty()
                ? Decision.ACCEPTED : Decision.DEGRADED;
        return new ScenarioResolution(
                decision,
                match.matchType,
                manifest.getScenarioId(),
                match.ruleId,
                request.getDigest(),
                context.getDigest(),
                capabilities.getDigest(),
                manifest,
                reasons,
                Set.of(manifest.getScenarioId()),
                missingContext,
                missingRequired,
                missingOptional);
    }

    private static ScenarioResolution rejected(
            Request request,
            ContextSnapshot context,
            CapabilitySnapshot capabilities,
            MatchType matchType,
            String scenarioId,
            String ruleId,
            Set<ReasonCode> reasons,
            Set<String> candidates,
            Set<VehicleSignalPath> missingContext,
            Set<CapabilityId> missingRequired,
            Set<CapabilityId> missingOptional) {
        return new ScenarioResolution(
                Decision.REJECTED,
                matchType,
                scenarioId,
                ruleId,
                request.getDigest(),
                context.getDigest(),
                capabilities.getDigest(),
                null,
                reasons,
                candidates,
                missingContext,
                missingRequired,
                missingOptional);
    }

    private static Match match(Request request) {
        if (!request.getExplicitScenarioId().isEmpty()) {
            return new Match(
                    MatchType.EXPLICIT_ID,
                    request.getExplicitScenarioId(),
                    "explicit.v1",
                    null,
                    Set.of(request.getExplicitScenarioId()));
        }
        Set<String> matchedScenarios = new LinkedHashSet<>();
        Set<String> matchedRules = new LinkedHashSet<>();
        String normalized = normalize(request.getTextIntent());
        for (String segment : normalized.split("[,，、;；/|]+")) {
            Rule rule = RULES.get(stripTerminalPunctuation(segment.trim()));
            if (rule != null) {
                matchedScenarios.add(rule.scenarioId);
                matchedRules.add(rule.ruleId);
            }
        }
        if (matchedScenarios.isEmpty()) {
            return new Match(
                    MatchType.NONE,
                    "",
                    "",
                    ReasonCode.UNKNOWN_INTENT,
                    Set.of());
        }
        if (matchedScenarios.size() != 1) {
            return new Match(
                    MatchType.NONE,
                    "",
                    "",
                    ReasonCode.AMBIGUOUS_INTENT,
                    matchedScenarios);
        }
        return new Match(
                MatchType.DETERMINISTIC_TEXT,
                matchedScenarios.iterator().next(),
                matchedRules.iterator().next(),
                null,
                matchedScenarios);
    }

    private static String normalize(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .trim();
        StringBuilder result = new StringBuilder(normalized.length());
        boolean previousWhitespace = false;
        for (int index = 0; index < normalized.length(); index++) {
            char current = normalized.charAt(index);
            if (Character.isWhitespace(current)) {
                if (!previousWhitespace) {
                    result.append(' ');
                }
                previousWhitespace = true;
            } else {
                result.append(current);
                previousWhitespace = false;
            }
        }
        return stripTerminalPunctuation(result.toString());
    }

    private static String stripTerminalPunctuation(String value) {
        int end = value.length();
        while (end > 0 && ".?!。！？".indexOf(value.charAt(end - 1)) >= 0) {
            end--;
        }
        return value.substring(0, end).trim();
    }

    private static boolean zoneMatches(Zone requested, SeatZone captured) {
        try {
            return requested.name().equals(captured.name());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean supportsZone(VehicleCapability capability, Zone zone) {
        if (capability.getAreas().contains("cabin")) {
            return true;
        }
        switch (zone) {
            case ROW1_DRIVER:
                return capability.getAreas().contains("row1.driver");
            case ROW1_PASSENGER:
                return capability.getAreas().contains("row1.passenger");
            case ROW2_LEFT:
                return capability.getAreas().contains("row2.left");
            case ROW2_RIGHT:
                return capability.getAreas().contains("row2.right");
            case CABIN:
                return capability.getAreas().contains("cabin");
            default:
                return false;
        }
    }

    private static Map<String, Rule> rules() {
        Map<String, Rule> result = new LinkedHashMap<>();
        add(result, "intent.cold.v1", "scene.comfort.cold.v1",
                "我冷了", "我有点冷", "我有些冷", "有点冷", "好冷", "cold");
        add(result, "intent.fatigue.v1", "scene.fatigue.assist.v1",
                "我累了", "我有点累", "我有些疲惫", "有点疲惫", "疲惫", "tired", "fatigue");
        add(result, "intent.rest.v1", "scene.rest.nap.v1",
                "我想休息", "休息一下", "休息模式", "小睡一会", "take a rest", "nap");
        return Collections.unmodifiableMap(result);
    }

    private static void add(
            Map<String, Rule> rules,
            String ruleId,
            String scenarioId,
            String... aliases) {
        Rule rule = new Rule(ruleId, scenarioId);
        for (String alias : aliases) {
            String normalized = normalize(alias);
            if (rules.put(normalized, rule) != null) {
                throw new IllegalStateException(
                        "CB_SCENARIO_RESOLVE: duplicate deterministic intent alias");
            }
        }
    }

    private static final class Rule {
        private final String ruleId;
        private final String scenarioId;

        private Rule(String ruleId, String scenarioId) {
            this.ruleId = ruleId;
            this.scenarioId = scenarioId;
        }
    }

    private static final class Match {
        private final MatchType matchType;
        private final String scenarioId;
        private final String ruleId;
        private final ReasonCode reason;
        private final Set<String> candidates;

        private Match(
                MatchType matchType,
                String scenarioId,
                String ruleId,
                ReasonCode reason,
                Set<String> candidates) {
            this.matchType = matchType;
            this.scenarioId = scenarioId;
            this.ruleId = ruleId;
            this.reason = reason;
            this.candidates = candidates;
        }
    }
}
