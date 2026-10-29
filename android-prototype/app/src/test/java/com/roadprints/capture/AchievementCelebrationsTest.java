package com.roadprints.capture;

import android.content.Context;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.Collections;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class AchievementCelebrationsTest {
    private Context app;
    @Before public void reset(){app=RuntimeEnvironment.getApplication();app.getSharedPreferences("roadprints_achievements_v1",0).edit().clear().commit();app.getSharedPreferences("roadprints_achievement_celebrations_v1",0).edit().clear().commit();}
    private void earn(String id,double value){AchievementStore.evaluate(app,Collections.singletonMap(id,value),Collections.emptyMap(),new boolean[7],0);}
    @Test public void recognitionWithoutVisibleScreenStaysPendingAcrossRepeatedCalculations(){
        earn("motorway-quarter",25);assertEquals(1,AchievementCelebrations.pending(app).size());
        earn("motorway-quarter",25);assertEquals(1,AchievementCelebrations.pending(app).size());
    }
    @Test public void acknowledgingDeliveredLevelPreventsReplayButHigherLevelStillCelebrates(){
        earn("foot-total",25);AchievementCelebrations.Event event=AchievementCelebrations.pending(app).get(0);
        AchievementCelebrations.acknowledge(app,event);assertTrue(AchievementCelebrations.pending(app).isEmpty());
        earn("foot-total",100);assertEquals(1,AchievementCelebrations.pending(app).size());
        assertTrue(AchievementCelebrations.pending(app).get(0).level>event.level);
        AchievementCelebrations.acknowledge(app,event);assertEquals(1,AchievementCelebrations.pending(app).size());
    }
    @Test public void legacyBackgroundUnlockIsRecoveredWithoutRecognizingItAgain() throws Exception {
        app.getSharedPreferences("roadprints_achievements_v1",0).edit().putString("unlocked",new JSONObject().put("motorway-quarter",new JSONObject().put("level",1).put("unlocked_at",1)).toString()).commit();
        assertEquals("motorway-quarter",AchievementCelebrations.pending(app).get(0).definition.id);
    }
    @Test public void withdrawnEvidenceRemovesPendingWithoutGrantingAnAchievement(){
        earn("motorway-quarter",25);AchievementCelebrations.Event event=AchievementCelebrations.pending(app).get(0);
        earn("motorway-quarter",0);assertTrue(AchievementCelebrations.pending(app).isEmpty());
        AchievementCelebrations.acknowledge(app,event);assertTrue(AchievementCelebrations.pending(app).isEmpty());
        assertFalse(AchievementStore.catalogueSnapshot(app).achievements.stream().filter(p->p.definition.id.equals("motorway-quarter")).findFirst().get().unlocked);
    }
    @Test public void futureMilestoneCannotBeAcknowledgedBeforeItIsEarned(){
        earn("foot-total",25);AchievementCelebrations.Event current=AchievementCelebrations.pending(app).get(0);
        AchievementCelebrations.acknowledge(app,new AchievementCelebrations.Event(current.definition,current.level+1));
        assertEquals(1,AchievementCelebrations.pending(app).size());
    }
}
