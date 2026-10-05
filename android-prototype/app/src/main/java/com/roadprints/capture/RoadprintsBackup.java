package com.roadprints.capture;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Portable local backup for Roadprints' app-private user data.
 * Movement diagnostics are deliberately excluded because they contain precise GPS samples.
 */
final class RoadprintsBackup {
    private static final int SCHEMA_VERSION = 1;
    private static final long MAX_ARCHIVE_BYTES = 1024L * 1024L * 1024L;
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final String DIAGNOSTICS_PREFS = "roadprints_movement_diagnostics";
    private static final String DIAGNOSTICS_FILE = "roadprints_movement_diagnostics.jsonl";

    private RoadprintsBackup() {}

    static int export(Context context, OutputStream destination) throws Exception {
        Context app = context.getApplicationContext();
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(destination))) {
            JSONObject manifest = new JSONObject();
            manifest.put("format", "roadprints-backup");
            manifest.put("schema_version", SCHEMA_VERSION);
            manifest.put("application_id", app.getPackageName());
            manifest.put("created_at_utc", java.time.Instant.now().toString());
            putEntry(zip, "manifest.json", manifest.toString().getBytes(StandardCharsets.UTF_8));

            List<File> files = new ArrayList<>();
            collectFiles(app.getFilesDir(), app.getFilesDir(), files);
            files.sort(Comparator.comparing(File::getAbsolutePath));
            int journeyCount = 0;
            for (File file : files) {
                String relative = relativePath(app.getFilesDir(), file);
                if (relative.startsWith("screen-cache/") || relative.equals(DIAGNOSTICS_FILE)
                        || relative.endsWith(".tmp")) continue;
                if (relative.startsWith("journey_") && relative.endsWith(".json")) journeyCount++;
                zip.putNextEntry(new ZipEntry("files/" + relative));
                try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
                    copy(input, zip, Long.MAX_VALUE, null);
                }
                zip.closeEntry();
            }

            File preferencesDirectory = new File(app.getApplicationInfo().dataDir, "shared_prefs");
            File[] preferenceFiles = preferencesDirectory.listFiles((dir, name) ->
                    name.endsWith(".xml") && !name.equals(DIAGNOSTICS_PREFS + ".xml"));
            if (preferenceFiles != null) {
                List<File> sortedPreferences = new ArrayList<>();
                Collections.addAll(sortedPreferences, preferenceFiles);
                sortedPreferences.sort(Comparator.comparing(File::getName));
                for (File preferenceFile : sortedPreferences) {
                    String name = preferenceFile.getName().substring(
                            0, preferenceFile.getName().length() - 4);
                    JSONObject payload = encodePreferences(app.getSharedPreferences(name, Context.MODE_PRIVATE), name);
                    putEntry(zip, "preferences/" + name + ".json",
                            payload.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            zip.finish();
            return journeyCount;
        }
    }

    static int restore(Context context, InputStream source) throws Exception {
        Context app = context.getApplicationContext();
        if (CaptureService.isActive(app)) {
            throw new IllegalStateException("Stop the current journey before restoring a backup.");
        }

        File stage = new File(app.getCacheDir(), "roadprints-restore-" + System.currentTimeMillis());
        deleteRecursively(stage);
        if (!stage.mkdirs()) throw new IllegalStateException("Could not prepare a restore workspace.");

        boolean validManifest = false;
        long[] total = {0L};
        Set<String> seen = new HashSet<>();
        int journeyCount = 0;
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(source))) {
            ZipEntry entry;
            byte[] buffer = new byte[BUFFER_SIZE];
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (!seen.add(name)) throw new IllegalArgumentException("Backup contains a duplicate item.");
                if ("manifest.json".equals(name)) {
                    JSONObject manifest = new JSONObject(new String(readLimited(zip, 64 * 1024, total),
                            StandardCharsets.UTF_8));
                    if (!"roadprints-backup".equals(manifest.optString("format"))
                            || manifest.optInt("schema_version", -1) != SCHEMA_VERSION
                            || !app.getPackageName().equals(manifest.optString("application_id"))) {
                        throw new IllegalArgumentException("This is not a compatible Roadprints backup.");
                    }
                    validManifest = true;
                } else if (name.startsWith("files/")) {
                    String relative = safeRelativePath(name.substring("files/".length()));
                    if (relative == null || relative.isEmpty()
                            || relative.equals(DIAGNOSTICS_FILE)
                            || relative.startsWith("screen-cache/")
                            || relative.endsWith(".tmp")) {
                        throw new IllegalArgumentException("Backup contains an unsupported file path.");
                    }
                    File target = new File(stage, "files/" + relative);
                    File parent = target.getParentFile();
                    if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                        throw new IllegalStateException("Could not prepare backup data.");
                    }
                    long copied = copy(zip, new FileOutputStream(target), MAX_ARCHIVE_BYTES - total[0], total);
                    if (relative.startsWith("journey_") && relative.endsWith(".json")) journeyCount++;
                } else if (name.startsWith("preferences/") && name.endsWith(".json")) {
                    String preferenceName = name.substring("preferences/".length(),
                            name.length() - ".json".length());
                    if (!safePreferenceName(preferenceName) || DIAGNOSTICS_PREFS.equals(preferenceName)) {
                        throw new IllegalArgumentException("Backup contains an unsupported preference.");
                    }
                    byte[] bytes = readLimited(zip, 32 * 1024 * 1024, total);
                    JSONObject payload = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
                    if (!preferenceName.equals(payload.optString("name"))
                            || payload.optJSONArray("values") == null) {
                        throw new IllegalArgumentException("Backup contains invalid settings data.");
                    }
                    File target = new File(stage, "preferences/" + preferenceName + ".json");
                    File parent = target.getParentFile();
                    if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                        throw new IllegalStateException("Could not prepare backup settings.");
                    }
                    try (FileOutputStream output = new FileOutputStream(target)) {
                        output.write(bytes);
                    }
                } else {
                    throw new IllegalArgumentException("Backup contains an unrecognized item.");
                }
                zip.closeEntry();
            }
            if (!validManifest) throw new IllegalArgumentException("Roadprints backup information is missing.");
            replaceFiles(app, new File(stage, "files"));
            replacePreferences(app, new File(stage, "preferences"));
            return journeyCount;
        } finally {
            deleteRecursively(stage);
        }
    }

    private static JSONObject encodePreferences(SharedPreferences preferences, String name)
            throws Exception {
        JSONObject result = new JSONObject();
        result.put("name", name);
        JSONArray values = new JSONArray();
        List<String> keys = new ArrayList<>(preferences.getAll().keySet());
        Collections.sort(keys);
        Map<String, ?> all = preferences.getAll();
        for (String key : keys) {
            Object value = all.get(key);
            if (value == null) continue;
            JSONObject item = new JSONObject();
            item.put("key", key);
            if (value instanceof String) {
                item.put("type", "string"); item.put("value", value);
            } else if (value instanceof Boolean) {
                item.put("type", "boolean"); item.put("value", value);
            } else if (value instanceof Integer) {
                item.put("type", "int"); item.put("value", value);
            } else if (value instanceof Long) {
                item.put("type", "long"); item.put("value", value);
            } else if (value instanceof Float) {
                item.put("type", "float"); item.put("value", value);
            } else if (value instanceof Set) {
                JSONArray set = new JSONArray();
                List<String> members = new ArrayList<>();
                for (Object member : (Set<?>) value) if (member instanceof String) members.add((String) member);
                Collections.sort(members);
                for (String member : members) set.put(member);
                item.put("type", "string_set"); item.put("value", set);
            } else {
                continue;
            }
            values.put(item);
        }
        result.put("values", values);
        return result;
    }

    private static void replaceFiles(Context app, File stagedFiles) throws Exception {
        File files = app.getFilesDir();
        File[] current = files.listFiles();
        if (current != null) {
            for (File file : current) {
                if (DIAGNOSTICS_FILE.equals(file.getName())) continue;
                deleteRecursively(file);
            }
        }
        if (!stagedFiles.isDirectory()) return;
        copyTree(stagedFiles, stagedFiles, files);
    }

    private static void replacePreferences(Context app, File stagedPreferences) throws Exception {
        File directory = new File(app.getApplicationInfo().dataDir, "shared_prefs");
        File[] files = directory.listFiles((dir, name) ->
                name.endsWith(".xml") && !name.equals(DIAGNOSTICS_PREFS + ".xml"));
        if (files != null) {
            for (File file : files) {
                String name = file.getName().substring(0, file.getName().length() - 4);
                if (!app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()) {
                    throw new IllegalStateException("Could not replace saved settings.");
                }
            }
        }

        File[] payloads = stagedPreferences.listFiles((dir, name) -> name.endsWith(".json"));
        if (payloads == null) return;
        for (File payloadFile : payloads) {
            String name = payloadFile.getName().substring(0, payloadFile.getName().length() - 5);
            JSONObject payload;
            try (FileInputStream input = new FileInputStream(payloadFile)) {
                byte[] bytes = readLimited(input, 32 * 1024 * 1024);
                payload = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            }
            SharedPreferences.Editor editor = app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear();
            JSONArray values = payload.optJSONArray("values");
            for (int i = 0; values != null && i < values.length(); i++) {
                JSONObject item = values.optJSONObject(i);
                if (item == null) continue;
                String key = item.optString("key", "");
                if (key.isEmpty()) continue;
                Object value = item.opt("value");
                switch (item.optString("type")) {
                    case "string": if (value instanceof String) editor.putString(key, (String) value); break;
                    case "boolean": if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value); break;
                    case "int": if (value instanceof Number) editor.putInt(key, ((Number) value).intValue()); break;
                    case "long": if (value instanceof Number) editor.putLong(key, ((Number) value).longValue()); break;
                    case "float": if (value instanceof Number) editor.putFloat(key, ((Number) value).floatValue()); break;
                    case "string_set":
                        JSONArray setValues = item.optJSONArray("value");
                        if (setValues != null) {
                            Set<String> set = new HashSet<>();
                            for (int j = 0; j < setValues.length(); j++) {
                                String member = setValues.optString(j, null);
                                if (member != null) set.add(member);
                            }
                            editor.putStringSet(key, set);
                        }
                        break;
                    default: throw new IllegalArgumentException("Backup has an unsupported setting type.");
                }
            }
            if (!editor.commit()) throw new IllegalStateException("Could not restore saved settings.");
        }
    }

    private static void collectFiles(File root, File directory, List<File> result) {
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                if (!"screen-cache".equals(file.getName())) collectFiles(root, file, result);
            } else if (file.isFile() && !DIAGNOSTICS_FILE.equals(file.getName())
                    && !file.getName().endsWith(".tmp")) {
                result.add(file);
            }
        }
    }

    private static void copyTree(File root, File source, File destinationRoot) throws Exception {
        File[] children = source.listFiles();
        if (children == null) return;
        for (File child : children) {
            File target = new File(destinationRoot, relativePath(root, child));
            if (child.isDirectory()) {
                if (!target.isDirectory() && !target.mkdirs()) throw new IllegalStateException("Could not restore data files.");
                copyTree(root, child, destinationRoot);
            } else {
                File parent = target.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IllegalStateException("Could not restore data files.");
                try (InputStream input = new FileInputStream(child); FileOutputStream output = new FileOutputStream(target)) {
                    copy(input, output, Long.MAX_VALUE, null);
                }
            }
        }
    }

    private static void putEntry(ZipOutputStream zip, String name, byte[] bytes) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    private static byte[] readLimited(InputStream input, int limit, long[] total) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        copy(input, output, limit, total);
        return output.toByteArray();
    }

    private static byte[] readLimited(InputStream input, int limit) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        copy(input, output, limit, null);
        return output.toByteArray();
    }

    private static long copy(InputStream input, OutputStream output, long maximum, long[] total)
            throws Exception {
        byte[] buffer = new byte[BUFFER_SIZE];
        long copied = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            copied += count;
            if (copied > maximum || (total != null && total[0] + count > MAX_ARCHIVE_BYTES)) {
                throw new IllegalArgumentException("Backup is larger than Roadprints can restore.");
            }
            output.write(buffer, 0, count);
            if (total != null) total[0] += count;
        }
        return copied;
    }

    private static String relativePath(File root, File file) {
        String prefix = root.getAbsolutePath() + File.separator;
        return file.getAbsolutePath().startsWith(prefix)
                ? file.getAbsolutePath().substring(prefix.length()).replace(File.separatorChar, '/')
                : "";
    }

    private static String safeRelativePath(String path) {
        if (path == null || path.isEmpty() || path.startsWith("/") || path.indexOf('\\') >= 0) return null;
        String[] segments = path.split("/");
        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) return null;
        }
        return path;
    }

    private static boolean safePreferenceName(String value) {
        return value != null && value.matches("[A-Za-z0-9_.-]{1,120}");
    }

    private static void copy(InputStream input, OutputStream output, long maximum, Object ignored)
            throws Exception {
        copy(input, output, maximum, (long[]) null);
    }

    private static void copyTree(File root, File source, File destinationRoot, boolean ignored)
            throws Exception {
        copyTree(root, source, destinationRoot);
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        file.delete();
    }
}
