package com.centralbrain.runtime.suggestion;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Arrays;

/** Debug-only active-suggestion UX and API 33 ARM64 contract probe. */
public final class ActiveSuggestionHmiActivity extends Activity {
    private static final String TAG = "CbActiveSuggestion";
    private static final String OWNER =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String PAYLOAD =
            "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc";
    private static final String EVIDENCE =
            "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd";

    private ActiveSuggestionController controller;
    private ActiveSuggestionController.DrivingState drivingState =
            ActiveSuggestionController.DrivingState.PARKED;
    private TextView modeView;
    private TextView chainView;
    private TextView outcomeView;
    private LinearLayout suggestionsView;
    private long sequence;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        controller = ActiveSuggestionController.createForContractTest(SystemClock::elapsedRealtime);
        if (getIntent().getBooleanExtra("automated", false)) {
            runAutomatedProbe();
            return;
        }
        setContentView(createContentView());
        render("READY");
    }

    private View createContentView() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.TRANSPARENT);
        root.setOnClickListener(view -> finish());

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        scroller.setClickable(true);
        scroller.setOnClickListener(view -> { });
        int responsiveWidth = Math.max(dp(420),
                Math.round(getResources().getDisplayMetrics().widthPixels * 0.34f));
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                Math.min(dp(650), responsiveWidth),
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.END);
        panelParams.setMargins(dp(16), dp(16), dp(16), dp(16));
        scroller.setLayoutParams(panelParams);
        scroller.setBackground(panelBackground());
        scroller.setElevation(dp(8));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(22), dp(18), dp(22), dp(22));
        scroller.addView(panel, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView eyebrow = text("AI ACTIVE CARE", 11, Color.rgb(18, 112, 104));
        eyebrow.setTypeface(null, Typeface.BOLD);
        panel.addView(eyebrow);
        TextView title = text("主动场景建议", 23, Color.rgb(22, 32, 40));
        title.setTypeface(null, Typeface.BOLD);
        panel.addView(title, marginTop(5));

        modeView = text("", 12, Color.rgb(38, 52, 61));
        modeView.setPadding(dp(12), dp(10), dp(12), dp(10));
        modeView.setBackground(sectionBackground());
        panel.addView(modeView, marginTop(14));

        TextView inputLabel = text("场景输入", 11, Color.rgb(74, 86, 97));
        inputLabel.setTypeface(null, Typeface.BOLD);
        panel.addView(inputLabel, marginTop(15));
        LinearLayout inputs = new LinearLayout(this);
        inputs.setOrientation(LinearLayout.HORIZONTAL);
        Button fatigue = button("我有些疲惫");
        fatigue.setOnClickListener(view -> addSuggestion(
                "fatigue-care", ActiveSuggestionController.ReasonCode.DRIVER_FATIGUE));
        inputs.addView(fatigue, weighted());
        Button cold = button("我有点冷");
        cold.setOnClickListener(view -> addSuggestion(
                "comfort-care", ActiveSuggestionController.ReasonCode.CABIN_TOO_COLD));
        inputs.addView(cold, weightedMargin());
        panel.addView(inputs, fixedHeight(50, 6));

        TextView stateLabel = text("驾驶状态", 11, Color.rgb(74, 86, 97));
        stateLabel.setTypeface(null, Typeface.BOLD);
        panel.addView(stateLabel, marginTop(15));
        LinearLayout states = new LinearLayout(this);
        states.setOrientation(LinearLayout.HORIZONTAL);
        states.addView(drivingButton(
                "驻车", ActiveSuggestionController.DrivingState.PARKED), weighted());
        states.addView(drivingButton(
                "行驶", ActiveSuggestionController.DrivingState.MOVING), weightedMargin());
        states.addView(drivingButton(
                "未知", ActiveSuggestionController.DrivingState.UNKNOWN), weightedMargin());
        panel.addView(states, fixedHeight(46, 6));

        TextView chainLabel = text("自动化链路", 11, Color.rgb(74, 86, 97));
        chainLabel.setTypeface(null, Typeface.BOLD);
        panel.addView(chainLabel, marginTop(18));
        chainView = text("", 12, Color.rgb(38, 52, 61));
        chainView.setLineSpacing(dp(3), 1.0f);
        chainView.setPadding(dp(12), dp(12), dp(12), dp(12));
        chainView.setBackground(sectionBackground());
        panel.addView(chainView, marginTop(6));

        suggestionsView = new LinearLayout(this);
        suggestionsView.setOrientation(LinearLayout.VERTICAL);
        panel.addView(suggestionsView, marginTop(8));

        outcomeView = text("", 11, Color.rgb(83, 99, 110));
        panel.addView(outcomeView, marginTop(12));
        TextView boundary = text(
                "当前仅验证主动建议的合并、冷却、驾驶限制和展示。建议不会自动批准，也不会控制空调、座椅、语音、模型或 NPU。",
                11,
                Color.rgb(83, 99, 110));
        panel.addView(boundary, marginTop(13));

        root.addView(scroller);
        return root;
    }

    private void addSuggestion(
            String scenarioId, ActiveSuggestionController.ReasonCode reason) {
        long now = SystemClock.elapsedRealtime();
        sequence++;
        ActiveSuggestionController.IngestResult result = controller.ingest(
                ActiveSuggestionController.Candidate.create(
                        "debug.suggestion." + sequence,
                        PAYLOAD,
                        OWNER,
                        scenarioId,
                        "driver",
                        reason,
                        now,
                        now + 300_000L,
                        60_000L,
                        EVIDENCE));
        render(result.getCode().name());
    }

    private Button drivingButton(
            String label, ActiveSuggestionController.DrivingState target) {
        Button button = button(label);
        button.setContentDescription("设置驾驶状态为" + label);
        button.setOnClickListener(view -> {
            drivingState = target;
            render("DRIVING_STATE_CHANGED");
        });
        return button;
    }

    private void render(String outcome) {
        ActiveSuggestionController.HmiSnapshot snapshot = controller.snapshot(
                OWNER, drivingState);
        modeView.setText("模式：" + snapshot.getDrivingState()
                + " · " + snapshot.getPresentationMode()
                + (snapshot.getMinimalVoiceKey() == null
                ? "" : " · 最小语音提示已投影"));
        suggestionsView.removeAllViews();
        if (snapshot.getCards().isEmpty()) {
            chainView.setText("等待场景输入\n感知 → 原因 → 建议 → 用户确认 → 执行（未接入）");
        } else {
            ActiveSuggestionController.SuggestionCard top = snapshot.getCards().get(0);
            if (snapshot.getPresentationMode()
                    == ActiveSuggestionController.PresentationMode.MINIMAL_BANNER) {
                chainView.setText("检测到需要关注的座舱场景\n当前处于驾驶限制模式，仅显示最高优先级建议；详细原因和复杂操作已隐藏。");
            } else {
                chainView.setText("1  场景感知：" + reasonLabel(top.getReason())
                        + "\n2  原因判断：" + whyLabel(top.getReason())
                        + "\n3  建议方案：" + planLabel(top.getReason())
                        + "\n4  当前状态：等待用户查看与确认，未执行车控");
            }
            for (ActiveSuggestionController.SuggestionCard card : snapshot.getCards()) {
                suggestionsView.addView(cardView(card, snapshot.getPresentationMode()));
            }
        }
        outcomeView.setText("最近结果：" + outcome
                + " · revision " + snapshot.getRevision()
                + "\nEffect dispatch：FALSE · Preference persistence：FALSE");
    }

    private View cardView(
            ActiveSuggestionController.SuggestionCard card,
            ActiveSuggestionController.PresentationMode mode) {
        LinearLayout cardView = new LinearLayout(this);
        cardView.setOrientation(LinearLayout.VERTICAL);
        cardView.setPadding(dp(12), dp(11), dp(12), dp(11));
        cardView.setBackground(cardBackground());
        LinearLayout.LayoutParams cardParams = marginTop(8);
        cardView.setLayoutParams(cardParams);

        TextView title = text(reasonLabel(card.getReason()), 14, Color.rgb(22, 32, 40));
        title.setTypeface(null, Typeface.BOLD);
        cardView.addView(title);
        if (mode == ActiveSuggestionController.PresentationMode.FULL_CARD) {
            cardView.addView(text(
                    whyLabel(card.getReason())
                            + "\n建议：" + planLabel(card.getReason())
                            + "\n合并：" + card.getMergedCount()
                            + " 条 · 关闭后冷却 " + (card.getCooldownMs() / 1000L) + " 秒",
                    11,
                    Color.rgb(55, 69, 78)), marginTop(5));
        } else {
            cardView.addView(text("已采用行驶中最小提示", 11, Color.rgb(55, 69, 78)),
                    marginTop(4));
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button dismiss = button("稍后提醒");
        dismiss.setOnClickListener(view -> {
            ActiveSuggestionController.ActionResult result = controller.dismiss(
                    OWNER, card.getSuggestionId(), drivingState);
            render(result.getCode().name());
        });
        actions.addView(dismiss, weighted());
        if (card.getActions().contains(ActiveSuggestionController.SuggestedAction.NEVER_ASK)) {
            Button neverAsk = button("不再询问");
            neverAsk.setOnClickListener(view -> {
                ActiveSuggestionController.ActionResult result = controller.neverAsk(
                        OWNER, card.getSuggestionId(), drivingState);
                render(result.getCode().name());
            });
            actions.addView(neverAsk, weightedMargin());
        }
        cardView.addView(actions, fixedHeight(44, 8));
        return cardView;
    }

    private void runAutomatedProbe() {
        String nonce = getIntent().getStringExtra("nonce");
        if (nonce == null || nonce.isEmpty()) {
            nonce = "missing";
        }
        try {
            long now = SystemClock.elapsedRealtime();
            ActiveSuggestionController.Candidate fatigue =
                    ActiveSuggestionController.Candidate.create(
                            "probe.fatigue", PAYLOAD, OWNER, "fatigue-care", "driver",
                            ActiveSuggestionController.ReasonCode.DRIVER_FATIGUE,
                            now, now + 300_000L, 60_000L, EVIDENCE);
            ActiveSuggestionController.IngestResult added = controller.ingest(fatigue);
            ActiveSuggestionController.IngestResult replay = controller.ingest(fatigue);
            controller.ingest(ActiveSuggestionController.Candidate.create(
                    "probe.fatigue.merge", PAYLOAD, OWNER, "fatigue-care", "driver",
                    ActiveSuggestionController.ReasonCode.DRIVER_ATTENTION_LOW,
                    now, now + 300_000L, 90_000L, EVIDENCE));
            ActiveSuggestionController.HmiSnapshot parked = controller.snapshot(
                    OWNER, ActiveSuggestionController.DrivingState.PARKED);
            ActiveSuggestionController.HmiSnapshot moving = controller.snapshot(
                    OWNER, ActiveSuggestionController.DrivingState.MOVING);
            ActiveSuggestionController.ActionResult movingNeverAsk = controller.neverAsk(
                    OWNER, parked.getCards().get(0).getSuggestionId(),
                    ActiveSuggestionController.DrivingState.MOVING);
            ActiveSuggestionController.ActionResult parkedNeverAsk = controller.neverAsk(
                    OWNER, parked.getCards().get(0).getSuggestionId(),
                    ActiveSuggestionController.DrivingState.PARKED);
            boolean android13Arm64 = Build.VERSION.SDK_INT == 33
                    && Arrays.asList(Build.SUPPORTED_ABIS).contains("arm64-v8a");
            boolean fullCard = added.getCode() == ActiveSuggestionController.IngestCode.ADDED
                    && parked.getPresentationMode()
                    == ActiveSuggestionController.PresentationMode.FULL_CARD
                    && parked.getCards().get(0).getWhyKey() != null
                    && parked.getCards().get(0).getCooldownMs() == 90_000L;
            boolean mergeReplay = parked.getCards().get(0).getMergedCount() == 2
                    && replay.getCode() == ActiveSuggestionController.IngestCode.REPLAYED;
            boolean movingMinimal = moving.getPresentationMode()
                    == ActiveSuggestionController.PresentationMode.MINIMAL_BANNER
                    && moving.getCards().size() == 1
                    && moving.getMinimalVoiceKey() != null
                    && !moving.isVoiceSynthesisRequested();
            boolean neverAsk = movingNeverAsk.getCode()
                    == ActiveSuggestionController.ActionCode.DRIVING_RESTRICTED
                    && parkedNeverAsk.getCode()
                    == ActiveSuggestionController.ActionCode.APPLIED
                    && !parkedNeverAsk.isPreferencePersisted();

            Log.i(TAG, "nonce=" + nonce
                    + " active_suggestion_hmi_probe_complete=true"
                    + " active_suggestion_full_card_verified=" + fullCard
                    + " active_suggestion_merge_replay_verified=" + mergeReplay
                    + " active_suggestion_moving_minimal_verified=" + movingMinimal
                    + " active_suggestion_never_ask_verified=" + neverAsk
                    + " active_suggestion_android13_arm64_verified=" + android13Arm64
                    + " active_suggestion_hmi_projection_only=true"
                    + " active_suggestion_production_source_wired=false"
                    + " active_suggestion_preference_repository_wired=false"
                    + " active_suggestion_voice_engine_wired=false"
                    + " trigger_engine_wired=false"
                    + " graph_execution_enabled=false"
                    + " effect_dispatch_enabled=false"
                    + " vehicle_readback_accessed=false"
                    + " model_invoked=false"
                    + " npu_accessed=false"
                    + " network_accessed=false"
                    + " hardware_accessed=false"
                    + " production_ready=false"
                    + " target_hardware_validated=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " active_suggestion_hmi_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " effect_dispatch_enabled=false"
                    + " hardware_accessed=false");
        }
        finish();
    }

    private String reasonLabel(ActiveSuggestionController.ReasonCode reason) {
        switch (reason) {
            case DRIVER_FATIGUE:
                return "检测到疲惫场景";
            case DRIVER_ATTENTION_LOW:
                return "注意力恢复场景";
            case CABIN_TOO_COLD:
                return "座舱偏冷场景";
            case CABIN_AIR_QUALITY:
                return "空气质量场景";
            case RUNTIME_DEGRADED:
                return "系统降级场景";
            default:
                throw new IllegalArgumentException("unsupported reason");
        }
    }

    private String whyLabel(ActiveSuggestionController.ReasonCode reason) {
        switch (reason) {
            case DRIVER_FATIGUE:
                return "用户主动表达疲惫，需要舒缓座舱环境";
            case DRIVER_ATTENTION_LOW:
                return "注意力状态需要恢复";
            case CABIN_TOO_COLD:
                return "用户主动表达偏冷，需要改善热舒适";
            case CABIN_AIR_QUALITY:
                return "座舱空气状态需要改善";
            case RUNTIME_DEGRADED:
                return "运行时能力处于受限状态";
            default:
                throw new IllegalArgumentException("unsupported reason");
        }
    }

    private String planLabel(ActiveSuggestionController.ReasonCode reason) {
        switch (reason) {
            case DRIVER_FATIGUE:
                return "预览座椅舒缓与柔和通风方案";
            case DRIVER_ATTENTION_LOW:
                return "预览注意力恢复方案";
            case CABIN_TOO_COLD:
                return "预览升温与座椅加热方案";
            case CABIN_AIR_QUALITY:
                return "预览空气净化方案";
            case RUNTIME_DEGRADED:
                return "保持当前安全状态并查看降级原因";
            default:
                throw new IllegalArgumentException("unsupported reason");
        }
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(11);
        button.setTextColor(Color.rgb(38, 52, 61));
        button.setMinHeight(dp(44));
        button.setBackground(actionBackground());
        return button;
    }

    private TextView text(String value, int sizeSp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        return view;
    }

    private LinearLayout.LayoutParams marginTop(int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topDp);
        return params;
    }

    private LinearLayout.LayoutParams fixedHeight(int heightDp, int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp));
        params.topMargin = dp(topDp);
        return params;
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
    }

    private LinearLayout.LayoutParams weightedMargin() {
        LinearLayout.LayoutParams params = weighted();
        params.leftMargin = dp(6);
        return params;
    }

    private GradientDrawable panelBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(198, 238, 242, 243));
        background.setCornerRadius(dp(8));
        return background;
    }

    private GradientDrawable sectionBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(132, 255, 255, 255));
        background.setCornerRadius(dp(6));
        return background;
    }

    private GradientDrawable cardBackground() {
        GradientDrawable background = sectionBackground();
        background.setStroke(dp(1), Color.argb(60, 38, 52, 61));
        return background;
    }

    private GradientDrawable actionBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(190, 255, 255, 255));
        background.setCornerRadius(dp(6));
        background.setStroke(dp(1), Color.argb(86, 38, 52, 61));
        return background;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
