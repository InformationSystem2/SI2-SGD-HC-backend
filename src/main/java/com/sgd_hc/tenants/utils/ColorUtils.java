package com.sgd_hc.tenants.utils;

public final class ColorUtils {

    private ColorUtils() {}

    public static String normalizeHex(String hex) {
        if (hex == null) return "#000000";
        String h = hex.replace("#", "");
        if (h.length() == 3) {
            h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
        }
        return h.length() == 6 ? "#" + h : hex;
    }

    public static int[] hexToRgb(String hex) {
        String h = normalizeHex(hex).replace("#", "");
        if (h.length() != 6) {
            return new int[]{0, 0, 0};
        }
        return new int[]{
                Integer.parseInt(h.substring(0, 2), 16),
                Integer.parseInt(h.substring(2, 4), 16),
                Integer.parseInt(h.substring(4, 6), 16)
        };
    }

    public static String rgbToHex(int r, int g, int b) {
        return String.format("#%02x%02x%02x",
                Math.max(0, Math.min(255, r)),
                Math.max(0, Math.min(255, g)),
                Math.max(0, Math.min(255, b)));
    }

    public static String darkenHex(String hex, double factor) {
        int[] rgb = hexToRgb(hex);
        int r = (int) Math.max(0, rgb[0] * (1 - factor));
        int g = (int) Math.max(0, rgb[1] * (1 - factor));
        int b = (int) Math.max(0, rgb[2] * (1 - factor));
        return rgbToHex(r, g, b);
    }

    public static double getLuminance(int r, int g, int b) {
        return (0.299 * r + 0.587 * g + 0.114 * b) / 255;
    }

    public static String determineTextColor(String hex) {
        int[] rgb = hexToRgb(hex);
        double luminance = getLuminance(rgb[0], rgb[1], rgb[2]);
        return luminance > 0.5 ? "#000000" : "#FFFFFF";
    }
}