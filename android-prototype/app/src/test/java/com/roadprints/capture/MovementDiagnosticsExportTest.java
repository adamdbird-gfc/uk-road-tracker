package com.roadprints.capture;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class MovementDiagnosticsExportTest {
    private Context app;
    @Before public void setUp(){
        app=RuntimeEnvironment.getApplication();
        MovementDiagnostics.clear(app);
    }
    @Test public void exportsBeyondClipboardLengthWithExactSourceAndEndMarker() throws Exception {
        MovementDiagnostics.start(app);
        for(int i=0;i<800;i++)MovementDiagnostics.recordEvent(app,"sample","Entry "+i);
        File file=new File(app.getFilesDir(),"roadprints_movement_diagnostics.jsonl");
        byte[] source=Files.readAllBytes(file.toPath());
        assertTrue(source.length>20000);
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        MovementDiagnostics.writeReport(app,output);
        String report=output.toString("UTF-8");
        String header="Roadprints movement diagnostics (precise location; stored locally only)\n";
        assertTrue(report.startsWith(header+new String(source,StandardCharsets.UTF_8)));
        assertTrue(report.contains("Entry 799"));
        assertTrue(report.contains("End of movement diagnostics export. Log bytes: "+source.length));
        assertArrayEquals(source,Files.readAllBytes(file.toPath()));
        assertTrue(MovementDiagnostics.isRunning(app));
    }
    @Test public void reportsStorageSafetyLimitWithoutHidingIt() throws Exception {
        MovementDiagnostics.start(app);
        app.getSharedPreferences("roadprints_movement_diagnostics",Context.MODE_PRIVATE)
                .edit().putBoolean("truncated",true).commit();
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        MovementDiagnostics.writeReport(app,output);
        assertTrue(output.toString("UTF-8").contains("later samples were omitted"));
    }
    @Test(expected=IOException.class) public void missingLogDoesNotClaimSuccessfulExport() throws Exception {
        MovementDiagnostics.writeReport(app,new ByteArrayOutputStream());
    }
}
