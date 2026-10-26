package de.flyingfinger.dialogui;

import de.flyingfinger.dialogui.dialog.DialogFactory;
import de.flyingfinger.dialogui.font.ShiftFonts;
import de.flyingfinger.dialogui.font.VanillaText;
import de.flyingfinger.dialogui.m0.M0Screen;
import de.flyingfinger.dialogui.pack.PackFiles;
import de.flyingfinger.dialogui.render.LineBuilder;
import de.flyingfinger.dialogui.serve.PackServer;
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

public final class DialogUIPlugin extends JavaPlugin implements Listener {

    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("PaperDialogUI".getBytes());

    private PackServer server;
    private M0Screen m0;
    private String sha1;
    private final Map<UUID, M0Screen.State> states = new HashMap<>();
    private final Map<UUID, PlayerResourcePackStatusEvent.Status> packStatus = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        VanillaText text = VanillaText.load();
        m0 = new M0Screen(text);

        PackFiles pack = new PackFiles();
        pack.put("pack.mcmeta", """
            {"pack":{"description":"PaperDialogUI","min_format":97,"max_format":97}}
            """);
        ShiftFonts.write(pack, m0.sprites(), m0.offsets(), text);
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
        getLogger().info("Pack " + sha1 + " (" + zip.length + " bytes, offsets " + m0.offsets() + ") at " + packUrl());

        getServer().getPluginManager().registerEvents(this, this);
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
                        + packStatus.get(player.getUniqueId()) + ") – /dialogui pack", NamedTextColor.YELLOW));
                }
                openM0(player);
            }
            default -> player.sendMessage(Component.text("/dialogui <test|pack>", NamedTextColor.GRAY));
        }
        return true;
    }

    private void openM0(Player player) {
        M0Screen.State state = states.computeIfAbsent(player.getUniqueId(), id -> new M0Screen.State());
        LineBuilder line = m0.render(state, target -> ClickEvent.callback(audience -> {
            // callbacks may arrive off the main thread
            Bukkit.getScheduler().runTask(this, () -> onM0Click(player, state, target));
        }, DialogFactory.callbackOptions()));
        DialogFactory.show(player, DialogFactory.gui(Component.text("PaperDialogUI M0"),
            line.build(M0Screen.H), line.width()));
    }

    private void onM0Click(Player player, M0Screen.State state, M0Screen.Target target) {
        if (!player.isOnline()) {
            return;
        }
        getLogger().info("M0 click " + player.getName() + ": " + target);
        switch (target) {
            case CLOSE -> {
                player.closeDialog();
                return;
            }
            case TOGGLE -> state.soundOn = !state.soundOn;
            case COUNTER -> state.count++;
            case PANEL -> {
            }
            default -> state.selectedSlot = target.ordinal() - M0Screen.Target.SLOT_0.ordinal();
        }
        state.lastClick = switch (target) {
            case PANEL -> "Panel";
            case TOGGLE -> "Umschalter";
            case COUNTER -> "Zähler";
            default -> "Zelle " + (state.selectedSlot + 1);
        };
        openM0(player);
    }
}
