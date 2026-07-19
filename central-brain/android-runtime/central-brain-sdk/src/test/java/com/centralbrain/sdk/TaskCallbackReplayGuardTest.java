package com.centralbrain.sdk;

import static org.junit.Assert.assertEquals;

import com.centralbrain.sdk.production.ICentralBrainRuntime;
import com.centralbrain.sdk.production.TaskFailure;
import com.centralbrain.sdk.production.TaskResult;
import com.centralbrain.sdk.production.TaskUpdate;

import org.junit.Test;

public final class TaskCallbackReplayGuardTest {
    private static final String TASK_ID = "task-p9-w03e";

    @Test
    public void dropsDuplicateAndStaleSequencesWithoutRegressingState() {
        TaskCallbackReplayGuard guard = boundGuard();
        assertDecision(TaskCallbackReplayGuard.Decision.ACCEPT,
                guard.admitUpdate(update(1, ICentralBrainRuntime.TASK_STATE_ACCEPTED, 0)));
        assertDecision(TaskCallbackReplayGuard.Decision.DROP_REPLAY,
                guard.admitUpdate(update(1, ICentralBrainRuntime.TASK_STATE_ACCEPTED, 0)));
        assertDecision(TaskCallbackReplayGuard.Decision.ACCEPT,
                guard.admitUpdate(update(2, ICentralBrainRuntime.TASK_STATE_RUNNING, 50)));
        assertDecision(TaskCallbackReplayGuard.Decision.DROP_REPLAY,
                guard.admitUpdate(update(1, ICentralBrainRuntime.TASK_STATE_ACCEPTED, 0)));
        assertDecision(TaskCallbackReplayGuard.Decision.REJECT,
                guard.admitUpdate(update(3, ICentralBrainRuntime.TASK_STATE_ACCEPTED, 75)));
    }

    @Test
    public void rejectsCrossTaskAndMalformedCallbackPayloads() {
        TaskCallbackReplayGuard guard = boundGuard();
        TaskUpdate crossTask = update(1, ICentralBrainRuntime.TASK_STATE_ACCEPTED, 0);
        crossTask.taskId = "task-foreign";
        assertDecision(TaskCallbackReplayGuard.Decision.REJECT,
                guard.admitUpdate(crossTask));

        TaskUpdate badSchema = update(1, ICentralBrainRuntime.TASK_STATE_ACCEPTED, 0);
        badSchema.schemaVersion = 2;
        assertDecision(TaskCallbackReplayGuard.Decision.REJECT,
                guard.admitUpdate(badSchema));
    }

    @Test
    public void permitsOneTerminalAndDropsTerminalReplayOrLateUpdate() {
        TaskCallbackReplayGuard guard = boundGuard();
        assertDecision(TaskCallbackReplayGuard.Decision.ACCEPT,
                guard.admitUpdate(update(3, ICentralBrainRuntime.TASK_STATE_COMPLETED, 100)));
        assertDecision(TaskCallbackReplayGuard.Decision.DROP_REPLAY,
                guard.admitUpdate(update(4, ICentralBrainRuntime.TASK_STATE_FAILED, 100)));

        TaskResult result = new TaskResult();
        result.taskId = TASK_ID;
        result.completionCode = ICentralBrainRuntime.ERROR_NONE;
        assertDecision(TaskCallbackReplayGuard.Decision.ACCEPT,
                guard.admitCompleted(result));
        assertDecision(TaskCallbackReplayGuard.Decision.DROP_REPLAY,
                guard.admitCompleted(result));
        assertDecision(TaskCallbackReplayGuard.Decision.DROP_REPLAY,
                guard.admitUpdate(update(5, ICentralBrainRuntime.TASK_STATE_COMPLETED, 100)));
    }

    @Test
    public void rejectsFailureForAnotherTask() {
        TaskCallbackReplayGuard guard = boundGuard();
        TaskFailure failure = new TaskFailure();
        failure.taskId = "task-foreign";
        failure.errorCode = ICentralBrainRuntime.ERROR_INTERNAL;
        assertDecision(TaskCallbackReplayGuard.Decision.REJECT,
                guard.admitFailed(failure));
    }

    private static TaskCallbackReplayGuard boundGuard() {
        TaskCallbackReplayGuard guard = new TaskCallbackReplayGuard();
        guard.bindTaskId(TASK_ID);
        return guard;
    }

    private static TaskUpdate update(long sequence, int state, int progress) {
        TaskUpdate update = new TaskUpdate();
        update.taskId = TASK_ID;
        update.sequence = sequence;
        update.state = state;
        update.progressPercent = progress;
        return update;
    }

    private static void assertDecision(
            TaskCallbackReplayGuard.Decision expected,
            TaskCallbackReplayGuard.Admission actual) {
        assertEquals(expected, actual.getDecision());
    }
}
