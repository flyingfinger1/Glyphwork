package de.flyingfinger.glyphwork.pack;

import de.flyingfinger.glyphwork.core.Settings;
import de.flyingfinger.glyphwork.serve.PackServer;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerResourcePackStatusEvent.Status;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/** Publishes the generated pack (embedded HTTP or files for external hosting), sends it, tracks status. */
public final class PackService {

    /** One pack id for Glyphwork: sending a new version replaces the old one on the client. */
    public static final UUID PACK_ID = UUID.nameUUIDFromBytes("Glyphwork".getBytes());
    private static final int KEEP_EXTERNAL = 5;

    private final Plugin plugin;
    private final Path dir;
    private final Map<UUID, Status> status = new ConcurrentHashMap<>();
    /** Players whose client loaded the server's own pack (server.properties resource-pack). */
    private final java.util.Set<UUID> serverPack = ConcurrentHashMap.newKeySet();
    private @Nullable PackServer server;
    private String serverKey = "";
    private Settings settings;
    private String sha1 = "";

    public PackService(Plugin plugin) {
        this.plugin = plugin;
        this.dir = plugin.getDataFolder().toPath().resolve("pack");
    }

    /**
     * Makes the pack available under the configured URL.
     *
     * @return true if the hash changed (players need the new pack)
     */
    public boolean publish(Settings s, byte[] zip, String newSha1) throws IOException {
        Files.createDirectories(dir);
        Files.write(dir.resolve("latest.zip"), zip);
        if (s.provided()) {
            // merged into the server's or proxy's own pack by the admin: a stable name, nothing to serve
            Files.write(dir.resolve("glyphwork.zip"), zip);
            stopServer();
        } else if (s.external()) {
            Files.write(dir.resolve(newSha1 + ".zip"), zip);
            prune(newSha1);
            stopServer();
        } else {
            String key = s.bind() + ":" + s.port();
            if (server == null || !key.equals(serverKey)) {
                stopServer();
                server = new PackServer(s.bind(), s.port(), plugin.getLogger());
                serverKey = key;
            }
            server.publish(zip, newSha1);
        }
        boolean changed = !newSha1.equals(sha1);
        this.settings = s;
        this.sha1 = newSha1;
        return changed;
    }

    /** Keeps the newest few hashed zips so players with a cached URL can still download. */
    private void prune(String current) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> old = files
                .filter(p -> p.getFileName().toString().matches("[0-9a-f]{40}\\.zip"))
                .filter(p -> !p.getFileName().toString().startsWith(current))
                .sorted(Comparator.comparingLong((Path p) -> p.toFile().lastModified()).reversed())
                .skip(KEEP_EXTERNAL - 1)
                .toList();
            for (Path p : old) {
                Files.deleteIfExists(p);
            }
        }
    }

    private void stopServer() {
        if (server != null) {
            server.stop();
            server = null;
            serverKey = "";
        }
    }

    public void stop() {
        stopServer();
    }

    public String sha1() {
        return sha1;
    }

    public String url() {
        return settings.url(sha1);
    }

    public boolean external() {
        return settings.external();
    }

    public void send(Player player) {
        if (settings != null && settings.provided()) {
            return;                                 // the server or proxy sends the pack with the Glyphwork part
        }
        if (de.flyingfinger.glyphwork.core.Bedrock.is(player)) {
            return;                                 // Bedrock cannot load Java packs; it gets the plain dialog
        }
        player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
            .packs(ResourcePackInfo.resourcePackInfo(PACK_ID, URI.create(url()), sha1))
            .required(settings.required())
            .prompt(settings.prompt().isBlank()
                ? de.flyingfinger.glyphwork.core.Messages.active().get(player, "pack.prompt")
                : Component.text(settings.prompt()))
            .replace(false)
            .build());
    }

    /** Sends the current pack to everyone online (after a reload changed it). */
    public int sendToAll() {
        int n = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            status.remove(p.getUniqueId());
            send(p);
            n++;
        }
        return n;
    }

    public void status(Player player, Status s) {
        status.put(player.getUniqueId(), s);
    }

    public @Nullable Status status(Player player) {
        return status.get(player.getUniqueId());
    }

    public boolean hasPack(Player player) {
        if (settings != null && settings.provided()) {
            // the Glyphwork part comes with the server's or proxy's pack
            return settings.providedAlways() || hasServerPack(player);
        }
        return status.get(player.getUniqueId()) == Status.SUCCESSFULLY_LOADED;
    }

    public void forget(Player player) {
        status.remove(player.getUniqueId());
        serverPack.remove(player.getUniqueId());
    }

    /**
     * Call on join, before sending the Glyphwork pack: the connection still holds the answer to the
     * server pack, which is sent while logging in (no event fires for it).
     */
    public void recordServerPack(Player player) {
        String url = Bukkit.getServer().getResourcePack();
        if (url != null && !url.isBlank() && player.getResourcePackStatus() == Status.SUCCESSFULLY_LOADED) {
            serverPack.add(player.getUniqueId());
        } else {
            serverPack.remove(player.getUniqueId());
        }
    }

    /** A later status for the server pack (e.g. sent again by another plugin). */
    public void serverPackStatus(Player player, Status s) {
        if (s == Status.SUCCESSFULLY_LOADED) {
            serverPack.add(player.getUniqueId());
        } else if (s == Status.DECLINED || s == Status.FAILED_DOWNLOAD || s == Status.DISCARDED
            || s == Status.INVALID_URL || s == Status.FAILED_RELOAD) {
            serverPack.remove(player.getUniqueId());
        }
    }

    public boolean hasServerPack(Player player) {
        if (settings != null && settings.provided() && settings.providedAlways()) {
            return true;                            // a pack everyone has (e.g. required on the proxy)
        }
        return serverPack.contains(player.getUniqueId());
    }
}
