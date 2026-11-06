package de.flyingfinger.glyphwork.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** pack.include-source-pack: the server pack inside the Glyphwork pack. */
class IncludedPackTest {

    private static Path zip(Path dir, Map<String, String> files) throws Exception {
        Path p = dir.resolve("server.zip");
        try (OutputStream out = Files.newOutputStream(p); ZipOutputStream z = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> e : files.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
        return p;
    }

    private static Map<String, String> unzip(byte[] zip) throws Exception {
        Map<String, String> files = new HashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                files.put(e.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return files;
    }

    private static List<String> warnings(Logger log) {
        List<String> out = new ArrayList<>();
        log.setUseParentHandlers(false);
        log.addHandler(new Handler() {
            @Override public void publish(LogRecord r) { out.add(r.getMessage()); }
            @Override public void flush() { }
            @Override public void close() { }
        });
        return out;
    }

    @Test
    void serverFilesComeInGlyphworkKeepsItsOwn(@TempDir Path dir) throws Exception {
        PackFiles pack = new PackFiles();
        pack.put("pack.mcmeta", "{\"pack\":{\"description\":\"Glyphwork\",\"min_format\":97,\"max_format\":97}}");
        pack.put("assets/glyphwork/font/i0.json", "glyphwork");
        pack.put("assets/minecraft/textures/gui/sprites/dialog/warning_button.png", "glyphwork warning");
        Map<String, String> server = new LinkedHashMap<>();
        server.put("pack.mcmeta", "{\"pack\":{\"description\":\"My server\",\"min_format\":97,\"max_format\":97},"
            + "\"overlays\":{\"entries\":[]}}");
        server.put("pack.png", "icon");
        server.put("assets/myserver/textures/item/ruby.png", "ruby");
        server.put("assets/minecraft/textures/item/diamond.png", "diamond");
        server.put("assets/minecraft/textures/gui/sprites/dialog/warning_button.png", "server warning");
        server.put("assets/glyphwork/font/c0.json", "stale glyphwork part");   // an old merge
        server.put("assets/", "");                                           // directory entry
        Logger log = Logger.getLogger("include-test-1");
        List<String> warned = warnings(log);
        IncludedPack.Result r;
        try (ZipFile z = new ZipFile(zip(dir, server).toFile())) {
            r = IncludedPack.include(pack, z, log);
        }
        Map<String, String> out = unzip(pack.toZip());
        assertEquals(2, r.files());                                          // ruby + diamond
        assertEquals(2, r.skipped());                                        // warning button + old c0
        assertTrue(out.get("pack.mcmeta").contains("My server") && out.get("pack.mcmeta").contains("overlays"));
        assertEquals("icon", out.get("pack.png"));
        assertEquals("ruby", out.get("assets/myserver/textures/item/ruby.png"));
        assertEquals("diamond", out.get("assets/minecraft/textures/item/diamond.png"));
        assertEquals("glyphwork warning", out.get("assets/minecraft/textures/gui/sprites/dialog/warning_button.png"));
        assertEquals("glyphwork", out.get("assets/glyphwork/font/i0.json"));
        assertFalse(out.containsKey("assets/glyphwork/font/c0.json"));
        assertTrue(warned.isEmpty(), warned.toString());
    }

    @Test
    void packFormatIsChecked() {
        Logger log = Logger.getLogger("include-test-2");
        List<String> warned = warnings(log);
        assertTrue(IncludedPack.checkFormat("{\"pack\":{\"min_format\":[90,0],\"max_format\":[99,1]}}".getBytes(), log));
        assertTrue(IncludedPack.checkFormat("{\"pack\":{\"pack_format\":34,\"supported_formats\":[34,120]}}".getBytes(), log));
        assertTrue(warned.isEmpty(), warned.toString());
        assertTrue(IncludedPack.checkFormat("{\"pack\":{\"pack_format\":34}}".getBytes(), log));   // old: kept, reported
        assertEquals(1, warned.size());
        assertTrue(warned.getFirst().contains("34"), warned.getFirst());
        assertFalse(IncludedPack.checkFormat("kein json".getBytes(), log));                     // Glyphwork's is kept
        assertEquals(2, warned.size());
    }
}
