package com.cuenti.homebanking.views.components;

import com.vaadin.flow.component.html.Span;

/**
 * Deterministic tag color utility based on tag letters.
 */
public final class TagColorUtil {

    private TagColorUtil() {
    }

    public static Span createTagBadge(String rawTag) {
        String tag = rawTag == null ? "" : rawTag.trim();
        Span badge = new Span(tag);
        badge.addClassName("tag-badge");

        String bg = lettersToHsl(tag);
        String fg = textColor(bg);

        badge.getStyle()
                .set("--tag-bg", bg)
                .set("--tag-fg", fg);
        return badge;
    }

    private static String lettersToHsl(String tag) {
        String normalized = tag == null ? "" : tag.toLowerCase();

        int hueSeed = 0;
        int satSeed = 0;
        int lightSeed = 0;
        int letters = 0;

        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (ch >= 'a' && ch <= 'z') {
                int value = ch - 'a' + 1;
                int position = letters + 1;
                hueSeed += value * position;
                satSeed += value * (position + 3);
                lightSeed += value * (position + 7);
                letters++;
            }
        }

        if (letters == 0) {
            return hslToHex(220, 65, 45);
        }

        int hue = Math.floorMod(hueSeed * 37, 360);
        int saturation = 62 + Math.floorMod(satSeed, 19); // 62..80
        int lightness = 40 + Math.floorMod(lightSeed, 13); // 40..52
        return hslToHex(hue, saturation, lightness);
    }

    private static String textColor(String backgroundHex) {
        int r = Integer.parseInt(backgroundHex.substring(1, 3), 16);
        int g = Integer.parseInt(backgroundHex.substring(3, 5), 16);
        int b = Integer.parseInt(backgroundHex.substring(5, 7), 16);

        double luminance = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0;
        return luminance > 0.6 ? "#111111" : "#FFFFFF";
    }

    private static String hslToHex(int h, int s, int l) {
        double hue = h / 360.0;
        double sat = s / 100.0;
        double lig = l / 100.0;

        double r;
        double g;
        double b;

        if (sat == 0) {
            r = lig;
            g = lig;
            b = lig;
        } else {
            double q = lig < 0.5 ? lig * (1 + sat) : lig + sat - lig * sat;
            double p = 2 * lig - q;
            r = hueToRgb(p, q, hue + 1.0 / 3.0);
            g = hueToRgb(p, q, hue);
            b = hueToRgb(p, q, hue - 1.0 / 3.0);
        }

        return String.format("#%02X%02X%02X",
                (int) Math.round(r * 255),
                (int) Math.round(g * 255),
                (int) Math.round(b * 255));
    }

    private static double hueToRgb(double p, double q, double t) {
        if (t < 0) {
            t += 1;
        }
        if (t > 1) {
            t -= 1;
        }
        if (t < 1.0 / 6.0) {
            return p + (q - p) * 6 * t;
        }
        if (t < 1.0 / 2.0) {
            return q;
        }
        if (t < 2.0 / 3.0) {
            return p + (q - p) * (2.0 / 3.0 - t) * 6;
        }
        return p;
    }
}

