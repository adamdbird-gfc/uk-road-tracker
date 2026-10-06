package com.roadprints.capture;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Spinner;
import java.time.*;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class JourneyFiltersTest {
    private JSONObject journey(String stamp) throws Exception {return new JSONObject().put("started_at",stamp);}
    private boolean date(JourneyDateFilter filter,String stamp) throws Exception {return filter.matches(journey(stamp),LocalDate.of(2026,10,6),ZoneId.of("Europe/London"));}
    @Test public void todayUsesLocalMidnightRatherThanUtc() throws Exception {
        JourneyDateFilter filter=new JourneyDateFilter("today",null,null);
        assertTrue(date(filter,"2026-10-05T23:00:00Z"));
        assertFalse(date(filter,"2026-10-05T22:59:59Z"));
        assertTrue(date(filter,"2026-10-06T22:59:59Z"));
        assertFalse(date(filter,"2026-10-06T23:00:00Z"));
    }
    @Test public void weekIncludesSevenCalendarDays() throws Exception {
        JourneyDateFilter filter=new JourneyDateFilter("week",null,null);
        assertTrue(date(filter,"2026-09-29T23:00:00Z"));
        assertFalse(date(filter,"2026-09-29T22:59:59Z"));
        assertFalse(date(filter,"2026-10-07T12:00:00Z"));
    }
    @Test public void monthStartsAtLocalFirstDay() throws Exception {
        JourneyDateFilter filter=new JourneyDateFilter("month",null,null);
        assertTrue(date(filter,"2026-09-30T23:00:00Z"));
        assertFalse(date(filter,"2026-09-30T22:59:59Z"));
    }
    @Test public void customIncludesWholeDstFallbackDay() throws Exception {
        JourneyDateFilter filter=new JourneyDateFilter("custom",LocalDate.of(2026,10,25),LocalDate.of(2026,10,25));
        assertTrue(date(filter,"2026-10-24T23:00:00Z"));
        assertTrue(date(filter,"2026-10-25T23:59:59Z"));
        assertFalse(date(filter,"2026-10-26T00:00:00Z"));
    }
    @Test public void malformedDatesRemainAvailableUnderAllDates() throws Exception {
        assertTrue(date(JourneyDateFilter.all(),"broken"));
        assertFalse(date(new JourneyDateFilter("today",null,null),"broken"));
        assertFalse(new JourneyDateFilter("custom",LocalDate.of(2026,10,6),LocalDate.of(2026,10,5)).valid());
        assertEquals("all",JourneyDateFilter.restore("custom","broken","broken").preset);
    }
    private void set(Object target,String name,Object value) throws Exception {java.lang.reflect.Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);}
    private Object get(Object target,String name) throws Exception {java.lang.reflect.Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);}
    private Object call(Object target,String name,Class<?>[] types,Object... args) throws Exception {java.lang.reflect.Method method=target.getClass().getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(target,args);}
    private JourneyListActivity activity() {
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions("com.roadprints.capture.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION");
        return Robolectric.buildActivity(JourneyListActivity.class).create().get();
    }
    @Test public void transportStatusAndDateCombineIncludingRunning() throws Exception {
        JourneyListActivity screen=activity();set(screen,"activeFilter",2);set(screen,"activeJourneyStatusFilter","matched");
        set(screen,"activeDateFilter",new JourneyDateFilter("custom",LocalDate.of(2026,10,6),LocalDate.of(2026,10,6)));
        JSONObject row=journey("2026-10-06T12:00:00Z").put("mode","running").put("processing_status","complete").put("_has_stored_match",true);
        assertEquals(true,call(screen,"matchesActiveFilter",new Class[]{JSONObject.class},row));
        row.put("mode","driving");assertEquals(false,call(screen,"matchesActiveFilter",new Class[]{JSONObject.class},row));
        row.put("mode","pedestrian").put("processing_status","failed");assertEquals(false,call(screen,"matchesActiveFilter",new Class[]{JSONObject.class},row));
        row.put("processing_status","complete").put("started_at","2026-10-05T12:00:00Z");assertEquals(false,call(screen,"matchesActiveFilter",new Class[]{JSONObject.class},row));
        screen.finish();
    }
    @Test public void trainWithoutMatchIsNotAFailedRoadMatch() throws Exception {
        JourneyListActivity screen=activity();set(screen,"activeJourneyStatusFilter","failed");
        JSONObject row=new JSONObject().put("mode","train").put("processing_status","complete");
        assertEquals(false,call(screen,"matchesJourneyStatusFilter",new Class[]{JSONObject.class},row));
        set(screen,"activeJourneyStatusFilter","no_match");assertEquals(true,call(screen,"matchesJourneyStatusFilter",new Class[]{JSONObject.class},row));screen.finish();
    }
    @Test public void cancelDiscardsDraftAndApplyCommitsSelection() throws Exception {
        JourneyListActivity screen=activity();set(screen,"activeFilter",0);set(screen,"activeJourneyStatusFilter","all");set(screen,"activeDateFilter",JourneyDateFilter.all());
        set(screen,"journeys",java.util.Arrays.asList(archiveRow("drive","driving", "complete"),archiveRow("train","train", "complete")));
        call(screen,"showJourneyFilters",new Class[]{});
        AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
        Spinner transport=findSpinner(dialog.getWindow().getDecorView());transport.setSelection(1);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        dialog.cancel();assertEquals(0,get(screen,"activeFilter"));
        call(screen,"showJourneyFilters",new Class[]{});dialog=ShadowAlertDialog.getLatestAlertDialog();
        findSpinner(dialog.getWindow().getDecorView()).setSelection(1);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        dialog.getWindow().getDecorView().findViewWithTag("journey_filter_apply").performClick();
        assertEquals(1,get(screen,"activeFilter"));screen.finish();
    }
    private JSONObject archiveRow(String id, String mode, String status) throws Exception {
        return journey(Instant.now().toString()).put("journey_id",id).put("mode",mode)
            .put("processing_status",status).put("_route_point_count",2).put("_has_stored_match","complete".equals(status));
    }
    @Test public void unavailableModesAndStatusesAreAbsentButOnFootGroupsRunning() throws Exception {
        JourneyListActivity screen=activity();
        set(screen,"journeys",java.util.Arrays.asList(archiveRow("drive","driving","complete"),archiveRow("run","running","pending"),archiveRow("train","train","complete")));
        assertEquals(java.util.Arrays.asList(0,1,2,4),call(screen,"availableTransportOptions",new Class[]{}));
        assertEquals(java.util.Arrays.asList(0,1,2,5),call(screen,"availableStatusOptions",new Class[]{}));
        set(screen,"recapJourneyIds",new java.util.HashSet<>(java.util.Arrays.asList("train")));
        assertEquals(java.util.Arrays.asList(0,4),call(screen,"availableTransportOptions",new Class[]{}));
        assertEquals(java.util.Arrays.asList(0,5),call(screen,"availableStatusOptions",new Class[]{})); screen.finish();
    }
    @Test public void compactDropdownPositionMapsToOriginalTransportValue() throws Exception {
        JourneyListActivity screen=activity();set(screen,"activeFilter",0);set(screen,"activeJourneyStatusFilter","all");set(screen,"activeDateFilter",JourneyDateFilter.all());
        set(screen,"journeys",java.util.Arrays.asList(archiveRow("drive","driving","complete"),archiveRow("train","train","complete")));
        call(screen,"showJourneyFilters",new Class[]{});AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
        Spinner transport=findSpinner(dialog.getWindow().getDecorView());assertEquals(3,transport.getAdapter().getCount());
        transport.setSelection(2);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        dialog.getWindow().getDecorView().findViewWithTag("journey_filter_apply").performClick();
        assertEquals(4,get(screen,"activeFilter"));screen.finish();
    }
    @Test public void dateChoicesOmitEmptyPresetsAndCustomStartsOnLatestRecording() throws Exception {
        JourneyListActivity screen=activity();set(screen,"activeDateFilter",JourneyDateFilter.all());
        JSONObject old=archiveRow("old","train","complete").put("started_at","2020-01-15T12:00:00Z");
        set(screen,"journeys",java.util.Arrays.asList(old));
        assertEquals(java.util.Arrays.asList(0,4),call(screen,"availableDateOptions",new Class[]{}));
        assertEquals(LocalDate.of(2020,1,15),call(screen,"latestJourneyDate",new Class[]{}));screen.finish();
    }
    @Test public void calendarUsesBrandAccentAndReturnsChosenDate() throws Exception {
        JourneyListActivity screen=activity();java.util.concurrent.atomic.AtomicReference<LocalDate> picked=new java.util.concurrent.atomic.AtomicReference<>();
        call(screen,"pickFilterDate",new Class[]{LocalDate.class,java.util.function.Consumer.class},LocalDate.of(2026,10,5),(java.util.function.Consumer<LocalDate>)picked::set);
        android.app.DatePickerDialog dialog=(android.app.DatePickerDialog)ShadowAlertDialog.getLatestAlertDialog();
        android.util.TypedValue accent=new android.util.TypedValue();dialog.getContext().getTheme().resolveAttribute(android.R.attr.colorAccent,accent,true);
        assertEquals(0xFFF7C450,accent.data);
        dialog.updateDate(2026,9,4);dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertEquals(LocalDate.of(2026,10,4),picked.get());screen.finish();
    }
    private Spinner findSpinner(View view) {if(view instanceof Spinner)return (Spinner)view;if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++){Spinner found=findSpinner(group.getChildAt(i));if(found!=null)return found;}}return null;}
}
