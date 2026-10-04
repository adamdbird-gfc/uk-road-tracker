package com.roadprints.capture;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class JourneyMapEditorActivity extends Activity {
    private static final String EXTRA_JOURNEY_ID = "journey_id";
    private final Set<Integer> removedEdges = new LinkedHashSet<>();
    private final Set<Integer> originalRemovedEdges = new LinkedHashSet<>();
    private final Deque<Set<Integer>> undoStack = new ArrayDeque<>();
    private JSONObject journey;
    private RoutePreviewView routeView;
    private TextView status;
    private TextView removeButton;
    private TextView restoreButton;
    private TextView saveButton;
    private TextView undoButton;
    private TextView correctionButton;
    private JSONArray correctionTrace;
    private boolean drawingCorrection;
    private List<JSONArray> routeSegments;
    private boolean removeMode = true;
    private final Set<Integer> selectedEdges = new LinkedHashSet<>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window window = getWindow();
        window.setStatusBarColor(0xFF0B1C50);
        window.setNavigationBarColor(0xFF10275D);

        String journeyId = getIntent().getStringExtra(EXTRA_JOURNEY_ID);
        journey = journeyId == null || journeyId.isEmpty()
                ? null : JourneyStore.get(this, journeyId);
        if (journey == null) {
            Toast.makeText(this, "Journey could not be opened", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        List<JSONArray> routes = matchedRouteSegments(
                journey.optJSONObject("processing_result"));
        if (routes.isEmpty()) {
            Toast.makeText(this, "This journey has no matched route to edit", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        readSavedCorrections();
        originalRemovedEdges.addAll(removedEdges);
        routeSegments = routes;
        buildScreen(routes);
    }

    private void buildScreen(List<JSONArray> routes) {
        int side = dp(20);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0B1C50);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(side, dp(12), side, dp(12));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Edit journey route", 21, Color.WHITE, true);
        titleRow.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView cancel = text("CANCEL", 13, 0xFFD3DCED, true);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(dp(10), dp(10), dp(10), dp(10));
        cancel.setOnClickListener(v -> requestCancel());
        titleRow.addView(cancel);
        saveButton = text("SAVE", 13, 0xFF0B1C50, true);
        saveButton.setGravity(Gravity.CENTER);
        saveButton.setPadding(dp(16), dp(10), dp(16), dp(10));
        saveButton.setOnClickListener(v -> saveChanges());
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(40));
        saveParams.setMargins(dp(8), 0, 0, 0);
        titleRow.addView(saveButton, saveParams);
        header.addView(titleRow);

        TextView instructions = text(
                "Tap a route section to select its overlapping lines together. Restore also brings back the latest removal.",
                14, 0xFFD3DCED, false);
        instructions.setPadding(0, dp(6), 0, dp(4));
        header.addView(instructions);
        root.addView(header);

        JSONObject geometry = journey.optJSONObject("route_geometry");
        JSONArray original = geometry == null ? null : geometry.optJSONArray("coordinates");
        routeView = new RoutePreviewView(this, original, routes, removedEdges,
                this::onRouteEdgesTap);
        routeView.setRouteExtensions(unmatchedEndpointTrace(original, routes));
        routeView.setCorrectionTraceListener(this::onCorrectionTrace);
        root.addView(routeView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(side, dp(10), side, dp(12));
        footer.setBackgroundColor(0xFF10275D);

        status = text("", 13, 0xFF67D5CC, false);
        status.setPadding(0, 0, 0, dp(10));
        footer.addView(status);

        correctionButton = controlButton("DRAW CORRECTION", false);
        correctionButton.setOnClickListener(v -> beginCorrectionTrace());
        LinearLayout.LayoutParams correctionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        correctionParams.setMargins(0, 0, 0, dp(8));
        footer.addView(correctionButton, correctionParams);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        removeButton = controlButton("REMOVE", true);
        restoreButton = controlButton("RESTORE", false);
        removeButton.setOnClickListener(v -> applySelectedChange(true));
        restoreButton.setOnClickListener(v -> applySelectedChange(false));
        undoButton = controlButton("UNDO", false);
        undoButton.setOnClickListener(v -> undo());
        LinearLayout.LayoutParams controlParams = new LinearLayout.LayoutParams(
                0, dp(48), 1);
        controlParams.setMargins(0, 0, dp(8), 0);
        controls.addView(removeButton, controlParams);
        controls.addView(restoreButton, controlParams);
        LinearLayout.LayoutParams undoParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        controls.addView(undoButton, undoParams);
        footer.addView(controls);
        root.addView(footer);

        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top = 0;
            int bottom = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            header.setPadding(side, dp(12) + top, side, dp(12));
            footer.setPadding(side, dp(10), side, dp(12) + bottom);
            return insets;
        });
        setContentView(root);
        root.requestApplyInsets();
        updateStatus();
        updateActionButtons();
    }

    private TextView controlButton(String label, boolean selected) {
        TextView button = text(label, 12, selected ? 0xFF0B1C50 : Color.WHITE, true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(roundRect(selected ? 0xFFF7C450 : 0xFF233B78,
                selected ? 0xFFF7C450 : 0xFF46649E, dp(10)));

        return button;
    }

    private void updateModeStyles() {
        routeView.setRouteEditState(removedEdges, selectedEdges, !removeMode);
        updateStatus();
        updateActionButtons();
    }

    private void updateActionButtons() {
        int activeCount = 0;
        int removedCount = 0;
        for (Integer edge : selectedEdges) {
            if (removedEdges.contains(edge)) removedCount++;
            else activeCount++;
        }
        boolean canRemove = activeCount > 0;
        boolean canRestore = removedCount > 0 || !removedEdges.isEmpty();
        boolean dirty = !removedEdges.equals(originalRemovedEdges) || correctionTrace != null;
        boolean canUndo = !undoStack.isEmpty();
        removeButton.setText(activeCount > 1 ? "REMOVE " + activeCount + " SECTIONS" : "REMOVE");
        restoreButton.setText(removedCount > 1 ? "RESTORE " + removedCount + " SECTIONS" : "RESTORE");
        styleActionButton(removeButton, canRemove, removeMode);
        styleActionButton(restoreButton, canRestore, canRestore);
        styleActionButton(undoButton, canUndo, false);
        styleActionButton(correctionButton, true, drawingCorrection || correctionTrace == null);
        correctionButton.setText(correctionTrace == null ? "DRAW CORRECTION" : "REDRAW CORRECTION");
        saveButton.setEnabled(dirty);
        saveButton.setBackground(roundRect(dirty ? 0xFFF7C450 : 0xFF655C48,
                dirty ? 0xFFF7C450 : 0xFF655C48, dp(10)));
        saveButton.setTextColor(dirty ? 0xFF0B1C50 : 0xFFAEBBD0);
        saveButton.setAlpha(dirty ? 1f : 0.68f);
    }

    private void styleActionButton(TextView button, boolean enabled, boolean highlighted) {
        int fill = !enabled ? 0xFF17294F : highlighted ? 0xFFF7C450 : 0xFF233B78;
        int stroke = !enabled ? 0xFF293E6A : highlighted ? 0xFFF7C450 : 0xFF46649E;
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.58f);
        button.setBackground(roundRect(fill, stroke, dp(10)));
        button.setTextColor(!enabled ? 0xFF8795AE
                : highlighted ? 0xFF0B1C50 : Color.WHITE);
    }

    private void requestCancel() {
        if (!removedEdges.equals(originalRemovedEdges)) {
            new AlertDialog.Builder(this)
                    .setTitle("Discard route changes?")
                    .setMessage("You have unsaved route edits. Discard them and leave?")
                    .setNegativeButton("Keep editing", (dialog, which) -> dialog.dismiss())
                    .setPositiveButton("Discard", (dialog, which) -> finish())
                    .show();
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        requestCancel();
    }

    private void onRouteEdgesTap(Set<Integer> edgeIndices) {
        selectedEdges.clear();
        if (edgeIndices != null) selectedEdges.addAll(edgeIndices);
        routeView.setRouteEditState(removedEdges, selectedEdges, !removeMode);
        updateStatus();
        updateActionButtons();
    }

    private void beginCorrectionTrace() {
        if (drawingCorrection) {
            drawingCorrection = false;
            routeView.setCorrectionDrawMode(false);
            status.setText("Route tracing cancelled.");
            updateActionButtons();
            return;
        }
        drawingCorrection = true;
        routeView.setCorrectionDrawMode(true);
        status.setText(selectedEdges.isEmpty()
                ? "Trace the corrected path over the map; the nearest route section will be selected."
                : "Trace the route you actually travelled on the map. Tap DRAW CORRECTION to cancel.");
        correctionButton.setText("CANCEL TRACE");
    }

    private void onCorrectionTrace(JSONArray trace) {
        drawingCorrection = false;
        if (trace == null || trace.length() < 2) {
            status.setText("Draw a longer route section to save a correction.");
            updateActionButtons();
            return;
        }
        correctionTrace = trace;
        routeView.setCorrectionTrace(trace);
        status.setText("Corrected path ready · SAVE will update the journey and rematch it.");
        updateActionButtons();
    }

    private void applySelectedChange(boolean remove) {
        removeMode = remove;
        if (selectedEdges.isEmpty() && !remove && !removedEdges.isEmpty()) {
            selectedEdges.add(latestRemovedEdge());
        }
        int eligibleCount = 0;
        for (Integer edge : selectedEdges) {
            if (removedEdges.contains(edge) != remove) eligibleCount++;
        }
        if (eligibleCount == 0) {
            updateModeStyles();
            status.setText(selectedEdges.isEmpty()
                    ? "Tap a route section to select it first."
                    : remove ? "Those sections are already removed."
                    : "Select a removed section to restore it.");
            return;
        }
        undoStack.push(new LinkedHashSet<>(removedEdges));
        if (undoStack.size() > 30) undoStack.removeLast();
        for (Integer edge : selectedEdges) {
            if (remove) removedEdges.add(edge);
            else removedEdges.remove(edge);
        }
        selectedEdges.clear();
        routeView.setRouteEditState(removedEdges, selectedEdges, !removeMode);
        updateStatus();
        updateActionButtons();
    }

    private void undo() {
        if (undoStack.isEmpty()) {
            status.setText("There are no route edits to undo.");
            return;
        }
        removedEdges.clear();
        removedEdges.addAll(undoStack.pop());
        selectedEdges.clear();
        routeView.setRouteEditState(removedEdges, selectedEdges, !removeMode);
        updateStatus();
        updateActionButtons();
    }

    private int latestRemovedEdge() {
        int latest = -1;
        for (Integer edge : removedEdges) latest = edge;
        return latest;
    }

    private void updateStatus() {
        if (!selectedEdges.isEmpty()) {
            int active = 0;
            int removed = 0;
            for (Integer edge : selectedEdges) {
                if (removedEdges.contains(edge)) removed++;
                else active++;
            }
            if (active > 0) {
                status.setText(active == 1
                        ? "Route section selected in red · press REMOVE."
                        : active + " overlapping route sections selected in red · press REMOVE to remove them together.");
            } else {
                status.setText(removed == 1
                        ? "Removed section selected · press RESTORE."
                        : removed + " removed sections selected · press RESTORE to bring them back together.");
            }
            return;
        }
        status.setText(removedEdges.size() + " route section"
                + (removedEdges.size() == 1 ? "" : "s")
                + " removed · tap a section or press RESTORE to bring back the latest removal.");
    }

    private void saveChanges() {
        if (removedEdges.equals(originalRemovedEdges) && correctionTrace == null) return;
        List<JSONArray> routes = matchedRouteSegments(journey.optJSONObject("processing_result"));
        List<JSONObject> roadRecords = JourneyCorrectionUtils.removedRoadRecords(
                journey, routes, removedEdges);
        if (correctionTrace != null) {
            new AlertDialog.Builder(this)
                    .setTitle("Save corrected route?")
                    .setMessage("This will replace the selected section of the GPS trace, then rematch the journey so its route and road records are recalculated.")
                    .setNegativeButton("KEEP EDITING", (dialog, which) -> dialog.dismiss())
                    .setPositiveButton("SAVE & REMATCH", (dialog, which) -> persistChanges(roadRecords))
                    .show();
            return;
        }
        if (JourneyCorrectionUtils.hasNewRemovals(removedEdges, originalRemovedEdges)) {
            StringBuilder message = new StringBuilder();
            if (roadRecords.isEmpty()) {
                message.append(removedEdges.size()).append(" matched route section")
                        .append(removedEdges.size() == 1 ? "" : "s")
                        .append(" will be removed. No named road records could be associated with them.");
            } else {
                message.append("Road records for these roads will be removed from this journey and its map, journey summaries, progress and achievements:");
                for (JSONObject record : roadRecords) {
                    message.append("\n\n• ").append(record.optString("label", "Road"));
                }
            }
            new AlertDialog.Builder(this)
                    .setTitle("Confirm road record removal")
                    .setMessage(message.toString())
                    .setNegativeButton("CANCEL", (dialog, which) -> dialog.dismiss())
                    .setPositiveButton("REMOVE RECORDS", (dialog, which) ->
                            persistChanges(roadRecords))
                    .show();
            return;
        }
        persistChanges(roadRecords);
    }

    private void persistChanges(List<JSONObject> roadRecords) {
        try {
            JSONObject corrections = journey.optJSONObject("journey_corrections");
            if (corrections == null) corrections = new JSONObject();
            List<Integer> ordered = new ArrayList<>(removedEdges);
            Collections.sort(ordered);
            JSONArray saved = new JSONArray();
            for (Integer edge : ordered) saved.put(edge);
            JSONArray removedRoadIds = new JSONArray();
            JSONArray removedRoadLabels = new JSONArray();
            Set<String> seenRoadIds = new LinkedHashSet<>();
            for (JSONObject record : roadRecords) {
                String id = record.optString("id", "");
                if (id.isEmpty() || !seenRoadIds.add(id)) continue;
                removedRoadIds.put(id);
                removedRoadLabels.put(record.optString("label", "Road"));
            }
            corrections.put("removed_matched_segments", correctionTrace == null ? saved : new JSONArray());
            if (correctionTrace != null) {
                JSONArray corrected = spliceGpsTrace(journey, correctionTrace);
                if (corrected == null || corrected.length() < 2) {
                    Toast.makeText(this, "The selected section could not be matched to GPS points", Toast.LENGTH_LONG).show();
                    return;
                }
                JSONObject geometry = journey.optJSONObject("route_geometry");
                if (geometry == null) geometry = new JSONObject();
                geometry.put("coordinates", corrected);
                geometry.put("type", "LineString");
                journey.put("route_geometry", geometry);
                journey.remove("processing_result");
                journey.put("processing_status", "pending");
                journey.remove("error_summary");
            }
            corrections.put("removed_road_ids", removedRoadIds);
            corrections.put("removed_road_labels", removedRoadLabels);
            journey.put("journey_corrections", corrections);
            journey.put("revision", journey.optInt("revision", 1) + 1);
            JourneyStore.save(this, journey);
            if (correctionTrace != null) {
                boolean queued = MatchingCoordinator.get(this).rematch(journey.optString("journey_id"));
                if (!queued) Toast.makeText(this,
                        "Route saved. Matching is busy; rematch this journey when it is ready.",
                        Toast.LENGTH_LONG).show();
            }
            setResult(RESULT_OK);
            finish();
        } catch (Exception error) {
            Toast.makeText(this, "Changes could not be saved", Toast.LENGTH_LONG).show();
        }
    }

    private static JSONArray spliceGpsTrace(JSONObject journey, JSONArray replacement) {
        if (journey == null || replacement == null || replacement.length() < 2) return null;
        JSONObject geometry = journey.optJSONObject("route_geometry");
        JSONArray original = geometry == null ? null : geometry.optJSONArray("coordinates");
        if (original == null || original.length() < 2) return null;

        JSONArray replacementStart = replacement.optJSONArray(0);
        JSONArray replacementEnd = replacement.optJSONArray(replacement.length() - 1);
        int firstIndex = -1, lastIndex = -1;
        double bestPairDistance = Double.MAX_VALUE;
        for (int i = 0; i < original.length(); i++) {
            JSONArray gpsStart = original.optJSONArray(i);
            double startDistance = tracePointDistanceMetres(gpsStart, replacementStart);
            if (startDistance > 250) continue;
            for (int j = 0; j < original.length(); j++) {
                if (i == j) continue;
                JSONArray gpsEnd = original.optJSONArray(j);
                double endDistance = tracePointDistanceMetres(gpsEnd, replacementEnd);
                double pairDistance = startDistance + endDistance;
                if (endDistance <= 250 && pairDistance < bestPairDistance) {
                    bestPairDistance = pairDistance;
                    firstIndex = i;
                    lastIndex = j;
                }
            }
        }
        if (firstIndex < 0 || lastIndex < 0) return null;

        int low = Math.min(firstIndex, lastIndex);
        int high = Math.max(firstIndex, lastIndex);
        boolean reverseReplacement = tracePointDistanceMetres(
                replacementStart, original.optJSONArray(low))
                > tracePointDistanceMetres(replacementStart, original.optJSONArray(high));
        JSONArray result = new JSONArray();
        for (int i = 0; i < low; i++) result.put(original.optJSONArray(i));
        appendDistinct(result, replacement, reverseReplacement);
        for (int i = high + 1; i < original.length(); i++) result.put(original.optJSONArray(i));
        return result.length() >= 2 ? result : null;
    }

    private static void appendDistinct(JSONArray target, JSONArray source, boolean reverse) {
        int last = target.length() == 0 ? -1 : target.length() - 1;
        if (!reverse) {
            for (int i = 0; i < source.length(); i++) {
                JSONArray point = source.optJSONArray(i);
                if (point == null || point.length() < 2) continue;
                if (last >= 0 && tracePointDistanceMetres(target.optJSONArray(last), point) < 5) continue;
                target.put(point);
                last = target.length() - 1;
            }
        } else {
            for (int i = source.length() - 1; i >= 0; i--) {
                JSONArray point = source.optJSONArray(i);
                if (point == null || point.length() < 2) continue;
                if (last >= 0 && tracePointDistanceMetres(target.optJSONArray(last), point) < 5) continue;
                target.put(point);
                last = target.length() - 1;
            }
        }
    }

    static List<JSONArray> unmatchedEndpointTrace(JSONArray original, List<JSONArray> routes) {
        List<JSONArray> extensions = new ArrayList<>();
        if (original == null || original.length() < 2 || routes == null || routes.isEmpty())
            return extensions;
        try {
            JSONArray firstRoute = routes.get(0);
            JSONArray lastRoute = routes.get(routes.size() - 1);
            if (firstRoute.length() < 2 || lastRoute.length() < 2) return extensions;
            JSONArray firstMatched = firstRoute.getJSONArray(0);
            JSONArray lastMatched = lastRoute.getJSONArray(lastRoute.length() - 1);
            int firstIndex = nearestTracePoint(original, firstMatched);
            int lastIndex = nearestTracePoint(original, lastMatched);
            if (firstIndex < 0 || lastIndex < 0) return extensions;
            boolean forward = firstIndex <= lastIndex;
            int low = Math.min(firstIndex, lastIndex);
            int high = Math.max(firstIndex, lastIndex);
            JSONArray lowEndpoint = forward ? firstMatched : lastMatched;
            JSONArray highEndpoint = forward ? lastMatched : firstMatched;
            if (tracePointDistanceMetres(original.getJSONArray(low), lowEndpoint) > 250
                    || tracePointDistanceMetres(original.getJSONArray(high), highEndpoint) > 250)
                return extensions;
            if (low > 0) {
                JSONArray prefix = new JSONArray();
                for (int index = 0; index <= low; index++) prefix.put(original.getJSONArray(index));
                prefix.put(lowEndpoint);
                if (prefix.length() >= 2) extensions.add(prefix);
            }
            if (high < original.length() - 1) {
                JSONArray suffix = new JSONArray().put(highEndpoint);
                for (int index = high + 1; index < original.length(); index++)
                    suffix.put(original.getJSONArray(index));
                if (suffix.length() >= 2) extensions.add(suffix);
            }
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
        return extensions;
    }

    private static int nearestTracePoint(JSONArray trace, JSONArray point) {
        if (point == null || point.length() < 2) return -1;
        int nearest = -1;
        double nearestDistance = Double.MAX_VALUE;
        for (int index = 0; index < trace.length(); index++) {
            JSONArray candidate = trace.optJSONArray(index);
            double distance = tracePointDistanceMetres(candidate, point);
            if (distance < nearestDistance) { nearestDistance = distance; nearest = index; }
        }
        return nearest;
    }

    private static double tracePointDistanceMetres(JSONArray a, JSONArray b) {
        if (a == null || b == null || a.length() < 2 || b.length() < 2)
            return Double.MAX_VALUE;
        double latitude = Math.toRadians((a.optDouble(1) + b.optDouble(1)) / 2.0);
        double dx = (a.optDouble(0) - b.optDouble(0)) * 111320.0 * Math.cos(latitude);
        double dy = (a.optDouble(1) - b.optDouble(1)) * 110540.0;
        return Math.hypot(dx, dy);
    }

    private void readSavedCorrections() {
        JSONObject corrections = journey.optJSONObject("journey_corrections");
        JSONArray removed = corrections == null
                ? null : corrections.optJSONArray("removed_matched_segments");
        if (removed == null) return;
        for (int index = 0; index < removed.length(); index++) {
            int edge = removed.optInt(index, -1);
            if (edge >= 0) removedEdges.add(edge);
        }
    }

    private List<JSONArray> matchedRouteSegments(JSONObject result) {
        List<JSONArray> segments = new ArrayList<>();
        if (result == null) return segments;
        JSONObject geojson = result.optJSONObject("geojson");
        JSONArray features = geojson == null ? null : geojson.optJSONArray("features");
        if (features == null) return segments;
        for (int index = 0; index < features.length(); index++) {
            JSONObject feature = features.optJSONObject(index);
            JSONObject geometry = feature == null ? null : feature.optJSONObject("geometry");
            if (geometry == null) continue;
            String type = geometry.optString("type", "");
            JSONArray coordinates = geometry.optJSONArray("coordinates");
            if ("LineString".equals(type) && coordinates != null && coordinates.length() >= 2) {
                segments.add(coordinates);
            } else if ("MultiLineString".equals(type) && coordinates != null) {
                for (int part = 0; part < coordinates.length(); part++) {
                    JSONArray segment = coordinates.optJSONArray(part);
                    if (segment != null && segment.length() >= 2) segments.add(segment);
                }
            }
        }
        return segments;
    }

    private TextView text(String value, float size, int colour, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(colour);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private android.graphics.drawable.GradientDrawable roundRect(
            int fill, int stroke, float radius) {
        android.graphics.drawable.GradientDrawable drawable =
                new android.graphics.drawable.GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
