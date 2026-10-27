package de.flyingfinger.glyphwork.menu;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Entries for dynamic lists ({@code items-from:}), read each time the menu is drawn. Each entry is a
 * map of values for {@code %item%} ("") and {@code %item_<key>%}.
 */
public final class Sources {

    /** Known sources and the values each entry has (for the docs and error messages). */
    static final Map<String, Set<String>> KEYS = Map.of(
        "online-players", Set.of("", "name", "world", "uuid", "icon"),
        "worlds", Set.of("", "name", "players", "environment", "icon"));

    private Sources() {
    }

    /** Reads the server (main thread). */
    public static List<Map<String, String>> fetch(String source) {
        if (Bukkit.getServer() == null) {
            return List.of();
        }
        List<Map<String, String>> out = new ArrayList<>();
        switch (source) {
            case "online-players" -> {
                List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
                players.sort(Comparator.comparing(p -> p.getName().toLowerCase()));
                for (Player p : players) {
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("", p.getName());
                    m.put("name", p.getName());
                    m.put("world", p.getWorld().getName());
                    m.put("uuid", p.getUniqueId().toString());
                    m.put("icon", ""); // heads are drawn by a special renderer: no icon yet
                    out.add(m);
                }
            }
            case "worlds" -> {
                for (World w : Bukkit.getWorlds()) {
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("", w.getName());
                    m.put("name", w.getName());
                    m.put("players", String.valueOf(w.getPlayers().size()));
                    String env = switch (w.getEnvironment()) {
                        case NETHER -> "nether";
                        case THE_END -> "the_end";
                        default -> "normal";
                    };
                    m.put("environment", env);
                    m.put("icon", switch (env) {
                        case "nether" -> "netherrack";
                        case "the_end" -> "end_stone";
                        default -> "grass_block";
                    });
                    out.add(m);
                }
            }
            default -> {
            }
        }
        return out;
    }
}
