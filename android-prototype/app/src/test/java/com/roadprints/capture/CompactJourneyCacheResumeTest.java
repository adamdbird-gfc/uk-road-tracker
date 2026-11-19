package com.roadprints.capture;

import static org.junit.Assert.*;

import android.content.Context;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Owner-size Map preparation can be interrupted after two minutes.
 * Disposable attribution results must persist across cancellation and be
 * reused without rescanning or editing their authoritative journey files.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public final class CompactJourneyCacheResumeTest {
    @Test public void cancelledArchiveScanKeepsCompletedRowsForNextMapAttempt() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        String kind = "map-resume-303-" + System.nanoTime();
        List<File> sources = new ArrayList<>();
        byte[][] originals = new byte[57][];
        File database = new File(context.getCacheDir(), "compact-" + kind + ".db");
        try {
            for (int i = 0; i < originals.length; i++) {
                File file = new File(context.getCacheDir(), "resume303-" + i + ".json");
                byte[] bytes = ("{\"journey_id\":\"resume-" + i + "\"}").getBytes(StandardCharsets.UTF_8);
                Files.write(file.toPath(), bytes);
                originals[i] = bytes;
                sources.add(file);
            }
            try (CompactJourneyCache first = new CompactJourneyCache(context, kind)) {
                for (int i = 0; i < sources.size(); i++) {
                    final int id = i;
                    JSONObject calculated = first.get(sources.get(i),
                            file -> new JSONObject().put("source", id));
                    assertEquals(i, calculated.getInt("source"));
                }
                assertEquals(57, first.reads);
                assertEquals(0, first.hits);
                // An interrupted worker exits through try-with-resources.
                throw new CancellationException("simulate Map deadline");
            } catch (CancellationException expected) {
                assertEquals("simulate Map deadline", expected.getMessage());
            }

            AtomicInteger reprocessed = new AtomicInteger();
            try (CompactJourneyCache resumed = new CompactJourneyCache(context, kind)) {
                for (int i = 0; i < sources.size(); i++) {
                    JSONObject cached = resumed.get(sources.get(i), file -> {
                        reprocessed.incrementAndGet();
                        return new JSONObject().put("incorrect", true);
                    });
                    assertEquals(i, cached.getInt("source"));
                }
                assertEquals(57, resumed.hits);
                assertEquals(0, resumed.reads);
            }
            assertEquals(0, reprocessed.get());
            for (int i = 0; i < sources.size(); i++)
                assertArrayEquals(originals[i], Files.readAllBytes(sources.get(i).toPath()));
        } finally {
            for (File source : sources) Files.deleteIfExists(source.toPath());
            android.database.sqlite.SQLiteDatabase.deleteDatabase(database);
        }
    }

    @Test public void acceptedRoadCacheSurvivesDeletionOfTemporaryAndroidCacheDatabase()
            throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        String kind = RoadReference.ALGORITHM + "-persistent-test-" + System.nanoTime();
        File source = File.createTempFile("durable-local-", ".json", context.getFilesDir());
        File durable = new File(context.getNoBackupFilesDir(), "compact-" + kind + ".db");
        File temporary = new File(context.getCacheDir(), "compact-" + kind + ".db");
        try {
            Files.write(source.toPath(), new JSONObject().put("journey_id", "durable").toString().getBytes(StandardCharsets.UTF_8));
            try (CompactJourneyCache first = new CompactJourneyCache(context, kind)) {
                assertEquals(7, first.get(source, f -> new JSONObject().put("covered", 7))
                        .getInt("covered"));
                assertEquals(1, first.reads);
            }
            assertTrue("Accepted-road cache belongs in no-backup files, not evictable cache",
                    durable.isFile());
            android.database.sqlite.SQLiteDatabase.deleteDatabase(temporary);
            try (CompactJourneyCache later = new CompactJourneyCache(context, kind)) {
                assertEquals(7, later.get(source, f -> {
                    throw new AssertionError("Eviction of temporary files must not cause rematching");
                }).getInt("covered"));
                assertEquals(1, later.hits);
                assertEquals(0, later.reads);
            }
        } finally {
            Files.deleteIfExists(source.toPath());
            android.database.sqlite.SQLiteDatabase.deleteDatabase(durable);
            android.database.sqlite.SQLiteDatabase.deleteDatabase(temporary);
        }
    }

    @Test public void priorTemporaryAcceptedRoadCacheMigratesWithoutReassessment() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        String kind = RoadReference.ALGORITHM + "-legacy-migration-" + System.nanoTime();
        File source = File.createTempFile("legacy-local-", ".json", context.getFilesDir());
        File durable = new File(context.getNoBackupFilesDir(), "compact-" + kind + ".db");
        File legacy = new File(context.getCacheDir(), "compact-" + kind + ".db");
        try {
            Files.write(source.toPath(), new JSONObject().put("journey_id", "legacy").toString().getBytes(StandardCharsets.UTF_8));
            // Simulate the pre-309 schema under Android's evictable cache.
            try (android.database.sqlite.SQLiteDatabase old =
                    android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(legacy, null)) {
                old.execSQL("CREATE TABLE rows (kind TEXT,source TEXT,token TEXT,data BLOB,sha TEXT,"
                        + "PRIMARY KEY(kind,source))");
                old.execSQL("CREATE TABLE row_index (kind TEXT,source TEXT,token TEXT,data TEXT,sha TEXT,"
                        + "PRIMARY KEY(kind,source))");
                byte[] bytes = new JSONObject().put("covered", 42)
                        .toString().getBytes(StandardCharsets.UTF_8);
                android.content.ContentValues row = new android.content.ContentValues();
                row.put("kind", kind);
                row.put("source", source.getName());
                row.put("token", JourneyStore.fileToken(source));
                row.put("data", bytes);
                row.put("sha", NcnJson.hash(bytes));
                old.insertOrThrow("rows", null, row);
            }
            try (CompactJourneyCache migrated = new CompactJourneyCache(context, kind)) {
                assertEquals(42, migrated.get(source, f -> {
                    throw new AssertionError("Valid historical contribution must be migrated");
                }).getInt("covered"));
                assertEquals(1, migrated.hits);
                assertEquals(0, migrated.reads);
            }
            assertTrue(durable.isFile());
            android.database.sqlite.SQLiteDatabase.deleteDatabase(legacy);
            try (CompactJourneyCache retained = new CompactJourneyCache(context, kind)) {
                assertEquals(42, retained.get(source, f -> {
                    throw new AssertionError("Migrated durable contribution must survive legacy removal");
                }).getInt("covered"));
                assertEquals(1, retained.hits);
            }
        } finally {
            Files.deleteIfExists(source.toPath());
            android.database.sqlite.SQLiteDatabase.deleteDatabase(durable);
            android.database.sqlite.SQLiteDatabase.deleteDatabase(legacy);
        }
    }

    @Test public void changedJourneyInvalidatesOnlyItsOwnDerivedContribution() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        String kind = "map-resume-change-303-" + System.nanoTime();
        File one = File.createTempFile("resume303-a-", ".json", context.getCacheDir());
        File two = File.createTempFile("resume303-b-", ".json", context.getCacheDir());
        File database = new File(context.getCacheDir(), "compact-" + kind + ".db");
        try {
            Files.write(one.toPath(), "{\"journey_id\":\"one\"}".getBytes(StandardCharsets.UTF_8));
            Files.write(two.toPath(), "{\"journey_id\":\"two\"}".getBytes(StandardCharsets.UTF_8));
            try (CompactJourneyCache initial = new CompactJourneyCache(context, kind)) {
                initial.get(one, f -> new JSONObject().put("generation", 1));
                initial.get(two, f -> new JSONObject().put("generation", 1));
            }
            Files.write(one.toPath(), "{\"journey_id\":\"one\",\"corrected\":true}".getBytes(StandardCharsets.UTF_8));
            try (CompactJourneyCache resumed = new CompactJourneyCache(context, kind)) {
                assertEquals(2, resumed.get(one,
                        f -> new JSONObject().put("generation", 2)).getInt("generation"));
                assertEquals(1, resumed.get(two,
                        f -> { throw new AssertionError("unchanged journey must reuse its cache"); })
                        .getInt("generation"));
                assertEquals(1, resumed.reads);
                assertEquals(1, resumed.hits);
            }
        } finally {
            Files.deleteIfExists(one.toPath());
            Files.deleteIfExists(two.toPath());
            android.database.sqlite.SQLiteDatabase.deleteDatabase(database);
        }
    }
}
