package com.centralbrain.runtime.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public final class CockpitModelPromptTest {
    @Test
    public void fatiguePromptCarriesCockpitContextAndRequiredEffects() {
        CockpitModelPrompt prompt = CockpitModelPrompt.forScenario(
                "a".repeat(64), "scene.fatigue.assist.v1");

        assertTrue(prompt.systemInstruction().contains("汽车座舱"));
        assertTrue(prompt.systemInstruction().contains("不能授权Safety或Effect"));
        assertTrue(prompt.getContext().contains("occupant_role=DRIVER"));
        assertTrue(prompt.getContext().contains("hvac_setpoint=26.5_CELSIUS"));
        assertTrue(prompt.getContext().contains("effect_mode=UI_SIMULATION_ONLY"));
        assertEquals(2, prompt.getRequiredActions().size());
        prompt.validateAdmittedActions(List.of("hvac.ventilate", "seat.recline"));
        assertThrows(IllegalStateException.class, () ->
                prompt.validateAdmittedActions(List.of("hvac.ventilate")));
    }

    @Test
    public void coldPromptRequiresWhitelistedHvacAction() {
        CockpitModelPrompt prompt = CockpitModelPrompt.forScenario(
                "b".repeat(64), "scene.comfort.cold.v1");

        assertEquals(List.of("hvac.warm_cabin", "media.keep_playing"),
                prompt.getAllowedActions());
        assertEquals("UNKNOWN_RESTRICTED",
                prompt.getContext().contains("driving_state=UNKNOWN_RESTRICTED")
                        ? "UNKNOWN_RESTRICTED" : "");
        assertThrows(IllegalArgumentException.class, () ->
                CockpitModelPrompt.forScenario("b".repeat(64), "scene.unknown"));
    }
}
