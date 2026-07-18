package com.centralbrain.runtime.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Metadata-only inventory for the P9-W03c public AIDL and validation boundaries. */
public final class SecurityBoundaryInventoryContract {
    public static final int SCHEMA_VERSION = 1;
    public static final String PROFILE_ID = "android13-p9-security-boundary-inventory-v1";
    public static final int AIDL_INTERFACE_COUNT = 7;
    public static final int AIDL_PARCELABLE_COUNT = 30;
    public static final int AIDL_SURFACE_COUNT = AIDL_INTERFACE_COUNT + AIDL_PARCELABLE_COUNT;
    public static final int VALIDATION_FAMILY_COUNT = 8;

    private static final List<NamespaceCount> NAMESPACES = buildNamespaces();
    private static final String INVENTORY_DIGEST = sha256(canonicalInventory());

    private SecurityBoundaryInventoryContract() {
    }

    public enum ValidationFamily {
        SESSION_CONTRACT,
        PLAN_CONTRACT,
        EVENT_CONTRACT,
        EFFECT_CONTRACT,
        CHECKPOINT,
        SCENARIO_MANIFEST,
        TOOL_SCHEMA,
        STRUCTURED_MODEL_OUTPUT
    }

    public static final class NamespaceCount {
        private final String namespace;
        private final int interfaceCount;
        private final int parcelableCount;

        private NamespaceCount(String namespace, int interfaceCount, int parcelableCount) {
            this.namespace = Objects.requireNonNull(namespace, "namespace");
            if (!namespace.matches("[a-z][a-z0-9_]{2,31}")) {
                throw new IllegalArgumentException("AIDL namespace is invalid");
            }
            if (interfaceCount < 0 || parcelableCount < 0
                    || interfaceCount + parcelableCount < 1) {
                throw new IllegalArgumentException("AIDL namespace counts are invalid");
            }
            this.interfaceCount = interfaceCount;
            this.parcelableCount = parcelableCount;
        }

        public String getNamespace() {
            return namespace;
        }

        public int getInterfaceCount() {
            return interfaceCount;
        }

        public int getParcelableCount() {
            return parcelableCount;
        }

        public int getSurfaceCount() {
            return interfaceCount + parcelableCount;
        }

        private String canonicalForm() {
            return namespace + '|' + interfaceCount + '|' + parcelableCount;
        }
    }

    public static List<NamespaceCount> namespaces() {
        return NAMESPACES;
    }

    public static String inventoryDigest() {
        return INVENTORY_DIGEST;
    }

    public static boolean isAidlParcelInventoryComplete() {
        return true;
    }

    public static boolean isHostPathOversizeAggregateVerified() {
        return true;
    }

    public static boolean isAndroidDebugProbeAvailable() {
        return true;
    }

    public static boolean isAndroidDebugProbeExecuted() {
        return false;
    }

    public static boolean isCoverageGuidedFuzzComplete() {
        return false;
    }

    public static boolean isBinderCallingUidSpoofAndroidVerified() {
        return false;
    }

    public static boolean isPackageSignatureCryptographicallyVerified() {
        return false;
    }

    public static boolean isAndroid13Arm64Verified() {
        return false;
    }

    public static boolean isRuntimeWired() {
        return false;
    }

    public static boolean isHardwareAccessed() {
        return false;
    }

    public static boolean isProductionReady() {
        return false;
    }

    public static boolean isTargetHardwareValidated() {
        return false;
    }

    private static List<NamespaceCount> buildNamespaces() {
        List<NamespaceCount> values = new ArrayList<>();
        values.add(new NamespaceCount("diagnostics", 1, 3));
        values.add(new NamespaceCount("effect", 0, 4));
        values.add(new NamespaceCount("event", 2, 5));
        values.add(new NamespaceCount("governance", 1, 4));
        values.add(new NamespaceCount("plan", 0, 4));
        values.add(new NamespaceCount("production", 2, 5));
        values.add(new NamespaceCount("session", 1, 5));

        int interfaces = values.stream().mapToInt(NamespaceCount::getInterfaceCount).sum();
        int parcelables = values.stream().mapToInt(NamespaceCount::getParcelableCount).sum();
        if (interfaces != AIDL_INTERFACE_COUNT || parcelables != AIDL_PARCELABLE_COUNT) {
            throw new IllegalStateException("AIDL inventory counts changed");
        }
        if (ValidationFamily.values().length != VALIDATION_FAMILY_COUNT) {
            throw new IllegalStateException("validation family count changed");
        }
        return Collections.unmodifiableList(values);
    }

    private static String canonicalInventory() {
        StringBuilder value = new StringBuilder()
                .append(SCHEMA_VERSION).append('|').append(PROFILE_ID)
                .append('|').append(AIDL_INTERFACE_COUNT)
                .append('|').append(AIDL_PARCELABLE_COUNT)
                .append('|').append(VALIDATION_FAMILY_COUNT);
        for (NamespaceCount namespace : NAMESPACES) {
            value.append('|').append(namespace.canonicalForm());
        }
        for (ValidationFamily family : ValidationFamily.values()) {
            value.append('|').append(family.name());
        }
        return value.toString();
    }

    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte current : bytes) {
                result.append(Character.forDigit((current >>> 4) & 0xf, 16));
                result.append(Character.forDigit(current & 0xf, 16));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
