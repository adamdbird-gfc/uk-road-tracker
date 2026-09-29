package com.roadprints.capture;

import android.app.Activity;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class JourneyMapEditorActivity extends Activity {
    private static final String EXTRA_JOURNEY = "journey_json";
    private final Set<Integer> removedEdges = new HashSet<>();
    private final Deque<Set<Integer>> undoStack = new ArrayDeque<>();
    private JSONObject journey;
    private RoutePreviewView routeView;
    private TextView status;
    private TextView removeButton;
    private TextView restoreButton;
    private boolean removeMode = true;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window window = getWindow();
        window.setStatusBarColor(0xFF0B1C50);
        window.setNavigationBarColor(0xFF10275D);

        try {
            journey = new JSONObject(getIntent().getStringExtra(EXTRA_JOURNEY));
        } catch (Exception error) {
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
        TextView cancel = text("CANCEL", 13, 0xFFF7C450, true);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(dp(10), dp(10), 0, dp(10));
        cancel.setOnClickListener(v -> finish());
        titleRow.addView(cancel);
        header.addView(titleRow);

        TextView instructions = text(
                "Tap a blue route section to remove it. Use Restore to bring it back.",
                14, 0xFFD3DCED, false);
        instructions.setPadding(0, dp(6), 0, dp(4));
        header.addView(instructions);
        root.addView(header);

        JSONObject geometry = journey.optJSONObject("route_geometry");
        JSONArray original = geometry == null ? null : geometry.optJSONArray("coordinates");
        routeView = new RoutePreviewView(this, original, routes, removedEdges,
                this::onRouteEdgeTap);
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
        TextView undo = controlButton("UNDO", false);
        undo.setOnClickListener(v -> undo());
        LinearLayout.LayoutParams controlParams = new LinearLayout.LayoutParams(
                0, dp(48), 1);
        controlParams.setMargins(0, 0, dp(8), 0);
        controls.addView(removeButton, controlParams);
        controls.addView(restoreButton, controlParams);
        LinearLayout.LayoutParams undoParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        controls.addView(undo, undoParams);
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
    }

    private TextView controlButton(String label, boolean selected) {
        TextView button = text(label, 12, selected ? 0xFF0B1C50 : Color.WHITE, true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(roundRect(selected ? 0xFFF7C450 : 0xFF233B78,
                selected ? 0xFFF7C450 : 0xFF46649E, dp(10)));
        button.setOnClickListener(v -> {
            removeMode = "REMOVE".equals(label);
            updateModeStyles();
        });
        return button;
    }

    private void updateModeStyles() {
        removeButton.setBackground(roundRect(removeMode ? 0xFFF7C450 : 0xFF233B78,
                removeMode ? 0xFFF7C450 : 0xFF46649E, dp(10)));
        removeButton.setTextColor(removeMode ? 0xFF0B1C50 : Color.WHITE);
        restoreButton.setBackground(roundRect(removeMode ? 0xFF233B78 : 0xFFF7C450,
                removeMode ? 0xFF46649E : 0xFFF7C450, dp(10)));
        restoreButton.setTextColor(removeMode ? Color.WHITE : 0xFF0B1C50);
        status.setText(removeMode
                ? "Remove mode · tap the section that does not belong."
                : "Restore mode · tap a removed section to put it back.");
    }

    private void onRouteEdgeTap(int edgeIndex) {
        boolean wasRemoved = removedEdges.contains(edgeIndex);
        if (removeMode == wasRemoved) {
            status.setText(removeMode
                    ? "That section is already removed."
                    : "That section is still part of the route.");
            return;
        }
        undoStack.push(new HashSet<>(removedEdges));
        if (undoStack.size() > 30) undoStack.removeLast();
        if (removeMode) removedEdges.add(edgeIndex);
        else removedEdges.remove(edgeIndex);
        routeView.invalidate();
        updateStatus();
    }

    private void undo() {
        if (undoStack.isEmpty()) {
            status.setText("There are no route edits to undo.");
            return;
        }
        removedEdges.clear();
        removedEdges.addAll(undoStack.pop());
        routeView.invalidate();
        updateStatus();
    }

    private void updateStatus() {
        String mode = removeMode ? "Remove" : "Restore";
        status.setText(mode + " mode · " + removedEdges.size()
                + " route section" + (removedEdges.size() == 1 ? "" : "s") + " removed.");
    }

    private void saveChanges() {
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

    private void saveClicked(View ignored) {
        saveChanges();
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
