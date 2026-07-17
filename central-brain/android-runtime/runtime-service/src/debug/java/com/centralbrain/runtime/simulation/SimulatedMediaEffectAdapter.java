package com.centralbrain.runtime.simulation;

import com.centralbrain.runtime.effects.EffectAdapter;
import com.centralbrain.runtime.vehicle.capability.CapabilityCatalog;
import com.centralbrain.runtime.vehicle.capability.VehicleCapability;
import com.centralbrain.runtime.vehicle.schema.SignalSource;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/** Debug-only media state adapter. It never controls an Android or vendor media player. */
public final class SimulatedMediaEffectAdapter extends SimulatedEffectAdapter {
    public static final String ADAPTER_ID = "debug.simulated.media.v1";
    public static final String DESTINATION = "media.player";
    public static final int TARGET_SCHEMA_VERSION = 1;

    private static final int TARGET_MAGIC = 0x43424d44;
    private static final int TARGET_FIXED_BYTES = Integer.BYTES * 5;

    public enum PlaybackCommand {
        PLAY,
        PAUSE,
        STOP
    }

    public interface MediaStateBackend {
        MediaStateObservation apply(
                PlaybackCommand command,
                long revision,
                long capturedAtElapsedRealtimeMs,
                boolean injectMismatch);

        void reset();

        default boolean isSimulationOnly() {
            return true;
        }

        default boolean isProductionAuthorized() {
            return false;
        }

        default boolean startsExternalActivity() {
            return false;
        }

        default boolean usesNetwork() {
            return false;
        }
    }

    public static final class MediaTarget {
        private final String area;
        private final PlaybackCommand command;

        private MediaTarget(String area, PlaybackCommand command) {
            this.area = requireArea(area);
            this.command = Objects.requireNonNull(command, "command");
        }

        public static MediaTarget playback(PlaybackCommand command) {
            return new MediaTarget("cabin", command);
        }

        public VehicleCapability.CapabilityId getCapabilityId() {
            return VehicleCapability.CapabilityId.MEDIA_PLAYBACK;
        }

        public String getArea() {
            return area;
        }

        public PlaybackCommand getCommand() {
            return command;
        }

        public byte[] toCanonicalPayload() {
            byte[] areaBytes = area.getBytes(StandardCharsets.UTF_8);
            byte[] commandBytes = command.name().getBytes(StandardCharsets.US_ASCII);
            ByteBuffer output = ByteBuffer.allocate(
                    TARGET_FIXED_BYTES + areaBytes.length + commandBytes.length);
            output.putInt(TARGET_MAGIC);
            output.putInt(TARGET_SCHEMA_VERSION);
            output.putInt(1);
            output.putInt(areaBytes.length);
            output.put(areaBytes);
            output.putInt(commandBytes.length);
            output.put(commandBytes);
            return output.array();
        }

        public static MediaTarget fromCanonicalPayload(byte[] payload) {
            Objects.requireNonNull(payload, "payload");
            if (payload.length < TARGET_FIXED_BYTES) {
                throw new IllegalArgumentException("CB_SIM_MEDIA: payload is truncated");
            }
            ByteBuffer input = ByteBuffer.wrap(payload);
            if (input.getInt() != TARGET_MAGIC
                    || input.getInt() != TARGET_SCHEMA_VERSION
                    || input.getInt() != 1) {
                throw new IllegalArgumentException("CB_SIM_MEDIA: payload header is invalid");
            }
            int areaLength = input.getInt();
            if (areaLength < 1 || areaLength > 32 || input.remaining() < areaLength + 4) {
                throw new IllegalArgumentException("CB_SIM_MEDIA: area length is invalid");
            }
            byte[] areaBytes = new byte[areaLength];
            input.get(areaBytes);
            int commandLength = input.getInt();
            if (commandLength < 1 || commandLength > 16 || input.remaining() != commandLength) {
                throw new IllegalArgumentException("CB_SIM_MEDIA: command length is invalid");
            }
            byte[] commandBytes = new byte[commandLength];
            input.get(commandBytes);
            PlaybackCommand command;
            try {
                command = PlaybackCommand.valueOf(
                        new String(commandBytes, StandardCharsets.US_ASCII));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "CB_SIM_MEDIA: command is unsupported", exception);
            }
            MediaTarget target = new MediaTarget(
                    new String(areaBytes, StandardCharsets.UTF_8), command);
            if (!Arrays.equals(payload, target.toCanonicalPayload())) {
                throw new IllegalArgumentException("CB_SIM_MEDIA: payload is not canonical");
            }
            return target;
        }
    }

    public static final class MediaStateObservation {
        private final PlaybackCommand command;
        private final long revision;
        private final long capturedAtElapsedRealtimeMs;
        private final boolean mismatchInjected;

        public MediaStateObservation(
                PlaybackCommand command,
                long revision,
                long capturedAtElapsedRealtimeMs,
                boolean mismatchInjected) {
            this.command = Objects.requireNonNull(command, "command");
            if (revision < 1 || capturedAtElapsedRealtimeMs < 0) {
                throw new IllegalArgumentException("CB_SIM_MEDIA: observation metadata is invalid");
            }
            this.revision = revision;
            this.capturedAtElapsedRealtimeMs = capturedAtElapsedRealtimeMs;
            this.mismatchInjected = mismatchInjected;
        }

        public PlaybackCommand getCommand() {
            return command;
        }

        public long getRevision() {
            return revision;
        }

        public long getCapturedAtElapsedRealtimeMs() {
            return capturedAtElapsedRealtimeMs;
        }

        public boolean isMismatchInjected() {
            return mismatchInjected;
        }

        public SignalSource getSource() {
            return SignalSource.SIMULATED;
        }

        public boolean isProductionTrusted() {
            return false;
        }
    }

    private static final class InMemoryMediaStateBackend implements MediaStateBackend {
        @Override
        public MediaStateObservation apply(
                PlaybackCommand command,
                long revision,
                long capturedAtElapsedRealtimeMs,
                boolean injectMismatch) {
            PlaybackCommand reported = injectMismatch ? mismatchFor(command) : command;
            return new MediaStateObservation(
                    reported, revision, capturedAtElapsedRealtimeMs, injectMismatch);
        }

        @Override
        public void reset() {
            // No independent state; the adapter owns the latest immutable observation.
        }
    }

    private final CapabilityCatalog capabilityCatalog = CapabilityCatalog.stage2Defaults();
    private final MediaStateBackend backend;
    private MediaStateObservation currentState;
    private long revision;

    public SimulatedMediaEffectAdapter(
            SimulationClock clock,
            FaultInjectionProfile initialProfile) {
        this(clock, initialProfile, new InMemoryMediaStateBackend());
    }

    public SimulatedMediaEffectAdapter(
            SimulationClock clock,
            FaultInjectionProfile initialProfile,
            MediaStateBackend backend) {
        super(ADAPTER_ID, DESTINATION, clock, initialProfile);
        this.backend = requireSafeBackend(backend);
    }

    public synchronized Optional<MediaStateObservation> getCurrentState() {
        return Optional.ofNullable(currentState);
    }

    @Override
    protected void validateSimulationInvocation(EffectAdapter.Invocation invocation) {
        MediaTarget target = decodeAndValidate(invocation);
        VehicleCapability capability = capabilityCatalog.require(target.getCapabilityId());
        if (!capability.getAvailability().isWritable()
                || !capability.getAvailability().isSimulatable()
                || capability.getAvailability().canUseProduction()
                || capability.getReportedSignalPath().isPresent()) {
            throw new IllegalStateException("CB_SIM_MEDIA: capability availability is unsafe");
        }
        if (!capability.getAreas().contains(target.getArea())) {
            throw new IllegalArgumentException("CB_SIM_MEDIA: area is unsupported");
        }
        capability.getTargetRange().validateText(target.getCommand().name());
    }

    @Override
    protected synchronized void onSimulationApplied(
            EffectAdapter.Invocation invocation,
            FaultInjectionProfile profile) {
        MediaTarget target = decodeAndValidate(invocation);
        requireSafeBackend(backend);
        boolean mismatch = profile.getMode()
                == FaultInjectionProfile.Mode.READBACK_MISMATCH;
        currentState = Objects.requireNonNull(
                backend.apply(
                        target.getCommand(),
                        ++revision,
                        nowSimulationElapsedRealtimeMs(),
                        mismatch),
                "backend observation");
        if (currentState.getRevision() != revision
                || currentState.getSource() != SignalSource.SIMULATED
                || currentState.isProductionTrusted()
                || currentState.isMismatchInjected() != mismatch
                || !mismatch && currentState.getCommand() != target.getCommand()
                || mismatch && currentState.getCommand() == target.getCommand()) {
            throw new IllegalStateException("CB_SIM_MEDIA: backend observation is unsafe");
        }
    }

    @Override
    protected synchronized void onSimulationReset() {
        currentState = null;
        revision = 0;
        backend.reset();
    }

    private MediaTarget decodeAndValidate(EffectAdapter.Invocation invocation) {
        if (!DESTINATION.equals(invocation.getDestination())) {
            throw new IllegalArgumentException("CB_SIM_MEDIA: destination is invalid");
        }
        MediaTarget target = MediaTarget.fromCanonicalPayload(
                invocation.getCanonicalPayload());
        if (!target.getCapabilityId().getCanonicalId().equals(invocation.getActionId())) {
            throw new IllegalArgumentException("CB_SIM_MEDIA: action is invalid");
        }
        return target;
    }

    private static MediaStateBackend requireSafeBackend(MediaStateBackend backend) {
        Objects.requireNonNull(backend, "backend");
        if (!backend.isSimulationOnly()
                || backend.isProductionAuthorized()
                || backend.startsExternalActivity()
                || backend.usesNetwork()) {
            throw new IllegalArgumentException("CB_SIM_MEDIA: backend crosses simulation boundary");
        }
        return backend;
    }

    private static PlaybackCommand mismatchFor(PlaybackCommand command) {
        switch (command) {
            case PLAY:
                return PlaybackCommand.PAUSE;
            case PAUSE:
                return PlaybackCommand.STOP;
            case STOP:
                return PlaybackCommand.PLAY;
            default:
                throw new IllegalStateException("CB_SIM_MEDIA: unknown command");
        }
    }

    private static String requireArea(String area) {
        if (!"cabin".equals(area)) {
            throw new IllegalArgumentException("CB_SIM_MEDIA: area must be cabin");
        }
        return area;
    }
}
