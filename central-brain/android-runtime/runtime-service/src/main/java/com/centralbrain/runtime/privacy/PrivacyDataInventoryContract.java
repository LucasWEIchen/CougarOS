package com.centralbrain.runtime.privacy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Metadata-only P9-W04a inventory of current Android data lifecycle boundaries. */
public final class PrivacyDataInventoryContract {
    public static final int SCHEMA_VERSION = 1;
    public static final String PROFILE_ID = "android13-p9-privacy-data-inventory-v1";
    public static final int SURFACE_COUNT = 12;
    public static final int DURABLE_SURFACE_COUNT = 6;
    public static final int PROCESS_LOCAL_SURFACE_COUNT = 5;
    public static final int TRANSIENT_SURFACE_COUNT = 1;
    public static final int POLICY_GAP_COUNT = 2;
    public static final int AUTHORIZED_EXPORT_SURFACE_COUNT = 1;

    private static final List<DataSurface> SURFACES = buildSurfaces();
    private static final String INVENTORY_DIGEST = sha256(canonicalInventory());

    private PrivacyDataInventoryContract() {
    }

    public enum Sensitivity {
        INTERNAL,
        SENSITIVE,
        RESTRICTED
    }

    public enum StorageMode {
        DURABLE_ROOM,
        PROCESS_LOCAL,
        TRANSIENT_ONLY
    }

    public enum ContentForm {
        FIXED_METADATA,
        DIGEST_METADATA,
        BOUNDED_OPAQUE_CONTENT,
        SEALED_TYPED_PREFERENCE,
        FIXED_ENUM_SUMMARY,
        TRANSIENT_MODEL_CONTENT
    }

    public enum OwnerScope {
        PRINCIPAL_SESSION,
        SESSION_TASK,
        PRINCIPAL,
        SESSION,
        TASK,
        SYSTEM
    }

    public enum ConsentMode {
        OPERATIONAL_POLICY,
        SESSION_POLICY,
        EXPLICIT_CONSENT,
        AUTHORITY_EVIDENCE,
        ACCESS_POLICY_EVIDENCE
    }

    public enum RetentionMode {
        TERMINAL_CAPACITY_BOUNDED,
        SESSION_CASCADE,
        EXPIRY_BOUNDED,
        CANCELLED_CAPACITY_BOUNDED,
        TTL_SESSION_TERMINAL,
        MAXIMUM_30_DAYS,
        CAPACITY_BOUNDED,
        CALL_ONLY,
        OWNER_POLICY_MISSING
    }

    public enum DeletionMode {
        TERMINAL_CASCADE,
        AUTHORIZED_DELETE,
        AUTHORIZED_ERASE,
        CAPACITY_EVICTION,
        NOT_STORED,
        NO_PUBLIC_DELETE
    }

    public enum ExportMode {
        FORBIDDEN,
        AUTHORIZED_BOUNDED
    }

    public enum LogMode {
        CONTENT_FORBIDDEN,
        DIGEST_METADATA_ONLY
    }

    public enum EnforcementState {
        PARTIAL_RUNTIME,
        CONTRACT_TEST_ONLY,
        TRANSIENT_ENFORCED,
        POLICY_GAP
    }

    public static final class DataSurface {
        private final String surfaceId;
        private final Sensitivity sensitivity;
        private final StorageMode storageMode;
        private final ContentForm contentForm;
        private final OwnerScope ownerScope;
        private final ConsentMode consentMode;
        private final RetentionMode retentionMode;
        private final DeletionMode deletionMode;
        private final ExportMode exportMode;
        private final LogMode logMode;
        private final EnforcementState enforcementState;
        private final boolean acceptsContentPayload;
        private final List<String> sourceClasses;

        private DataSurface(
                String surfaceId,
                Sensitivity sensitivity,
                StorageMode storageMode,
                ContentForm contentForm,
                OwnerScope ownerScope,
                ConsentMode consentMode,
                RetentionMode retentionMode,
                DeletionMode deletionMode,
                ExportMode exportMode,
                LogMode logMode,
                EnforcementState enforcementState,
                boolean acceptsContentPayload,
                String... sourceClasses) {
            this.surfaceId = requireSurfaceId(surfaceId);
            this.sensitivity = Objects.requireNonNull(sensitivity, "sensitivity");
            this.storageMode = Objects.requireNonNull(storageMode, "storageMode");
            this.contentForm = Objects.requireNonNull(contentForm, "contentForm");
            this.ownerScope = Objects.requireNonNull(ownerScope, "ownerScope");
            this.consentMode = Objects.requireNonNull(consentMode, "consentMode");
            this.retentionMode = Objects.requireNonNull(retentionMode, "retentionMode");
            this.deletionMode = Objects.requireNonNull(deletionMode, "deletionMode");
            this.exportMode = Objects.requireNonNull(exportMode, "exportMode");
            this.logMode = Objects.requireNonNull(logMode, "logMode");
            this.enforcementState = Objects.requireNonNull(
                    enforcementState, "enforcementState");
            this.acceptsContentPayload = acceptsContentPayload;
            if (sourceClasses.length < 1 || sourceClasses.length > 8) {
                throw new IllegalArgumentException("sourceClasses count is invalid");
            }
            List<String> sources = new ArrayList<>(sourceClasses.length);
            for (String sourceClass : sourceClasses) {
                if (sourceClass == null
                        || !sourceClass.matches(
                                "com/centralbrain/runtime/[a-z]+/[A-Za-z0-9]+[.]java")) {
                    throw new IllegalArgumentException("sourceClass is invalid");
                }
                if (sources.contains(sourceClass)) {
                    throw new IllegalArgumentException("sourceClass is duplicated");
                }
                sources.add(sourceClass);
            }
            this.sourceClasses = Collections.unmodifiableList(sources);
            validatePolicyCombination();
        }

        public String getSurfaceId() {
            return surfaceId;
        }

        public Sensitivity getSensitivity() {
            return sensitivity;
        }

        public StorageMode getStorageMode() {
            return storageMode;
        }

        public ContentForm getContentForm() {
            return contentForm;
        }

        public OwnerScope getOwnerScope() {
            return ownerScope;
        }

        public ConsentMode getConsentMode() {
            return consentMode;
        }

        public RetentionMode getRetentionMode() {
            return retentionMode;
        }

        public DeletionMode getDeletionMode() {
            return deletionMode;
        }

        public ExportMode getExportMode() {
            return exportMode;
        }

        public LogMode getLogMode() {
            return logMode;
        }

        public EnforcementState getEnforcementState() {
            return enforcementState;
        }

        public boolean acceptsContentPayload() {
            return acceptsContentPayload;
        }

        public List<String> getSourceClasses() {
            return sourceClasses;
        }

        private void validatePolicyCombination() {
            if (storageMode == StorageMode.TRANSIENT_ONLY
                    != (deletionMode == DeletionMode.NOT_STORED)) {
                throw new IllegalArgumentException("transient deletion policy is inconsistent");
            }
            if (enforcementState == EnforcementState.POLICY_GAP
                    != (retentionMode == RetentionMode.OWNER_POLICY_MISSING)) {
                throw new IllegalArgumentException("policy-gap retention is inconsistent");
            }
            if (exportMode == ExportMode.AUTHORIZED_BOUNDED
                    && (consentMode != ConsentMode.EXPLICIT_CONSENT
                    || deletionMode != DeletionMode.AUTHORIZED_DELETE)) {
                throw new IllegalArgumentException("authorized export policy is inconsistent");
            }
            if (acceptsContentPayload && logMode != LogMode.CONTENT_FORBIDDEN) {
                throw new IllegalArgumentException("content payload logging must be forbidden");
            }
        }

        private String canonicalForm() {
            return surfaceId + '|' + sensitivity.name() + '|' + storageMode.name()
                    + '|' + contentForm.name() + '|' + ownerScope.name()
                    + '|' + consentMode.name() + '|' + retentionMode.name()
                    + '|' + deletionMode.name() + '|' + exportMode.name()
                    + '|' + logMode.name() + '|' + enforcementState.name()
                    + '|' + acceptsContentPayload + '|'
                    + String.join(",", sourceClasses);
        }
    }

    public static List<DataSurface> surfaces() {
        return SURFACES;
    }

    public static String inventoryDigest() {
        return INVENTORY_DIGEST;
    }

    public static boolean isInventoryComplete() {
        return true;
    }

    public static boolean isRawUserTextPersisted() {
        return false;
    }

    public static boolean isRawModelOutputPersisted() {
        return false;
    }

    public static boolean isRawVehiclePayloadPersisted() {
        return false;
    }

    public static boolean isLocationPersisted() {
        return false;
    }

    public static boolean isAuditContentLogged() {
        return false;
    }

    public static boolean isOwnerPolicyApproved() {
        return false;
    }

    public static boolean isProductionLifecycleComplete() {
        return false;
    }

    public static boolean isRuntimeLifecycleWiringComplete() {
        return false;
    }

    public static boolean isAndroid13Arm64Verified() {
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

    private static List<DataSurface> buildSurfaces() {
        List<DataSurface> values = Arrays.asList(
                surface(
                        "durable.session_event",
                        Sensitivity.SENSITIVE,
                        StorageMode.DURABLE_ROOM,
                        ContentForm.FIXED_METADATA,
                        OwnerScope.PRINCIPAL_SESSION,
                        ConsentMode.OPERATIONAL_POLICY,
                        RetentionMode.TERMINAL_CAPACITY_BOUNDED,
                        DeletionMode.TERMINAL_CASCADE,
                        ExportMode.FORBIDDEN,
                        LogMode.DIGEST_METADATA_ONLY,
                        EnforcementState.PARTIAL_RUNTIME,
                        false,
                        "com/centralbrain/runtime/persistence/SessionEntity.java",
                        "com/centralbrain/runtime/persistence/RuntimeEventEntity.java"),
                surface(
                        "durable.plan_graph",
                        Sensitivity.RESTRICTED,
                        StorageMode.DURABLE_ROOM,
                        ContentForm.DIGEST_METADATA,
                        OwnerScope.SESSION_TASK,
                        ConsentMode.OPERATIONAL_POLICY,
                        RetentionMode.SESSION_CASCADE,
                        DeletionMode.TERMINAL_CASCADE,
                        ExportMode.FORBIDDEN,
                        LogMode.DIGEST_METADATA_ONLY,
                        EnforcementState.PARTIAL_RUNTIME,
                        false,
                        "com/centralbrain/runtime/persistence/PlanEntity.java",
                        "com/centralbrain/runtime/persistence/PlanNodeEntity.java",
                        "com/centralbrain/runtime/persistence/RuntimeTaskEntity.java",
                        "com/centralbrain/runtime/persistence/TaskCheckpointEntity.java"),
                surface(
                        "durable.effect_recovery",
                        Sensitivity.RESTRICTED,
                        StorageMode.DURABLE_ROOM,
                        ContentForm.DIGEST_METADATA,
                        OwnerScope.SESSION_TASK,
                        ConsentMode.OPERATIONAL_POLICY,
                        RetentionMode.OWNER_POLICY_MISSING,
                        DeletionMode.NO_PUBLIC_DELETE,
                        ExportMode.FORBIDDEN,
                        LogMode.DIGEST_METADATA_ONLY,
                        EnforcementState.POLICY_GAP,
                        false,
                        "com/centralbrain/runtime/persistence/PendingEffectEntity.java",
                        "com/centralbrain/runtime/persistence/OutboxEntity.java",
                        "com/centralbrain/runtime/persistence/EffectObservationEntity.java",
                        "com/centralbrain/runtime/persistence/CompensationEntity.java"),
                surface(
                        "durable.approval",
                        Sensitivity.SENSITIVE,
                        StorageMode.DURABLE_ROOM,
                        ContentForm.FIXED_METADATA,
                        OwnerScope.PRINCIPAL,
                        ConsentMode.AUTHORITY_EVIDENCE,
                        RetentionMode.EXPIRY_BOUNDED,
                        DeletionMode.CAPACITY_EVICTION,
                        ExportMode.FORBIDDEN,
                        LogMode.DIGEST_METADATA_ONLY,
                        EnforcementState.PARTIAL_RUNTIME,
                        false,
                        "com/centralbrain/runtime/persistence/ApprovalRequestEntity.java"),
                surface(
                        "durable.audit",
                        Sensitivity.SENSITIVE,
                        StorageMode.DURABLE_ROOM,
                        ContentForm.DIGEST_METADATA,
                        OwnerScope.SYSTEM,
                        ConsentMode.OPERATIONAL_POLICY,
                        RetentionMode.OWNER_POLICY_MISSING,
                        DeletionMode.NO_PUBLIC_DELETE,
                        ExportMode.FORBIDDEN,
                        LogMode.DIGEST_METADATA_ONLY,
                        EnforcementState.POLICY_GAP,
                        false,
                        "com/centralbrain/runtime/persistence/AuditEventEntity.java"),
                surface(
                        "durable.event_cursor",
                        Sensitivity.SENSITIVE,
                        StorageMode.DURABLE_ROOM,
                        ContentForm.FIXED_METADATA,
                        OwnerScope.PRINCIPAL,
                        ConsentMode.ACCESS_POLICY_EVIDENCE,
                        RetentionMode.CANCELLED_CAPACITY_BOUNDED,
                        DeletionMode.CAPACITY_EVICTION,
                        ExportMode.FORBIDDEN,
                        LogMode.DIGEST_METADATA_ONLY,
                        EnforcementState.PARTIAL_RUNTIME,
                        false,
                        "com/centralbrain/runtime/persistence/EventCursorEntity.java"),
                surface(
                        "memory.working",
                        Sensitivity.RESTRICTED,
                        StorageMode.PROCESS_LOCAL,
                        ContentForm.BOUNDED_OPAQUE_CONTENT,
                        OwnerScope.SESSION,
                        ConsentMode.SESSION_POLICY,
                        RetentionMode.TTL_SESSION_TERMINAL,
                        DeletionMode.TERMINAL_CASCADE,
                        ExportMode.FORBIDDEN,
                        LogMode.CONTENT_FORBIDDEN,
                        EnforcementState.CONTRACT_TEST_ONLY,
                        true,
                        "com/centralbrain/runtime/memory/WorkingMemoryStore.java"),
                surface(
                        "memory.profile",
                        Sensitivity.SENSITIVE,
                        StorageMode.PROCESS_LOCAL,
                        ContentForm.SEALED_TYPED_PREFERENCE,
                        OwnerScope.PRINCIPAL,
                        ConsentMode.EXPLICIT_CONSENT,
                        RetentionMode.MAXIMUM_30_DAYS,
                        DeletionMode.AUTHORIZED_DELETE,
                        ExportMode.AUTHORIZED_BOUNDED,
                        LogMode.CONTENT_FORBIDDEN,
                        EnforcementState.CONTRACT_TEST_ONLY,
                        true,
                        "com/centralbrain/runtime/memory/ProfileMemoryStore.java"),
                surface(
                        "memory.episodic",
                        Sensitivity.SENSITIVE,
                        StorageMode.PROCESS_LOCAL,
                        ContentForm.FIXED_ENUM_SUMMARY,
                        OwnerScope.PRINCIPAL,
                        ConsentMode.AUTHORITY_EVIDENCE,
                        RetentionMode.MAXIMUM_30_DAYS,
                        DeletionMode.AUTHORIZED_ERASE,
                        ExportMode.FORBIDDEN,
                        LogMode.CONTENT_FORBIDDEN,
                        EnforcementState.CONTRACT_TEST_ONLY,
                        false,
                        "com/centralbrain/runtime/memory/EpisodicMemoryStore.java"),
                surface(
                        "events.broker",
                        Sensitivity.INTERNAL,
                        StorageMode.PROCESS_LOCAL,
                        ContentForm.DIGEST_METADATA,
                        OwnerScope.PRINCIPAL,
                        ConsentMode.ACCESS_POLICY_EVIDENCE,
                        RetentionMode.CAPACITY_BOUNDED,
                        DeletionMode.CAPACITY_EVICTION,
                        ExportMode.FORBIDDEN,
                        LogMode.DIGEST_METADATA_ONLY,
                        EnforcementState.CONTRACT_TEST_ONLY,
                        false,
                        "com/centralbrain/runtime/events/InProcessDurableEventBroker.java"),
                surface(
                        "tools.execution_audit",
                        Sensitivity.SENSITIVE,
                        StorageMode.PROCESS_LOCAL,
                        ContentForm.DIGEST_METADATA,
                        OwnerScope.TASK,
                        ConsentMode.OPERATIONAL_POLICY,
                        RetentionMode.CAPACITY_BOUNDED,
                        DeletionMode.CAPACITY_EVICTION,
                        ExportMode.FORBIDDEN,
                        LogMode.DIGEST_METADATA_ONLY,
                        EnforcementState.CONTRACT_TEST_ONLY,
                        false,
                        "com/centralbrain/runtime/tools/InProcessBuiltInToolExecutor.java"),
                surface(
                        "model.inference_boundary",
                        Sensitivity.RESTRICTED,
                        StorageMode.TRANSIENT_ONLY,
                        ContentForm.TRANSIENT_MODEL_CONTENT,
                        OwnerScope.SESSION_TASK,
                        ConsentMode.AUTHORITY_EVIDENCE,
                        RetentionMode.CALL_ONLY,
                        DeletionMode.NOT_STORED,
                        ExportMode.FORBIDDEN,
                        LogMode.CONTENT_FORBIDDEN,
                        EnforcementState.TRANSIENT_ENFORCED,
                        true,
                        "com/centralbrain/runtime/model/ModelContractV2.java",
                        "com/centralbrain/runtime/model/StructuredModelOutput.java"));

        validateAggregate(values);
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    private static DataSurface surface(
            String surfaceId,
            Sensitivity sensitivity,
            StorageMode storageMode,
            ContentForm contentForm,
            OwnerScope ownerScope,
            ConsentMode consentMode,
            RetentionMode retentionMode,
            DeletionMode deletionMode,
            ExportMode exportMode,
            LogMode logMode,
            EnforcementState enforcementState,
            boolean acceptsContentPayload,
            String... sourceClasses) {
        return new DataSurface(
                surfaceId,
                sensitivity,
                storageMode,
                contentForm,
                ownerScope,
                consentMode,
                retentionMode,
                deletionMode,
                exportMode,
                logMode,
                enforcementState,
                acceptsContentPayload,
                sourceClasses);
    }

    private static void validateAggregate(List<DataSurface> values) {
        if (values.size() != SURFACE_COUNT
                || countStorage(values, StorageMode.DURABLE_ROOM) != DURABLE_SURFACE_COUNT
                || countStorage(values, StorageMode.PROCESS_LOCAL)
                        != PROCESS_LOCAL_SURFACE_COUNT
                || countStorage(values, StorageMode.TRANSIENT_ONLY) != TRANSIENT_SURFACE_COUNT
                || values.stream().filter(value -> value.enforcementState
                        == EnforcementState.POLICY_GAP).count() != POLICY_GAP_COUNT
                || values.stream().filter(value -> value.exportMode
                        == ExportMode.AUTHORIZED_BOUNDED).count()
                        != AUTHORIZED_EXPORT_SURFACE_COUNT) {
            throw new IllegalStateException("privacy inventory aggregate changed");
        }
        List<String> ids = new ArrayList<>();
        for (DataSurface value : values) {
            if (ids.contains(value.surfaceId)) {
                throw new IllegalStateException("privacy surface ID duplicated");
            }
            ids.add(value.surfaceId);
        }
    }

    private static long countStorage(List<DataSurface> values, StorageMode mode) {
        return values.stream().filter(value -> value.storageMode == mode).count();
    }

    private static String canonicalInventory() {
        StringBuilder value = new StringBuilder()
                .append(SCHEMA_VERSION).append('|').append(PROFILE_ID);
        for (DataSurface surface : SURFACES) {
            value.append('|').append(surface.canonicalForm());
        }
        return value.toString();
    }

    private static String requireSurfaceId(String value) {
        if (value == null || !value.matches("[a-z][a-z0-9_]{2,31}[.][a-z][a-z0-9_]{2,31}")) {
            throw new IllegalArgumentException("surfaceId is invalid");
        }
        return value;
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
