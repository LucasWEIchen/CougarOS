package com.centralbrain.client2;

/** Immutable allowlisted display policy for the maintained cockpit overlay. */
public final class CockpitDisplayPolicy {
    public static final int MIN_TOUCH_TARGET_DP = 48;
    public static final float MAX_FONT_SCALE = 1.30f;

    public enum Profile {
        COMPACT_1280_720(1280, 720, 107),
        STANDARD_1920_1080(1920, 1080, 160),
        LARGE_2560_1440(2560, 1440, 213),
        UNSUPPORTED(0, 0, 0);

        private final int widthPixels;
        private final int heightPixels;
        private final int densityDpi;

        Profile(int widthPixels, int heightPixels, int densityDpi) {
            this.widthPixels = widthPixels;
            this.heightPixels = heightPixels;
            this.densityDpi = densityDpi;
        }
    }

    public static final class Bounds {
        private final int left;
        private final int top;
        private final int right;
        private final int bottom;

        private Bounds(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        public int getLeft() {
            return left;
        }

        public int getTop() {
            return top;
        }

        public int getRight() {
            return right;
        }

        public int getBottom() {
            return bottom;
        }

        public int getWidth() {
            return right - left;
        }

        public int getHeight() {
            return bottom - top;
        }
    }

    private final Profile profile;
    private final int widthPixels;
    private final int heightPixels;
    private final int densityDpi;
    private final float fontScale;
    private final String rejectionCode;

    private CockpitDisplayPolicy(
            Profile profile,
            int widthPixels,
            int heightPixels,
            int densityDpi,
            float fontScale,
            String rejectionCode) {
        this.profile = profile;
        this.widthPixels = Math.max(0, widthPixels);
        this.heightPixels = Math.max(0, heightPixels);
        this.densityDpi = Math.max(0, densityDpi);
        this.fontScale = fontScale;
        this.rejectionCode = rejectionCode;
    }

    public static CockpitDisplayPolicy resolve(
            int widthPixels,
            int heightPixels,
            int densityDpi,
            float fontScale) {
        if (widthPixels <= 0 || heightPixels <= 0 || densityDpi <= 0) {
            return unsupported(widthPixels, heightPixels, densityDpi, fontScale,
                    "INVALID_METRICS");
        }
        if (Float.isNaN(fontScale) || Float.isInfinite(fontScale)
                || fontScale < 0.85f || fontScale > MAX_FONT_SCALE + 0.0001f) {
            return unsupported(widthPixels, heightPixels, densityDpi, fontScale,
                    "UNSUPPORTED_FONT_SCALE");
        }
        if (widthPixels <= heightPixels) {
            return unsupported(widthPixels, heightPixels, densityDpi, fontScale,
                    "LANDSCAPE_REQUIRED");
        }
        for (Profile candidate : Profile.values()) {
            if (candidate == Profile.UNSUPPORTED) {
                continue;
            }
            if (candidate.widthPixels == widthPixels
                    && candidate.heightPixels == heightPixels
                    && candidate.densityDpi == densityDpi) {
                return new CockpitDisplayPolicy(
                        candidate,
                        widthPixels,
                        heightPixels,
                        densityDpi,
                        fontScale,
                        "");
            }
        }
        return unsupported(widthPixels, heightPixels, densityDpi, fontScale,
                "DISPLAY_MATRIX_MISMATCH");
    }

    public boolean isSupported() {
        return profile != Profile.UNSUPPORTED;
    }

    public Profile getProfile() {
        return profile;
    }

    public String getRejectionCode() {
        return rejectionCode;
    }

    public float getFontScale() {
        return fontScale;
    }

    public int getWidthPixels() {
        return widthPixels;
    }

    public boolean isLargeTextProfile() {
        return isSupported() && fontScale > 1.0f;
    }

    public int getMinimumTouchTargetPixels() {
        return (int) Math.ceil(MIN_TOUCH_TARGET_DP * densityDpi / 160.0d);
    }

    public Bounds getPanelBoundsPixels() {
        if (!isSupported()) {
            return new Bounds(0, 0, 0, 0);
        }
        int panelWidth = dpToPixels(624);
        int panelHeight = dpToPixels(888);
        int topMargin = dpToPixels(160);
        int rightMargin = dpToPixels(32);
        return new Bounds(
                widthPixels - rightMargin - panelWidth,
                topMargin,
                widthPixels - rightMargin,
                topMargin + panelHeight);
    }

    public boolean panelFitsDisplay() {
        Bounds bounds = getPanelBoundsPixels();
        return isSupported()
                && bounds.left >= 0
                && bounds.top >= 0
                && bounds.right <= widthPixels
                && bounds.bottom <= heightPixels;
    }

    public boolean isEffectAuthorizationSource() {
        return false;
    }

    private int dpToPixels(int dp) {
        return Math.round(dp * densityDpi / 160.0f);
    }

    private static CockpitDisplayPolicy unsupported(
            int widthPixels,
            int heightPixels,
            int densityDpi,
            float fontScale,
            String rejectionCode) {
        return new CockpitDisplayPolicy(
                Profile.UNSUPPORTED,
                widthPixels,
                heightPixels,
                densityDpi,
                fontScale,
                rejectionCode);
    }
}
