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

    @Test
    public void multimodalPromptLimitsImageReasoningToShoppingAndRouteGoals() {
        CockpitModelPrompt prompt = CockpitModelPrompt.forMultimodal(
                "c".repeat(64), "处理一下");

        assertEquals("scene.cabin.multimodal.assist.v1", prompt.getScenarioId());
        assertEquals("处理一下", prompt.getUtterance());
        assertTrue(prompt.getContext().contains("image_present=true"));
        assertTrue(prompt.getContext().contains("VISIBLE_CABIN_FACTS_ONLY"));
        assertTrue(prompt.getContext().contains(
                "NO_IDENTITY_OR_SENSITIVE_ATTRIBUTE_INFERENCE"));
        assertTrue(prompt.getContext().contains("shopping_mode=PRODUCT_AND_MERCHANT_SEARCH"));
        assertEquals(List.of(
                        "shopping.search_products",
                        "shopping.prepare_order",
                        "navigation.plan_purchase_route"),
                prompt.getAllowedActions());
        assertEquals(2, prompt.getRequiredActions().size());
        prompt.validateAdmittedActions(List.of(
                "shopping.search_products",
                "navigation.plan_purchase_route"));
        assertThrows(IllegalStateException.class, () ->
                prompt.validateAdmittedActions(List.of("shopping.search_products")));
    }

    @Test
    public void freeformPromptCarriesRawIntentButOnlyAdmitsAllowlistedCandidates() {
        String utterance = "请把座舱调暖一点，并找一个休息区";
        CockpitModelPrompt prompt = CockpitModelPrompt.forFreeform(
                "d".repeat(64), utterance);

        assertEquals("scene.aios.freeform.v1", prompt.getScenarioId());
        assertEquals(utterance, prompt.getUtterance());
        assertTrue(prompt.getContext().contains("environment=AUTOMOTIVE_COCKPIT"));
        assertTrue(prompt.getContext().contains("execution_policy=MODEL_PROPOSAL_ONLY"));
        prompt.validateAdmittedActions(List.of(
                "assistant.respond",
                "hvac.warm_cabin",
                "navigation.find_rest_area"));
        assertThrows(IllegalStateException.class, () ->
                prompt.validateAdmittedActions(List.of(
                        "assistant.respond", "vehicle.unlock_doors")));
    }
}
