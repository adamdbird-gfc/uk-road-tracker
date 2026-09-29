package com.roadprints.capture;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.JsonReader;
import android.util.JsonToken;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TimelineImportActivity extends Activity {
    private static final int PICK_TIMELINE = 81;
    private TextView status;
    private TextView filename;
    private TextView choose;
    private TextView capture;
    private final ExecutorService importer = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFF0B1C50);
        getWindow().setNavigationBarColor(0xFF0B1C50);
        buildScreen();
    }

    @Override
    protected void onDestroy() {
        importer.shutdownNow();
        super.onDestroy();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF0B1C50);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 48, 32, 36);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top = insets.getSystemWindowInsetTop();
            int bottom = insets.getSystemWindowInsetBottom();
            view.setPadding(32, 48 + top, 32, 36 + bottom);
            return insets;
        });
        root.requestApplyInsets();

        TextView title = text("Timeline data", 30, Color.WHITE, true);
        root.addView(title);

        TextView intro = text(
                "Choose your Google Timeline JSON file. It will be validated and imported into your local Roadprints archive.",
                17, 0xFFD3DCED, false);
        intro.setPadding(0, 12, 0, 26);
        root.addView(intro);

        choose = action("CHOOSE TIMELINE JSON");
        choose.setOnClickListener(v -> chooseFile());
        root.addView(choose, new LinearLayout.LayoutParams(-1, 58));

        filename = text("No file selected.", 15, 0xFF9FB3D0, false);
        filename.setPadding(0, 20, 0, 0);
        root.addView(filename);

        status = text(
                "Nothing has been imported yet.",
                16, 0xFF67D5CC, false);
        status.setPadding(0, 16, 0, 0);
        root.addView(status);

        capture = action("CONTINUE TO CAPTURE");
        capture.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
        LinearLayout.LayoutParams captureParams = new LinearLayout.LayoutParams(-1, 58);
        captureParams.setMargins(0, 28, 0, 0);
        root.addView(capture, captureParams);

        scroll.addView(root);
        setContentView(scroll);
    }

    private void chooseFile() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("*/*");
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(picker, PICK_TIMELINE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_TIMELINE || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;

        try {
            getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
            // Some file providers do not offer persistable permissions.
        }

        filename.setText("Selected: " + displayName(uri));
        setBusy(true);
        status.setText("Reading Timeline file locally…");

        importer.submit(() -> {
            ImportResult result;
            try {
                result = importFile(uri);
            } catch (Exception error) {
                result = new ImportResult(0, 0, 1,
                        "Import failed: " + safeMessage(error));
            }
            ImportResult finalResult = result;
            runOnUiThread(() -> {
                setBusy(false);
                status.setText(finalResult.message);
            });
        });
    }

    private ImportResult importFile(Uri uri) throws Exception {
        int added = 0;
        int skipped = 0;
        int invalid = 0;
        int found = 0;
        int journeyRecordsParsed = 0;
        int journeysWithIntermediateTrace = 0;
        int sourceRoutePointsFound = 0;
        String fileFingerprint = fingerprint(uri.toString());
        List<JSONObject> semanticSegments = new ArrayList<>();
        List<TimedPoint> semanticPathPoints = new ArrayList<>();

        try (InputStream input = getContentResolver().openInputStream(uri);
             InputStreamReader inputReader = new InputStreamReader(input, StandardCharsets.UTF_8);
             JsonReader reader = new JsonReader(inputReader)) {
            if (input == null) {
                throw new IllegalStateException("The selected file could not be opened.");
            }

            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                throw new IllegalArgumentException("This Timeline file is not in a supported JSON format.");
            }

            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if ("semanticSegments".equals(name)) {
                    if (reader.peek() != JsonToken.BEGIN_ARRAY) {
                        reader.skipValue();
                        continue;
                    }
                    reader.beginArray();
                    while (reader.hasNext()) {
                        JSONObject segment = readJsonObject(reader);
                        found++;
                        collectTimelinePathPoints(segment, semanticPathPoints);
                        if (segment.optJSONObject("activity") != null) {
                            semanticSegments.add(segment);
                        } else {
                            invalid++;
                        }
                    }
                    reader.endArray();
                } else if ("timelineObjects".equals(name)) {
                    if (reader.peek() != JsonToken.BEGIN_ARRAY) {
                        reader.skipValue();
                        continue;
                    }
                    reader.beginArray();
                    while (reader.hasNext()) {
                        JSONObject wrapper = readJsonObject(reader);
                        JSONObject segment = wrapper.optJSONObject("activitySegment");
                        if (segment == null) continue;
                        found++;
                        ImportCounts counts = importSegment(segment, fileFingerprint, null);
                        added += counts.added;
                        skipped += counts.skipped;
                        invalid += counts.invalid;
                        journeyRecordsParsed += counts.recordsParsed;
                        journeysWithIntermediateTrace += counts.withIntermediateTrace;
                        sourceRoutePointsFound += counts.sourceRoutePoints;
                        publishImportProgress(found, added, skipped, invalid);
                    }
                    reader.endArray();
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
        }

        semanticPathPoints.sort((left, right) -> Long.compare(left.timeMs, right.timeMs));
        int semanticActivitiesProcessed = 0;
        for (JSONObject segment : semanticSegments) {
            ImportCounts counts = importSegment(segment, fileFingerprint, semanticPathPoints);
            added += counts.added;
            skipped += counts.skipped;
            invalid += counts.invalid;
            journeyRecordsParsed += counts.recordsParsed;
            journeysWithIntermediateTrace += counts.withIntermediateTrace;
            sourceRoutePointsFound += counts.sourceRoutePoints;
            publishImportProgress(++semanticActivitiesProcessed, added, skipped, invalid);
        }

        if (found == 0) {
            throw new IllegalArgumentException(
                    "No supported Timeline journeys were found in this file.");
        }

        return new ImportResult(
                added, skipped, invalid, journeyRecordsParsed,
                journeysWithIntermediateTrace, sourceRoutePointsFound,
                String.format(Locale.UK,
                        "Import complete: %d added, %d already present, %d unsupported. "
                                + "Timeline paths: %d route points across %d journeys; "
                                + "%d journeys contain intermediate points.",
                        added, skipped, invalid, sourceRoutePointsFound,
                        journeyRecordsParsed, journeysWithIntermediateTrace));
    }

    private ImportCounts importSegment(
            JSONObject segment, String fileFingerprint, List<TimedPoint> pathPoints) {
        JSONObject journey = journeyFromSegment(segment, fileFingerprint, pathPoints);
        if (journey == null) return new ImportCounts(0, 0, 1, 0, 0, 0);
        String id = journey.optString("journey_id");
        JSONObject quality = journey.optJSONObject("capture_quality");
        int gpsPoints = quality == null ? 0 : quality.optInt("gps_points", 0);
        int sourceRoutePoints = quality == null
                ? 0 : quality.optInt("source_route_points", 0);
        int withIntermediateTrace = gpsPoints > 2 ? 1 : 0;
        JSONObject existing = JourneyStore.get(this, id);
        if (existing != null) {
            if (shouldRefreshImportedJourney(existing, journey)) {
                try {
                    journey.put("revision", existing.optInt("revision", 1) + 1);
                    if (existing.has("title")) journey.put("title", existing.opt("title"));
                    JourneyStore.save(this, journey);
                } catch (Exception ignored) {
                    // Keep the existing archive if a refresh cannot be committed.
                }
            }
            return new ImportCounts(0, 1, 0, 1, withIntermediateTrace, sourceRoutePoints);
        }
        JourneyStore.save(this, journey);
        return new ImportCounts(1, 0, 0, 1, withIntermediateTrace, sourceRoutePoints);
    }

    private boolean shouldRefreshImportedJourney(
            JSONObject existing, JSONObject incoming) {
        JSONObject source = existing.optJSONObject("source");
        if (source == null
                || !"timeline_import".equals(source.optString("type", ""))) return false;
        if (!existing.optString("mode", "unknown")
                .equals(incoming.optString("mode", "unknown"))) return true;

        JSONObject oldQuality = existing.optJSONObject("capture_quality");
        JSONObject newQuality = incoming.optJSONObject("capture_quality");
        int oldPoints = oldQuality == null ? 0 : oldQuality.optInt("gps_points", 0);
        int newPoints = newQuality == null ? 0 : newQuality.optInt("gps_points", 0);
        if (newPoints > oldPoints) return true;

        JSONObject oldProcessing = existing.optJSONObject("processing");
        JSONObject newProcessing = incoming.optJSONObject("processing");
        if (oldProcessing == null || newProcessing == null) return false;
        return !oldProcessing.optString("road_matching", "")
                        .equals(newProcessing.optString("road_matching", ""))
                || !oldProcessing.optString("foot_matching", "")
                        .equals(newProcessing.optString("foot_matching", ""));
    }

    private void publishImportProgress(int found, int added, int skipped, int invalid) {
        if (found == 1 || found % 10 == 0) {
            publishStatus(String.format(Locale.UK,
                    "Imported %d journeys locally… %d new, %d already present.",
                    found, added, skipped));
        }
    }

    private JSONObject readJsonObject(JsonReader reader) throws Exception {
        Object value = readJsonValue(reader);
        if (!(value instanceof JSONObject)) {
            throw new IllegalArgumentException("Timeline segment was not a JSON object.");
        }
        return (JSONObject) value;
    }

    private Object readJsonValue(JsonReader reader) throws Exception {
        JsonToken token = reader.peek();
        switch (token) {
            case BEGIN_OBJECT:
                JSONObject object = new JSONObject();
                reader.beginObject();
                while (reader.hasNext()) {
                    object.put(reader.nextName(), readJsonValue(reader));
                }
                reader.endObject();
                return object;
            case BEGIN_ARRAY:
                JSONArray array = new JSONArray();
                reader.beginArray();
                while (reader.hasNext()) array.put(readJsonValue(reader));
                reader.endArray();
                return array;
            case STRING:
                return reader.nextString();
            case NUMBER:
                String number = reader.nextString();
                try {
                    return number.contains(".")
                            ? Double.parseDouble(number)
                            : Long.parseLong(number);
                } catch (NumberFormatException ignored) {
                    return number;
                }
            case BOOLEAN:
                return reader.nextBoolean();
            case NULL:
                reader.nextNull();
                return JSONObject.NULL;
            default:
                reader.skipValue();
                return JSONObject.NULL;
        }
    }

    private static final class ImportCounts {
        final int added;
        final int skipped;
        final int invalid;
        final int recordsParsed;
        final int withIntermediateTrace;
        final int sourceRoutePoints;

        ImportCounts(int added, int skipped, int invalid, int recordsParsed,
                     int withIntermediateTrace, int sourceRoutePoints) {
            this.added = added;
            this.skipped = skipped;
            this.invalid = invalid;
            this.recordsParsed = recordsParsed;
            this.withIntermediateTrace = withIntermediateTrace;
            this.sourceRoutePoints = sourceRoutePoints;
        }
    }

    private List<JSONObject> findSegments(JSONObject root) {
        List<JSONObject> result = new ArrayList<>();

        JSONArray semantic = root.optJSONArray("semanticSegments");
        if (semantic != null) {
            for (int i = 0; i < semantic.length(); i++) {
                JSONObject segment = semantic.optJSONObject(i);
                if (segment != null && segment.optJSONObject("activity") != null) {
                    result.add(segment);
                }
            }
        }

        JSONArray timeline = root.optJSONArray("timelineObjects");
        if (timeline != null) {
            for (int i = 0; i < timeline.length(); i++) {
                JSONObject wrapper = timeline.optJSONObject(i);
                if (wrapper == null) continue;
                JSONObject activity = wrapper.optJSONObject("activitySegment");
                if (activity != null) result.add(activity);
            }
        }

        return result;
    }

    private JSONObject journeyFromSegment(
            JSONObject segment, String fileFingerprint, List<TimedPoint> timelinePathPoints) {
        try {
            boolean semantic = segment.has("activity");
            JSONObject activity = semantic
                    ? segment.optJSONObject("activity")
                    : segment;

            String started = semantic
                    ? segment.optString("startTime", "")
                    : timestamp(activity.optJSONObject("duration"), "startTimestamp");
            String ended = semantic
                    ? segment.optString("endTime", "")
                    : timestamp(activity.optJSONObject("duration"), "endTimestamp");
            if (started.isEmpty() || ended.isEmpty()) return null;

            List<double[]> points = new ArrayList<>();
            addLocationPoint(points, semantic
                    ? activity.optJSONObject("start")
                    : activity.optJSONObject("startLocation"));

            int sourceRoutePoints = 0;
            if (semantic && timelinePathPoints != null) {
                long startMs = parseTimelineTime(started);
                long endMs = parseTimelineTime(ended);
                for (int i = lowerBound(timelinePathPoints, startMs);
                     i < timelinePathPoints.size(); i++) {
                    TimedPoint pathPoint = timelinePathPoints.get(i);
                    if (pathPoint.timeMs > endMs) break;
                    if (pathPoint.timeMs >= startMs
                            && appendUniquePoint(points, pathPoint.coordinates)) {
                        sourceRoutePoints++;
                    }
                }
            } else if (!semantic) {
                JSONArray path = activity.optJSONArray("waypointPath");
                if (path != null) {
                    for (int i = 0; i < path.length(); i++) {
                        List<double[]> parsed = new ArrayList<>(1);
                        addPoint(parsed, path.optString(i, ""));
                        if (!parsed.isEmpty()
                                && appendUniquePoint(points, parsed.get(0))) {
                            sourceRoutePoints++;
                        }
                    }
                }
            }

            addLocationPoint(points, semantic
                    ? activity.optJSONObject("end")
                    : activity.optJSONObject("endLocation"));

            if (points.isEmpty()) return null;

            String mode = modeFor(activity, semantic);
            double distance = distanceFor(activity, points);
            String idMode = "unknown".equals(mode) ? "driving" : mode;
            double[] firstPoint = points.get(0);
            double[] lastPoint = points.get(points.size() - 1);
            String stableEndpoints = firstPoint[0] + "," + firstPoint[1]
                    + ";" + lastPoint[0] + "," + lastPoint[1];
            String id = "timeline-" + fingerprint(
                    started + "|" + ended + "|" + idMode + "|" + stableEndpoints);

            JSONObject coordinates = new JSONObject();
            coordinates.put("type", "LineString");
            JSONArray coordinateArray = new JSONArray();
            for (double[] point : points) {
                coordinateArray.put(new JSONArray().put(point[1]).put(point[0]));
            }
            coordinates.put("coordinates", coordinateArray);

            boolean eligibleForRoadMatching = roadMode(mode) && sourceRoutePoints >= 2;
            JSONObject processing = new JSONObject()
                    .put("import", "complete")
                    .put("road_matching", eligibleForRoadMatching ? "pending" : "not_required")
                    .put("foot_matching", footMode(mode) ? "pending" : "not_required");

            return new JSONObject()
                    .put("journey_id", id)
                    .put("revision", 1)
                    .put("source", new JSONObject()
                            .put("type", "timeline_import")
                            .put("source_file_fingerprint", fileFingerprint))
                    .put("started_at", started)
                    .put("ended_at", ended)
                    .put("timezone", "Europe/London")
                    .put("mode", mode)
                    .put("transport_confirmation", "confirmed")
                    .put("distance_meters", distance)
                    .put("route_geometry", coordinates)
                    .put("processing", processing)
                    .put("places", new JSONArray())
                    .put("stops", new JSONArray())
                    .put("capture_quality", new JSONObject()
                            .put("gps_points", points.size())
                            .put("source_route_points", sourceRoutePoints)
                            .put("distance_meters", distance)
                            .put("status", points.size() >= 2
                                    ? "usable" : "insufficient_gps_data"));
        } catch (Exception error) {
            return null;
        }
    }

    private void collectTimelinePathPoints(
            JSONObject segment, List<TimedPoint> destination) {
        JSONArray path = segment.optJSONArray("timelinePath");
        if (path == null) return;
        for (int i = 0; i < path.length(); i++) {
            JSONObject item = path.optJSONObject(i);
            if (item == null) continue;
            long timeMs = parseTimelineTime(item.optString("time", ""));
            double[] coordinates = parseCoordinates(item.opt("point"));
            if (timeMs >= 0 && coordinates != null) {
                destination.add(new TimedPoint(timeMs, coordinates));
            }
        }
    }

    private long parseTimelineTime(String value) {
        try {
            return Instant.parse(value).toEpochMilli();
        } catch (Exception ignored) {
            return -1L;
        }
    }

    private int lowerBound(List<TimedPoint> points, long timeMs) {
        int low = 0;
        int high = points.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (points.get(mid).timeMs < timeMs) low = mid + 1;
            else high = mid;
        }
        return low;
    }

    private double[] parseCoordinates(Object value) {
        if (value == null || value == JSONObject.NULL) return null;
        if (value instanceof String) {
            List<double[]> parsed = new ArrayList<>(1);
            addPoint(parsed, (String) value);
            return parsed.isEmpty() ? null : parsed.get(0);
        }
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            Object nested = object.opt("point");
            if (nested == null) nested = object.opt("geo");
            if (nested == null) nested = object.opt("latLng");
            if (nested == null) nested = object.opt("location");

            Double lat = numberValue(object.opt("latitude"));
            if (lat == null) lat = numberValue(object.opt("lat"));
            if (lat == null) {
                Double e7 = numberValue(object.opt("latitudeE7"));
                if (e7 != null) lat = e7 / 10000000.0;
            }
            Double lng = numberValue(object.opt("longitude"));
            if (lng == null) lng = numberValue(object.opt("lng"));
            if (lng == null) lng = numberValue(object.opt("lon"));
            if (lng == null) {
                Double e7 = numberValue(object.opt("longitudeE7"));
                if (e7 != null) lng = e7 / 10000000.0;
            }
            if (lat != null && lng != null
                    && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180) {
                return new double[]{lat, lng};
            }
            if (nested != null && nested != value) return parseCoordinates(nested);
        }
        return null;
    }

    private Double numberValue(Object value) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try {
                return Double.parseDouble(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private void addLocationPoint(List<double[]> points, JSONObject location) {
        if (location == null) return;
        double[] coordinates = parseCoordinates(location.opt("latLng"));
        if (coordinates == null) {
            coordinates = parseCoordinates(location);
        }
        if (coordinates != null) appendUniquePoint(points, coordinates);
    }

    private boolean appendUniquePoint(List<double[]> points, double[] point) {
        if (point == null) return false;
        if (!points.isEmpty()) {
            double[] last = points.get(points.size() - 1);
            if (Math.abs(last[0] - point[0]) < 0.0000001
                    && Math.abs(last[1] - point[1]) < 0.0000001) {
                return false;
            }
        }
        points.add(point);
        return true;
    }

    private static final class TimedPoint {
        final long timeMs;
        final double[] coordinates;

        TimedPoint(long timeMs, double[] coordinates) {
            this.timeMs = timeMs;
            this.coordinates = coordinates;
        }
    }

    private String modeFor(JSONObject activity, boolean semantic) {
        String value;
        if (semantic) {
            JSONObject candidate = activity.optJSONObject("topCandidate");
            value = candidate == null
                    ? activity.optString("topCandidate", "")
                    : candidate.optString("type", "");
        } else {
            value = activity.optString("activityType", "");
        }
        String mode = value.toUpperCase(Locale.UK);
        if (mode.contains("WALK") || mode.contains("PEDESTRIAN")) return "walking";
        if (mode.contains("CYCL")) return "cycling";
        if (mode.contains("BUS")) return "bus";
        if (mode.contains("RAIL") || mode.contains("TRAIN")
                || mode.contains("SUBWAY") || mode.contains("TRAM")) return "train";
        if (mode.contains("FERRY")) return "ferry";
        if (mode.contains("FLIGHT") || mode.contains("PLANE")) return "plane";
        return "unknown";
    }

    private boolean roadMode(String mode) {
        // Match the web POC: buses use roads, while cycling and unknown modes
        // remain visible in the journey list without entering road matching.
        return "driving".equals(mode) || "bus".equals(mode);
    }

    private boolean footMode(String mode) {
        return "walking".equals(mode);
    }

    private String coordinateKey(List<double[]> points) {
        StringBuilder key = new StringBuilder();
        for (double[] point : points) key.append(point[0]).append(",").append(point[1]).append(";");
        return key.toString();
    }

    private String timestamp(JSONObject duration, String key) {
        return duration == null ? "" : duration.optString(key, "");
    }

    private String pointValue(JSONObject location) {
        if (location == null) return "";
        String value = location.optString("latLng", "");
        if (!value.isEmpty()) return value;
        if (location.has("latitudeE7") && location.has("longitudeE7")) {
            return (location.optDouble("latitudeE7") / 10000000.0) + ","
                    + (location.optDouble("longitudeE7") / 10000000.0);
        }
        return "";
    }

    private void addPoint(List<double[]> points, String value) {
        if (value == null || value.isEmpty()) return;
        String cleaned = value.replace("geo:", "").replace("°", "").trim();
        String[] parts = cleaned.split(",");
        if (parts.length < 2) return;
        try {
            double lat = Double.parseDouble(parts[0].trim());
            double lng = Double.parseDouble(parts[1].trim());
            if (lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180) {
                points.add(new double[]{lat, lng});
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private double distanceFor(JSONObject activity, List<double[]> points) {
        double supplied = activity.optDouble("distanceMeters", -1);
        if (supplied >= 0) return supplied;
        supplied = activity.optDouble("distance", -1);
        if (supplied >= 0) return supplied;
        double total = 0;
        for (int i = 1; i < points.size(); i++) {
            total += haversine(points.get(i - 1), points.get(i));
        }
        return total;
    }

    private double haversine(double[] first, double[] second) {
        double earth = 6371000;
        double lat1 = Math.toRadians(first[0]);
        double lat2 = Math.toRadians(second[0]);
        double dLat = lat2 - lat1;
        double dLng = Math.toRadians(second[1] - first[1]);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return earth * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private String fingerprint(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte item : digest) result.append(String.format("%02x", item));
        return result.substring(0, 24);
    }

    private String displayName(Uri uri) {
        String result = uri.toString();
        try (android.database.Cursor cursor = getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) result = cursor.getString(0);
        } catch (Exception ignored) {
        }
        return result;
    }

    private void publishStatus(String message) {
        runOnUiThread(() -> {
            if (status != null) status.setText(message);
        });
    }

    private void setBusy(boolean busy) {
        setActionState(choose, busy);
        setActionState(capture, busy);
    }

    private void setActionState(TextView button, boolean busy) {
        button.setEnabled(!busy);
        button.setAlpha(busy ? 0.72f : 1f);
        button.setTextColor(busy ? 0xFF5D5D5D : 0xFF0B1C50);
        button.setBackgroundColor(busy ? 0xFF9E9E9E : 0xFFF7C450);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024 * 1024) {
            return (bytes / 1024) + " KB";
        }
        return String.format(Locale.UK, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private String safeMessage(Exception error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private TextView action(String label) {
        TextView button = text(label, 15, 0xFF0B1C50, true);
        button.setGravity(Gravity.CENTER);
        button.setBackgroundColor(0xFFF7C450);
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private TextView text(String value, float size, int colour, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(colour);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private static final class ImportResult {
        final int added;
        final int skipped;
        final int invalid;
        final int recordsParsed;
        final int journeysWithIntermediateTrace;
        final int sourceRoutePoints;
        final String message;

        ImportResult(int added, int skipped, int invalid, int recordsParsed,
                     int journeysWithIntermediateTrace, int sourceRoutePoints, String message) {
            this.added = added;
            this.skipped = skipped;
            this.invalid = invalid;
            this.recordsParsed = recordsParsed;
            this.journeysWithIntermediateTrace = journeysWithIntermediateTrace;
            this.sourceRoutePoints = sourceRoutePoints;
            this.message = message;
        }

        ImportResult(int added, int skipped, int invalid, String message) {
            this(added, skipped, invalid, 0, 0, 0, message);
        }
    }
}
