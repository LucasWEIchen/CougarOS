package com.centralbrain.sdk.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public final class DevelopmentModelInputContractTest {
    @Test
    public void textOnlyInputAndReceiptAreDigestBound() {
        DevelopmentModelInput input = textInput();
        DevelopmentModelInputContract.validateMetadata(input);

        DevelopmentModelInputReceipt receipt = new DevelopmentModelInputReceipt();
        receipt.schemaVersion = DevelopmentModelInputContract.SCHEMA_VERSION;
        receipt.inputMode = DevelopmentModelInputContract.INPUT_TEXT_ONLY;
        receipt.sessionId = input.sessionId;
        receipt.scenarioId = input.scenarioId;
        receipt.inputText = input.inputText;
        receipt.imageMimeType = "";
        receipt.imageFileName = "";
        receipt.imageByteCount = 0L;
        receipt.imageSha256 = "";
        receipt.acceptedAtEpochMs = 1_750_000_000_000L;
        receipt.inputAggregateDigest =
                DevelopmentModelInputContract.calculateInputAggregateDigest(receipt);
        receipt.receiptDigest =
                DevelopmentModelInputContract.calculateReceiptDigest(receipt);

        DevelopmentModelInputContract.validateReceipt(receipt);
        assertEquals(64, receipt.inputAggregateDigest.length());
    }

    @Test
    public void textOnlyInputRejectsImageMetadataAndOversizedText() {
        DevelopmentModelInput withImageMetadata = textInput();
        withImageMetadata.imageMimeType = "image/png";
        assertThrows(
                IllegalArgumentException.class,
                () -> DevelopmentModelInputContract.validateMetadata(
                        withImageMetadata));

        DevelopmentModelInput oversized = textInput();
        oversized.inputText =
                "a".repeat(DevelopmentModelInputContract.MAX_TEXT_CHARS + 1);
        assertThrows(
                IllegalArgumentException.class,
                () -> DevelopmentModelInputContract.validateMetadata(oversized));
    }

    private static DevelopmentModelInput textInput() {
        DevelopmentModelInput input = new DevelopmentModelInput();
        input.schemaVersion = DevelopmentModelInputContract.SCHEMA_VERSION;
        input.inputMode = DevelopmentModelInputContract.INPUT_TEXT_ONLY;
        input.sessionId = "8d595630-2255-4f4d-ac0f-26a20ee96f29";
        input.scenarioId = "scene.aios.freeform.v1";
        input.inputText = "请把座舱调暖一点";
        input.imageMimeType = "";
        input.imageFileName = "";
        input.imageByteCount = 0L;
        input.imageSha256 = "";
        input.imageFd = null;
        return input;
    }
}
