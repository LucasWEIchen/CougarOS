package com.centralbrain.demo;

import android.app.Activity;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;

import com.centralbrain.sdk.CentralBrainClient;
import com.centralbrain.sdk.production.AgentTaskRequest;
import com.centralbrain.sdk.production.TaskFailure;
import com.centralbrain.sdk.production.TaskHandle;
import com.centralbrain.sdk.production.TaskResult;
import com.centralbrain.sdk.production.TaskUpdate;

/** ADB-only client-process death probe. Req IDs: XSC-006, NV-G-006, DEL-004. */
public final class ClientDeathProbeActivity extends Activity {
    private static final String TAG = "CentralBrainDeathProbe";

    private CentralBrainClient client;
    private String nonce = "missing";
    private boolean submitted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!BuildConfig.DEBUG) {
            throw new IllegalStateException("Client death probe must never run in a release build");
        }
        String requestedNonce = getIntent().getStringExtra("nonce");
        nonce = requestedNonce == null ? "missing" : requestedNonce;
        setContentView(new View(this));
        client = new CentralBrainClient(this, getMainExecutor(), connectionListener);
        if (!client.connect()) {
            Log.e(TAG, "nonce=" + nonce + " client_death_probe_submitted=false bind=false");
            finish();
        }
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
                    if (submitted) {
                        return;
                    }
                    submitted = true;
                    try {
                        TaskHandle handle = connectedClient.submitAgentTask(
                                request(),
                                taskCallback);
                        Log.i(TAG, "nonce=" + nonce
                                + " client_death_probe_submitted=true taskId=" + handle.taskId
                                + " hardware_accessed=false");
                    } catch (RemoteException | RuntimeException exception) {
                        Log.e(TAG, "nonce=" + nonce
                                + " client_death_probe_submitted=false", exception);
                        finish();
                    }
                }

                @Override
                public void onDisconnected() {
                    Log.w(TAG, "nonce=" + nonce + " client_death_probe_disconnected=true");
                }

                @Override
                public void onConnectionFailed(String reason) {
                    Log.e(TAG, "nonce=" + nonce
                            + " client_death_probe_submitted=false reason=" + reason);
                    finish();
                }
            };

    private final CentralBrainClient.TaskCallback taskCallback =
            new CentralBrainClient.TaskCallback() {
                @Override
                public void onUpdate(TaskUpdate update) {
                    // The host kills this process before the debug task reaches a terminal state.
                }

                @Override
                public void onCompleted(TaskResult result) {
                    Log.e(TAG, "nonce=" + nonce + " client_death_probe_completed_unexpectedly=true");
                    finish();
                }

                @Override
                public void onFailed(TaskFailure failure) {
                    Log.e(TAG, "nonce=" + nonce
                            + " client_death_probe_failed_unexpectedly=" + failure.errorCode);
                    finish();
                }
            };

    private AgentTaskRequest request() {
        long now = SystemClock.elapsedRealtime();
        AgentTaskRequest request = new AgentTaskRequest();
        request.clientRequestId = "client-death-" + nonce;
        request.sessionId = "r2c-client-death";
        request.utterance = "hold deterministic task for client death verification";
        request.locale = "en-US";
        request.deadlineElapsedRealtimeMs = now + 10000;
        request.priority = 1;
        request.idempotencyKey = request.clientRequestId;
        return request;
    }
}
