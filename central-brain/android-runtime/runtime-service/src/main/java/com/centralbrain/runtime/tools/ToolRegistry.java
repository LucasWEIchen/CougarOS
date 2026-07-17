package com.centralbrain.runtime.tools;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Immutable registry of static Tool manifests. It owns no health or execution state. */
public final class ToolRegistry {
    public static final int MAX_REGISTRATIONS = 128;

    private static final Pattern FAMILY_ID = Pattern.compile(
            "tool[.][a-z][a-z0-9_-]*(?:[.][a-z0-9][a-z0-9_-]*){1,6}");

    public enum ErrorCode {
        REGISTRATION_LIMIT_EXCEEDED,
        CONTRACT_CONFLICT
    }

    public static final class RegistrationException extends IllegalArgumentException {
        private final ErrorCode errorCode;

        RegistrationException(ErrorCode errorCode) {
            super("CB_TOOL_REGISTRY: " + errorCode.name());
            this.errorCode = errorCode;
        }

        public ErrorCode getErrorCode() {
            return errorCode;
        }
    }

    private final NavigableMap<String, NavigableMap<Integer, ToolManifest>> manifests;
    private final int size;
    private final String registryDigest;

    public ToolRegistry(List<ToolManifest> source) {
        Objects.requireNonNull(source, "source");
        if (source.size() > MAX_REGISTRATIONS) {
            throw new RegistrationException(ErrorCode.REGISTRATION_LIMIT_EXCEEDED);
        }
        TreeMap<String, TreeMap<Integer, ToolManifest>> collected = new TreeMap<>();
        int uniqueCount = 0;
        for (ToolManifest candidate : source) {
            ToolManifest manifest = Objects.requireNonNull(candidate, "manifest");
            TreeMap<Integer, ToolManifest> versions = collected.computeIfAbsent(
                    manifest.getFamilyId(), ignored -> new TreeMap<>());
            ToolManifest existing = versions.get(manifest.getVersion());
            if (existing != null) {
                if (!existing.getContractDigest().equals(manifest.getContractDigest())) {
                    throw new RegistrationException(ErrorCode.CONTRACT_CONFLICT);
                }
                continue;
            }
            versions.put(manifest.getVersion(), manifest);
            uniqueCount++;
        }
        TreeMap<String, NavigableMap<Integer, ToolManifest>> immutable = new TreeMap<>();
        for (Map.Entry<String, TreeMap<Integer, ToolManifest>> entry : collected.entrySet()) {
            immutable.put(
                    entry.getKey(),
                    Collections.unmodifiableNavigableMap(new TreeMap<>(entry.getValue())));
        }
        manifests = Collections.unmodifiableNavigableMap(immutable);
        size = uniqueCount;
        registryDigest = digest(manifests);
    }

    public int size() {
        return size;
    }

    public boolean isRegistered(String familyId) {
        return manifests.containsKey(requireFamilyId(familyId));
    }

    public boolean isRegistered(String familyId, int version) {
        if (version < 1) {
            throw new IllegalArgumentException("CB_TOOL_REGISTRY: version must be positive");
        }
        NavigableMap<Integer, ToolManifest> versions = manifests.get(
                requireFamilyId(familyId));
        return versions != null && versions.containsKey(version);
    }

    public List<ToolManifest> manifestsFor(String familyId) {
        NavigableMap<Integer, ToolManifest> versions = manifests.get(
                requireFamilyId(familyId));
        if (versions == null) {
            return List.of();
        }
        return Collections.unmodifiableList(new ArrayList<>(versions.values()));
    }

    public String getRegistryDigest() {
        return registryDigest;
    }

    ToolManifest highestCompatible(String familyId, int minimumVersion, int maximumVersion) {
        NavigableMap<Integer, ToolManifest> versions = manifests.get(familyId);
        if (versions == null) {
            return null;
        }
        Map.Entry<Integer, ToolManifest> selected = versions.floorEntry(maximumVersion);
        if (selected == null || selected.getKey() < minimumVersion) {
            return null;
        }
        return selected.getValue();
    }

    static String requireFamilyId(String familyId) {
        if (familyId == null || !FAMILY_ID.matcher(familyId).matches()) {
            throw new IllegalArgumentException("CB_TOOL_REGISTRY: familyId is not canonical");
        }
        return familyId;
    }

    private static String digest(
            NavigableMap<String, NavigableMap<Integer, ToolManifest>> source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, "central-brain-tool-registry-v1");
            for (Map.Entry<String, NavigableMap<Integer, ToolManifest>> family
                    : source.entrySet()) {
                update(digest, family.getKey());
                for (ToolManifest manifest : family.getValue().values()) {
                    update(digest, Integer.toString(manifest.getVersion()));
                    update(digest, manifest.getContractDigest());
                }
            }
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(Character.forDigit((value >>> 4) & 0xf, 16));
            result.append(Character.forDigit(value & 0xf, 16));
        }
        return result.toString();
    }
}
