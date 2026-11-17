package com.roadprints.capture;

import static org.junit.Assert.*;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/** A valid older driving-only derived row cannot suppress newer on-foot road attribution. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class MapJourneyStoreCacheMigrationTest {
    @Test public void priorDrivingOnlyCacheIsNotReusedAfterMixedModeEligibilityChange()
            throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File source = new File(context.getCacheDir(), "map-mode-cache-migration-test.json");
        byte[] original = "{\"mode\":\"walking\",\"journey_id\":\"cache-migration\"}"
                .getBytes(StandardCharsets.UTF_8);
        Files.write(source.toPath(), original);
        File database = new File(context.getCacheDir(), "map-journeys-v1.db");
        String token = JourneyStore.fileToken(source);
        String name = source.getName();
        try {
            try (SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(database, null)) {
                db.execSQL("CREATE TABLE IF NOT EXISTS entries (source TEXT PRIMARY KEY, token TEXT NOT NULL, format INTEGER NOT NULL, header BLOB NOT NULL, header_sha TEXT NOT NULL, payload BLOB, payload_sha TEXT)");
                byte[] header = original;
                byte[] oldPayload = "{\"motorway\":[],\"a_road\":[]}"
                        .getBytes(StandardCharsets.UTF_8);
                ContentValues row = new ContentValues();
                row.put("source", name);
                row.put("token", token);
                row.put("format", 2); // Written before walking/cycling could count.
                row.put("header", header);
                row.put("header_sha", NcnJson.hash(header));
                row.put("payload", oldPayload);
                row.put("payload_sha", NcnJson.hash(oldPayload));
                db.insertWithOnConflict("entries", null, row, SQLiteDatabase.CONFLICT_REPLACE);
            }
            AtomicInteger recalculations = new AtomicInteger();
            try (MapJourneyStore store = new MapJourneyStore(context)) {
                var record = store.new Record(source, token,
                        new JSONObject().put("mode", "walking"),
                        new JSONObject(new String(original, StandardCharsets.UTF_8)), true);
                JSONObject rebuilt = record.prepared(raw -> {
                    recalculations.incrementAndGet();
                    assertEquals("walking", raw.getString("mode"));
                    return new JSONObject().put("motorway", new JSONArray())
                            .put("a_road", new JSONArray().put(
                                    new JSONObject().put("id", "GB:A1")));
                });
                assertEquals("The previous format's empty A-road result must be rebuilt",
                        1, recalculations.get());
                assertEquals(1, rebuilt.getJSONArray("a_road").length());
                assertEquals(1, store.rebuilt);
                assertEquals(0, store.reused);
            }
            assertArrayEquals("Original journey evidence must remain byte-identical",
                    original, Files.readAllBytes(source.toPath()));
        } finally {
            try (SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(database, null)) {
                db.delete("entries", "source=?", new String[]{name});
            }
            Files.deleteIfExists(source.toPath());
        }
    }
}
