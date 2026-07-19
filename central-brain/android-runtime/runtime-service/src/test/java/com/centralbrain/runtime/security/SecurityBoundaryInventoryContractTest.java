package com.centralbrain.runtime.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.centralbrain.runtime.model.ModelContractV2;
import com.centralbrain.runtime.model.StructuredModelOutput;
import com.centralbrain.runtime.scenario.ScenarioCatalog;
import com.centralbrain.runtime.vehicle.capability.CapabilityCatalog;
import com.centralbrain.sdk.session.ICentralBrainSessionRuntime;
import com.centralbrain.sdk.session.SessionContract;
import com.centralbrain.sdk.session.SessionRequest;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

public final class SecurityBoundaryInventoryContractTest {
    private static final long NOW = 1_750_000_000_000L;
    private static final Path AIDL_ROOT = Path.of("../central-brain-sdk/src/main/aidl");
    private static final Path SCENARIOS = Path.of("src/main/assets/scenarios");
    private static final String TRACE = "a".repeat(64);
    private static final String INPUT = "b".repeat(64);

    @Test
    public void publicAidlTreeMatchesNineInterfacesThirtyFiveParcelablesAndNamespaces()
            throws Exception {
        int interfaces = 0;
        int parcelables = 0;
        Map<String, int[]> namespaces = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.walk(AIDL_ROOT)) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".aidl"))
                    .sorted().toList()) {
                String source = new String(
                        Files.readAllBytes(path), StandardCharsets.UTF_8);
                String namespace = AIDL_ROOT.relativize(path).getName(3).toString();
                int[] counts = namespaces.computeIfAbsent(namespace, ignored -> new int[2]);
                if (source.matches("(?sm).*^(?:oneway )?interface [A-Za-z0-9_]+ \\{.*")) {
                    interfaces++;
                    counts[0]++;
                } else if (source.matches("(?sm).*^parcelable [A-Za-z0-9_]+ \\{.*")) {
                    parcelables++;
                    counts[1]++;
                } else {
                    throw new AssertionError("unclassified AIDL surface: " + path.getFileName());
                }
            }
        }

        assertEquals(SecurityBoundaryInventoryContract.AIDL_INTERFACE_COUNT, interfaces);
        assertEquals(SecurityBoundaryInventoryContract.AIDL_PARCELABLE_COUNT, parcelables);
        assertEquals(SecurityBoundaryInventoryContract.AIDL_SURFACE_COUNT,
                interfaces + parcelables);
        assertEquals(SecurityBoundaryInventoryContract.namespaces().size(), namespaces.size());
        for (SecurityBoundaryInventoryContract.NamespaceCount expected
                : SecurityBoundaryInventoryContract.namespaces()) {
            int[] actual = namespaces.get(expected.getNamespace());
            assertEquals(expected.getNamespace(), expected.getInterfaceCount(), actual[0]);
            assertEquals(expected.getNamespace(), expected.getParcelableCount(), actual[1]);
        }
        assertTrue(SecurityBoundaryInventoryContract.inventoryDigest()
                .matches("[0-9a-f]{64}"));
    }

    @Test
    public void structuredModelOutputRejectsUnknownPathAndOversizeExactly() throws Exception {
        assertModelError(
                StructuredModelOutput.ErrorCode.UNKNOWN_FIELD,
                bytes(validOutput().replace("\"summary\"", "\"command\":\"shell\",\"summary\"")));
        assertModelError(
                StructuredModelOutput.ErrorCode.UNKNOWN_SCENARIO,
                bytes(validOutput().replace("scene.comfort.cold.v1", "../private/model")));
        assertModelError(
                StructuredModelOutput.ErrorCode.OVERSIZE,
                new byte[StructuredModelOutput.MAX_OUTPUT_BYTES + 1]);
    }

    @Test
    public void sessionContractRejectsAggregateUtteranceOversize() {
        SessionRequest request = new SessionRequest();
        request.requestId = "8d595630-2255-4f4d-ac0f-26a20ee96f29";
        request.scenarioId = "scene.fatigue.assist.v1";
        request.utterance = "x".repeat(SessionContract.MAX_UTTERANCE_CHARS + 1);
        request.source = ICentralBrainSessionRuntime.SOURCE_HMI_BUTTON;
        request.seatZone = ICentralBrainSessionRuntime.SEAT_ZONE_DRIVER;
        request.locale = "en-US";
        request.deadlineEpochMs = NOW + 60_000L;
        request.clientContextVersion = 4;

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> SessionContract.validateRequest(request, NOW));
        assertTrue(failure.getMessage().startsWith("CB_SESSION_CONTRACT:"));
    }

    @Test
    public void inventoryDoesNotClaimProbeExecutionFuzzAndroidOrProductionQualification() {
        assertEquals(8, SecurityBoundaryInventoryContract.VALIDATION_FAMILY_COUNT);
        assertTrue(SecurityBoundaryInventoryContract.isAidlParcelInventoryComplete());
        assertTrue(SecurityBoundaryInventoryContract.isHostPathOversizeAggregateVerified());
        assertTrue(SecurityBoundaryInventoryContract.isAndroidDebugProbeAvailable());
        assertFalse(SecurityBoundaryInventoryContract.isAndroidDebugProbeExecuted());
        assertFalse(SecurityBoundaryInventoryContract.isCoverageGuidedFuzzComplete());
        assertFalse(SecurityBoundaryInventoryContract
                .isBinderCallingUidSpoofAndroidVerified());
        assertFalse(SecurityBoundaryInventoryContract
                .isPackageSignatureCryptographicallyVerified());
        assertFalse(SecurityBoundaryInventoryContract.isAndroid13Arm64Verified());
        assertFalse(SecurityBoundaryInventoryContract.isRuntimeWired());
        assertFalse(SecurityBoundaryInventoryContract.isHardwareAccessed());
        assertFalse(SecurityBoundaryInventoryContract.isProductionReady());
        assertFalse(SecurityBoundaryInventoryContract.isTargetHardwareValidated());
    }

    private static void assertModelError(
            StructuredModelOutput.ErrorCode expected,
            byte[] output) throws Exception {
        StructuredModelOutput.ValidationException failure = assertThrows(
                StructuredModelOutput.ValidationException.class,
                () -> StructuredModelOutput.validate(
                        request(), output, scenarioCatalog(), CapabilityCatalog.stage2Defaults()));
        assertEquals(expected, failure.getErrorCode());
    }

    private static ScenarioCatalog scenarioCatalog() throws IOException {
        Map<String, byte[]> assets = new LinkedHashMap<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(
                SCENARIOS, "scene.*.json")) {
            for (Path path : stream) {
                assets.put(path.getFileName().toString(), Files.readAllBytes(path));
            }
        }
        return ScenarioCatalog.load(assets);
    }

    private static ModelContractV2.ModelRequest request() {
        return new ModelContractV2.ModelRequest(
                "security-boundary-probe",
                ModelContractV2.Purpose.SCENARIO_REASONING,
                ModelContractV2.PrivacyClass.INTERNAL,
                new ModelContractV2.LatencyBudget(1_500),
                new ModelContractV2.TokenBudget(256, 256, 512),
                ModelContractV2.RequiredCapability.STRUCTURED_SCENARIO_CANDIDATE,
                ModelContractV2.FallbackPolicy.NO_FALLBACK,
                TRACE,
                INPUT);
    }

    private static String validOutput() {
        return "{\"schemaVersion\":1,"
                + "\"scenarioId\":\"scene.comfort.cold.v1\","
                + "\"parameters\":["
                + "{\"capabilityId\":\"vehicle.hvac.target_temperature\","
                + "\"area\":\"row1.driver\",\"value\":22.5}],"
                + "\"summary\":\"scenario proposal ready\"}";
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
