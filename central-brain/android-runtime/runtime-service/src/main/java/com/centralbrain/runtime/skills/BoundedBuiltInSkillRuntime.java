package com.centralbrain.runtime.skills;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/** Process-local admission contract for compiled-in Skill manifests. */
public final class BoundedBuiltInSkillRuntime {
    public static final String SKILL_VEHICLE_STATE_QUERY = "vehicle.state.query";
    public static final String SKILL_CABIN_PRECONDITION = "cabin.precondition";
    public static final String SKILL_CABIN_SCENE_NAP = "cabin.scene.nap";

    private static final String BUILT_IN_SIGNER_SHA256 = repeat("1", 64);
    private static final int MAX_ID_LENGTH = 128;
    private static final Map<String, SkillManifest> CATALOG = createCatalog();

    public enum Capability {
        VEHICLE_READ,
        VEHICLE_CONTROL
    }

    public enum RiskClass {
        READ_ONLY,
        COMFORT_CONTROL
    }

    public enum SafetyState {
        NORMAL,
        DEGRADED,
        DIAGNOSTIC_READ_ONLY,
        UNKNOWN,
        EMERGENCY
    }

    public enum RouteKind {
        SOA_OPERATION,
        UIB_ACTION,
        AGENT_PLAN
    }

    public enum InvocationState {
        ADMITTED,
        CANCELLED
    }

    public enum AdmissionOutcome {
        ADMITTED,
        REPLAYED,
        CONFLICT,
        UNKNOWN_SKILL,
        VERSION_MISMATCH,
        INPUT_SCHEMA_MISMATCH,
        CAPABILITY_DENIED,
        SAFETY_STATE_DENIED,
        GLOBAL_LIMIT,
        OWNER_LIMIT
    }

    public enum CancelOutcome {
        APPLIED,
        REPLAYED,
        NOT_FOUND
    }

    private final Limits limits;
    private final Supplier<String> invocationIdSource;
    private final Map<String, Entry> byInvocationId = new LinkedHashMap<>();
    private final Map<String, Entry> byClientKey = new LinkedHashMap<>();

    private long admittedCount;
    private long cancelledTransitionCount;
    private long cancelledEvictionCount;

    private BoundedBuiltInSkillRuntime(Limits limits, Supplier<String> invocationIdSource) {
        this.limits = Objects.requireNonNull(limits, "limits");
        this.invocationIdSource = Objects.requireNonNull(
                invocationIdSource,
                "invocationIdSource");
    }

    public static BoundedBuiltInSkillRuntime createForContractTest(
            Limits limits,
            Supplier<String> invocationIdSource) {
        return new BoundedBuiltInSkillRuntime(limits, invocationIdSource);
    }

    public List<SkillManifest> listManifests() {
        return Collections.unmodifiableList(new ArrayList<>(CATALOG.values()));
    }

    public SkillManifest findManifest(String skillId) {
        if (skillId == null) {
            return null;
        }
        return CATALOG.get(skillId);
    }

    public synchronized AdmissionResult admit(TrustedInvocation request) {
        Objects.requireNonNull(request, "request");
        String clientKey = clientKey(request.ownerFingerprint, request.clientInvocationId);
        Entry existing = byClientKey.get(clientKey);
        if (existing != null) {
            return new AdmissionResult(
                    existing.matches(request)
                            ? AdmissionOutcome.REPLAYED
                            : AdmissionOutcome.CONFLICT,
                    existing.snapshot());
        }

        SkillManifest manifest = CATALOG.get(request.skillId);
        if (manifest == null) {
            return new AdmissionResult(AdmissionOutcome.UNKNOWN_SKILL, null);
        }
        if (!manifest.version.equals(request.version)) {
            return new AdmissionResult(AdmissionOutcome.VERSION_MISMATCH, null);
        }
        if (!manifest.inputSchemaId.equals(request.inputSchemaId)) {
            return new AdmissionResult(AdmissionOutcome.INPUT_SCHEMA_MISMATCH, null);
        }
        if (!request.grantedCapabilities.containsAll(manifest.requiredCapabilities)) {
            return new AdmissionResult(AdmissionOutcome.CAPABILITY_DENIED, null);
        }
        if (!manifest.allowedSafetyStates.contains(request.safetyState)) {
            return new AdmissionResult(AdmissionOutcome.SAFETY_STATE_DENIED, null);
        }
        if (activeCount() >= limits.maxActiveInvocations) {
            return new AdmissionResult(AdmissionOutcome.GLOBAL_LIMIT, null);
        }
        if (activeCount(request.ownerFingerprint) >= limits.maxActiveInvocationsPerOwner) {
            return new AdmissionResult(AdmissionOutcome.OWNER_LIMIT, null);
        }

        Entry entry = new Entry(nextInvocationId(), request, manifest);
        byInvocationId.put(entry.invocationId, entry);
        byClientKey.put(clientKey, entry);
        admittedCount++;
        return new AdmissionResult(AdmissionOutcome.ADMITTED, entry.snapshot());
    }

    public synchronized InvocationSnapshot findOwned(
            String invocationId,
            String ownerFingerprint) {
        requireId(invocationId, "invocationId");
        requireOwner(ownerFingerprint);
        Entry entry = byInvocationId.get(invocationId);
        return entry != null && entry.ownerFingerprint.equals(ownerFingerprint)
                ? entry.snapshot()
                : null;
    }

    public synchronized CancelOutcome cancelOwned(
            String invocationId,
            String ownerFingerprint) {
        requireId(invocationId, "invocationId");
        requireOwner(ownerFingerprint);
        Entry entry = byInvocationId.get(invocationId);
        if (entry == null || !entry.ownerFingerprint.equals(ownerFingerprint)) {
            return CancelOutcome.NOT_FOUND;
        }
        if (entry.state == InvocationState.CANCELLED) {
            return CancelOutcome.REPLAYED;
        }
        entry.state = InvocationState.CANCELLED;
        cancelledTransitionCount++;
        trimCancelled(entry.invocationId);
        return CancelOutcome.APPLIED;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(
                CATALOG.size(),
                activeCount(),
                cancelledCount(),
                admittedCount,
                cancelledTransitionCount,
                cancelledEvictionCount,
                true,
                true,
                false,
                false,
                false,
                false,
                false);
    }

    private void trimCancelled(String retainedInvocationId) {
        while (cancelledCount() > limits.maxCancelledInvocations) {
            Iterator<Map.Entry<String, Entry>> iterator = byInvocationId.entrySet().iterator();
            boolean removed = false;
            while (iterator.hasNext()) {
                Entry entry = iterator.next().getValue();
                if (entry.state == InvocationState.CANCELLED
                        && !entry.invocationId.equals(retainedInvocationId)) {
                    iterator.remove();
                    byClientKey.remove(clientKey(
                            entry.ownerFingerprint,
                            entry.clientInvocationId));
                    cancelledEvictionCount++;
                    removed = true;
                    break;
                }
            }
            if (!removed) {
                throw new IllegalStateException("cancelled Skill retention cannot be bounded");
            }
        }
    }

    private int activeCount() {
        int count = 0;
        for (Entry entry : byInvocationId.values()) {
            if (entry.state == InvocationState.ADMITTED) {
                count++;
            }
        }
        return count;
    }

    private int activeCount(String ownerFingerprint) {
        int count = 0;
        for (Entry entry : byInvocationId.values()) {
            if (entry.state == InvocationState.ADMITTED
                    && entry.ownerFingerprint.equals(ownerFingerprint)) {
                count++;
            }
        }
        return count;
    }

    private int cancelledCount() {
        return byInvocationId.size() - activeCount();
    }

    private String nextInvocationId() {
        for (int attempt = 0; attempt < 16; attempt++) {
            String invocationId = "skill-invocation-" + requireId(
                    invocationIdSource.get(),
                    "generated invocationId");
            if (!byInvocationId.containsKey(invocationId)) {
                return invocationId;
            }
        }
        throw new IllegalStateException("Skill invocation ID source did not produce a unique ID");
    }

    private static Map<String, SkillManifest> createCatalog() {
        Map<String, SkillManifest> catalog = new LinkedHashMap<>();
        putUnique(catalog, new SkillManifest(
                SKILL_VEHICLE_STATE_QUERY,
                "0.1.0",
                "skill.vehicle.state.query.input.v1",
                "skill.vehicle.state.query.output.v1",
                RouteKind.SOA_OPERATION,
                "vehicle-state.getState",
                EnumSet.of(Capability.VEHICLE_READ),
                RiskClass.READ_ONLY,
                EnumSet.of(
                        SafetyState.NORMAL,
                        SafetyState.DEGRADED,
                        SafetyState.DIAGNOSTIC_READ_ONLY),
                repeat("a", 64),
                BUILT_IN_SIGNER_SHA256));
        putUnique(catalog, new SkillManifest(
                SKILL_CABIN_PRECONDITION,
                "0.1.0",
                "skill.cabin.precondition.input.v1",
                "skill.cabin.precondition.output.v1",
                RouteKind.UIB_ACTION,
                "Cabin.SetTemperature",
                EnumSet.of(Capability.VEHICLE_READ, Capability.VEHICLE_CONTROL),
                RiskClass.COMFORT_CONTROL,
                EnumSet.of(SafetyState.NORMAL),
                repeat("b", 64),
                BUILT_IN_SIGNER_SHA256));
        putUnique(catalog, new SkillManifest(
                SKILL_CABIN_SCENE_NAP,
                "0.1.0",
                "skill.cabin.scene.nap.input.v1",
                "skill.cabin.scene.nap.output.v1",
                RouteKind.AGENT_PLAN,
                "cabin_nap_prepare",
                EnumSet.of(Capability.VEHICLE_READ, Capability.VEHICLE_CONTROL),
                RiskClass.COMFORT_CONTROL,
                EnumSet.of(SafetyState.NORMAL),
                repeat("c", 64),
                BUILT_IN_SIGNER_SHA256));
        return Collections.unmodifiableMap(catalog);
    }

    private static void putUnique(
            Map<String, SkillManifest> catalog,
            SkillManifest manifest) {
        if (catalog.put(manifest.skillId, manifest) != null) {
            throw new IllegalStateException("duplicate built-in Skill ID: " + manifest.skillId);
        }
    }

    private static String requestFingerprint(TrustedInvocation request) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        updateDigest(digest, "central-brain-built-in-skill-invocation-v1");
        updateDigest(digest, request.ownerFingerprint);
        updateDigest(digest, request.clientInvocationId);
        updateDigest(digest, request.skillId);
        updateDigest(digest, request.version);
        updateDigest(digest, request.inputSchemaId);
        updateDigest(digest, request.inputDigest);
        for (Capability capability : request.grantedCapabilities) {
            updateDigest(digest, capability.name());
        }
        updateDigest(digest, request.safetyState.name());
        return toHex(digest.digest());
    }

    private static void updateDigest(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    private static String toHex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] output = new char[bytes.length * 2];
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xff;
            output[index * 2] = digits[value >>> 4];
            output[index * 2 + 1] = digits[value & 0x0f];
        }
        return new String(output);
    }

    private static String clientKey(String ownerFingerprint, String clientInvocationId) {
        return ownerFingerprint + ":" + clientInvocationId;
    }

    private static String requireSkillId(String value) {
        if (value == null
                || value.length() > MAX_ID_LENGTH
                || !value.matches("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+")) {
            throw new IllegalArgumentException("skillId is invalid");
        }
        return value;
    }

    private static String requireVersion(String value) {
        if (value == null || !value.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) {
            throw new IllegalArgumentException("semantic version is required");
        }
        return value;
    }

    private static String requireId(String value, String name) {
        if (value == null
                || value.isEmpty()
                || value.length() > MAX_ID_LENGTH
                || !value.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static String requireDigest(String value, String name) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " must be a lowercase SHA-256 digest");
        }
        return value;
    }

    private static String requireOwner(String value) {
        return requireDigest(value, "ownerFingerprint");
    }

    private static String repeat(String value, int count) {
        StringBuilder output = new StringBuilder(value.length() * count);
        for (int index = 0; index < count; index++) {
            output.append(value);
        }
        return output.toString();
    }

    public static final class Limits {
        private final int maxActiveInvocations;
        private final int maxActiveInvocationsPerOwner;
        private final int maxCancelledInvocations;

        public Limits(
                int maxActiveInvocations,
                int maxActiveInvocationsPerOwner,
                int maxCancelledInvocations) {
            if (maxActiveInvocations < 1
                    || maxActiveInvocationsPerOwner < 1
                    || maxActiveInvocationsPerOwner > maxActiveInvocations
                    || maxCancelledInvocations < 1) {
                throw new IllegalArgumentException("Skill invocation limits are invalid");
            }
            this.maxActiveInvocations = maxActiveInvocations;
            this.maxActiveInvocationsPerOwner = maxActiveInvocationsPerOwner;
            this.maxCancelledInvocations = maxCancelledInvocations;
        }
    }

    public static final class SkillManifest {
        private final String skillId;
        private final String version;
        private final String inputSchemaId;
        private final String outputSchemaId;
        private final RouteKind routeKind;
        private final String routeTarget;
        private final Set<Capability> requiredCapabilities;
        private final RiskClass riskClass;
        private final Set<SafetyState> allowedSafetyStates;
        private final String artifactDigest;
        private final String signerDigest;

        private SkillManifest(
                String skillId,
                String version,
                String inputSchemaId,
                String outputSchemaId,
                RouteKind routeKind,
                String routeTarget,
                Set<Capability> requiredCapabilities,
                RiskClass riskClass,
                Set<SafetyState> allowedSafetyStates,
                String artifactDigest,
                String signerDigest) {
            this.skillId = requireSkillId(skillId);
            this.version = requireVersion(version);
            this.inputSchemaId = requireId(inputSchemaId, "inputSchemaId");
            this.outputSchemaId = requireId(outputSchemaId, "outputSchemaId");
            this.routeKind = Objects.requireNonNull(routeKind, "routeKind");
            this.routeTarget = requireId(routeTarget, "routeTarget");
            if (requiredCapabilities == null || requiredCapabilities.isEmpty()) {
                throw new IllegalArgumentException("Skill capability set is required");
            }
            this.requiredCapabilities = Collections.unmodifiableSet(
                    EnumSet.copyOf(requiredCapabilities));
            this.riskClass = Objects.requireNonNull(riskClass, "riskClass");
            if (allowedSafetyStates == null
                    || allowedSafetyStates.isEmpty()
                    || allowedSafetyStates.contains(SafetyState.UNKNOWN)
                    || allowedSafetyStates.contains(SafetyState.EMERGENCY)) {
                throw new IllegalArgumentException("Skill safety states are invalid");
            }
            this.allowedSafetyStates = Collections.unmodifiableSet(
                    EnumSet.copyOf(allowedSafetyStates));
            if (this.riskClass == RiskClass.READ_ONLY
                    && this.requiredCapabilities.contains(Capability.VEHICLE_CONTROL)) {
                throw new IllegalArgumentException("read-only Skill cannot require control");
            }
            if (this.riskClass == RiskClass.COMFORT_CONTROL
                    && (!this.requiredCapabilities.contains(Capability.VEHICLE_CONTROL)
                            || !this.allowedSafetyStates.equals(
                                    EnumSet.of(SafetyState.NORMAL)))) {
                throw new IllegalArgumentException(
                        "comfort Skill requires control and NORMAL-only safety state");
            }
            this.artifactDigest = requireDigest(artifactDigest, "artifactDigest");
            this.signerDigest = requireDigest(signerDigest, "signerDigest");
            if (!BUILT_IN_SIGNER_SHA256.equals(this.signerDigest)) {
                throw new IllegalArgumentException("built-in Skill signer is not allowlisted");
            }
        }

        public String getSkillId() {
            return skillId;
        }

        public String getVersion() {
            return version;
        }

        public String getInputSchemaId() {
            return inputSchemaId;
        }

        public String getOutputSchemaId() {
            return outputSchemaId;
        }

        public RouteKind getRouteKind() {
            return routeKind;
        }

        public String getRouteTarget() {
            return routeTarget;
        }

        public Set<Capability> getRequiredCapabilities() {
            return requiredCapabilities;
        }

        public RiskClass getRiskClass() {
            return riskClass;
        }

        public Set<SafetyState> getAllowedSafetyStates() {
            return allowedSafetyStates;
        }

        public String getArtifactDigest() {
            return artifactDigest;
        }

        public String getSignerDigest() {
            return signerDigest;
        }

        public boolean isCompiledIn() {
            return true;
        }

        public boolean isSignerAllowlistMatched() {
            return true;
        }

        public boolean isArtifactDigestBound() {
            return true;
        }

        public boolean isCryptographicArtifactVerificationPerformed() {
            return false;
        }

        public boolean isDynamicLoadingAllowed() {
            return false;
        }
    }

    public static final class TrustedInvocation {
        private final String ownerFingerprint;
        private final String clientInvocationId;
        private final String skillId;
        private final String version;
        private final String inputSchemaId;
        private final String inputDigest;
        private final Set<Capability> grantedCapabilities;
        private final SafetyState safetyState;

        private TrustedInvocation(
                String ownerFingerprint,
                String clientInvocationId,
                String skillId,
                String version,
                String inputSchemaId,
                String inputDigest,
                Set<Capability> grantedCapabilities,
                SafetyState safetyState) {
            this.ownerFingerprint = requireOwner(ownerFingerprint);
            this.clientInvocationId = requireId(clientInvocationId, "clientInvocationId");
            this.skillId = requireSkillId(skillId);
            this.version = requireVersion(version);
            this.inputSchemaId = requireId(inputSchemaId, "inputSchemaId");
            this.inputDigest = requireDigest(inputDigest, "inputDigest");
            this.grantedCapabilities = grantedCapabilities == null
                    || grantedCapabilities.isEmpty()
                    ? Collections.emptySet()
                    : Collections.unmodifiableSet(EnumSet.copyOf(grantedCapabilities));
            this.safetyState = Objects.requireNonNull(safetyState, "safetyState");
        }

        public static TrustedInvocation fromRuntimePolicy(
                String ownerFingerprint,
                String clientInvocationId,
                String skillId,
                String version,
                String inputSchemaId,
                String inputDigest,
                Set<Capability> grantedCapabilities,
                SafetyState safetyState) {
            return new TrustedInvocation(
                    ownerFingerprint,
                    clientInvocationId,
                    skillId,
                    version,
                    inputSchemaId,
                    inputDigest,
                    grantedCapabilities,
                    safetyState);
        }
    }

    public static final class AdmissionResult {
        private final AdmissionOutcome outcome;
        private final InvocationSnapshot invocation;

        AdmissionResult(AdmissionOutcome outcome, InvocationSnapshot invocation) {
            this.outcome = outcome;
            this.invocation = invocation;
        }

        public AdmissionOutcome getOutcome() {
            return outcome;
        }

        public InvocationSnapshot getInvocation() {
            return invocation;
        }
    }

    public static final class InvocationSnapshot {
        private final String invocationId;
        private final String skillId;
        private final String version;
        private final RiskClass riskClass;
        private final RouteKind routeKind;
        private final String routeTarget;
        private final String artifactDigest;
        private final String signerDigest;
        private final InvocationState state;
        private final boolean dispatchAllowed;

        InvocationSnapshot(Entry entry) {
            invocationId = entry.invocationId;
            skillId = entry.manifest.skillId;
            version = entry.manifest.version;
            riskClass = entry.manifest.riskClass;
            routeKind = entry.manifest.routeKind;
            routeTarget = entry.manifest.routeTarget;
            artifactDigest = entry.manifest.artifactDigest;
            signerDigest = entry.manifest.signerDigest;
            state = entry.state;
            dispatchAllowed = false;
        }

        public String getInvocationId() {
            return invocationId;
        }

        public String getSkillId() {
            return skillId;
        }

        public String getVersion() {
            return version;
        }

        public RiskClass getRiskClass() {
            return riskClass;
        }

        public RouteKind getRouteKind() {
            return routeKind;
        }

        public String getRouteTarget() {
            return routeTarget;
        }

        public String getArtifactDigest() {
            return artifactDigest;
        }

        public String getSignerDigest() {
            return signerDigest;
        }

        public InvocationState getState() {
            return state;
        }

        public boolean isDispatchAllowed() {
            return dispatchAllowed;
        }
    }

    public static final class Snapshot {
        private final int manifestCount;
        private final int activeInvocationCount;
        private final int cancelledInvocationCount;
        private final long admittedCount;
        private final long cancelledCount;
        private final long cancelledEvictionCount;
        private final boolean allManifestsCompiledIn;
        private final boolean allSignerAllowlistsMatched;
        private final boolean cryptographicArtifactVerificationPerformed;
        private final boolean dynamicCodeLoadingEnabled;
        private final boolean networkAccessEnabled;
        private final boolean productionServiceWired;
        private final boolean hardwareAccessed;

        Snapshot(
                int manifestCount,
                int activeInvocationCount,
                int cancelledInvocationCount,
                long admittedCount,
                long cancelledCount,
                long cancelledEvictionCount,
                boolean allManifestsCompiledIn,
                boolean allSignerAllowlistsMatched,
                boolean cryptographicArtifactVerificationPerformed,
                boolean dynamicCodeLoadingEnabled,
                boolean networkAccessEnabled,
                boolean productionServiceWired,
                boolean hardwareAccessed) {
            this.manifestCount = manifestCount;
            this.activeInvocationCount = activeInvocationCount;
            this.cancelledInvocationCount = cancelledInvocationCount;
            this.admittedCount = admittedCount;
            this.cancelledCount = cancelledCount;
            this.cancelledEvictionCount = cancelledEvictionCount;
            this.allManifestsCompiledIn = allManifestsCompiledIn;
            this.allSignerAllowlistsMatched = allSignerAllowlistsMatched;
            this.cryptographicArtifactVerificationPerformed =
                    cryptographicArtifactVerificationPerformed;
            this.dynamicCodeLoadingEnabled = dynamicCodeLoadingEnabled;
            this.networkAccessEnabled = networkAccessEnabled;
            this.productionServiceWired = productionServiceWired;
            this.hardwareAccessed = hardwareAccessed;
        }

        public int getManifestCount() {
            return manifestCount;
        }

        public int getActiveInvocationCount() {
            return activeInvocationCount;
        }

        public int getCancelledInvocationCount() {
            return cancelledInvocationCount;
        }

        public long getAdmittedCount() {
            return admittedCount;
        }

        public long getCancelledCount() {
            return cancelledCount;
        }

        public long getCancelledEvictionCount() {
            return cancelledEvictionCount;
        }

        public boolean areAllManifestsCompiledIn() {
            return allManifestsCompiledIn;
        }

        public boolean areAllSignerAllowlistsMatched() {
            return allSignerAllowlistsMatched;
        }

        public boolean isCryptographicArtifactVerificationPerformed() {
            return cryptographicArtifactVerificationPerformed;
        }

        public boolean isDynamicCodeLoadingEnabled() {
            return dynamicCodeLoadingEnabled;
        }

        public boolean isNetworkAccessEnabled() {
            return networkAccessEnabled;
        }

        public boolean isProductionServiceWired() {
            return productionServiceWired;
        }

        public boolean isHardwareAccessed() {
            return hardwareAccessed;
        }
    }

    private static final class Entry {
        final String invocationId;
        final String ownerFingerprint;
        final String clientInvocationId;
        final SkillManifest manifest;
        final String requestFingerprint;
        InvocationState state = InvocationState.ADMITTED;

        Entry(
                String invocationId,
                TrustedInvocation request,
                SkillManifest manifest) {
            this.invocationId = invocationId;
            ownerFingerprint = request.ownerFingerprint;
            clientInvocationId = request.clientInvocationId;
            this.manifest = manifest;
            requestFingerprint = BoundedBuiltInSkillRuntime.requestFingerprint(request);
        }

        boolean matches(TrustedInvocation request) {
            return requestFingerprint.equals(
                    BoundedBuiltInSkillRuntime.requestFingerprint(request));
        }

        InvocationSnapshot snapshot() {
            return new InvocationSnapshot(this);
        }
    }
}
