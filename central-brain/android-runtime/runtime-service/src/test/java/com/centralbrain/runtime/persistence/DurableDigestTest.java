package com.centralbrain.runtime.persistence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public final class DurableDigestTest {
    @Test
    public void isDeterministicDomainSeparatedAndLengthFramed() {
        String first = DurableDigest.sha256("task-v1", "ab", "c");
        assertEquals(64, first.length());
        assertEquals(first, DurableDigest.sha256("task-v1", "ab", "c"));
        assertNotEquals(first, DurableDigest.sha256("task-v2", "ab", "c"));
        assertNotEquals(first, DurableDigest.sha256("task-v1", "a", "bc"));
    }
}
