package com.centralbrain.runtime.effects;

import com.centralbrain.sdk.effect.EffectIntent;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Immutable capability/area/profile registry with no simulation-to-production fallback. */
public final class AdapterRegistry {
    private static final Pattern FAILURE_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    public enum Profile {
        DEBUG_SIMULATION,
        PRODUCTION
    }

    private final Map<String, Registration> registrations;

    public AdapterRegistry(List<Registration> source) {
        Objects.requireNonNull(source, "source");
        if (source.size() > 64) {
            throw violation("adapter registration count exceeds 64");
        }
        Map<String, Registration> copy = new LinkedHashMap<>();
        for (Registration candidate : source) {
            Registration registration = Objects.requireNonNull(candidate, "registration");
            String key = key(
                    registration.capabilityId,
                    registration.targetArea,
                    registration.profile);
            if (copy.put(key, registration) != null) {
                throw violation("duplicate adapter route registration");
            }
        }
        this.registrations = Collections.unmodifiableMap(copy);
    }

    public Resolution resolve(String capabilityId, String targetArea, Profile profile) {
        EffectBatch.requireQualifiedId(capabilityId, 96, "capabilityId");
        EffectBatch.requireQualifiedId(targetArea, 96, "targetArea");
        Objects.requireNonNull(profile, "profile");
        Registration registration = registrations.get(key(capabilityId, targetArea, profile));
        if (registration == null || !registration.activated) {
            throw unavailable("no activated adapter for the exact capability/area/profile");
        }
        if (profile == Profile.DEBUG_SIMULATION) {
            if (!registration.simulation || registration.productionAuthorized) {
                throw violation("debug route is not an isolated simulation adapter");
            }
        } else if (registration.simulation || !registration.productionAuthorized) {
            throw unavailable("production route is not explicitly authorized");
        }
        return new Resolution(registration, registration.descriptor);
    }

    public int size() {
        return registrations.size();
    }

    private static String key(String capabilityId, String targetArea, Profile profile) {
        return capabilityId + "\u0000" + targetArea + "\u0000" + profile.name();
    }

    private static AdapterUnavailableException unavailable(String message) {
        return new AdapterUnavailableException("CB_ERR_ADAPTER_UNAVAILABLE: " + message);
    }

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_ADAPTER_REGISTRY: " + message);
    }

    public interface PreparationAdapter {
        PrepareResult prepare(EffectIntent intent, long nowEpochMs);
    }

    public static final class Registration {
        private final String registrationId;
        private final String capabilityId;
        private final String targetArea;
        private final Profile profile;
        private final boolean activated;
        private final boolean simulation;
        private final boolean productionAuthorized;
        private final PreparationAdapter preparationAdapter;
        private final EffectAdapter adapter;
        private final EffectAdapter.Descriptor descriptor;

        public Registration(
                String registrationId,
                String capabilityId,
                String targetArea,
                Profile profile,
                boolean activated,
                boolean simulation,
                boolean productionAuthorized,
                PreparationAdapter preparationAdapter,
                EffectAdapter adapter) {
            this.registrationId = EffectBatch.requireQualifiedId(
                    registrationId, 96, "registrationId");
            this.capabilityId = EffectBatch.requireQualifiedId(
                    capabilityId, 96, "capabilityId");
            this.targetArea = EffectBatch.requireQualifiedId(
                    targetArea, 96, "targetArea");
            this.profile = Objects.requireNonNull(profile, "profile");
            this.activated = activated;
            this.simulation = simulation;
            this.productionAuthorized = productionAuthorized;
            this.preparationAdapter = Objects.requireNonNull(
                    preparationAdapter, "preparationAdapter");
            this.adapter = Objects.requireNonNull(adapter, "adapter");
            if (profile == Profile.DEBUG_SIMULATION
                    && (!simulation || productionAuthorized)) {
                throw violation("debug registration must be simulation-only");
            }
            if (profile == Profile.PRODUCTION && simulation) {
                throw violation("production registration cannot be simulated");
            }
            EffectAdapter.Descriptor declared = Objects.requireNonNull(
                    this.adapter.descriptor(), "adapter descriptor");
            this.descriptor = EffectAdapterContract.requireSafe(
                    this.adapter, declared.getDestination());
        }

        public String getRegistrationId() {
            return registrationId;
        }

        public String getCapabilityId() {
            return capabilityId;
        }

        public String getTargetArea() {
            return targetArea;
        }

        public Profile getProfile() {
            return profile;
        }

        public boolean isActivated() {
            return activated;
        }

        public boolean isSimulation() {
            return simulation;
        }

        public boolean isProductionAuthorized() {
            return productionAuthorized;
        }
    }

    public static final class Resolution {
        private final Registration registration;
        private final EffectAdapter.Descriptor descriptor;

        private Resolution(
                Registration registration,
                EffectAdapter.Descriptor descriptor) {
            this.registration = registration;
            this.descriptor = descriptor;
        }

        public String getRegistrationId() {
            return registration.registrationId;
        }

        public String getCapabilityId() {
            return registration.capabilityId;
        }

        public String getTargetArea() {
            return registration.targetArea;
        }

        public Profile getProfile() {
            return registration.profile;
        }

        public boolean isSimulation() {
            return registration.simulation;
        }

        public EffectAdapter.Descriptor getDescriptor() {
            return descriptor;
        }

        PreparationAdapter preparationAdapter() {
            return registration.preparationAdapter;
        }

        EffectAdapter adapter() {
            return registration.adapter;
        }
    }

    public static final class PrepareResult {
        public enum State {
            READY,
            REJECTED
        }

        private final State state;
        private final PreparedMaterial material;
        private final String failureCode;

        private PrepareResult(State state, PreparedMaterial material, String failureCode) {
            this.state = Objects.requireNonNull(state, "state");
            if (state == State.READY) {
                this.material = Objects.requireNonNull(material, "material");
                if (failureCode != null && !failureCode.isEmpty()) {
                    throw violation("ready preparation cannot carry a failure code");
                }
                this.failureCode = "";
            } else {
                if (material != null) {
                    throw violation("rejected preparation cannot carry material");
                }
                this.material = null;
                if (failureCode == null || !FAILURE_CODE.matcher(failureCode).matches()) {
                    throw violation("failureCode is not canonical");
                }
                this.failureCode = failureCode;
            }
        }

        public static PrepareResult ready(PreparedMaterial material) {
            return new PrepareResult(State.READY, material, "");
        }

        public static PrepareResult rejected(String failureCode) {
            return new PrepareResult(State.REJECTED, null, failureCode);
        }

        public State getState() {
            return state;
        }

        public PreparedMaterial getMaterial() {
            if (material == null) {
                throw violation("rejected preparation has no material");
            }
            return material;
        }

        public String getFailureCode() {
            return failureCode;
        }
    }

    public static final class PreparedMaterial {
        private final String actionId;
        private final String destination;
        private final byte[] canonicalPayload;
        private final byte[] canonicalEnvelope;
        private final String payloadDigest;
        private final String envelopeDigest;
        private final String beforeStateDigest;
        private final String preparationEvidenceDigest;

        public PreparedMaterial(
                String actionId,
                String destination,
                byte[] canonicalPayload,
                byte[] canonicalEnvelope,
                String beforeStateDigest,
                String preparationEvidenceDigest) {
            this.actionId = EffectBatch.canonicalUuid(actionId, "actionId");
            if (destination == null || destination.isEmpty() || destination.length() > 256) {
                throw violation("destination is invalid");
            }
            this.destination = destination;
            this.canonicalPayload = boundedCopy(
                    canonicalPayload, EffectAdapter.MAX_PAYLOAD_BYTES, "canonicalPayload");
            this.canonicalEnvelope = boundedCopy(
                    canonicalEnvelope, EffectAdapter.MAX_ENVELOPE_BYTES, "canonicalEnvelope");
            this.payloadDigest = sha256(this.canonicalPayload);
            this.envelopeDigest = sha256(this.canonicalEnvelope);
            this.beforeStateDigest = EffectBatch.requireDigest(
                    beforeStateDigest, "beforeStateDigest");
            this.preparationEvidenceDigest = EffectBatch.requireDigest(
                    preparationEvidenceDigest, "preparationEvidenceDigest");
        }

        public String getActionId() {
            return actionId;
        }

        public String getDestination() {
            return destination;
        }

        public byte[] getCanonicalPayload() {
            return Arrays.copyOf(canonicalPayload, canonicalPayload.length);
        }

        public byte[] getCanonicalEnvelope() {
            return Arrays.copyOf(canonicalEnvelope, canonicalEnvelope.length);
        }

        public String getPayloadDigest() {
            return payloadDigest;
        }

        public String getEnvelopeDigest() {
            return envelopeDigest;
        }

        public String getBeforeStateDigest() {
            return beforeStateDigest;
        }

        public String getPreparationEvidenceDigest() {
            return preparationEvidenceDigest;
        }

        private static byte[] boundedCopy(byte[] value, int maxBytes, String label) {
            if (value == null || value.length == 0 || value.length > maxBytes) {
                throw violation(label + " size is invalid");
            }
            return Arrays.copyOf(value, value.length);
        }

        private static String sha256(byte[] value) {
            try {
                return EffectBatch.hex(MessageDigest.getInstance("SHA-256").digest(value));
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 unavailable", exception);
            }
        }
    }

    public static final class AdapterUnavailableException extends IllegalStateException {
        private AdapterUnavailableException(String message) {
            super(message);
        }
    }
}
