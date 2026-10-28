package com.roadprints.capture;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.view.ViewCompat;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.json.JSONException;

/** Release notes and user-initiated feedback, available from Utilities. */
public class WhatsNewActivity extends Activity {
    private static final int NAVY = 0xFF0B1C50;
    private static final int TEAL = 0xFF67D5CC;
    private static final int MUTED = 0xFFD3DCED;
    private static final String EMAIL = "adamdbird@gmail.com";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(NAVY);
        root.setPadding(dp(24), dp(24), dp(24), dp(28));
        LinearLayout header = RoadprintsHeader.create(this);
        header.setPadding(0, 0, 0, dp(24));
        root.addView(header);
        TextView title = text("What's new", 32, Color.WHITE, true);
        ViewCompat.setAccessibilityHeading(title, true);
        root.addView(title);
        TextView intro = text("The latest improvements to your Roadprints experience.", 16, MUTED, false);
        intro.setPadding(0, dp(8), 0, dp(20));
        root.addView(intro);
        try {
            List<ReleaseNotes.Release> releases = ReleaseNotes.load(this);
            for (ReleaseNotes.Release release : releases) addRelease(root, release);
        } catch (IOException | JSONException error) {
            root.addView(text("Release " + BuildConfig.VERSION_NAME, 21, Color.WHITE, true));
            TextView unavailable = text("Release notes aren't available right now. You can still contact Roadprints below.", 16, MUTED, false);
            unavailable.setPadding(0, dp(10), 0, dp(20));
            root.addView(unavailable);
        }

        LinearLayout contact = card(0xFF10275D);
        TextView prompt = text("Spotted an issue, or would like to suggest a new feature?", 18, Color.WHITE, true);
        ViewCompat.setAccessibilityHeading(prompt, true);
        contact.addView(prompt);
        Button button = new Button(this);
        button.setText("Contact Roadprints");
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setTextColor(NAVY);
        button.setMinHeight(dp(48));
        button.setBackgroundTintList(ColorStateList.valueOf(TEAL));
        button.setOnClickListener(v -> openContact(this));
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, -2);
        buttonParams.topMargin = dp(14);
        contact.addView(button, buttonParams);
        TextView address = text(EMAIL, 14, MUTED, false);
        address.setTextIsSelectable(true);
        address.setPadding(0, dp(8), 0, 0);
        contact.addView(address);
        root.addView(contact);
        RoadprintsHeader.installUtilityPage(this, root, "BACK TO UTILITIES", this::finish);
    }

    private void addRelease(LinearLayout root, ReleaseNotes.Release release) {
        LinearLayout card = card(0xFF233B78);
        TextView heading = text("Release " + release.versionName, 24, Color.WHITE, true);
        ViewCompat.setAccessibilityHeading(heading, true);
        card.addView(heading);
        TextView date = text("Released " + release.date.format(DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.UK)), 15, TEAL, false);
        date.setPadding(0, dp(6), 0, 0);
        card.addView(date);
        if (!release.since.isEmpty()) {
            TextView since = text("Changes since " + release.since, 14, MUTED, false);
            since.setPadding(0, dp(6), 0, dp(4));
            card.addView(since);
        }
        for (ReleaseNotes.Change change : release.changes) {
            TextView name = text(change.title, 18, Color.WHITE, true);
            name.setPadding(0, dp(20), 0, dp(6));
            ViewCompat.setAccessibilityHeading(name, true);
            card.addView(name);
            card.addView(text(change.description, 16, MUTED, false));
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(20);
        root.addView(card, params);
    }

    static void openContact(Activity activity) {
        Intent email = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + EMAIL
                + "?subject=" + Uri.encode("Roadprints feedback (" + BuildConfig.VERSION_NAME + ")")));
        try {
            activity.startActivity(email);
        } catch (ActivityNotFoundException error) {
            new AlertDialog.Builder(activity)
                    .setTitle("Contact Roadprints")
                    .setMessage("No email app is available. You can email Roadprints at " + EMAIL + ".")
                    .setPositiveButton("Copy email address", (dialog, which) -> {
                        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                        clipboard.setPrimaryClip(ClipData.newPlainText("Roadprints email", EMAIL));
                        Toast.makeText(activity, "Email address copied", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Close", null)
                    .show();
        }
    }

    private LinearLayout card(int color) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(16));
        background.setStroke(dp(1), 0xFF46649E);
        card.setBackground(background);
        return card;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
