package com.roadprints.capture;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Small, revision-keyed private disk cache for derived screen data. */
final class PersistentScreenCache {
    private static final int FORMAT = 1;
    private PersistentScreenCache() {}

    static JSONObject read(Context context, String key, long revision) {
        File file = file(context, key);
        if (!file.isFile() || file.length() > 12L * 1024L * 1024L) return null;
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int offset = 0, count;
            while (offset < bytes.length && (count = input.read(bytes, offset, bytes.length-offset)) >= 0)
                offset += count;
            JSONObject wrapper = new JSONObject(new String(bytes, 0, offset, StandardCharsets.UTF_8));
            if (wrapper.optInt("format", -1) != FORMAT || wrapper.optLong("revision", -1) != revision)
                return null;
            return wrapper.optJSONObject("data");
        } catch (Exception ignored) {
            return null;
        }
    }

    static void write(Context context, String key, long revision, JSONObject data) {
        if (data == null) return;
        File target = file(context, key);
        File temporary = new File(target.getParentFile(), target.getName() + ".tmp");
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return;
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            JSONObject wrapper = new JSONObject().put("format", FORMAT)
                    .put("revision", revision).put("data", data);
            byte[] bytes = wrapper.toString().getBytes(StandardCharsets.UTF_8);
            if (bytes.length > 12 * 1024 * 1024) { temporary.delete(); return; }
            output.write(bytes);
            output.getFD().sync();
            if (target.exists() && !target.delete()) return;
            if (!temporary.renameTo(target)) temporary.delete();
        } catch (Exception ignored) {
            temporary.delete();
        }
    }

    private static File file(Context context, String key) {
        return new File(new File(context.getFilesDir(), "screen-cache"), key + ".json");
    }
}
