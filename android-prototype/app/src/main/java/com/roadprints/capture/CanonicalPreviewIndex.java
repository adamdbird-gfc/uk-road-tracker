package com.roadprints.capture;

import java.util.LinkedHashMap;
import java.util.Collection;

/**
 * UI-owned, bounded preview index. Upserting an OSM section ID replaces its
 * earlier geometry instead of appending a second overlapping road.
 * Preview entries are disposable and NEVER authoritative journey evidence.
 */
final class CanonicalPreviewIndex<T> {
    private final LinkedHashMap<String, Entry<T>> entries = new LinkedHashMap<>();
    private final int maxSections, maxLines;
    private int lines;

    private record Entry<T>(T layer, int lines) {}

    CanonicalPreviewIndex(int maxSections, int maxLines) {
        if (maxSections <= 0 || maxLines <= 0)
            throw new IllegalArgumentException("Invalid preview budget");
        this.maxSections = maxSections;
        this.maxLines = maxLines;
    }

    boolean replace(String canonicalId, T layer, int lineCount) {
        if (canonicalId == null || canonicalId.isEmpty() || layer == null
                || lineCount < 0) return false;
        Entry<T> previous = entries.get(canonicalId);
        int next = lines - (previous == null ? 0 : previous.lines()) + lineCount;
        if (next > maxLines || (previous == null && entries.size() >= maxSections))
            return false;
        if (lineCount == 0) entries.remove(canonicalId);
        else entries.put(canonicalId, new Entry<>(layer, lineCount));
        lines = next;
        return true;
    }

    Collection<T> layers() {
        java.util.List<T> values = new java.util.ArrayList<>(entries.size());
        for (Entry<T> entry : entries.values()) values.add(entry.layer());
        return values;
    }

    int sectionCount() { return entries.size(); }
    int lineCount() { return lines; }
    void clear() { entries.clear(); lines = 0; }
}
