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
    private TextView helpBody;
    private boolean serviceOnly;
    private LinearLayout importSummary;
    private final ExecutorService importer = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFF0B1C50);
        getWindow().setNavigationBarColor(0xFF0B1C50);
        serviceOnly = getIntent().getBooleanExtra("service_only", false);
        buildScreen();
    }

    @Override
    protected void onDestroy() {
        importer.shutdownNow();
        super.onDestroy();
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        LinearLayout brand = RoadprintsHeader.create(this);
        brand.setPadding(0, 0, 0, dp(22));
        root.addView(brand);
        root.addView(text("DATA MANAGEMENT", 13, 0xFF67D5CC, true));

        TextView title = text(serviceOnly ? "Service station visits" : "Timeline data",
                30, Color.WHITE, true);
        root.addView(title);

        TextView intro = text(serviceOnly
                ? "Add another Google Timeline JSON file to update service station visits. "
                    + "Saved journeys and route matching will be left untouched."
                : "Choose your Google Timeline JSON file. It will be validated and imported into your local Roadprints archive.",
                17, 0xFFD3DCED, false);
        intro.setPadding(0, dp(8), 0, dp(22));
        root.addView(intro);

        choose = action(serviceOnly ? "ADD TIMELINE FILE" : "CHOOSE TIMELINE JSON");
        choose.setOnClickListener(v -> chooseFile());
        root.addView(choose, new LinearLayout.LayoutParams(-1, -2));

        filename = text("No file selected.", 15, 0xFF9FB3D0, false);
        filename.setPadding(0, dp(14), 0, 0);
        root.addView(filename);

        status = text(
                "Nothing has been imported yet.",
                16, 0xFF67D5CC, false);
        status.setPadding(0, dp(16), 0, 0);
        root.addView(status);
        importSummary = new LinearLayout(this);
        importSummary.setOrientation(LinearLayout.VERTICAL);
        importSummary.setVisibility(View.GONE);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(-1, -2);
        summaryParams.topMargin = dp(16);
        root.addView(importSummary, summaryParams);

        TextView help = text("▸ Where do I find my Timeline file?", 16, 0xFF67D5CC, true);
        help.setPadding(0, dp(24), 0, dp(12));
        help.setMinHeight(dp(48));
        help.setClickable(true);
        help.setFocusable(true);
        root.addView(help);
        helpBody = text("In Google Maps, open your profile and choose Your Timeline. "
                + "Open the Timeline menu, choose Settings and privacy, then Export Timeline data. "
                + "Select the JSON file here. Roadprints reads and saves it on this device.",
                14, 0xFFD3DCED, false);
        helpBody.setVisibility(View.GONE);
        helpBody.setPadding(0, 0, 0, dp(12));
        root.addView(helpBody);
        help.setOnClickListener(v -> {
            boolean show = helpBody.getVisibility() != View.VISIBLE;
            helpBody.setVisibility(show ? View.VISIBLE : View.GONE);
            help.setText((show ? "▾ " : "▸ ") + "Where do I find my Timeline file?");
        });

        capture = action(serviceOnly ? "RETURN TO MAP" : "CONTINUE TO GROWING");
        capture.setVisibility(View.GONE);
        capture.setOnClickListener(v -> {
            if (serviceOnly) {
                startActivity(new Intent(this, MapActivity.class));
            } else {
                Intent growing = new Intent(this, JourneyListActivity.class);
                growing.putExtra("open_growing", true);
                startActivity(growing);
            }
            finish();
        });
        LinearLayout.LayoutParams captureParams = new LinearLayout.LayoutParams(-1, -2);
        captureParams.setMargins(0, dp(24), 0, 0);
        root.addView(capture, captureParams);

        RoadprintsHeader.installUtilityPage(this, root, "BACK", this::finish);
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
        capture.setVisibility(View.GONE);
        importSummary.removeAllViews();
        importSummary.setVisibility(View.GONE);
        setBusy(true);
        status.setText("Reading Timeline file locally…");

        importer.submit(() -> {
            ImportResult result;
            try {
                result = importFile(uri, serviceOnly);
            } catch (Exception error) {
                result = new ImportResult(0, 0, 1,
                        "Import failed: " + safeMessage(error));
            }
            ImportResult finalResult = result;
            runOnUiThread(() -> {
                setBusy(false);
                status.setText(finalResult.message);
                if (finalResult.message.startsWith("Import complete:")
                        || finalResult.message.startsWith("Service import complete:")) {
                    showImportSummary(finalResult);
                    capture.setVisibility(View.VISIBLE);
                    capture.setEnabled(true);
                }
            });
        });
    }

    private ImportResult importFile(Uri uri, boolean serviceOnly) throws Exception {
        int added = 0;
        int skipped = 0;
        int invalid = 0;
        int found = 0;
        int journeyRecordsParsed = 0;
        int journeysWithIntermediateTrace = 0;
        int sourceRoutePointsFound = 0;
        String fileFingerprint = fingerprint(uri.toString());
        List<JSONObject> semanticSegments = new ArrayList<>();
        JSONArray confirmedTimelineVisits = new JSONArray();
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
                        if (serviceOnly) {
                            collectServiceOnlySemanticVisit(reader, confirmedTimelineVisits, fileFingerprint);
                            continue;
                        }
                        JSONObject segment = readJsonObject(reader);
                        found++;
                        collectTimelinePathPoints(segment, semanticPathPoints);
                        JSONObject visit = segment.optJSONObject("visit");
                        if (visit == null) visit = segment.optJSONObject("placeVisit");
                        if (visit != null) collectConfirmedVisit(
                                segment, visit, confirmedTimelineVisits, fileFingerprint);
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
                        if (serviceOnly) {
                            collectServiceOnlyLegacyVisit(reader, confirmedTimelineVisits, fileFingerprint);
                            continue;
                        }
                        JSONObject wrapper = readJsonObject(reader);
                        JSONObject placeVisit = wrapper.optJSONObject("placeVisit");
                        if (placeVisit != null) collectConfirmedVisit(
                                placeVisit, placeVisit, confirmedTimelineVisits, fileFingerprint);
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

        if (serviceOnly && confirmedTimelineVisits.length() == 0) {
            throw new IllegalArgumentException(
                    "No Timeline place visits with coordinates were found. Saved journeys were not changed.");
        }
        int matchedServiceStations =
                ServiceStationStore.recordConfirmedTimelineVisits(this, confirmedTimelineVisits);
        if (serviceOnly) {
            ImportResult result = new ImportResult(0, 0, 0,
                    "Service import complete: " + count(confirmedTimelineVisits.length())
                            + " Timeline place visits saved; " + count(matchedServiceStations)
                            + " service stations matched. Saved journeys were not changed.");
            result.visitsSaved = confirmedTimelineVisits.length();
            result.stationsMatched = matchedServiceStations;
            return result;
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
                        "Import complete: %,d added, %,d already present, %,d unsupported or insufficient. "
                                + "Timeline paths: %,d route points across %,d journeys; "
                                + "%,d journeys contain intermediate points.",
                        added, skipped, invalid, sourceRoutePointsFound,
                        journeyRecordsParsed, journeysWithIntermediateTrace));
    }

    /** Reads only visit fields and skips route/activity payloads for service-only imports. */
    private void collectServiceOnlySemanticVisit(
            JsonReader reader, JSONArray output, String fileFingerprint) throws Exception {
        JSONObject segment = new JSONObject();
        JSONObject visit = null;
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if ("startTime".equals(name) || "endTime".equals(name)) {
                if (reader.peek() == JsonToken.STRING) segment.put(name, reader.nextString());
                else reader.skipValue();
            } else if ("visit".equals(name) || "placeVisit".equals(name)) {
                if (reader.peek() == JsonToken.BEGIN_OBJECT) visit = readJsonObject(reader);
                else reader.skipValue();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        if (visit != null) collectConfirmedVisit(segment, visit, output, fileFingerprint);
    }

    /** Old Timeline exports wrap place visits beside large activity route records. */
    private void collectServiceOnlyLegacyVisit(
            JsonReader reader, JSONArray output, String fileFingerprint) throws Exception {
        JSONObject visit = null;
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if ("placeVisit".equals(name) && reader.peek() == JsonToken.BEGIN_OBJECT) {
                visit = readJsonObject(reader);
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        if (visit != null) collectConfirmedVisit(visit, visit, output, fileFingerprint);
    }

    private void collectConfirmedVisit(
            JSONObject segment, JSONObject visit, JSONArray output, String fileFingerprint) {
        if (segment == null || visit == null) return;
        JSONObject candidate=visit.optJSONObject("topCandidate");
        JSONObject place=candidate==null?null:candidate.optJSONObject("placeLocation");
        if(place==null) place=visit.optJSONObject("location");
        double[] point=place==null?null:parseCoordinates(place.opt("latLng"));
        if(point==null&&place!=null) point=parseCoordinates(place);
        if(point==null) point=parseCoordinates(visit.opt("latLng"));
        if(point==null) return;
        String start=segment.optString("startTime","");
        String end=segment.optString("endTime","");
        JSONObject duration=visit.optJSONObject("duration");
        if(start.isEmpty()&&duration!=null) start=timestamp(duration,"startTimestamp");
        if(end.isEmpty()&&duration!=null) end=timestamp(duration,"endTimestamp");
        JSONObject confirmed=new JSONObject();
        try {
            confirmed.put("id", start + "|" + end + "|"
                    + String.format(Locale.US, "%.5f", point[0]) + "|"
                    + String.format(Locale.US, "%.5f", point[1]));
            confirmed.put("source", "google_timeline_placeVisit");
            confirmed.put("source_file_fingerprint", fileFingerprint);
            confirmed.put("schema", segment.has("startTime")
                    ? "semanticSegments" : "timelineObjects");
            confirmed.put("lat",point[0]);
            confirmed.put("lng",point[1]);
            confirmed.put("start",start);
            confirmed.put("end",end);
            if (candidate != null) {
                if(candidate.has("placeId")) confirmed.put("place_id",candidate.opt("placeId"));
                if(candidate.has("placeName")) confirmed.put("place_name",candidate.opt("placeName"));
                if(candidate.has("placeType")) confirmed.put("place_type",candidate.opt("placeType"));
                if(candidate.has("semanticType")) confirmed.put("semantic_type",candidate.opt("semanticType"));
                confirmed.put("source_candidate",candidate);
            }
            if (place != null) {
                if(place.has("name")) confirmed.put("place_name",place.opt("name"));
                if(place.has("address")) confirmed.put("place_address",place.opt("address"));
            }
            confirmed.put("source_visit", visit);
            output.put(confirmed);
        } catch(org.json.JSONException ignored) {
            // Ignore only the malformed place visit.
        }
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
                    "Imported %,d journeys locally… %,d new, %,d already present.",
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
                    if (pathPoint.timeMs >= startMs) {
                        sourceRoutePoints++;
                        appendUniquePoint(points, pathPoint.coordinates);
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
            if ((roadMode(mode) && sourceRoutePoints < 2)
                    || (footMode(mode) && points.size() < 2)) {
                // Insufficient-evidence road and walking trips cannot be matched
                // or usefully reviewed, so do not add them to the local archive.
                return null;
            }
            double distance = distanceFor(activity, points);
            // Preserve the legacy ID used when Timeline FLYING labels were misclassified as unknown.
            String idMode = ("unknown".equals(mode) || "plane".equals(mode))
                    ? "driving" : mode;
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
        if (mode.contains("WALK") || mode.contains("RUN") || mode.contains("PEDESTRIAN")) return "walking";
        if (mode.contains("IN_PASSENGER_VEHICLE") || mode.contains("IN_VEHICLE") || mode.equals("DRIVING")) return "driving";
        if (mode.contains("CYCL")) return "cycling";
        if (mode.contains("BUS")) return "bus";
        if (mode.contains("RAIL") || mode.contains("TRAIN")
                || mode.contains("SUBWAY") || mode.contains("TRAM")) return "train";
        if (mode.contains("FERRY")) return "ferry";
        if (mode.contains("FLY") || mode.contains("AIRPLANE")
                || mode.contains("FLIGHT") || mode.contains("PLANE")) return "plane";
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
        button.setTextColor(busy ? 0xFFB9C5D8 : Color.WHITE);
        button.setBackground(rounded(busy ? 0xFF233B78 : 0xFF35558F, 0xFF496096));
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
        android.widget.Button button = new android.widget.Button(this);
        button.setText(label);
        button.setTextSize(15);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        button.setTextColor(Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(58));
        button.setPadding(dp(18), dp(12), dp(18), dp(12));
        button.setBackground(rounded(0xFF35558F, 0xFF496096));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private void showImportSummary(ImportResult result) {
        status.setText(serviceOnly ? "Service import complete" : "Import complete");
        importSummary.removeAllViews();
        if (serviceOnly) {
            addSummaryRow("Timeline visits saved", result.visitsSaved,
                    "Service stations matched", result.stationsMatched);
        } else {
            addSummaryRow("Journeys added", result.added, "Already present", result.skipped);
            addSummaryRow("Unsupported or insufficient", result.invalid, "Route points", result.sourceRoutePoints);
            addSummaryRow("Journeys with paths", result.recordsParsed,
                    "With intermediate points", result.journeysWithIntermediateTrace);
        }
        importSummary.setVisibility(View.VISIBLE);
    }

    private void addSummaryRow(String firstLabel, int firstValue, String secondLabel, int secondValue) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, -1, 1);
        left.rightMargin = dp(6);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, -1, 1);
        right.leftMargin = dp(6);
        row.addView(summaryTile(firstLabel, firstValue), left);
        row.addView(summaryTile(secondLabel, secondValue), right);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(12);
        importSummary.addView(row, params);
    }

    private LinearLayout summaryTile(String label, int value) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(16), dp(16), dp(16), dp(16));
        tile.setBackground(rounded(0xFF233B78, 0xFF35558F));
        tile.addView(text(count(value), 24, 0xFF67D5CC, true));
        TextView title = text(label, 13, 0xFFD3DCED, false);
        title.setPadding(0, dp(6), 0, 0);
        tile.addView(title);
        return tile;
    }

    private static String count(int value) { return String.format(Locale.UK, "%,d", value); }

    private android.graphics.drawable.GradientDrawable rounded(int colour, int border) {
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setColor(colour);
        shape.setCornerRadius(dp(14));
        shape.setStroke(dp(1), border);
        return shape;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private TextView text(String value, float size, int colour, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(colour);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private static final class ImportResult {
        int visitsSaved;
        int stationsMatched;
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
