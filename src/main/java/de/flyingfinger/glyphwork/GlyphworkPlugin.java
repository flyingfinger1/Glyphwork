package de.flyingfinger.glyphwork;

import de.flyingfinger.glyphwork.api.Glyphwork;
import de.flyingfinger.glyphwork.core.GlyphworkService;
import de.flyingfinger.glyphwork.demo.Demo;
import de.flyingfinger.glyphwork.font.GlyphAllocator;
import de.flyingfinger.glyphwork.font.ShiftFonts;
import de.flyingfinger.glyphwork.font.Sheet;
import de.flyingfinger.glyphwork.font.VanillaText;
import de.flyingfinger.glyphwork.pack.PackFiles;
import de.flyingfinger.glyphwork.render.SceneRenderer;
import de.flyingfinger.glyphwork.serve.PackServer;
import de.flyingfinger.glyphwork.theme.Theme;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
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
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GlyphworkPlugin extends JavaPlugin implements Listener {

    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("Glyphwork".getBytes());

    private PackServer server;
    private GlyphworkService service;
    private Demo demo;
    private String sha1;
    private final Map<UUID, PlayerResourcePackStatusEvent.Status> packStatus = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        int maxHeight = Math.max(16, Math.min(1024, getConfig().getInt("max-height", 256)));
        VanillaText text = VanillaText.load();
        GlyphAllocator alloc = new GlyphAllocator();
        Theme theme = loadTheme(alloc);
        Sheet hit = Sheet.invisible(alloc);

        List<Sheet> sheets = new ArrayList<>(theme.sheets());
        sheets.add(hit);
        PackFiles pack = new PackFiles();
        pack.put("pack.mcmeta", """
            {"pack":{"description":"Glyphwork","min_format":97,"max_format":97}}
            """);
        ShiftFonts.write(pack, sheets, maxHeight, text);
        byte[] zip = pack.toZip();
        sha1 = PackFiles.sha1(zip);
        try {
            Path dir = getDataFolder().toPath().resolve("pack");
            Files.createDirectories(dir);
            Files.write(dir.resolve("latest.zip"), zip);
            server = new PackServer(getConfig().getString("pack-server.bind", "0.0.0.0"),
                getConfig().getInt("pack-server.port", 8765), getLogger());
        } catch (IOException e) {
            getLogger().severe("Pack server failed to start: " + e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        server.publish(zip, sha1);
        getLogger().info("Theme '" + theme.name() + "', pack " + sha1 + " (" + zip.length / 1024 + " KB, height "
            + maxHeight + " px) at " + packUrl());

        SceneRenderer renderer = new SceneRenderer(text, theme, hit.glyph(), maxHeight, getLogger());
        service = new GlyphworkService(this, renderer, maxHeight,
            p -> packStatus.get(p.getUniqueId()) == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED);
        getServer().getServicesManager().register(Glyphwork.class, service, this, ServicePriority.Normal);
        demo = new Demo();
        getServer().getPluginManager().registerEvents(this, this);
    }

    /**
     * Exports the built-in skin to themes/vanilla on first start (as a template for own skins),
     * then loads the configured theme; falls back to the built-in one if that fails.
     */
    private Theme loadTheme(GlyphAllocator alloc) {
        Path themes = getDataFolder().toPath().resolve("themes");
        try {
            if (!Files.exists(themes.resolve("vanilla"))) {
                Theme.builtin(new GlyphAllocator()).save(themes.resolve("vanilla"));
            }
        } catch (IOException e) {
            getLogger().warning("Could not export the built-in theme: " + e);
        }
        String id = getConfig().getString("theme", "vanilla");
        try {
            return Theme.load(themes.resolve(id), alloc);
        } catch (IOException | RuntimeException e) {
            getLogger().warning("Theme '" + id + "' not loaded (" + e.getMessage() + "), using the built-in skin");
            return Theme.builtin(alloc);
        }
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        if (server != null) {
            server.stop();
        }
    }

    private String packUrl() {
        String base = getConfig().getString("pack-server.public-url", "http://127.0.0.1:8765/");
        return (base.endsWith("/") ? base : base + "/") + sha1 + ".zip";
    }

    private void sendPack(Player player) {
        player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
            .packs(ResourcePackInfo.resourcePackInfo(PACK_ID, URI.create(packUrl()), sha1))
            .required(getConfig().getBoolean("pack.required", false))
            .prompt(Component.text(getConfig().getString("pack.prompt", "")))
            .replace(false)
            .build());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (getConfig().getBoolean("pack.send-on-join", true)) {
            sendPack(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        packStatus.remove(event.getPlayer().getUniqueId());
        service.forget(event.getPlayer());
    }

    @EventHandler
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        if (!PACK_ID.equals(event.getID())) {
            return;
        }
        packStatus.put(event.getPlayer().getUniqueId(), event.getStatus());
        getLogger().info(event.getPlayer().getName() + " pack status: " + event.getStatus());
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur für Spieler.");
            return true;
        }
        String sub = args.length > 0 ? args[0] : "test";
        switch (sub) {
            case "pack" -> {
                sendPack(player);
                player.sendMessage(Component.text("Pack gesendet.", NamedTextColor.GRAY));
            }
            case "test" -> {
                if (!service.hasPack(player)) {
                    player.sendMessage(Component.text("Pack noch nicht geladen (Status: "
                        + packStatus.get(player.getUniqueId()) + ") – /glyphwork pack", NamedTextColor.YELLOW));
                }
                service.open(player, demo.main);
            }
            default -> player.sendMessage(Component.text("/glyphwork <test|pack>", NamedTextColor.GRAY));
        }
        return true;
    }
}
