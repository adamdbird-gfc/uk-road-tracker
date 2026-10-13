package com.roadprints.capture;
import android.app.Activity;
import android.app.AlertDialog;
import org.json.JSONArray;
import org.json.JSONObject;
import com.google.android.gms.tasks.CancellationTokenSource;
/** Saved routes/test hooks are retained; dialogs are shared between all venue collections. */
final class PubVisitUi {
    private static final String ID=CollectionAccessStore.SPOONS;
    interface LocationRequester {com.google.android.gms.tasks.Task<android.location.Location> get(Activity activity,CancellationTokenSource request);}
    static final LocationRequester DEVICE_LOCATION=VenueVisitUi.DEVICE_LOCATION::get;
    static LocationRequester locationRequester=DEVICE_LOCATION;
    static void importTimeline(Activity a){VenueVisitUi.importTimeline(a);}
    static void review(Activity a,Runnable changed){VenueVisitUi.reviewNative(a,ID,changed);}
    static AlertDialog showSlider(Activity a,JSONArray pending,Runnable changed){return VenueVisitUi.showSlider(a,ID,pending,changed);}
    static AlertDialog details(Activity a,JSONObject pub,Runnable changed){return VenueVisitUi.details(a,ID,pub,changed);}
    static AlertDialog live(Activity a,JSONObject pub,Runnable changed,Runnable closed){return VenueVisitUi.live(a,ID,pub,changed,closed);}
    static String date(String at){return VenueVisitUi.date(at);}
}
