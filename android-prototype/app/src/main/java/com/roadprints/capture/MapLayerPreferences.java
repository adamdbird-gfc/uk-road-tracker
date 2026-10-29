package com.roadprints.capture;
import android.content.Context;
/** Visual preferences only; hiding a layer never changes evidence or progress. */
final class MapLayerPreferences {
    static final String PREFS="roadprints_map_layers_v1";
    enum Layer {
        TRAVEL("Recorded travel",0xFF000000,"Matched travel traces, including roads and paths."),
        MOTORWAYS("Motorway coverage",0xFF005EB8,"Motorway sections you have covered."),
        A_ROADS("A-road coverage",0xFF25834A,"A-road sections you have covered."),
        REMAINING("Remaining major-road sections",0xFFD93A3A,"Motorway and A-road sections remaining in the reference coverage layer."),
        BOUNDARIES("Town boundary",0xFFE45757,"The outline of the selected town.");
        final String label,detail;final int color;
        Layer(String label,int color,String detail){this.label=label;this.color=color;this.detail=detail;}
    }
    static boolean visible(Context c,Layer layer){return c.getSharedPreferences(PREFS,0).getBoolean(layer.name(),true);}
    static void set(Context c,Layer layer,boolean visible){c.getSharedPreferences(PREFS,0).edit().putBoolean(layer.name(),visible).apply();}
}
