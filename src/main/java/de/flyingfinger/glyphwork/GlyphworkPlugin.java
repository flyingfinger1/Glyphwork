package de.flyingfinger.glyphwork;

import de.flyingfinger.glyphwork.demo.DemoScreen;
import de.flyingfinger.glyphwork.dialog.DialogFactory;
import de.flyingfinger.glyphwork.font.GlyphAllocator;
import de.flyingfinger.glyphwork.font.ShiftFonts;
import de.flyingfinger.glyphwork.font.VanillaText;
import de.flyingfinger.glyphwork.pack.PackFiles;
import de.flyingfinger.glyphwork.render.LineBuilder;
import de.flyingfinger.glyphwork.serve.PackServer;
import de.flyingfinger.glyphwork.theme.Theme;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class GlyphworkPlugin extends JavaPlugin implements Listener {

    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("Glyphwork".getBytes());

    private PackServer server;
    private DemoScreen demo;
    private String sha1;
    private final Map<UUID, DemoScreen.State> states = new HashMap<>();
    private final Map<UUID, PlayerResourcePackStatusEvent.Status> packStatus = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        VanillaText text = VanillaText.load();
        GlyphAllocator alloc = new GlyphAllocator();
        Theme theme = loadTheme(alloc);
        demo = new DemoScreen(text, theme);

        PackFiles pack = new PackFiles();
        pack.put("pack.mcmeta", """
            {"pack":{"description":"Glyphwork","min_format":97,"max_format":97}}
            """);
        ShiftFonts.write(pack, theme.sheets(), demo.offsets(), text);
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
        getLogger().info("Theme '" + theme.name() + "', pack " + sha1 + " (" + zip.length + " bytes, "
            + demo.offsets().size() + " shift fonts) at " + packUrl());

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
        states.remove(event.getPlayer().getUniqueId());
        packStatus.remove(event.getPlayer().getUniqueId());
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
                if (packStatus.get(player.getUniqueId()) != PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
                    player.sendMessage(Component.text("Pack noch nicht geladen (Status: "
                        + packStatus.get(player.getUniqueId()) + ") – /glyphwork pack", NamedTextColor.YELLOW));
                }
                openDemo(player);
            }
            default -> player.sendMessage(Component.text("/glyphwork <test|pack>", NamedTextColor.GRAY));
        }
        return true;
    }

    private void openDemo(Player player) {
        DemoScreen.State state = states.computeIfAbsent(player.getUniqueId(), id -> new DemoScreen.State());
        LineBuilder line = demo.render(state, target -> ClickEvent.callback(audience -> {
            // callbacks may arrive off the main thread
            Bukkit.getScheduler().runTask(this, () -> onDemoClick(player, state, target));
        }, DialogFactory.callbackOptions()));
        DialogFactory.show(player, DialogFactory.gui(Component.text("Glyphwork"),
            line.build(DemoScreen.H), line.width()));
    }

    private void onDemoClick(Player player, DemoScreen.State state, DemoScreen.Target target) {
        if (!player.isOnline()) {
            return;
        }
        getLogger().info("Demo click " + player.getName() + ": " + target);
        switch (target) {
            case CLOSE -> {
                player.closeDialog();
                return;
            }
            case TOGGLE -> state.soundOn = !state.soundOn;
            case COUNTER -> state.count++;
            case PANEL, WELL -> {
            }
            default -> state.selectedSlot = target.ordinal() - DemoScreen.Target.SLOT_0.ordinal();
        }
        state.lastClick = switch (target) {
            case PANEL -> "Panel";
            case WELL -> "L-Form";
            case TOGGLE -> "Umschalter";
            case COUNTER -> "Zähler";
            default -> "Zelle " + (state.selectedSlot + 1);
        };
        openDemo(player);
    }
}
