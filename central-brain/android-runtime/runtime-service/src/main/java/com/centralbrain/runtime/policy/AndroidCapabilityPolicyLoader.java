package com.centralbrain.runtime.policy;

import android.content.Context;
import android.content.res.XmlResourceParser;

import com.centralbrain.runtime.identity.CallerIdentitySnapshot;
import com.centralbrain.runtime.policy.CallerCapabilityPolicy.Capability;
import com.centralbrain.runtime.policy.CallerCapabilityPolicy.PrincipalRule;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Strict parser for the APK-owned capability policy resource. */
public final class AndroidCapabilityPolicyLoader {
    private static final String ROOT = "capability-policy";
    private static final String PRINCIPAL = "principal";
    private static final String CAPABILITY = "capability";
    private static final String RUNTIME_CURRENT_SIGNER = "runtime-current";

    private AndroidCapabilityPolicyLoader() {
    }

    public static CallerCapabilityPolicy load(
            Context context,
            int resourceId,
            CallerIdentitySnapshot runtimeIdentity) {
        CallerIdentitySnapshot.PackageIdentity runtimePackage = findRuntimePackage(
                context.getPackageName(),
                runtimeIdentity);
        List<PrincipalRule> rules = new ArrayList<>();
        Set<String> configuredPackages = new HashSet<>();

        try (XmlResourceParser parser = context.getResources().getXml(resourceId)) {
            moveToRoot(parser);
            requireElement(parser, ROOT, 1);
            requireAttributes(parser, "version", "defaultDecision");
            if (!"1".equals(parser.getAttributeValue(null, "version"))) {
                throw invalid("capability policy version must be 1");
            }
            if (!"deny".equals(parser.getAttributeValue(null, "defaultDecision"))) {
                throw invalid("capability policy defaultDecision must be deny");
            }

            int rootDepth = parser.getDepth();
            while (true) {
                int event = parser.next();
                if (event == XmlPullParser.END_DOCUMENT) {
                    throw invalid("capability policy root was not closed");
                }
                if (event == XmlPullParser.END_TAG && parser.getDepth() == rootDepth) {
                    break;
                }
                if (event == XmlPullParser.TEXT && parser.isWhitespace()) {
                    continue;
                }
                if (event != XmlPullParser.START_TAG) {
                    throw invalid("unexpected content under capability policy root");
                }
                requireElement(parser, PRINCIPAL, rootDepth + 1);
                PrincipalRule rule = parsePrincipal(
                        parser,
                        runtimePackage.getCurrentSignerSha256());
                if (!configuredPackages.add(rule.getPackageName())) {
                    throw invalid("duplicate principal package: " + rule.getPackageName());
                }
                rules.add(rule);
            }
        } catch (IOException | XmlPullParserException exception) {
            throw new IllegalStateException("cannot parse capability policy", exception);
        }
        return new CallerCapabilityPolicy(rules);
    }

    private static PrincipalRule parsePrincipal(
            XmlResourceParser parser,
            List<String> runtimeCurrentSigners)
            throws IOException, XmlPullParserException {
        requireAttributes(parser, "packageName", "signer");
        String packageName = parser.getAttributeValue(null, "packageName");
        if (!RUNTIME_CURRENT_SIGNER.equals(parser.getAttributeValue(null, "signer"))) {
            throw invalid("principal signer must be runtime-current");
        }

        EnumSet<Capability> capabilities = EnumSet.noneOf(Capability.class);
        int principalDepth = parser.getDepth();
        while (true) {
            int event = parser.next();
            if (event == XmlPullParser.END_DOCUMENT) {
                throw invalid("principal element was not closed");
            }
            if (event == XmlPullParser.END_TAG && parser.getDepth() == principalDepth) {
                break;
            }
            if (event == XmlPullParser.TEXT && parser.isWhitespace()) {
                continue;
            }
            if (event != XmlPullParser.START_TAG) {
                throw invalid("unexpected content under principal");
            }
            requireElement(parser, CAPABILITY, principalDepth + 1);
            requireAttributes(parser, "name");
            Capability capability;
            try {
                capability = Capability.fromId(parser.getAttributeValue(null, "name"));
            } catch (IllegalArgumentException exception) {
                throw invalid(exception.getMessage());
            }
            if (!capabilities.add(capability)) {
                throw invalid("duplicate capability: " + capability.getId());
            }
            consumeEmptyElement(parser, principalDepth + 1);
        }
        return new PrincipalRule(packageName, runtimeCurrentSigners, capabilities);
    }

    private static CallerIdentitySnapshot.PackageIdentity findRuntimePackage(
            String runtimePackageName,
            CallerIdentitySnapshot runtimeIdentity) {
        if (runtimeIdentity == null || !runtimeIdentity.isResolved()) {
            throw new IllegalStateException("Runtime signing identity is unresolved");
        }
        for (CallerIdentitySnapshot.PackageIdentity packageIdentity
                : runtimeIdentity.getPackages()) {
            if (runtimePackageName.equals(packageIdentity.getPackageName())) {
                return packageIdentity;
            }
        }
        throw new IllegalStateException("Runtime package is missing from its UID identity");
    }

    private static void moveToRoot(XmlResourceParser parser)
            throws IOException, XmlPullParserException {
        while (parser.getEventType() != XmlPullParser.START_TAG) {
            if (parser.next() == XmlPullParser.END_DOCUMENT) {
                throw invalid("capability policy is empty");
            }
        }
    }

    private static void consumeEmptyElement(XmlResourceParser parser, int depth)
            throws IOException, XmlPullParserException {
        while (true) {
            int event = parser.next();
            if (event == XmlPullParser.END_TAG && parser.getDepth() == depth) {
                return;
            }
            if (event == XmlPullParser.TEXT && parser.isWhitespace()) {
                continue;
            }
            throw invalid("capability entries must be empty elements");
        }
    }

    private static void requireElement(
            XmlResourceParser parser,
            String expectedName,
            int expectedDepth) {
        if (!expectedName.equals(parser.getName()) || parser.getDepth() != expectedDepth) {
            throw invalid("expected " + expectedName + " at depth " + expectedDepth);
        }
    }

    private static void requireAttributes(XmlResourceParser parser, String... allowedNames) {
        Set<String> allowed = new HashSet<>();
        for (String name : allowedNames) {
            allowed.add(name);
        }
        if (parser.getAttributeCount() != allowed.size()) {
            throw invalid("unexpected or missing attributes on " + parser.getName());
        }
        for (int index = 0; index < parser.getAttributeCount(); index++) {
            String namespace = parser.getAttributeNamespace(index);
            String name = parser.getAttributeName(index);
            if ((namespace != null && !namespace.isEmpty()) || !allowed.contains(name)) {
                throw invalid("unexpected attribute on " + parser.getName() + ": " + name);
            }
        }
        for (String name : allowed) {
            String value = parser.getAttributeValue(null, name);
            if (value == null || value.trim().isEmpty()) {
                throw invalid("missing attribute on " + parser.getName() + ": " + name);
            }
        }
    }

    private static IllegalStateException invalid(String message) {
        return new IllegalStateException("invalid capability policy: " + message);
    }
}
