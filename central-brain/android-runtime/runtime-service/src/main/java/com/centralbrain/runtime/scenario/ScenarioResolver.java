package com.centralbrain.runtime.scenario;

import com.centralbrain.runtime.context.ContextSnapshot;
import com.centralbrain.runtime.scenario.ScenarioManifest.Source;
import com.centralbrain.runtime.scenario.ScenarioManifest.Zone;
import com.centralbrain.runtime.vehicle.capability.CapabilityCatalog;
import com.centralbrain.runtime.vehicle.capability.VehicleCapability;
import com.centralbrain.runtime.vehicle.capability.VehicleCapability.CapabilityId;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Selects one registered scenario without compiling or executing its plan template. */
public interface ScenarioResolver {
    ScenarioResolution resolve(
            Request request,
            ScenarioCatalog catalog,
            ContextSnapshot context,
            CapabilitySnapshot capabilities);

    /** Bounded internal request derived from an admitted Session request. */
    final class Request {
        public static final int MAX_INTENT_CHARS = 256;
        private static final Pattern SCENARIO_ID = Pattern.compile(
                "[a-z][a-z0-9]*(?:[.][a-z0-9][a-z0-9_-]*){2,7}");

        private final String explicitScenarioId;
        private final String textIntent;
        private final Source source;
        private final Zone zone;
        private final String digest;

        public Request(
                String explicitScenarioId,
                String textIntent,
                Source source,
                Zone zone) {
            this.explicitScenarioId = bounded(
                    explicitScenarioId, 96, "explicitScenarioId");
            this.textIntent = bounded(textIntent, MAX_INTENT_CHARS, "textIntent");
            this.source = Objects.requireNonNull(source, "source");
            this.zone = Objects.requireNonNull(zone, "zone");
            if (this.explicitScenarioId.isEmpty() && this.textIntent.isEmpty()) {
                throw new IllegalArgumentException(
                        "CB_SCENARIO_RESOLVE: scenario ID or text intent is required");
            }
            if (!this.explicitScenarioId.isEmpty()
                    && !SCENARIO_ID.matcher(this.explicitScenarioId).matches()) {
                throw new IllegalArgumentException(
                        "CB_SCENARIO_RESOLVE: explicit scenario ID is invalid");
            }
            this.digest = digest(
                    this.explicitScenarioId,
                    this.textIntent,
                    source.name(),
                    zone.name());
        }

        public String getExplicitScenarioId() {
            return explicitScenarioId;
        }

        public String getTextIntent() {
            return textIntent;
        }

        public Source getSource() {
            return source;
        }

        public Zone getZone() {
            return zone;
        }

        public String getDigest() {
            return digest;
        }

        private static String bounded(String value, int maximum, String field) {
            String original = value == null ? "" : value;
            if (original.length() > maximum) {
                throw new IllegalArgumentException(
                        "CB_SCENARIO_RESOLVE: " + field + " is too long");
            }
            for (int index = 0; index < original.length(); index++) {
                if (Character.isISOControl(original.charAt(index))) {
                    throw new IllegalArgumentException(
                            "CB_SCENARIO_RESOLVE: " + field + " contains control characters");
                }
            }
            return original.trim();
        }
    }

    enum CapabilityProfile {
        SOFTWARE_SIMULATION,
        PRODUCTION
    }

    /** Immutable runtime-owned availability view; it does not discover or activate adapters. */
    final class CapabilitySnapshot {
        public static final int SCHEMA_VERSION = 1;

        private final long revision;
        private final CapabilityProfile profile;
        private final Map<CapabilityId, Boolean> available;
        private final String digest;

        private CapabilitySnapshot(
                long revision,
                CapabilityProfile profile,
                Map<CapabilityId, Boolean> available,
                String digest) {
            this.revision = revision;
            this.profile = profile;
            this.available = Collections.unmodifiableMap(new EnumMap<>(available));
            this.digest = digest;
        }

        public static CapabilitySnapshot capture(
                CapabilityCatalog catalog,
                CapabilityProfile profile,
                long revision,
                Set<CapabilityId> runtimeUnavailable) {
            Objects.requireNonNull(catalog, "catalog");
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(runtimeUnavailable, "runtimeUnavailable");
            if (revision < 1) {
                throw new IllegalArgumentException(
                        "CB_SCENARIO_RESOLVE: capability snapshot metadata is invalid");
            }
            for (CapabilityId id : runtimeUnavailable) {
                if (id == null) {
                    throw new IllegalArgumentException(
                            "CB_SCENARIO_RESOLVE: unavailable capability is null");
                }
                catalog.require(id);
            }
            EnumMap<CapabilityId, Boolean> values = new EnumMap<>(CapabilityId.class);
            MessageDigest digest = sha256();
            update(digest, "central-brain-scenario-capability-snapshot-v1");
            update(digest, Long.toString(revision));
            update(digest, profile.name());
            for (VehicleCapability capability : catalog.all()) {
                boolean enabled = profile == CapabilityProfile.SOFTWARE_SIMULATION
                        ? capability.getAvailability().isWritable()
                                && capability.getAvailability().isSimulatable()
                        : capability.getAvailability().canUseProduction();
                enabled &= !runtimeUnavailable.contains(capability.getId());
                values.put(capability.getId(), enabled);
                update(digest, capability.getId().getCanonicalId());
                update(digest, Boolean.toString(enabled));
            }
            return new CapabilitySnapshot(
                    revision,
                    profile,
                    values,
                    toHex(digest.digest()));
        }

        public long getRevision() {
            return revision;
        }

        public CapabilityProfile getProfile() {
            return profile;
        }

        public boolean isAvailable(CapabilityId id) {
            return Boolean.TRUE.equals(available.get(Objects.requireNonNull(id, "id")));
        }

        public Set<CapabilityId> getAvailableCapabilities() {
            Set<CapabilityId> result = new LinkedHashSet<>();
            for (Map.Entry<CapabilityId, Boolean> entry : available.entrySet()) {
                if (entry.getValue()) {
                    result.add(entry.getKey());
                }
            }
            return Collections.unmodifiableSet(result);
        }

        public String getDigest() {
            return digest;
        }

        public boolean isProductionTrusted() {
            return false;
        }
    }

    static String digest(String... values) {
        MessageDigest digest = sha256();
        update(digest, "central-brain-scenario-request-v1");
        for (String value : values) {
            update(digest, value);
        }
        return toHex(digest.digest());
    }

    static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "CB_SCENARIO_RESOLVE: SHA-256 unavailable", exception);
        }
    }

    static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    static String toHex(byte[] bytes) {
        char[] result = new char[bytes.length * 2];
        char[] alphabet = "0123456789abcdef".toCharArray();
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xff;
            result[index * 2] = alphabet[value >>> 4];
            result[index * 2 + 1] = alphabet[value & 0x0f];
        }
        return new String(result);
    }
}
