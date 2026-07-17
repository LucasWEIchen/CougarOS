package com.centralbrain.client2;

import android.app.Activity;
import android.app.Application;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.centralbrain.sdk.event.RuntimeEvent;
import com.centralbrain.sdk.session.SessionContract;
import com.centralbrain.sdk.session.SessionHandle;
import com.centralbrain.sdk.session.SessionSnapshot;

import java.lang.ref.WeakReference;

/** Maintained Java owner for Client2 overlay state, rendering and Session lifecycle. */
public final class CockpitControlCoordinator implements
        View.OnClickListener,
        ScenarioCallback,
        Application.ActivityLifecycleCallbacks {
    private static final String TAG = "CbClient2Hmi";
    private static final String MENU_TAG = "central_brain_menu_toggle";
    private static final String PREFS_NAME = "central_brain_hmi_state_v1";
    private static final int CHECKPOINT_SCHEMA = 1;
    private static final Object ACTIVE_LOCK = new Object();

    private static WeakReference<CockpitControlCoordinator> active = new WeakReference<>(null);
    private static CockpitHmiState retainedState = CockpitHmiState.initial();

    private final Activity activity;
    private final Application application;
    private final SharedPreferences preferences;

    private CockpitHmiState state;
    private Client2ScenarioBridge.SessionConnection connection;
    private TextView replyView;
    private View panelOverlay;
    private boolean detached;

    private CockpitControlCoordinator(Activity activity) {
        this.activity = activity;
        this.application = activity.getApplication();
        this.preferences = activity.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);
        synchronized (ACTIVE_LOCK) {
            state = retainedState.getRevision() > 0
                    ? retainedState
                    : restoreCheckpoint();
        }
    }

    /** Called by the one-line MainActivity smali hook after setContentView. */
    public static void install(Activity activity) {
        if (activity == null) {
            return;
        }
        CockpitControlCoordinator coordinator = new CockpitControlCoordinator(activity);
        CockpitControlCoordinator previous;
        synchronized (ACTIVE_LOCK) {
            previous = active.get();
            active = new WeakReference<>(coordinator);
        }
        if (previous != null) {
            previous.detach(false);
        }
        coordinator.attach();
    }

    private void attach() {
        application.registerActivityLifecycleCallbacks(this);
        bindViews();
        render();
        boolean resume = state.hasResumableSession(System.currentTimeMillis());
        Log.i(TAG, markers()
                + " client2_hmi_coordinator_installed=true"
                + " client2_hmi_state_restored=" + (state.getRevision() > 0)
                + " client2_hmi_resume_available=" + resume
                + " client2_hmi_checkpoint_text_persisted=false");
        if (resume) {
            resumeSession();
        }
    }

    private void bindViews() {
        View controls = findView("centralBrainControlGroup");
        if (controls != null) {
            bindButtons(controls);
        }
        View reply = findView("centralBrainReplyText");
        if (reply instanceof TextView) {
            replyView = (TextView) reply;
        }
        panelOverlay = findView("centralBrainPanelOverlay");
        if (panelOverlay != null) {
            panelOverlay.setOnClickListener(this);
        }
        View navigationTrigger = findView("centralBrainNavigationTrigger");
        if (navigationTrigger != null) {
            navigationTrigger.setOnClickListener(this);
        }
    }

    private View findView(String name) {
        int id = activity.getResources().getIdentifier(
                name,
                "id",
                activity.getPackageName());
        return id == 0 ? null : activity.findViewById(id);
    }

    private void bindButtons(View view) {
        if (view instanceof Button) {
            Button button = (Button) view;
            button.setBackgroundTintList(null);
            button.setOnClickListener(this);
            return;
        }
        if (!(view instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            bindButtons(group.getChildAt(index));
        }
    }

    @Override
    public void onClick(View view) {
        if (view == null || detached) {
            return;
        }
        if (view == panelOverlay) {
            setPanelVisible(false);
            return;
        }
        Object tag = view.getTag();
        if (MENU_TAG.equals(tag)) {
            setPanelVisible(
                    state.getPanelVisibility() != CockpitHmiState.PanelVisibility.VISIBLE);
            return;
        }
        if (!(view instanceof TextView)) {
            return;
        }
        String scenarioId = tag == null ? "" : tag.toString();
        CharSequence text = ((TextView) view).getText();
        startScenario(scenarioId, text == null ? "" : text.toString());
    }

    private void setPanelVisible(boolean visible) {
        accept(CockpitHmiReducer.Event.panelVisibility(visible));
        if (!visible) {
            Log.i(TAG, markers()
                    + " client2_hmi_hidden_state_preserved=true");
        }
    }

    private void startScenario(String scenarioId, String userText) {
        if (scenarioId == null || scenarioId.isEmpty()) {
            return;
        }
        closeCurrentConnection(true);
        accept(CockpitHmiReducer.Event.scenarioSubmitted(scenarioId));
        Client2ScenarioBridge.SessionConnection opened =
                Client2ScenarioBridge.openSession(activity, scenarioId, userText, this);
        attachConnection(opened);
    }

    private void resumeSession() {
        SessionHandle handle = state.toSessionHandle();
        if (handle == null || state.getUiScenarioId().isEmpty()) {
            return;
        }
        Log.i(TAG, markers()
                + " client2_hmi_session_resume_requested=true"
                + " resume_cursor_present=" + !state.getResumeCursor().isEmpty());
        Client2ScenarioBridge.SessionConnection resumed =
                Client2ScenarioBridge.resumeSession(
                        activity,
                        state.getUiScenarioId(),
                        handle,
                        state.getResumeCursor(),
                        this);
        attachConnection(resumed);
    }

    private synchronized void attachConnection(
            Client2ScenarioBridge.SessionConnection candidate) {
        if (detached) {
            if (candidate != null) {
                candidate.close();
            }
            return;
        }
        connection = candidate;
    }

    private void closeCurrentConnection(boolean replacing) {
        Client2ScenarioBridge.SessionConnection previous;
        synchronized (this) {
            previous = connection;
            connection = null;
        }
        if (previous != null) {
            previous.close();
            if (replacing) {
                Log.i(TAG, markers()
                        + " client2_hmi_session_replaced=true");
            }
        }
    }

    @Override
    public void onSessionConnectionChanged(boolean connected, boolean reconnected) {
        accept(CockpitHmiReducer.Event.connectionChanged(connected, reconnected));
        Log.i(TAG, markers()
                + " client2_hmi_connection_changed=true"
                + " connected=" + connected
                + " reconnected=" + reconnected);
    }

    @Override
    public void onSessionOpened(SessionHandle handle, String scenarioId) {
        accept(CockpitHmiReducer.Event.sessionOpened(handle, scenarioId));
    }

    @Override
    public void onSessionSnapshot(SessionSnapshot snapshot) {
        accept(CockpitHmiReducer.Event.snapshot(snapshot));
    }

    @Override
    public void onSessionEvent(RuntimeEvent event) {
        CockpitHmiState before;
        synchronized (this) {
            before = state;
        }
        accept(CockpitHmiReducer.Event.runtimeEvent(event));
        synchronized (this) {
            if (state == before) {
                Log.i(TAG, markers()
                        + " client2_hmi_duplicate_event_suppressed=true");
            }
        }
    }

    @Override
    public void onSessionReplayComplete(SessionHandle handle, long lastSequence) {
        accept(CockpitHmiReducer.Event.replayComplete(handle, lastSequence));
        Log.i(TAG, markers()
                + " client2_hmi_replay_projected=true");
    }

    @Override
    public void onSessionOverflow(SessionHandle handle, String resumeCursor) {
        accept(CockpitHmiReducer.Event.overflow(handle, resumeCursor));
    }

    @Override
    public void onSessionClosed(
            SessionHandle handle,
            int reasonCode,
            String resumeCursor) {
        accept(CockpitHmiReducer.Event.streamClosed(handle, reasonCode, resumeCursor));
    }

    @Override
    public void onSessionError(SessionHandle handle, String code, String message) {
        accept(CockpitHmiReducer.Event.failure(handle, code, message));
    }

    private void accept(CockpitHmiReducer.Event event) {
        CockpitHmiState next;
        synchronized (this) {
            if (detached) {
                return;
            }
            next = CockpitHmiReducer.reduce(state, event);
            if (next == state) {
                return;
            }
            state = next;
            synchronized (ACTIVE_LOCK) {
                retainedState = next;
            }
        }
        persistCheckpoint(next.checkpoint());
        render();
        Log.i(TAG, markers()
                + " client2_hmi_state_reduced=true"
                + " hmi_revision=" + next.getRevision()
                + " hmi_connection=" + next.getConnectionState()
                + " hmi_last_event_sequence=" + next.getLastEventSequence());
    }

    private void render() {
        if (detached) {
            return;
        }
        activity.runOnUiThread(() -> {
            if (detached) {
                return;
            }
            CockpitHmiState current;
            synchronized (CockpitControlCoordinator.this) {
                current = state;
            }
            if (panelOverlay != null) {
                panelOverlay.setVisibility(
                        current.getPanelVisibility() == CockpitHmiState.PanelVisibility.VISIBLE
                                ? View.VISIBLE : View.GONE);
            }
            if (replyView != null) {
                replyView.setText(current.renderText());
            }
        });
    }

    private CockpitHmiState restoreCheckpoint() {
        if (preferences.getInt("schema", 0) != CHECKPOINT_SCHEMA) {
            return CockpitHmiState.initial();
        }
        try {
            CockpitHmiState.Checkpoint checkpoint = new CockpitHmiState.Checkpoint(
                    preferences.getBoolean("panel_visible", false),
                    preferences.getString("ui_scenario", ""),
                    preferences.getString("canonical_scenario", ""),
                    preferences.getInt("handle_schema", SessionContract.SCHEMA_VERSION),
                    preferences.getString("session_id", ""),
                    preferences.getLong("accepted_at", 0),
                    preferences.getLong("expires_at", 0),
                    preferences.getLong("last_sequence", 0),
                    preferences.getString("resume_cursor", ""));
            if (checkpoint.hasSession()) {
                SessionHandle handle = new SessionHandle();
                handle.schemaVersion = checkpoint.handleSchemaVersion;
                handle.sessionId = checkpoint.sessionId;
                handle.acceptedAtEpochMs = checkpoint.acceptedAtEpochMs;
                handle.expiresAtEpochMs = checkpoint.expiresAtEpochMs;
                SessionContract.validateHandle(handle);
                if (checkpoint.uiScenarioId.isEmpty()
                        || checkpoint.canonicalScenarioId.isEmpty()
                        || handle.expiresAtEpochMs < System.currentTimeMillis()) {
                    throw new IllegalArgumentException("stale HMI checkpoint");
                }
            }
            return CockpitHmiReducer.reduce(
                    CockpitHmiState.initial(),
                    CockpitHmiReducer.Event.restored(checkpoint));
        } catch (RuntimeException invalid) {
            preferences.edit().clear().apply();
            return CockpitHmiState.initial();
        }
    }

    private void persistCheckpoint(CockpitHmiState.Checkpoint checkpoint) {
        preferences.edit()
                .putInt("schema", CHECKPOINT_SCHEMA)
                .putBoolean("panel_visible", checkpoint.panelVisible)
                .putString("ui_scenario", checkpoint.uiScenarioId)
                .putString("canonical_scenario", checkpoint.canonicalScenarioId)
                .putInt("handle_schema", checkpoint.handleSchemaVersion)
                .putString("session_id", checkpoint.sessionId)
                .putLong("accepted_at", checkpoint.acceptedAtEpochMs)
                .putLong("expires_at", checkpoint.expiresAtEpochMs)
                .putLong("last_sequence", checkpoint.lastEventSequence)
                .putString("resume_cursor", checkpoint.resumeCursor)
                .apply();
    }

    private void detach(boolean lifecycleDestroy) {
        Client2ScenarioBridge.SessionConnection previous;
        CockpitHmiState detachedState;
        synchronized (this) {
            if (detached) {
                return;
            }
            detachedState = CockpitHmiReducer.reduce(
                    state,
                    CockpitHmiReducer.Event.detached());
            state = detachedState;
            detached = true;
            previous = connection;
            connection = null;
        }
        synchronized (ACTIVE_LOCK) {
            retainedState = detachedState;
            if (active.get() == this) {
                active = new WeakReference<>(null);
            }
        }
        persistCheckpoint(detachedState.checkpoint());
        if (previous != null) {
            previous.close();
        }
        application.unregisterActivityLifecycleCallbacks(this);
        Log.i(TAG, markers()
                + " client2_hmi_lifecycle_detached=true"
                + " lifecycle_destroy=" + lifecycleDestroy
                + " client2_hmi_state_retained=true");
    }

    private String markers() {
        return "cockpit_hmi_state_reducer_implemented=true"
                + " cockpit_hmi_lifecycle_owner_java=true"
                + " legacy_text_callback_authoritative=false"
                + " scenario_execution_enabled=false"
                + " service_dispatch_triggered=false"
                + " hardware_accessed=false";
    }

    @Override
    public void onActivityDestroyed(Activity destroyedActivity) {
        if (destroyedActivity == activity) {
            detach(true);
        }
    }

    @Override
    public void onActivitySaveInstanceState(Activity savedActivity, Bundle outState) {
        if (savedActivity == activity) {
            persistCheckpoint(state.checkpoint());
        }
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) {}
    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityResumed(Activity activity) {}
    @Override public void onActivityPaused(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
}
