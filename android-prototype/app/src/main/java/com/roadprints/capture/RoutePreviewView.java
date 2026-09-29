package com.roadprints.capture;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import org.json.JSONArray;

public class RoutePreviewView extends View {
    private final JSONArray coordinates;
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public RoutePreviewView(Context context, JSONArray coordinates) {
        super(context);
        this.coordinates = coordinates;
        linePaint.setColor(Color.rgb(28, 105, 162));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(8f);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        setBackgroundColor(Color.rgb(239, 246, 250));
        setMinimumHeight(360);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (coordinates == null || coordinates.length() < 2) {
            pointPaint.setColor(Color.DKGRAY);
            pointPaint.setTextSize(34f);
            canvas.drawText("Not enough GPS points for a route preview", 24, getHeight() / 2f, pointPaint);
            return;
        }

        try {
            double minLon = Double.MAX_VALUE;
            double maxLon = -Double.MAX_VALUE;
            double minLat = Double.MAX_VALUE;
            double maxLat = -Double.MAX_VALUE;
            for (int index = 0; index < coordinates.length(); index++) {
                JSONArray point = coordinates.getJSONArray(index);
                double lon = point.getDouble(0);
                double lat = point.getDouble(1);
                minLon = Math.min(minLon, lon);
                maxLon = Math.max(maxLon, lon);
                minLat = Math.min(minLat, lat);
                maxLat = Math.max(maxLat, lat);
            }

            float padding = 48f;
            float width = Math.max(1f, getWidth() - padding * 2);
            float height = Math.max(1f, getHeight() - padding * 2);
            double lonSpan = Math.max(0.000001, maxLon - minLon);
            double latSpan = Math.max(0.000001, maxLat - minLat);
            float scale = (float) Math.min(width / lonSpan, height / latSpan);
            float drawnWidth = (float) (lonSpan * scale);
            float drawnHeight = (float) (latSpan * scale);
            float left = (getWidth() - drawnWidth) / 2f;
            float top = (getHeight() - drawnHeight) / 2f;

            Path route = new Path();
            for (int index = 0; index < coordinates.length(); index++) {
                JSONArray point = coordinates.getJSONArray(index);
                float x = left + (float) ((point.getDouble(0) - minLon) * scale);
                float y = top + drawnHeight - (float) ((point.getDouble(1) - minLat) * scale);
                if (index == 0) route.moveTo(x, y);
                else route.lineTo(x, y);
            }
            canvas.drawPath(route, linePaint);

            drawEndpoint(canvas, coordinates.getJSONArray(0), minLon, minLat, scale,
                    left, top, drawnHeight, Color.rgb(35, 140, 75));
            drawEndpoint(canvas, coordinates.getJSONArray(coordinates.length() - 1),
                    minLon, minLat, scale, left, top, drawnHeight,
                    Color.rgb(190, 55, 55));
        } catch (Exception ignored) {
            pointPaint.setColor(Color.DKGRAY);
            pointPaint.setTextSize(34f);
            canvas.drawText("Route preview unavailable", 24, getHeight() / 2f, pointPaint);
        }
    }

    private void drawEndpoint(
            Canvas canvas,
            JSONArray point,
            double minLon,
            double minLat,
            float scale,
            float left,
            float top,
            float drawnHeight,
            int color) throws Exception {
        float x = left + (float) ((point.getDouble(0) - minLon) * scale);
        float y = top + drawnHeight - (float) ((point.getDouble(1) - minLat) * scale);
        pointPaint.setColor(color);
        pointPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(x, y, 14f, pointPaint);
    }
}
