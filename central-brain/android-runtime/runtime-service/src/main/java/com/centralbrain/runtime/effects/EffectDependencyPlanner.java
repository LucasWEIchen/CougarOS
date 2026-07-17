package com.centralbrain.runtime.effects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Deterministic dependency planner that serializes conflicting Effect resources into waves. */
public final class EffectDependencyPlanner {
    public Plan plan(EffectBatch batch) {
        Objects.requireNonNull(batch, "batch");
        Map<String, EffectBatch.Entry> pending = new LinkedHashMap<>();
        for (EffectBatch.Entry entry : batch.getEntries()) {
            pending.put(entry.getEffectId(), entry);
        }
        Set<String> scheduled = new HashSet<>();
        List<Wave> waves = new ArrayList<>();
        while (!pending.isEmpty()) {
            Set<String> waveResources = new HashSet<>();
            List<String> waveEffectIds = new ArrayList<>();
            for (EffectBatch.Entry entry : pending.values()) {
                if (!scheduled.containsAll(entry.getDependencyEffectIds())
                        || !waveResources.add(entry.getResourceKey())) {
                    continue;
                }
                waveEffectIds.add(entry.getEffectId());
            }
            if (waveEffectIds.isEmpty()) {
                throw violation("effect dependency graph contains a cycle");
            }
            for (String effectId : waveEffectIds) {
                pending.remove(effectId);
                scheduled.add(effectId);
            }
            waves.add(new Wave(waves.size(), waveEffectIds, batch));
        }
        return new Plan(batch, waves);
    }

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_EFFECT_DEPENDENCY: " + message);
    }

    public static final class Plan {
        private final String batchId;
        private final String batchDigest;
        private final List<Wave> waves;
        private final Map<String, Integer> waveByEffectId;
        private final String planDigest;

        private Plan(EffectBatch batch, List<Wave> waves) {
            this.batchId = batch.getBatchId();
            this.batchDigest = batch.getBatchDigest();
            this.waves = Collections.unmodifiableList(new ArrayList<>(waves));
            Map<String, Integer> index = new LinkedHashMap<>();
            List<String> digestParts = new ArrayList<>();
            digestParts.add(batchId);
            digestParts.add(batchDigest);
            digestParts.add(Integer.toString(waves.size()));
            for (Wave wave : waves) {
                digestParts.add(Integer.toString(wave.waveIndex));
                digestParts.add(Integer.toString(wave.effectIds.size()));
                for (String effectId : wave.effectIds) {
                    if (index.put(effectId, wave.waveIndex) != null) {
                        throw violation("effect was scheduled more than once");
                    }
                    digestParts.add(effectId);
                    digestParts.add(batch.requireEntry(effectId).getResourceKey());
                }
            }
            if (index.size() != batch.getEntries().size()) {
                throw violation("dependency plan omitted an effect");
            }
            this.waveByEffectId = Collections.unmodifiableMap(index);
            this.planDigest = EffectBatch.digest(
                    "effect.dependency.plan.v1", digestParts.toArray(new String[0]));
        }

        public String getBatchId() {
            return batchId;
        }

        public String getBatchDigest() {
            return batchDigest;
        }

        public List<Wave> getWaves() {
            return waves;
        }

        public int getWaveIndex(String effectId) {
            Integer result = waveByEffectId.get(effectId);
            if (result == null) {
                throw violation("effect is not present in the dependency plan");
            }
            return result;
        }

        public String getPlanDigest() {
            return planDigest;
        }
    }

    public static final class Wave {
        private final int waveIndex;
        private final List<String> effectIds;
        private final List<String> resourceKeys;

        private Wave(int waveIndex, List<String> effectIds, EffectBatch batch) {
            this.waveIndex = waveIndex;
            this.effectIds = Collections.unmodifiableList(new ArrayList<>(effectIds));
            List<String> resources = new ArrayList<>(effectIds.size());
            Set<String> unique = new HashSet<>();
            for (String effectId : effectIds) {
                String resource = batch.requireEntry(effectId).getResourceKey();
                if (!unique.add(resource)) {
                    throw violation("one dependency wave contains a resource conflict");
                }
                resources.add(resource);
            }
            this.resourceKeys = Collections.unmodifiableList(resources);
        }

        public int getWaveIndex() {
            return waveIndex;
        }

        public List<String> getEffectIds() {
            return effectIds;
        }

        public List<String> getResourceKeys() {
            return resourceKeys;
        }
    }
}
