package com.centralbrain.runtime.scheduler;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.centralbrain.runtime.model.ModelProviderProfiles;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class InferenceSchedulerContractProbeActivity extends Activity {
    private static final String TAG = "CbSchedulerProbe";
    private static final String OWNER_A = repeat("a", 64);
    private static final String OWNER_B = repeat("b", 64);
    private static final String OWNER_C = repeat("c", 64);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        runProbe(nonce == null ? "" : nonce);
        finish();
    }

    private static void runProbe(String nonce) {
        try {
            AtomicLong clock = new AtomicLong(1_000);
            InferenceResourceScheduler orderScheduler = scheduler(
                    clock,
                    new InferenceResourceScheduler.Limits(8, 8, 5, 5, 10_000),
                    5,
                    true);
            boolean trustedEffectivePriorityVerified = admit(
                    orderScheduler, "background", OWNER_A,
                    InferenceResourceScheduler.EffectivePriority.BACKGROUND,
                    9_000, 8_000)
                    && admit(
                            orderScheduler, "normal-late", OWNER_A,
                            InferenceResourceScheduler.EffectivePriority.NORMAL,
                            9_000, 8_000)
                    && admit(
                            orderScheduler, "normal-early", OWNER_A,
                            InferenceResourceScheduler.EffectivePriority.NORMAL,
                            5_000, 4_000)
                    && admit(
                            orderScheduler, "normal-early-fifo", OWNER_A,
                            InferenceResourceScheduler.EffectivePriority.NORMAL,
                            5_000, 4_000)
                    && admit(
                            orderScheduler, "high", OWNER_A,
                            InferenceResourceScheduler.EffectivePriority.HIGH,
                            9_000, 8_000);
            InferenceResourceScheduler.Lease high = orderScheduler.claimNext().getLease();
            boolean priorityDeadlineFifoOrderVerified = high != null
                    && "high".equals(high.getActive().getRequestId())
                    && "normal-early".equals(orderScheduler.claimNext()
                            .getLease().getActive().getRequestId())
                    && "normal-early-fifo".equals(orderScheduler.claimNext()
                            .getLease().getActive().getRequestId())
                    && "normal-late".equals(orderScheduler.claimNext()
                            .getLease().getActive().getRequestId())
                    && "background".equals(orderScheduler.claimNext()
                            .getLease().getActive().getRequestId());

            InferenceResourceScheduler runningQuotaScheduler = scheduler(
                    clock,
                    new InferenceResourceScheduler.Limits(5, 3, 2, 1, 10_000),
                    3,
                    true);
            admit(runningQuotaScheduler, "a-high", OWNER_A,
                    InferenceResourceScheduler.EffectivePriority.HIGH, 9_000, 8_000);
            admit(runningQuotaScheduler, "a-normal", OWNER_A,
                    InferenceResourceScheduler.EffectivePriority.NORMAL, 9_000, 8_000);
            admit(runningQuotaScheduler, "b-normal", OWNER_B,
                    InferenceResourceScheduler.EffectivePriority.NORMAL, 9_000, 8_000);
            admit(runningQuotaScheduler, "c-background", OWNER_C,
                    InferenceResourceScheduler.EffectivePriority.BACKGROUND,
                    9_000, 8_000);
            InferenceResourceScheduler.Lease firstOwner = runningQuotaScheduler
                    .claimNext().getLease();
            InferenceResourceScheduler.Lease secondOwner = runningQuotaScheduler
                    .claimNext().getLease();
            boolean globalOwnerRunningQuotaVerified = firstOwner != null
                    && "a-high".equals(firstOwner.getActive().getRequestId())
                    && secondOwner != null
                    && "b-normal".equals(secondOwner.getActive().getRequestId())
                    && runningQuotaScheduler.claimNext().getLease() == null
                    && runningQuotaScheduler.snapshot().getRunningSlotCount() == 2
                    && runningQuotaScheduler.snapshot().getQueuedCount() == 2;

            InferenceResourceScheduler cancelScheduler = scheduler(
                    clock,
                    1,
                    true);
            admit(cancelScheduler, "running", OWNER_A,
                    InferenceResourceScheduler.EffectivePriority.NORMAL, 9_000, 8_000);
            InferenceResourceScheduler.Lease running = cancelScheduler
                    .claimNext()
                    .getLease();
            boolean runningCancelRequiresProviderVerified =
                    cancelScheduler.cancelOwned("running", OWNER_A).getOutcome()
                            == InferenceResourceScheduler.CancelOutcome
                                    .PROVIDER_CANCEL_REQUIRED;
            InferenceResourceScheduler.Settlement completionRace = cancelScheduler.settle(
                    "running",
                    running.getLeaseId(),
                    InferenceResourceScheduler.ProviderTerminalOutcome.COMPLETED);
            boolean completionAfterCancelResolved = completionRace.getOutcome()
                    == InferenceResourceScheduler.SettlementOutcome.APPLIED
                    && completionRace.getLocalTerminalState()
                            == InferenceResourceScheduler.LocalTerminalState.CANCELLED
                    && cancelScheduler.findOwned("running", OWNER_A) == null;
            boolean queuedCancelVerified = admit(
                    cancelScheduler, "queued-cancel", OWNER_A,
                    InferenceResourceScheduler.EffectivePriority.BACKGROUND,
                    9_000, 8_000)
                    && cancelScheduler.cancelOwned("queued-cancel", OWNER_A).getOutcome()
                            == InferenceResourceScheduler.CancelOutcome.CANCELLED_QUEUED;

            InferenceResourceScheduler queueQuotaScheduler = scheduler(
                    clock,
                    new InferenceResourceScheduler.Limits(2, 1, 2, 2, 10_000),
                    2,
                    true);
            boolean ownerQueueQuotaVerified = admit(
                    queueQuotaScheduler, "quota-a1", OWNER_A,
                    InferenceResourceScheduler.EffectivePriority.NORMAL,
                    9_000, 8_000)
                    && queueQuotaScheduler.admit(submission(
                            "quota-a2", OWNER_A,
                            InferenceResourceScheduler.EffectivePriority.NORMAL,
                            9_000, 8_000)).getOutcome()
                            == InferenceResourceScheduler.AdmissionOutcome
                                    .OWNER_QUEUE_QUOTA_EXCEEDED;
            boolean globalQueueQuotaVerified = admit(
                    queueQuotaScheduler, "quota-b1", OWNER_B,
                    InferenceResourceScheduler.EffectivePriority.NORMAL,
                    9_000, 8_000)
                    && queueQuotaScheduler.admit(submission(
                            "quota-c1", OWNER_C,
                            InferenceResourceScheduler.EffectivePriority.NORMAL,
                            9_000, 8_000)).getOutcome()
                            == InferenceResourceScheduler.AdmissionOutcome
                                    .GLOBAL_QUEUE_QUOTA_EXCEEDED;
            boolean globalOwnerQueueQuotaVerified = ownerQueueQuotaVerified
                    && globalQueueQuotaVerified;

            InferenceResourceScheduler slotScheduler = scheduler(
                    clock,
                    new InferenceResourceScheduler.Limits(4, 4, 3, 3, 10_000),
                    1,
                    true);
            admit(slotScheduler, "slot-a", OWNER_A,
                    InferenceResourceScheduler.EffectivePriority.NORMAL, 9_000, 8_000);
            admit(slotScheduler, "slot-b", OWNER_B,
                    InferenceResourceScheduler.EffectivePriority.NORMAL, 9_000, 8_000);
            boolean providerSlotQuotaVerified = slotScheduler.claimNext().getLease() != null
                    && slotScheduler.claimNext().getLease() == null
                    && slotScheduler.snapshot().getRunningSlotCount() == 1
                    && slotScheduler.snapshot().getQueuedCount() == 1;

            clock.set(1_000);
            InferenceResourceScheduler queueExpiryScheduler = scheduler(
                    clock,
                    2,
                    true);
            admit(queueExpiryScheduler, "queue-expire", OWNER_A,
                    InferenceResourceScheduler.EffectivePriority.NORMAL,
                    9_000, 500);
            clock.set(1_500);
            boolean queuedDeadlineExpiryVerified = queueExpiryScheduler
                    .sweepDeadlines()
                    .getQueuedExpiredRequestIds()
                    .contains("queue-expire");

            clock.set(2_000);
            InferenceResourceScheduler deadlineScheduler = scheduler(
                    clock,
                    1,
                    true);
            admit(deadlineScheduler, "run-expire", OWNER_A,
                    InferenceResourceScheduler.EffectivePriority.NORMAL,
                    2_500, 5_000);
            deadlineScheduler.claimNext();
            clock.set(2_500);
            boolean runningDeadlineCancelDirectiveVerified = deadlineScheduler
                    .sweepDeadlines()
                    .getCancellationDirectives()
                    .size() == 1;

            boolean currentProfilesNonRoutableVerified =
                    !InferenceResourceScheduler.RouteTarget.fromProfile(
                            ModelProviderProfiles.deterministicStub()).isRoutingEnabled()
                            && !InferenceResourceScheduler.RouteTarget.fromProfile(
                                    ModelProviderProfiles.vendorNpuEmpty())
                                    .isRoutingEnabled();
            boolean schedulerContractVerified = trustedEffectivePriorityVerified
                    && priorityDeadlineFifoOrderVerified
                    && globalOwnerQueueQuotaVerified
                    && globalOwnerRunningQuotaVerified
                    && providerSlotQuotaVerified
                    && queuedDeadlineExpiryVerified
                    && runningDeadlineCancelDirectiveVerified
                    && queuedCancelVerified
                    && runningCancelRequiresProviderVerified
                    && completionAfterCancelResolved
                    && currentProfilesNonRoutableVerified;

            Log.i(TAG, "nonce=" + nonce
                    + " scheduler_probe_complete=true"
                    + " inference_scheduler_contract_verified="
                    + schedulerContractVerified
                    + " trusted_effective_priority_verified="
                    + trustedEffectivePriorityVerified
                    + " priority_deadline_fifo_order_verified="
                    + priorityDeadlineFifoOrderVerified
                    + " global_owner_queue_quota_verified="
                    + globalOwnerQueueQuotaVerified
                    + " global_owner_running_quota_verified="
                    + globalOwnerRunningQuotaVerified
                    + " provider_slot_quota_verified=" + providerSlotQuotaVerified
                    + " queued_deadline_expiry_verified="
                    + queuedDeadlineExpiryVerified
                    + " running_deadline_cancel_directive_verified="
                    + runningDeadlineCancelDirectiveVerified
                    + " queued_cancel_verified=" + queuedCancelVerified
                    + " running_cancel_requires_provider_verified="
                    + runningCancelRequiresProviderVerified
                    + " completion_after_cancel_resolved="
                    + completionAfterCancelResolved
                    + " current_profiles_non_routable_verified="
                    + currentProfilesNonRoutableVerified
                    + " provider_cancel_invoked=false"
                    + " scheduler_production_wired=false"
                    + " model_provider_runtime_wired=false"
                    + " model_router_dispatch_enabled=false"
                    + " hardware_accessed=false"
                    + " driver_development_triggered=false"
                    + " virtualization_development_triggered=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " scheduler_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " provider_cancel_invoked=false"
                    + " scheduler_production_wired=false"
                    + " model_provider_runtime_wired=false"
                    + " model_router_dispatch_enabled=false"
                    + " hardware_accessed=false", exception);
        }
    }

    private static InferenceResourceScheduler scheduler(
            AtomicLong clock,
            int slots,
            boolean supportsCancellation) {
        return scheduler(
                clock,
                new InferenceResourceScheduler.Limits(8, 8, slots, slots, 10_000),
                slots,
                supportsCancellation);
    }

    private static InferenceResourceScheduler scheduler(
            AtomicLong clock,
            InferenceResourceScheduler.Limits limits,
            int slots,
            boolean supportsCancellation) {
        AtomicInteger leases = new AtomicInteger();
        return new InferenceResourceScheduler(
                limits,
                clock::get,
                () -> "deadline-lease-" + leases.incrementAndGet(),
                Arrays.asList(
                        InferenceResourceScheduler.RouteTarget.forContractTest(
                                "test.stub", slots, supportsCancellation)));
    }

    private static boolean admit(
            InferenceResourceScheduler scheduler,
            String requestId,
            String owner,
            InferenceResourceScheduler.EffectivePriority priority,
            long deadline,
            long queueWait) {
        return scheduler.admit(submission(
                requestId,
                owner,
                priority,
                deadline,
                queueWait)).getOutcome() == InferenceResourceScheduler.AdmissionOutcome.ADMITTED;
    }

    private static InferenceResourceScheduler.TrustedSubmission submission(
            String requestId,
            String owner,
            InferenceResourceScheduler.EffectivePriority priority,
            long deadline,
            long queueWait) {
        return InferenceResourceScheduler.TrustedSubmission.fromRuntimePolicy(
                requestId,
                owner,
                "central-intent-v0",
                "test.stub",
                priority,
                deadline,
                queueWait);
    }

    private static String repeat(String value, int count) {
        StringBuilder output = new StringBuilder(value.length() * count);
        for (int index = 0; index < count; index++) {
            output.append(value);
        }
        return output.toString();
    }
}
