package com.centralbrain.runtime.security;

/** Debug-only identity acquisition probe; no raw identity is returned. */
interface ISecurityIdentityProbe {
    boolean verifyCallingUid(int expectedUid, int spoofedUid);
    boolean verifyCallingPackage(String expectedPackage, String spoofedPackage);
    boolean verifyCallingSigner(
            String expectedPackage,
            String expectedSignerSha256,
            String spoofedSignerSha256);
}
