package de.flyingfinger.glyphwork;

import de.flyingfinger.glyphwork.core.Messages;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Language files: complete in every built-in language, chosen per player, overridable. */
class MessagesTest {

    private static Player player(Locale locale) {
        return (Player) Proxy.newProxyInstance(MessagesTest.class.getClassLoader(), new Class<?>[] {Player.class},
            (p, m, a) -> m.getName().equals("locale") ? locale : null);
    }

    private static String plain(net.kyori.adventure.text.Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    private static Set<String> keys(String code) throws Exception {
        try (InputStream in = MessagesTest.class.getResourceAsStream("/lang/" + code + ".yml")) {
            YamlConfiguration c = new YamlConfiguration();
            c.loadFromString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            Set<String> out = new TreeSet<>();
            for (String k : c.getKeys(true)) {
                if (!c.isConfigurationSection(k)) {
                    out.add(k);
                }
            }
            return out;
        }
    }

    @Test
    void everyLanguageHasEveryKeyAndTheCodeUsesOnlyExistingOnes() throws Exception {
        Set<String> en = keys("en");
        for (String code : Messages.BUILTIN) {
            assertEquals(en, keys(code), "keys of " + code + ".yml");
        }
        // every "key" passed to get/plain/raw in the plugin sources exists
        Pattern use = Pattern.compile("(?:get|plain|raw)\\((?:[a-zA-Z.()]+|lang), \"([a-z0-9.-]+)\"");
        Pattern keyLiteral = Pattern.compile("\"((?:cmd|display|dialog|fallback|pack|menu|reload|status|help)\\.[a-z0-9.-]+|players-only|no-permission|player-not-found)\"");
        Set<String> used = new TreeSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String src = Files.readString(f);
                for (Pattern p : new Pattern[] {use, keyLiteral}) {
                    Matcher m = p.matcher(src);
                    while (m.find()) {
                        used.add(m.group(1));
                    }
                }
            }
        }
        used.removeIf(k -> k.startsWith("pack.") && !en.contains(k) && !k.equals("pack.prompt")); // config keys like pack.url
        assertTrue(used.size() > 25, "found keys: " + used);
        for (String k : used) {
            assertTrue(en.contains(k), "missing in en.yml: " + k);
        }
    }

    @Test
    void languageFollowsTheClientAndFallsBack() {
        Messages m = Messages.builtin();
        assertEquals("Menü 'x' gibt es nicht.", plain(m.get(player(Locale.GERMANY), "menu.unknown", "id", "x")));
        assertEquals("Menu 'x' does not exist.", plain(m.get(player(Locale.US), "menu.unknown", "id", "x")));
        assertEquals("Menu 'x' does not exist.", plain(m.get(player(Locale.FRANCE), "menu.unknown", "id", "x"))); // no fr.yml
        assertEquals("Menu 'x' does not exist.", plain(m.get(null, "menu.unknown", "id", "x")));                 // console
    }

    @Test
    void valuesCannotInjectFormattingAndEscapedBracketsStay() {
        Messages m = Messages.builtin();
        assertEquals("Menu '<red>evil' does not exist.", plain(m.get(null, "menu.unknown", "id", "<red>evil")));
        assertEquals("/glyphwork menu <id> [player] – menus: a, b", plain(m.get(null, "menu.usage", "menus", "a, b")));
        assertEquals("/glyphwork <pack|display>", plain(m.get(null, "help.player")));
    }

    @Test
    void ownFilesOverrideKeysAndAddLanguages(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("de.yml"), "pack.sent: \"<gold>Pack ist unterwegs\"\nkaputt: x\n");
        Files.writeString(dir.resolve("fr.yml"), "pack.sent: \"Pack envoyé\"\n");
        Messages m = Messages.load(dir, "auto", "en", Logger.getLogger("test"));
        assertEquals("Pack ist unterwegs", plain(m.get(player(Locale.GERMANY), "pack.sent")));
        assertEquals("Spieler nicht gefunden.", plain(m.get(player(Locale.GERMANY), "player-not-found"))); // rest built in
        assertEquals("Pack envoyé", plain(m.get(player(Locale.FRANCE), "pack.sent")));
        assertEquals("Player not found.", plain(m.get(player(Locale.FRANCE), "player-not-found")));         // fallback en

        Messages fixed = Messages.load(dir, "de", "en", Logger.getLogger("test"));
        assertEquals("Pack ist unterwegs", plain(fixed.get(player(Locale.US), "pack.sent")));
        assertEquals("de", fixed.language(null));
    }

    @Test
    void commandWordsAreAcceptedInEveryLanguage() {
        Messages m = Messages.builtin();
        assertEquals(Set.of("darstellung", "display"), m.everyLanguage("cmd.display"));
        assertEquals(Set.of("ohne-icons", "no-icons"), m.everyLanguage("cmd.no-icons"));
    }

    @Test
    void exportWritesTheBuiltInFilesOnce(@TempDir Path dir) throws Exception {
        Messages.export(dir);
        assertTrue(Files.exists(dir.resolve("de.yml")) && Files.exists(dir.resolve("en.yml")));
        Files.writeString(dir.resolve("en.yml"), "pack.sent: mine\n");
        Messages.export(dir);
        assertEquals("pack.sent: mine\n", Files.readString(dir.resolve("en.yml")));
    }
}
