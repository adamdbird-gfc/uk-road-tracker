package com.roadprints.capture;

import android.content.Context;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.JsonWriter;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Writes one archive record at a time. The manifest is written after all records succeed. */
final class BulkJourneyDiagnostics {
    private static final List<String> EVIDENCE = java.util.Arrays.asList(
            "route_geometry.coordinates", "raw_capture_geometry.coordinates",
            "raw_capture_samples", "capture_route_samples", "activity_recognition_history",
            "capture_decisions", "capture_validation", "timeline_source_evidence.route_point_times_ms",
            "timeline_match_evidence.point_times_ms", "processing_result.geojson.features",
            "processing_result.road_geojson.features", "processing_result.cycling_geojson.features",
            "processing_result.motorway_geojson.features", "processing_result.a_road_geojson.features",
            "processing_result.matched_path_evidence", "processing_result.cycling_path_evidence",
            "railway_distance",
            "last_match_attempt", "error_summary", "match_retry_state", "journey_corrections");
    private static final List<String> METADATA = java.util.Arrays.asList(
            "journey_id", "started_at", "ended_at", "mode", "title", "timezone");

    interface Progress { void update(int completed, int total); }

    private BulkJourneyDiagnostics() {}

    static int write(Context context, OutputStream output, BooleanSupplier cancelled, Progress progress)
            throws IOException {
        return write(JourneyStore.diagnosticSources(context), output, cancelled, progress);
    }

    static int write(List<JourneyStore.DiagnosticSource> sources, OutputStream output,
                     BooleanSupplier cancelled, Progress progress) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(output))) {
            List<String[]> index = new ArrayList<>(sources.size());
            Set<String> uniqueIds = new HashSet<>();
            progress.update(0, sources.size());
            for (JourneyStore.DiagnosticSource source : sources) {
                checkCancellation(cancelled);
                String entry = "journeys/" + (index.size() + 1) + ".json";
                zip.putNextEntry(new ZipEntry(entry));
                String[] metadata = new String[METADATA.size()];
                try (JsonReader reader = new JsonReader(source.open())) {
                    JsonWriter writer = writer(zip);
                    Set<String> stored = new HashSet<>();
                    if (reader.peek() != JsonToken.BEGIN_OBJECT) throw new IOException("Invalid journey record.");
                    writer.beginObject().name("journey");
                    copy(reader, writer, "", stored, metadata, cancelled);
                    if (reader.peek() != JsonToken.END_DOCUMENT)
                        throw new IOException("Unexpected content after the journey record.");
                    if (!source.id().equals(metadata[0]) || !uniqueIds.add(metadata[0]))
                        throw new IOException("Missing, inconsistent or duplicate journey ID.");
                    writer.name("stored").beginArray();
                    for (String path : EVIDENCE) if (stored.contains(path)) writer.value(path);
                    writer.endArray().name("unavailable").beginArray();
                    for (String path : EVIDENCE) if (!stored.contains(path)) writer.value(path);
                    writer.endArray().endObject().flush();
                } catch (IOException | RuntimeException error) {
                    checkCancellation(cancelled);
                    throw new IOException("Could not read saved journey " + (index.size() + 1)
                            + " of " + sources.size() + ". It may have changed or be unreadable.", error);
                }
                zip.closeEntry();
                index.add(metadata);
                progress.update(index.size(), sources.size());
            }
            checkCancellation(cancelled);
            zip.putNextEntry(new ZipEntry("manifest.json"));
            JsonWriter manifest = writer(zip);
            manifest.beginObject()
                    .name("format").value("roadprints_bulk_journey_diagnostics")
                    .name("schema_version").value(1)
                    .name("exported_at_utc").value(Instant.now().toString())
                    .name("app_version").value(BuildConfig.VERSION_NAME)
                    .name("app_version_code").value(BuildConfig.VERSION_CODE)
                    .name("journey_count").value(index.size())
                    .name("journeys").beginArray();
            for (String[] record : index) {
                manifest.beginObject();
                for (int i = 0; i < METADATA.size(); i++) manifest.name(METADATA.get(i)).value(record[i]);
                manifest.endObject();
            }
            manifest.endArray().name("export_complete").value(true).endObject().flush();
            zip.closeEntry();
        }
        return sources.size();
    }

    private static JsonWriter writer(ZipOutputStream zip) {
        JsonWriter writer = new JsonWriter(new OutputStreamWriter(zip, StandardCharsets.UTF_8));
        return writer;
    }

    private static void checkCancellation(BooleanSupplier cancelled) throws IOException {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted())
            throw new IOException("Export cancelled.");
    }

    private static void copy(JsonReader reader, JsonWriter writer, String path, Set<String> stored,
                             String[] metadata, BooleanSupplier cancelled) throws IOException {
        checkCancellation(cancelled);
        boolean tracked = EVIDENCE.contains(path);
        switch (reader.peek()) {
            case BEGIN_OBJECT:
                reader.beginObject();
                writer.beginObject();
                if (tracked && reader.hasNext()) stored.add(path);
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    writer.name(key);
                    String child = path.isEmpty() ? key : path + "." + key;
                    copy(reader, writer, child, stored, metadata, cancelled);
                }
                reader.endObject();
                writer.endObject();
                break;
            case BEGIN_ARRAY:
                reader.beginArray();
                writer.beginArray();
                if (tracked && reader.hasNext()) stored.add(path);
                while (reader.hasNext()) copy(reader, writer, "[]", stored, metadata, cancelled);
                reader.endArray();
                writer.endArray();
                break;
            case STRING:
                String value = reader.nextString();
                if (tracked && !value.isEmpty()) stored.add(path);
                int field = METADATA.indexOf(path);
                if (field >= 0) metadata[field] = value;
                writer.value(value);
                break;
            case NUMBER: writer.value(new BigDecimal(reader.nextString())); break;
            case BOOLEAN: writer.value(reader.nextBoolean()); break;
            case NULL: reader.nextNull(); writer.nullValue(); break;
            default: throw new IOException("Invalid journey JSON.");
        }
    }
}
