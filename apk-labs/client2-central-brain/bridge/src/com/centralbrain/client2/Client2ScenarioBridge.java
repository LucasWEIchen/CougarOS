package com.centralbrain.client2;

import android.app.Activity;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

import com.centralbrain.sdk.CentralBrainClient;
import com.centralbrain.sdk.production.AgentTaskRequest;
import com.centralbrain.sdk.production.ICentralBrainRuntime;
import com.centralbrain.sdk.production.TaskFailure;
import com.centralbrain.sdk.production.TaskHandle;
import com.centralbrain.sdk.production.TaskResult;
import com.centralbrain.sdk.production.TaskUpdate;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Typed Binder bridge embedded into the isolated Client2 debug APK as classes2.dex. */
public final class Client2ScenarioBridge {
    private static final String TAG = "CbClient2Binder";
    private static final long DEADLINE_MS = 10_000L;
    private static final Set<String> SCENARIOS = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList(
                    "care.cold",
                    "care.fatigue",
                    "task.home",
                    "skill.nap",
                    "state.vehicle",
                    "memory.preference",
                    "skills.catalog",
                    "governance.audit",
                    "security.denied",
                    "security.privacy",
                    "runtime.npu",
                    "system.overview")));

    private Client2ScenarioBridge() {
    }

    public static boolean submit(
            Activity activity,
            String scenarioId,
            String userText,
            ScenarioCallback callback) {
        if (activity == null || callback == null || !SCENARIOS.contains(scenarioId)) {
            if (callback != null) {
                callback.onBridgeFailure("unsupported scenario");
            }
            return false;
        }
        String boundedText = userText == null ? "" : userText.trim();
        if (boundedText.length() > 256) {
            boundedText = boundedText.substring(0, 256);
        }
        return new Submission(activity, scenarioId, boundedText, callback).start();
    }

    private static final class Submission implements
            CentralBrainClient.ConnectionListener,
            CentralBrainClient.TaskCallback {
        private final Activity activity;
        private final String scenarioId;
        private final String userText;
        private final ScenarioCallback callback;
        private final AtomicBoolean terminal = new AtomicBoolean();

        private CentralBrainClient client;

        Submission(
                Activity activity,
                String scenarioId,
                String userText,
                ScenarioCallback callback) {
            this.activity = activity;
            this.scenarioId = scenarioId;
            this.userText = userText;
            this.callback = callback;
        }

        boolean start() {
            try {
                callback.onBridgeStatus("Binder connecting: " + scenarioId);
                client = new CentralBrainClient(activity, activity.getMainExecutor(), this);
                client.connect();
                return true;
            } catch (RuntimeException exception) {
                finishFailure("Binder setup failed: " + exception.getClass().getSimpleName());
                return false;
            }
        }

        @Override
        public void onConnected(CentralBrainClient connectedClient) {
            try {
                if (connectedClient.getProtocolVersion() != 1
                        || !ICentralBrainRuntime.INTERFACE_HASH.equals(
                                connectedClient.getProtocolHash())) {
                    finishFailure("Binder protocol mismatch");
                    return;
                }
                AgentTaskRequest request = request();
                TaskHandle handle = connectedClient.submitAgentTask(request, this);
                callback.onBridgeStatus("Binder submitted: " + scenarioId);
                Log.i(TAG, "client2_binder_connected=true"
                        + " client2_binder_task_submitted=true"
                        + " scenario_id=" + scenarioId
                        + " task_id_present=" + (handle.taskId != null)
                        + " http_transport_used=false"
                        + " hardware_accessed=false");
            } catch (RemoteException | RuntimeException exception) {
                finishFailure("Binder submit failed: " + exception.getClass().getSimpleName());
            }
        }

        @Override
        public void onDisconnected() {
            finishFailure("Runtime Binder disconnected");
        }

        @Override
        public void onConnectionFailed(String reason) {
            finishFailure("Runtime Binder unavailable: " + safe(reason));
        }

        @Override
        public void onUpdate(TaskUpdate update) {
            if (!terminal.get() && update != null) {
                callback.onBridgeStatus("Binder progress: " + update.progressPercent + "%");
            }
        }

        @Override
        public void onCompleted(TaskResult result) {
            String reply = "Binder task completed";
            if (result != null && hasText(result.replyText)) {
                reply = result.replyText;
            } else if (result != null && hasText(result.summary)) {
                reply = result.summary;
            }
            if (terminal.compareAndSet(false, true)) {
                try {
                    callback.onBridgeReply(reply);
                    Log.i(TAG, "client2_binder_task_completed=true"
                            + " scenario_id=" + scenarioId
                            + " reply_present=" + hasText(reply)
                            + " http_transport_used=false"
                            + " service_dispatch_triggered=false"
                            + " hardware_accessed=false");
                } finally {
                    closeClient();
                }
            }
        }

        @Override
        public void onFailed(TaskFailure failure) {
            String detail = failure == null
                    ? "unknown task failure"
                    : "task error " + failure.errorCode + ": " + safe(failure.errorMessage);
            finishFailure(detail);
        }

        private AgentTaskRequest request() {
            long now = SystemClock.elapsedRealtime();
            String requestId = "client2-" + scenarioId.replace('.', '-') + "-" + now;
            AgentTaskRequest request = new AgentTaskRequest();
            request.clientRequestId = requestId;
            request.sessionId = "client2-binder-demo";
            request.utterance = scenarioId + ": " + userText;
            request.locale = "zh-CN";
            request.deadlineElapsedRealtimeMs = now + DEADLINE_MS;
            request.priority = 1;
            request.idempotencyKey = requestId;
            return request;
        }

        private void finishFailure(String reason) {
            if (terminal.compareAndSet(false, true)) {
                try {
                    callback.onBridgeFailure(safe(reason));
                    Log.e(TAG, "client2_binder_task_failed=true"
                            + " scenario_id=" + scenarioId
                            + " http_transport_used=false"
                            + " hardware_accessed=false"
                            + " reason=" + safe(reason));
                } finally {
                    closeClient();
                }
            }
        }

        private void closeClient() {
            CentralBrainClient current = client;
            client = null;
            if (current != null) {
                current.close();
            }
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
    }
}
