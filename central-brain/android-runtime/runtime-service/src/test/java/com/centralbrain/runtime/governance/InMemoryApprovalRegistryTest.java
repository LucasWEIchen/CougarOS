package com.centralbrain.runtime.governance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.centralbrain.runtime.governance.ActionGovernancePolicy.Decision;
import com.centralbrain.runtime.governance.InMemoryApprovalRegistry.Status;
import com.centralbrain.runtime.identity.CallerIdentitySnapshot;

import org.junit.Test;

import java.util.Collections;
import java.util.function.LongSupplier;

public final class InMemoryApprovalRegistryTest {
    private final ActionGovernancePolicy policy = new ActionGovernancePolicy();
    private final SafetyVehicleStateSnapshot state =
            new RuntimeOwnedSafetyVehicleStateProvider(() -> 10).currentSnapshot();

    @Test
    public void storesOnlyHighRiskPendingRequestsAndNeverGrantsThem() {
        FakeClock clock = new FakeClock(100);
        InMemoryApprovalRegistry registry = new InMemoryApprovalRegistry(4, 1000, 5000, clock);
        CallerIdentitySnapshot owner = owner(11001);
        Decision highRisk = policy.evaluate(ActionGovernancePolicy.ACTION_OTA_INSTALL, state);

        InMemoryApprovalRegistry.Snapshot pending = registry.request(
                ActionGovernancePolicy.ACTION_OTA_INSTALL,
                highRisk,
                owner);

        assertEquals("approval-1", pending.getApprovalId());
        assertEquals(Status.PENDING, pending.getStatus());
        assertEquals(100, pending.getCreatedAtElapsedRealtimeMs());
        assertEquals(1100, pending.getExpiresAtElapsedRealtimeMs());
        assertFalse(registry.supportsApprovalGrant());
        assertFalse(registry.isDurable());
        assertThrows(IllegalArgumentException.class, () -> registry.request(
                ActionGovernancePolicy.ACTION_CABIN_TEMPERATURE_SET,
                policy.evaluate(ActionGovernancePolicy.ACTION_CABIN_TEMPERATURE_SET, state),
                owner));
    }

    @Test
    public void isolatesOwnerAndKeepsCancellationIdempotent() {
        InMemoryApprovalRegistry registry = new InMemoryApprovalRegistry(
                4,
                1000,
                5000,
                new FakeClock(100));
        CallerIdentitySnapshot owner = owner(11001);
        CallerIdentitySnapshot other = owner(11002);
        String approvalId = registry.request(
                ActionGovernancePolicy.ACTION_DIAGNOSTIC_WRITE,
                policy.evaluate(ActionGovernancePolicy.ACTION_DIAGNOSTIC_WRITE, state),
                owner).getApprovalId();

        assertNull(registry.findOwned(approvalId, other));
        assertFalse(registry.cancelOwned(approvalId, other));
        assertTrue(registry.cancelOwned(approvalId, owner));
        assertTrue(registry.cancelOwned(approvalId, owner));
        assertEquals(Status.CANCELLED, registry.findOwned(approvalId, owner).getStatus());
    }

    @Test
    public void expiresPendingRequestsAndPrunesAfterTerminalRetention() {
        FakeClock clock = new FakeClock(100);
        InMemoryApprovalRegistry registry = new InMemoryApprovalRegistry(4, 50, 25, clock);
        CallerIdentitySnapshot owner = owner(11001);
        String approvalId = registry.request(
                ActionGovernancePolicy.ACTION_OTA_INSTALL,
                policy.evaluate(ActionGovernancePolicy.ACTION_OTA_INSTALL, state),
                owner).getApprovalId();

        clock.advance(50);
        assertEquals(Status.EXPIRED, registry.findOwned(approvalId, owner).getStatus());
        clock.advance(24);
        assertTrue(registry.pruneExpired().isEmpty());
        clock.advance(1);
        assertEquals(Collections.singletonList(approvalId), registry.pruneExpired());
        assertNull(registry.findOwned(approvalId, owner));
    }

    @Test
    public void neverEvictsPendingRequestsButPressureEvictsTerminalRecords() {
        FakeClock clock = new FakeClock(100);
        InMemoryApprovalRegistry registry = new InMemoryApprovalRegistry(2, 1000, 5000, clock);
        CallerIdentitySnapshot owner = owner(11001);
        Decision decision = policy.evaluate(ActionGovernancePolicy.ACTION_OTA_INSTALL, state);
        String first = registry.request(
                ActionGovernancePolicy.ACTION_OTA_INSTALL,
                decision,
                owner).getApprovalId();
        registry.request(ActionGovernancePolicy.ACTION_OTA_INSTALL, decision, owner);

        assertThrows(InMemoryApprovalRegistry.CapacityExceededException.class, () ->
                registry.request(ActionGovernancePolicy.ACTION_OTA_INSTALL, decision, owner));
        assertTrue(registry.cancelOwned(first, owner));
        String replacement = registry.request(
                ActionGovernancePolicy.ACTION_OTA_INSTALL,
                decision,
                owner).getApprovalId();
        assertEquals("approval-3", replacement);
        assertNull(registry.findOwned(first, owner));
        assertEquals(2, registry.size());
    }

    private static CallerIdentitySnapshot owner(int uid) {
        return CallerIdentitySnapshot.resolved(
                uid,
                0,
                Collections.singletonList(new CallerIdentitySnapshot.PackageIdentity(
                        "com.centralbrain.owner" + uid,
                        Collections.singletonList(String.join(
                                "",
                                Collections.nCopies(64, "a"))))));
    }

    private static final class FakeClock implements LongSupplier {
        private long now;

        FakeClock(long now) {
            this.now = now;
        }

        void advance(long delta) {
            now += delta;
        }

        @Override
        public long getAsLong() {
            return now;
        }
    }
}
