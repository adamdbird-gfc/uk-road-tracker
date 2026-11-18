package com.roadprints.capture;

import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.List;

/** One compact projected line array, prepared off the UI thread for drawing and hit tests. */
final class LocalRoadLayer {
    record Hit(LocalRoadCoverage.Line line, double fraction, double distance, double x, double y) {}

    private final List<LocalRoadCoverage.Line> lines;
    private final double[] coordinates;
    final double west, south, east, north;

    LocalRoadLayer(List<LocalRoadCoverage.Line> lines) {
        this.lines = lines;
        coordinates = new double[lines.size() * 4];
        double minLon = Double.POSITIVE_INFINITY, minLat = Double.POSITIVE_INFINITY;
        double maxLon = Double.NEGATIVE_INFINITY, maxLat = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < lines.size(); i++) {
            for (int endpoint = 0; endpoint < 2; endpoint++) {
                LocalRoadCoverage.Line line = lines.get(i);
                double lon = line.longitude(endpoint == 1), lat = line.latitude(endpoint == 1);
                minLon = Math.min(minLon, lon);
                minLat = Math.min(minLat, lat);
                maxLon = Math.max(maxLon, lon);
                maxLat = Math.max(maxLat, lat);
                int offset = i * 4 + endpoint * 2;
                coordinates[offset] = (lon + 180) / 360;
                coordinates[offset + 1] = .5 - MapRenderGeometry.y(lat) / (2 * Math.PI * 6378137);
            }
        }
        west = minLon;
        south = minLat;
        east = maxLon;
        north = maxLat;
    }

    boolean isEmpty() { return lines.isEmpty(); }

    void draw(Canvas canvas, Paint paint, double xOffset, double yOffset, double scale,
              int width, int height, float[] batch) {
        double padding = paint.getStrokeWidth() + 2;
        int count = 0;
        for (int i = 0; i < coordinates.length; i += 4) {
            double x1 = xOffset + coordinates[i] * scale, y1 = yOffset + coordinates[i + 1] * scale;
            double x2 = xOffset + coordinates[i + 2] * scale, y2 = yOffset + coordinates[i + 3] * scale;
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

    Hit nearest(double x, double y, double tolerance) {
        Hit hit = null;
        for (int i = 0; i < coordinates.length; i += 4) {
            double ax = coordinates[i], ay = coordinates[i + 1], bx = coordinates[i + 2], by = coordinates[i + 3];
            if (x < Math.min(ax, bx) - tolerance || x > Math.max(ax, bx) + tolerance
                    || y < Math.min(ay, by) - tolerance || y > Math.max(ay, by) + tolerance) continue;
            double dx = bx - ax, dy = by - ay;
            double squared = dx * dx + dy * dy;
            double fraction = squared == 0 ? 0 : Math.max(0, Math.min(1, ((x - ax) * dx + (y - ay) * dy) / squared));
            double px = ax + fraction * dx, py = ay + fraction * dy;
            double distance = Math.hypot(x - px, y - py);
            if (distance <= tolerance && (hit == null || distance < hit.distance))
                hit = new Hit(lines.get(i / 4), fraction, distance, px, py);
        }
        return hit;
    }
}
