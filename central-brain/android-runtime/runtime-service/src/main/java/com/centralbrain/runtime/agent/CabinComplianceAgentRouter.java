package com.centralbrain.runtime.agent;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/** Deterministic route boundary between cabin compliance triage and specialist agents. */
public final class CabinComplianceAgentRouter {
    public static final String SMOKING_SCENARIO_ID =
            "scene.cabin.compliance.smoking.v1";
    public static final String TRIAGE_AGENT_ID =
            "agent.cabin.compliance-triage.v1";
    public static final String SMOKING_AGENT_ID =
            "agent.cabin.smoking-detection.v1";
    public static final String SMOKING_CANDIDATE = "SMOKING_DETECTION";
    public static final double MINIMUM_TRIAGE_CONFIDENCE = 0.5;

    public enum ActivationSource {
        EXPLICIT_SCENARIO,
        TRIAGE_CANDIDATE
    }

    public static final class RouteDecision {
        private final ActivationSource source;
        private final String triageAgentId;
        private final String specialistAgentId;
        private final String scenarioId;
        private final String routeDigest;

        private RouteDecision(
                ActivationSource source,
                String scenarioId,
                String specialistAgentId) {
            this.source = Objects.requireNonNull(source, "source");
            this.triageAgentId = TRIAGE_AGENT_ID;
            this.specialistAgentId = Objects.requireNonNull(
                    specialistAgentId, "specialistAgentId");
            this.scenarioId = Objects.requireNonNull(scenarioId, "scenarioId");
            this.routeDigest = digest(
                    source.name(), triageAgentId, specialistAgentId, scenarioId);
        }

        public ActivationSource getSource() { return source; }
        public String getTriageAgentId() { return triageAgentId; }
        public String getSpecialistAgentId() { return specialistAgentId; }
        public String getScenarioId() { return scenarioId; }
        public String getRouteDigest() { return routeDigest; }
        public boolean isModelSelectedRoute() {
            return source == ActivationSource.TRIAGE_CANDIDATE;
        }
    }

    /** Explicit HMI scenarios never ask a model to choose the specialist. */
    public RouteDecision routeExplicit(String scenarioId) {
        if (!SMOKING_SCENARIO_ID.equals(scenarioId)) {
            throw violation("explicit scenario is not registered");
        }
        return new RouteDecision(
                ActivationSource.EXPLICIT_SCENARIO,
                scenarioId,
                SMOKING_AGENT_ID);
    }

    /** Future camera triage may nominate a specialist, but code admits the route. */
    public RouteDecision routeCandidate(
            String candidateType,
            double confidence,
            String scenarioId) {
        if (!Double.isFinite(confidence)
                || confidence < MINIMUM_TRIAGE_CONFIDENCE
                || confidence > 1.0) {
            throw violation("triage confidence is outside the admission bound");
        }
        if (!SMOKING_CANDIDATE.equals(candidateType)
                || !SMOKING_SCENARIO_ID.equals(scenarioId)) {
            throw violation("triage candidate has no registered specialist");
        }
        return new RouteDecision(
                ActivationSource.TRIAGE_CANDIDATE,
                scenarioId,
                SMOKING_AGENT_ID);
    }

    private static String digest(String... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, "central-brain-cabin-agent-route-v1");
            for (String value : values) {
                update(digest, value);
            }
            byte[] bytes = digest.digest();
            char[] result = new char[bytes.length * 2];
            char[] alphabet = "0123456789abcdef".toCharArray();
            for (int index = 0; index < bytes.length; index++) {
                int value = bytes[index] & 0xff;
                result[index * 2] = alphabet[value >>> 4];
                result[index * 2 + 1] = alphabet[value & 0x0f];
            }
            return new String(result);
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_CABIN_AGENT_ROUTER: " + message);
    }
}
