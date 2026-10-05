package com.roadprints.capture;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class RoadprintsBackupTest {
    @Test public void exportedBackupRestoresJourneyFilesPreferencesAndKeepsMovementLogSeparate()
            throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File journey = new File(context.getFilesDir(), "journey_backup-roundtrip.json");
        byte[] originalJourney = "{\"journey_id\":\"backup-roundtrip\",\"mode\":\"walking\"}"
                .getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream output = new FileOutputStream(journey)) {
            output.write(originalJourney);
        }

        SharedPreferences preferences = context.getSharedPreferences("backup_roundtrip", Context.MODE_PRIVATE);
        Set<String> originalSet = new HashSet<>();
        originalSet.add("M25");
        originalSet.add("A2");
        assertTrue(preferences.edit().putString("traveller", "national")
                .putLong("revision", 8_000_000_000L).putStringSet("roads", originalSet).commit());

        File movementLog = new File(context.getFilesDir(), "roadprints_movement_diagnostics.jsonl");
        try (FileOutputStream output = new FileOutputStream(movementLog)) {
            output.write("precise-location-log".getBytes(StandardCharsets.UTF_8));
        }

        ByteArrayOutputStream backup = new ByteArrayOutputStream();
        assertEquals(1, RoadprintsBackup.export(context, backup));

        try (FileOutputStream output = new FileOutputStream(journey)) {
            output.write("changed".getBytes(StandardCharsets.UTF_8));
        }
        assertTrue(preferences.edit().clear().commit());

        assertEquals(1, RoadprintsBackup.restore(context, new ByteArrayInputStream(backup.toByteArray())));

        byte[] restoredJourney = new byte[(int) journey.length()];
        try (java.io.FileInputStream input = new java.io.FileInputStream(journey)) {
            assertEquals(restoredJourney.length, input.read(restoredJourney));
        }
        assertEquals(new String(originalJourney, StandardCharsets.UTF_8),
                new String(restoredJourney, StandardCharsets.UTF_8));
        SharedPreferences restored = context.getSharedPreferences("backup_roundtrip", Context.MODE_PRIVATE);
        assertEquals("national", restored.getString("traveller", ""));
        assertEquals(8_000_000_000L, restored.getLong("revision", 0));
        assertEquals(originalSet, restored.getStringSet("roads", java.util.Collections.emptySet()));
        assertTrue(movementLog.isFile());
        assertFalse(movementLog.length() == 0);
    }
}
