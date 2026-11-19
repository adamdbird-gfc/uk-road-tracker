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
    private final int maxSections, maxLines, maxEstimatedBytes;
    private int lines, estimatedBytes, rejectedUpdates;
    private Collection<T> drawingSnapshot = java.util.Collections.emptyList();
    private boolean drawingDirty = true;

    private record Entry<T>(T layer, int lines, int estimatedBytes) {}

    CanonicalPreviewIndex(int maxSections, int maxLines) {
        this(maxSections, maxLines, Integer.MAX_VALUE);
    }

    CanonicalPreviewIndex(int maxSections, int maxLines, int maxEstimatedBytes) {
        if (maxSections <= 0 || maxLines <= 0 || maxEstimatedBytes <= 0)
            throw new IllegalArgumentException("Invalid preview budget");
        this.maxSections = maxSections;
        this.maxLines = maxLines;
        this.maxEstimatedBytes = maxEstimatedBytes;
    }

    // Conservative budget for canonical ID, index entry, geometry wrapper,
    // world-unit coordinate array and object/header bookkeeping.
    private static int estimateBytes(String id, int count) {
        return 160 + 2 * id.length() + 32 * count;
    }

    boolean replace(String canonicalId, T layer, int lineCount) {
        if (canonicalId == null || canonicalId.isEmpty() || layer == null
                || lineCount < 0) return false;
        Entry<T> previous = entries.get(canonicalId);
        int next = lines - (previous == null ? 0 : previous.lines()) + lineCount;
        int candidateBytes = lineCount == 0 ? 0 : estimateBytes(canonicalId, lineCount);
        long nextBytes = (long) estimatedBytes
                - (previous == null ? 0 : previous.estimatedBytes()) + candidateBytes;
        if (next > maxLines || nextBytes > maxEstimatedBytes
                || (previous == null && lineCount > 0 && entries.size() >= maxSections)) {
            rejectedUpdates++;
            return false;
        }
        if (lineCount == 0) entries.remove(canonicalId);
        else entries.put(canonicalId, new Entry<>(layer, lineCount, candidateBytes));
        lines = next;
        estimatedBytes = (int) nextBytes;
        drawingDirty = true;
        return true;
    }

    Collection<T> layers() {
        if (drawingDirty) {
            java.util.List<T> values = new java.util.ArrayList<>(entries.size());
            for (Entry<T> entry : entries.values()) values.add(entry.layer());
            drawingSnapshot = values;
            drawingDirty = false;
        }
        return drawingSnapshot;
    }

    int sectionCount() { return entries.size(); }
    int lineCount() { return lines; }
    int estimatedBytes() { return estimatedBytes; }
    int rejectedUpdates() { return rejectedUpdates; }
    void clear() {
        entries.clear();
        lines = 0;
        estimatedBytes = 0;
        rejectedUpdates = 0;
        drawingSnapshot = java.util.Collections.emptyList();
        drawingDirty = false;
    }
}
