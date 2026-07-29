package com.centralbrain.runtime.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Bounded provider-neutral prompt material for cockpit scenarios and free-form intent. */
public final class CockpitModelPrompt {
    public static final String EFFECT_MODE = "UI_SIMULATION_ONLY";
    public static final String SAFETY_MODE = "INTERFACE_RESERVED";

    private final String inputDigest;
    private final String scenarioId;
    private final String utterance;
    private final String context;
    private final List<String> allowedActions;
    private final Set<String> requiredActions;

    private CockpitModelPrompt(
            String inputDigest,
            String scenarioId,
            String utterance,
            String context,
            List<String> allowedActions,
            Set<String> requiredActions) {
        this.inputDigest = requireDigest(inputDigest);
        this.scenarioId = bounded(scenarioId, 96, "scenarioId");
        this.utterance = bounded(utterance, 1_024, "utterance");
        this.context = bounded(context, 1_024, "context");
        this.allowedActions = immutableActions(allowedActions, "allowedActions");
        this.requiredActions = Collections.unmodifiableSet(
                new LinkedHashSet<>(immutableActions(
                        new ArrayList<>(requiredActions), "requiredActions")));
        if (!this.allowedActions.containsAll(this.requiredActions)) {
            throw new IllegalArgumentException(
                    "CB_COCKPIT_MODEL_PROMPT: required action is not allowlisted");
        }
    }

    public static CockpitModelPrompt forScenario(String inputDigest, String scenarioId) {
        if ("scene.comfort.cold.v1".equals(scenarioId)) {
            return new CockpitModelPrompt(
                    inputDigest,
                    scenarioId,
                    "车里有点冷",
                    context(
                            "ROW1_DRIVER",
                            "UNKNOWN_RESTRICTED",
                            "SIMULATED",
                            "17.0_CELSIUS",
                            "26.5_CELSIUS",
                            "NOT_OBSERVED",
                            "HVAC,SEAT"),
                    List.of("hvac.warm_cabin", "media.keep_playing"),
                    Set.of("hvac.warm_cabin"));
        }
        if ("scene.fatigue.assist.v1".equals(scenarioId)) {
            return new CockpitModelPrompt(
                    inputDigest,
                    scenarioId,
                    "我有些疲惫",
                    context(
                            "ROW1_DRIVER",
                            "UNKNOWN_RESTRICTED",
                            "SIMULATED",
                            "26.5_CELSIUS",
                            "26.5_CELSIUS",
                            "0.82_SIMULATED",
                            "HVAC,SEAT,MEDIA,NAVIGATION"),
                    List.of(
                            "seat.recline",
                            "hvac.ventilate",
                            "media.pause",
                            "navigation.find_rest_area"),
                    Set.of("seat.recline", "hvac.ventilate"));
        }
        if ("scene.cabin.multimodal.assist.v1".equals(scenarioId)) {
            return forMultimodal(inputDigest, "处理一下");
        }
        throw new IllegalArgumentException(
                "CB_COCKPIT_MODEL_PROMPT: scenario is not allowlisted");
    }

    public static CockpitModelPrompt forFreeform(
            String inputDigest, String inputText) {
        return new CockpitModelPrompt(
                inputDigest,
                "scene.aios.freeform.v1",
                inputText,
                context(
                        "ROW1_DRIVER",
                        "UNKNOWN_RESTRICTED",
                        "ANDROID_HMI_TEXT",
                        "NOT_OBSERVED",
                        "NOT_OBSERVED",
                        "NOT_OBSERVED",
                        "HVAC,SEAT,MEDIA,NAVIGATION")
                        + ";input_mode=FREE_FORM"
                        + ";execution_policy=MODEL_PROPOSAL_ONLY"
                        + ";vehicle_bus=UNAVAILABLE",
                List.of(
                        "assistant.respond",
                        "hvac.warm_cabin",
                        "hvac.cool_cabin",
                        "hvac.ventilate",
                        "seat.recline",
                        "media.pause",
                        "media.resume",
                        "navigation.find_rest_area"),
                Set.of("assistant.respond"));
    }

    public static CockpitModelPrompt forMultimodal(
            String inputDigest, String inputText) {
        return new CockpitModelPrompt(
                inputDigest,
                "scene.cabin.multimodal.assist.v1",
                inputText,
                context(
                        "CABIN_VISIBLE_FRAME",
                        "UNKNOWN_RESTRICTED",
                        "ANDROID_HMI_IMAGE_AND_TEXT",
                        "22.0_CELSIUS",
                        "26.5_CELSIUS",
                        "NOT_OBSERVED",
                        "SHOPPING,NAVIGATION")
                        + ";image_present=true"
                        + ";image_scope=VISIBLE_CABIN_FACTS_ONLY"
                        + ";occupancy_scope=FOUR_SEAT_ZONES"
                        + ";shopping_mode=PRODUCT_AND_MERCHANT_SEARCH"
                        + ";route_mode=PURCHASE_ROUTE_PREVIEW"
                        + ";privacy_policy=NO_IDENTITY_OR_SENSITIVE_ATTRIBUTE_INFERENCE",
                List.of(
                        "shopping.search_products",
                        "shopping.prepare_order",
                        "navigation.plan_purchase_route"),
                Set.of(
                        "shopping.search_products",
                        "navigation.plan_purchase_route"));
    }

    public String systemInstruction() {
        return "你是运行在汽车座舱中的AIOS场景规划器，首要目标是服务驾驶员的舒适、清醒和行车任务。"
                + "你只提出候选动作，不能授权Safety或Effect，也不能声称真实车辆已经执行。"
                + "对于自由输入，先判断是否属于座舱服务；不明确时只回复并要求澄清。"
                + "当前末端反馈仅为UI仿真，安全接口仅保留合同。"
                + "处理图片时只描述画面中直接可见的座舱事实，不识别人身份，不推断敏感属性。"
                + "当可见事实支持购物候选时，只能提出商品搜索和购买路径规划目标；"
                + "不能授权下单、支付或启动导航，这三类副作用由确定性策略和独立确认控制。"
                + "只输出一个JSON对象，不输出Markdown、代码围栏或解释。";
    }

    public String userInstruction() {
        return "用户表达：" + utterance
                + "\n场景ID：" + scenarioId
                + "\n座舱上下文：" + context
                + "\n允许动作：" + String.join(",", allowedActions)
                + "\n必要动作：" + String.join(",", requiredActions);
    }

    public void validateAdmittedActions(List<String> admittedActions) {
        Objects.requireNonNull(admittedActions, "admittedActions");
        if (admittedActions.isEmpty() || admittedActions.size() > 4) {
            throw new IllegalStateException(
                    "CB_COCKPIT_MODEL_PROMPT: admitted action count is invalid");
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String action : admittedActions) {
            if (!allowedActions.contains(action) || !unique.add(action)) {
                throw new IllegalStateException(
                        "CB_COCKPIT_MODEL_PROMPT: action is not allowlisted");
            }
        }
        if (!admittedActions.containsAll(requiredActions)) {
            throw new IllegalStateException(
                    "CB_COCKPIT_MODEL_PROMPT: required action is missing");
        }
    }

    public boolean matches(CockpitModelPrompt other) {
        return other != null
                && scenarioId.equals(other.scenarioId)
                && utterance.equals(other.utterance)
                && context.equals(other.context)
                && allowedActions.equals(other.allowedActions)
                && requiredActions.equals(other.requiredActions);
    }

    public String getInputDigest() { return inputDigest; }
    public String getScenarioId() { return scenarioId; }
    public String getUtterance() { return utterance; }
    public String getContext() { return context; }
    public List<String> getAllowedActions() { return allowedActions; }
    public Set<String> getRequiredActions() { return requiredActions; }

    private static String context(
            String zone,
            String drivingState,
            String source,
            String cabinTemperature,
            String hvacSetpoint,
            String fatigueScore,
            String capabilities) {
        return "environment=AUTOMOTIVE_COCKPIT"
                + ";occupant_role=DRIVER"
                + ";service_goal=DRIVER_COMFORT_AND_ALERTNESS"
                + ";zone=" + zone
                + ";driving_state=" + drivingState
                + ";context_source=" + source
                + ";cabin_temperature=" + cabinTemperature
                + ";hvac_setpoint=" + hvacSetpoint
                + ";fatigue_score=" + fatigueScore
                + ";available_effect_domains=" + capabilities
                + ";effect_mode=" + EFFECT_MODE
                + ";safety_mode=" + SAFETY_MODE;
    }

    private static List<String> immutableActions(List<String> actions, String field) {
        Objects.requireNonNull(actions, field);
        if (actions.isEmpty() || actions.size() > 8) {
            throw new IllegalArgumentException(
                    "CB_COCKPIT_MODEL_PROMPT: " + field + " count is invalid");
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String action : actions) {
            String value = bounded(action, 64, field);
            if (!unique.add(value)) {
                throw new IllegalArgumentException(
                        "CB_COCKPIT_MODEL_PROMPT: duplicate " + field);
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(unique));
    }

    private static String bounded(String value, int maximum, String field) {
        String result = Objects.requireNonNull(value, field).trim();
        if (result.isEmpty() || result.length() > maximum) {
            throw new IllegalArgumentException(
                    "CB_COCKPIT_MODEL_PROMPT: " + field + " is outside bounds");
        }
        for (int index = 0; index < result.length(); index++) {
            if (Character.isISOControl(result.charAt(index))
                    && result.charAt(index) != '\n') {
                throw new IllegalArgumentException(
                        "CB_COCKPIT_MODEL_PROMPT: " + field + " contains control data");
            }
        }
        return result;
    }

    private static String requireDigest(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "CB_COCKPIT_MODEL_PROMPT: inputDigest is invalid");
        }
        return value;
    }
}
