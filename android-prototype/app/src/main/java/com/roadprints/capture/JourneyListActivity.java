package com.roadprints.capture;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
            "All", "🚗 Driving", "👟 On foot", "🚌 Bus",
            "🚆 Train", "🚲 Cycling", "✈️ Flight", "⛴ Ferry"
    };
    private static final String[] FILTER_VALUES = {
            "all", "driving", "walking", "bus", "train", "cycling", "plane", "ferry"
    };

    private LinearLayout journeyList;
    private TextView count;
    private List<JSONObject> journeys;
    private int activeFilter = 0;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window window = getWindow();
        window.setStatusBarColor(0xFF0B1C50);
        window.setNavigationBarColor(0xFF10275D);
        buildScreen();
        journeys = JourneyStore.all(this);
        render();
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0B1C50);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(36, 26, 36, 12);

        TextView logo = new TextView(this);
        logo.setText("roadprints");
        logo.setTextSize(22);
        logo.setTypeface(null, android.graphics.Typeface.BOLD);
        logo.setTextColor(Color.WHITE);
        logo.setPadding(0, 0, 0, 32);

        LinearLayout headingRow = new LinearLayout(this);
        headingRow.setGravity(Gravity.CENTER_VERTICAL);
        headingRow.setPadding(0, 0, 0, 8);

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView eyebrow = new TextView(this);
        eyebrow.setText("YOUR TRAVEL RECORD");
        eyebrow.setTextSize(13);
        eyebrow.setTypeface(null, android.graphics.Typeface.BOLD);
        eyebrow.setTextColor(0xFF67D5CC);

        TextView title = new TextView(this);
        title.setText("Journeys");
        title.setTextSize(36);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(Color.WHITE);

        heading.addView(eyebrow);
        heading.addView(title);

        count = new TextView(this);
        count.setTextSize(22);
        count.setTypeface(null, android.graphics.Typeface.BOLD);
        count.setTextColor(0xFFF7C450);
        count.setGravity(Gravity.CENTER);
        count.setPadding(22, 14, 22, 14);
        count.setBackground(pill(0x332F4D91, 0x334D6CA6, 40));

        headingRow.addView(heading);
        headingRow.addView(count);

        TextView intro = new TextView(this);
        intro.setText("Choose a journey to inspect its route and correct any section that does not belong.");
        intro.setTextSize(16);
        intro.setTextColor(0xFFD3DCED);
        intro.setPadding(0, 8, 0, 20);

        HorizontalScrollView filterScroll = new HorizontalScrollView(this);
        filterScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        for (int index = 0; index < FILTER_LABELS.length; index++) {
            final int selected = index;
            Button filter = new Button(this);
            filter.setText(FILTER_LABELS[index]);
            filter.setTextSize(13);
            filter.setAllCaps(false);
            filter.setTextColor(Color.WHITE);
            filter.setPadding(18, 0, 18, 0);
            filter.setMinHeight(48);
            filter.setMinWidth(0);
            filter.setOnClickListener(v -> {
                activeFilter = selected;
                render();
            });
            filters.addView(filter, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, 52));
        }
        filterScroll.addView(filters);

        journeyList = new LinearLayout(this);
        journeyList.setOrientation(LinearLayout.VERTICAL);
        journeyList.setPadding(0, 18, 0, 18);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(journeyList);
        scroll.setFillViewport(true);

        content.addView(logo);
        content.addView(headingRow);
        content.addView(intro);
        content.addView(filterScroll);
        content.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        root.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        root.addView(buildBottomNavigation());
        setContentView(root);
    }

    private View buildBottomNavigation() {
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(8, 10, 8, 12);
        nav.setBackgroundColor(0xFF10275D);

        String[] labels = {"⌖\nMap", "▤\nJourneys", "▥\nProgress",
                "★\nAchievements", "●\nCollections"};
        for (int index = 0; index < labels.length; index++) {
            TextView item = new TextView(this);
            item.setText(labels[index]);
            item.setGravity(Gravity.CENTER);
            item.setTextSize(11);
            item.setTypeface(null, android.graphics.Typeface.BOLD);
            item.setTextColor(index == 1 ? 0xFFF7C450 : 0xFFB9C5D8);
            nav.addView(item, new LinearLayout.LayoutParams(
                    0, 58, 1));
        }
        return nav;
    }

    private void render() {
        if (journeyList == null || journeys == null) return;
        journeyList.removeAllViews();
        int visible = 0;
        for (JSONObject journey : journeys) {
            if (!"all".equals(FILTER_VALUES[activeFilter])
                    && !FILTER_VALUES[activeFilter].equals(
                    journey.optString("mode", "unknown"))) continue;
            journeyList.addView(createCard(journey));
            visible++;
        }
        count.setText(String.valueOf(visible));
        if (visible == 0) {
            TextView empty = new TextView(this);
            empty.setText("No journeys match this filter.");
            empty.setTextSize(16);
            empty.setTextColor(0xFFD3DCED);
            empty.setPadding(0, 28, 0, 28);
            journeyList.addView(empty);
        }
    }

    private View createCard(JSONObject journey) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(22, 20, 22, 20);
        card.setBackground(roundRect(0xFF233B78, 0xFF46649E, 28));
        card.setElevation(2);

        String mode = journey.optString("mode", "unknown");
        double metres = journey.optDouble("distance_meters", 0);
        JSONObject geometry = journey.optJSONObject("route_geometry");
        JSONArray coordinates = geometry == null
                ? null : geometry.optJSONArray("coordinates");
        int points = coordinates == null ? 0 : coordinates.length();

        TextView label = new TextView(this);
        label.setText(displayMode(mode).toUpperCase() + "  •  "
                + displayTime(journey.optString("started_at")).toUpperCase());
        label.setTextSize(12);
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        label.setTextColor(0xFF67D5CC);

        TextView heading = new TextView(this);
        heading.setText("Captured journey");
        heading.setTextSize(23);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setTextColor(Color.WHITE);
        heading.setPadding(0, 8, 0, 0);

        TextView summary = new TextView(this);
        summary.setText(String.format("%.1f mi  •  %s  •  %d GPS points",
                metres / 1609.344, journeyDuration(journey), points));
        summary.setTextSize(16);
        summary.setTextColor(0xFFD3DCED);
        summary.setPadding(0, 4, 0, 0);

        TextView evidence = new TextView(this);
        evidence.setText(points >= 2 ? "Route captured locally" : "Insufficient GPS evidence");
        evidence.setTextSize(14);
        evidence.setTextColor(points >= 2 ? 0xFF67D5CC : 0xFFF7C450);
        evidence.setPadding(0, 16, 0, 16);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        Button inspect = actionButton("View & refine", 0xFF102047, Color.WHITE);
        inspect.setOnClickListener(v -> showDetails(journey));
        actions.addView(inspect, new LinearLayout.LayoutParams(0, 52, 1));

        card.addView(label);
        card.addView(heading);
        card.addView(summary);
        card.addView(evidence);
        card.addView(actions);
        card.setOnClickListener(v -> showDetails(journey));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 16);
        card.setLayoutParams(params);
        return card;
    }

    private Button actionButton(String text, int background, int foreground) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        button.setTextColor(foreground);
        button.setBackground(roundRect(background, background, 18));
        return button;
    }

    private GradientDrawable roundRect(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        drawable.setStroke(1, stroke);
        return drawable;
    }

    private GradientDrawable pill(int fill, int stroke, int radius) {
        return roundRect(fill, stroke, radius);
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
        if ("walking".equals(value)) return "On foot";
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
