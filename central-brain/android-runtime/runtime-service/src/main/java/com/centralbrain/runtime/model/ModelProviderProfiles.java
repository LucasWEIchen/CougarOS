package com.centralbrain.runtime.model;

import java.util.Objects;

/** Current Android model-provider profiles; no profile is production-routable. */
public final class ModelProviderProfiles {
    public static final String DETERMINISTIC_STUB_ID = "deterministic.stub";
    public static final String ANDROID_LOCAL_DEVELOPMENT_ID =
            "android.local.development";
    public static final String VENDOR_NPU_EMPTY_ID = "vendor.npu.empty";
    public static final String TARGET_OPENCLAW_TRANSITIONAL_ID =
            "external.openclaw.transitional";

    private static final Profile DETERMINISTIC_STUB = createDeterministicStub();
    private static final Profile ANDROID_LOCAL_DEVELOPMENT =
            createAndroidLocalDevelopment();
    private static final Profile VENDOR_NPU_EMPTY = createVendorNpuEmpty();
    private static final Profile TARGET_OPENCLAW_TRANSITIONAL =
            createTargetOpenClawTransitional();

    private ModelProviderProfiles() {
    }

    public static Profile deterministicStub() {
        return DETERMINISTIC_STUB;
    }

    public static Profile androidLocalDevelopment() {
        return ANDROID_LOCAL_DEVELOPMENT;
    }

    public static Profile vendorNpuEmpty() {
        return VENDOR_NPU_EMPTY;
    }

    public static Profile targetOpenClawTransitional() {
        return TARGET_OPENCLAW_TRANSITIONAL;
    }

    private static Profile createDeterministicStub() {
        ModelProvider.Descriptor descriptor = new ModelProvider.Descriptor(
                DETERMINISTIC_STUB_ID,
                ModelProvider.BackendKind.DETERMINISTIC_STUB,
                ModelProvider.Assurance.TEST_ONLY,
                ModelProvider.FallbackClass.TEST_ONLY,
                false,
                false,
                true,
                true,
                true,
                true,
                true,
                true,
                1);
        ModelProvider.Snapshot snapshot = new ModelProvider.Snapshot(
                descriptor,
                ModelProvider.LifecycleState.COLD,
                ModelProvider.HealthState.HEALTHY,
                0,
                0,
                0,
                false,
                "STUB_IMPLEMENTATION_NOT_WIRED");
        return new Profile(descriptor, snapshot, false, false);
    }

    private static Profile createVendorNpuEmpty() {
        ModelProvider.Descriptor descriptor = new ModelProvider.Descriptor(
                VENDOR_NPU_EMPTY_ID,
                ModelProvider.BackendKind.VENDOR_NPU,
                ModelProvider.Assurance.EMPTY,
                ModelProvider.FallbackClass.NEVER,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                0);
        ModelProvider.Snapshot snapshot = new ModelProvider.Snapshot(
                descriptor,
                ModelProvider.LifecycleState.UNAVAILABLE,
                ModelProvider.HealthState.UNAVAILABLE,
                0,
                0,
                0,
                false,
                "VENDOR_RUNTIME_UNAVAILABLE");
        return new Profile(descriptor, snapshot, false, false);
    }

    private static Profile createAndroidLocalDevelopment() {
        ModelProvider.Descriptor descriptor = new ModelProvider.Descriptor(
                ANDROID_LOCAL_DEVELOPMENT_ID,
                ModelProvider.BackendKind.ANDROID_LOCAL_DEVELOPMENT,
                ModelProvider.Assurance.DEBUG_ONLY,
                ModelProvider.FallbackClass.NEVER,
                false,
                false,
                true,
                true,
                true,
                true,
                true,
                true,
                1);
        ModelProvider.Snapshot snapshot = new ModelProvider.Snapshot(
                descriptor,
                ModelProvider.LifecycleState.COLD,
                ModelProvider.HealthState.HEALTHY,
                0,
                0,
                0,
                false,
                "LOCAL_DEVELOPMENT_IMPLEMENTATION_NOT_CONFIGURED");
        return new Profile(descriptor, snapshot, false, false);
    }

    private static Profile createTargetOpenClawTransitional() {
        ModelProvider.Descriptor descriptor = new ModelProvider.Descriptor(
                TARGET_OPENCLAW_TRANSITIONAL_ID,
                ModelProvider.BackendKind.OPENCLAW_GATEWAY,
                ModelProvider.Assurance.TARGET_INTEGRATION,
                ModelProvider.FallbackClass.NEVER,
                false,
                false,
                true,
                true,
                true,
                true,
                true,
                true,
                1);
        ModelProvider.Snapshot snapshot = new ModelProvider.Snapshot(
                descriptor,
                ModelProvider.LifecycleState.COLD,
                ModelProvider.HealthState.HEALTHY,
                0,
                0,
                0,
                false,
                "TARGET_OPENCLAW_CREDENTIAL_NOT_PROVISIONED");
        return new Profile(descriptor, snapshot, false, false);
    }

    public static final class Profile {
        private final ModelProvider.Descriptor descriptor;
        private final ModelProvider.Snapshot snapshot;
        private final boolean implementationConfigured;
        private final boolean routingEnabled;

        Profile(
                ModelProvider.Descriptor descriptor,
                ModelProvider.Snapshot snapshot,
                boolean implementationConfigured,
                boolean routingEnabled) {
            this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
            this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
            if (!descriptor.getProviderId().equals(snapshot.getProviderId())) {
                throw new IllegalArgumentException("profile descriptor and snapshot must agree");
            }
            if (routingEnabled
                    && (!implementationConfigured
                            || !descriptor.isSupportsInference()
                            || snapshot.getLifecycleState()
                                    != ModelProvider.LifecycleState.READY
                            || snapshot.getHealthState() != ModelProvider.HealthState.HEALTHY)) {
                throw new IllegalArgumentException(
                        "routing requires a configured, ready and healthy provider");
            }
            this.implementationConfigured = implementationConfigured;
            this.routingEnabled = routingEnabled;
        }

        public ModelProvider.Descriptor getDescriptor() {
            return descriptor;
        }

        public ModelProvider.Snapshot getSnapshot() {
            return snapshot;
        }

        public boolean isImplementationConfigured() {
            return implementationConfigured;
        }

        public boolean isRoutingEnabled() {
            return routingEnabled;
        }

        public String diagnosticDetail() {
            return "provider_id=" + descriptor.getProviderId()
                    + " backend=" + descriptor.getBackendKind()
                    + " assurance=" + descriptor.getAssurance()
                    + " lifecycle=" + snapshot.getLifecycleState()
                    + " health=" + snapshot.getHealthState()
                    + " max_concurrency=" + descriptor.getMaxConcurrentRequests()
                    + " implementation_configured=" + implementationConfigured
                    + " routing_enabled=" + routingEnabled
                    + " hardware_backed=" + descriptor.isHardwareBacked()
                    + " hardware_accessed=" + snapshot.isHardwareAccessed()
                    + " production_eligible=" + descriptor.isProductionEligible();
        }
    }
}
