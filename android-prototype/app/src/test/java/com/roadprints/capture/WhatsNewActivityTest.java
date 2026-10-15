package com.roadprints.capture;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class WhatsNewActivityTest {
    private TextView find(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) view;
            for (int i = 0; i < parent.getChildCount(); i++) {
                TextView result = find(parent.getChildAt(i), text);
                if (result != null) return result;
            }
        }
        return null;
    }

    @Test public void utilitiesOpensRegisteredPrivateNotesScreen() throws Exception {
        UtilitiesActivity utilities = Robolectric.buildActivity(UtilitiesActivity.class).setup().get();
        TextView option = find(utilities.getWindow().getDecorView(), "What's new");
        assertNotNull(option);
        ((View) option.getParent()).performClick();
        Intent destination = Shadows.shadowOf(utilities).getNextStartedActivity();
        assertEquals(WhatsNewActivity.class.getName(), destination.getComponent().getClassName());
        assertFalse(utilities.getPackageManager().getActivityInfo(
                new ComponentName(utilities, WhatsNewActivity.class), 0).exported);
    }

    @Test public void currentReleaseNotesMatchInstalledBuildAndShowDateAndComparison() throws Exception {
        List<ReleaseNotes.Release> releases = ReleaseNotes.load(RuntimeEnvironment.getApplication());
        ReleaseNotes.Release current = releases.get(0);
        assertEquals(BuildConfig.VERSION_NAME, current.versionName);
        assertEquals(BuildConfig.VERSION_CODE, current.versionCode);
        assertFalse(current.date.isAfter(LocalDate.now()));
        WhatsNewActivity activity = Robolectric.buildActivity(WhatsNewActivity.class).setup().get();
        View root = activity.getWindow().getDecorView();
        assertNotNull(find(root, "Release " + BuildConfig.VERSION_NAME));
        assertNotNull(find(root, "Released " + current.date.format(DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.UK))));
        assertNotNull(find(root, "Changes since " + current.since));
        assertNotNull(find(root, current.changes.get(0).title));
        find(root, "BACK TO UTILITIES").performClick();
        assertTrue(activity.isFinishing());
    }

    @Test public void contactOpensOnlyEmailDraftWithCorrectRecipientAndVersion() {
        WhatsNewActivity activity = Robolectric.buildActivity(WhatsNewActivity.class).setup().get();
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        TextView contact = find(activity.getWindow().getDecorView(), "Contact Roadprints");
        assertNotNull(contact);
        contact.performClick();
        Intent email = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(Intent.ACTION_SENDTO, email.getAction());
        assertEquals("mailto", email.getData().getScheme());
        assertTrue(email.getData().getSchemeSpecificPart().startsWith("adamdbird@gmail.com?subject="));
        assertTrue(email.getData().toString().contains(BuildConfig.VERSION_NAME));
        assertNull(email.getClipData());
        assertFalse(email.hasExtra(Intent.EXTRA_STREAM));
        assertFalse(email.hasExtra(Intent.EXTRA_TEXT));
    }

    public static class NoEmailActivity extends Activity {
        @Override public void startActivity(Intent intent) { throw new ActivityNotFoundException(); }
    }

    @Test public void missingEmailAppOffersExplicitCopyWithoutCrashing() {
        Activity activity = Robolectric.buildActivity(NoEmailActivity.class).setup().get();
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.clearPrimaryClip();
        WhatsNewActivity.openContact(activity);
        assertFalse(clipboard.hasPrimaryClip());
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertTrue(dialog.isShowing());
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals("adamdbird@gmail.com", clipboard.getPrimaryClip().getItemAt(0).getText().toString());
    }

    private JSONObject release(int code, String name, String date) throws Exception {
        return new JSONObject().put("version_code", code).put("version_name", name).put("release_date", date)
                .put("changes", new JSONArray().put(new JSONObject().put("title", "Improvement").put("description", "A clearer screen.")));
    }

    @Test public void historyIsNewestFirstAndFutureReleasesAreHidden() throws Exception {
        JSONArray releases = new JSONArray().put(release(1, "1.0.0", "2026-10-13"))
                .put(release(3, "1.0.2", "2026-10-16")).put(release(2, "1.0.1", "2026-10-15"));
        String raw = new JSONObject().put("schema_version", 1).put("releases", releases).toString();
        List<ReleaseNotes.Release> result = ReleaseNotes.parse(raw, 2, "1.0.1");
        assertEquals(2, result.size());
        assertEquals("1.0.1", result.get(0).versionName);
        assertEquals("1.0.0", result.get(1).versionName);
    }

    @Test public void missingInstalledNotesOrInvalidDatesFailReleaseValidation() throws Exception {
        for (JSONObject row : new JSONObject[] {release(1, "1.0.0", "2026-10-13"),
                release(2, "wrong-version", "2026-10-15"), release(2, "1.0.1", "2026-02-30")}) {
            String raw = new JSONObject().put("schema_version", 1).put("releases", new JSONArray().put(row)).toString();
            try {
                ReleaseNotes.parse(raw, 2, "1.0.1");
                fail("An inaccurate release entry must block packaging");
            } catch (JSONException expected) { }
        }
    }
}
