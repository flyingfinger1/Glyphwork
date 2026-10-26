package de.flyingfinger.glyphwork;

import de.flyingfinger.glyphwork.api.Glyphwork;
import de.flyingfinger.glyphwork.core.GlyphworkService;
import de.flyingfinger.glyphwork.core.Settings;
import de.flyingfinger.glyphwork.demo.Demo;
import de.flyingfinger.glyphwork.font.GlyphAllocator;
import de.flyingfinger.glyphwork.font.ShiftFonts;
import de.flyingfinger.glyphwork.font.Sheet;
import de.flyingfinger.glyphwork.font.VanillaText;
import de.flyingfinger.glyphwork.pack.PackFiles;
import de.flyingfinger.glyphwork.pack.PackService;
import de.flyingfinger.glyphwork.render.SceneRenderer;
import de.flyingfinger.glyphwork.theme.Theme;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent.Status;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class GlyphworkPlugin extends JavaPlugin implements Listener {

    private static final String ADMIN = "glyphwork.admin";

    private final VanillaText text = VanillaText.load();
    private PackService packs;
    private GlyphworkService service;
    private Settings settings;
    private String themeName = "";
    private final Demo demo = new Demo();

    /** Everything that depends on config + theme. */
    private record Build(Settings settings, String themeName, SceneRenderer renderer, byte[] zip, String sha1) {
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        packs = new PackService(this);
        try {
            Build b = build();
            packs.publish(b.settings(), b.zip(), b.sha1());
            apply(b);
            service = new GlyphworkService(this, b.renderer(), b.settings().maxHeight(), b.settings().fallback(), packs::hasPack);
        } catch (IOException e) {
            getLogger().severe("Pack could not be published: " + e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getServicesManager().register(Glyphwork.class, service, this, ServicePriority.Normal);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        if (packs != null) {
            packs.stop();
        }
    }

    private Build build() {
        Settings s = Settings.read(getConfig());
        GlyphAllocator alloc = new GlyphAllocator();
        Theme theme = loadTheme(s.theme(), alloc);
        Sheet hit = Sheet.invisible(alloc);
        List<Sheet> sheets = new ArrayList<>(theme.sheets());
        sheets.add(hit);
        PackFiles pack = new PackFiles();
        pack.put("pack.mcmeta", """
            {"pack":{"description":"Glyphwork","min_format":97,"max_format":97}}
            """);
        ShiftFonts.write(pack, sheets, s.maxHeight(), text);
        byte[] zip = pack.toZip();
        SceneRenderer renderer = new SceneRenderer(text, theme, hit.glyph(), s.maxHeight(), getLogger());
        return new Build(s, theme.name(), renderer, zip, PackFiles.sha1(zip));
    }

    private void apply(Build b) {
        settings = b.settings();
        themeName = b.themeName();
        getLogger().info("Theme '" + themeName + "', pack " + b.sha1() + " (" + b.zip().length / 1024 + " KB, height "
            + settings.maxHeight() + " px, " + (settings.external() ? "external" : "embedded") + ") at " + packs.url());
        if (settings.external()) {
            getLogger().info("External hosting: serve " + getDataFolder().toPath().resolve("pack").toAbsolutePath()
                + " so that the URL above resolves");
        }
    }

    /**
     * Exports the built-in skin to themes/vanilla on first start (as a template for own skins),
     * then loads the configured theme; falls back to the built-in one if that fails.
     */
    private Theme loadTheme(String id, GlyphAllocator alloc) {
        Path themes = getDataFolder().toPath().resolve("themes");
        try {
            if (!Files.exists(themes.resolve("vanilla"))) {
                Theme.builtin(new GlyphAllocator()).save(themes.resolve("vanilla"));
            }
        } catch (IOException e) {
            getLogger().warning("Could not export the built-in theme: " + e);
        }
        try {
            return Theme.load(themes.resolve(id), alloc);
        } catch (IOException | RuntimeException e) {
            getLogger().warning("Theme '" + id + "' not loaded (" + e.getMessage() + "), using the built-in skin");
            return Theme.builtin(alloc);
        }
    }

    /** /glyphwork reload: config, theme and pack; players get the new pack if it changed. */
    private void reload(CommandSender sender) {
        reloadConfig();
        try {
            Build b = build();
            boolean changed = packs.publish(b.settings(), b.zip(), b.sha1());
            apply(b);
            service.reconfigure(b.renderer(), b.settings().maxHeight(), b.settings().fallback());
            if (changed) {
                int n = packs.sendToAll();
                sender.sendMessage(Component.text("Glyphwork neu geladen: Theme '" + themeName + "', neues Pack an "
                    + n + " Spieler gesendet.", NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("Glyphwork neu geladen: Theme '" + themeName
                    + "', Pack unverändert.", NamedTextColor.GREEN));
            }
        } catch (IOException | RuntimeException e) {
            getLogger().warning("Reload failed: " + e);
            sender.sendMessage(Component.text("Reload fehlgeschlagen: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (settings.sendOnJoin()) {
            packs.send(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        packs.forget(event.getPlayer());
        service.forget(event.getPlayer());
    }

    @EventHandler
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        if (!PackService.PACK_ID.equals(event.getID())) {
            return;
        }
        Player p = event.getPlayer();
        packs.status(p, event.getStatus());
        getLogger().info(p.getName() + " pack status: " + event.getStatus());
        switch (event.getStatus()) {
            case DECLINED -> p.sendMessage(Component.text("Ohne das Glyphwork-Pack werden Menüs vereinfacht angezeigt. "
                + "/glyphwork pack lädt es nachträglich.", NamedTextColor.GRAY));
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> p.sendMessage(Component.text(
                "Das Glyphwork-Pack konnte nicht geladen werden (" + event.getStatus() + "). Menüs werden vereinfacht angezeigt.",
                NamedTextColor.GRAY));
            default -> {
            }
        }
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        String sub = args.length > 0 ? args[0].toLowerCase() : "help";
        switch (sub) {
            case "pack" -> {
                if (sender instanceof Player player) {
                    packs.send(player);
                    player.sendMessage(Component.text("Pack gesendet.", NamedTextColor.GRAY));
                }
            }
            case "test" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Nur für Spieler.");
                } else if (!sender.hasPermission(ADMIN)) {
                    noPermission(sender);
                } else {
                    service.open(player, demo.main);
                }
            }
            case "reload" -> {
                if (sender.hasPermission(ADMIN)) {
                    reload(sender);
                } else {
                    noPermission(sender);
                }
            }
            case "status" -> {
                if (!sender.hasPermission(ADMIN)) {
                    noPermission(sender);
                    return true;
                }
                sender.sendMessage(Component.text("Theme '" + themeName + "', pack " + packs.sha1() + " ("
                    + (settings.external() ? "external" : "embedded") + ")\n" + packs.url(), NamedTextColor.GRAY));
                for (Player p : getServer().getOnlinePlayers()) {
                    Status st = packs.status(p);
                    sender.sendMessage(Component.text("  " + p.getName() + ": " + (st == null ? "-" : st),
                        st == Status.SUCCESSFULLY_LOADED ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
                }
            }
            default -> sender.sendMessage(Component.text(sender.hasPermission(ADMIN)
                ? "/glyphwork <pack|test|status|reload>" : "/glyphwork pack", NamedTextColor.GRAY));
        }
        return true;
    }

    private static void noPermission(CommandSender sender) {
        sender.sendMessage(Component.text("Keine Berechtigung.", NamedTextColor.RED));
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, String @NonNull [] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> subs = sender.hasPermission(ADMIN) ? List.of("pack", "reload", "status", "test") : List.of("pack");
        return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
    }
}
