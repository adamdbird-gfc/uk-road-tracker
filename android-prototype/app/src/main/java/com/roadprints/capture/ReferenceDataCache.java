package com.roadprints.capture;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;

/** Persistent public reference cache. No journey revisions, app versions or TTL eviction. */
final class ReferenceDataCache {
    interface Loader { byte[] load() throws Exception; }
    interface Validator { void validate(byte[] bytes) throws Exception; }
    private static final Object LOCK = new Object();
    private static final long RETRY_MS = 60L * 60 * 1000;
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private final File directory;
    ReferenceDataCache(File directory) { this.directory = directory; }
    byte[] get(String dataset, String version, Loader loader, Validator validator) throws Exception {
        if (!dataset.matches("[A-Za-z0-9_-]{1,100}") || version.length() > 200)
            throw new IllegalArgumentException("Invalid reference identity");
        // Shared across instances; a waiting consumer rechecks the committed file.
        synchronized (LOCK) {
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Reference cache unavailable");
            File target = new File(directory, dataset + ".ref"), retry = new File(directory, dataset + ".retry");
            byte[] previous = null; String existing = "none";
            try (DataInputStream in = new DataInputStream(new FileInputStream(target))) {
                existing = in.readUTF(); String checksum = in.readUTF(); int size = in.readInt();
                if (size < 1 || size > MAX_BYTES) throw new IOException("Invalid reference length");
                previous = new byte[size]; in.readFully(previous);
                if (in.read() != -1 || !checksum.equals(digest(previous))) throw new IOException("Reference integrity failure");
                validator.validate(previous);
            } catch (Exception corrupt) { previous = null; existing = "none"; }
            if (previous != null && version.equals(existing)) {
                ReferenceDataDiagnostics.cache("settlement-boundary", version, existing, true, "valid");
                return previous;
            }
            long now = System.currentTimeMillis();
            if (retry.isFile() && now - retry.lastModified() >= 0 && now - retry.lastModified() < RETRY_MS) {
                ReferenceDataDiagnostics.cache("settlement-boundary", version, existing, false, "retry_deferred");
                if (previous != null) return previous;
                throw new IOException("Reference retry deferred");
            }
            ReferenceDataDiagnostics.cache("settlement-boundary", version, existing, false, "missing_corrupt_or_version");
            File temp = new File(directory, dataset + ".tmp");
            try {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
                byte[] bytes = loader.load();
                if (bytes.length < 1 || bytes.length > MAX_BYTES) throw new IOException("Reference size limit");
                validator.validate(bytes);
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
                try (FileOutputStream file = new FileOutputStream(temp); DataOutputStream out = new DataOutputStream(file)) {
                    out.writeUTF(version); out.writeUTF(digest(bytes)); out.writeInt(bytes.length); out.write(bytes);
                    out.flush(); file.getFD().sync();
                }
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                retry.delete(); return bytes;
            } catch (Exception failure) {
                temp.delete();
                try (FileOutputStream out = new FileOutputStream(retry)) { out.write(0); }
                if (previous != null) return previous;
                throw failure;
            }
        }
    }
    private static String digest(byte[] bytes) throws Exception {
        StringBuilder out = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes))
            out.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return out.toString();
    }
}
