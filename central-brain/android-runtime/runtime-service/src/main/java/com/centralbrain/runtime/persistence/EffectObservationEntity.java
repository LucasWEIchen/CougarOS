package com.centralbrain.runtime.persistence;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

/** Immutable typed effect readback metadata. Evidence is digest/reference only. */
@Entity(
        tableName = "effect_observations",
        primaryKeys = {"effect_id", "sequence"},
        foreignKeys = @ForeignKey(
                entity = SessionEntity.class,
                parentColumns = "session_id",
                childColumns = "session_id",
                onDelete = ForeignKey.CASCADE),
        indices = {
                @Index(name = "index_effect_observations_session", value = "session_id"),
                @Index(
                        name = "index_effect_observations_observation",
                        value = "observation_id",
                        unique = true)
        })
public final class EffectObservationEntity {
    @NonNull
    @ColumnInfo(name = "effect_id")
    public String effectId = "";

    public long sequence;

    @NonNull
    @ColumnInfo(name = "observation_id")
    public String observationId = "";

    @NonNull
    @ColumnInfo(name = "session_id")
    public String sessionId = "";

    public int state;

    public int source;

    @ColumnInfo(name = "attempt_count")
    public int attemptCount;

    @NonNull
    @ColumnInfo(name = "target_digest")
    public String targetDigest = "";

    @NonNull
    @ColumnInfo(name = "reported_digest")
    public String reportedDigest = "";

    @NonNull
    @ColumnInfo(name = "evidence_digest")
    public String evidenceDigest = "";

    @NonNull
    @ColumnInfo(name = "observation_digest")
    public String observationDigest = "";

    public boolean terminal;

    @ColumnInfo(name = "observed_at_wall_ms")
    public long observedAtWallMs;
}
