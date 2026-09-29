package com.roadprints.capture;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
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
    private Button choose;
    private Button capture;
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

        TextView title = text("Timeline data", 30, Color.WHITE, true);
        root.addView(title);

        TextView intro = text(
                "Choose your Google Timeline JSON file. It will be validated and imported into your local Roadprints archive.",
                17, 0xFFD3DCED, false);
        intro.setPadding(0, 12, 0, 26);
        root.addView(intro);

        choose = new Button(this);
        choose.setText("CHOOSE TIMELINE JSON");
        choose.setTextColor(0xFF0B1C50);
        choose.setTextSize(14);
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

        capture = new Button(this);
        capture.setText("CONTINUE TO CAPTURE");
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
        status.setText("Validating Timeline data…");

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
        StringBuilder raw = new StringBuilder();
        try (InputStream input = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(
                     input, StandardCharsets.UTF_8))) {
            if (input == null) throw new IllegalStateException("The selected file could not be opened.");
            String line;
            while ((line = reader.readLine()) != null) raw.append(line);
        }

        JSONObject root = new JSONObject(raw.toString());
        List<JSONObject> segments = findSegments(root);
        if (segments.isEmpty()) {
            throw new IllegalArgumentException(
                    "No supported Timeline journeys were found in this file.");
        }

        int added = 0;
        int skipped = 0;
        int invalid = 0;
        for (JSONObject segment : segments) {
            JSONObject journey = journeyFromSegment(segment, fingerprint(raw.toString()));
            if (journey == null) {
                invalid++;
                continue;
            }
            String id = journey.optString("journey_id");
            if (JourneyStore.get(this, id) != null) {
                skipped++;
            } else {
                JourneyStore.save(this, journey);
                added++;
            }
        }

        return new ImportResult(
                added, skipped, invalid,
                String.format(Locale.UK,
                        "Import complete: %d added, %d already present, %d unsupported.",
                        added, skipped, invalid));
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

    private JSONObject journeyFromSegment(JSONObject segment, String fileFingerprint) {
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
            if (semantic) {
                JSONArray path = segment.optJSONArray("timelinePath");
                if (path != null) {
                    for (int i = 0; i < path.length(); i++) {
                        JSONObject point = path.optJSONObject(i);
                        if (point != null) addPoint(points, point.optString("point", ""));
                    }
                }
            } else {
                JSONArray path = activity.optJSONArray("waypointPath");
                if (path != null) {
                    for (int i = 0; i < path.length(); i++) {
                        addPoint(points, path.optString(i, ""));
                    }
                }
            }

            addPoint(points, pointValue(semantic
                    ? activity.optJSONObject("start")
                    : activity.optJSONObject("startLocation")));
            addPoint(points, pointValue(semantic
                    ? activity.optJSONObject("end")
                    : activity.optJSONObject("endLocation")));

            if (points.isEmpty()) return null;

            String mode = modeFor(activity, semantic);
            double distance = distanceFor(activity, points);
            String id = "timeline-" + fingerprint(started + "|" + ended + "|" + mode + "|" + coordinateKey(points));

            JSONObject coordinates = new JSONObject();
            coordinates.put("type", "LineString");
            JSONArray coordinateArray = new JSONArray();
            for (double[] point : points) {
                coordinateArray.put(new JSONArray().put(point[1]).put(point[0]));
            }
            coordinates.put("coordinates", coordinateArray);

            JSONObject processing = new JSONObject()
                    .put("import", "complete")
                    .put("road_matching", roadMode(mode) ? "pending" : "not_required")
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
                            .put("distance_meters", distance)
                            .put("status", points.size() >= 2
                                    ? "usable" : "insufficient_gps_data"));
        } catch (Exception error) {
            return null;
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
        return "driving";
    }

    private boolean roadMode(String mode) {
        return "driving".equals(mode) || "bus".equals(mode) || "cycling".equals(mode);
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

    private void setBusy(boolean busy) {
        choose.setEnabled(!busy);
        capture.setEnabled(!busy);
    }

    private String safeMessage(Exception error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
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
        final String message;

        ImportResult(int added, int skipped, int invalid, String message) {
            this.added = added;
            this.skipped = skipped;
            this.invalid = invalid;
            this.message = message;
        }
    }
}
