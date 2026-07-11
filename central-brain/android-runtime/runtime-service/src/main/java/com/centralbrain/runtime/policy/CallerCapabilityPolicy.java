package com.centralbrain.runtime.policy;

import com.centralbrain.runtime.identity.CallerIdentitySnapshot;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Default-deny package and current-signer capability policy. */
public final class CallerCapabilityPolicy {
    public enum Capability {
        PROTOCOL_READ("runtime.protocol.read"),
        TASK_SUBMIT("runtime.task.submit"),
        TASK_STATUS_OWN("runtime.task.status.own"),
        TASK_CANCEL_OWN("runtime.task.cancel.own"),
        GOVERNANCE_PROTOCOL_READ("governance.protocol.read"),
        ACTION_EVALUATE("governance.action.evaluate"),
        APPROVAL_REQUEST("governance.approval.request"),
        APPROVAL_STATUS_OWN("governance.approval.status.own"),
        APPROVAL_CANCEL_OWN("governance.approval.cancel.own"),
        DIAGNOSTICS_READ("runtime.diagnostics.read");

        private final String id;

        Capability(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public static Capability fromId(String id) {
            for (Capability capability : values()) {
                if (capability.id.equals(id)) {
                    return capability;
                }
            }
            throw new IllegalArgumentException("unknown capability: " + id);
        }
    }

    public enum DecisionReason {
        ALLOWED,
        IDENTITY_UNRESOLVED,
        PACKAGE_NOT_CONFIGURED,
        CURRENT_SIGNER_MISMATCH,
        CAPABILITY_NOT_GRANTED
    }

    private final Map<String, PrincipalRule> rulesByPackage;

    public CallerCapabilityPolicy(List<PrincipalRule> rules) {
        Map<String, PrincipalRule> indexed = new LinkedHashMap<>();
        for (PrincipalRule rule : Objects.requireNonNull(rules, "rules")) {
            PrincipalRule requiredRule = Objects.requireNonNull(rule, "rule");
            PrincipalRule previous = indexed.put(
                    requiredRule.getPackageName(),
                    requiredRule);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "duplicate capability principal: " + requiredRule.getPackageName());
            }
        }
        rulesByPackage = Collections.unmodifiableMap(indexed);
    }

    public Decision evaluate(
            CallerIdentitySnapshot caller,
            Capability capability) {
        Objects.requireNonNull(capability, "capability");
        if (caller == null || !caller.isResolved()) {
            return Decision.denied(capability, DecisionReason.IDENTITY_UNRESOLVED, "");
        }

        boolean configuredPackageFound = false;
        String capabilityPackage = "";
        for (CallerIdentitySnapshot.PackageIdentity packageIdentity : caller.getPackages()) {
            PrincipalRule rule = rulesByPackage.get(packageIdentity.getPackageName());
            if (rule == null) {
                continue;
            }
            configuredPackageFound = true;
            if (!rule.requiredCurrentSignerSha256.equals(
                    packageIdentity.getCurrentSignerSha256())) {
                return Decision.denied(
                        capability,
                        DecisionReason.CURRENT_SIGNER_MISMATCH,
                        packageIdentity.getPackageName());
            }
            if (rule.capabilities.contains(capability)) {
                capabilityPackage = packageIdentity.getPackageName();
            }
        }
        if (!capabilityPackage.isEmpty()) {
            return Decision.allowed(capability, capabilityPackage);
        }
        if (configuredPackageFound) {
            return Decision.denied(capability, DecisionReason.CAPABILITY_NOT_GRANTED, "");
        }
        return Decision.denied(capability, DecisionReason.PACKAGE_NOT_CONFIGURED, "");
    }

    public int getRuleCount() {
        return rulesByPackage.size();
    }

    public static final class PrincipalRule {
        private final String packageName;
        private final List<String> requiredCurrentSignerSha256;
        private final Set<Capability> capabilities;

        public PrincipalRule(
                String packageName,
                List<String> requiredCurrentSignerSha256,
                Set<Capability> capabilities) {
            CallerIdentitySnapshot.PackageIdentity signerEvidence =
                    new CallerIdentitySnapshot.PackageIdentity(
                            validatePackageName(packageName),
                            requiredCurrentSignerSha256);
            this.packageName = signerEvidence.getPackageName();
            this.requiredCurrentSignerSha256 = signerEvidence.getCurrentSignerSha256();
            if (capabilities == null || capabilities.isEmpty()) {
                throw new IllegalArgumentException("principal must grant at least one capability");
            }
            this.capabilities = Collections.unmodifiableSet(EnumSet.copyOf(capabilities));
        }

        public String getPackageName() {
            return packageName;
        }

        public List<String> getRequiredCurrentSignerSha256() {
            return requiredCurrentSignerSha256;
        }

        public Set<Capability> getCapabilities() {
            return capabilities;
        }

        private static String validatePackageName(String packageName) {
            if (packageName == null
                    || packageName.trim().isEmpty()
                    || !packageName.matches(
                            "[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")) {
                throw new IllegalArgumentException("literal package name is required");
            }
            return packageName;
        }
    }

    public static final class Decision {
        private final boolean allowed;
        private final Capability capability;
        private final DecisionReason reason;
        private final String matchedPackage;

        private Decision(
                boolean allowed,
                Capability capability,
                DecisionReason reason,
                String matchedPackage) {
            this.allowed = allowed;
            this.capability = capability;
            this.reason = reason;
            this.matchedPackage = matchedPackage;
        }

        static Decision allowed(Capability capability, String matchedPackage) {
            return new Decision(true, capability, DecisionReason.ALLOWED, matchedPackage);
        }

        static Decision denied(
                Capability capability,
                DecisionReason reason,
                String matchedPackage) {
            return new Decision(false, capability, reason, matchedPackage);
        }

        public boolean isAllowed() {
            return allowed;
        }

        public Capability getCapability() {
            return capability;
        }

        public DecisionReason getReason() {
            return reason;
        }

        public String getMatchedPackage() {
            return matchedPackage;
        }
    }
}
