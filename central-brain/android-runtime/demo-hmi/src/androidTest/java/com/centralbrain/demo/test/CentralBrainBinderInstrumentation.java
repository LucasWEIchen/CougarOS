package com.centralbrain.demo.test;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** API 33 R2C Binder lifecycle/race instrumentation. Req IDs: XSC-006, NV-G-006. */
public final class CentralBrainBinderInstrumentation extends Instrumentation {
    private static final String TAG = "CentralBrainBinderTest";
    private static final long WAIT_SECONDS = 10;
    private static final int RACE_TASK_COUNT = 15;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override
    public void onStart() {
        Log.i(TAG, "R2C Binder instrumentation started");
        runChecks();
    }

    private void runChecks() {
        Bundle result = new Bundle();
        try {
            Log.i(TAG, "verifying service death and reconnect");
            verifyServiceDeathAndReconnect();
            Log.i(TAG, "verifying cancel-completion race");
            verifyCancelCompletionRace();
            result.putString(
                    "stream",
                    "\nbinder_service_death_verified=true"
                            + "\nbinder_reconnect_verified=true"
                            + "\ndurable_recovery_pending_verified=true"
                            + "\nbinder_terminal_uniqueness_verified=true"
                            + "\nbinder_cancel_completion_race_verified=true"
                            + "\nhardware_accessed=false\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            Log.e(TAG, "R2C Binder instrumentation failed", failure);
            result.putString("stream", "\nR2C Binder instrumentation failed:\n"
                    + Log.getStackTraceString(failure));
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void verifyServiceDeathAndReconnect() throws Exception {
        shell("am force-stop com.centralbrain.runtime");
        ExecutorService callbacks = Executors.newFixedThreadPool(4);
        ConnectionRecorder connections = new ConnectionRecorder();
        CentralBrainClient client = new CentralBrainClient(
                getTargetContext(),
                callbacks,
                connections);
        try {
            assertTrue(client.connect(), "initial bindService returned false");
            await(connections.firstConnected, "initial connection");
            assertNull(connections.failure.get(), "initial connection failed");

            CountDownLatch terminal = new CountDownLatch(1);
            AtomicInteger terminalCount = new AtomicInteger();
            AtomicInteger failureCode = new AtomicInteger(-1);
            AgentTaskRequest interruptedRequest = request("service-death");
            TaskHandle interruptedHandle = client.submitAgentTask(
                    interruptedRequest,
                    new CentralBrainClient.TaskCallback() {
                        @Override
                        public void onUpdate(TaskUpdate update) {
                            // A non-terminal update may arrive before the process is stopped.
                        }

                        @Override
                        public void onCompleted(TaskResult taskResult) {
                            terminalCount.incrementAndGet();
                            terminal.countDown();
                        }

                        @Override
                        public void onFailed(TaskFailure failure) {
                            failureCode.set(failure.errorCode);
                            terminalCount.incrementAndGet();
                            terminal.countDown();
                        }
                    });

            shell("am force-stop com.centralbrain.runtime");
            await(terminal, "SERVICE_DIED terminal callback");
            await(connections.disconnected, "single disconnect callback");
            Thread.sleep(300);
            assertEquals(
                    ICentralBrainRuntime.ERROR_SERVICE_DIED,
                    failureCode.get(),
                    "active task did not fail with SERVICE_DIED");
            assertEquals(1, terminalCount.get(), "service death emitted multiple terminal callbacks");
            assertEquals(1, connections.disconnectedCount.get(),
                    "service death emitted multiple disconnect callbacks");

            assertTrue(client.reconnect(), "explicit reconnect bindService returned false");
            await(connections.reconnected, "explicit reconnect");
            assertNull(connections.failure.get(), "explicit reconnect failed");
            assertTrue(client.isConnected(), "client is not connected after explicit reconnect");

            CountDownLatch replayTerminal = new CountDownLatch(1);
            AtomicInteger replayFailureCode = new AtomicInteger(-1);
            AtomicBoolean replayRetryable = new AtomicBoolean();
            TaskHandle replayHandle = client.submitAgentTask(
                    interruptedRequest,
                    new CentralBrainClient.TaskCallback() {
                        @Override
                        public void onUpdate(TaskUpdate update) {
                            // The persisted state may be ACCEPTED or RUNNING at process death.
                        }

                        @Override
                        public void onCompleted(TaskResult taskResult) {
                            replayTerminal.countDown();
                        }

                        @Override
                        public void onFailed(TaskFailure failure) {
                            replayFailureCode.set(failure.errorCode);
                            replayRetryable.set(failure.retryable);
                            replayTerminal.countDown();
                        }
                    });
            await(replayTerminal, "durable recovery-pending replay");
            assertEquals(interruptedHandle.taskId, replayHandle.taskId,
                    "durable replay returned a different task handle");
            assertEquals(ICentralBrainRuntime.ERROR_INTERNAL, replayFailureCode.get(),
                    "unrecovered durable replay did not fail explicitly");
            assertTrue(replayRetryable.get(),
                    "unrecovered durable replay failure was not retryable");

            CountDownLatch recoveryTerminal = new CountDownLatch(1);
            AtomicInteger recoveryCount = new AtomicInteger();
            AtomicReference<Throwable> recoveryFailure = new AtomicReference<>();
            client.submitAgentTask(
                    request("recovery"),
                    new CentralBrainClient.TaskCallback() {
                        @Override
                        public void onUpdate(TaskUpdate update) {
                            // Progress is not the terminal assertion.
                        }

                        @Override
                        public void onCompleted(TaskResult taskResult) {
                            recoveryCount.incrementAndGet();
                            recoveryTerminal.countDown();
                        }

                        @Override
                        public void onFailed(TaskFailure failure) {
                            recoveryFailure.compareAndSet(null, new AssertionError(
                                    "recovery task failed with code " + failure.errorCode));
                            recoveryCount.incrementAndGet();
                            recoveryTerminal.countDown();
                        }
                    });
            await(recoveryTerminal, "post-reconnect completion");
            assertNull(recoveryFailure.get(), "post-reconnect task failed");
            assertEquals(1, recoveryCount.get(),
                    "post-reconnect task emitted multiple terminal callbacks");
        } finally {
            client.close();
            callbacks.shutdownNow();
        }
    }

    private void verifyCancelCompletionRace() throws Exception {
        shell("am force-stop com.centralbrain.runtime");
        ExecutorService callbacks = Executors.newFixedThreadPool(6);
        ScheduledExecutorService cancellers = Executors.newScheduledThreadPool(5);
        ConnectionRecorder connections = new ConnectionRecorder();
        CentralBrainClient client = new CentralBrainClient(
                getTargetContext(),
                callbacks,
                connections);
        CountDownLatch terminals = new CountDownLatch(RACE_TASK_COUNT);
        CountDownLatch cancellationCalls = new CountDownLatch(RACE_TASK_COUNT);
        AtomicInteger completed = new AtomicInteger();
        AtomicInteger cancelled = new AtomicInteger();
        AtomicReference<Throwable> unexpected = new AtomicReference<>();
        TaskObservation[] observations = new TaskObservation[RACE_TASK_COUNT];

        try {
            assertTrue(client.connect(), "race bindService returned false");
            await(connections.firstConnected, "race connection");
            assertNull(connections.failure.get(), "race connection failed");

            for (int index = 0; index < RACE_TASK_COUNT; index++) {
                TaskObservation observation = new TaskObservation(terminals, unexpected);
                observations[index] = observation;
                TaskHandle handle = client.submitAgentTask(
                        request("race-" + index),
                        new CentralBrainClient.TaskCallback() {
                            @Override
                            public void onUpdate(TaskUpdate update) {
                                observation.recordUpdate();
                            }

                            @Override
                            public void onCompleted(TaskResult result) {
                                completed.incrementAndGet();
                                observation.recordTerminal();
                            }

                            @Override
                            public void onFailed(TaskFailure failure) {
                                if (failure.errorCode != ICentralBrainRuntime.ERROR_CANCELLED) {
                                    unexpected.compareAndSet(null, new AssertionError(
                                            "race task failed with code " + failure.errorCode));
                                }
                                cancelled.incrementAndGet();
                                observation.recordTerminal();
                            }
                        });

                long cancelDelayMs = 2950L + (index % 5) * 50L;
                cancellers.schedule(() -> {
                    try {
                        boolean first = client.cancelTask(
                                handle,
                                ICentralBrainRuntime.CANCEL_REASON_USER);
                        boolean second = client.cancelTask(
                                handle,
                                ICentralBrainRuntime.CANCEL_REASON_USER);
                        if (first != second) {
                            unexpected.compareAndSet(null, new AssertionError(
                                    "duplicate cancel returned inconsistent outcomes"));
                        }
                    } catch (Throwable failure) {
                        unexpected.compareAndSet(null, failure);
                    } finally {
                        cancellationCalls.countDown();
                    }
                }, cancelDelayMs, TimeUnit.MILLISECONDS);
            }

            await(terminals, "cancel-completion terminal callbacks");
            await(cancellationCalls, "duplicate cancel calls");
            Thread.sleep(500);
            assertNull(unexpected.get(), "cancel-completion race failed");
            assertTrue(completed.get() > 0, "race did not observe any completed tasks");
            assertTrue(cancelled.get() > 0, "race did not observe any cancelled tasks");
            assertEquals(RACE_TASK_COUNT, completed.get() + cancelled.get(),
                    "race terminal callback total is incorrect");
            for (TaskObservation observation : observations) {
                assertEquals(1, observation.terminalCount.get(),
                        "race task emitted multiple terminal callbacks");
                assertEquals(0, observation.lateUpdateCount.get(),
                        "race task emitted an update after terminal callback");
            }
        } finally {
            client.close();
            cancellers.shutdownNow();
            callbacks.shutdownNow();
        }
    }

    private AgentTaskRequest request(String suffix) {
        long now = SystemClock.elapsedRealtime();
        AgentTaskRequest request = new AgentTaskRequest();
        request.clientRequestId = "r2c-" + suffix + "-" + now;
        request.sessionId = "r2c-instrumentation";
        request.utterance = "deterministic Binder lifecycle test " + suffix;
        request.locale = "en-US";
        request.deadlineElapsedRealtimeMs = now + 15000;
        request.priority = 1;
        request.idempotencyKey = request.clientRequestId;
        return request;
    }

    private String shell(String command) throws IOException {
        ParcelFileDescriptor descriptor = getUiAutomation().executeShellCommand(command);
        try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(descriptor);
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void await(CountDownLatch latch, String description) throws InterruptedException {
        assertTrue(latch.await(WAIT_SECONDS, TimeUnit.SECONDS),
                "timed out waiting for " + description);
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void assertNull(Object value, String message) {
        if (value != null) {
            throw new AssertionError(message + ": " + value);
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertEquals(String expected, String actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static final class ConnectionRecorder implements CentralBrainClient.ConnectionListener {
        final CountDownLatch firstConnected = new CountDownLatch(1);
        final CountDownLatch reconnected = new CountDownLatch(1);
        final CountDownLatch disconnected = new CountDownLatch(1);
        final AtomicInteger connectedCount = new AtomicInteger();
        final AtomicInteger disconnectedCount = new AtomicInteger();
        final AtomicReference<String> failure = new AtomicReference<>();

        @Override
        public void onConnected(CentralBrainClient client) {
            int count = connectedCount.incrementAndGet();
            if (count == 1) {
                firstConnected.countDown();
            } else {
                reconnected.countDown();
            }
        }

        @Override
        public void onDisconnected() {
            disconnectedCount.incrementAndGet();
            disconnected.countDown();
        }

        @Override
        public void onConnectionFailed(String reason) {
            failure.compareAndSet(null, reason);
            firstConnected.countDown();
            reconnected.countDown();
        }
    }

    private static final class TaskObservation {
        final CountDownLatch terminals;
        final AtomicReference<Throwable> unexpected;
        final AtomicBoolean terminal = new AtomicBoolean();
        final AtomicInteger terminalCount = new AtomicInteger();
        final AtomicInteger lateUpdateCount = new AtomicInteger();

        TaskObservation(
                CountDownLatch terminals,
                AtomicReference<Throwable> unexpected) {
            this.terminals = terminals;
            this.unexpected = unexpected;
        }

        void recordUpdate() {
            if (terminal.get()) {
                lateUpdateCount.incrementAndGet();
            }
        }

        void recordTerminal() {
            terminalCount.incrementAndGet();
            if (!terminal.compareAndSet(false, true)) {
                unexpected.compareAndSet(null, new AssertionError(
                        "duplicate terminal callback observed"));
                return;
            }
            terminals.countDown();
        }
    }
}
