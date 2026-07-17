package com.centralbrain.client2;

/** Fail-closed mapping from typed driving evidence to presentation mode. */
public final class DrivingUxPolicy {
    private DrivingUxPolicy() {}

    public static PanelPresentationMode modeFor(
            CockpitSeatState.SafetyContext context) {
        if (context == null
                || context.getSource() == CockpitSeatState.EvidenceSource.UNAVAILABLE
                || context.getQuality() != CockpitSeatState.EvidenceQuality.OBSERVED
                || context.getRevision() <= 0) {
            return PanelPresentationMode.MOVING_RESTRICTED;
        }
        return context.getDrivingState() == CockpitSeatState.DrivingState.PARKED
                ? PanelPresentationMode.PARKED_FULL
                : PanelPresentationMode.MOVING_RESTRICTED;
    }

    public static boolean isHighRiskScenario(String scenarioId) {
        return "skill.nap".equals(scenarioId);
    }
}
