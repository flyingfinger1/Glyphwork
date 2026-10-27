package de.flyingfinger.glyphwork;

import de.flyingfinger.glyphwork.api.DisplayMode;
import de.flyingfinger.glyphwork.api.Glyphwork;
import de.flyingfinger.glyphwork.core.GlyphworkService;
import de.flyingfinger.glyphwork.core.Messages;
import de.flyingfinger.glyphwork.core.Settings;
import de.flyingfinger.glyphwork.demo.Demo;
import de.flyingfinger.glyphwork.dialog.DialogFactory;
import de.flyingfinger.glyphwork.font.GlyphAllocator;
import de.flyingfinger.glyphwork.font.IconCatalog;
import de.flyingfinger.glyphwork.font.ShiftFonts;
import de.flyingfinger.glyphwork.font.Sheet;
import de.flyingfinger.glyphwork.font.VanillaText;
import de.flyingfinger.glyphwork.menu.MenuCommands;
import de.flyingfinger.glyphwork.menu.Menus;
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
import java.util.stream.Stream;

public final class GlyphworkPlugin extends JavaPlugin implements Listener {

    private static final String ADMIN = "glyphwork.admin";

    private final VanillaText text = VanillaText.load();
    private PackService packs;
    private GlyphworkService service;
    private Settings settings;
    private String themeName = "";
    private final Demo demo = new Demo();
    private Menus menus = new Menus();
    private de.flyingfinger.glyphwork.menu.MenuTriggers triggers;
    private MenuCommands menuCommands;

    /** Everything that depends on config + theme. */
    private record Build(Settings settings, int height, String themeName, SceneRenderer renderer, byte[] zip, String sha1) {
    }

    /** Most rows the pack ever gets (each 256 px add about 160 KB). */
    static final int HEIGHT_LIMIT = 2048;

    /**
     * Rows of y-fonts the pack needs: max-height, or the tallest menu (rounded up to 32 px) when
     * that is higher, at most {@link #HEIGHT_LIMIT}.
     */
    static int packHeight(int maxHeight, Iterable<Integer> menuHeights, java.util.logging.Logger log) {
        int h = maxHeight;
        for (int m : menuHeights) {
            h = Math.max(h, (m + 31) / 32 * 32);
        }
        if (h > HEIGHT_LIMIT) {
            log.warning("A menu is taller than " + HEIGHT_LIMIT + " px; frames below that are not drawn");
            h = HEIGHT_LIMIT;
        }
        return h;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        packs = new PackService(this);
        try {
            Build b = build();
            packs.publish(b.settings(), b.zip(), b.sha1());
            apply(b);
            service = new GlyphworkService(this, b.renderer(), b.height(), b.settings().fallback(),
                packs::hasPack, packs::send, packs::hasServerPack);
        } catch (IOException e) {
            getLogger().severe("Pack could not be published: " + e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getServicesManager().register(Glyphwork.class, service, this, ServicePriority.Normal);
        getServer().getPluginManager().registerEvents(this, this);
        menuCommands = new MenuCommands(this);
        triggers = new de.flyingfinger.glyphwork.menu.MenuTriggers(this, () -> menus, this::openMenu,
            p -> settings.sendOnJoin());
        getServer().getPluginManager().registerEvents(triggers, this);
        loadMenus();
    }

    /** The example menus, German and English (same layout). */
    static final List<String> EXAMPLES_DE = List.of("beispiel.yml", "beispiel-regeln.yml", "beispiel-warps.yml",
        "_beispiel-vorlagen.yml", "beispiel-spieler.yml", "beispiel-formular.yml");
    static final List<String> EXAMPLES_EN = List.of("example.yml", "example-rules.yml", "example-warps.yml",
        "_example-templates.yml", "example-players.yml", "example-forms.yml");

    /**
     * First start: copies the example menus to plugins/Glyphwork/menus/, in the server's language
     * ({@code language}, or {@code fallback-language} when it is "auto"): German or English.
     */
    private void exportExampleMenus() {
        if (Files.isDirectory(getDataFolder().toPath().resolve("menus"))) {
            return;
        }
        Settings s = Settings.read(getConfig());
        String lang = "auto".equalsIgnoreCase(s.language()) ? s.fallbackLanguage() : s.language();
        for (String f : lang.toLowerCase().startsWith("de") ? EXAMPLES_DE : EXAMPLES_EN) {
            saveResource("menus/" + f, false);
        }
    }

    /** (Re)loads menus/*.yml and their commands; returns the problems found. */
    private List<String> loadMenus() {
        menus = Menus.load(getDataFolder().toPath().resolve("menus"));
        for (String e : menus.errors()) {
            getLogger().warning("Menu not loaded: " + e);
        }
        menuCommands.register(menus, this::openMenu);
        if (triggers != null) {
            triggers.giveAll();
        }
        getLogger().info(menus.all().size() + " menu(s) loaded" + (menus.errors().isEmpty() ? "" : ", "
            + menus.errors().size() + " with errors"));
        return menus.errors();
    }

    private static Messages msg() {
        return Messages.active();
    }

    private void openMenu(Player player, String id) {
        Menus.Menu m = menus.get(id);
        if (m == null) {
            player.sendMessage(msg().get(player, "menu.unknown", "id", id));
        } else if (!m.allowed(player)) {
            player.sendMessage(msg().get(player, "no-permission"));
        } else {
            service.open(player, m.scene());
        }
    }

    @Override
    public void onDisable() {
        if (menuCommands != null) {
            menuCommands.unregister();
        }
        getServer().getServicesManager().unregisterAll(this);
        if (packs != null) {
            packs.stop();
        }
    }

    /** Skin faces for big heads; kept across reloads (the cache survives). */
    private de.flyingfinger.glyphwork.core.@org.jspecify.annotations.Nullable SkinFaces faces;

    private de.flyingfinger.glyphwork.core.SkinFaces faces() {
        if (faces == null) {
            faces = new de.flyingfinger.glyphwork.core.SkinFaces(this);
        }
        return faces;
    }

    private Build build() {
        Settings s = Settings.read(getConfig());
        GlyphAllocator alloc = new GlyphAllocator();
        Theme theme = loadTheme(s.theme(), alloc);
        Sheet hit = Sheet.invisible(alloc);
        List<Sheet> sheets = new ArrayList<>(theme.sheets());
        sheets.add(hit);
        de.flyingfinger.glyphwork.font.FillSheets fills = new de.flyingfinger.glyphwork.font.FillSheets(alloc);
        sheets.addAll(fills.sheets());
        de.flyingfinger.glyphwork.font.SquareSheets squares = new de.flyingfinger.glyphwork.font.SquareSheets(alloc);
        sheets.add(squares.sheet());
        PackFiles pack = new PackFiles();
        pack.put("pack.mcmeta", """
            {"pack":{"description":"Glyphwork","min_format":97,"max_format":97}}
            """);
        IconCatalog icons = s.icons() ? IconCatalog.load(alloc) : null;
        if (icons != null) {
            applySourcePack(icons, s, alloc);
        }
        // web sources first: menus that use them are checked against them
        de.flyingfinger.glyphwork.menu.WebSources.configure(
            de.flyingfinger.glyphwork.menu.WebSources.read(getConfig().getConfigurationSection("web"), getLogger()));
        // tall menus get the fonts they need: they scroll in the dialog, frames included
        exportExampleMenus();
        List<Integer> heights = Menus.load(getDataFolder().toPath().resolve("menus")).all().stream()
            .map(m -> m.scene().height()).toList();
        int height = packHeight(s.maxHeight(), heights, getLogger());
        ShiftFonts.write(pack, sheets, height, text, icons);
        Path imageDir = getDataFolder().toPath().resolve("images");
        try {
            Files.createDirectories(imageDir);
        } catch (IOException e) {
            getLogger().warning("Could not create images/: " + e.getMessage());
        }
        de.flyingfinger.glyphwork.font.ImageCatalog images = de.flyingfinger.glyphwork.font.ImageCatalog.load(imageDir, alloc, getLogger());
        images.write(pack);
        Path langDir = getDataFolder().toPath().resolve("lang");
        try {
            Messages.export(langDir);
        } catch (IOException e) {
            getLogger().warning("Could not export the language files: " + e);
        }
        de.flyingfinger.glyphwork.pack.DialogWarning.write(pack, s.warningIcon(), s.warningIconHover(), images,
            Messages.load(langDir, s.language(), s.fallbackLanguage(), getLogger()), getLogger());
        if (images.size() > 0) {
            getLogger().info(images.size() + " picture(s) from images/: " + String.join(", ", images.ids()));
        }
        byte[] zip = pack.toZip();
        SceneRenderer renderer = new SceneRenderer(text, theme, hit.glyph(), height, getLogger(), icons).fills(fills).images(images)
            .bigHeads(squares, faces());
        return new Build(s, height, theme.name(), renderer, zip, PackFiles.sha1(zip));
    }

    /** Adapts icons to the server's resource pack (icon-source-pack). */
    private void applySourcePack(IconCatalog icons, Settings s, GlyphAllocator alloc) {
        Path zip = de.flyingfinger.glyphwork.pack.SourcePack.resolve(s.iconSourcePack(), getDataFolder().toPath(), getLogger());
        if (zip == null) {
            return;
        }
        try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(zip.toFile())) {
            IconCatalog.PackResult r = icons.applyPack(new de.flyingfinger.glyphwork.font.PackIcons(z), alloc);
            getLogger().info("Icons matched to " + zip.getFileName() + ": " + r.remeasured() + " textures measured, "
                + r.packItems() + " item(s) from the pack (" + r.newTextures() + " new textures)");
        } catch (IOException | RuntimeException e) {
            getLogger().warning("icon-source-pack " + zip + " could not be read: " + e.getMessage());
        }
    }

    private void apply(Build b) {
        settings = b.settings();
        de.flyingfinger.glyphwork.menu.Feeds.configure(settings.feedCache(), settings.feedDateFormat(), getLogger());
        de.flyingfinger.glyphwork.menu.Feeds.clear();
        themeName = b.themeName();
        Path lang = getDataFolder().toPath().resolve("lang");
        try {
            Messages.export(lang);
        } catch (IOException e) {
            getLogger().warning("Could not export the language files: " + e);
        }
        Messages.activate(Messages.load(lang, settings.language(), settings.fallbackLanguage(), getLogger()));
        // players.commands: false -> only admins see /glyphwork (also in tab completion and /help)
        org.bukkit.command.PluginCommand cmd = getCommand("glyphwork");
        if (cmd != null) {
            cmd.setPermission(settings.playerCommands() ? null : ADMIN);
            getServer().getOnlinePlayers().forEach(Player::updateCommands);
        }
        getLogger().info("Theme '" + themeName + "', pack " + b.sha1() + " (" + b.zip().length / 1024 + " KB, height "
            + b.height() + " px, " + (settings.external() ? "external" : "embedded") + ") at " + packs.url());
        if (settings.external()) {
            getLogger().info("External hosting: serve " + getDataFolder().toPath().resolve("pack").toAbsolutePath()
                + " so that the URL above resolves");
        }
    }

    /**
     * Exports the built-in skins to themes/<id> when their folder is missing (as templates for own skins),
     * then loads the configured theme; falls back to the built-in one if that fails.
     */
    private Theme loadTheme(String id, GlyphAllocator alloc) {
        Path themes = getDataFolder().toPath().resolve("themes");
        try {
            for (Theme.Builtin b : Theme.BUILTINS) {
                if (!Files.exists(themes.resolve(b.id()))) {
                    Theme.builtin(b, new GlyphAllocator()).save(themes.resolve(b.id()));
                }
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
            service.reconfigure(b.renderer(), b.height(), b.settings().fallback());
            List<String> menuErrors = loadMenus();
            sender.sendMessage(msg().get(sender, "reload.menus", "count", menus.all().size()));
            for (String e : menuErrors) {
                sender.sendMessage(Component.text("  " + e, NamedTextColor.RED));
            }
            if (changed) {
                int n = packs.sendToAll();
                sender.sendMessage(msg().get(sender, "reload.pack-sent", "theme", themeName, "count", n));
            } else {
                sender.sendMessage(msg().get(sender, "reload.pack-same", "theme", themeName));
            }
        } catch (IOException | RuntimeException e) {
            getLogger().warning("Reload failed: " + e);
            sender.sendMessage(msg().get(sender, "reload.failed", "error", String.valueOf(e.getMessage())));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        packs.recordServerPack(event.getPlayer());
        if (getServer().getResourcePack() != null && !getServer().getResourcePack().isBlank()) {
            getLogger().info(event.getPlayer().getName() + " server pack (server.properties): "
                + event.getPlayer().getResourcePackStatus()
                + (packs.hasServerPack(event.getPlayer()) ? "" : " -> icons from it are left out for this player"));
        }
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
            org.bukkit.packs.ResourcePack server = getServer().getServerResourcePack();
            if (server != null && server.getId().equals(event.getID())) {
                packs.serverPackStatus(event.getPlayer(), event.getStatus());
            }
            return;
        }
        Player p = event.getPlayer();
        packs.status(p, event.getStatus());
        boolean finished = switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED, DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> true;
            default -> false;
        };
        if (finished && triggers != null) {
            triggers.packDone(p);
        }
        getLogger().info(p.getName() + " pack status: " + event.getStatus());
        switch (event.getStatus()) {
            case DECLINED -> p.sendMessage(msg().get(p, "pack.declined"));
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED ->
                p.sendMessage(msg().get(p, "pack.failed", "status", event.getStatus()));
            default -> {
            }
        }
    }

    /** The command word {@code key} (cmd.*) in any language, e.g. "display" or "darstellung". */
    private static boolean is(String arg, String key) {
        return msg().everyLanguage(key).contains(arg.toLowerCase(java.util.Locale.ROOT));
    }

    private static @org.jspecify.annotations.Nullable DisplayMode mode(String arg) {
        return is(arg, "cmd.full") || arg.equalsIgnoreCase("full") ? DisplayMode.FULL
            : is(arg, "cmd.no-icons") || arg.equalsIgnoreCase("no-icons") ? DisplayMode.NO_ICONS
            : is(arg, "cmd.simple") || arg.equalsIgnoreCase("simple") ? DisplayMode.SIMPLE
            : null;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        String sub = args.length > 0 ? args[0].toLowerCase() : "help";
        if (is(sub, "cmd.display") || sub.equals("display")) {
            sub = "display";
        }
        switch (sub) {
            case "pack" -> {
                if (sender instanceof Player player) {
                    packs.send(player);
                    player.sendMessage(msg().get(player, "pack.sent"));
                } else {
                    sender.sendMessage(msg().get(sender, "players-only"));
                }
            }
            case "display" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(msg().get(sender, "players-only"));
                } else if (args.length < 2) {
                    chooseDisplay(player);
                } else {
                    DisplayMode mode = mode(args[1]);
                    if (mode == null) {
                        player.sendMessage(msg().get(player, "display.usage"));
                    } else {
                        setDisplay(player, mode);
                    }
                }
            }
            case "test" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(msg().get(sender, "players-only"));
                } else if (!sender.hasPermission(ADMIN)) {
                    noPermission(sender);
                } else {
                    service.openAs(player, demo.main, args.length > 1 && args[1].equalsIgnoreCase("fallback"));
                }
            }
            case "menu" -> {
                if (!sender.hasPermission(ADMIN)) {
                    noPermission(sender);
                } else if (args.length < 2) {
                    sender.sendMessage(msg().get(sender, "menu.usage",
                        "menus", String.join(", ", menus.all().stream().map(Menus.Menu::id).toList())));
                } else {
                    Player target = args.length > 2 ? getServer().getPlayerExact(args[2])
                        : sender instanceof Player p ? p : null;
                    if (target == null) {
                        sender.sendMessage(msg().get(sender, "player-not-found"));
                    } else if (menus.get(args[1]) == null) {
                        sender.sendMessage(msg().get(sender, "menu.unknown", "id", args[1]));
                    } else {
                        service.open(target, menus.get(args[1]).scene());
                    }
                }
            }
            case "item" -> {
                if (!sender.hasPermission(ADMIN)) {
                    noPermission(sender);
                    return true;
                }
                List<String> withItems = menus.all().stream()
                    .filter(m -> m.trigger() != null && m.trigger().item() != null).map(Menus.Menu::id).toList();
                if (args.length < 2) {
                    sender.sendMessage(msg().get(sender, "item.usage", "menus", String.join(", ", withItems)));
                    return true;
                }
                Player target = args.length > 2 ? getServer().getPlayerExact(args[2]) : sender instanceof Player p ? p : null;
                if (target == null) {
                    sender.sendMessage(msg().get(sender, "player-not-found"));
                } else if (!withItems.contains(args[1].toLowerCase())) {
                    sender.sendMessage(msg().get(sender, "item.none", "id", args[1]));
                } else if (triggers.give(target, args[1].toLowerCase(), true)) {
                    sender.sendMessage(msg().get(sender, "item.given", "id", args[1].toLowerCase(), "player", target.getName()));
                } else {
                    sender.sendMessage(msg().get(sender, "item.has", "player", target.getName()));
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
                sender.sendMessage(msg().get(sender, "status.pack", "theme", themeName, "sha1", packs.sha1(),
                    "hosting", settings.external() ? "external" : "embedded", "url", packs.url()));
                for (Player p : getServer().getOnlinePlayers()) {
                    Status st = packs.status(p);
                    sender.sendMessage(Component.text("  " + p.getName() + ": " + (st == null ? "-" : st)
                            + " (" + msg().language(p) + ")",
                        st == Status.SUCCESSFULLY_LOADED ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
                }
            }
            default -> sender.sendMessage(msg().get(sender, sender.hasPermission(ADMIN) ? "help.admin" : "help.player"));
        }
        return true;
    }

    private static String displayName(CommandSender to, DisplayMode m) {
        return msg().plain(to, switch (m) {
            case FULL -> "display.full";
            case NO_ICONS -> "display.no-icons";
            case SIMPLE -> "display.simple";
        });
    }

    private void setDisplay(Player player, DisplayMode mode) {
        service.displayMode(player, mode);
        player.sendMessage(msg().get(player, "display.set", "mode", displayName(player, mode)));
    }

    /** Native dialog (works even if a resource pack breaks the pixel GUI). */
    private void chooseDisplay(Player player) {
        DisplayMode current = service.displayMode(player);
        List<Component> lines = List.of(
            msg().get(player, "display.question"),
            msg().get(player, "display.info-full"),
            msg().get(player, "display.info-no-icons"),
            msg().get(player, "display.info-simple"));
        List<DialogFactory.FallbackButton> buttons = new ArrayList<>();
        for (DisplayMode m : DisplayMode.values()) {
            String label = (m == current ? "\u25b6 " : "") + displayName(player, m);
            buttons.add(new DialogFactory.FallbackButton(Component.text(label), null,
                () -> getServer().getScheduler().runTask(this, () -> {
                    setDisplay(player, m);
                    player.closeDialog();
                })));
        }
        DialogFactory.show(player, DialogFactory.fallback(msg().get(player, "display.title"), lines, buttons,
            msg().get(player, "dialog.close"), () -> { }));
    }

    private static void noPermission(CommandSender sender) {
        sender.sendMessage(msg().get(sender, "no-permission"));
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, String @NonNull [] args) {
        String lang = msg().language(sender);
        if (args.length == 2 && args[0].equalsIgnoreCase("menu") && sender.hasPermission(ADMIN)) {
            return menus.all().stream().map(Menus.Menu::id).filter(id -> id.startsWith(args[1].toLowerCase())).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("item") && sender.hasPermission(ADMIN)) {
            return menus.all().stream().filter(m -> m.trigger() != null && m.trigger().item() != null)
                .map(Menus.Menu::id).filter(id -> id.startsWith(args[1].toLowerCase())).toList();
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("menu") || args[0].equalsIgnoreCase("item")) && sender.hasPermission(ADMIN)) {
            return getServer().getOnlinePlayers().stream().map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase())).toList();
        }
        if (args.length == 2 && (is(args[0], "cmd.display") || args[0].equalsIgnoreCase("display"))) {
            return Stream.of("cmd.full", "cmd.no-icons", "cmd.simple").map(k -> msg().raw(lang, k))
                .filter(s -> s.startsWith(args[1].toLowerCase())).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("test") && sender.hasPermission(ADMIN)) {
            return "fallback".startsWith(args[1].toLowerCase()) ? List.of("fallback") : List.of();
        }
        if (args.length != 1) {
            return List.of();
        }
        String display = msg().raw(lang, "cmd.display");
        List<String> subs = sender.hasPermission(ADMIN) ? List.of(display, "item", "menu", "pack", "reload", "status", "test")
            : List.of(display, "pack");
        return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
    }
}
