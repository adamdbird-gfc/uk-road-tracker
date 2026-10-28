package com.roadprints.capture;
import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class JourneyStoreDeletionTest {
    private Context app;
    @Before public void setup(){app=RuntimeEnvironment.getApplication();JourneyStore.deleteAll(app);}
    @After public void cleanup(){JourneyStore.deleteAll(app);}
    private File write(String name,String content) throws Exception {
        File f=new File(app.getFilesDir(),name);
        try(FileOutputStream out=new FileOutputStream(f)){out.write(content.getBytes(StandardCharsets.UTF_8));}
        return f;
    }
    @Test public void deletesLargeAndCorruptArchivesWithoutParsingAndKeepsOtherFiles() throws Exception {
        File large=new File(app.getFilesDir(),"journey_large.json");
        try(RandomAccessFile out=new RandomAccessFile(large,"rw")){out.setLength(300L*1024*1024);}
        File corrupt=write("journey_corrupt.json","not JSON"), temporary=write("journey_interrupted.tmp","incomplete");
        File keep=write("backup-test.zip","saved backup");long revision=JourneyStore.dataRevision(app);
        try {
            assertEquals(2,JourneyStore.deleteAll(app));
            assertFalse(large.exists());assertFalse(corrupt.exists());assertFalse(temporary.exists());
            assertTrue(keep.exists());assertTrue(JourneyStore.dataRevision(app)>revision);
            assertEquals(0,JourneyStore.count(app));
        } finally {keep.delete();}
    }
    @Test public void clearsUnmigratedLegacyJourneysWithoutRecreatingThem() throws Exception {
        app.getSharedPreferences("roadprints_journeys_v1",Context.MODE_PRIVATE).edit().clear()
                .putString("journey:old","{\"journey_id\":\"old\",\"mode\":\"walking\"}").commit();
        JourneyStore.deleteAll(app);assertEquals(0,JourneyStore.count(app));
        assertFalse(app.getSharedPreferences("roadprints_journeys_v1",Context.MODE_PRIVATE).contains("journey:old"));
        assertFalse(new File(app.getFilesDir(),"journey_old.json").exists());
    }
    @Test public void modeDeletionReadsOnlySummariesAndRetainsOtherModes() throws Exception {
        write("journey_drive.json","{\"journey_id\":\"drive\",\"mode\":\"driving\",\"processing_result\":{\"ignored\":[1,2,3]}}");
        write("journey_walk.json","{\"journey_id\":\"walk\",\"mode\":\"walking\"}");
        assertEquals(1,JourneyStore.countByModes(app,"driving"));
        assertEquals(1,JourneyStore.deleteByModes(app,"driving","bus"));
        assertFalse(new File(app.getFilesDir(),"journey_drive.json").exists());
        assertTrue(new File(app.getFilesDir(),"journey_walk.json").exists());
    }
    @Test public void exportedBackupSurvivesDeleteAllAndRestoresJourneys() throws Exception {
        File saved=write("journey_backup-delete.json","{\"journey_id\":\"backup-delete\",\"mode\":\"walking\"}");
        ByteArrayOutputStream backup=new ByteArrayOutputStream();assertEquals(1,RoadprintsBackup.export(app,backup));
        assertEquals(1,JourneyStore.deleteAll(app));assertFalse(saved.exists());
        assertEquals(1,RoadprintsBackup.restore(app,new ByteArrayInputStream(backup.toByteArray())));
        assertTrue(saved.exists());assertEquals("walking",JourneyStore.get(app,"backup-delete").getString("mode"));
    }
}
