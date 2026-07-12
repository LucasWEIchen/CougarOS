# Central Brain Android 13 Hybrid C/Java Handoff

This bundle is the B4 application-layer delivery for a black-box Android 13
cockpit controller. It contains the C Native Runtime AAR, Java/AIDL SDK AAR,
Runtime APK, maintenance Demo APK and optional Client2 Binder demo APK.

Use `docs/CENTRAL_BRAIN_ANDROID13_HYBRID_INSTALLATION_AND_USAGE.md` as the
authoritative installation and operation guide. The installer is dry-run by
default and checks API, 64-bit ABI, artifact identity and every selected existing
package signer before the first install.

The debug bundle is for controlled integration only. It does not modify vendor
Android, AOSP, BSP, SELinux or system/vendor partitions. It does not activate a
Vendor NPU, VHAL, vehicle bus or production Effect/Model/Event/Memory/Skill path.

Bundle hashes prove internal consistency, not publisher authenticity. Obtain the
archive SHA-256 through a trusted release channel before unpacking or installing.
