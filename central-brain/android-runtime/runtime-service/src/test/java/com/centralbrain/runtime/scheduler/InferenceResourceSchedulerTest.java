package com.centralbrain.runtime.scheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.centralbrain.runtime.model.ModelProviderProfiles;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

public final class InferenceResourceSchedulerTest {
    private static final String OWNER_A = "a".repeat(64);
    private static final String OWNER_B = "b".repeat(64);

    @Test
    public void dispatchOrderIsPriorityThenDeadlineThenFifo() {
        AtomicLong clock = new AtomicLong(1_000);
        InferenceResourceScheduler scheduler = scheduler(
                clock,
                new InferenceResourceScheduler.Limits(8, 8, 5, 5, 10_000),
                route("test.stub", 5, true));

        admit(scheduler, "background", OWNER_A,
                InferenceResourceScheduler.EffectivePriority.BACKGROUND, 9_000, 8_000);
        admit(scheduler, "normal-late", OWNER_A,
                InferenceResourceScheduler.EffectivePriority.NORMAL, 9_000, 8_000);
        admit(scheduler, "high", OWNER_A,
                InferenceResourceScheduler.EffectivePriority.HIGH, 9_000, 8_000);
        admit(scheduler, "normal-early", OWNER_A,
                InferenceResourceScheduler.EffectivePriority.NORMAL, 5_000, 4_000);
        admit(scheduler, "normal-early-fifo", OWNER_A,
                InferenceResourceScheduler.EffectivePriority.NORMAL, 5_000, 4_000);

        assertEquals("high", scheduler.claimNext().getLease().getActive().getRequestId());
        assertEquals(
                "normal-early",
                scheduler.claimNext().getLease().getActive().getRequestId());
        assertEquals(
                "normal-early-fifo",
                scheduler.claimNext().getLease().getActive().getRequestId());
        assertEquals(
                "normal-late",
                scheduler.claimNext().getLease().getActive().getRequestId());
        assertEquals(
                "background",
                scheduler.claimNext().getLease().getActive().getRequestId());
    }

    @Test
    public void admissionEnforcesOwnerAndGlobalQueueQuotas() {
        AtomicLong clock = new AtomicLong(1_000);
        InferenceResourceScheduler scheduler = scheduler(
                clock,
                new InferenceResourceScheduler.Limits(2, 1, 2, 1, 10_000),
                route("test.stub", 2, true));

        InferenceResourceScheduler.TrustedSubmission first = submission(
                "a-1", OWNER_A, InferenceResourceScheduler.EffectivePriority.NORMAL,
                9_000, 8_000, "test.stub");
        assertEquals(
                InferenceResourceScheduler.AdmissionOutcome.ADMITTED,
                scheduler.admit(first).getOutcome());
        assertEquals(
                InferenceResourceScheduler.AdmissionOutcome.REPLAYED,
                scheduler.admit(first).getOutcome());
        assertEquals(
                InferenceResourceScheduler.AdmissionOutcome.OWNER_QUEUE_QUOTA_EXCEEDED,
                scheduler.admit(submission(
                        "a-2", OWNER_A,
                        InferenceResourceScheduler.EffectivePriority.NORMAL,
                        9_000, 8_000, "test.stub")).getOutcome());
        assertEquals(
                InferenceResourceScheduler.AdmissionOutcome.ADMITTED,
                scheduler.admit(submission(
                        "b-1", OWNER_B,
                        InferenceResourceScheduler.EffectivePriority.NORMAL,
                        9_000, 8_000, "test.stub")).getOutcome());
        assertEquals(
                InferenceResourceScheduler.AdmissionOutcome.GLOBAL_QUEUE_QUOTA_EXCEEDED,
                scheduler.admit(submission(
                        "c-1", "c".repeat(64),
                        InferenceResourceScheduler.EffectivePriority.NORMAL,
                        9_000, 8_000, "test.stub")).getOutcome());
    }

    @Test
    public void claimEnforcesOwnerRunningQuotaAndProviderSlots() {
        AtomicLong clock = new AtomicLong(1_000);
        InferenceResourceScheduler scheduler = scheduler(
                clock,
                new InferenceResourceScheduler.Limits(8, 8, 3, 1, 10_000),
                route("test.stub", 2, true));
        admit(scheduler, "a-high", OWNER_A,
                InferenceResourceScheduler.EffectivePriority.HIGH, 9_000, 8_000);
        admit(scheduler, "a-normal", OWNER_A,
                InferenceResourceScheduler.EffectivePriority.NORMAL, 9_000, 8_000);
        admit(scheduler, "b-normal", OWNER_B,
                InferenceResourceScheduler.EffectivePriority.NORMAL, 9_000, 8_000);

        assertEquals("a-high", scheduler.claimNext().getLease().getActive().getRequestId());
        assertEquals("b-normal", scheduler.claimNext().getLease().getActive().getRequestId());
        assertNull(scheduler.claimNext().getLease());
        assertEquals(1, scheduler.snapshot().getQueuedCount());
        assertEquals(2, scheduler.snapshot().getRunningSlotCount());
    }

    @Test
    public void deadlinesExpireQueuedAndRequestRunningProviderCancellation() {
        AtomicLong clock = new AtomicLong(1_000);
        InferenceResourceScheduler scheduler = scheduler(
                clock,
                new InferenceResourceScheduler.Limits(8, 8, 2, 2, 10_000),
                route("test.stub", 2, true));
        admit(scheduler, "running", OWNER_A,
                InferenceResourceScheduler.EffectivePriority.NORMAL, 2_000, 8_000);
        InferenceResourceScheduler.Lease lease = scheduler.claimNext().getLease();
        admit(scheduler, "queued", OWNER_B,
                InferenceResourceScheduler.EffectivePriority.NORMAL, 9_000, 500);

        clock.set(2_000);
        InferenceResourceScheduler.ExpiryReport report = scheduler.sweepDeadlines();
        assertEquals(Collections.singletonList("queued"), report.getQueuedExpiredRequestIds());
        assertEquals(1, report.getCancellationDirectives().size());
        assertEquals(
                InferenceResourceScheduler.CancelReason.DEADLINE_EXCEEDED,
                report.getCancellationDirectives().get(0).getReason());
        assertEquals(
                InferenceResourceScheduler.ActiveState.CANCEL_REQUESTED,
                scheduler.findOwned("running", OWNER_A).getState());
        assertEquals(
                InferenceResourceScheduler.SettlementOutcome.APPLIED,
                scheduler.settle(
                        "running",
                        lease.getLeaseId(),
                        InferenceResourceScheduler.ProviderTerminalOutcome.CANCELLED)
                        .getOutcome());
        assertNull(scheduler.findOwned("running", OWNER_A));
    }

    @Test
    public void queuedCancelIsLocalAndRunningCancelOnlyReturnsDirective() {
        AtomicLong clock = new AtomicLong(1_000);
        InferenceResourceScheduler scheduler = scheduler(
                clock,
                new InferenceResourceScheduler.Limits(8, 8, 2, 2, 10_000),
                route("test.stub", 2, true));
        admit(scheduler, "queued", OWNER_A,
                InferenceResourceScheduler.EffectivePriority.NORMAL, 9_000, 8_000);
        assertEquals(
                InferenceResourceScheduler.CancelOutcome.CANCELLED_QUEUED,
                scheduler.cancelOwned("queued", OWNER_A).getOutcome());
        assertNull(scheduler.findOwned("queued", OWNER_A));

        admit(scheduler, "running", OWNER_A,
                InferenceResourceScheduler.EffectivePriority.NORMAL, 9_000, 8_000);
        InferenceResourceScheduler.Lease lease = scheduler.claimNext().getLease();
        InferenceResourceScheduler.CancelResult cancel = scheduler.cancelOwned(
                "running", OWNER_A);
        assertEquals(
                InferenceResourceScheduler.CancelOutcome.PROVIDER_CANCEL_REQUIRED,
                cancel.getOutcome());
        assertEquals(lease.getLeaseId(), cancel.getDirective().getLeaseId());
        InferenceResourceScheduler.Settlement completionRace = scheduler.settle(
                "running",
                lease.getLeaseId(),
                InferenceResourceScheduler.ProviderTerminalOutcome.COMPLETED);
        assertEquals(
                InferenceResourceScheduler.SettlementOutcome.APPLIED,
                completionRace.getOutcome());
        assertEquals(
                InferenceResourceScheduler.LocalTerminalState.CANCELLED,
                completionRace.getLocalTerminalState());
        assertNull(scheduler.findOwned("running", OWNER_A));
    }

    @Test
    public void currentProfilesRemainNonRoutable() {
        AtomicLong clock = new AtomicLong(1_000);
        InferenceResourceScheduler scheduler = scheduler(
                clock,
                new InferenceResourceScheduler.Limits(8, 8, 2, 2, 10_000),
                InferenceResourceScheduler.RouteTarget.fromProfile(
                        ModelProviderProfiles.deterministicStub()),
                InferenceResourceScheduler.RouteTarget.fromProfile(
                        ModelProviderProfiles.vendorNpuEmpty()));
        assertFalse(InferenceResourceScheduler.RouteTarget.fromProfile(
                ModelProviderProfiles.deterministicStub()).isRoutingEnabled());
        assertFalse(InferenceResourceScheduler.RouteTarget.fromProfile(
                ModelProviderProfiles.vendorNpuEmpty()).isRoutingEnabled());
        assertEquals(
                InferenceResourceScheduler.AdmissionOutcome.ROUTE_UNAVAILABLE,
                scheduler.admit(submission(
                        "stub-current", OWNER_A,
                        InferenceResourceScheduler.EffectivePriority.NORMAL,
                        9_000, 8_000,
                        ModelProviderProfiles.DETERMINISTIC_STUB_ID)).getOutcome());
    }

    @Test
    public void unsupportedCancellationKeepsSlotUntilTerminalAndReportsDeadlineOnce() {
        AtomicLong clock = new AtomicLong(1_000);
        InferenceResourceScheduler scheduler = scheduler(
                clock,
                new InferenceResourceScheduler.Limits(4, 4, 1, 1, 10_000),
                route("test.no-cancel", 1, false));
        assertEquals(
                InferenceResourceScheduler.AdmissionOutcome.ADMITTED,
                scheduler.admit(submission(
                        "no-cancel", OWNER_A,
                        InferenceResourceScheduler.EffectivePriority.NORMAL,
                        2_000, 5_000, "test.no-cancel")).getOutcome());
        InferenceResourceScheduler.Lease lease = scheduler.claimNext().getLease();
        assertEquals(
                InferenceResourceScheduler.CancelOutcome.PROVIDER_CANCEL_UNSUPPORTED,
                scheduler.cancelOwned("no-cancel", OWNER_A).getOutcome());
        assertEquals(1, scheduler.snapshot().getRunningSlotCount());

        clock.set(2_000);
        assertEquals(
                Collections.singletonList("no-cancel"),
                scheduler.sweepDeadlines().getCancellationUnsupportedRequestIds());
        assertTrue(scheduler.sweepDeadlines()
                .getCancellationUnsupportedRequestIds().isEmpty());
        assertEquals(
                InferenceResourceScheduler.SettlementOutcome.APPLIED,
                scheduler.settle(
                        "no-cancel",
                        lease.getLeaseId(),
                        InferenceResourceScheduler.ProviderTerminalOutcome.COMPLETED)
                        .getOutcome());
        assertEquals(0, scheduler.snapshot().getRunningSlotCount());
    }

    @Test
    public void ownerIsolationAndLeaseValidationFailClosed() {
        AtomicLong clock = new AtomicLong(1_000);
        InferenceResourceScheduler scheduler = scheduler(
                clock,
                new InferenceResourceScheduler.Limits(4, 4, 1, 1, 10_000),
                route("test.stub", 1, true));
        admit(scheduler, "owned", OWNER_A,
                InferenceResourceScheduler.EffectivePriority.NORMAL, 9_000, 8_000);
        InferenceResourceScheduler.Lease lease = scheduler.claimNext().getLease();
        assertNull(scheduler.findOwned("owned", OWNER_B));
        assertEquals(
                InferenceResourceScheduler.CancelOutcome.NOT_FOUND_OR_NOT_OWNER,
                scheduler.cancelOwned("owned", OWNER_B).getOutcome());
        assertEquals(
                InferenceResourceScheduler.SettlementOutcome.STALE_LEASE,
                scheduler.settle(
                        "owned",
                        "stale-lease",
                        InferenceResourceScheduler.ProviderTerminalOutcome.COMPLETED)
                        .getOutcome());
        assertEquals(1, scheduler.snapshot().getRunningSlotCount());
        assertEquals(
                InferenceResourceScheduler.SettlementOutcome.APPLIED,
                scheduler.settle(
                        "owned",
                        lease.getLeaseId(),
                        InferenceResourceScheduler.ProviderTerminalOutcome.COMPLETED)
                        .getOutcome());
    }

    private static InferenceResourceScheduler scheduler(
            AtomicLong clock,
            InferenceResourceScheduler.Limits limits,
            InferenceResourceScheduler.RouteTarget... routes) {
        AtomicInteger leases = new AtomicInteger();
        return new InferenceResourceScheduler(
                limits,
                clock::get,
                () -> "lease-" + leases.incrementAndGet(),
                Arrays.asList(routes));
    }

    private static InferenceResourceScheduler.RouteTarget route(
            String providerId,
            int slots,
            boolean cancellation) {
        return InferenceResourceScheduler.RouteTarget.forContractTest(
                providerId,
                slots,
                cancellation);
    }

    private static void admit(
            InferenceResourceScheduler scheduler,
            String requestId,
            String owner,
            InferenceResourceScheduler.EffectivePriority priority,
            long deadline,
            long queueWait) {
        assertEquals(
                InferenceResourceScheduler.AdmissionOutcome.ADMITTED,
                scheduler.admit(submission(
                        requestId,
                        owner,
                        priority,
                        deadline,
                        queueWait,
                        "test.stub")).getOutcome());
    }

    private static InferenceResourceScheduler.TrustedSubmission submission(
            String requestId,
            String owner,
            InferenceResourceScheduler.EffectivePriority priority,
            long deadline,
            long queueWait,
            String providerId) {
        return InferenceResourceScheduler.TrustedSubmission.fromRuntimePolicy(
                requestId,
                owner,
                "central-intent-v0",
                providerId,
                priority,
                deadline,
                queueWait);
    }
}
