package com.roadprints.capture;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Entry point for app utilities that do not belong on the main capture screen. */
public class UtilitiesActivity extends Activity {
    private static final int NAVY = 0xFF0B1C50;
    private static final int CARD = 0xFF233B78;
    private static final int TEAL = 0xFF67D5CC;
    private static final int MUTED = 0xFFD3DCED;

    private static final int REQUEST_DIAGNOSTICS = 612;
    private final ExecutorService exportWorker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean exportCancelled = new AtomicBoolean();
    private boolean exporting;
    private AlertDialog exportDialog;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(NAVY);
        root.setPadding(dp(24), dp(24), dp(24), dp(28));
        LinearLayout header = RoadprintsHeader.create(this);
        header.setPadding(0, 0, 0, dp(26));
        root.addView(header);
        TextView eyebrow = text("ROADPRINTS", 13, TEAL, true);
        root.addView(eyebrow);
        root.addView(text("Utilities", 32, Color.WHITE, true));
        TextView copy = text("Manage tracking, permissions and your saved data.", 16, MUTED, false);
        copy.setPadding(0, dp(8), 0, dp(20));
        root.addView(copy);

        add(root, "Permissions", "Manage location access and notifications",
                PermissionsActivity.class);
        add(root, "Data management", "Load Timeline data and manage saved journeys",
                DataManagementActivity.class);
        add(root, "Tracking settings", "Journey type, automatic tracking and manual capture",
                TrackingSettingsActivity.class);
        add(root, "Settings", "Distance units and display preferences", SettingsActivity.class);
        add(root, "What's new", "See release notes and contact Roadprints", WhatsNewActivity.class);
        add(root, "Privacy and diagnostics", "Choose optional analytics and diagnostic sharing", MeasurementSettingsActivity.class);
        LinearLayout mapGeometry = menuItem("Export Map geometry",
                "Inspect the cumulative Map and save visible travelled coordinates privately");
        mapGeometry.setOnClickListener(v -> startActivity(new Intent(this, MapActivity.class)
                .putExtra(MapActivity.EXTRA_GEOMETRY_EXPORT_MODE, true)));
        root.addView(mapGeometry, params());
        LinearLayout diagnostics = menuItem("Export all journey diagnostics",
                "Save a private ZIP of your saved journeys for Map Laboratory");
        diagnostics.setOnClickListener(v -> chooseDiagnosticsFile());
        root.addView(diagnostics, params());
        LinearLayout debug = menuItem("Debug", "View or copy diagnostic reports");
        debug.setOnClickListener(v -> startActivity(new Intent(this, DebugActivity.class)));
        root.addView(debug, params());
        RoadprintsHeader.installUtilityPage(this, root, "BACK TO ROADPRINTS", this::finish);
    }
    private void chooseDiagnosticsFile() {
        if (exporting) return;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        intent.putExtra(Intent.EXTRA_TITLE, "roadprints-journey-diagnostics-" + LocalDate.now() + ".zip");
        startActivityForResult(intent, REQUEST_DIAGNOSTICS);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == REQUEST_DIAGNOSTICS && result == RESULT_OK && data != null && data.getData() != null)
            exportDiagnostics(data.getData());
    }

    private void exportDiagnostics(Uri destination) {
        exporting = true;
        exportCancelled.set(false);
        exportDialog = new AlertDialog.Builder(this)
                .setTitle("Export all journey diagnostics")
                .setMessage("Opening saved journeys…")
                .setNegativeButton("Cancel", null)
                .setCancelable(false)
                .show();
        exportDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(view -> {
            exportCancelled.set(true);
            view.setEnabled(false);
            exportDialog.setMessage("Cancelling export…");
        });
        ContentResolver resolver = getContentResolver();
        exportWorker.execute(() -> {
            String message;
            try (OutputStream output = resolver.openOutputStream(destination, "wt")) {
                if (output == null) throw new IOException("The selected file could not be opened.");
                int count = BulkJourneyDiagnostics.write(getApplicationContext(), output,
                        exportCancelled::get, (completed, total) -> {
                            if (completed == 0 || completed == total || completed % 10 == 0)
                                runOnUiThread(() -> {
                                    if (!exportCancelled.get() && exportDialog != null)
                                        exportDialog.setMessage("Exporting journeys… " + completed + " of " + total);
                                });
                        });
                if (exportCancelled.get()) throw new IOException("Export cancelled.");
                message = "Saved " + count + " journeys. The ZIP contains private location history.";
            } catch (IOException | RuntimeException error) {
                boolean removed = deleteIncompleteDiagnostics(resolver, destination);
                String reason = exportCancelled.get() ? "Export cancelled." : error.getMessage();
                message = (reason == null ? "Could not save the diagnostics ZIP." : reason)
                        + (removed ? "" : " The selected file may be incomplete. Delete it and choose a local folder when retrying.");
            }
            String result = message;
            runOnUiThread(() -> {
                exporting = false;
                if (isDestroyed() || isFinishing()) return;
                exportDialog.dismiss();
                exportDialog = null;
                new RoadprintsDialog.Builder(this)
                        .setTitle("Journey diagnostics")
                        .setMessage(result)
                        .setPositiveButton("Close", null)
                        .show();
            });
        });
    }

    private static boolean deleteIncompleteDiagnostics(ContentResolver resolver, Uri destination) {
        try {
            return DocumentsContract.deleteDocument(resolver, destination);
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override protected void onDestroy() {
        exportCancelled.set(true);
        exportWorker.shutdown();
        if (exportDialog != null) exportDialog.dismiss();
        super.onDestroy();
    }

    private void add(LinearLayout root, String title, String detail, Class<?> target) {
        LinearLayout item = menuItem(title, detail);
        item.setOnClickListener(v -> startActivity(new Intent(this, target)));
        root.addView(item, params());
    }
    private LinearLayout.LayoutParams params() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(12);
        return p;
    }
    private LinearLayout menuItem(String title, String detail) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(dp(18), dp(15), dp(18), dp(15));
        item.setMinimumHeight(dp(82));
        item.setBackground(rounded(CARD));
        item.setClickable(true);
        item.setFocusable(true);
        TextView heading = text(title, 17, Color.WHITE, true);
        TextView description = text(detail, 14, MUTED, false);
        description.setPadding(0, dp(4), 0, 0);
        item.addView(heading);
        item.addView(description);
        item.setContentDescription(title + ". " + detail);
        return item;
    }
    private android.graphics.drawable.GradientDrawable rounded(int color) {
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setColor(color); shape.setCornerRadius(dp(16)); shape.setStroke(dp(1), 0xFF46649E);
        return shape;
    }
    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
