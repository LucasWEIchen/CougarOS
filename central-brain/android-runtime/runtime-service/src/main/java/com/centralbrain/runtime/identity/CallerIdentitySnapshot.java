package com.centralbrain.runtime.identity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Immutable identity evidence captured from Binder and PackageManager. */
public final class CallerIdentitySnapshot {
    private final int uid;
    private final long androidUserSerial;
    private final List<PackageIdentity> packages;
    private final boolean resolved;
    private final String resolutionFailure;

    private CallerIdentitySnapshot(
            int uid,
            long androidUserSerial,
            List<PackageIdentity> packages,
            boolean resolved,
            String resolutionFailure) {
        this.uid = uid;
        this.androidUserSerial = androidUserSerial;
        this.packages = immutablePackages(packages);
        this.resolved = resolved;
        this.resolutionFailure = safe(resolutionFailure);
        if (resolved && this.packages.isEmpty()) {
            throw new IllegalArgumentException("resolved identity requires package evidence");
        }
    }

    public static CallerIdentitySnapshot resolved(
            int uid,
            long androidUserSerial,
            List<PackageIdentity> packages) {
        if (uid < 0 || androidUserSerial < 0) {
            throw new IllegalArgumentException("uid and Android user must be non-negative");
        }
        return new CallerIdentitySnapshot(uid, androidUserSerial, packages, true, "");
    }

    public static CallerIdentitySnapshot unresolved(
            int uid,
            long androidUserSerial,
            String resolutionFailure) {
        return new CallerIdentitySnapshot(
                uid,
                androidUserSerial,
                Collections.emptyList(),
                false,
                resolutionFailure);
    }

    public int getUid() {
        return uid;
    }

    public long getAndroidUserSerial() {
        return androidUserSerial;
    }

    public List<PackageIdentity> getPackages() {
        return packages;
    }

    public boolean isResolved() {
        return resolved;
    }

    public String getResolutionFailure() {
        return resolutionFailure;
    }

    public boolean samePrincipal(CallerIdentitySnapshot other) {
        return other != null
                && resolved
                && other.resolved
                && uid == other.uid
                && androidUserSerial == other.androidUserSerial
                && packages.equals(other.packages);
    }

    public String auditSummary() {
        List<String> packageNames = new ArrayList<>();
        for (PackageIdentity packageIdentity : packages) {
            packageNames.add(packageIdentity.getPackageName());
        }
        return "uid=" + uid
                + " userSerial=" + androidUserSerial
                + " packages=" + packageNames
                + " resolved=" + resolved;
    }

    private static List<PackageIdentity> immutablePackages(List<PackageIdentity> packages) {
        if (packages == null) {
            return Collections.emptyList();
        }
        List<PackageIdentity> copy = new ArrayList<>(packages);
        copy.sort(Comparator.comparing(PackageIdentity::getPackageName));
        Set<String> names = new LinkedHashSet<>();
        for (PackageIdentity packageIdentity : copy) {
            Objects.requireNonNull(packageIdentity, "package identity");
            if (!names.add(packageIdentity.getPackageName())) {
                throw new IllegalArgumentException(
                        "duplicate package identity: " + packageIdentity.getPackageName());
            }
        }
        return Collections.unmodifiableList(copy);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    /** A package name and its current APK signing certificates. */
    public static final class PackageIdentity {
        private final String packageName;
        private final List<String> currentSignerSha256;

        public PackageIdentity(String packageName, List<String> currentSignerSha256) {
            if (packageName == null || packageName.trim().isEmpty()) {
                throw new IllegalArgumentException("package name is required");
            }
            this.packageName = packageName;
            this.currentSignerSha256 = immutableDigests(currentSignerSha256);
            if (this.currentSignerSha256.isEmpty()) {
                throw new IllegalArgumentException("at least one current signer is required");
            }
        }

        public String getPackageName() {
            return packageName;
        }

        public List<String> getCurrentSignerSha256() {
            return currentSignerSha256;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PackageIdentity)) {
                return false;
            }
            PackageIdentity that = (PackageIdentity) other;
            return packageName.equals(that.packageName)
                    && currentSignerSha256.equals(that.currentSignerSha256);
        }

        @Override
        public int hashCode() {
            return Objects.hash(packageName, currentSignerSha256);
        }

        private static List<String> immutableDigests(List<String> digests) {
            if (digests == null) {
                return Collections.emptyList();
            }
            Set<String> normalized = new LinkedHashSet<>();
            for (String digest : digests) {
                String value = safe(digest).toLowerCase(Locale.ROOT);
                if (!value.matches("[0-9a-f]{64}")) {
                    throw new IllegalArgumentException("invalid SHA-256 signing digest");
                }
                normalized.add(value);
            }
            List<String> copy = new ArrayList<>(normalized);
            Collections.sort(copy);
            return Collections.unmodifiableList(copy);
        }
    }
}
