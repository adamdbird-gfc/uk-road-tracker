package com.roadprints.capture;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import org.json.JSONObject;

/** Scan-owned, lossless geometry spill. Only small content addresses survive each journey.
 * Not an evidence store: source archives remain authoritative and are never modified. */
final class AchievementGeometrySpool implements AutoCloseable {
    private static final String PREFIX="roadprints-geometry:";
    private final File directory;
    AchievementGeometrySpool(File cache) throws IOException {
        directory=Files.createTempDirectory(cache.toPath(),"achievement-scan-").toFile();
    }
    String retain(JSONObject geometry) {
        try {
            byte[] bytes=geometry.toString().getBytes(StandardCharsets.UTF_8);
            byte[] hash=MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder name=new StringBuilder();for(byte b:hash)name.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            File file=new File(directory,name.toString());
            if(!file.exists())try(OutputStream out=new BufferedOutputStream(new FileOutputStream(file))){out.write(bytes);}
            return PREFIX+file.getAbsolutePath();
        } catch(Exception failure){throw new IllegalStateException("Cannot spill achievement geometry",failure);}
    }
    static String restore(String value) throws IOException {
        return value.startsWith(PREFIX)?new String(Files.readAllBytes(new File(value.substring(PREFIX.length())).toPath()),StandardCharsets.UTF_8):value;
    }
    public void close(){File[] files=directory.listFiles();if(files!=null)for(File f:files)f.delete();directory.delete();}
}
