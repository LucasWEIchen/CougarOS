package com.centralbrain.runtime.effects;

import com.centralbrain.sdk.effect.EffectContract;
import com.centralbrain.sdk.effect.EffectIntent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Immutable, digest-bound Effect batch input for P3-W06 coordination. */
public final class EffectBatch {
    public static final int MAX_EFFECTS = 16;
    public static final int MAX_DEPENDENCIES_PER_EFFECT = 16;

    private static final Pattern RESOURCE_KEY =
            Pattern.compile("[a-z][a-z0-9_-]*(?::[a-z0-9][a-z0-9_-]*){1,7}");
    private static final Pattern QUALIFIED_ID =
            Pattern.compile("[a-z][a-z0-9_-]*(?:[.][a-z0-9][a-z0-9_-]*){1,7}");
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    private final String batchId;
    private final String sessionId;
    private final String planId;
    private final String actionId;
    private final String planDigest;
    private final List<Entry> entries;
    private final Map<String, Entry> entriesByEffectId;
    private final String batchDigest;

    private EffectBatch(String batchId, List<Entry> source, long nowEpochMs) {
        this.batchId = canonicalUuid(batchId, "batchId");
        if (source == null || source.isEmpty() || source.size() > MAX_EFFECTS) {
            throw violation("batch must contain 1..16 effects");
        }
        if (nowEpochMs <= 0L) {
            throw violation("validation time must be positive");
        }

        List<Entry> copy = new ArrayList<>(source.size());
        Map<String, Entry> byId = new LinkedHashMap<>();
        Set<String> idempotencyKeys = new HashSet<>();
        String expectedSession = null;
        String expectedPlan = null;
        String expectedAction = null;
        String expectedPlanDigest = null;
        for (Entry input : source) {
            Entry entry = new Entry(
                    Objects.requireNonNull(input, "entry").intent,
                    input.resourceKey,
                    input.dependencyEffectIds);
            EffectIntent intent = entry.intent;
            EffectContract.validateIntent(intent, nowEpochMs);
            if (byId.put(intent.effectId, entry) != null) {
                throw violation("batch contains a duplicate effect ID");
            }
            if (!idempotencyKeys.add(intent.idempotencyKey)) {
                throw violation("batch contains a duplicate idempotency key");
            }
            if (expectedSession == null) {
                expectedSession = intent.sessionId;
                expectedPlan = intent.planId;
                expectedAction = intent.actionId;
                expectedPlanDigest = intent.planDigest;
            } else if (!expectedSession.equals(intent.sessionId)
                    || !expectedPlan.equals(intent.planId)
                    || !expectedAction.equals(intent.actionId)
                    || !expectedPlanDigest.equals(intent.planDigest)) {
                throw violation("batch effects do not share one governed action binding");
            }
            copy.add(entry);
        }

        for (Entry entry : copy) {
            for (String dependencyId : entry.dependencyEffectIds) {
                Entry dependency = byId.get(dependencyId);
                if (dependency == null) {
                    throw violation("effect dependency is outside the batch");
                }
                if (entry.intent.effectId.equals(dependencyId)) {
                    throw violation("effect cannot depend on itself");
                }
                if (entry.intent.required && !dependency.intent.required) {
                    throw violation("required effect cannot depend on an optional effect");
                }
            }
        }

        this.sessionId = expectedSession;
        this.planId = expectedPlan;
        this.actionId = expectedAction;
        this.planDigest = expectedPlanDigest;
        this.entries = Collections.unmodifiableList(copy);
        this.entriesByEffectId = Collections.unmodifiableMap(byId);
        List<String> digestParts = new ArrayList<>();
        digestParts.add(this.batchId);
        digestParts.add(this.sessionId);
        digestParts.add(this.planId);
        digestParts.add(this.actionId);
        digestParts.add(this.planDigest);
        digestParts.add(Integer.toString(this.entries.size()));
        for (Entry entry : this.entries) {
            EffectIntent intent = entry.intent;
            digestParts.add("entry");
            digestParts.add(intent.effectId);
            digestParts.add(intent.nodeId);
            digestParts.add(intent.capabilityId);
            digestParts.add(intent.targetArea);
            digestParts.add(intent.targetValueDigest);
            digestParts.add(intent.idempotencyKey);
            digestParts.add(intent.contextDigest);
            digestParts.add(Long.toString(intent.contextVersion));
            digestParts.add(Boolean.toString(intent.required));
            digestParts.add(entry.resourceKey);
            digestParts.add(Integer.toString(entry.dependencyEffectIds.size()));
            digestParts.addAll(entry.dependencyEffectIds);
        }
        this.batchDigest = digest("effect.batch.v1", digestParts.toArray(new String[0]));
    }

    public static EffectBatch create(String batchId, List<Entry> entries, long nowEpochMs) {
        return new EffectBatch(batchId, entries, nowEpochMs);
    }

    public String getBatchId() {
        return batchId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getPlanId() {
        return planId;
    }

    public String getActionId() {
        return actionId;
    }

    public String getPlanDigest() {
        return planDigest;
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public String getBatchDigest() {
        return batchDigest;
    }

    Entry requireEntry(String effectId) {
        Entry entry = entriesByEffectId.get(effectId);
        if (entry == null) {
            throw violation("effect is not part of the batch");
        }
        return entry;
    }

    static String canonicalUuid(String value, String label) {
        if (value == null || value.length() != 36) {
            throw violation(label + " is not a canonical UUID");
        }
        try {
            String canonical = UUID.fromString(value).toString();
            if (!canonical.equals(value)) {
                throw violation(label + " is not a canonical UUID");
            }
            return canonical;
        } catch (IllegalArgumentException exception) {
            throw violation(label + " is not a canonical UUID");
        }
    }

    static String requireQualifiedId(String value, int maxChars, String label) {
        if (value == null
                || value.length() > maxChars
                || !QUALIFIED_ID.matcher(value).matches()) {
            throw violation(label + " is not canonical");
        }
        return value;
    }

    static String requireDigest(String value, String label) {
        if (value == null || !SHA_256.matcher(value).matches()) {
            throw violation(label + " must be lowercase SHA-256");
        }
        return value;
    }

    static String digest(String domain, String... values) {
        requireQualifiedId(domain, 64, "digest domain");
        try {
            MessageDigest result = MessageDigest.getInstance("SHA-256");
            update(result, domain);
            for (String value : values) {
                update(result, Objects.requireNonNull(value, "digest value"));
            }
            return hex(result.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (encoded.length >>> 24));
        digest.update((byte) (encoded.length >>> 16));
        digest.update((byte) (encoded.length >>> 8));
        digest.update((byte) encoded.length);
        digest.update(encoded);
    }

    static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(Character.forDigit((value >>> 4) & 0xf, 16));
            result.append(Character.forDigit(value & 0xf, 16));
        }
        return result.toString();
    }

    static EffectIntent copyIntent(EffectIntent source) {
        Objects.requireNonNull(source, "intent");
        EffectIntent copy = new EffectIntent();
        copy.schemaVersion = source.schemaVersion;
        copy.effectId = source.effectId;
        copy.sessionId = source.sessionId;
        copy.planId = source.planId;
        copy.nodeId = source.nodeId;
        copy.actionId = source.actionId;
        copy.capabilityId = source.capabilityId;
        copy.targetArea = source.targetArea;
        copy.valueKind = source.valueKind;
        copy.booleanValue = source.booleanValue;
        copy.integerValue = source.integerValue;
        copy.decimalValue = source.decimalValue;
        copy.textValue = source.textValue;
        copy.unit = source.unit;
        copy.targetValueDigest = source.targetValueDigest;
        copy.idempotencyKey = source.idempotencyKey;
        copy.planDigest = source.planDigest;
        copy.contextDigest = source.contextDigest;
        copy.contextVersion = source.contextVersion;
        copy.riskClass = source.riskClass;
        copy.required = source.required;
        copy.verificationPolicy = source.verificationPolicy;
        copy.verificationTolerance = source.verificationTolerance;
        copy.reversible = source.reversible;
        copy.compensationDigest = source.compensationDigest;
        copy.createdAtEpochMs = source.createdAtEpochMs;
        copy.deadlineEpochMs = source.deadlineEpochMs;
        return copy;
    }

    static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_EFFECT_BATCH: " + message);
    }

    public static final class Entry {
        private final EffectIntent intent;
        private final String resourceKey;
        private final List<String> dependencyEffectIds;

        public Entry(
                EffectIntent intent,
                String resourceKey,
                List<String> dependencyEffectIds) {
            this.intent = copyIntent(intent);
            if (resourceKey == null
                    || resourceKey.length() > 96
                    || !RESOURCE_KEY.matcher(resourceKey).matches()) {
                throw violation("resource key is not canonical");
            }
            this.resourceKey = resourceKey;
            if (dependencyEffectIds == null
                    || dependencyEffectIds.size() > MAX_DEPENDENCIES_PER_EFFECT) {
                throw violation("dependency count exceeds the batch bound");
            }
            Set<String> unique = new HashSet<>();
            List<String> dependencies = new ArrayList<>(dependencyEffectIds.size());
            for (String dependencyId : dependencyEffectIds) {
                String canonical = canonicalUuid(dependencyId, "dependencyEffectId");
                if (!unique.add(canonical)) {
                    throw violation("effect contains a duplicate dependency");
                }
                dependencies.add(canonical);
            }
            Collections.sort(dependencies);
            this.dependencyEffectIds = Collections.unmodifiableList(dependencies);
        }

        public EffectIntent getIntent() {
            return copyIntent(intent);
        }

        public String getEffectId() {
            return intent.effectId;
        }

        public String getResourceKey() {
            return resourceKey;
        }

        public List<String> getDependencyEffectIds() {
            return dependencyEffectIds;
        }

        public boolean isRequired() {
            return intent.required;
        }

        EffectIntent internalIntentCopy() {
            return copyIntent(intent);
        }
    }
}
