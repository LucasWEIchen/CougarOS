package com.centralbrain.runtime.simulation;

import com.centralbrain.runtime.effects.EffectAdapter;
import com.centralbrain.runtime.vehicle.schema.SignalSource;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Bounded debug/test adapter base. It is absent from the production source set. */
public class SimulatedEffectAdapter implements EffectAdapter {
    public static final int MAX_RECORDS = 128;

    public enum ReadbackState {
        NOT_APPLIED,
        PENDING,
        MATCHED,
        MISMATCH,
        TIMED_OUT,
        RETRYABLE_FAILURE,
        TERMINAL_FAILURE
    }

    public static final class SimulationDescriptor {
        private final String adapterId;
        private final String destination;

        private SimulationDescriptor(String adapterId, String destination) {
            this.adapterId = adapterId;
            this.destination = destination;
        }

        public String getAdapterId() {
            return adapterId;
        }

        public String getDestination() {
            return destination;
        }

        public SignalSource getObservationSource() {
            return SignalSource.SIMULATED;
        }

        public boolean isSimulation() {
            return true;
        }

        public boolean isProductionAuthorized() {
            return false;
        }
    }

    public static final class SimulationObservation {
        private final String idempotencyToken;
        private final ReadbackState state;
        private final String evidenceDigest;
        private final long observedAtElapsedRealtimeMs;

        private SimulationObservation(
                String idempotencyToken,
                ReadbackState state,
                String evidenceDigest,
                long observedAtElapsedRealtimeMs) {
            this.idempotencyToken = idempotencyToken;
            this.state = state;
            this.evidenceDigest = evidenceDigest;
            this.observedAtElapsedRealtimeMs = observedAtElapsedRealtimeMs;
        }

        public String getIdempotencyToken() {
            return idempotencyToken;
        }

        public ReadbackState getState() {
            return state;
        }

        public String getEvidenceDigest() {
            return evidenceDigest;
        }

        public long getObservedAtElapsedRealtimeMs() {
            return observedAtElapsedRealtimeMs;
        }

        public SignalSource getSource() {
            return SignalSource.SIMULATED;
        }

        public boolean isProductionTrusted() {
            return false;
        }
    }

    private static final class Record {
        private final Invocation invocation;
        private final String invocationDigest;
        private final FaultInjectionProfile profile;
        private final ApplyResult originalResult;
        private final long readyAtElapsedRealtimeMs;
        private boolean appliedCallbackComplete;

        private Record(
                Invocation invocation,
                String invocationDigest,
                FaultInjectionProfile profile,
                ApplyResult originalResult,
                long readyAtElapsedRealtimeMs) {
            this.invocation = invocation;
            this.invocationDigest = invocationDigest;
            this.profile = profile;
            this.originalResult = originalResult;
            this.readyAtElapsedRealtimeMs = readyAtElapsedRealtimeMs;
        }
    }

    private final Descriptor descriptor;
    private final SimulationDescriptor simulationDescriptor;
    private final SimulationClock clock;
    private final Map<String, Record> records = new LinkedHashMap<>();
    private FaultInjectionProfile nextProfile;

    public SimulatedEffectAdapter(
            String adapterId,
            String destination,
            SimulationClock clock,
            FaultInjectionProfile initialProfile) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.nextProfile = Objects.requireNonNull(initialProfile, "initialProfile");
        this.descriptor = new Descriptor(
                adapterId,
                destination,
                IdempotencyMode.TOKEN_DEDUPLICATED,
                true,
                true,
                StatusConsistency.LINEARIZABLE,
                FaultInjectionProfile.MAX_DURATION_MS);
        this.simulationDescriptor = new SimulationDescriptor(adapterId, destination);
    }

    @Override
    public final Descriptor descriptor() {
        return descriptor;
    }

    public final SimulationDescriptor simulationDescriptor() {
        return simulationDescriptor;
    }

    public final synchronized void setFaultInjectionProfile(FaultInjectionProfile profile) {
        nextProfile = Objects.requireNonNull(profile, "profile");
    }

    public final synchronized FaultInjectionProfile getFaultInjectionProfile() {
        return nextProfile;
    }

    @Override
    public final synchronized ApplyResult apply(Invocation invocation) {
        Objects.requireNonNull(invocation, "invocation");
        if (!descriptor.getDestination().equals(invocation.getDestination())) {
            throw new IllegalArgumentException(
                    "CB_SIM_ADAPTER: invocation destination does not match adapter");
        }
        String token = invocation.getIdempotencyToken();
        String invocationDigest = invocationDigest(invocation);
        Record existing = records.get(token);
        if (existing != null) {
            if (!existing.invocationDigest.equals(invocationDigest)) {
                throw new IllegalStateException(
                        "CB_SIM_ADAPTER: idempotency token is bound to another invocation");
            }
            return existing.originalResult;
        }
        if (records.size() >= MAX_RECORDS) {
            throw new AdapterUnavailableException(
                    "CB_SIM_ADAPTER: simulation record capacity exceeded");
        }

        validateSimulationInvocation(invocation);
        FaultInjectionProfile profile = nextProfile;
        long now = clock.nowElapsedRealtimeMs();
        long readyAt = profile.getDurationMs() == 0
                ? now : safeAdd(now, profile.getDurationMs());
        String evidence = digest(
                "central-brain-simulated-effect-apply-v1",
                token,
                invocationDigest,
                profile.getDigest());
        ApplyResult result = new ApplyResult(token, applyState(profile), evidence);
        Record record = new Record(
                invocation, invocationDigest, profile, result, readyAt);
        records.put(token, record);
        completeIfReady(record);
        return result;
    }

    @Override
    public final synchronized StatusResult queryStatus(String idempotencyToken) {
        requireToken(idempotencyToken);
        Record record = records.get(idempotencyToken);
        if (record == null) {
            return new StatusResult(
                    idempotencyToken,
                    DeliveryState.NOT_APPLIED,
                    digest("central-brain-simulated-effect-absent-v1", idempotencyToken));
        }
        completeIfReady(record);
        return new StatusResult(
                idempotencyToken,
                deliveryState(record),
                record.originalResult.getEvidenceDigest());
    }

    public final synchronized SimulationObservation querySimulationObservation(
            String idempotencyToken) {
        requireToken(idempotencyToken);
        long now = clock.nowElapsedRealtimeMs();
        Record record = records.get(idempotencyToken);
        ReadbackState state;
        String evidence;
        if (record == null) {
            state = ReadbackState.NOT_APPLIED;
            evidence = digest("central-brain-simulated-readback-absent-v1", idempotencyToken);
        } else {
            completeIfReady(record);
            state = readbackState(record, now);
            evidence = digest(
                    "central-brain-simulated-readback-v1",
                    idempotencyToken,
                    record.originalResult.getEvidenceDigest(),
                    state.name());
        }
        return new SimulationObservation(idempotencyToken, state, evidence, now);
    }

    public final synchronized int getRecordCount() {
        return records.size();
    }

    public final synchronized void reset() {
        records.clear();
        onSimulationReset();
    }

    protected void validateSimulationInvocation(Invocation invocation) {
        // Subclasses add capability/area/range checks before the record is admitted.
    }

    protected void onSimulationApplied(
            Invocation invocation, FaultInjectionProfile profile) {
        // Subclasses update only simulated desired/reported state here.
    }

    protected void onSimulationReset() {
        // Subclasses clear their own simulated state here.
    }

    private void completeIfReady(Record record) {
        if (record.appliedCallbackComplete) {
            return;
        }
        FaultInjectionProfile.Mode mode = record.profile.getMode();
        boolean shouldApply = mode == FaultInjectionProfile.Mode.NONE
                || mode == FaultInjectionProfile.Mode.READBACK_MISMATCH
                || mode == FaultInjectionProfile.Mode.DELAY
                        && clock.nowElapsedRealtimeMs() >= record.readyAtElapsedRealtimeMs;
        if (shouldApply) {
            onSimulationApplied(record.invocation, record.profile);
            record.appliedCallbackComplete = true;
        }
    }

    private DeliveryState deliveryState(Record record) {
        switch (record.profile.getMode()) {
            case NONE:
            case READBACK_MISMATCH:
                return DeliveryState.APPLIED;
            case DELAY:
                return record.appliedCallbackComplete
                        ? DeliveryState.APPLIED : DeliveryState.UNKNOWN;
            case TIMEOUT:
                return DeliveryState.UNKNOWN;
            case RETRYABLE_FAILURE:
                return DeliveryState.NOT_APPLIED;
            case TERMINAL_FAILURE:
                return DeliveryState.REJECTED;
            default:
                throw new IllegalStateException("CB_SIM_ADAPTER: unknown fault mode");
        }
    }

    private ReadbackState readbackState(Record record, long now) {
        switch (record.profile.getMode()) {
            case NONE:
                return ReadbackState.MATCHED;
            case DELAY:
                return record.appliedCallbackComplete
                        ? ReadbackState.MATCHED : ReadbackState.PENDING;
            case TIMEOUT:
                return now >= record.readyAtElapsedRealtimeMs
                        ? ReadbackState.TIMED_OUT : ReadbackState.PENDING;
            case RETRYABLE_FAILURE:
                return ReadbackState.RETRYABLE_FAILURE;
            case TERMINAL_FAILURE:
                return ReadbackState.TERMINAL_FAILURE;
            case READBACK_MISMATCH:
                return ReadbackState.MISMATCH;
            default:
                throw new IllegalStateException("CB_SIM_ADAPTER: unknown fault mode");
        }
    }

    private static ApplyState applyState(FaultInjectionProfile profile) {
        switch (profile.getMode()) {
            case NONE:
            case READBACK_MISMATCH:
                return ApplyState.APPLIED;
            case DELAY:
            case TIMEOUT:
                return ApplyState.UNKNOWN;
            case RETRYABLE_FAILURE:
                return ApplyState.RETRYABLE_FAILURE;
            case TERMINAL_FAILURE:
                return ApplyState.TERMINAL_FAILURE;
            default:
                throw new IllegalStateException("CB_SIM_ADAPTER: unknown fault mode");
        }
    }

    private static String invocationDigest(Invocation invocation) {
        return digest(
                "central-brain-simulated-effect-invocation-v1",
                invocation.getEffectId(),
                invocation.getOutboxId(),
                invocation.getIdempotencyToken(),
                invocation.getDestination(),
                invocation.getActionId(),
                invocation.getPayloadDigest(),
                invocation.getEnvelopeDigest());
    }

    private static long safeAdd(long first, long second) {
        if (first > Long.MAX_VALUE - second) {
            throw new IllegalArgumentException("CB_SIM_ADAPTER: simulation time overflow");
        }
        return first + second;
    }

    private static void requireToken(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "CB_SIM_ADAPTER: idempotency token is invalid");
        }
    }

    private static String digest(String... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : values) {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                digest.update(bytes);
            }
            byte[] bytes = digest.digest();
            char[] output = new char[bytes.length * 2];
            char[] digits = "0123456789abcdef".toCharArray();
            for (int index = 0; index < bytes.length; index++) {
                int current = bytes[index] & 0xff;
                output[index * 2] = digits[current >>> 4];
                output[index * 2 + 1] = digits[current & 0x0f];
            }
            return new String(output);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "CB_SIM_ADAPTER: SHA-256 unavailable", exception);
        }
    }
}
