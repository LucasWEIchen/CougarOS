package com.centralbrain.runtime.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Immutable dynamic Tool health evidence in an elapsed-realtime clock domain. */
public final class ToolHealthSnapshot {
    public static final int MAX_OBSERVATIONS = ToolRegistry.MAX_REGISTRATIONS;

    private static final Pattern CHECK_ID = Pattern.compile(
            "[a-z][a-z0-9_-]*(?:[.][a-z0-9][a-z0-9_-]*){1,7}");

    public enum State {
        HEALTHY,
        UNHEALTHY,
        UNKNOWN
    }

    public enum Eligibility {
        HEALTHY,
        MISSING,
        UNKNOWN,
        UNHEALTHY,
        STALE,
        CLOCK_INVALID
    }

    public static final class Observation {
        private final String checkId;
        private final State state;
        private final long observedAtElapsedRealtimeMs;
        private final long revision;

        public Observation(
                String checkId,
                State state,
                long observedAtElapsedRealtimeMs,
                long revision) {
            if (checkId == null || !CHECK_ID.matcher(checkId).matches()) {
                throw new IllegalArgumentException("CB_TOOL_HEALTH: checkId is not canonical");
            }
            this.checkId = checkId;
            this.state = Objects.requireNonNull(state, "state");
            if (observedAtElapsedRealtimeMs < 0L) {
                throw new IllegalArgumentException(
                        "CB_TOOL_HEALTH: observed time must be non-negative");
            }
            if (revision < 1L) {
                throw new IllegalArgumentException(
                        "CB_TOOL_HEALTH: revision must be positive");
            }
            this.observedAtElapsedRealtimeMs = observedAtElapsedRealtimeMs;
            this.revision = revision;
        }

        public String getCheckId() {
            return checkId;
        }

        public State getState() {
            return state;
        }

        public long getObservedAtElapsedRealtimeMs() {
            return observedAtElapsedRealtimeMs;
        }

        public long getRevision() {
            return revision;
        }
    }

    private final NavigableMap<String, Observation> observations;

    public ToolHealthSnapshot(List<Observation> source) {
        Objects.requireNonNull(source, "source");
        if (source.size() > MAX_OBSERVATIONS) {
            throw new IllegalArgumentException(
                    "CB_TOOL_HEALTH: observation count exceeds bound");
        }
        TreeMap<String, Observation> copy = new TreeMap<>();
        for (Observation candidate : source) {
            Observation observation = Objects.requireNonNull(candidate, "observation");
            if (copy.put(observation.checkId, observation) != null) {
                throw new IllegalArgumentException(
                        "CB_TOOL_HEALTH: duplicate checkId");
            }
        }
        observations = Collections.unmodifiableNavigableMap(copy);
    }

    public static ToolHealthSnapshot empty() {
        return new ToolHealthSnapshot(List.of());
    }

    public int size() {
        return observations.size();
    }

    public List<Observation> observations() {
        return Collections.unmodifiableList(new ArrayList<>(observations.values()));
    }

    public Eligibility eligibility(ToolManifest manifest, long nowElapsedRealtimeMs) {
        Objects.requireNonNull(manifest, "manifest");
        if (nowElapsedRealtimeMs < 0L) {
            return Eligibility.CLOCK_INVALID;
        }
        ToolManifest.HealthContract contract = manifest.getHealthContract();
        Observation observation = observations.get(contract.getCheckId());
        if (observation == null) {
            return Eligibility.MISSING;
        }
        if (observation.observedAtElapsedRealtimeMs > nowElapsedRealtimeMs) {
            return Eligibility.CLOCK_INVALID;
        }
        if (observation.state == State.UNKNOWN) {
            return Eligibility.UNKNOWN;
        }
        if (observation.state == State.UNHEALTHY) {
            return Eligibility.UNHEALTHY;
        }
        long age = nowElapsedRealtimeMs - observation.observedAtElapsedRealtimeMs;
        if (age > contract.getMaximumStalenessMs()) {
            return Eligibility.STALE;
        }
        return Eligibility.HEALTHY;
    }
}
