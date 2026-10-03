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
    private boolean removeMode = true;
    private int selectedEdge = -1;

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
                "Tap a route section to select it. Restore can also bring back the latest removal.",
                14, 0xFFD3DCED, false);
        instructions.setPadding(0, dp(6), 0, dp(4));
        header.addView(instructions);
        root.addView(header);

        JSONObject geometry = journey.optJSONObject("route_geometry");
        JSONArray original = geometry == null ? null : geometry.optJSONArray("coordinates");
        routeView = new RoutePreviewView(this, original, routes, removedEdges,
                this::onRouteEdgeTap);
        routeView.setRouteExtensions(unmatchedEndpointTrace(original, routes));
        root.addView(routeView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(side, dp(10), side, dp(12));
        footer.setBackgroundColor(0xFF10275D);

        status = text("", 13, 0xFF67D5CC, false);
        status.setPadding(0, 0, 0, dp(10));
        footer.addView(status);

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
        routeView.setRouteEditState(removedEdges, selectedEdge, !removeMode);
        updateStatus();
        updateActionButtons();
    }

    private void updateActionButtons() {
        boolean canRemove = selectedEdge >= 0 && !removedEdges.contains(selectedEdge);
        boolean canRestore = !removedEdges.isEmpty();
        boolean dirty = !removedEdges.equals(originalRemovedEdges);
        boolean canUndo = !undoStack.isEmpty();

        styleActionButton(removeButton, canRemove, removeMode);
        styleActionButton(restoreButton, canRestore, canRestore);
        styleActionButton(undoButton, canUndo, false);
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

    private void onRouteEdgeTap(int edgeIndex) {
        selectedEdge = edgeIndex;
        routeView.setRouteEditState(removedEdges, selectedEdge, !removeMode);
        updateStatus();
        updateActionButtons();
    }

    private void applySelectedChange(boolean remove) {
        removeMode = remove;
        updateModeStyles();
        if (selectedEdge < 0 && !remove && !removedEdges.isEmpty()) {
            selectedEdge = latestRemovedEdge();
        }
        if (selectedEdge < 0) {
            status.setText("Tap a route section to select it first.");
            updateActionButtons();
            return;
        }
        boolean alreadyRemoved = removedEdges.contains(selectedEdge);
        if (remove == alreadyRemoved) {
            status.setText(remove
                    ? "That section is already removed. Select another section."
                    : "That section is still part of the route. Select a removed section.");
            return;
        }
        undoStack.push(new LinkedHashSet<>(removedEdges));
        if (undoStack.size() > 30) undoStack.removeLast();
        if (remove) removedEdges.add(selectedEdge);
        else removedEdges.remove(selectedEdge);
        selectedEdge = -1;
        routeView.setRouteEditState(removedEdges, selectedEdge, !removeMode);
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
        selectedEdge = -1;
        routeView.setRouteEditState(removedEdges, selectedEdge, !removeMode);
        updateStatus();
        updateActionButtons();
    }

    private int latestRemovedEdge() {
        int latest = -1;
        for (Integer edge : removedEdges) latest = edge;
        return latest;
    }

    private void updateStatus() {
        if (selectedEdge >= 0) {
            boolean removed = removedEdges.contains(selectedEdge);
            status.setText(removed
                    ? "Removed section selected · press RESTORE to bring it back."
                    : "Route section selected in red · press REMOVE to apply.");
            return;
        }
        status.setText(removedEdges.size() + " route section"
                + (removedEdges.size() == 1 ? "" : "s")
                + " removed · tap a section or press RESTORE to bring back the latest removal.");
    }

    private void saveChanges() {
        if (removedEdges.equals(originalRemovedEdges)) return;
        try {
            JSONObject corrections = journey.optJSONObject("journey_corrections");
            if (corrections == null) corrections = new JSONObject();
            List<Integer> ordered = new ArrayList<>(removedEdges);
            Collections.sort(ordered);
            JSONArray saved = new JSONArray();
            for (Integer edge : ordered) saved.put(edge);
            corrections.put("removed_matched_segments", saved);
            journey.put("journey_corrections", corrections);
            journey.put("revision", journey.optInt("revision", 1) + 1);
            JourneyStore.save(this, journey);
            setResult(RESULT_OK);
            finish();
        } catch (Exception error) {
            Toast.makeText(this, "Changes could not be saved", Toast.LENGTH_LONG).show();
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
