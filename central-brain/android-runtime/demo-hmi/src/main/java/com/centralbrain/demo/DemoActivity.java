package com.centralbrain.demo;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.centralbrain.sdk.CentralBrainClient;
import com.centralbrain.sdk.CentralBrainGovernanceClient;
import com.centralbrain.sdk.CentralBrainSdk;
import com.centralbrain.sdk.governance.ActionDecision;
import com.centralbrain.sdk.governance.ActionRequest;
import com.centralbrain.sdk.governance.ApprovalHandle;
import com.centralbrain.sdk.governance.ApprovalStatus;
import com.centralbrain.sdk.governance.ICentralBrainGovernance;
import com.centralbrain.sdk.production.AgentTaskRequest;
import com.centralbrain.sdk.production.ICentralBrainRuntime;
import com.centralbrain.sdk.production.TaskFailure;
import com.centralbrain.sdk.production.TaskHandle;
import com.centralbrain.sdk.production.TaskResult;
import com.centralbrain.sdk.production.TaskUpdate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Req IDs: APP-004, XSC-001, XSC-006, NV-G-003, NV-G-006, DEL-001. */
public final class DemoActivity extends Activity {
    private static final String TAG = "CentralBrainGovernanceDemo";
    private static final int CONTENT_PADDING_DP = 32;
    private static final int MAX_RECONNECT_ATTEMPTS = 10;
    private static final long RECONNECT_DELAY_MS = 500L;

    private TextView protocolStatus;
    private TextView completionStatus;
    private TextView replayStatus;
    private TextView concurrentReplayStatus;
    private TextView cancellationStatus;
    private TextView governanceStatus;
    private CentralBrainClient client;
    private CentralBrainGovernanceClient governanceClient;
    private boolean demoStarted;
    private boolean governanceDemoStarted;
    private boolean destroyed;
    private int runtimeReconnectAttempts;
    private int governanceReconnectAttempts;
    private final Handler reconnectHandler = new Handler(Looper.getMainLooper());
    private final Runnable runtimeReconnectTask = this::retryRuntimeConnection;
    private final Runnable governanceReconnectTask = this::retryGovernanceConnection;
    private final ExecutorService replayExecutor = Executors.newFixedThreadPool(2);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int padding = Math.round(CONTENT_PADDING_DP * getResources().getDisplayMetrics().density);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding(padding, padding, padding, padding);
        content.setBackgroundColor(Color.rgb(245, 247, 248));

        TextView title = textView(30, Color.rgb(25, 31, 35));
        title.setText(R.string.app_name);
        content.addView(title, matchWidth());

        TextView maturity = textView(17, Color.rgb(67, 77, 84));
        maturity.setText(getString(
                R.string.foundation_status,
                CentralBrainSdk.SDK_VERSION,
                CentralBrainSdk.MATURITY));
        content.addView(maturity, spacedWidth(12));

        protocolStatus = textView(16, Color.rgb(43, 55, 61));
        protocolStatus.setText("Typed Binder: connecting");
        content.addView(protocolStatus, spacedWidth(22));

        completionStatus = textView(16, Color.rgb(43, 55, 61));
        completionStatus.setText("Completion: pending");
        content.addView(completionStatus, spacedWidth(8));

        replayStatus = textView(16, Color.rgb(43, 55, 61));
        replayStatus.setText(R.string.replay_pending);
        content.addView(replayStatus, spacedWidth(8));

        concurrentReplayStatus = textView(16, Color.rgb(43, 55, 61));
        concurrentReplayStatus.setText(R.string.concurrent_replay_pending);
        content.addView(concurrentReplayStatus, spacedWidth(8));

        cancellationStatus = textView(16, Color.rgb(43, 55, 61));
        cancellationStatus.setText("Cancel: pending");
        content.addView(cancellationStatus, spacedWidth(8));

        governanceStatus = textView(16, Color.rgb(43, 55, 61));
        governanceStatus.setText(R.string.governance_connecting);
        content.addView(governanceStatus, spacedWidth(8));

        setContentView(content);
        client = new CentralBrainClient(this, getMainExecutor(), connectionListener);
        client.connect();
        governanceClient = new CentralBrainGovernanceClient(
                this,
                getMainExecutor(),
                governanceConnectionListener);
        governanceClient.connect();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        reconnectHandler.removeCallbacks(runtimeReconnectTask);
        reconnectHandler.removeCallbacks(governanceReconnectTask);
        if (client != null) {
            client.close();
        }
        if (governanceClient != null) {
            governanceClient.close();
        }
        replayExecutor.shutdownNow();
        super.onDestroy();
    }

    private final CentralBrainClient.ConnectionListener connectionListener =
            new CentralBrainClient.ConnectionListener() {
                @Override
                public void onConnected(CentralBrainClient connectedClient) {
                    runtimeReconnectAttempts = 0;
                    reconnectHandler.removeCallbacks(runtimeReconnectTask);
                    try {
                        int version = connectedClient.getProtocolVersion();
                        String hash = connectedClient.getProtocolHash();
                        protocolStatus.setText(
                                "Typed Binder: connected v" + version + " " + hash.substring(0, 8));
                        if (demoStarted) {
                            return;
                        }
                        demoStarted = true;
                        submitCompletionTask(connectedClient);
                        submitCancellationTask(connectedClient);
                        submitConcurrentReplayTask(connectedClient);
                    } catch (RemoteException | RuntimeException exception) {
                        protocolStatus.setText("Typed Binder: failed " + exception.getClass().getSimpleName());
                    }
                }

                @Override
                public void onDisconnected() {
                    protocolStatus.setText("Typed Binder: disconnected");
                    scheduleRuntimeReconnect();
                }

                @Override
                public void onConnectionFailed(String reason) {
                    protocolStatus.setText("Typed Binder: failed " + reason);
                    scheduleRuntimeReconnect();
                }
            };

    private final CentralBrainGovernanceClient.ConnectionListener governanceConnectionListener =
            new CentralBrainGovernanceClient.ConnectionListener() {
                @Override
                public void onConnected(CentralBrainGovernanceClient connectedClient) {
                    governanceReconnectAttempts = 0;
                    reconnectHandler.removeCallbacks(governanceReconnectTask);
                    try {
                        if (governanceDemoStarted) {
                            int version = connectedClient.getProtocolVersion();
                            String hash = connectedClient.getProtocolHash();
                            if (version != ICentralBrainGovernance.INTERFACE_VERSION
                                    || !hash.equals(ICentralBrainGovernance.INTERFACE_HASH)) {
                                throw new IllegalStateException(
                                        "governance protocol verification failed after reconnect");
                            }
                            governanceStatus.setText(getString(
                                    R.string.governance_verified,
                                    version));
                            return;
                        }
                        runGovernanceProbe(connectedClient);
                        governanceDemoStarted = true;
                    } catch (RemoteException | RuntimeException exception) {
                        governanceStatus.setText(getString(
                                R.string.governance_failed,
                                exception.getClass().getSimpleName()));
                        Log.e(TAG, "governance_probe_complete=false", exception);
                    }
                }

                @Override
                public void onDisconnected() {
                    governanceStatus.setText(R.string.governance_disconnected);
                    scheduleGovernanceReconnect();
                }

                @Override
                public void onConnectionFailed(String reason) {
                    governanceStatus.setText(getString(R.string.governance_failed, reason));
                    scheduleGovernanceReconnect();
                }
            };

    private void scheduleRuntimeReconnect() {
        if (destroyed || client == null || client.isConnected()) {
            return;
        }
        if (runtimeReconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            protocolStatus.setText("Typed Binder: failed reconnect timeout");
            return;
        }
        reconnectHandler.removeCallbacks(runtimeReconnectTask);
        reconnectHandler.postDelayed(runtimeReconnectTask, RECONNECT_DELAY_MS);
    }

    private void retryRuntimeConnection() {
        if (destroyed || client == null || client.isConnected()) {
            return;
        }
        runtimeReconnectAttempts++;
        try {
            client.reconnect();
        } catch (IllegalStateException ignored) {
            return;
        }
        scheduleRuntimeReconnect();
    }

    private void scheduleGovernanceReconnect() {
        if (destroyed || governanceClient == null || governanceClient.isConnected()) {
            return;
        }
        if (governanceReconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            governanceStatus.setText(getString(
                    R.string.governance_failed,
                    "reconnect timeout"));
            return;
        }
        reconnectHandler.removeCallbacks(governanceReconnectTask);
        reconnectHandler.postDelayed(governanceReconnectTask, RECONNECT_DELAY_MS);
    }

    private void retryGovernanceConnection() {
        if (destroyed || governanceClient == null || governanceClient.isConnected()) {
            return;
        }
        governanceReconnectAttempts++;
        try {
            governanceClient.reconnect();
        } catch (IllegalStateException ignored) {
            return;
        }
        scheduleGovernanceReconnect();
    }

    private void runGovernanceProbe(CentralBrainGovernanceClient connectedClient)
            throws RemoteException {
        int version = connectedClient.getProtocolVersion();
        String hash = connectedClient.getProtocolHash();
        ActionDecision read = connectedClient.evaluateAction(actionRequest(
                "read",
                ICentralBrainGovernance.ACTION_VEHICLE_STATE_READ));
        ActionDecision comfort = connectedClient.evaluateAction(actionRequest(
                "comfort",
                ICentralBrainGovernance.ACTION_CABIN_TEMPERATURE_SET));
        ActionRequest highRiskRequest = actionRequest(
                "ota",
                ICentralBrainGovernance.ACTION_OTA_INSTALL);
        ActionDecision highRisk = connectedClient.evaluateAction(highRiskRequest);
        ApprovalHandle handle = connectedClient.requestApproval(highRiskRequest);
        ApprovalHandle replayedHandle = connectedClient.requestApproval(highRiskRequest);
        ApprovalStatus pending = connectedClient.getApprovalStatus(handle);
        boolean firstCancel = connectedClient.cancelApproval(handle);
        boolean secondCancel = connectedClient.cancelApproval(handle);
        ApprovalStatus cancelled = connectedClient.getApprovalStatus(handle);

        boolean verified = version == ICentralBrainGovernance.INTERFACE_VERSION
                && hash.equals(ICentralBrainGovernance.INTERFACE_HASH)
                && read.riskClass == ICentralBrainGovernance.RISK_READ_ONLY
                && read.outcome == ICentralBrainGovernance.DECISION_ALLOW_POLICY_ONLY
                && comfort.riskClass == ICentralBrainGovernance.RISK_COMFORT_CONTROL
                && comfort.outcome == ICentralBrainGovernance.DECISION_ALLOW_POLICY_ONLY
                && highRisk.riskClass == ICentralBrainGovernance.RISK_OTA
                && highRisk.outcome == ICentralBrainGovernance.DECISION_APPROVAL_REQUIRED
                && handle.approvalId.equals(replayedHandle.approvalId)
                && !highRisk.sourceHardwareBacked
                && !highRisk.sourceProductionTrusted
                && !highRisk.dispatchAllowed
                && pending.status == ICentralBrainGovernance.APPROVAL_STATUS_PENDING
                && !pending.grantSupported
                && pending.durable
                && !pending.dispatchAllowed
                && firstCancel
                && secondCancel
                && cancelled.status == ICentralBrainGovernance.APPROVAL_STATUS_CANCELLED
                && cancelled.durable;
        if (!verified) {
            throw new IllegalStateException("governance contract verification failed");
        }

        governanceStatus.setText(getString(R.string.governance_verified, version));
        Log.i(TAG, "governance_probe_complete=true"
                + " read_policy_only=true"
                + " comfort_policy_only=true"
                + " high_risk_approval_required=true"
                + " approval_pending=true"
                + " approval_idempotent_replay_verified=true"
                + " approval_cancelled=true"
                + " source_hardware_backed=false"
                + " source_production_trusted=false"
                + " approval_grant_supported=false"
                + " approval_durable=true"
                + " dispatch_allowed=false hardware_accessed=false");
    }

    private void submitCompletionTask(CentralBrainClient connectedClient) throws RemoteException {
        AgentTaskRequest request = request("completion", "demo typed binder");
        TaskHandle firstHandle = connectedClient.submitAgentTask(
                request,
                new CentralBrainClient.TaskCallback() {
            @Override
            public void onUpdate(TaskUpdate update) {
                completionStatus.setText("Completion: progress " + update.progressPercent + "%");
            }

            @Override
            public void onCompleted(TaskResult result) {
                completionStatus.setText("Typed Binder: completed");
            }

            @Override
            public void onFailed(TaskFailure failure) {
                completionStatus.setText("Completion: failed " + failure.errorCode);
            }
        });
        TaskHandle replayHandle = connectedClient.submitAgentTask(
                request,
                new CentralBrainClient.TaskCallback() {
                    @Override
                    public void onUpdate(TaskUpdate update) {
                        replayStatus.setText(R.string.replay_same_handle);
                    }

                    @Override
                    public void onCompleted(TaskResult result) {
                        replayStatus.setText(R.string.replay_completed);
                    }

                    @Override
                    public void onFailed(TaskFailure failure) {
                        replayStatus.setText(getString(R.string.replay_failed, failure.errorCode));
                    }
                });
        if (!firstHandle.taskId.equals(replayHandle.taskId)) {
            throw new IllegalStateException("idempotent replay returned a different task handle");
        }
    }

    private void submitCancellationTask(CentralBrainClient connectedClient) throws RemoteException {
        AgentTaskRequest request = request("cancel", "cancel this deterministic task");
        TaskHandle handle = connectedClient.submitAgentTask(
                request,
                new CentralBrainClient.TaskCallback() {
                    @Override
                    public void onUpdate(TaskUpdate update) {
                        if (update.state == ICentralBrainRuntime.TASK_STATE_CANCELLED) {
                            cancellationStatus.setText("Cancel: state confirmed");
                        }
                    }

                    @Override
                    public void onCompleted(TaskResult result) {
                        cancellationStatus.setText("Cancel: unexpected completion");
                    }

                    @Override
                    public void onFailed(TaskFailure failure) {
                        if (failure.errorCode == ICentralBrainRuntime.ERROR_CANCELLED) {
                            cancellationStatus.setText("Cancel: confirmed");
                        } else {
                            cancellationStatus.setText("Cancel: failed " + failure.errorCode);
                        }
                    }
                });
        boolean first = connectedClient.cancelTask(handle, ICentralBrainRuntime.CANCEL_REASON_USER);
        boolean second = connectedClient.cancelTask(handle, ICentralBrainRuntime.CANCEL_REASON_USER);
        if (!first || !second) {
            cancellationStatus.setText("Cancel: idempotency failed");
        }
    }

    private void submitConcurrentReplayTask(CentralBrainClient connectedClient) {
        AgentTaskRequest request = request("concurrent-replay", "concurrent replay task");
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<String> firstTaskId = new AtomicReference<>();
        AtomicReference<String> secondTaskId = new AtomicReference<>();
        AtomicInteger terminalCallbacks = new AtomicInteger();
        submitConcurrentReplay(
                connectedClient,
                request,
                start,
                firstTaskId,
                secondTaskId,
                terminalCallbacks,
                true);
        submitConcurrentReplay(
                connectedClient,
                request,
                start,
                firstTaskId,
                secondTaskId,
                terminalCallbacks,
                false);
        start.countDown();
    }

    private void submitConcurrentReplay(
            CentralBrainClient connectedClient,
            AgentTaskRequest request,
            CountDownLatch start,
            AtomicReference<String> firstTaskId,
            AtomicReference<String> secondTaskId,
            AtomicInteger terminalCallbacks,
            boolean first) {
        replayExecutor.execute(() -> {
            try {
                start.await();
                TaskHandle handle = connectedClient.submitAgentTask(
                        request,
                        new CentralBrainClient.TaskCallback() {
                            @Override
                            public void onUpdate(TaskUpdate update) {
                                // The terminal callback is the acceptance condition.
                            }

                            @Override
                            public void onCompleted(TaskResult result) {
                                int completed = terminalCallbacks.incrementAndGet();
                                if (completed == 2
                                        && firstTaskId.get() != null
                                        && firstTaskId.get().equals(secondTaskId.get())) {
                                    concurrentReplayStatus.setText(
                                            R.string.concurrent_replay_completed);
                                }
                            }

                            @Override
                            public void onFailed(TaskFailure failure) {
                                concurrentReplayStatus.setText(getString(
                                        R.string.concurrent_replay_failed,
                                        failure.errorCode));
                            }
                        });
                if (first) {
                    firstTaskId.set(handle.taskId);
                } else {
                    secondTaskId.set(handle.taskId);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                runOnUiThread(() -> concurrentReplayStatus.setText(
                        R.string.concurrent_replay_interrupted));
            } catch (RemoteException | RuntimeException exception) {
                runOnUiThread(() -> concurrentReplayStatus.setText(getString(
                        R.string.concurrent_replay_exception,
                        exception.getClass().getSimpleName())));
            }
        });
    }

    private static AgentTaskRequest request(String suffix, String utterance) {
        long now = SystemClock.elapsedRealtime();
        AgentTaskRequest request = new AgentTaskRequest();
        request.clientRequestId = "demo-" + suffix + "-" + now;
        request.sessionId = "demo-session";
        request.utterance = utterance;
        request.locale = "en-US";
        request.deadlineElapsedRealtimeMs = now + 5000;
        request.priority = 1;
        request.idempotencyKey = request.clientRequestId;
        return request;
    }

    private static ActionRequest actionRequest(String suffix, String actionId) {
        long now = SystemClock.elapsedRealtime();
        ActionRequest request = new ActionRequest();
        request.schemaVersion = 1;
        request.clientRequestId = "governance-" + suffix + "-" + now;
        request.actionId = actionId;
        request.idempotencyKey = request.clientRequestId;
        return request;
    }

    private TextView textView(int sizeSp, int color) {
        TextView view = new TextView(this);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        return view;
    }

    private static LinearLayout.LayoutParams matchWidth() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams spacedWidth(int topMarginDp) {
        LinearLayout.LayoutParams params = matchWidth();
        params.topMargin = Math.round(
                topMarginDp * getResources().getDisplayMetrics().density);
        return params;
    }
}
