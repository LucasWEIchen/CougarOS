package com.centralbrain.client2;

/** Driving-aware presentation contract. It never grants Runtime or Effect authority. */
public enum PanelPresentationMode {
    PARKED_FULL(true, true, true),
    MOVING_RESTRICTED(false, false, false);

    private final boolean longTextVisible;
    private final boolean parameterEditingEnabled;
    private final boolean highRiskScenarioEnabled;

    PanelPresentationMode(
            boolean longTextVisible,
            boolean parameterEditingEnabled,
            boolean highRiskScenarioEnabled) {
        this.longTextVisible = longTextVisible;
        this.parameterEditingEnabled = parameterEditingEnabled;
        this.highRiskScenarioEnabled = highRiskScenarioEnabled;
    }

    public boolean isLongTextVisible() {
        return longTextVisible;
    }

    public boolean isParameterEditingEnabled() {
        return parameterEditingEnabled;
    }

    public boolean isHighRiskScenarioEnabled() {
        return highRiskScenarioEnabled;
    }

    public boolean isEffectAuthorizationSource() {
        return false;
    }
}
