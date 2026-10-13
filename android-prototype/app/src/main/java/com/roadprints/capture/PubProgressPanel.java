package com.roadprints.capture;
import android.app.Activity;
import android.widget.LinearLayout;
import java.util.Set;
/** Compatibility wrapper; grouped progress belongs to the shared venue framework. */
final class PubProgressPanel {
    static void populate(Activity a,LinearLayout panel,Set<String> expanded,Runnable changed){VenueProgressPanel.populate(a,CollectionAccessStore.SPOONS,panel,expanded,changed);}
}
