package com.centralbrain.runtime.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.After;
import org.junit.Test;

public final class OpenClawCredentialStoreTest {
    @After
    public void clearCredential() {
        OpenClawCredentialStore.clear();
    }

    @Test
    public void credentialIsCopiedAndHeldOnlyUntilExplicitClear() {
        char[] source = "unit-test-token".toCharArray();
        OpenClawCredentialStore.provision(source);
        Arrays.fill(source, 'x');

        assertTrue(OpenClawCredentialStore.isProvisioned());
        assertEquals("unit-test-token", OpenClawCredentialStore.requireToken());

        OpenClawCredentialStore.clear();
        assertFalse(OpenClawCredentialStore.isProvisioned());
        assertThrows(IllegalStateException.class,
                OpenClawCredentialStore::requireToken);
    }

    @Test
    public void malformedCredentialsFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> OpenClawCredentialStore.provision("short".toCharArray()));
        assertThrows(IllegalArgumentException.class,
                () -> OpenClawCredentialStore.provision("invalid token".toCharArray()));
    }
}
