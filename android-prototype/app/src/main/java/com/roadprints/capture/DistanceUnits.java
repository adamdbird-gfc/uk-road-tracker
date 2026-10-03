package com.roadprints.capture;

import android.content.Context;

import java.util.Locale;

/** Shared distance preference and presentation for journey summaries. */
final class DistanceUnits {
    private static final String PREFERENCES = "roadprints_display";
    private static final String KILOMETRES = "distance_kilometres";
    private static final double METRES_PER_MILE = 1609.344;

    private DistanceUnits() {}

    static boolean usesKilometres(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getBoolean(KILOMETRES, false);
    }

    static void setKilometres(Context context, boolean useKilometres) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .putBoolean(KILOMETRES, useKilometres).apply();
    }

    static String format(double metres, boolean useKilometres) {
        if (useKilometres) {
            return String.format(Locale.UK, "%.1f km", metres / 1000.0);
        }
        return String.format(Locale.UK, "%.1f mi", metres / METRES_PER_MILE);
    }

    static String format(Context context, double metres) {
        return format(metres, usesKilometres(context));
    }
}
