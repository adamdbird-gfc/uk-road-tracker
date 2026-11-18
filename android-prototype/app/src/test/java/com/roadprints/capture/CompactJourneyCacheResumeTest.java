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
                byte[] bytes = ("{\\"journey_id\\":\\"resume-" + i + "\\"}").getBytes(StandardCharsets.UTF_8);
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

    @Test public void changedJourneyInvalidatesOnlyItsOwnDerivedContribution() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        String kind = "map-resume-change-303-" + System.nanoTime();
        File one = File.createTempFile("resume303-a-", ".json", context.getCacheDir());
        File two = File.createTempFile("resume303-b-", ".json", context.getCacheDir());
        File database = new File(context.getCacheDir(), "compact-" + kind + ".db");
        try {
            Files.writeString(one.toPath(), "{\\"journey_id\\":\\"one\\"}");
            Files.writeString(two.toPath(), "{\\"journey_id\\":\\"two\\"}");
            try (CompactJourneyCache initial = new CompactJourneyCache(context, kind)) {
                initial.get(one, f -> new JSONObject().put("generation", 1));
                initial.get(two, f -> new JSONObject().put("generation", 1));
            }
            Files.writeString(one.toPath(), "{\\"journey_id\\":\\"one\\",\\"corrected\\":true}");
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
