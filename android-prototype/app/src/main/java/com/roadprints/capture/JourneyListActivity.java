package com.roadprints.capture;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class JourneyListActivity extends Activity {
    private static final String[] FILTER_LABELS = {
            "All journeys", "Driving", "Walking", "Bus", "Train", "Cycling", "Plane", "Ferry"
    };
    private static final String[] FILTER_VALUES = {
            "all", "driving", "walking", "bus", "train", "cycling", "plane", "ferry"
    };

    private LinearLayout journeyList;
    private TextView count;
    private List<JSONObject> journeys;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildScreen();
        journeys = JourneyStore.all(this);
        render("all");
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 42, 28, 24);
        root.setBackgroundColor(0xFFF6F9FC);

        TextView title = new TextView(this);
        title.setText("Journeys");
        title.setTextSize(30);
        title.setTextColor(0xFF0A2B43);

        TextView subtitle = new TextView(this);
        subtitle.setText("Your travel record");
        subtitle.setTextSize(16);
        subtitle.setTextColor(0xFF52677D);
        subtitle.setPadding(0, 4, 0, 18);

        count = new TextView(this);
        count.setTextSize(16);
        count.setTextColor(0xFF52677D);

        Spinner filter = new Spinner(this);
        filter.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, FILTER_LABELS));
        filter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(
                    android.widget.AdapterView<?> parent, View view, int position, long id) {
                render(FILTER_VALUES[position]);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        journeyList = new LinearLayout(this);
        journeyList.setOrientation(LinearLayout.VERTICAL);
        journeyList.setPadding(0, 16, 0, 0);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(journeyList);

        Button close = new Button(this);
        close.setText("Back to capture");
        close.setOnClickListener(v -> finish());

        root.addView(title);
        root.addView(subtitle);
        root.addView(count);
        root.addView(filter);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        root.addView(close);
        setContentView(root);
    }

    private void render(String filter) {
        if (journeyList == null || journeys == null) return;
        journeyList.removeAllViews();
        int visible = 0;
        for (JSONObject journey : journeys) {
            if (!"all".equals(filter)
                    && !filter.equals(journey.optString("mode", "unknown"))) continue;
            journeyList.addView(createCard(journey));
            visible++;
        }
        count.setText(visible + " saved " + (visible == 1 ? "journey" : "journeys"));
        if (visible == 0) {
            TextView empty = new TextView(this);
            empty.setText("No journeys match this filter.");
            empty.setTextSize(16);
            empty.setTextColor(0xFF52677D);
            empty.setPadding(0, 28, 0, 28);
            journeyList.addView(empty);
        }
    }

    private View createCard(JSONObject journey) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(20, 18, 20, 18);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFFFFFFFF);
        background.setCornerRadius(22);
        background.setStroke(1, 0xFFD9E2EA);
        card.setBackground(background);
        card.setElevation(3);

        String mode = journey.optString("mode", "unknown");
        double metres = journey.optDouble("distance_meters", 0);
        JSONObject geometry = journey.optJSONObject("route_geometry");
        JSONArray coordinates = geometry == null
                ? null : geometry.optJSONArray("coordinates");
        int points = coordinates == null ? 0 : coordinates.length();

        TextView heading = new TextView(this);
        heading.setText(displayMode(mode) + "  •  " + displayTime(journey.optString("started_at")));
        heading.setTextSize(18);
        heading.setTextColor(0xFF0A2B43);

        TextView summary = new TextView(this);
        summary.setText(String.format(
                "%.0f m  •  %s  •  %d GPS points",
                metres, journeyDuration(journey), points));
        summary.setTextSize(15);
        summary.setTextColor(0xFF52677D);
        summary.setPadding(0, 8, 0, 0);

        TextView hint = new TextView(this);
        hint.setText("Tap to inspect route");
        hint.setTextSize(13);
        hint.setTextColor(0xFF1C69A2);
        hint.setPadding(0, 12, 0, 0);

        card.addView(heading);
        card.addView(summary);
        card.addView(hint);
        card.setOnClickListener(v -> showDetails(journey));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 14);
        card.setLayoutParams(params);
        return card;
    }

    private void showDetails(JSONObject journey) {
        JSONObject geometry = journey.optJSONObject("route_geometry");
        JSONArray coordinates = geometry == null
                ? null : geometry.optJSONArray("coordinates");
        RoutePreviewView preview = new RoutePreviewView(this, coordinates);
        preview.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 360));

        double metres = journey.optDouble("distance_meters", 0);
        int points = coordinates == null ? 0 : coordinates.length();
        TextView message = new TextView(this);
        message.setText(String.format(
                "Start: %s\nEnd: %s\nDuration: %s\n%s • %.0f m • %d GPS points",
                displayTime(journey.optString("started_at")),
                displayTime(journey.optString("ended_at")),
                journeyDuration(journey),
                displayMode(journey.optString("mode", "unknown")),
                metres, points));
        message.setTextSize(16);
        message.setPadding(24, 16, 24, 16);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.addView(preview);
        container.addView(message);

        Button share = new Button(this);
        share.setText("Share journey JSON");
        share.setOnClickListener(v -> shareJourney(journey));
        container.addView(share);

        new AlertDialog.Builder(this)
                .setTitle("Journey details")
                .setView(container)
                .setPositiveButton("Close", null)
                .show();
    }

    private void shareJourney(JSONObject journey) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("application/json");
        share.putExtra(Intent.EXTRA_SUBJECT, "Roadprints journey");
        share.putExtra(Intent.EXTRA_TEXT, journey.toString());
        startActivity(Intent.createChooser(share, "Share journey data"));
    }

    private String displayMode(String value) {
        if ("driving".equals(value)) return "Driving";
        if ("walking".equals(value)) return "Walking";
        if ("cycling".equals(value)) return "Cycling";
        if ("bus".equals(value)) return "Bus";
        if ("train".equals(value)) return "Train";
        if ("plane".equals(value)) return "Plane";
        if ("ferry".equals(value)) return "Ferry";
        return "Unknown";
    }

    private String displayTime(String value) {
        try {
            return DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.parse(value));
        } catch (Exception ignored) {
            return value.replace("T", " ").replace("Z", "");
        }
    }

    private String journeyDuration(JSONObject journey) {
        try {
            long seconds = Math.max(0, Duration.between(
                    Instant.parse(journey.optString("started_at")),
                    Instant.parse(journey.optString("ended_at"))).getSeconds());
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            long remaining = seconds % 60;
            if (hours > 0) return String.format("%dh %02dm", hours, minutes);
            if (minutes > 0) return String.format("%dm %02ds", minutes, remaining);
            return String.format("%ds", remaining);
        } catch (Exception ignored) {
            return "Unknown duration";
        }
    }
}
