package com.roadprints.capture;
import android.app.AlertDialog;import android.widget.Button;
import org.json.*;import org.junit.Test;import org.junit.runner.RunWith;
import org.robolectric.*;import org.robolectric.annotation.Config;import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class OversizedJourneyReviewTest {
 @Test public void largeTrainDetailOffersExportReviewWithoutStartingAMatchOrChangingEvidence()throws Exception {
  Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions("com.roadprints.capture.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION");
  JourneyListActivity screen=Robolectric.buildActivity(JourneyListActivity.class).create().get();
  JSONArray rows=new JSONArray(),coords=new JSONArray();
  for(int i=0;i<39120;i++){
   rows.put(new JSONArray().put(0).put(0).put(5).put(1000L+i*5000L).put(0));
   if(i<32767)coords.put(new JSONArray().put(0).put(0));
  }
  JSONObject journey=new JSONObject().put("journey_id","synthetic-large-train").put("mode","train")
      .put("started_at","2026-10-28T18:23:09Z").put("ended_at","2026-10-30T19:45:35Z")
      .put("raw_capture_samples",rows).put("gps_point_count",rows.length())
      .put("route_geometry",new JSONObject().put("type","LineString").put("coordinates",coords));
  String original=journey.toString();
  try{
   java.lang.reflect.Method show=JourneyListActivity.class.getDeclaredMethod("showDetails",JSONObject.class);show.setAccessible(true);show.invoke(screen,journey);
   AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
   Button review=dialog.getWindow().getDecorView().findViewWithTag("journey_oversized_review");assertNotNull(review);
   assertEquals("EXPORT RECORDING FOR SAFE REVIEW",review.getText().toString());review.performClick();
   AlertDialog explanation=ShadowAlertDialog.getLatestAlertDialog();assertNotSame(dialog,explanation);
   assertTrue(Shadows.shadowOf(explanation).getMessage().toString().contains("39120 original GPS samples"));
   assertEquals(original,journey.toString());explanation.dismiss();dialog.dismiss();
  }finally{screen.finish();}
 }
}
