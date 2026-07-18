package com.centralbrain.runtime.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Fixed provider catalog and freshness-bounded health registry. It does not route requests.
 * Req IDs: S2-MDL-001, S2-SAF-001, S2-OBS-001, DEL-001, DEL-004, DEL-005.
 */
public final class ModelProviderRegistry {
    public static final int SCHEMA_VERSION = 1;
    public static final int PROVIDER_COUNT = 4;
    public static final long MAX_HEALTH_VALIDITY_MS = 60_000L;
    public static final String DETERMINISTIC_TEST_ID =
            ModelProviderProfiles.DETERMINISTIC_STUB_ID;
    public static final String ANDROID_LOCAL_DEVELOPMENT_ID =
            ModelProviderProfiles.ANDROID_LOCAL_DEVELOPMENT_ID;
    public static final String VENDOR_NPU_PLACEHOLDER_ID =
            ModelProviderProfiles.VENDOR_NPU_EMPTY_ID;
    public static final String CLOUD_PLACEHOLDER_ID = "cloud.placeholder";

    private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9.:-]{1,128}");
    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");
    private static final List<ProviderDescriptor> FIXED_CATALOG = createFixedCatalog();
    private static final String FIXED_CATALOG_DIGEST = digestCatalog(FIXED_CATALOG);

    public enum ProviderKind {
        DETERMINISTIC_ANDROID_TEST,
        ANDROID_LOCAL_DEVELOPMENT,
        VENDOR_NPU,
        CLOUD
    }

    public enum HealthSource {
        CONTRACT_TEST,
        LOCAL_DEVELOPMENT_RUNTIME,
        VENDOR_RUNTIME,
        CLOUD_CONTROL_PLANE
    }

    public enum HealthState {
        UNKNOWN,
        HEALTHY,
        DEGRADED,
        UNHEALTHY,
        UNAVAILABLE
    }

    public enum HealthFreshness {
        MISSING,
        FRESH,
        STALE
    }

    public enum PublishCode {
        UPDATED,
        REPLAYED,
        UNKNOWN_PROVIDER,
        SOURCE_MISMATCH,
        REJECTED_FUTURE,
        REJECTED_EXPIRED,
        REJECTED_REVISION,
        REVISION_CONFLICT
    }

    public static final class ProviderDescriptor {
        private final String providerId;
        private final ProviderKind kind;
        private final HealthSource healthSource;
        private final Set<ModelContractV2.RequiredCapability> capabilities;
        private final boolean contractTestAvailable;
        private final boolean developmentAvailable;
        private final boolean productionImplementationAvailable;
        private final boolean productionEligible;
        private final boolean networkRequired;
        private final boolean hardwareExpected;
        private final String descriptorDigest;

        private ProviderDescriptor(
                String providerId,
                ProviderKind kind,
                HealthSource healthSource,
                Set<ModelContractV2.RequiredCapability> capabilities,
                boolean contractTestAvailable,
                boolean developmentAvailable,
                boolean productionImplementationAvailable,
                boolean productionEligible,
                boolean networkRequired,
                boolean hardwareExpected) {
            this.providerId = requireIdentifier(providerId, "providerId");
            this.kind = Objects.requireNonNull(kind, "kind");
            this.healthSource = Objects.requireNonNull(healthSource, "healthSource");
            if (capabilities == null || capabilities.isEmpty()) {
                throw new IllegalArgumentException("capabilities must not be empty");
            }
            this.capabilities = Collections.unmodifiableSet(EnumSet.copyOf(capabilities));
            if (productionEligible && !productionImplementationAvailable) {
                throw new IllegalArgumentException(
                        "production eligibility requires an available production implementation");
            }
            if (contractTestAvailable && (developmentAvailable
                    || productionImplementationAvailable
                    || productionEligible)) {
                throw new IllegalArgumentException(
                        "contract-test availability must remain isolated");
            }
            if (kind == ProviderKind.CLOUD && !networkRequired) {
                throw new IllegalArgumentException("cloud provider must declare network dependency");
            }
            if (kind != ProviderKind.CLOUD && networkRequired) {
                throw new IllegalArgumentException(
                        "only the cloud provider may declare network dependency");
            }
            if (kind == ProviderKind.VENDOR_NPU != hardwareExpected) {
                throw new IllegalArgumentException(
                        "only the vendor NPU profile may expect hardware");
            }
            this.contractTestAvailable = contractTestAvailable;
            this.developmentAvailable = developmentAvailable;
            this.productionImplementationAvailable = productionImplementationAvailable;
            this.productionEligible = productionEligible;
            this.networkRequired = networkRequired;
            this.hardwareExpected = hardwareExpected;
            this.descriptorDigest = sha256(canonicalForm());
        }

        public String getProviderId() {
            return providerId;
        }

        public ProviderKind getKind() {
            return kind;
        }

        public HealthSource getHealthSource() {
            return healthSource;
        }

        public Set<ModelContractV2.RequiredCapability> getCapabilities() {
            return capabilities;
        }

        public boolean supports(ModelContractV2.RequiredCapability capability) {
            return capabilities.contains(Objects.requireNonNull(capability, "capability"));
        }

        public boolean isContractTestAvailable() {
            return contractTestAvailable;
        }

        public boolean isDevelopmentAvailable() {
            return developmentAvailable;
        }

        public boolean isProductionImplementationAvailable() {
            return productionImplementationAvailable;
        }

        public boolean isProductionEligible() {
            return productionEligible;
        }

        public boolean isNetworkRequired() {
            return networkRequired;
        }

        public boolean isHardwareExpected() {
            return hardwareExpected;
        }

        public String getDescriptorDigest() {
            return descriptorDigest;
        }

        private String canonicalForm() {
            List<String> capabilityNames = new ArrayList<>();
            for (ModelContractV2.RequiredCapability capability : capabilities) {
                capabilityNames.add(capability.name());
            }
            Collections.sort(capabilityNames);
            return providerId
                    + "|" + kind.name()
                    + "|" + healthSource.name()
                    + "|" + String.join(",", capabilityNames)
                    + "|" + contractTestAvailable
                    + "|" + developmentAvailable
                    + "|" + productionImplementationAvailable
                    + "|" + productionEligible
                    + "|" + networkRequired
                    + "|" + hardwareExpected;
        }
    }

    public static final class HealthReport {
        private final String providerId;
        private final HealthSource source;
        private final HealthState state;
        private final long revision;
        private final long observedAtElapsedMs;
        private final long validUntilElapsedMs;
        private final String sourceEvidenceDigest;
        private final String reportDigest;

        public HealthReport(
                String providerId,
                HealthSource source,
                HealthState state,
                long revision,
                long observedAtElapsedMs,
                long validUntilElapsedMs,
                String sourceEvidenceDigest) {
            this.providerId = requireIdentifier(providerId, "providerId");
            this.source = Objects.requireNonNull(source, "source");
            this.state = Objects.requireNonNull(state, "state");
            if (revision < 1) {
                throw new IllegalArgumentException("revision must be positive");
            }
            if (observedAtElapsedMs < 0
                    || validUntilElapsedMs <= observedAtElapsedMs
                    || validUntilElapsedMs - observedAtElapsedMs
                            > MAX_HEALTH_VALIDITY_MS) {
                throw new IllegalArgumentException("health validity window is invalid");
            }
            this.revision = revision;
            this.observedAtElapsedMs = observedAtElapsedMs;
            this.validUntilElapsedMs = validUntilElapsedMs;
            this.sourceEvidenceDigest = requireDigest(
                    sourceEvidenceDigest,
                    "sourceEvidenceDigest");
            this.reportDigest = sha256(canonicalForm());
        }

        public String getProviderId() {
            return providerId;
        }

        public HealthSource getSource() {
            return source;
        }

        public HealthState getState() {
            return state;
        }

        public long getRevision() {
            return revision;
        }

        public long getObservedAtElapsedMs() {
            return observedAtElapsedMs;
        }

        public long getValidUntilElapsedMs() {
            return validUntilElapsedMs;
        }

        public String getSourceEvidenceDigest() {
            return sourceEvidenceDigest;
        }

        public String getReportDigest() {
            return reportDigest;
        }

        private String canonicalForm() {
            return providerId
                    + "|" + source.name()
                    + "|" + state.name()
                    + "|" + revision
                    + "|" + observedAtElapsedMs
                    + "|" + validUntilElapsedMs
                    + "|" + sourceEvidenceDigest;
        }
    }

    public static final class PublishResult {
        private final PublishCode code;
        private final String providerId;
        private final long acceptedRevision;

        private PublishResult(PublishCode code, String providerId, long acceptedRevision) {
            this.code = Objects.requireNonNull(code, "code");
            this.providerId = requireIdentifier(providerId, "providerId");
            this.acceptedRevision = acceptedRevision;
        }

        public PublishCode getCode() {
            return code;
        }

        public String getProviderId() {
            return providerId;
        }

        public long getAcceptedRevision() {
            return acceptedRevision;
        }

        public boolean isUpdated() {
            return code == PublishCode.UPDATED;
        }
    }

    public static final class ProviderView {
        private final ProviderDescriptor descriptor;
        private final HealthState healthState;
        private final HealthFreshness healthFreshness;
        private final long healthRevision;
        private final String healthEvidenceDigest;

        private ProviderView(
                ProviderDescriptor descriptor,
                HealthState healthState,
                HealthFreshness healthFreshness,
                long healthRevision,
                String healthEvidenceDigest) {
            this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
            this.healthState = Objects.requireNonNull(healthState, "healthState");
            this.healthFreshness = Objects.requireNonNull(healthFreshness, "healthFreshness");
            this.healthRevision = healthRevision;
            this.healthEvidenceDigest = healthEvidenceDigest;
        }

        public ProviderDescriptor getDescriptor() {
            return descriptor;
        }

        public HealthState getHealthState() {
            return healthState;
        }

        public HealthFreshness getHealthFreshness() {
            return healthFreshness;
        }

        public long getHealthRevision() {
            return healthRevision;
        }

        public String getHealthEvidenceDigest() {
            return healthEvidenceDigest;
        }

        public boolean isContractTestAvailable() {
            return descriptor.isContractTestAvailable();
        }

        public boolean isDevelopmentAvailable() {
            return descriptor.isDevelopmentAvailable();
        }

        public boolean isProductionReady() {
            return descriptor.isProductionImplementationAvailable()
                    && descriptor.isProductionEligible()
                    && healthFreshness == HealthFreshness.FRESH
                    && healthState == HealthState.HEALTHY;
        }

        public boolean isRoutingEnabled() {
            return false;
        }
    }

    public static final class RegistrySnapshot {
        private final List<ProviderView> providers;
        private final String catalogDigest;
        private final int contractTestAvailableCount;
        private final int developmentAvailableCount;
        private final int productionReadyCount;

        private RegistrySnapshot(List<ProviderView> providers, String catalogDigest) {
            this.providers = Collections.unmodifiableList(new ArrayList<>(providers));
            this.catalogDigest = requireDigest(catalogDigest, "catalogDigest");
            int testCount = 0;
            int developmentCount = 0;
            int productionCount = 0;
            for (ProviderView provider : providers) {
                if (provider.isContractTestAvailable()) {
                    testCount++;
                }
                if (provider.isDevelopmentAvailable()) {
                    developmentCount++;
                }
                if (provider.isProductionReady()) {
                    productionCount++;
                }
            }
            this.contractTestAvailableCount = testCount;
            this.developmentAvailableCount = developmentCount;
            this.productionReadyCount = productionCount;
        }

        public int getSchemaVersion() {
            return SCHEMA_VERSION;
        }

        public List<ProviderView> getProviders() {
            return providers;
        }

        public String getCatalogDigest() {
            return catalogDigest;
        }

        public int getContractTestAvailableCount() {
            return contractTestAvailableCount;
        }

        public int getDevelopmentAvailableCount() {
            return developmentAvailableCount;
        }

        public int getProductionReadyCount() {
            return productionReadyCount;
        }

        public boolean isProductionRoutingEnabled() {
            return false;
        }

        public boolean isModelInvoked() {
            return false;
        }

        public boolean isNetworkAccessed() {
            return false;
        }

        public boolean isNpuAccessed() {
            return false;
        }

        public boolean isHardwareAccessed() {
            return false;
        }
    }

    private final Map<String, ProviderDescriptor> descriptors = new LinkedHashMap<>();
    private final Map<String, HealthReport> healthByProvider = new LinkedHashMap<>();

    private ModelProviderRegistry() {
        for (ProviderDescriptor descriptor : FIXED_CATALOG) {
            descriptors.put(descriptor.getProviderId(), descriptor);
        }
    }

    public static ModelProviderRegistry createForContractTest() {
        return new ModelProviderRegistry();
    }

    public synchronized PublishResult publishHealth(HealthReport report, long nowElapsedMs) {
        Objects.requireNonNull(report, "report");
        requireElapsed(nowElapsedMs, "nowElapsedMs");
        ProviderDescriptor descriptor = descriptors.get(report.getProviderId());
        if (descriptor == null) {
            return result(PublishCode.UNKNOWN_PROVIDER, report, 0);
        }
        HealthReport current = healthByProvider.get(report.getProviderId());
        long currentRevision = current == null ? 0 : current.getRevision();
        if (descriptor.getHealthSource() != report.getSource()) {
            return result(PublishCode.SOURCE_MISMATCH, report, currentRevision);
        }
        if (report.getObservedAtElapsedMs() > nowElapsedMs) {
            return result(PublishCode.REJECTED_FUTURE, report, currentRevision);
        }
        if (report.getValidUntilElapsedMs() <= nowElapsedMs) {
            return result(PublishCode.REJECTED_EXPIRED, report, currentRevision);
        }
        if (current != null) {
            if (report.getRevision() < current.getRevision()) {
                return result(PublishCode.REJECTED_REVISION, report, currentRevision);
            }
            if (report.getRevision() == current.getRevision()) {
                PublishCode code = report.getReportDigest().equals(current.getReportDigest())
                        ? PublishCode.REPLAYED
                        : PublishCode.REVISION_CONFLICT;
                return result(code, report, currentRevision);
            }
        }
        healthByProvider.put(report.getProviderId(), report);
        return result(PublishCode.UPDATED, report, report.getRevision());
    }

    public synchronized RegistrySnapshot snapshot(long nowElapsedMs) {
        requireElapsed(nowElapsedMs, "nowElapsedMs");
        List<ProviderView> views = new ArrayList<>();
        for (ProviderDescriptor descriptor : FIXED_CATALOG) {
            HealthReport report = healthByProvider.get(descriptor.getProviderId());
            if (report == null) {
                views.add(new ProviderView(
                        descriptor,
                        HealthState.UNKNOWN,
                        HealthFreshness.MISSING,
                        0,
                        null));
            } else if (report.getValidUntilElapsedMs() <= nowElapsedMs) {
                views.add(new ProviderView(
                        descriptor,
                        HealthState.UNKNOWN,
                        HealthFreshness.STALE,
                        report.getRevision(),
                        report.getSourceEvidenceDigest()));
            } else {
                views.add(new ProviderView(
                        descriptor,
                        report.getState(),
                        HealthFreshness.FRESH,
                        report.getRevision(),
                        report.getSourceEvidenceDigest()));
            }
        }
        return new RegistrySnapshot(views, FIXED_CATALOG_DIGEST);
    }

    private static PublishResult result(
            PublishCode code,
            HealthReport report,
            long acceptedRevision) {
        return new PublishResult(code, report.getProviderId(), acceptedRevision);
    }

    private static List<ProviderDescriptor> createFixedCatalog() {
        List<ProviderDescriptor> catalog = new ArrayList<>();
        catalog.add(new ProviderDescriptor(
                DETERMINISTIC_TEST_ID,
                ProviderKind.DETERMINISTIC_ANDROID_TEST,
                HealthSource.CONTRACT_TEST,
                EnumSet.of(ModelContractV2.RequiredCapability.TEXT_GENERATION),
                true,
                false,
                false,
                false,
                false,
                false));
        catalog.add(new ProviderDescriptor(
                ANDROID_LOCAL_DEVELOPMENT_ID,
                ProviderKind.ANDROID_LOCAL_DEVELOPMENT,
                HealthSource.LOCAL_DEVELOPMENT_RUNTIME,
                EnumSet.of(
                        ModelContractV2.RequiredCapability.TEXT_GENERATION,
                        ModelContractV2.RequiredCapability.SUMMARIZATION),
                false,
                true,
                false,
                false,
                false,
                false));
        catalog.add(new ProviderDescriptor(
                VENDOR_NPU_PLACEHOLDER_ID,
                ProviderKind.VENDOR_NPU,
                HealthSource.VENDOR_RUNTIME,
                EnumSet.allOf(ModelContractV2.RequiredCapability.class),
                false,
                false,
                false,
                false,
                false,
                true));
        catalog.add(new ProviderDescriptor(
                CLOUD_PLACEHOLDER_ID,
                ProviderKind.CLOUD,
                HealthSource.CLOUD_CONTROL_PLANE,
                EnumSet.allOf(ModelContractV2.RequiredCapability.class),
                false,
                false,
                false,
                false,
                true,
                false));
        catalog.sort(Comparator.comparing(ProviderDescriptor::getProviderId));
        if (catalog.size() != PROVIDER_COUNT) {
            throw new IllegalStateException("fixed provider catalog size is invalid");
        }
        return Collections.unmodifiableList(catalog);
    }

    private static String digestCatalog(List<ProviderDescriptor> catalog) {
        StringBuilder canonical = new StringBuilder();
        canonical.append(SCHEMA_VERSION);
        for (ProviderDescriptor descriptor : catalog) {
            canonical.append('|')
                    .append(descriptor.getProviderId())
                    .append(':')
                    .append(descriptor.getDescriptorDigest());
        }
        return sha256(canonical.toString());
    }

    private static void requireElapsed(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }

    private static String requireIdentifier(String value, String name) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static String requireDigest(String value, String name) {
        if (value == null || !DIGEST.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a lowercase SHA-256 digest");
        }
        return value;
    }

    private static String sha256(String value) {
        try {
            byte[] encoded = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(encoded.length * 2);
            for (byte item : encoded) {
                builder.append(String.format("%02x", item & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
