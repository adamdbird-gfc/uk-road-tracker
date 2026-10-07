package com.roadprints.capture;

import android.app.Activity;
import android.widget.LinearLayout;
import java.time.Duration;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class GrowingStatusControlTest {
    private Activity activity;
    private GrowingStatusControl bar;
    @Before public void setup() {
        activity=Robolectric.buildActivity(Activity.class).setup().get();
        bar=(GrowingStatusControl)GrowingStatusControl.create(activity);
    }
    private MatchingCoordinator.Snapshot snapshot(MatchingCoordinator.State state,int failed) {
        return new MatchingCoordinator.Snapshot(state,10,4,4,failed,10,4,4,0,0,0,0,0,0,"");
    }
    private int dp(int value) { return Math.round(value*activity.getResources().getDisplayMetrics().density); }
    @Test public void idleReadyIsSlimFullWidthAndNotAnInactiveShortcut() {
        bar.render(snapshot(MatchingCoordinator.State.IDLE,0));
        assertEquals(LinearLayout.LayoutParams.MATCH_PARENT,bar.getLayoutParams().width);
        assertEquals(dp(28),bar.getLayoutParams().height);assertEquals("✓ Ready",bar.getText().toString());
        assertFalse(bar.isClickable());assertFalse(bar.isFocusable());
    }
    @Test public void growingExpandsAndOpensExistingActivityScreen() {
        bar.render(snapshot(MatchingCoordinator.State.RUNNING,0));
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
        assertEquals(dp(52),bar.getLayoutParams().height);assertTrue(bar.getText().toString().contains("4 / 10"));
        assertTrue(bar.isClickable());assertTrue(bar.performClick());
        assertEquals(GrowingActivity.class.getName(),shadowOf(activity).getNextStartedActivity().getComponent().getClassName());
    }
    @Test public void pausedAndRetryRemainAccessibleAndSuccessCollapses() {
        for(MatchingCoordinator.State state:new MatchingCoordinator.State[]{MatchingCoordinator.State.PREPARING,
                MatchingCoordinator.State.PAUSING,MatchingCoordinator.State.PAUSED,MatchingCoordinator.State.ERROR,
                MatchingCoordinator.State.COMPLETE}) {
            bar.render(snapshot(state,1));assertTrue(bar.isClickable());assertTrue(bar.isFocusable());
        }
        bar.render(snapshot(MatchingCoordinator.State.COMPLETE,0));
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
        assertEquals(dp(28),bar.getLayoutParams().height);assertEquals("✓ Ready",bar.getText().toString());
        assertFalse(bar.isClickable());
    }
    @Test public void rowHasOwnLayoutSpaceAboveFooterAndHeaderContainsNoStatus() {
        LinearLayout root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);
        root.addView(GrowingStatusControl.create(activity));root.addView(RoadprintsNavigation.create(activity,1));
        assertTrue(root.getChildAt(0) instanceof GrowingStatusControl);
        assertEquals(2,root.getChildCount());assertEquals(1,RoadprintsHeader.create(activity).getChildCount());
    }
}
