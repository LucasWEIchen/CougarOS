package com.centralbrain.runtime.model;

import com.centralbrain.sdk.model.DevelopmentModelProjection;
import com.centralbrain.sdk.model.DevelopmentModelProjectionContract;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Process-local bounded store for debug UX projection. Raw model text is never persisted. */
public final class DevelopmentModelProjectionStore {
    private static final int MAX_ENTRIES = 16;
    private static final DevelopmentModelProjectionStore INSTANCE =
            new DevelopmentModelProjectionStore();

    private final Map<String, Entry> bySession = new LinkedHashMap<>();

    private DevelopmentModelProjectionStore() {}

    public static DevelopmentModelProjectionStore getInstance() {
        return INSTANCE;
    }

    public synchronized void publish(
            String ownerFingerprint,
            String sessionId,
            String scenarioId,
            String providerId,
            String assistantDisplayText,
            long latencyMs,
            String outputDigest,
            long completedAtEpochMs) {
        DevelopmentModelProjection projection = new DevelopmentModelProjection();
        projection.schemaVersion = DevelopmentModelProjectionContract.SCHEMA_VERSION;
        projection.sessionId = sessionId;
        projection.scenarioId = scenarioId;
        projection.providerId = providerId;
        projection.assistantDisplayText = assistantDisplayText;
        projection.latencyMs = latencyMs;
        projection.outputDigest = outputDigest;
        projection.completedAtEpochMs = completedAtEpochMs;
        projection.projectionDigest =
                DevelopmentModelProjectionContract.calculateDigest(projection);
        DevelopmentModelProjectionContract.validate(projection);
        if (ownerFingerprint == null || !ownerFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("model projection owner is invalid");
        }
        if (!bySession.containsKey(sessionId) && bySession.size() >= MAX_ENTRIES) {
            Iterator<String> oldest = bySession.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
        bySession.put(sessionId, new Entry(ownerFingerprint, copy(projection)));
    }

    public synchronized DevelopmentModelProjection getOwn(
            String ownerFingerprint, String sessionId) {
        Entry entry = bySession.get(sessionId);
        if (entry == null || !entry.ownerFingerprint.equals(ownerFingerprint)) {
            return null;
        }
        return copy(entry.projection);
    }

    synchronized void clearForTest() {
        bySession.clear();
    }

    private static DevelopmentModelProjection copy(DevelopmentModelProjection source) {
        DevelopmentModelProjection copy = new DevelopmentModelProjection();
        copy.schemaVersion = source.schemaVersion;
        copy.sessionId = source.sessionId;
        copy.scenarioId = source.scenarioId;
        copy.providerId = source.providerId;
        copy.assistantDisplayText = source.assistantDisplayText;
        copy.latencyMs = source.latencyMs;
        copy.outputDigest = source.outputDigest;
        copy.projectionDigest = source.projectionDigest;
        copy.completedAtEpochMs = source.completedAtEpochMs;
        return copy;
    }

    private static final class Entry {
        private final String ownerFingerprint;
        private final DevelopmentModelProjection projection;

        private Entry(String ownerFingerprint, DevelopmentModelProjection projection) {
            this.ownerFingerprint = ownerFingerprint;
            this.projection = projection;
        }
    }
}
