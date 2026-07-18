package com.centralbrain.runtime.security;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import com.centralbrain.runtime.identity.AndroidCallerIdentityResolver;
import com.centralbrain.runtime.identity.CallerIdentitySnapshot;

/** Debug-only cross-process proof that caller identity comes from Binder and PackageManager. */
public final class SecurityIdentityProbeService extends Service {
    private AndroidCallerIdentityResolver identityResolver;
    private final ISecurityIdentityProbe.Stub probe = new ISecurityIdentityProbe.Stub() {
        @Override
        public boolean verifyCallingUid(int expectedUid, int spoofedUid) {
            int callingUid = Binder.getCallingUid();
            CallerIdentitySnapshot identity = identityResolver.resolveCallingIdentity();
            return identity.isResolved()
                    && identity.getUid() == callingUid
                    && callingUid == expectedUid
                    && callingUid != spoofedUid;
        }

        @Override
        public boolean verifyCallingPackage(String expectedPackage, String spoofedPackage) {
            CallerIdentitySnapshot identity = identityResolver.resolveCallingIdentity();
            return hasPackage(identity, expectedPackage)
                    && !hasPackage(identity, spoofedPackage);
        }

        @Override
        public boolean verifyCallingSigner(
                String expectedPackage,
                String expectedSignerSha256,
                String spoofedSignerSha256) {
            CallerIdentitySnapshot identity = identityResolver.resolveCallingIdentity();
            return hasSigner(identity, expectedPackage, expectedSignerSha256)
                    && !hasSigner(identity, expectedPackage, spoofedSignerSha256);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        identityResolver = new AndroidCallerIdentityResolver(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return probe;
    }

    private static boolean hasPackage(CallerIdentitySnapshot identity, String packageName) {
        if (!identity.isResolved() || packageName == null) {
            return false;
        }
        for (CallerIdentitySnapshot.PackageIdentity packageIdentity : identity.getPackages()) {
            if (packageName.equals(packageIdentity.getPackageName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSigner(
            CallerIdentitySnapshot identity,
            String packageName,
            String signerSha256) {
        if (!identity.isResolved() || packageName == null || signerSha256 == null) {
            return false;
        }
        for (CallerIdentitySnapshot.PackageIdentity packageIdentity : identity.getPackages()) {
            if (packageName.equals(packageIdentity.getPackageName())
                    && packageIdentity.getCurrentSignerSha256().contains(signerSha256)) {
                return true;
            }
        }
        return false;
    }
}
