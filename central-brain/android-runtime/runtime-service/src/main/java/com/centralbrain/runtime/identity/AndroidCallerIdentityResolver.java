package com.centralbrain.runtime.identity;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Binder;
import android.os.UserHandle;
import android.os.UserManager;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Resolves trusted caller evidence without accepting identity fields from a request. */
public final class AndroidCallerIdentityResolver {
    private final PackageManager packageManager;
    private final UserManager userManager;

    public AndroidCallerIdentityResolver(Context context) {
        Context applicationContext = context.getApplicationContext();
        packageManager = applicationContext.getPackageManager();
        userManager = applicationContext.getSystemService(UserManager.class);
    }

    public CallerIdentitySnapshot resolveCallingIdentity() {
        return resolveUid(Binder.getCallingUid());
    }

    CallerIdentitySnapshot resolveUid(int uid) {
        long userSerial = userManager == null
                ? -1
                : userManager.getSerialNumberForUser(UserHandle.getUserHandleForUid(uid));
        if (userSerial < 0) {
            return CallerIdentitySnapshot.unresolved(uid, userSerial, "Android user is unknown");
        }
        String[] packageNames = packageManager.getPackagesForUid(uid);
        if (packageNames == null || packageNames.length == 0) {
            return CallerIdentitySnapshot.unresolved(uid, userSerial, "uid has no visible packages");
        }

        List<String> sortedPackageNames = new ArrayList<>(Arrays.asList(packageNames));
        Collections.sort(sortedPackageNames);
        List<CallerIdentitySnapshot.PackageIdentity> packages = new ArrayList<>();
        try {
            for (String packageName : sortedPackageNames) {
                PackageInfo packageInfo = packageManager.getPackageInfo(
                        packageName,
                        PackageManager.GET_SIGNING_CERTIFICATES);
                SigningInfo signingInfo = packageInfo.signingInfo;
                Signature[] currentSigners = signingInfo == null
                        ? null
                        : signingInfo.getApkContentsSigners();
                if (currentSigners == null || currentSigners.length == 0) {
                    return CallerIdentitySnapshot.unresolved(
                            uid,
                            userSerial,
                            "package has no current signer: " + packageName);
                }
                List<String> signerDigests = new ArrayList<>();
                for (Signature signature : currentSigners) {
                    signerDigests.add(sha256(signature.toByteArray()));
                }
                packages.add(new CallerIdentitySnapshot.PackageIdentity(
                        packageName,
                        signerDigests));
            }
        } catch (PackageManager.NameNotFoundException | RuntimeException exception) {
            return CallerIdentitySnapshot.unresolved(
                    uid,
                    userSerial,
                    "package evidence lookup failed: " + exception.getClass().getSimpleName());
        }
        return CallerIdentitySnapshot.resolved(uid, userSerial, packages);
    }

    private static String sha256(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder encoded = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                encoded.append(String.format("%02x", item & 0xff));
            }
            return encoded.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
