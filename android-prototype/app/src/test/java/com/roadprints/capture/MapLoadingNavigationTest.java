package com.roadprints.capture;

import android.app.Activity;
import android.content.Intent;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import java.time.Duration;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@LooperMode(LooperMode.Mode.PAUSED)
public class MapLoadingNavigationTest {
    @Test public void leavingOverviewRetainsMapAndReturningReusesIt() {
        MapActivity map = Robolectric.buildActivity(MapActivity.class).create().get();
        RoadprintsNavigation.openDestination(map, JourneyListActivity.class);
        assertFalse(map.isFinishing());
        assertEquals(JourneyListActivity.class.getName(),
                Shadows.shadowOf(map).getNextStartedActivity().getComponent().getClassName());
        Activity journeys = Robolectric.buildActivity(Activity.class).create().get();
        RoadprintsNavigation.openDestination(journeys, MapActivity.class);
        Intent returning = Shadows.shadowOf(journeys).getNextStartedActivity();
        assertEquals(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP,
                returning.getFlags());
        assertTrue(journeys.isFinishing());
    }

    @Test public void settlementMapDoesNotRemainUnderTabs() {
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), MapActivity.class)
                .putExtra("settlement_code", "test");
        MapActivity map = Robolectric.buildActivity(MapActivity.class, intent).create().get();
        RoadprintsNavigation.openDestination(map, ProgressActivity.class);
        assertTrue(map.isFinishing());
    }

    @Test public void otherTabChangesStillFinishPreviousTab() {
        Activity activity = Robolectric.buildActivity(Activity.class).create().get();
        RoadprintsNavigation.openDestination(activity, AchievementsActivity.class);
        assertTrue(activity.isFinishing());
        assertEquals(0, Shadows.shadowOf(activity).getNextStartedActivity().getFlags());
    }

    @Test public void loadingHasIndeterminateSpinnerAndRotatesOnlyWhileVisible() {
        MapLoadingView card = new MapLoadingView(RuntimeEnvironment.getApplication(), false);
        LinearLayout title = (LinearLayout) card.getChildAt(0);
        assertTrue(((ProgressBar) title.getChildAt(0)).isIndeterminate());
        assertEquals("Finding your Roadprints", ((TextView) title.getChildAt(1)).getText().toString());
        TextView fact = (TextView) card.getChildAt(3);
        String first = fact.getText().toString();
        card.start();
        card.start();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(8));
        String second = fact.getText().toString();
        assertNotEquals(first, second);
        card.stop();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(16));
        assertEquals(second, fact.getText().toString());
        card.start();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(8));
        assertNotEquals(second, fact.getText().toString());
        card.stop();
    }

    @Test public void refreshingCopyKeepsExistingMapAvailable() {
        MapLoadingView card = new MapLoadingView(RuntimeEnvironment.getApplication(), true);
        LinearLayout title = (LinearLayout) card.getChildAt(0);
        assertEquals("Growing your Roadprints", ((TextView) title.getChildAt(1)).getText().toString());
        assertTrue(((TextView) card.getChildAt(1)).getText().toString().contains("keep exploring"));
    }
}
