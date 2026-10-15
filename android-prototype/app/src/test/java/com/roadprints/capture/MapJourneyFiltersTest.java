package com.roadprints.capture;

import android.app.AlertDialog;
import android.widget.Spinner;
import android.view.View;
import android.os.Looper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class MapJourneyFiltersTest {
    private static final LocalDate DAY = LocalDate.of(2026, 7, 19);
    private static final ZoneId ZONE = ZoneId.of("Europe/London");
    private Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        java.lang.reflect.Method method=target.getClass().getDeclaredMethod(name,types);
        method.setAccessible(true); return method.invoke(target,args);
    }
    private Object field(Object target, String name) throws Exception {
        java.lang.reflect.Field field=target.getClass().getDeclaredField(name);
        field.setAccessible(true); return field.get(target);
    }
    private JSONObject row(String id, String mode) throws Exception {
        JSONObject geometry=new JSONObject().put("type","LineString")
                .put("coordinates",new JSONArray("[[0.37,51.44],[0.371,51.441]]"));
        JSONObject geojson=new JSONObject().put("type","FeatureCollection").put("features",
                new JSONArray().put(new JSONObject().put("type","Feature").put("geometry",geometry)
                    .put("properties",new JSONObject())));
        return new JSONObject().put("journey_id",id).put("mode",mode)
                .put("started_at","2026-07-19T10:00:00Z").put("ended_at","2026-07-19T10:10:00Z")
                .put("route_geometry",geometry).put("processing_status","complete")
                .put("processing_result",new JSONObject().put("geojson",geojson));
    }
    @Test public void onFootSelectionCombinesDateAndStatusWithoutChangingEvidence() throws Exception {
        JourneyFilters selection=new JourneyFilters(2,"matched",new JourneyDateFilter("custom",DAY,DAY));
        JSONObject walk=row("walk","walking"), drive=row("drive","driving"), run=row("run","running");
        String original=walk.toString();
        assertTrue(selection.matches(walk,DAY,ZONE)); assertTrue(selection.matches(run,DAY,ZONE));
        assertFalse(selection.matches(drive,DAY,ZONE));
        assertFalse(selection.matches(row("old","walking").put("started_at","2026-07-18T12:00:00Z"),DAY,ZONE));
        assertFalse(selection.matches(row("failed","walking").put("processing_status","failed"),DAY,ZONE));
        assertEquals(original,walk.toString());
    }
    @Test public void cyclingIncludesBicycleAlias() throws Exception {
        JourneyFilters selection=new JourneyFilters(5,"all",JourneyDateFilter.all());
        assertTrue(selection.matches(row("cycle","bicycle"),DAY,ZONE));
        assertFalse(selection.matches(row("walk","walking"),DAY,ZONE));
    }
    @Test public void filteredRouteAssemblyExcludesDrivingAndResetRestoresIt() throws Exception {
        android.content.Context context=RuntimeEnvironment.getApplication();
        JourneyStore.save(context,row("walk","walking")); JourneyStore.save(context,row("drive","driving"));
        MapActivity screen=Robolectric.buildActivity(MapActivity.class).create().get();
        Class<?>[] types={JourneyFilters.class,LocalDate.class,ZoneId.class};
        Object filtered=call(screen,"readMapRoutes",types,new JourneyFilters(2,"all",JourneyDateFilter.all()),DAY,ZONE);
        assertEquals(1,field(filtered,"matchedJourneys"));
        Object all=call(screen,"readMapRoutes",types,JourneyFilters.all(),DAY,ZONE);
        assertEquals(2,field(all,"matchedJourneys"));
        Object empty=call(screen,"readMapRoutes",types,new JourneyFilters(5,"all",JourneyDateFilter.all()),DAY,ZONE);
        assertEquals(0,field(empty,"matchedJourneys"));
        screen.finish();
    }
    @Test public void mapPanelCancelDoesNotCommitAndApplyUsesSameChoices() throws Exception {
        android.app.Activity activity=Robolectric.buildActivity(android.app.Activity.class).create().get();
        java.util.concurrent.atomic.AtomicReference<JourneyFilters> applied=new java.util.concurrent.atomic.AtomicReference<>();
        JourneyFilterPanel panel=new JourneyFilterPanel(activity,Arrays.asList(row("drive","driving"),row("walk","walking")),JourneyFilters.all(),true,applied::set);
        panel.show(); AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
        Spinner spinner=dialog.getWindow().getDecorView().findViewWithTag("journey_filter_transport");
        spinner.setSelection(2); Shadows.shadowOf(Looper.getMainLooper()).idle();
        dialog.cancel(); assertNull(applied.get());
        panel.show(); dialog=ShadowAlertDialog.getLatestAlertDialog();
        spinner=dialog.getWindow().getDecorView().findViewWithTag("journey_filter_transport");
        spinner.setSelection(2); Shadows.shadowOf(Looper.getMainLooper()).idle();
        dialog.getWindow().getDecorView().findViewWithTag("journey_filter_apply").performClick();
        assertEquals(2,applied.get().mode); activity.finish();
    }
    @Test public void overlayResetPersistsIndependentlyOfJourneyListSelection() throws Exception {
        MapActivity screen=Robolectric.buildActivity(MapActivity.class).create().get();
        View filter=screen.findViewById(android.R.id.content).findViewWithTag("map_filters_open");
        View reset=screen.findViewById(android.R.id.content).findViewWithTag("map_filters_reset");
        assertNotNull(filter); assertNotNull(reset); assertTrue(filter.isFocusable());
        call(screen,"applyMapFilters",new Class[]{JourneyFilters.class},new JourneyFilters(2,"all",JourneyDateFilter.all()));
        assertEquals(2,RuntimeEnvironment.getApplication().getSharedPreferences("roadprints_map_journey_filters",0).getInt("mode",0));
        reset.performClick(); assertTrue(((JourneyFilters)field(screen,"mapFilters")).isAll());
        assertEquals(0,RuntimeEnvironment.getApplication().getSharedPreferences("roadprints_map_journey_filters",0).getInt("mode",-1));
        screen.finish();
    }
}
