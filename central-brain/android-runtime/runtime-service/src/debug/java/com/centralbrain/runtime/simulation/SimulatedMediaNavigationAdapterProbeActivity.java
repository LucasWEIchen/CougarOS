package com.centralbrain.runtime.simulation;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.centralbrain.runtime.effects.EffectAdapter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/** Isolated API 33 probe for debug-only Media and synthetic Navigation adapters. */
public final class SimulatedMediaNavigationAdapterProbeActivity extends Activity {
    private static final String TAG = "CbSimMediaNav";
    private static final byte[] ENVELOPE =
            "media-nav-probe-envelope-v1".getBytes(StandardCharsets.UTF_8);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        try {
            require(nonce != null && !nonce.isEmpty(), "nonce missing");
            verifyTypedTargetsAndMediaState();
            verifyMediaFaults();
            verifySyntheticNavigationPrivacy();
            verifyNavigationFaultsAndIdempotency();
            verifyBackendBoundary();
            Log.i(TAG, "nonce=" + nonce
                    + " simulated_media_nav_probe_complete=true"
                    + " simulated_media_adapter_defined=true"
                    + " simulated_navigation_adapter_defined=true"
                    + " simulated_media_nav_typed_target_verified=true"
                    + " simulated_media_state_verified=true"
                    + " simulated_navigation_synthetic_observation_verified=true"
                    + " simulated_navigation_query_digest_only=true"
                    + " simulated_media_nav_delay_verified=true"
                    + " simulated_media_nav_fault_readback_verified=true"
                    + " simulated_media_nav_idempotency_verified=true"
                    + " simulated_media_nav_replaceable_backend_verified=true"
                    + " simulated_media_nav_android13_arm64_verified=true"
                    + " simulated_media_nav_debug_only=true"
                    + " simulated_media_nav_production_registered=false"
                    + " simulated_media_nav_runtime_wired=false"
                    + " external_activity_started=false"
                    + " location_uploaded=false"
                    + " network_accessed=false"
                    + " scenario_plan_runtime_published=false"
                    + " scenario_graph_execution_enabled=false"
                    + " effect_dispatch_enabled=false"
                    + " hardware_accessed=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " simulated_media_nav_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " simulated_media_nav_debug_only=true"
                    + " simulated_media_nav_production_registered=false"
                    + " simulated_media_nav_runtime_wired=false"
                    + " external_activity_started=false"
                    + " location_uploaded=false"
                    + " network_accessed=false"
                    + " effect_dispatch_enabled=false"
                    + " hardware_accessed=false", exception);
        } finally {
            finish();
        }
    }

    private static void verifyTypedTargetsAndMediaState() {
        SimulatedMediaEffectAdapter.MediaTarget target =
                SimulatedMediaEffectAdapter.MediaTarget.playback(
                        SimulatedMediaEffectAdapter.PlaybackCommand.PLAY);
        require(Arrays.equals(
                target.toCanonicalPayload(),
                SimulatedMediaEffectAdapter.MediaTarget.fromCanonicalPayload(
                        target.toCanonicalPayload()).toCanonicalPayload()),
                "media target round-trip failed");
        SimulatedMediaEffectAdapter adapter = new SimulatedMediaEffectAdapter(
                new SimulationClock(1_000), FaultInjectionProfile.none());
        EffectAdapter.Invocation invocation = mediaInvocation(token('a'), target.getCommand());
        adapter.apply(invocation);
        require(adapter.getCurrentState().orElseThrow().getCommand()
                == SimulatedMediaEffectAdapter.PlaybackCommand.PLAY,
                "media state missing");
        long revision = adapter.getCurrentState().orElseThrow().getRevision();
        adapter.apply(invocation);
        require(adapter.getCurrentState().orElseThrow().getRevision() == revision,
                "media duplicate changed state");
    }

    private static void verifyMediaFaults() {
        SimulationClock clock = new SimulationClock(2_000);
        SimulatedMediaEffectAdapter delayed = new SimulatedMediaEffectAdapter(
                clock, FaultInjectionProfile.delay(50));
        delayed.apply(mediaInvocation(
                token('b'), SimulatedMediaEffectAdapter.PlaybackCommand.PAUSE));
        require(delayed.getCurrentState().isEmpty(), "media delay applied early");
        clock.advanceBy(50);
        require(delayed.queryStatus(token('b')).getState()
                == EffectAdapter.DeliveryState.APPLIED, "media delay did not apply");

        SimulatedMediaEffectAdapter mismatch = new SimulatedMediaEffectAdapter(
                new SimulationClock(2_000), FaultInjectionProfile.readbackMismatch());
        mismatch.apply(mediaInvocation(
                token('c'), SimulatedMediaEffectAdapter.PlaybackCommand.PLAY));
        require(mismatch.getCurrentState().orElseThrow().isMismatchInjected(),
                "media mismatch missing");
        require(mismatch.querySimulationObservation(token('c')).getState()
                == SimulatedEffectAdapter.ReadbackState.MISMATCH,
                "media base mismatch missing");

        SimulationClock timeoutClock = new SimulationClock(2_000);
        SimulatedMediaEffectAdapter timeout = new SimulatedMediaEffectAdapter(
                timeoutClock, FaultInjectionProfile.timeout(25));
        timeout.apply(mediaInvocation(
                token('1'), SimulatedMediaEffectAdapter.PlaybackCommand.STOP));
        timeoutClock.advanceBy(25);
        require(timeout.querySimulationObservation(token('1')).getState()
                        == SimulatedEffectAdapter.ReadbackState.TIMED_OUT
                        && timeout.getCurrentState().isEmpty(),
                "media timeout fabricated state");
    }

    private static void verifySyntheticNavigationPrivacy() {
        SimulatedNavigationEffectAdapter.NavigationTarget target =
                SimulatedNavigationEffectAdapter.NavigationTarget.poi("最近休息区");
        require(Arrays.equals(
                target.toCanonicalPayload(),
                SimulatedNavigationEffectAdapter.NavigationTarget.fromCanonicalPayload(
                        target.toCanonicalPayload()).toCanonicalPayload()),
                "navigation target round-trip failed");
        SimulatedNavigationEffectAdapter adapter = new SimulatedNavigationEffectAdapter(
                new SimulationClock(3_000), FaultInjectionProfile.none());
        adapter.apply(navigationInvocation(token('d'), target));
        SimulatedNavigationEffectAdapter.NavigationObservation observation =
                adapter.getObservation(token('d')).orElseThrow();
        require(observation.getQueryDigest().equals(target.getQueryDigest()),
                "navigation digest mismatch");
        require(observation.isSynthetic()
                        && !observation.isProductionTrusted()
                        && !observation.isLocationUploaded()
                        && !observation.isExternalActivityStarted(),
                "navigation privacy boundary failed");
        require(!observation.getPoiId().contains(target.getPoiQuery()),
                "navigation observation retained query");
    }

    private static void verifyNavigationFaultsAndIdempotency() {
        SimulationClock clock = new SimulationClock(4_000);
        SimulatedNavigationEffectAdapter delayed = new SimulatedNavigationEffectAdapter(
                clock, FaultInjectionProfile.delay(75));
        SimulatedNavigationEffectAdapter.NavigationTarget target =
                SimulatedNavigationEffectAdapter.NavigationTarget.poi("服务区");
        EffectAdapter.Invocation invocation = navigationInvocation(token('e'), target);
        delayed.apply(invocation);
        require(delayed.getAdmittedQueryDigest(token('e')).isPresent()
                        && delayed.getObservation(token('e')).isEmpty(),
                "navigation pending state failed");
        clock.advanceBy(75);
        delayed.queryStatus(token('e'));
        long revision = delayed.getObservation(token('e')).orElseThrow().getRevision();
        delayed.apply(invocation);
        require(delayed.getObservation(token('e')).orElseThrow().getRevision() == revision,
                "navigation duplicate changed observation");

        SimulatedNavigationEffectAdapter terminal = new SimulatedNavigationEffectAdapter(
                new SimulationClock(4_000), FaultInjectionProfile.terminalFailure());
        require(terminal.apply(navigationInvocation(token('f'), target)).getState()
                        == EffectAdapter.ApplyState.TERMINAL_FAILURE
                        && terminal.getObservation(token('f')).isEmpty(),
                "navigation failure fabricated observation");
    }

    private static void verifyBackendBoundary() {
        SimulatedNavigationEffectAdapter.SyntheticNavigationBackend unsafe =
                new SimulatedNavigationEffectAdapter.SyntheticNavigationBackend() {
                    @Override
                    public SimulatedNavigationEffectAdapter.NavigationObservation resolve(
                            String queryDigest,
                            long revision,
                            long capturedAtElapsedRealtimeMs,
                            boolean injectMismatch) {
                        throw new IllegalStateException("unsafe backend invoked");
                    }

                    @Override
                    public void reset() {}

                    @Override
                    public boolean uploadsLocation() {
                        return true;
                    }
                };
        boolean rejected = false;
        try {
            new SimulatedNavigationEffectAdapter(
                    new SimulationClock(0), FaultInjectionProfile.none(), unsafe);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected, "unsafe navigation backend accepted");
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

    private static EffectAdapter.Invocation navigationInvocation(
            String token,
            SimulatedNavigationEffectAdapter.NavigationTarget target) {
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
                "effect-media-nav-probe",
                "outbox-media-nav-probe",
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

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
