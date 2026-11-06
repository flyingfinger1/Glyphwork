package de.flyingfinger.glyphwork.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * {@code pack.include-source-pack}: the server's own resource pack goes into the Glyphwork pack, so
 * the client gets one pack. The server pack keeps its pack.mcmeta (overlays, filters, languages)
 * and pack.png; for every other file both have, Glyphwork's wins, and {@code assets/glyphwork/} always
 * comes from Glyphwork (a server pack with an old Glyphwork part merged in stays harmless).
 */
public final class IncludedPack {

    /** Resource pack format of the client Glyphwork targets (26.3). */
    public static final int PACK_FORMAT = 97;

    private IncludedPack() {
    }

    /** What was taken over. */
    public record Result(int files, int skipped) {
    }

    public static Result include(PackFiles pack, ZipFile source, Logger log) throws IOException {
        int files = 0;
        int skipped = 0;
        Enumeration<? extends ZipEntry> entries = source.entries();
        while (entries.hasMoreElements()) {
            ZipEntry e = entries.nextElement();
            String name = e.getName().replace('\\', '/');
            if (e.isDirectory() || name.startsWith("/") || name.contains("../") || name.startsWith("__MACOSX/")) {
                continue;
            }
            byte[] data;
            try (InputStream in = source.getInputStream(e)) {
                data = in.readAllBytes();
            }
            if (name.equals("pack.mcmeta")) {
                if (checkFormat(data, log)) {
                    pack.put(name, data);
                }
                continue;
            }
            if (name.equals("pack.png")) {
                pack.put(name, data);
                continue;
            }
            if (name.startsWith("assets/glyphwork/") || pack.has(name)) {
                skipped++;
                continue;
            }
            pack.put(name, data);
            files++;
        }
        return new Result(files, skipped);
    }

    /**
     * Whether the server pack's pack.mcmeta can be used: it must be JSON with a "pack" section. A
     * format range without {@link #PACK_FORMAT} is only reported (the client calls such a pack
     * incompatible but still loads it).
     */
    static boolean checkFormat(byte[] mcmeta, Logger log) {
        JsonObject pack;
        try {
            JsonElement root = JsonParser.parseString(new String(mcmeta, StandardCharsets.UTF_8));
            pack = root.isJsonObject() ? root.getAsJsonObject().getAsJsonObject("pack") : null;
        } catch (RuntimeException e) {
            pack = null;
        }
        if (pack == null) {
            log.warning("include-source-pack: the server pack's pack.mcmeta is not valid; Glyphwork's is used");
            return false;
        }
        int min = major(pack.get("min_format"), Integer.MIN_VALUE);
        int max = major(pack.get("max_format"), Integer.MAX_VALUE);
        if (!pack.has("min_format") && !pack.has("max_format")) {
            int f = major(pack.get("pack_format"), -1);
            min = f;
            max = f;
            JsonElement range = pack.get("supported_formats");
            if (range != null && range.isJsonArray() && range.getAsJsonArray().size() == 2) {
                JsonArray a = range.getAsJsonArray();
                min = a.get(0).getAsInt();
                max = a.get(1).getAsInt();
            } else if (range != null && range.isJsonObject()) {
                min = major(range.getAsJsonObject().get("min_inclusive"), min);
                max = major(range.getAsJsonObject().get("max_inclusive"), max);
            }
        }
        if (PACK_FORMAT < min || PACK_FORMAT > max) {
            log.warning("include-source-pack: the server pack is made for pack format "
                + (min == max ? String.valueOf(min) : min + " to " + max) + ", the client uses " + PACK_FORMAT
                + "; it may show the pack as incompatible");
        }
        return true;
    }

    /** A format number, or the first of [major, minor]. */
    private static int major(JsonElement e, int fallback) {
        try {
            if (e == null || e.isJsonNull()) {
                return fallback;
            }
            if (e.isJsonArray()) {
                return e.getAsJsonArray().get(0).getAsInt();
            }
            return e.getAsInt();
        } catch (RuntimeException ex) {
            return fallback;
        }
    }
}
