package com.centralbrain.runtime.model;

import com.centralbrain.sdk.model.DevelopmentModelInput;
import com.centralbrain.sdk.model.DevelopmentModelInputContract;
import com.centralbrain.sdk.model.DevelopmentModelInputReceipt;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Bounded, process-local, consume-once storage for debug multimodal inputs. */
public final class DevelopmentModelInputStore {
    private static final int MAX_ENTRIES = 4;
    private static final long MAX_TOTAL_BYTES = 12L * 1024L * 1024L;
    private static final DevelopmentModelInputStore INSTANCE =
            new DevelopmentModelInputStore();

    private final Map<String, Entry> bySession = new LinkedHashMap<>();
    private long totalBytes;

    private DevelopmentModelInputStore() {}

    public static DevelopmentModelInputStore getInstance() {
        return INSTANCE;
    }

    public synchronized DevelopmentModelInputReceipt stage(
            String ownerFingerprint,
            DevelopmentModelInput input,
            byte[] imageBytes,
            long acceptedAtEpochMs) {
        requireOwner(ownerFingerprint);
        DevelopmentModelInputContract.validateMetadata(input);
        Objects.requireNonNull(imageBytes, "imageBytes");
        if (imageBytes.length != input.imageByteCount) {
            throw violation("image byte count mismatch");
        }
        if (acceptedAtEpochMs <= 0L) {
            throw violation("acceptance time must be positive");
        }
        DevelopmentModelInputReceipt receipt = receipt(input, acceptedAtEpochMs);
        Entry existing = bySession.get(input.sessionId);
        if (existing != null) {
            if (!existing.ownerFingerprint.equals(ownerFingerprint)
                    || !existing.receipt.inputAggregateDigest.equals(
                            receipt.inputAggregateDigest)) {
                throw violation("session input conflict");
            }
            return copy(existing.receipt);
        }
        while (!bySession.isEmpty()
                && (bySession.size() >= MAX_ENTRIES
                || totalBytes + imageBytes.length > MAX_TOTAL_BYTES)) {
            Iterator<Entry> oldest = bySession.values().iterator();
            Entry evicted = oldest.next();
            oldest.remove();
            totalBytes -= evicted.imageBytes.length;
            Arrays.fill(evicted.imageBytes, (byte) 0);
        }
        if (totalBytes + imageBytes.length > MAX_TOTAL_BYTES) {
            throw violation("multimodal input capacity exhausted");
        }
        byte[] owned = imageBytes.clone();
        bySession.put(input.sessionId, new Entry(
                ownerFingerprint, copy(receipt), owned));
        totalBytes += owned.length;
        return copy(receipt);
    }

    public synchronized ConsumedInput consumeOwn(
            String ownerFingerprint, String sessionId, String scenarioId) {
        requireOwner(ownerFingerprint);
        Entry entry = bySession.get(sessionId);
        if (entry == null
                || !entry.ownerFingerprint.equals(ownerFingerprint)
                || !entry.receipt.scenarioId.equals(scenarioId)) {
            return null;
        }
        bySession.remove(sessionId);
        totalBytes -= entry.imageBytes.length;
        byte[] transferred = entry.imageBytes;
        return new ConsumedInput(copy(entry.receipt), transferred);
    }

    synchronized void clearForTest() {
        for (Entry entry : bySession.values()) {
            Arrays.fill(entry.imageBytes, (byte) 0);
        }
        bySession.clear();
        totalBytes = 0L;
    }

    private static DevelopmentModelInputReceipt receipt(
            DevelopmentModelInput input, long acceptedAtEpochMs) {
        DevelopmentModelInputReceipt result = new DevelopmentModelInputReceipt();
        result.schemaVersion = DevelopmentModelInputContract.SCHEMA_VERSION;
        result.sessionId = input.sessionId;
        result.scenarioId = input.scenarioId;
        result.inputText = input.inputText.trim();
        result.imageMimeType = input.imageMimeType;
        result.imageFileName = input.imageFileName;
        result.imageByteCount = input.imageByteCount;
        result.imageSha256 = input.imageSha256;
        result.acceptedAtEpochMs = acceptedAtEpochMs;
        result.inputAggregateDigest =
                DevelopmentModelInputContract.calculateInputAggregateDigest(result);
        result.receiptDigest =
                DevelopmentModelInputContract.calculateReceiptDigest(result);
        DevelopmentModelInputContract.validateReceipt(result);
        return result;
    }

    private static DevelopmentModelInputReceipt copy(DevelopmentModelInputReceipt source) {
        DevelopmentModelInputReceipt copy = new DevelopmentModelInputReceipt();
        copy.schemaVersion = source.schemaVersion;
        copy.sessionId = source.sessionId;
        copy.scenarioId = source.scenarioId;
        copy.inputText = source.inputText;
        copy.imageMimeType = source.imageMimeType;
        copy.imageFileName = source.imageFileName;
        copy.imageByteCount = source.imageByteCount;
        copy.imageSha256 = source.imageSha256;
        copy.inputAggregateDigest = source.inputAggregateDigest;
        copy.acceptedAtEpochMs = source.acceptedAtEpochMs;
        copy.receiptDigest = source.receiptDigest;
        return copy;
    }

    private static void requireOwner(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw violation("owner fingerprint is invalid");
        }
    }

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_DEVELOPMENT_MODEL_INPUT_STORE: " + message);
    }

    private static final class Entry {
        private final String ownerFingerprint;
        private final DevelopmentModelInputReceipt receipt;
        private final byte[] imageBytes;

        private Entry(
                String ownerFingerprint,
                DevelopmentModelInputReceipt receipt,
                byte[] imageBytes) {
            this.ownerFingerprint = ownerFingerprint;
            this.receipt = receipt;
            this.imageBytes = imageBytes;
        }
    }

    public static final class ConsumedInput implements AutoCloseable {
        private final DevelopmentModelInputReceipt receipt;
        private byte[] imageBytes;

        private ConsumedInput(
                DevelopmentModelInputReceipt receipt, byte[] imageBytes) {
            this.receipt = receipt;
            this.imageBytes = imageBytes;
        }

        public DevelopmentModelInputReceipt getReceipt() {
            return copy(receipt);
        }

        public byte[] copyImageBytes() {
            if (imageBytes == null) {
                throw violation("consumed input is closed");
            }
            return imageBytes.clone();
        }

        @Override
        public void close() {
            if (imageBytes != null) {
                Arrays.fill(imageBytes, (byte) 0);
                imageBytes = null;
            }
        }
    }
}
