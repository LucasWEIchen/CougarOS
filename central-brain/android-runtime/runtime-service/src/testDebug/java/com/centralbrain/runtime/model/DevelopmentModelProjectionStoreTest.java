package com.centralbrain.runtime.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotSame;

import com.centralbrain.sdk.model.DevelopmentModelProjection;

import org.junit.After;
import org.junit.Test;

public final class DevelopmentModelProjectionStoreTest {
    private final DevelopmentModelProjectionStore store =
            DevelopmentModelProjectionStore.getInstance();

    @After
    public void clear() {
        store.clearForTest();
    }

    @Test
    public void projectionIsOwnerScopedAndCopied() {
        String owner = "a".repeat(64);
        String session = "8d595630-2255-4f4d-ac0f-26a20ee96f29";
        store.publish(
                owner,
                session,
                "scene.fatigue.assist.v1",
                "android.local.development",
                "A bounded fatigue-care plan is ready.",
                1_234L,
                "b".repeat(64),
                1_750_000_000_000L);

        DevelopmentModelProjection first = store.getOwn(owner, session);
        DevelopmentModelProjection second = store.getOwn(owner, session);
        assertNotSame(first, second);
        assertEquals("android.local.development", first.providerId);
        assertNull(store.getOwn("c".repeat(64), session));
    }
}
