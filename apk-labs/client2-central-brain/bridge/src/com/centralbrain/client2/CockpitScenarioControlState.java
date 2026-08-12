package com.centralbrain.client2;

import com.centralbrain.sdk.session.ICentralBrainSessionRuntime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable catalog and Session projection shared by natural and manual cockpit controls. */
public final class CockpitScenarioControlState {
    public enum Origin { NONE, NATURAL, MANUAL_HVAC, MANUAL_SEAT }

    public enum DeviceRole { NOT_INVOLVED, CATALOG_REQUIRED, CATALOG_OPTIONAL, MANUAL_TARGET }

    public enum CatalogStatus { UNRESOLVED, MATCHED, MISMATCH }

    public enum Lifecycle {
        IDLE,
        REQUESTED,
        SESSION_ACCEPTED,
        PLANNING,
        WAITING_APPROVAL,
        EXECUTING,
        PARTIALLY_COMPLETED,
        COMPENSATING,
        STUCK,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    private static final Map<String, Definition> DEFINITIONS = definitions();

    private final String uiScenarioId;
    private final String canonicalScenarioId;
    private final Origin origin;
    private final DeviceRole hvacRole;
    private final DeviceRole seatRole;
    private final CatalogStatus catalogStatus;
    private final Lifecycle lifecycle;
    private final int activePlanRevision;
    private final long lastEventSequence;

    private CockpitScenarioControlState(
            String uiScenarioId,
            String canonicalScenarioId,
            Origin origin,
            DeviceRole hvacRole,
            DeviceRole seatRole,
            CatalogStatus catalogStatus,
            Lifecycle lifecycle,
            int activePlanRevision,
            long lastEventSequence) {
        this.uiScenarioId = bounded(uiScenarioId);
        this.canonicalScenarioId = bounded(canonicalScenarioId);
        this.origin = Objects.requireNonNull(origin, "origin");
        this.hvacRole = Objects.requireNonNull(hvacRole, "hvacRole");
        this.seatRole = Objects.requireNonNull(seatRole, "seatRole");
        this.catalogStatus = Objects.requireNonNull(catalogStatus, "catalogStatus");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.activePlanRevision = Math.max(0, activePlanRevision);
        this.lastEventSequence = Math.max(0, lastEventSequence);
    }

    public static CockpitScenarioControlState initial() {
        return new CockpitScenarioControlState(
                "", "", Origin.NONE, DeviceRole.NOT_INVOLVED, DeviceRole.NOT_INVOLVED,
                CatalogStatus.UNRESOLVED, Lifecycle.IDLE, 0, 0);
    }

    public static boolean isSupported(String uiScenarioId) {
        return DEFINITIONS.containsKey(uiScenarioId);
    }

    public static String canonicalScenarioId(String uiScenarioId) {
        Definition definition = DEFINITIONS.get(uiScenarioId);
        return definition == null ? "" : definition.canonicalScenarioId;
    }

    CockpitScenarioControlState scenarioRequested(String requestedUiScenarioId) {
        Definition definition = requireDefinition(requestedUiScenarioId);
        return new CockpitScenarioControlState(
                requestedUiScenarioId,
                "",
                definition.origin,
                definition.hvacRole,
                definition.seatRole,
                CatalogStatus.UNRESOLVED,
                Lifecycle.REQUESTED,
                0,
                0);
    }

    CockpitScenarioControlState sessionOpened(String acceptedCanonicalScenarioId) {
        Definition definition = requireDefinition(uiScenarioId);
        boolean matched = definition.canonicalScenarioId.equals(acceptedCanonicalScenarioId);
        return new CockpitScenarioControlState(
                uiScenarioId,
                acceptedCanonicalScenarioId,
                origin,
                matched ? hvacRole : DeviceRole.NOT_INVOLVED,
                matched ? seatRole : DeviceRole.NOT_INVOLVED,
                matched ? CatalogStatus.MATCHED : CatalogStatus.MISMATCH,
                matched ? Lifecycle.SESSION_ACCEPTED : Lifecycle.FAILED,
                0,
                0);
    }

    CockpitScenarioControlState snapshot(
            String snapshotCanonicalScenarioId,
            int sessionState,
            int planRevision) {
        if (catalogStatus != CatalogStatus.MATCHED
                || !canonicalScenarioId.equals(snapshotCanonicalScenarioId)) {
            return mismatch(snapshotCanonicalScenarioId);
        }
        return new CockpitScenarioControlState(
                uiScenarioId,
                canonicalScenarioId,
                origin,
                hvacRole,
                seatRole,
                CatalogStatus.MATCHED,
                lifecycleFor(sessionState),
                planRevision,
                lastEventSequence);
    }

    CockpitScenarioControlState runtimeEvent(long sequence) {
        if (catalogStatus != CatalogStatus.MATCHED || sequence <= lastEventSequence) {
            return this;
        }
        return new CockpitScenarioControlState(
                uiScenarioId,
                canonicalScenarioId,
                origin,
                hvacRole,
                seatRole,
                catalogStatus,
                lifecycle,
                activePlanRevision,
                sequence);
    }

    CockpitScenarioControlState failed() {
        return new CockpitScenarioControlState(
                uiScenarioId,
                canonicalScenarioId,
                origin,
                hvacRole,
                seatRole,
                catalogStatus,
                Lifecycle.FAILED,
                activePlanRevision,
                lastEventSequence);
    }

    static CockpitScenarioControlState restored(String uiScenarioId, String canonicalScenarioId) {
        if (uiScenarioId == null || uiScenarioId.isEmpty()) {
            return initial();
        }
        if (!isSupported(uiScenarioId)) {
            return initial();
        }
        CockpitScenarioControlState requested = initial().scenarioRequested(uiScenarioId);
        return canonicalScenarioId == null || canonicalScenarioId.isEmpty()
                ? requested : requested.sessionOpened(canonicalScenarioId);
    }

    public String getUiScenarioId() {
        return uiScenarioId;
    }

    public String getCanonicalScenarioId() {
        return canonicalScenarioId;
    }

    public Origin getOrigin() {
        return origin;
    }

    public DeviceRole getHvacRole() {
        return hvacRole;
    }

    public DeviceRole getSeatRole() {
        return seatRole;
    }

    public CatalogStatus getCatalogStatus() {
        return catalogStatus;
    }

    public Lifecycle getLifecycle() {
        return lifecycle;
    }

    public int getActivePlanRevision() {
        return activePlanRevision;
    }

    public long getLastEventSequence() {
        return lastEventSequence;
    }

    public boolean isCatalogMatched() {
        return catalogStatus == CatalogStatus.MATCHED;
    }

    public boolean isPlanPublished() {
        return activePlanRevision > 0;
    }

    public boolean isEffectDispatchEnabled() {
        return false;
    }

    public boolean isReadbackAvailable() {
        return false;
    }

    private CockpitScenarioControlState mismatch(String receivedCanonicalScenarioId) {
        return new CockpitScenarioControlState(
                uiScenarioId,
                receivedCanonicalScenarioId,
                origin,
                DeviceRole.NOT_INVOLVED,
                DeviceRole.NOT_INVOLVED,
                CatalogStatus.MISMATCH,
                Lifecycle.FAILED,
                0,
                lastEventSequence);
    }

    private static Definition requireDefinition(String uiScenarioId) {
        Definition definition = DEFINITIONS.get(uiScenarioId);
        if (definition == null) {
            throw new IllegalArgumentException("unsupported cockpit scenario");
        }
        return definition;
    }

    private static Lifecycle lifecycleFor(int state) {
        switch (state) {
            case ICentralBrainSessionRuntime.SESSION_STATE_CREATED:
                return Lifecycle.SESSION_ACCEPTED;
            case ICentralBrainSessionRuntime.SESSION_STATE_PLANNING:
                return Lifecycle.PLANNING;
            case ICentralBrainSessionRuntime.SESSION_STATE_WAITING_FOR_CONFIRMATION:
                return Lifecycle.WAITING_APPROVAL;
            case ICentralBrainSessionRuntime.SESSION_STATE_EXECUTING:
                return Lifecycle.EXECUTING;
            case ICentralBrainSessionRuntime.SESSION_STATE_PARTIALLY_COMPLETED:
                return Lifecycle.PARTIALLY_COMPLETED;
            case ICentralBrainSessionRuntime.SESSION_STATE_COMPENSATING:
                return Lifecycle.COMPENSATING;
            case ICentralBrainSessionRuntime.SESSION_STATE_STUCK:
                return Lifecycle.STUCK;
            case ICentralBrainSessionRuntime.SESSION_STATE_COMPLETED:
                return Lifecycle.COMPLETED;
            case ICentralBrainSessionRuntime.SESSION_STATE_FAILED:
                return Lifecycle.FAILED;
            case ICentralBrainSessionRuntime.SESSION_STATE_CANCELLED:
                return Lifecycle.CANCELLED;
            default:
                return Lifecycle.FAILED;
        }
    }

    private static Map<String, Definition> definitions() {
        Map<String, Definition> definitions = new LinkedHashMap<>();
        add(definitions, "agent.freeform", "scene.aios.freeform.v1", Origin.NATURAL,
                DeviceRole.NOT_INVOLVED, DeviceRole.NOT_INVOLVED);
        add(definitions, "care.cold", "scene.comfort.cold.v1", Origin.NATURAL,
                DeviceRole.CATALOG_REQUIRED, DeviceRole.CATALOG_OPTIONAL);
        add(definitions, "care.fatigue", "scene.fatigue.assist.v1", Origin.NATURAL,
                DeviceRole.CATALOG_REQUIRED, DeviceRole.CATALOG_OPTIONAL);
        add(definitions, "cabin.multimodal", "scene.cabin.multimodal.assist.v1",
                Origin.NATURAL,
                DeviceRole.NOT_INVOLVED, DeviceRole.NOT_INVOLVED);
        add(definitions, "cabin.smoking", "scene.cabin.compliance.smoking.v1",
                Origin.NATURAL,
                DeviceRole.NOT_INVOLVED, DeviceRole.NOT_INVOLVED);
        add(definitions, "task.home", "scene.navigation.home.v1", Origin.NATURAL,
                DeviceRole.NOT_INVOLVED, DeviceRole.NOT_INVOLVED);
        add(definitions, "skill.nap", "scene.rest.nap.v1", Origin.NATURAL,
                DeviceRole.CATALOG_REQUIRED, DeviceRole.CATALOG_REQUIRED);
        add(definitions, "state.vehicle", "scene.diagnostics.vehicle.v1", Origin.NATURAL,
                DeviceRole.NOT_INVOLVED, DeviceRole.NOT_INVOLVED);
        add(definitions, "memory.preference", "scene.memory.preference.v1", Origin.NATURAL,
                DeviceRole.NOT_INVOLVED, DeviceRole.NOT_INVOLVED);
        add(definitions, "skills.catalog", "scene.skills.catalog.v1", Origin.NATURAL,
                DeviceRole.NOT_INVOLVED, DeviceRole.NOT_INVOLVED);
        add(definitions, "governance.audit", "scene.governance.audit.v1", Origin.NATURAL,
                DeviceRole.NOT_INVOLVED, DeviceRole.NOT_INVOLVED);
        add(definitions, "security.denied", "scene.security.denied.v1", Origin.NATURAL,
                DeviceRole.NOT_INVOLVED, DeviceRole.NOT_INVOLVED);
        add(definitions, "security.privacy", "scene.security.privacy.v1", Origin.NATURAL,
                DeviceRole.NOT_INVOLVED, DeviceRole.NOT_INVOLVED);
        add(definitions, "runtime.npu", "scene.runtime.npu.v1", Origin.NATURAL,
                DeviceRole.NOT_INVOLVED, DeviceRole.NOT_INVOLVED);
        add(definitions, "system.overview", "scene.system.overview.v1", Origin.NATURAL,
                DeviceRole.NOT_INVOLVED, DeviceRole.NOT_INVOLVED);
        add(definitions, "manual.hvac", "scene.manual.hvac.adjust.v1", Origin.MANUAL_HVAC,
                DeviceRole.MANUAL_TARGET, DeviceRole.NOT_INVOLVED);
        add(definitions, "manual.seat", "scene.manual.seat.adjust.v1", Origin.MANUAL_SEAT,
                DeviceRole.NOT_INVOLVED, DeviceRole.MANUAL_TARGET);
        return Collections.unmodifiableMap(definitions);
    }

    private static void add(
            Map<String, Definition> definitions,
            String uiScenarioId,
            String canonicalScenarioId,
            Origin origin,
            DeviceRole hvacRole,
            DeviceRole seatRole) {
        definitions.put(uiScenarioId,
                new Definition(canonicalScenarioId, origin, hvacRole, seatRole));
    }

    private static String bounded(String value) {
        String safe = value == null ? "" : value;
        return safe.length() <= 96 ? safe : safe.substring(0, 96);
    }

    private static final class Definition {
        private final String canonicalScenarioId;
        private final Origin origin;
        private final DeviceRole hvacRole;
        private final DeviceRole seatRole;

        private Definition(
                String canonicalScenarioId,
                Origin origin,
                DeviceRole hvacRole,
                DeviceRole seatRole) {
            this.canonicalScenarioId = canonicalScenarioId;
            this.origin = origin;
            this.hvacRole = hvacRole;
            this.seatRole = seatRole;
        }
    }
}
