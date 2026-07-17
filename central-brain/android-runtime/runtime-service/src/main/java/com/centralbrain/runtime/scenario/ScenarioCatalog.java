package com.centralbrain.runtime.scenario;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Immutable catalog that isolates invalid build-owned manifests by source asset. */
public final class ScenarioCatalog {
    public static final int MAX_ASSETS = 64;

    public enum DisabledReason {
        INVALID_SOURCE,
        OVERSIZE,
        MALFORMED_JSON,
        DUPLICATE_FIELD,
        UNKNOWN_FIELD,
        TYPE_MISMATCH,
        VALIDATION_FAILED,
        DUPLICATE_SCENARIO_ID
    }

    public static final class DisabledManifest {
        private final String sourceName;
        private final DisabledReason reason;

        private DisabledManifest(String sourceName, DisabledReason reason) {
            this.sourceName = Objects.requireNonNull(sourceName, "sourceName");
            this.reason = Objects.requireNonNull(reason, "reason");
        }

        public String getSourceName() {
            return sourceName;
        }

        public DisabledReason getReason() {
            return reason;
        }
    }

    private static final class Candidate {
        private final String sourceName;
        private final ScenarioManifest manifest;

        private Candidate(String sourceName, ScenarioManifest manifest) {
            this.sourceName = sourceName;
            this.manifest = manifest;
        }
    }

    private final Map<String, ScenarioManifest> byId;
    private final List<ScenarioManifest> ordered;
    private final List<DisabledManifest> disabled;
    private final String catalogDigest;

    public static ScenarioCatalog load(Map<String, byte[]> assets) {
        Objects.requireNonNull(assets, "assets");
        if (assets.isEmpty() || assets.size() > MAX_ASSETS) {
            throw new IllegalArgumentException(
                    "CB_SCENARIO_CATALOG: asset count must be 1..64");
        }
        ScenarioManifestParser parser = new ScenarioManifestParser();
        List<String> sourceNames = new ArrayList<>(assets.keySet());
        sourceNames.sort(Comparator.nullsFirst(String::compareTo));
        List<Candidate> candidates = new ArrayList<>();
        List<DisabledManifest> disabled = new ArrayList<>();
        for (String sourceName : sourceNames) {
            try {
                candidates.add(new Candidate(
                        sourceName,
                        parser.parse(sourceName, assets.get(sourceName))));
            } catch (ScenarioManifestParser.ParseException exception) {
                disabled.add(new DisabledManifest(
                        sourceName == null ? "<invalid>" : sourceName,
                        DisabledReason.valueOf(exception.getErrorCode().name())));
            }
        }

        Map<String, List<Candidate>> grouped = new TreeMap<>();
        for (Candidate candidate : candidates) {
            grouped.computeIfAbsent(
                    candidate.manifest.getScenarioId(), ignored -> new ArrayList<>())
                    .add(candidate);
        }
        Map<String, ScenarioManifest> accepted = new LinkedHashMap<>();
        for (Map.Entry<String, List<Candidate>> entry : grouped.entrySet()) {
            if (entry.getValue().size() == 1) {
                accepted.put(entry.getKey(), entry.getValue().get(0).manifest);
                continue;
            }
            for (Candidate duplicate : entry.getValue()) {
                disabled.add(new DisabledManifest(
                        duplicate.sourceName,
                        DisabledReason.DUPLICATE_SCENARIO_ID));
            }
        }
        disabled.sort(Comparator.comparing(DisabledManifest::getSourceName));
        return new ScenarioCatalog(accepted, disabled);
    }

    private ScenarioCatalog(
            Map<String, ScenarioManifest> accepted,
            List<DisabledManifest> disabled) {
        this.byId = Collections.unmodifiableMap(new LinkedHashMap<>(accepted));
        this.ordered = Collections.unmodifiableList(new ArrayList<>(accepted.values()));
        this.disabled = Collections.unmodifiableList(new ArrayList<>(disabled));
        this.catalogDigest = digest(ordered, disabled);
    }

    public int size() {
        return ordered.size();
    }

    public List<ScenarioManifest> all() {
        return ordered;
    }

    public List<DisabledManifest> disabled() {
        return disabled;
    }

    public Optional<ScenarioManifest> find(String scenarioId) {
        return Optional.ofNullable(byId.get(scenarioId));
    }

    public ScenarioManifest require(String scenarioId) {
        ScenarioManifest manifest = byId.get(scenarioId);
        if (manifest == null) {
            throw new IllegalArgumentException(
                    "CB_SCENARIO_CATALOG: scenario is not enabled");
        }
        return manifest;
    }

    public String getCatalogDigest() {
        return catalogDigest;
    }

    public boolean isArtifactCryptographicallyVerified() {
        return false;
    }

    public boolean isProductionTrusted() {
        return false;
    }

    private static String digest(
            List<ScenarioManifest> manifests,
            List<DisabledManifest> disabled) {
        MessageDigest digest = sha256();
        update(digest, "central-brain-scenario-catalog-v1");
        update(digest, Integer.toString(ScenarioManifest.SCHEMA_VERSION));
        update(digest, Integer.toString(manifests.size()));
        for (ScenarioManifest manifest : manifests) {
            update(digest, manifest.getScenarioId());
            update(digest, Integer.toString(manifest.getVersion()));
            update(digest, manifest.getArtifactDigest());
        }
        update(digest, Integer.toString(disabled.size()));
        for (DisabledManifest failure : disabled) {
            update(digest, failure.sourceName);
            update(digest, failure.reason.name());
        }
        return toHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "CB_SCENARIO_CATALOG: SHA-256 unavailable", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static String toHex(byte[] bytes) {
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
