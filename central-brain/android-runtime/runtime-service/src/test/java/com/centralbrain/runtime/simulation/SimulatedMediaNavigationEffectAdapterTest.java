package com.centralbrain.runtime.simulation;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.centralbrain.runtime.effects.EffectAdapter;
import com.centralbrain.runtime.vehicle.schema.SignalSource;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public final class SimulatedMediaNavigationEffectAdapterTest {
    private static final byte[] ENVELOPE =
            "media-nav-test-envelope-v1".getBytes(StandardCharsets.UTF_8);

    @Test
    public void typedTargetsRoundTripAndNavigationNormalizesAtFactoryBoundary() {
        SimulatedMediaEffectAdapter.MediaTarget media =
                SimulatedMediaEffectAdapter.MediaTarget.playback(
                        SimulatedMediaEffectAdapter.PlaybackCommand.PAUSE);
        assertArrayEquals(
                media.toCanonicalPayload(),
                SimulatedMediaEffectAdapter.MediaTarget.fromCanonicalPayload(
                        media.toCanonicalPayload()).toCanonicalPayload());

        SimulatedNavigationEffectAdapter.NavigationTarget navigation =
                SimulatedNavigationEffectAdapter.NavigationTarget.poi("  最近休息区  ");
        assertEquals("最近休息区", navigation.getPoiQuery());
        assertArrayEquals(
                navigation.toCanonicalPayload(),
                SimulatedNavigationEffectAdapter.NavigationTarget.fromCanonicalPayload(
                        navigation.toCanonicalPayload()).toCanonicalPayload());
        assertEquals(64, navigation.getQueryDigest().length());

        SimulatedMediaEffectAdapter adapter = mediaAdapter(FaultInjectionProfile.none());
        assertTrue(adapter.simulationDescriptor().isSimulation());
        assertFalse(adapter.simulationDescriptor().isProductionAuthorized());
    }

    @Test
    public void mediaCommandsUpdateOnlySimulatedStateAndRemainIdempotent() {
        SimulatedMediaEffectAdapter adapter = mediaAdapter(FaultInjectionProfile.none());
        applyMedia(adapter, token('a'), SimulatedMediaEffectAdapter.PlaybackCommand.PLAY);
        assertEquals(
                SimulatedMediaEffectAdapter.PlaybackCommand.PLAY,
                adapter.getCurrentState().orElseThrow().getCommand());
        assertEquals(SignalSource.SIMULATED, adapter.getCurrentState().orElseThrow().getSource());
        assertFalse(adapter.getCurrentState().orElseThrow().isProductionTrusted());

        EffectAdapter.Invocation pause = mediaInvocation(
                token('b'), SimulatedMediaEffectAdapter.PlaybackCommand.PAUSE);
        adapter.apply(pause);
        long revision = adapter.getCurrentState().orElseThrow().getRevision();
        adapter.apply(pause);
        assertEquals(revision, adapter.getCurrentState().orElseThrow().getRevision());

        applyMedia(adapter, token('c'), SimulatedMediaEffectAdapter.PlaybackCommand.STOP);
        assertEquals(
                SimulatedMediaEffectAdapter.PlaybackCommand.STOP,
                adapter.getCurrentState().orElseThrow().getCommand());
        adapter.reset();
        assertTrue(adapter.getCurrentState().isEmpty());
        assertEquals(0, adapter.getRecordCount());
    }

    @Test
    public void mediaDelayTimeoutFailureAndMismatchStayObservable() {
        SimulationClock clock = new SimulationClock(1_000);
        SimulatedMediaEffectAdapter delayed = new SimulatedMediaEffectAdapter(
                clock, FaultInjectionProfile.delay(50));
        applyMedia(delayed, token('d'), SimulatedMediaEffectAdapter.PlaybackCommand.PLAY);
        assertTrue(delayed.getCurrentState().isEmpty());
        clock.advanceBy(50);
        assertEquals(EffectAdapter.DeliveryState.APPLIED,
                delayed.queryStatus(token('d')).getState());
        assertEquals(SimulatedMediaEffectAdapter.PlaybackCommand.PLAY,
                delayed.getCurrentState().orElseThrow().getCommand());

        SimulationClock timeoutClock = new SimulationClock(1_000);
        SimulatedMediaEffectAdapter timeout = new SimulatedMediaEffectAdapter(
                timeoutClock, FaultInjectionProfile.timeout(20));
        applyMedia(timeout, token('e'), SimulatedMediaEffectAdapter.PlaybackCommand.PAUSE);
        assertTrue(timeout.getCurrentState().isEmpty());
        timeoutClock.advanceBy(20);
        assertEquals(SimulatedEffectAdapter.ReadbackState.TIMED_OUT,
                timeout.querySimulationObservation(token('e')).getState());

        SimulatedMediaEffectAdapter retry = mediaAdapter(
                FaultInjectionProfile.retryableFailure());
        assertEquals(EffectAdapter.ApplyState.RETRYABLE_FAILURE,
                applyMedia(retry, token('f'), SimulatedMediaEffectAdapter.PlaybackCommand.STOP)
                        .getState());
        assertTrue(retry.getCurrentState().isEmpty());

        SimulatedMediaEffectAdapter mismatch = mediaAdapter(
                FaultInjectionProfile.readbackMismatch());
        applyMedia(mismatch, token('1'), SimulatedMediaEffectAdapter.PlaybackCommand.PLAY);
        assertTrue(mismatch.getCurrentState().orElseThrow().isMismatchInjected());
        assertNotEquals(SimulatedMediaEffectAdapter.PlaybackCommand.PLAY,
                mismatch.getCurrentState().orElseThrow().getCommand());
        assertEquals(SimulatedEffectAdapter.ReadbackState.MISMATCH,
                mismatch.querySimulationObservation(token('1')).getState());
    }

    @Test
    public void navigationObservationIsDeterministicSyntheticAndDigestOnly() {
        String query = "最近的安全休息区";
        SimulatedNavigationEffectAdapter first = navigationAdapter(FaultInjectionProfile.none());
        SimulatedNavigationEffectAdapter second = navigationAdapter(FaultInjectionProfile.none());
        applyNavigation(first, token('2'), query);
        applyNavigation(second, token('3'), query);

        SimulatedNavigationEffectAdapter.NavigationObservation one =
                first.getObservation(token('2')).orElseThrow();
        SimulatedNavigationEffectAdapter.NavigationObservation two =
                second.getObservation(token('3')).orElseThrow();
        assertEquals(one.getQueryDigest(), two.getQueryDigest());
        assertEquals(one.getPoiId(), two.getPoiId());
        assertEquals(one.getRouteId(), two.getRouteId());
        assertEquals(one.getDistanceMeters(), two.getDistanceMeters());
        assertEquals(one.getDurationSeconds(), two.getDurationSeconds());
        assertFalse(one.getPoiId().contains(query));
        assertTrue(one.isSynthetic());
        assertEquals(SignalSource.SIMULATED, one.getSource());
        assertFalse(one.isProductionTrusted());
        assertFalse(one.isLocationUploaded());
        assertFalse(one.isExternalActivityStarted());
    }

    @Test
    public void navigationDelayFaultMismatchAndDuplicateDoNotFabricateResults() {
        SimulationClock clock = new SimulationClock(2_000);
        SimulatedNavigationEffectAdapter delayed = new SimulatedNavigationEffectAdapter(
                clock, FaultInjectionProfile.delay(100));
        EffectAdapter.Invocation invocation = navigationInvocation(token('4'), "服务区");
        delayed.apply(invocation);
        assertTrue(delayed.getAdmittedQueryDigest(token('4')).isPresent());
        assertTrue(delayed.getObservation(token('4')).isEmpty());
        clock.advanceBy(100);
        delayed.queryStatus(token('4'));
        long revision = delayed.getObservation(token('4')).orElseThrow().getRevision();
        delayed.apply(invocation);
        assertEquals(revision, delayed.getObservation(token('4')).orElseThrow().getRevision());

        SimulatedNavigationEffectAdapter terminal = navigationAdapter(
                FaultInjectionProfile.terminalFailure());
        assertEquals(EffectAdapter.ApplyState.TERMINAL_FAILURE,
                applyNavigation(terminal, token('5'), "停车区").getState());
        assertTrue(terminal.getObservation(token('5')).isEmpty());

        SimulatedNavigationEffectAdapter mismatch = navigationAdapter(
                FaultInjectionProfile.readbackMismatch());
        applyNavigation(mismatch, token('6'), "休息区");
        assertTrue(mismatch.getObservation(token('6')).orElseThrow().isMismatchInjected());
        assertEquals(SimulatedEffectAdapter.ReadbackState.MISMATCH,
                mismatch.querySimulationObservation(token('6')).getState());
    }

    @Test
    public void mediaAndNavigationTargetsFailClosedOnRangeActionAndPayloadDrift() {
        assertThrows(IllegalArgumentException.class,
                () -> SimulatedNavigationEffectAdapter.NavigationTarget.poi("x".repeat(129)));
        assertThrows(IllegalArgumentException.class,
                () -> SimulatedNavigationEffectAdapter.NavigationTarget.poi("bad\npoi"));

        byte[] mediaPayload = SimulatedMediaEffectAdapter.MediaTarget.playback(
                SimulatedMediaEffectAdapter.PlaybackCommand.PLAY).toCanonicalPayload();
        assertThrows(IllegalArgumentException.class,
                () -> SimulatedMediaEffectAdapter.MediaTarget.fromCanonicalPayload(
                        Arrays.copyOf(mediaPayload, mediaPayload.length + 1)));

        byte[] navPayload = SimulatedNavigationEffectAdapter.NavigationTarget.poi(
                "休息区").toCanonicalPayload();
        assertThrows(IllegalArgumentException.class,
                () -> SimulatedNavigationEffectAdapter.NavigationTarget.fromCanonicalPayload(
                        Arrays.copyOf(navPayload, navPayload.length + 1)));

        EffectAdapter.Invocation wrongAction = invocation(
                token('7'),
                SimulatedMediaEffectAdapter.DESTINATION,
                "media.next",
                mediaPayload);
        assertThrows(IllegalArgumentException.class,
                () -> mediaAdapter(FaultInjectionProfile.none()).apply(wrongAction));
    }

    @Test
    public void replaceableBackendsReceiveOnlyBoundedSimulatedInputs() {
        AtomicInteger mediaCalls = new AtomicInteger();
        SimulatedMediaEffectAdapter.MediaStateBackend mediaBackend =
                new SimulatedMediaEffectAdapter.MediaStateBackend() {
                    @Override
                    public SimulatedMediaEffectAdapter.MediaStateObservation apply(
                            SimulatedMediaEffectAdapter.PlaybackCommand command,
                            long revision,
                            long capturedAtElapsedRealtimeMs,
                            boolean injectMismatch) {
                        mediaCalls.incrementAndGet();
                        return new SimulatedMediaEffectAdapter.MediaStateObservation(
                                command, revision, capturedAtElapsedRealtimeMs, injectMismatch);
                    }

                    @Override
                    public void reset() {}
                };
        SimulatedMediaEffectAdapter media = new SimulatedMediaEffectAdapter(
                new SimulationClock(3_000), FaultInjectionProfile.none(), mediaBackend);
        applyMedia(media, token('8'), SimulatedMediaEffectAdapter.PlaybackCommand.PAUSE);
        assertEquals(1, mediaCalls.get());

        AtomicInteger navCalls = new AtomicInteger();
        SimulatedNavigationEffectAdapter.SyntheticNavigationBackend navBackend =
                new SimulatedNavigationEffectAdapter.SyntheticNavigationBackend() {
                    @Override
                    public SimulatedNavigationEffectAdapter.NavigationObservation resolve(
                            String queryDigest,
                            long revision,
                            long capturedAtElapsedRealtimeMs,
                            boolean injectMismatch) {
                        navCalls.incrementAndGet();
                        assertEquals(64, queryDigest.length());
                        return new SimulatedNavigationEffectAdapter.NavigationObservation(
                                queryDigest,
                                "synthetic.poi.test",
                                "synthetic.route.test",
                                "synthetic.navigation.test",
                                1_000,
                                300,
                                revision,
                                capturedAtElapsedRealtimeMs,
                                injectMismatch);
                    }

                    @Override
                    public void reset() {}
                };
        SimulatedNavigationEffectAdapter navigation = new SimulatedNavigationEffectAdapter(
                new SimulationClock(3_000), FaultInjectionProfile.none(), navBackend);
        applyNavigation(navigation, token('9'), "测试休息区");
        assertEquals(1, navCalls.get());
    }

    @Test
    public void unsafeBackendsAreRejectedBeforeAnyInvocation() {
        SimulatedMediaEffectAdapter.MediaStateBackend networkBackend =
                new SimulatedMediaEffectAdapter.MediaStateBackend() {
                    @Override
                    public SimulatedMediaEffectAdapter.MediaStateObservation apply(
                            SimulatedMediaEffectAdapter.PlaybackCommand command,
                            long revision,
                            long capturedAtElapsedRealtimeMs,
                            boolean injectMismatch) {
                        throw new AssertionError("must not run");
                    }

                    @Override
                    public void reset() {}

                    @Override
                    public boolean usesNetwork() {
                        return true;
                    }
                };
        assertThrows(IllegalArgumentException.class,
                () -> new SimulatedMediaEffectAdapter(
                        new SimulationClock(0), FaultInjectionProfile.none(), networkBackend));

        SimulatedNavigationEffectAdapter.SyntheticNavigationBackend uploadBackend =
                new SimulatedNavigationEffectAdapter.SyntheticNavigationBackend() {
                    @Override
                    public SimulatedNavigationEffectAdapter.NavigationObservation resolve(
                            String queryDigest,
                            long revision,
                            long capturedAtElapsedRealtimeMs,
                            boolean injectMismatch) {
                        throw new AssertionError("must not run");
                    }

                    @Override
                    public void reset() {}

                    @Override
                    public boolean uploadsLocation() {
                        return true;
                    }
                };
        assertThrows(IllegalArgumentException.class,
                () -> new SimulatedNavigationEffectAdapter(
                        new SimulationClock(0), FaultInjectionProfile.none(), uploadBackend));
    }

    private static SimulatedMediaEffectAdapter mediaAdapter(FaultInjectionProfile profile) {
        return new SimulatedMediaEffectAdapter(new SimulationClock(1_000), profile);
    }

    private static SimulatedNavigationEffectAdapter navigationAdapter(
            FaultInjectionProfile profile) {
        return new SimulatedNavigationEffectAdapter(new SimulationClock(1_000), profile);
    }

    private static EffectAdapter.ApplyResult applyMedia(
            SimulatedMediaEffectAdapter adapter,
            String token,
            SimulatedMediaEffectAdapter.PlaybackCommand command) {
        return adapter.apply(mediaInvocation(token, command));
    }

    private static EffectAdapter.Invocation mediaInvocation(
            String token,
            SimulatedMediaEffectAdapter.PlaybackCommand command) {
        SimulatedMediaEffectAdapter.MediaTarget target =
                SimulatedMediaEffectAdapter.MediaTarget.playback(command);
        return invocation(
                token,
                SimulatedMediaEffectAdapter.DESTINATION,
                target.getCapabilityId().getCanonicalId(),
                target.toCanonicalPayload());
    }

    private static EffectAdapter.ApplyResult applyNavigation(
            SimulatedNavigationEffectAdapter adapter,
            String token,
            String query) {
        return adapter.apply(navigationInvocation(token, query));
    }

    private static EffectAdapter.Invocation navigationInvocation(
            String token,
            String query) {
        SimulatedNavigationEffectAdapter.NavigationTarget target =
                SimulatedNavigationEffectAdapter.NavigationTarget.poi(query);
        return invocation(
                token,
                SimulatedNavigationEffectAdapter.DESTINATION,
                target.getCapabilityId().getCanonicalId(),
                target.toCanonicalPayload());
    }

    private static EffectAdapter.Invocation invocation(
            String token,
            String destination,
            String actionId,
            byte[] payload) {
        return new EffectAdapter.Invocation(
                "effect-media-nav-test",
                "outbox-media-nav-test",
                token,
                destination,
                actionId,
                sha256(payload),
                sha256(ENVELOPE),
                1,
                3,
                payload,
                ENVELOPE);
    }

    private static String token(char value) {
        char[] token = new char[64];
        Arrays.fill(token, value);
        return new String(token);
    }

    private static String sha256(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder output = new StringBuilder(digest.length * 2);
            for (byte current : digest) {
                output.append(String.format("%02x", current & 0xff));
            }
            return output.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
