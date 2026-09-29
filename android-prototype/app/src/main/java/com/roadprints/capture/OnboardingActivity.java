package com.roadprints.capture;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class OnboardingActivity extends Activity {
    private static final String PREFS = "roadprints_onboarding";
    private static final String COMPLETE = "complete";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFF0B1C50);
        getWindow().setNavigationBarColor(0xFF0B1C50);

        if (getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(COMPLETE, false)) {
            openCapture();
            return;
        }
        buildScreen();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF0B1C50);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(24, 28, 24, 36);

        ImageView mark = new ImageView(this);
        mark.setImageResource(R.drawable.roadprints_mark);
        mark.setContentDescription("Roadprints");
        mark.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        root.addView(mark, new LinearLayout.LayoutParams(-1, 210));

        TextView brand = text("roadprints", 42, Color.WHITE, true);
        brand.setGravity(Gravity.CENTER);
        root.addView(brand);

        TextView tagline = text("Every road tells your story.", 18, 0xFF67D5CC, false);
        tagline.setGravity(Gravity.CENTER);
        tagline.setPadding(0, 8, 0, 38);
        root.addView(tagline);

        TextView eyebrow = text("A QUICK QUESTION", 14, Color.WHITE, true);
        eyebrow.setLetterSpacing(0.18f);
        eyebrow.setPadding(0, 0, 0, 12);
        root.addView(eyebrow);

        TextView question = text("How much of a traveller are you?", 28, Color.WHITE, true);
        question.setGravity(Gravity.CENTER);
        root.addView(question);

        TextView copy = text(
                "Roadprints will use your answer to shape your starting experience.",
                17, 0xFFD3DCED, false);
        copy.setGravity(Gravity.CENTER);
        copy.setPadding(0, 10, 0, 22);
        root.addView(copy);

        root.addView(choice("Local explorer", "Mostly nearby roads and places."));
        root.addView(choice("National traveller", "Regular journeys across the country."));
        root.addView(choice("Always on the move", "Journeys that take you further afield."));

        TextView routes = text("How would you like to begin?", 22, Color.WHITE, true);
        routes.setGravity(Gravity.CENTER);
        routes.setPadding(0, 34, 0, 10);
        root.addView(routes);

        TextView routeCopy = text(
                "Bring in your Google Timeline journeys, or start building your Roadprints journey from today.",
                16, 0xFFD3DCED, false);
        routeCopy.setGravity(Gravity.CENTER);
        routeCopy.setPadding(0, 0, 0, 18);
        root.addView(routeCopy);

        root.addView(route("I HAVE TIMELINE DATA", v -> {
            markComplete();
            startActivity(new Intent(this, TimelineImportActivity.class));
        }));
        root.addView(route("I'M STARTING FRESH", v -> {
            markComplete();
            openCapture();
        }));

        if (JourneyStore.count(this) > 0) {
            root.addView(route("VIEW SAVED JOURNEYS", v ->
                    startActivity(new Intent(this, JourneyListActivity.class))));
        }

        TextView privacy = text(
                "Your journey history stays on this device.",
                14, 0xFF9FB3D0, false);
        privacy.setGravity(Gravity.CENTER);
        privacy.setPadding(0, 24, 0, 0);
        root.addView(privacy);

        scroll.addView(root);
        setContentView(scroll);
    }

    private TextView choice(String title, String subtitle) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(22, 14, 22, 14);
        card.setBackgroundColor(0xFF2C4380);
        card.setClickable(true);
        card.setOnClickListener(v -> v.setBackgroundColor(0xFF3B579A));

        TextView heading = text(title, 18, Color.WHITE, true);
        TextView detail = text(subtitle, 14, 0xFFD3DCED, true);
        card.addView(heading);
        card.addView(detail);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, 78);
        params.setMargins(0, 0, 0, 12);
        card.setLayoutParams(params);
        return cardAsText(card);
    }

    private TextView cardAsText(LinearLayout card) {
        TextView proxy = new TextView(this);
        proxy.setText(((TextView) card.getChildAt(0)).getText() + "\\n" +
                ((TextView) card.getChildAt(1)).getText());
        proxy.setTextColor(Color.WHITE);
        proxy.setTextSize(17);
        proxy.setTypeface(null, Typeface.BOLD);
        proxy.setGravity(Gravity.CENTER_VERTICAL);
        proxy.setPadding(22, 0, 22, 0);
        proxy.setBackgroundColor(0xFF2C4380);
        proxy.setClickable(true);
        proxy.setOnClickListener(v -> v.setBackgroundColor(0xFF3B579A));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, 78);
        params.setMargins(0, 0, 0, 12);
        proxy.setLayoutParams(params);
        return proxy;
    }

    private TextView route(String label, View.OnClickListener listener) {
        TextView button = text(label, 15, 0xFF0B1C50, true);
        button.setGravity(Gravity.CENTER);
        button.setBackgroundColor(0xFFF7C450);
        button.setClickable(true);
        button.setFocusable(true);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, 58);
        params.setMargins(0, 0, 0, 14);
        button.setLayoutParams(params);
        return button;
    }

    private TextView text(String value, float size, int colour, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(colour);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private void markComplete() {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit().putBoolean(COMPLETE, true).apply();
    }

    private void openCapture() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
