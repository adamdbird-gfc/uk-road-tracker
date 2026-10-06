package com.roadprints.capture;

import android.content.Context;
import android.os.Build;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.JsonWriter;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/** Local, single-journey export. Never loads the whole archive or serialises
 * a large route on the UI thread. No journey data is sent over the network. */
final class JourneyDebugExporter {
    private JourneyDebugExporter() {}

    static void write(Context context, String journeyId, OutputStream output) throws IOException {
        try (InputStream snapshot = JourneyStore.openDiagnosticSnapshot(context, journeyId)) {
            writeSnapshot(snapshot, output);
        }
    }

    static void writeSnapshot(InputStream snapshot, OutputStream output) throws IOException {
        try (JsonReader reader = new JsonReader(new InputStreamReader(snapshot, StandardCharsets.UTF_8));
             JsonWriter writer = new JsonWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
            writer.setIndent("  ");
            writer.beginObject();
            writer.name("format").value("roadprints_journey_diagnostics");
            writer.name("schema_version").value(1);
            writer.name("exported_at_utc").value(Instant.now().toString());
            writer.name("app_version").value(BuildConfig.VERSION_NAME);
            writer.name("app_version_code").value(BuildConfig.VERSION_CODE);
            writer.name("device").value(Build.MANUFACTURER + " " + Build.MODEL);
            writer.name("android_api").value(Build.VERSION.SDK_INT);
            writer.name("data_notes").value("Saved journey snapshot, including stored route, matching results and errors. "
                    + "Point timestamps and accuracy are included only where already stored. "
                    + "An edited route may differ from its original recording.");
            writer.name("journey");
            if (reader.peek() != JsonToken.BEGIN_OBJECT) throw new IOException("Invalid journey archive");
            copyValue(reader, writer);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("Unexpected archive content");
            writer.name("export_complete").value(true);
            writer.endObject();
        }
    }

    private static void copyValue(JsonReader reader, JsonWriter writer) throws IOException {
        switch (reader.peek()) {
            case BEGIN_OBJECT:
                reader.beginObject(); writer.beginObject();
                while (reader.hasNext()) { writer.name(reader.nextName()); copyValue(reader, writer); }
                reader.endObject(); writer.endObject(); break;
            case BEGIN_ARRAY:
                reader.beginArray(); writer.beginArray();
                while (reader.hasNext()) copyValue(reader, writer);
                reader.endArray(); writer.endArray(); break;
            case STRING: writer.value(reader.nextString()); break;
            case NUMBER: writer.value(new BigDecimal(reader.nextString())); break;
            case BOOLEAN: writer.value(reader.nextBoolean()); break;
            case NULL: reader.nextNull(); writer.nullValue(); break;
            default: throw new IOException("Invalid journey JSON");
        }
    }
}
