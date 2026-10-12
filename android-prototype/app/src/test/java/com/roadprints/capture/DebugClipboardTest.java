package com.roadprints.capture;

import android.app.AlertDialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Before;
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
public class DebugClipboardTest {
    private Context app;
    private File log;
    @Before public void reset(){
        app=RuntimeEnvironment.getApplication();
        MovementDiagnostics.clear(app);
        CrashReporter.clear(app);
        log=new File(app.getFilesDir(),"roadprints_movement_diagnostics.jsonl");
        ((ClipboardManager)app.getSystemService(Context.CLIPBOARD_SERVICE)).clearPrimaryClip();
    }
    private void write(String text) throws Exception {
        Files.write(log.toPath(),text.getBytes(StandardCharsets.UTF_8));
    }
    private TextView button(View view,String text){
        if(view instanceof TextView && text.contentEquals(((TextView)view).getText()))return (TextView)view;
        if(view instanceof ViewGroup){
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++){
                TextView found=button(group.getChildAt(i),text);
                if(found!=null)return found;
            }
        }
        return null;
    }
    @Test public void shortLogCopiesCompleteText() throws Exception {
        write("{\"event\":\"test\",\"detail\":\"complete\"}\n");
        String report=MovementDiagnostics.getClipboardReport(app);
        assertNotNull(report);
        assertTrue(report.endsWith("complete\"}\n"));
        assertTrue(DebugActivity.copyReportToClipboard(app,"Log",report));
        ClipboardManager clipboard=(ClipboardManager)app.getSystemService(Context.CLIPBOARD_SERVICE);
        assertEquals(report,clipboard.getPrimaryClip().getItemAt(0).getText().toString());
    }
    @Test public void oversizedCopyOffersCompleteDownloadAndPreservesOriginal() throws Exception {
        char[] chars=new char[2_034_392];
        java.util.Arrays.fill(chars,'x');
        write(new String(chars));
        byte[] original=Files.readAllBytes(log.toPath());
        assertTrue(MovementDiagnostics.hasReport(app));
        assertNull(MovementDiagnostics.getClipboardReport(app));
        org.robolectric.android.controller.ActivityController<DebugActivity> controller=
                Robolectric.buildActivity(DebugActivity.class).setup();
        DebugActivity activity=controller.get();
        TextView copy=button(activity.getWindow().getDecorView(),"COPY MOVEMENT LOG");
        assertNotNull(copy);
        copy.performClick();
        assertFalse(((ClipboardManager)app.getSystemService(Context.CLIPBOARD_SERVICE)).hasPrimaryClip());
        AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
        assertTrue(dialog.isShowing());
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        Intent save=Shadows.shadowOf(activity).getNextStartedActivityForResult().intent;
        assertEquals(Intent.ACTION_CREATE_DOCUMENT,save.getAction());
        assertEquals("text/plain",save.getType());
        ByteArrayOutputStream exported=new ByteArrayOutputStream();
        MovementDiagnostics.writeReport(app,exported);
        String full=exported.toString("UTF-8");
        assertTrue(full.contains(new String(original,StandardCharsets.UTF_8)));
        assertTrue(full.contains("End of movement diagnostics export. Log bytes: "+original.length));
        assertArrayEquals(original,Files.readAllBytes(log.toPath()));
        controller.pause().stop().destroy();
    }
    @Test public void unicodeParcelLimitAndClipboardFailureDoNotCrash() {
        assertFalse(DebugActivity.copyReportToClipboard(app,"Log",
                new String(new char[MovementDiagnostics.MAX_CLIPBOARD_CHARS+1])));
        Context failing=new ContextWrapper(app){
            @Override public Object getSystemService(String name){
                throw new RuntimeException("Clipboard Binder transaction rejected");
            }
        };
        assertFalse(DebugActivity.copyReportToClipboard(failing,"Log","small report"));
    }
    @Test public void expiredLogIsNotOfferedOrCopied() throws Exception {
        write("expired");
        app.getSharedPreferences("roadprints_movement_diagnostics",0).edit()
                .putLong("retain_until",System.currentTimeMillis()-1).commit();
        assertFalse(MovementDiagnostics.hasReport(app));
        assertEquals("",MovementDiagnostics.getClipboardReport(app));
        assertFalse(log.exists());
    }
}
