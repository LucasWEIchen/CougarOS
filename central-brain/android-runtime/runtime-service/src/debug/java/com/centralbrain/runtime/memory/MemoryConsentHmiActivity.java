package com.centralbrain.runtime.memory;

import android.app.Activity;
import android.graphics.Color;
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
import android.widget.Switch;
import android.widget.TextView;

import java.util.Arrays;

/** Debug-only interactive Memory consent HMI and API 33 ARM64 contract probe. */
public final class MemoryConsentHmiActivity extends Activity {
    private static final String TAG = "CbMemoryConsent";
    private static final String OWNER =
            "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc";

    private MemoryConsentController controller;
    private MemoryConsentController.DrivingState drivingState =
            MemoryConsentController.DrivingState.PARKED;
    private TextView modeView;
    private TextView sourceView;
    private TextView outcomeView;
    private Switch retainedMemorySwitch;
    private Button clearPreferencesButton;
    private boolean rendering;
    private long requestSequence;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        controller = MemoryConsentController.createForContractTest(
                SystemClock::elapsedRealtime,
                (request, evidence) -> MemoryConsentController.AuthorizationDecision.ALLOWED);
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
        int maxWidth = dp(640);
        int responsiveWidth = Math.max(dp(360),
                Math.round(getResources().getDisplayMetrics().widthPixels * 0.38f));
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                Math.min(maxWidth, responsiveWidth),
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

        TextView eyebrow = text("MEMORY CONTROL", 11, Color.rgb(23, 111, 104));
        eyebrow.setTypeface(null, android.graphics.Typeface.BOLD);
        panel.addView(eyebrow);

        TextView title = text("记忆与隐私", 23, Color.rgb(23, 33, 41));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        panel.addView(title, marginTop(6));

        modeView = text("", 12, Color.rgb(38, 52, 61));
        modeView.setPadding(dp(12), dp(10), dp(12), dp(10));
        modeView.setBackground(sectionBackground());
        panel.addView(modeView, marginTop(14));

        TextView drivingLabel = text("驾驶状态", 11, Color.rgb(74, 86, 97));
        drivingLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        panel.addView(drivingLabel, marginTop(16));
        LinearLayout drivingControls = new LinearLayout(this);
        drivingControls.setOrientation(LinearLayout.HORIZONTAL);
        drivingControls.addView(drivingButton(
                "驻车", MemoryConsentController.DrivingState.PARKED), weighted());
        drivingControls.addView(drivingButton(
                "行驶", MemoryConsentController.DrivingState.MOVING), weightedMargin());
        drivingControls.addView(drivingButton(
                "未知", MemoryConsentController.DrivingState.UNKNOWN), weightedMargin());
        panel.addView(drivingControls, fixedHeight(48, 6));

        retainedMemorySwitch = new Switch(this);
        retainedMemorySwitch.setText("允许保留偏好与场景摘要");
        retainedMemorySwitch.setTextColor(Color.rgb(38, 52, 61));
        retainedMemorySwitch.setTextSize(13);
        retainedMemorySwitch.setPadding(dp(12), 0, dp(8), 0);
        retainedMemorySwitch.setBackground(sectionBackground());
        retainedMemorySwitch.setOnCheckedChangeListener((button, checked) -> {
            if (rendering) {
                return;
            }
            long now = SystemClock.elapsedRealtime();
            MemoryConsentController.MutationResult result = controller
                    .setRetainedMemoryEnabled(
                            OWNER,
                            checked,
                            drivingState,
                            MemoryConsentController.MutationEvidence.forRetainedMemory(
                                    nextRequestId("memory.toggle"),
                                    OWNER,
                                    checked,
                                    now,
                                    now + 30_000));
            render(result.getCode().name());
        });
        panel.addView(retainedMemorySwitch, fixedHeight(56, 18));

        clearPreferencesButton = button("清除已保存偏好");
        clearPreferencesButton.setOnClickListener(view -> {
            long now = SystemClock.elapsedRealtime();
            MemoryConsentController.MutationResult result = controller
                    .clearProfilePreferences(
                            OWNER,
                            drivingState,
                            MemoryConsentController.MutationEvidence.forPreferenceClear(
                                    nextRequestId("memory.clear"),
                                    OWNER,
                                    now,
                                    now + 30_000));
            render(result.getCode().name());
        });
        panel.addView(clearPreferencesButton, fixedHeight(48, 8));

        TextView sourcesLabel = text("记忆来源", 11, Color.rgb(74, 86, 97));
        sourcesLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        panel.addView(sourcesLabel, marginTop(18));
        sourceView = text("", 12, Color.rgb(38, 52, 61));
        sourceView.setLineSpacing(dp(3), 1.0f);
        sourceView.setPadding(dp(12), dp(12), dp(12), dp(12));
        sourceView.setBackground(sectionBackground());
        panel.addView(sourceView, marginTop(6));

        outcomeView = text("", 11, Color.rgb(96, 112, 123));
        panel.addView(outcomeView, marginTop(14));

        TextView boundary = text(
                "当前操作只更新 debug 进程内 HMI 投影。量产 Memory authority、加密仓库、模型上下文与车辆接口均未接入。",
                11,
                Color.rgb(96, 112, 123));
        panel.addView(boundary, marginTop(16));

        root.addView(scroller);
        return root;
    }

    private Button drivingButton(
            String label,
            MemoryConsentController.DrivingState target) {
        Button button = button(label);
        button.setContentDescription("设置驾驶状态为" + label);
        button.setOnClickListener(view -> {
            drivingState = target;
            render("DRIVING_STATE_CHANGED");
        });
        return button;
    }

    private void render(String outcome) {
        MemoryConsentController.HmiSnapshot snapshot = controller.snapshot(OWNER, drivingState);
        boolean allowed = snapshot.isManagementAllowed();
        modeView.setText("模式：" + drivingState
                + (allowed ? " · 可管理" : " · 仅查看来源"));
        StringBuilder sources = new StringBuilder();
        for (MemoryConsentController.SourceStatus source : snapshot.getSources()) {
            if (sources.length() > 0) {
                sources.append("\n\n");
            }
            sources.append(source.getSource())
                    .append("  ·  ")
                    .append(source.isEnabled() ? "ENABLED" : "DISABLED")
                    .append("\n用途：")
                    .append(source.getPurpose())
                    .append("\n保留：")
                    .append(source.getRetention())
                    .append(" · 状态：")
                    .append(source.getStoragePresence());
        }
        sourceView.setText(sources.toString());
        outcomeView.setText("最近结果：" + outcome
                + " · revision " + snapshot.getRevision()
                + "\nRepository mutation：FALSE");
        rendering = true;
        retainedMemorySwitch.setChecked(snapshot.isRetainedMemoryEnabled());
        retainedMemorySwitch.setEnabled(allowed);
        retainedMemorySwitch.setAlpha(allowed ? 1.0f : 0.55f);
        clearPreferencesButton.setEnabled(allowed);
        clearPreferencesButton.setAlpha(allowed ? 1.0f : 0.55f);
        rendering = false;
    }

    private void runAutomatedProbe() {
        String nonce = getIntent().getStringExtra("nonce");
        if (nonce == null || nonce.isEmpty()) {
            nonce = "missing";
        }
        try {
            long now = SystemClock.elapsedRealtime();
            MemoryConsentController.HmiSnapshot initial = controller.snapshot(
                    OWNER, MemoryConsentController.DrivingState.PARKED);
            MemoryConsentController.MutationResult disabled = controller
                    .setRetainedMemoryEnabled(
                            OWNER,
                            false,
                            MemoryConsentController.DrivingState.PARKED,
                            MemoryConsentController.MutationEvidence.forRetainedMemory(
                                    "probe.disable", OWNER, false, now, now + 30_000));
            MemoryConsentController.MutationResult cleared = controller
                    .clearProfilePreferences(
                            OWNER,
                            MemoryConsentController.DrivingState.PARKED,
                            MemoryConsentController.MutationEvidence.forPreferenceClear(
                                    "probe.clear", OWNER, now, now + 30_000));
            MemoryConsentController.MutationResult moving = controller
                    .setRetainedMemoryEnabled(
                            OWNER,
                            true,
                            MemoryConsentController.DrivingState.MOVING,
                            MemoryConsentController.MutationEvidence.forRetainedMemory(
                                    "probe.moving", OWNER, true, now, now + 30_000));
            boolean android13Arm64 = Build.VERSION.SDK_INT == 33
                    && Arrays.asList(Build.SUPPORTED_ABIS).contains("arm64-v8a");
            boolean sourceVisibility = initial.getSources().size()
                    == MemoryConsentController.SOURCE_COUNT;
            boolean disableVerified = disabled.getCode()
                    == MemoryConsentController.ResultCode.APPLIED;
            boolean clearVerified = cleared.getCode()
                    == MemoryConsentController.ResultCode.APPLIED;
            boolean movingRestricted = moving.getCode()
                    == MemoryConsentController.ResultCode.DRIVING_RESTRICTED;

            Log.i(TAG, "nonce=" + nonce
                    + " memory_consent_hmi_probe_complete=true"
                    + " memory_consent_source_visibility_verified=" + sourceVisibility
                    + " memory_consent_disable_verified=" + disableVerified
                    + " memory_consent_preference_clear_verified=" + clearVerified
                    + " memory_consent_moving_restriction_verified=" + movingRestricted
                    + " memory_consent_android13_arm64_verified=" + android13Arm64
                    + " memory_consent_hmi_projection_only=true"
                    + " memory_consent_repository_mutation_wired=false"
                    + " memory_consent_production_authority_wired=false"
                    + " memory_consent_runtime_wired=false"
                    + " memory_consent_model_context_published=false"
                    + " memory_consent_content_logged=false"
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
                    + " memory_consent_hmi_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " memory_consent_runtime_wired=false"
                    + " hardware_accessed=false");
        }
        finish();
    }

    private String nextRequestId(String prefix) {
        requestSequence++;
        return prefix + "." + requestSequence;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(11);
        button.setTextColor(Color.rgb(38, 52, 61));
        button.setAllCaps(false);
        button.setMinHeight(dp(48));
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
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(heightDp));
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
        background.setColor(Color.argb(220, 238, 242, 243));
        background.setCornerRadius(dp(8));
        return background;
    }

    private GradientDrawable sectionBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(150, 255, 255, 255));
        background.setCornerRadius(dp(6));
        return background;
    }

    private GradientDrawable actionBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(215, 255, 255, 255));
        background.setCornerRadius(dp(6));
        background.setStroke(dp(1), Color.argb(90, 38, 52, 61));
        return background;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
