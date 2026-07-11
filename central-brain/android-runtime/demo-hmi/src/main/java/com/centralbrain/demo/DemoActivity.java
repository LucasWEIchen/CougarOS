package com.centralbrain.demo;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.centralbrain.sdk.CentralBrainClient;
import com.centralbrain.sdk.CentralBrainSdk;
import com.centralbrain.sdk.production.AgentTaskRequest;
import com.centralbrain.sdk.production.ICentralBrainRuntime;
import com.centralbrain.sdk.production.TaskFailure;
import com.centralbrain.sdk.production.TaskHandle;
import com.centralbrain.sdk.production.TaskResult;
import com.centralbrain.sdk.production.TaskUpdate;

/** Req IDs: APP-004, XSC-001, XSC-006, NV-G-003, NV-G-006, DEL-001. */
public final class DemoActivity extends Activity {
    private static final int CONTENT_PADDING_DP = 32;

    private TextView protocolStatus;
    private TextView completionStatus;
    private TextView cancellationStatus;
    private CentralBrainClient client;
    private boolean demoStarted;

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

        cancellationStatus = textView(16, Color.rgb(43, 55, 61));
        cancellationStatus.setText("Cancel: pending");
        content.addView(cancellationStatus, spacedWidth(8));

        setContentView(content);
        client = new CentralBrainClient(this, getMainExecutor(), connectionListener);
        client.connect();
    }

    @Override
    protected void onDestroy() {
        if (client != null) {
            client.close();
        }
        super.onDestroy();
    }

    private final CentralBrainClient.ConnectionListener connectionListener =
            new CentralBrainClient.ConnectionListener() {
                @Override
                public void onConnected(CentralBrainClient connectedClient) {
                    if (demoStarted) {
                        return;
                    }
                    demoStarted = true;
                    try {
                        int version = connectedClient.getProtocolVersion();
                        String hash = connectedClient.getProtocolHash();
                        protocolStatus.setText(
                                "Typed Binder: connected v" + version + " " + hash.substring(0, 8));
                        submitCompletionTask(connectedClient);
                        submitCancellationTask(connectedClient);
                    } catch (RemoteException | RuntimeException exception) {
                        protocolStatus.setText("Typed Binder: failed " + exception.getClass().getSimpleName());
                    }
                }

                @Override
                public void onDisconnected() {
                    protocolStatus.setText("Typed Binder: disconnected");
                }

                @Override
                public void onConnectionFailed(String reason) {
                    protocolStatus.setText("Typed Binder: failed " + reason);
                }
            };

    private void submitCompletionTask(CentralBrainClient connectedClient) throws RemoteException {
        AgentTaskRequest request = request("completion", "demo typed binder");
        connectedClient.submitAgentTask(request, new CentralBrainClient.TaskCallback() {
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
