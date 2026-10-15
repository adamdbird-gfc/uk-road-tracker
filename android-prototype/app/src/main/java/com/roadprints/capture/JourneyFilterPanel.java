package com.roadprints.capture;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import org.json.JSONObject;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static com.roadprints.capture.JourneyFilters.*;
final class JourneyFilterPanel {
    private final Activity activity;
    private final List<JSONObject> journeys;
    private final int activeFilter;
    private final String activeJourneyStatusFilter;
    private final JourneyDateFilter activeDateFilter;
    private final boolean map;
    private final java.util.function.Consumer<JourneyFilters> onApply;
    JourneyFilterPanel(Activity activity, List<JSONObject> journeys, JourneyFilters filters,
            boolean map, java.util.function.Consumer<JourneyFilters> onApply) {
        this.activity=activity; this.journeys=journeys; this.activeFilter=filters.mode;
        this.activeJourneyStatusFilter=filters.status; this.activeDateFilter=filters.date;
        this.map=map; this.onApply=onApply;
    }
    private long journeyStartMillis(JSONObject journey) {
        try { return Instant.parse(journey.optString("started_at")).toEpochMilli(); }
        catch (Exception ignored) { return Long.MIN_VALUE; }
    }
    private int dp(float value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    void show() {
        final int[] mode = {activeFilter};
        final String[] status = {activeJourneyStatusFilter};
        final JourneyDateFilter[] date = {activeDateFilter};
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(18), dp(18), dp(18));
        panel.setBackground(roundRect(0xFF0B1C50, 0xFF46649E, dp(18)));
        TextView title = new TextView(activity);
        title.setText(map ? "Filter map journeys" : "Filter journeys"); title.setTextSize(24); title.setTextColor(Color.WHITE);
        panel.addView(title);
        TextView hint = new TextView(activity);
        hint.setText(map ? "Combine transport, status and start date. Only journeys with a mapped route can appear." : "Combine transport, status and journey start date."); hint.setTextColor(0xFFB9C5D8);
        hint.setPadding(0, dp(8), 0, dp(12)); panel.addView(hint);
        ScrollView scroll = new ScrollView(activity);
        LinearLayout choices = new LinearLayout(activity); choices.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(choices);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        Button apply = styledModalButton("SHOW JOURNEYS", 0xFFF7C450, 0xFF0B1C50);
        apply.setTag("journey_filter_apply");
        TextView error = new TextView(activity); error.setTextColor(0xFFFF9393); panel.addView(error);
        Runnable update = () -> {
            int total = 0;
            if (journeys != null) for (JSONObject journey : journeys) {
                if ( matchesFilter(journey, mode[0]) && matchesJourneyStatusFilter(journey, status[0])
                        && date[0].matches(journey, java.time.LocalDate.now(), ZoneId.systemDefault())) total++;
            }
            apply.setText(String.format(java.util.Locale.UK, map ? "APPLY TO MAP · %,d JOURNEYS" : "SHOW %,d JOURNEYS", total));
            apply.setEnabled(date[0].valid());
            error.setText(date[0].valid() ? "" : "Choose an end date on or after the start date.");
            error.setVisibility(date[0].valid() ? View.GONE : View.VISIBLE);
        };
        // Availability comes from the archive in this view, rather than another active
        // filter: choosing a narrow date must not hide the way to broaden transport.
        List<Integer> transportOptions = availableTransportOptions();
        List<Integer> statusOptions = availableStatusOptions();
        List<Integer> dateOptions = availableDateOptions();
        if (!transportOptions.contains(mode[0])) mode[0] = 0;
        int statusIndex = java.util.Arrays.asList(STATUS_VALUES).indexOf(status[0]);
        if (!statusOptions.contains(statusIndex)) status[0] = "all";
        addFilterHeading(choices, "Transport");
        Spinner transport = filterSpinner(choices, optionLabels(FILTER_LABELS, transportOptions), "Transport");
        transport.setSelection(transportOptions.indexOf(mode[0]));
        transport.setOnItemSelectedListener(selection(position -> { mode[0] = transportOptions.get(position); update.run(); }));
        addFilterHeading(choices, "Matching status");
        Spinner statuses = filterSpinner(choices, optionLabels(STATUS_LABELS, statusOptions), "Matching status");
        statuses.setSelection(statusOptions.indexOf(java.util.Arrays.asList(STATUS_VALUES).indexOf(status[0])));
        statuses.setOnItemSelectedListener(selection(position -> { status[0] = STATUS_VALUES[statusOptions.get(position)]; update.run(); }));
        addFilterHeading(choices, "Date");
        Spinner dates = filterSpinner(choices, optionLabels(JourneyDateFilter.LABELS, dateOptions), "Date");
        int dateIndex = java.util.Arrays.asList(JourneyDateFilter.VALUES).indexOf(date[0].preset);
        if (!dateOptions.contains(dateIndex)) date[0] = JourneyDateFilter.all();
        dates.setSelection(Math.max(0, dateOptions.indexOf(java.util.Arrays.asList(JourneyDateFilter.VALUES).indexOf(date[0].preset))));
        LinearLayout range = new LinearLayout(activity); range.setOrientation(LinearLayout.VERTICAL); choices.addView(range);
        Button from = styledModalButton("FROM", 0xFF233B78, Color.WHITE);
        Button to = styledModalButton("TO", 0xFF233B78, Color.WHITE);
        addFilterButton(range, from); addFilterButton(range, to);
        Runnable rangeLabels = () -> {
            range.setVisibility("custom".equals(date[0].preset) ? View.VISIBLE : View.GONE);
            from.setText("From: " + (date[0].from == null ? "Choose date" : date[0].from.format(DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.UK))));
            to.setText("To: " + (date[0].to == null ? "Choose date" : date[0].to.format(DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.UK))));
        };
        from.setOnClickListener(v -> pickFilterDate(date[0].from, chosen -> { date[0] = new JourneyDateFilter("custom", chosen, date[0].to); rangeLabels.run(); update.run(); }));
        to.setOnClickListener(v -> pickFilterDate(date[0].to, chosen -> { date[0] = new JourneyDateFilter("custom", date[0].from, chosen); rangeLabels.run(); update.run(); }));
        dates.setOnItemSelectedListener(selection(position -> {
            date[0] = new JourneyDateFilter(JourneyDateFilter.VALUES[dateOptions.get(position)], date[0].from, date[0].to);
            if ("custom".equals(date[0].preset) && date[0].from == null) date[0] = new JourneyDateFilter("custom", latestJourneyDate(), latestJourneyDate());
            rangeLabels.run(); update.run();
        }));
        Button reset = styledModalButton("RESET FILTERS", 0xFF233B78, Color.WHITE);
        reset.setOnClickListener(v -> { mode[0] = 0; status[0] = "all"; date[0] = JourneyDateFilter.all(); transport.setSelection(0); statuses.setSelection(0); dates.setSelection(0); rangeLabels.run(); update.run(); });
        reset.setTag("journey_filter_reset");
        addFilterButton(panel, reset); addFilterButton(panel, apply);
        Button close = styledModalButton("CANCEL", 0xFF0B1C50, 0xFFF7C450); addFilterButton(panel, close);
        AlertDialog dialog = new RoadprintsDialog.Builder(activity).setView(panel).create();
        apply.setOnClickListener(v -> { if (!date[0].valid()) return; onApply.accept(new JourneyFilters(mode[0], status[0], date[0])); dialog.dismiss(); });
        close.setOnClickListener(v -> dialog.dismiss());
        rangeLabels.run(); update.run(); dialog.show();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.getWindow().setLayout((int)(activity.getResources().getDisplayMetrics().widthPixels * .94), (int)(activity.getResources().getDisplayMetrics().heightPixels * .82));
    }

    private void addFilterHeading(LinearLayout parent, String label) {
        TextView heading = new TextView(activity); heading.setText(label); heading.setTextColor(0xFF67D5CC); heading.setTextSize(16);
        heading.setPadding(0, dp(16), 0, dp(8)); parent.addView(heading);
    }

    private void addFilterButton(LinearLayout parent, Button button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(52));
        params.topMargin = dp(12);
        parent.addView(button, params);
    }

    private List<Integer> availableTransportOptions() {
        List<Integer> available = new ArrayList<>(); available.add(0);
        if (journeys != null) for (int i = 1; i < FILTER_VALUES.length; i++) {
            for (JSONObject journey : journeys) if (matchesFilter(journey, i)) { available.add(i); break; }
        }
        return available;
    }

    private List<Integer> availableStatusOptions() {
        List<Integer> available = new ArrayList<>(); available.add(0);
        if (journeys != null) for (int i = 1; i < STATUS_VALUES.length; i++) {
            for (JSONObject journey : journeys) if (matchesJourneyStatusFilter(journey, STATUS_VALUES[i])) { available.add(i); break; }
        }
        return available;
    }

    private List<Integer> availableDateOptions() {
        List<Integer> available = new ArrayList<>(); available.add(0);
        java.time.LocalDate today = java.time.LocalDate.now(); ZoneId zone = ZoneId.systemDefault();
        if (journeys != null) for (int i = 1; i < JourneyDateFilter.VALUES.length - 1; i++) {
            JourneyDateFilter candidate = new JourneyDateFilter(JourneyDateFilter.VALUES[i], null, null);
            for (JSONObject journey : journeys) if (candidate.matches(journey, today, zone)) { available.add(i); break; }
        }
        // Keep the custom range action and an explicitly saved range/preset accessible.
        int selected = java.util.Arrays.asList(JourneyDateFilter.VALUES).indexOf(activeDateFilter.preset);
        if (selected > 0 && selected < 4 && !available.contains(selected)) available.add(selected);
        available.add(4);
        return available;
    }

    private java.time.LocalDate latestJourneyDate() {
        long latest = Long.MIN_VALUE;
        if (journeys != null) for (JSONObject journey : journeys) latest = Math.max(latest, journeyStartMillis(journey));
        return latest == Long.MIN_VALUE ? java.time.LocalDate.now() : Instant.ofEpochMilli(latest).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private String[] optionLabels(String[] labels, List<Integer> indices) {
        String[] values = new String[indices.size()];
        for (int i = 0; i < indices.size(); i++) values[i] = labels[indices.get(i)];
        return values;
    }

    private Spinner filterSpinner(LinearLayout parent, String[] labels, String description) {
        Spinner spinner = new Spinner(activity, Spinner.MODE_DROPDOWN);
        spinner.setContentDescription(description);
        spinner.setTag("journey_filter_" + description.toLowerCase(java.util.Locale.ROOT).replace(' ', '_'));
        spinner.setAdapter(new ArrayAdapter<String>(activity, android.R.layout.simple_spinner_dropdown_item, labels) {
            @Override public View getView(int position, View convert, android.view.ViewGroup group) {
                LinearLayout row = new LinearLayout(activity); row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(16), 0, dp(16), 0);
                TextView label = filterOptionText(labels[position]);
                row.addView(label, new LinearLayout.LayoutParams(0, -1, 1));
                ImageView arrow = RoadprintsIcons.image(activity, R.drawable.ic_roadprints_chevron_down, 0xFFB9C5D8, null);
                arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                row.addView(arrow, new LinearLayout.LayoutParams(dp(20), dp(20)));
                return row;
            }
            @Override public View getDropDownView(int position, View convert, android.view.ViewGroup group) {
                TextView label = filterOptionText(labels[position]); label.setPadding(dp(16), dp(12), dp(16), dp(12)); return label;
            }
        });
        spinner.setPopupBackgroundDrawable(roundRect(0xFF233B78, 0xFF46649E, dp(12)));
        spinner.setBackground(roundRect(0xFF233B78, 0xFF46649E, dp(12)));
        parent.addView(spinner, new LinearLayout.LayoutParams(-1, dp(48))); return spinner;
    }

    private TextView filterOptionText(String value) {
        TextView label = new TextView(activity); label.setText(value); label.setTextColor(Color.WHITE);
        label.setTextSize(16); label.setGravity(Gravity.CENTER_VERTICAL); label.setMinHeight(dp(48)); return label;
    }

    private AdapterView.OnItemSelectedListener selection(java.util.function.IntConsumer listener) {
        return new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { listener.accept(position); }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        };
    }

    private void pickFilterDate(java.time.LocalDate current, java.util.function.Consumer<java.time.LocalDate> selected) {
        java.time.LocalDate initial = current == null ? java.time.LocalDate.now() : current;
        android.app.DatePickerDialog dialog = new android.app.DatePickerDialog(activity, R.style.RoadprintsDatePickerDialog,
                (picker, year, month, day) -> selected.accept(java.time.LocalDate.of(year, month + 1, day)),
                initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth());
        dialog.show();
        android.view.ViewTreeObserver.OnGlobalLayoutListener headerStyle = () -> styleCalendarYear(dialog.getDatePicker());
        dialog.getDatePicker().getViewTreeObserver().addOnGlobalLayoutListener(headerStyle);
        dialog.setOnDismissListener(closed -> dialog.getDatePicker().getViewTreeObserver().removeOnGlobalLayoutListener(headerStyle));
        styleCalendarYear(dialog.getDatePicker());
        styleCalendarButton(dialog.getButton(AlertDialog.BUTTON_POSITIVE), 0xFFF7C450, 0xFF0B1C50);
        styleCalendarButton(dialog.getButton(AlertDialog.BUTTON_NEGATIVE), 0xFF233B78, Color.WHITE);
    }

    private void styleCalendarYear(View view) {
        if (view instanceof TextView && ((TextView)view).getText().toString().matches("[12][0-9]{3}")) {
            ((TextView)view).setTextColor(Color.WHITE); view.setAlpha(1f);
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup)view;
            for (int i = 0; i < group.getChildCount(); i++) styleCalendarYear(group.getChildAt(i));
        }
    }

    private void styleCalendarButton(Button button, int background, int foreground) {
        button.setTextColor(foreground); button.setTextSize(14); button.setMinHeight(dp(48));
        button.setStateListAnimator(null); button.setElevation(0);
        if (button.getParent() instanceof android.view.ViewGroup) {
            android.view.ViewGroup parent = (android.view.ViewGroup)button.getParent();
            parent.setClipChildren(false); parent.setClipToPadding(false);
            parent.setPadding(parent.getPaddingLeft(), dp(12), parent.getPaddingRight(), dp(12));
            android.view.ViewGroup.LayoutParams row = parent.getLayoutParams();
            if (row != null) { row.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT; parent.setLayoutParams(row); }
        }
        button.setPadding(dp(16), 0, dp(16), 0); button.setBackground(roundRect(background, background, dp(12)));
        android.view.ViewGroup.LayoutParams original = button.getLayoutParams();
        if (original instanceof android.view.ViewGroup.MarginLayoutParams) {
            android.view.ViewGroup.MarginLayoutParams margins = (android.view.ViewGroup.MarginLayoutParams)original;
            margins.height = dp(48);
            margins.setMargins(dp(6), 0, dp(6), 0); button.setLayoutParams(margins);
        }
    }

    private GradientDrawable roundRect(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        drawable.setStroke(1, stroke);
        return drawable;
    }
    private Button styledModalButton(String text, int background, int foreground) {
        Button button = new Button(activity);
        button.setText(text);
        button.setTextSize(13);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        button.setTextColor(foreground);
        button.setAllCaps(false);
        button.setMinHeight(dp(52));
        button.setPadding(dp(16), 0, dp(16), 0);
        button.setBackground(roundRect(background, background, dp(12)));
        return button;
    }
}
