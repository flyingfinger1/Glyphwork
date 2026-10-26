package de.flyingfinger.dialogui.pack;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** In-memory resource pack. The zip is byte-stable for equal content, so the SHA-1 only changes when the content does. */
public final class PackFiles {

    /** Fixed entry time (2020-01-01) so rebuilding the same content yields the same zip and hash. */
    private static final long FIXED_TIME = 1577836800000L;

    private final Map<String, byte[]> files = new TreeMap<>();

    public void put(String path, byte[] data) {
        files.put(path, data);
    }

    public void put(String path, String text) {
        put(path, text.getBytes(StandardCharsets.UTF_8));
    }

    public byte[] toZip() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> e : files.entrySet()) {
                ZipEntry entry = new ZipEntry(e.getKey());
                entry.setTime(FIXED_TIME);
                zip.putNextEntry(entry);
                zip.write(e.getValue());
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    public static String sha1(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
