package com.centralbrain.runtime.supervisor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.centralbrain.runtime.identity.CallerIdentitySnapshot;

import org.junit.Test;

import java.util.Collections;

public final class JobSupervisorTest {
    @Test
    public void enforcesDeterministicLifecycleAndSequence() {
        FakeClock clock = new FakeClock(1000);
        JobSupervisor supervisor = new JobSupervisor(4, 5000, clock);

        JobSupervisor.Admission admission = supervisor.admit("task-1", owner(11001), "accepted");
        assertEquals(JobSupervisor.State.ACCEPTED, admission.getSnapshot().getState());
        assertEquals(1, admission.getSnapshot().getSequence());
        assertEquals(1000, admission.getSnapshot().getAcceptedAtElapsedRealtimeMs());

        JobSupervisor.Transition running = supervisor.transition(
                "task-1",
                JobSupervisor.State.RUNNING,
                40,
                "running");
        assertTrue(running.wasApplied());
        assertEquals(2, running.getSnapshot().getSequence());
        assertEquals(40, running.getSnapshot().getProgressPercent());

        clock.advance(25);
        JobSupervisor.Transition completed = supervisor.transition(
                "task-1",
                JobSupervisor.State.COMPLETED,
                100,
                "completed");
        assertTrue(completed.wasApplied());
        assertTrue(completed.getSnapshot().isTerminal());
        assertEquals(3, completed.getSnapshot().getSequence());
        assertEquals(1025, completed.getSnapshot().getTerminalAtElapsedRealtimeMs());

        JobSupervisor.Transition lateFailure = supervisor.transition(
                "task-1",
                JobSupervisor.State.FAILED,
                100,
                "late failure");
        assertEquals(JobSupervisor.TransitionOutcome.ALREADY_TERMINAL, lateFailure.getOutcome());
        assertEquals(JobSupervisor.State.COMPLETED, lateFailure.getSnapshot().getState());
    }

    @Test
    public void rejectsInvalidTransitionsAndProgress() {
        JobSupervisor supervisor = new JobSupervisor(4, 5000, new FakeClock(10));
        supervisor.admit("task-1", owner(11001), "accepted");

        assertThrows(
                IllegalStateException.class,
                () -> supervisor.markTerminalDeliverySettled("task-1"));
        assertThrows(IllegalStateException.class, () -> supervisor.transition(
                "task-1",
                JobSupervisor.State.COMPLETED,
                100,
                "skipped running"));
        assertThrows(IllegalArgumentException.class, () -> supervisor.transition(
                "task-1",
                JobSupervisor.State.RUNNING,
                100,
                "invalid running progress"));

        supervisor.transition("task-1", JobSupervisor.State.RUNNING, 50, "running");
        assertThrows(IllegalArgumentException.class, () -> supervisor.transition(
                "task-1",
                JobSupervisor.State.FAILED,
                49,
                "progress regression"));
    }

    @Test
    public void isolatesOwnersAndKeepsCancellationIdempotent() {
        JobSupervisor supervisor = new JobSupervisor(4, 5000, new FakeClock(10));
        CallerIdentitySnapshot owner = owner(11001);
        CallerIdentitySnapshot other = owner(11002);
        supervisor.admit("task-1", owner, "accepted");

        assertNull(supervisor.findOwned("task-1", other));
        assertEquals(
                JobSupervisor.TransitionOutcome.NOT_FOUND_OR_NOT_OWNER,
                supervisor.cancelOwned("task-1", other, "unauthorized").getOutcome());
        assertEquals(JobSupervisor.State.ACCEPTED, supervisor.find("task-1").getState());

        assertTrue(supervisor.cancelOwned("task-1", owner, "cancelled").wasApplied());
        JobSupervisor.Transition duplicate = supervisor.cancelOwned(
                "task-1",
                owner,
                "cancelled again");
        assertEquals(JobSupervisor.TransitionOutcome.ALREADY_CANCELLED, duplicate.getOutcome());
        assertEquals(2, duplicate.getSnapshot().getSequence());
    }

    @Test
    public void boundsRegistryAndEvictsOnlyTerminalRecords() {
        FakeClock clock = new FakeClock(10);
        JobSupervisor supervisor = new JobSupervisor(2, 1000, clock);
        CallerIdentitySnapshot owner = owner(11001);
        supervisor.admit("task-1", owner, "accepted");
        supervisor.admit("task-2", owner, "accepted");

        assertThrows(
                JobSupervisor.CapacityExceededException.class,
                () -> supervisor.admit("task-3", owner, "accepted"));
        assertEquals(2, supervisor.size());

        supervisor.cancelOwned("task-1", owner, "cancelled");
        assertThrows(
                JobSupervisor.CapacityExceededException.class,
                () -> supervisor.admit("task-3", owner, "callback still pending"));
        supervisor.markTerminalDeliverySettled("task-1");
        JobSupervisor.Admission replacement = supervisor.admit("task-3", owner, "accepted");
        assertEquals(Collections.singletonList("task-1"), replacement.getEvictedTaskIds());
        assertNull(supervisor.find("task-1"));
        assertEquals(JobSupervisor.State.ACCEPTED, supervisor.find("task-2").getState());
        assertEquals(JobSupervisor.State.ACCEPTED, supervisor.find("task-3").getState());
    }

    @Test
    public void expiresTerminalRecordsAfterRetention() {
        FakeClock clock = new FakeClock(100);
        JobSupervisor supervisor = new JobSupervisor(4, 50, clock);
        CallerIdentitySnapshot owner = owner(11001);
        supervisor.admit("task-1", owner, "accepted");
        supervisor.cancelOwned("task-1", owner, "cancelled");

        clock.advance(50);
        assertTrue(supervisor.pruneExpired().isEmpty());
        assertEquals(1, supervisor.size());

        supervisor.markTerminalDeliverySettled("task-1");
        assertEquals(Collections.singletonList("task-1"), supervisor.pruneExpired());
        assertEquals(0, supervisor.size());
    }

    @Test
    public void rejectsUnresolvedOwnerEvidence() {
        JobSupervisor supervisor = new JobSupervisor(4, 50, new FakeClock(100));
        CallerIdentitySnapshot unresolved = CallerIdentitySnapshot.unresolved(
                11001,
                0,
                "test failure");
        assertFalse(unresolved.isResolved());
        assertThrows(
                SecurityException.class,
                () -> supervisor.admit("task-1", unresolved, "accepted"));
    }

    private static CallerIdentitySnapshot owner(int uid) {
        String digest = String.join("", Collections.nCopies(64, uid % 2 == 0 ? "b" : "a"));
        CallerIdentitySnapshot.PackageIdentity packageIdentity =
                new CallerIdentitySnapshot.PackageIdentity(
                        "com.centralbrain.client" + uid,
                        Collections.singletonList(digest));
        return CallerIdentitySnapshot.resolved(
                uid,
                0,
                Collections.singletonList(packageIdentity));
    }

    private static final class FakeClock implements JobSupervisor.ElapsedRealtimeClock {
        private long now;

        FakeClock(long now) {
            this.now = now;
        }

        @Override
        public long now() {
            return now;
        }

        void advance(long delta) {
            now += delta;
        }
    }
}
