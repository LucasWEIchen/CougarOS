package com.centralbrain.runtime.simulation;

import com.centralbrain.runtime.effects.EffectAdapter;
import com.centralbrain.runtime.vehicle.capability.CapabilityCatalog;
import com.centralbrain.runtime.vehicle.capability.VehicleCapability;
import com.centralbrain.runtime.vehicle.schema.SignalSource;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Debug-only synthetic POI adapter. It never resolves or uploads a real location. */
public final class SimulatedNavigationEffectAdapter extends SimulatedEffectAdapter {
    public static final String ADAPTER_ID = "debug.simulated.navigation.v1";
    public static final String DESTINATION = "navigation.poi";
    public static final int TARGET_SCHEMA_VERSION = 1;

    private static final int TARGET_MAGIC = 0x43424e56;
    private static final int TARGET_FIXED_BYTES = Integer.BYTES * 5;

    public interface SyntheticNavigationBackend {
        NavigationObservation resolve(
                String queryDigest,
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

        default boolean uploadsLocation() {
            return false;
        }
    }

    public static final class NavigationTarget {
        private final String area;
        private final String poiQuery;

        private NavigationTarget(String area, String poiQuery) {
            this.area = requireArea(area);
            this.poiQuery = requireCanonicalQuery(poiQuery);
        }

        public static NavigationTarget poi(String poiQuery) {
            return new NavigationTarget("cabin", canonicalizeQuery(poiQuery));
        }

        public VehicleCapability.CapabilityId getCapabilityId() {
            return VehicleCapability.CapabilityId.NAVIGATION_POI;
        }

        public String getArea() {
            return area;
        }

        public String getPoiQuery() {
            return poiQuery;
        }

        public String getQueryDigest() {
            return sha256(poiQuery.getBytes(StandardCharsets.UTF_8));
        }

        public byte[] toCanonicalPayload() {
            byte[] areaBytes = area.getBytes(StandardCharsets.UTF_8);
            byte[] queryBytes = poiQuery.getBytes(StandardCharsets.UTF_8);
            ByteBuffer output = ByteBuffer.allocate(
                    TARGET_FIXED_BYTES + areaBytes.length + queryBytes.length);
            output.putInt(TARGET_MAGIC);
            output.putInt(TARGET_SCHEMA_VERSION);
            output.putInt(1);
            output.putInt(areaBytes.length);
            output.put(areaBytes);
            output.putInt(queryBytes.length);
            output.put(queryBytes);
            return output.array();
        }

        public static NavigationTarget fromCanonicalPayload(byte[] payload) {
            Objects.requireNonNull(payload, "payload");
            if (payload.length < TARGET_FIXED_BYTES) {
                throw new IllegalArgumentException("CB_SIM_NAV: payload is truncated");
            }
            ByteBuffer input = ByteBuffer.wrap(payload);
            if (input.getInt() != TARGET_MAGIC
                    || input.getInt() != TARGET_SCHEMA_VERSION
                    || input.getInt() != 1) {
                throw new IllegalArgumentException("CB_SIM_NAV: payload header is invalid");
            }
            int areaLength = input.getInt();
            if (areaLength < 1 || areaLength > 32 || input.remaining() < areaLength + 4) {
                throw new IllegalArgumentException("CB_SIM_NAV: area length is invalid");
            }
            byte[] areaBytes = new byte[areaLength];
            input.get(areaBytes);
            int queryLength = input.getInt();
            if (queryLength < 1 || queryLength > 512 || input.remaining() != queryLength) {
                throw new IllegalArgumentException("CB_SIM_NAV: query length is invalid");
            }
            byte[] queryBytes = new byte[queryLength];
            input.get(queryBytes);
            NavigationTarget target = new NavigationTarget(
                    new String(areaBytes, StandardCharsets.UTF_8),
                    new String(queryBytes, StandardCharsets.UTF_8));
            if (!Arrays.equals(payload, target.toCanonicalPayload())) {
                throw new IllegalArgumentException("CB_SIM_NAV: payload is not canonical");
            }
            return target;
        }
    }

    public static final class NavigationObservation {
        private final String queryDigest;
        private final String poiId;
        private final String routeId;
        private final String labelKey;
        private final int distanceMeters;
        private final int durationSeconds;
        private final long revision;
        private final long capturedAtElapsedRealtimeMs;
        private final boolean mismatchInjected;

        public NavigationObservation(
                String queryDigest,
                String poiId,
                String routeId,
                String labelKey,
                int distanceMeters,
                int durationSeconds,
                long revision,
                long capturedAtElapsedRealtimeMs,
                boolean mismatchInjected) {
            this.queryDigest = requireDigest(queryDigest);
            this.poiId = requireStableId(poiId, "poiId");
            this.routeId = requireStableId(routeId, "routeId");
            this.labelKey = requireStableId(labelKey, "labelKey");
            if (distanceMeters < 100 || distanceMeters > 100_000
                    || durationSeconds < 60 || durationSeconds > 14_400
                    || revision < 1 || capturedAtElapsedRealtimeMs < 0) {
                throw new IllegalArgumentException("CB_SIM_NAV: observation metadata is invalid");
            }
            this.distanceMeters = distanceMeters;
            this.durationSeconds = durationSeconds;
            this.revision = revision;
            this.capturedAtElapsedRealtimeMs = capturedAtElapsedRealtimeMs;
            this.mismatchInjected = mismatchInjected;
        }

        public String getQueryDigest() {
            return queryDigest;
        }

        public String getPoiId() {
            return poiId;
        }

        public String getRouteId() {
            return routeId;
        }

        public String getLabelKey() {
            return labelKey;
        }

        public int getDistanceMeters() {
            return distanceMeters;
        }

        public int getDurationSeconds() {
            return durationSeconds;
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

        public boolean isSynthetic() {
            return true;
        }

        public boolean isProductionTrusted() {
            return false;
        }

        public boolean isLocationUploaded() {
            return false;
        }

        public boolean isExternalActivityStarted() {
            return false;
        }
    }

    private static final class DeterministicSyntheticNavigationBackend
            implements SyntheticNavigationBackend {
        @Override
        public NavigationObservation resolve(
                String queryDigest,
                long revision,
                long capturedAtElapsedRealtimeMs,
                boolean injectMismatch) {
            String seed = injectMismatch ? sha256Ascii(queryDigest + ":mismatch") : queryDigest;
            int distance = 500 + Integer.parseInt(seed.substring(0, 4), 16) % 19_500;
            int duration = 120 + Integer.parseInt(seed.substring(4, 8), 16) % 3_480;
            return new NavigationObservation(
                    queryDigest,
                    "synthetic.poi." + seed.substring(0, 12),
                    "synthetic.route." + seed.substring(12, 24),
                    "synthetic.navigation.poi_result",
                    distance,
                    duration,
                    revision,
                    capturedAtElapsedRealtimeMs,
                    injectMismatch);
        }

        @Override
        public void reset() {
            // Stateless deterministic backend.
        }
    }

    private final CapabilityCatalog capabilityCatalog = CapabilityCatalog.stage2Defaults();
    private final SyntheticNavigationBackend backend;
    private final Map<String, String> queryDigests = new LinkedHashMap<>();
    private final Map<String, NavigationObservation> observations = new LinkedHashMap<>();
    private long revision;

    public SimulatedNavigationEffectAdapter(
            SimulationClock clock,
            FaultInjectionProfile initialProfile) {
        this(clock, initialProfile, new DeterministicSyntheticNavigationBackend());
    }

    public SimulatedNavigationEffectAdapter(
            SimulationClock clock,
            FaultInjectionProfile initialProfile,
            SyntheticNavigationBackend backend) {
        super(ADAPTER_ID, DESTINATION, clock, initialProfile);
        this.backend = requireSafeBackend(backend);
    }

    public synchronized Optional<NavigationObservation> getObservation(
            String idempotencyToken) {
        return Optional.ofNullable(observations.get(idempotencyToken));
    }

    public synchronized Optional<String> getAdmittedQueryDigest(String idempotencyToken) {
        return Optional.ofNullable(queryDigests.get(idempotencyToken));
    }

    @Override
    protected void validateSimulationInvocation(EffectAdapter.Invocation invocation) {
        NavigationTarget target = decodeAndValidate(invocation);
        VehicleCapability capability = capabilityCatalog.require(target.getCapabilityId());
        if (!capability.getAvailability().isWritable()
                || !capability.getAvailability().isSimulatable()
                || capability.getAvailability().canUseProduction()
                || capability.getReportedSignalPath().isPresent()) {
            throw new IllegalStateException("CB_SIM_NAV: capability availability is unsafe");
        }
        if (!capability.getAreas().contains(target.getArea())) {
            throw new IllegalArgumentException("CB_SIM_NAV: area is unsupported");
        }
        capability.getTargetRange().validateText(target.getPoiQuery());
    }

    @Override
    protected synchronized void onSimulationAdmitted(
            EffectAdapter.Invocation invocation,
            FaultInjectionProfile profile) {
        NavigationTarget target = decodeAndValidate(invocation);
        queryDigests.put(invocation.getIdempotencyToken(), target.getQueryDigest());
    }

    @Override
    protected synchronized void onSimulationApplied(
            EffectAdapter.Invocation invocation,
            FaultInjectionProfile profile) {
        String queryDigest = queryDigests.get(invocation.getIdempotencyToken());
        if (queryDigest == null) {
            throw new IllegalStateException("CB_SIM_NAV: admitted query digest is missing");
        }
        requireSafeBackend(backend);
        boolean mismatch = profile.getMode()
                == FaultInjectionProfile.Mode.READBACK_MISMATCH;
        NavigationObservation observation = Objects.requireNonNull(
                backend.resolve(
                        queryDigest,
                        ++revision,
                        nowSimulationElapsedRealtimeMs(),
                        mismatch),
                "backend observation");
        if (!queryDigest.equals(observation.getQueryDigest())
                || observation.getRevision() != revision
                || observation.getSource() != SignalSource.SIMULATED
                || !observation.isSynthetic()
                || observation.isProductionTrusted()
                || observation.isLocationUploaded()
                || observation.isExternalActivityStarted()
                || observation.isMismatchInjected() != mismatch) {
            throw new IllegalStateException("CB_SIM_NAV: backend observation is unsafe");
        }
        observations.put(invocation.getIdempotencyToken(), observation);
    }

    @Override
    protected synchronized void onSimulationReset() {
        queryDigests.clear();
        observations.clear();
        revision = 0;
        backend.reset();
    }

    private NavigationTarget decodeAndValidate(EffectAdapter.Invocation invocation) {
        if (!DESTINATION.equals(invocation.getDestination())) {
            throw new IllegalArgumentException("CB_SIM_NAV: destination is invalid");
        }
        NavigationTarget target = NavigationTarget.fromCanonicalPayload(
                invocation.getCanonicalPayload());
        if (!target.getCapabilityId().getCanonicalId().equals(invocation.getActionId())) {
            throw new IllegalArgumentException("CB_SIM_NAV: action is invalid");
        }
        return target;
    }

    private static SyntheticNavigationBackend requireSafeBackend(
            SyntheticNavigationBackend backend) {
        Objects.requireNonNull(backend, "backend");
        if (!backend.isSimulationOnly()
                || backend.isProductionAuthorized()
                || backend.startsExternalActivity()
                || backend.usesNetwork()
                || backend.uploadsLocation()) {
            throw new IllegalArgumentException("CB_SIM_NAV: backend crosses simulation boundary");
        }
        return backend;
    }

    private static String canonicalizeQuery(String value) {
        Objects.requireNonNull(value, "poiQuery");
        return Normalizer.normalize(value, Normalizer.Form.NFKC).trim();
    }

    private static String requireCanonicalQuery(String value) {
        if (value == null || value.isEmpty() || value.length() > 128
                || !value.equals(canonicalizeQuery(value))) {
            throw new IllegalArgumentException("CB_SIM_NAV: query is invalid or non-canonical");
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                throw new IllegalArgumentException("CB_SIM_NAV: query contains control characters");
            }
        }
        return value;
    }

    private static String requireArea(String area) {
        if (!"cabin".equals(area)) {
            throw new IllegalArgumentException("CB_SIM_NAV: area must be cabin");
        }
        return area;
    }

    private static String requireDigest(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("CB_SIM_NAV: digest is invalid");
        }
        return value;
    }

    private static String requireStableId(String value, String field) {
        if (value == null || !value.matches("[a-z][a-z0-9_.]{2,95}")) {
            throw new IllegalArgumentException("CB_SIM_NAV: " + field + " is invalid");
        }
        return value;
    }

    private static String sha256Ascii(String value) {
        return sha256(value.getBytes(StandardCharsets.US_ASCII));
    }

    private static String sha256(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder output = new StringBuilder(digest.length * 2);
            for (byte current : digest) {
                output.append(String.format("%02x", current & 0xff));
            }
            return output.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
