package com.centralbrain.runtime.scenario;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.centralbrain.runtime.scenario.ScenarioCatalog.DisabledReason;
import com.centralbrain.runtime.scenario.ScenarioManifest.DrivingPolicy;
import com.centralbrain.runtime.scenario.ScenarioManifest.RiskClass;
import com.centralbrain.runtime.scenario.ScenarioManifestParser.ErrorCode;
import com.centralbrain.runtime.scenario.ScenarioManifestParser.ParseException;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ScenarioManifestParserTest {
    private static final Path ASSETS = Path.of("src/main/assets/scenarios");

    @Test
    public void loadsFourVersionedBuiltInScenariosWithStableDigests() throws Exception {
        ScenarioCatalog catalog = ScenarioCatalog.load(builtInAssets());

        assertEquals(4, catalog.size());
        assertTrue(catalog.disabled().isEmpty());
        assertTrue(catalog.getCatalogDigest().matches("[0-9a-f]{64}"));
        assertEquals(
                RiskClass.MEDIUM,
                catalog.require("scene.comfort.cold.v1").getRiskClass());
        ScenarioManifest fatigue = catalog.require("scene.fatigue.assist.v1");
        ScenarioManifest multimodal =
                catalog.require("scene.cabin.multimodal.assist.v1");
        assertEquals(2, multimodal.getVersion());
        assertEquals(RiskClass.HIGH, multimodal.getRiskClass());
        assertTrue(multimodal.getRequiredCapabilities().stream()
                .anyMatch(capability -> "navigation.poi".equals(
                        capability.getCanonicalId())));
        assertEquals(13, multimodal.getPlanTemplate().getNodes().size());
        assertEquals(6, multimodal.getPlanTemplate().getNodes().stream()
                .filter(node -> "tool.invoke".equals(node.getNodeType()))
                .count());
        assertEquals(3, multimodal.getPlanTemplate().getNodes().stream()
                .filter(node -> "approval.interrupt".equals(node.getNodeType()))
                .count());
        assertTrue(multimodal.getPlanTemplate().getNodes().stream()
                .noneMatch(node -> "effect.execute".equals(node.getNodeType())));
        assertEquals(RiskClass.HIGH, fatigue.getRiskClass());
        assertTrue(fatigue.getPlanTemplate().getNodes().stream()
                .anyMatch(node -> "vehicle.seat.recline".equals(
                        node.getCapabilityId() == null
                                ? "" : node.getCapabilityId().getCanonicalId())
                        && node.getPolicy().getDrivingPolicy() == DrivingPolicy.PARKED_ONLY
                        && node.getPolicy().isApprovalRequired()));
        assertEquals(
                sha256(asset("scene.comfort.cold.v1.json")),
                catalog.require("scene.comfort.cold.v1").getArtifactDigest());
        assertThrows(
                UnsupportedOperationException.class,
                () -> fatigue.getPlanTemplate().getNodes().clear());
        assertFalse(catalog.isArtifactCryptographicallyVerified());
        assertFalse(catalog.isProductionTrusted());
    }

    @Test
    public void rejectsUnknownAndDuplicateFieldsUnderStrictPolicy() throws Exception {
        ScenarioManifestParser parser = new ScenarioManifestParser();
        String source = text("scene.comfort.cold.v1.json");
        ParseException unknown = assertThrows(
                ParseException.class,
                () -> parser.parse(
                        "unknown-field.json",
                        source.replaceFirst("\\{", "{\"unknown\":true,")
                                .getBytes(StandardCharsets.UTF_8)));
        ParseException duplicate = assertThrows(
                ParseException.class,
                () -> parser.parse(
                        "duplicate-field.json",
                        source.replaceFirst(
                                        "\"schemaVersion\": 1,",
                                        "\"schemaVersion\": 1, \"schemaVersion\": 1,")
                                .getBytes(StandardCharsets.UTF_8)));

        assertEquals(ErrorCode.UNKNOWN_FIELD, unknown.getErrorCode());
        assertEquals(ErrorCode.DUPLICATE_FIELD, duplicate.getErrorCode());
    }

    @Test
    public void rejectsOversizeAndTrailingJson() throws Exception {
        ScenarioManifestParser parser = new ScenarioManifestParser();
        byte[] oversize = new byte[ScenarioManifestParser.MAX_MANIFEST_BYTES + 1];
        Arrays.fill(oversize, (byte) ' ');
        ParseException size = assertThrows(
                ParseException.class,
                () -> parser.parse("oversize.json", oversize));
        ParseException trailing = assertThrows(
                ParseException.class,
                () -> parser.parse(
                        "trailing.json",
                        (text("scene.comfort.cold.v1.json") + " {}")
                                .getBytes(StandardCharsets.UTF_8)));

        assertEquals(ErrorCode.OVERSIZE, size.getErrorCode());
        assertEquals(ErrorCode.MALFORMED_JSON, trailing.getErrorCode());
    }

    @Test
    public void duplicateScenarioIdsDisableAllCopiesWithoutAffectingOthers() throws Exception {
        Map<String, byte[]> assets = new LinkedHashMap<>();
        assets.put("cold-a.json", asset("scene.comfort.cold.v1.json"));
        assets.put("cold-b.json", asset("scene.comfort.cold.v1.json"));
        assets.put("rest.json", asset("scene.rest.nap.v1.json"));

        ScenarioCatalog catalog = ScenarioCatalog.load(assets);

        assertEquals(1, catalog.size());
        assertEquals(2, catalog.disabled().size());
        assertTrue(catalog.disabled().stream()
                .allMatch(value -> value.getReason() == DisabledReason.DUPLICATE_SCENARIO_ID));
        assertTrue(catalog.find("scene.comfort.cold.v1").isEmpty());
        assertTrue(catalog.find("scene.rest.nap.v1").isPresent());
    }

    @Test
    public void invalidManifestIsIsolatedFromValidCatalogEntries() throws Exception {
        Map<String, byte[]> assets = new LinkedHashMap<>();
        assets.put("cold.json", asset("scene.comfort.cold.v1.json"));
        assets.put(
                "invalid.json",
                text("scene.rest.nap.v1.json")
                        .replaceFirst("\\{", "{\"unknown\":true,")
                        .getBytes(StandardCharsets.UTF_8));

        ScenarioCatalog catalog = ScenarioCatalog.load(assets);

        assertEquals(1, catalog.size());
        assertEquals(1, catalog.disabled().size());
        assertEquals(DisabledReason.UNKNOWN_FIELD, catalog.disabled().get(0).getReason());
    }

    @Test
    public void rejectsUnknownCapabilityAndCyclicTemplate() throws Exception {
        ScenarioManifestParser parser = new ScenarioManifestParser();
        String source = text("scene.comfort.cold.v1.json");
        ParseException capability = assertThrows(
                ParseException.class,
                () -> parser.parse(
                        "unknown-capability.json",
                        source.replaceFirst(
                                        "vehicle[.]seat[.]heating",
                                        "vehicle.seat.unknown")
                                .getBytes(StandardCharsets.UTF_8)));
        String cyclic = source.replace(
                "\"prerequisiteNodeId\": \"evaluate_policy\", \"dependentNodeId\": \"set_hvac_power\"",
                "\"prerequisiteNodeId\": \"evaluate_policy\", \"dependentNodeId\": \"capture_context\"");
        ParseException cycle = assertThrows(
                ParseException.class,
                () -> parser.parse("cyclic.json", cyclic.getBytes(StandardCharsets.UTF_8)));

        assertEquals(ErrorCode.VALIDATION_FAILED, capability.getErrorCode());
        assertEquals(ErrorCode.VALIDATION_FAILED, cycle.getErrorCode());
    }

    @Test
    public void schemaDeclaresStrictVersionedObjects() throws Exception {
        String schema = new String(
                Files.readAllBytes(ASSETS.resolve("schema/scenario-manifest-v1.schema.json")),
                StandardCharsets.UTF_8);

        assertTrue(schema.contains("\"$schema\": \"https://json-schema.org/draft/2020-12/schema\""));
        assertTrue(schema.contains("\"schemaVersion\": {\"const\": 1}"));
        assertTrue(schema.split("\"additionalProperties\": false", -1).length >= 7);
    }

    private static Map<String, byte[]> builtInAssets() throws IOException {
        Map<String, byte[]> result = new LinkedHashMap<>();
        result.put("scene.cabin.multimodal.assist.v1.json",
                asset("scene.cabin.multimodal.assist.v1.json"));
        result.put("scene.comfort.cold.v1.json", asset("scene.comfort.cold.v1.json"));
        result.put("scene.fatigue.assist.v1.json", asset("scene.fatigue.assist.v1.json"));
        result.put("scene.rest.nap.v1.json", asset("scene.rest.nap.v1.json"));
        return result;
    }

    private static byte[] asset(String name) throws IOException {
        return Files.readAllBytes(ASSETS.resolve(name));
    }

    private static String text(String name) throws IOException {
        return new String(Files.readAllBytes(ASSETS.resolve(name)), StandardCharsets.UTF_8);
    }

    private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        byte[] value = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte current : value) {
            result.append(String.format("%02x", current & 0xff));
        }
        return result.toString();
    }
}
