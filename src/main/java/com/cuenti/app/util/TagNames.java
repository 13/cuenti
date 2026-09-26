package com.cuenti.app.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tags are stored as a comma-separated string. These helpers trim names and
 * drop case-insensitive duplicates (first spelling wins) so a tag never shows
 * up twice on one transaction.
 */
public final class TagNames {

    private TagNames() {
    }

    /** Trimmed, de-duplicated tag names in their original order. */
    public static List<String> parse(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return distinct(List.of(tags.split(",")));
    }

    /** Joins names into the stored form; {@code null} when nothing is left. */
    public static String join(Collection<String> names) {
        List<String> clean = distinct(names);
        return clean.isEmpty() ? null : String.join(",", clean);
    }

    /** Stored form of {@code tags} with blanks and duplicates removed. */
    public static String normalize(String tags) {
        return join(parse(tags));
    }

    /** True when both names denote the same tag. */
    public static boolean same(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
    }

    private static List<String> distinct(Collection<String> names) {
        Map<String, String> byLower = new LinkedHashMap<>();
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            String trimmed = name.trim();
            byLower.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
        }
        return new ArrayList<>(byLower.values());
    }
}
