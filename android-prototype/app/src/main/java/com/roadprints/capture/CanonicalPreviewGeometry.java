package com.roadprints.capture;

import android.graphics.Canvas;
import android.graphics.Paint;
import java.util.Arrays;
import java.util.Map;

/**
 * Read-only, non-interactive projection of one canonical section.
 *
 * No journey IDs, contributor history, JSON, Section, or tappable Lines are
 * retained by the preview. Constructed only on the archive worker.
 */
final class CanonicalPreviewGeometry {
    final String id;
    private final double[] xy;

    private CanonicalPreviewGeometry(String id, double[] xy) {
        this.id = id;
        this.xy = xy;
    }

    int lineCount() { return xy.length / 4; }

    static CanonicalPreviewGeometry from(LocalRoadNetwork.Edge edge,
            Map<String, int[]> contributors) {
        int count = 0;
        for (int[] spans : contributors.values()) count += spans.length / 2;
        if (count == 0) return new CanonicalPreviewGeometry(edge.id(), new double[0]);

        long[] packed = new long[count];
        int cursor = 0;
        for (int[] spans : contributors.values())
            for (int i = 0; i < spans.length; i += 2)
                packed[cursor++] = ((long) spans[i] << 32) | (spans[i + 1] & 0xffffffffL);
        Arrays.sort(packed);

        // Same exact endpoint-union semantics as LocalRoadCoverage.Section:
        // adjacent and overlapping spans become one physical interval.
        int unions = 0, start = -1, end = -1;
        for (long interval : packed) {
            int a = (int) (interval >>> 32), b = (int) interval;
            if (start >= 0 && a > end) { unions++; start = -1; }
            if (start < 0) { start = a; end = b; }
            else end = Math.max(end, b);
        }
        if (start >= 0) unions++;
        double[] projected = new double[unions * 4];
        double lonA = LocalRoadNetwork.ukLongitude(edge.a);
        double latA = LocalRoadNetwork.ukLatitude(edge.a);
        double lonB = LocalRoadNetwork.ukLongitude(edge.b);
        double latB = LocalRoadNetwork.ukLatitude(edge.b);
        cursor = 0; start = -1; end = -1;
        for (long interval : packed) {
            int a = (int) (interval >>> 32), b = (int) interval;
            if (start >= 0 && a > end) {
                cursor = writeLine(projected, cursor, start, end, edge.mm,
                        lonA, latA, lonB, latB);
                start = -1;
            }
            if (start < 0) { start = a; end = b; }
            else end = Math.max(end, b);
        }
        if (start >= 0)
            writeLine(projected, cursor, start, end, edge.mm, lonA, latA, lonB, latB);
        return new CanonicalPreviewGeometry(edge.id(), projected);
    }

    private static int writeLine(double[] target, int at, int start, int end, int mm,
            double lonA, double latA, double lonB, double latB) {
        // The packed reference uses millimetre-quantised canonical intervals.
        double from = mm == 0 ? 0 : start / (double) mm;
        double to = mm == 0 ? 0 : end / (double) mm;
        double firstLon = lonA + (lonB - lonA) * from;
        double firstLat = latA + (latB - latA) * from;
        double lastLon = lonA + (lonB - lonA) * to;
        double lastLat = latA + (latB - latA) * to;
        target[at++] = (firstLon + 180.0) / 360.0;
        target[at++] = .5 - MapRenderGeometry.y(firstLat) / (2 * Math.PI * 6378137);
        target[at++] = (lastLon + 180.0) / 360.0;
        target[at++] = .5 - MapRenderGeometry.y(lastLat) / (2 * Math.PI * 6378137);
        return at;
    }

    /** Draw in the same world-unit coordinate system as LocalRoadLayer. */
    void draw(Canvas canvas, Paint paint, double xOffset, double yOffset,
              double scale, int width, int height, float[] batch) {
        double padding = paint.getStrokeWidth() + 2;
        int count = 0;
        for (int i = 0; i < xy.length; i += 4) {
            double x1 = xOffset + xy[i] * scale, y1 = yOffset + xy[i + 1] * scale;
            double x2 = xOffset + xy[i + 2] * scale, y2 = yOffset + xy[i + 3] * scale;
            if (Math.max(x1, x2) < -padding || Math.min(x1, x2) > width + padding
                    || Math.max(y1, y2) < -padding || Math.min(y1, y2) > height + padding) continue;
            batch[count++] = (float) x1;
            batch[count++] = (float) y1;
            batch[count++] = (float) x2;
            batch[count++] = (float) y2;
            if (count == batch.length) {
                canvas.drawLines(batch, 0, count, paint);
                count = 0;
            }
        }
        if (count != 0) canvas.drawLines(batch, 0, count, paint);
    }

    // Allows exact geometry comparison in regression tests without rendering.
    double[] projectionForTest() { return xy.clone(); }
}
