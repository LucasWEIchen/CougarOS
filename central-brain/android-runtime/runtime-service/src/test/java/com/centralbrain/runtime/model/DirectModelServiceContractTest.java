package com.centralbrain.runtime.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class DirectModelServiceContractTest {
    private static final String DIGEST_A = "a".repeat(64);
    private static final String DIGEST_B = "b".repeat(64);
    private static final String DIGEST_C = "c".repeat(64);
    private static final String DIGEST_D = "d".repeat(64);

    @Test
    public void productionOllamaEndpointIsFixedAndAgentGatewayFree() {
        DirectModelServiceContract.Endpoint endpoint =
                DirectModelServiceContract.productionOllama("vendor/qwen3.6:27b");

        assertEquals(
                DirectModelServiceContract.PRODUCTION_PROFILE_ID,
                endpoint.getProfileId());
        assertEquals(
                DirectModelServiceContract.WireProtocol.OLLAMA_CHAT_V1,
                endpoint.getWireProtocol());
        assertEquals(
                "http://169.254.208.110:11434/api/chat",
                endpoint.getChatUri().toString());
        assertEquals("vendor/qwen3.6:27b", endpoint.getModelName());
        assertTrue(endpoint.isTextSupported());
        assertTrue(endpoint.isImageSupported());
        assertTrue(endpoint.isStreamingSupported());
        assertTrue(endpoint.isStructuredJsonSupported());
        assertFalse(endpoint.isAgentGatewayRequired());
        assertFalse(endpoint.isArbitraryEndpointOverrideAllowed());
        assertThrows(
                IllegalArgumentException.class,
                () -> DirectModelServiceContract.productionOllama(
                        "http://untrusted.example/model"));
    }

    @Test
    public void textAndImageRequestBindsAllDigestsWithoutGrantingAuthority() {
        DirectModelServiceContract.Endpoint endpoint =
                DirectModelServiceContract.productionOllama("qwen3.6:27b");
        DirectModelServiceContract.ImageDescriptor image =
                new DirectModelServiceContract.ImageDescriptor(
                        "image/png",
                        2_244_206,
                        DIGEST_D);
        DirectModelServiceContract.Request request =
                new DirectModelServiceContract.Request(
                        endpoint,
                        "request.direct.1",
                        "session.driver.1",
                        "idempotency.direct.1",
                        DirectModelServiceContract.Modality.TEXT_IMAGE,
                        DIGEST_A,
                        DIGEST_B,
                        DIGEST_C,
                        image,
                        120_000);

        assertEquals(DirectModelServiceContract.Modality.TEXT_IMAGE, request.getModality());
        assertEquals(image, request.getImage());
        assertTrue(request.getRequestFingerprint().matches("[0-9a-f]{64}"));
        assertFalse(request.containsRawModelInput());
        assertFalse(request.grantsToolAuthority());
        assertFalse(request.grantsEffectAuthority());
    }

    @Test
    public void fingerprintChangesWithModalityAndImage() {
        DirectModelServiceContract.Endpoint endpoint =
                DirectModelServiceContract.productionOllama("qwen3.6:27b");
        DirectModelServiceContract.Request text =
                new DirectModelServiceContract.Request(
                        endpoint,
                        "request.direct.2",
                        "session.driver.1",
                        "idempotency.direct.2",
                        DirectModelServiceContract.Modality.TEXT,
                        DIGEST_A,
                        DIGEST_B,
                        DIGEST_C,
                        null,
                        120_000);
        DirectModelServiceContract.Request multimodal =
                new DirectModelServiceContract.Request(
                        endpoint,
                        "request.direct.2",
                        "session.driver.1",
                        "idempotency.direct.2",
                        DirectModelServiceContract.Modality.TEXT_IMAGE,
                        DIGEST_A,
                        DIGEST_B,
                        DIGEST_C,
                        new DirectModelServiceContract.ImageDescriptor(
                                "image/jpeg",
                                1024,
                                DIGEST_D),
                        120_000);

        assertNotEquals(text.getRequestFingerprint(), multimodal.getRequestFingerprint());
    }

    @Test
    public void modalityMimeAndSizeViolationsFailClosed() {
        DirectModelServiceContract.Endpoint endpoint =
                DirectModelServiceContract.productionOllama("qwen3.6:27b");
        assertThrows(IllegalArgumentException.class, () ->
                new DirectModelServiceContract.ImageDescriptor(
                        "image/webp",
                        1024,
                        DIGEST_D));
        assertThrows(IllegalArgumentException.class, () ->
                new DirectModelServiceContract.ImageDescriptor(
                        "image/png",
                        DirectModelServiceContract.MAX_IMAGE_BYTES + 1,
                        DIGEST_D));
        assertThrows(IllegalArgumentException.class, () ->
                new DirectModelServiceContract.Request(
                        endpoint,
                        "request.direct.3",
                        "session.driver.1",
                        "idempotency.direct.3",
                        DirectModelServiceContract.Modality.TEXT_IMAGE,
                        DIGEST_A,
                        DIGEST_B,
                        DIGEST_C,
                        null,
                        120_000));
        assertThrows(IllegalArgumentException.class, () ->
                new DirectModelServiceContract.Request(
                        endpoint,
                        "request.direct.4",
                        "session.driver.1",
                        "idempotency.direct.4",
                        DirectModelServiceContract.Modality.TEXT,
                        DIGEST_A,
                        DIGEST_B,
                        DIGEST_C,
                        new DirectModelServiceContract.ImageDescriptor(
                                "image/png",
                                1024,
                                DIGEST_D),
                        120_000));
    }
}
