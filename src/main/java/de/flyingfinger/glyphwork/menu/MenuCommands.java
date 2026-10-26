package de.flyingfinger.glyphwork.menu;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

/** Registers a command per menu ("command:" in the file) at runtime, and removes them on reload. */
public final class MenuCommands {

    private final Plugin plugin;
    private final List<Command> registered = new ArrayList<>();

    public MenuCommands(Plugin plugin) {
        this.plugin = plugin;
    }

    private static final class MenuCommand extends Command {
        private final String menu;
        private final BiConsumer<Player, String> open;

        MenuCommand(String name, Menus.Menu m, BiConsumer<Player, String> open) {
            super(name, m.description() != null ? m.description() : "Opens the menu " + m.id(), "/" + name, m.aliases());
            this.menu = m.id();
            this.open = open;
            if (m.permission() != null) {
                setPermission(m.permission());
            }
        }

        @Override
        public boolean execute(@NonNull CommandSender sender, @NonNull String label, String @NonNull [] args) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(de.flyingfinger.glyphwork.core.Messages.active().get(sender, "commands.only-players"));
                return true;
            }
            if (!testPermission(p)) {
                return true;
            }
            open.accept(p, menu);
            return true;
        }

        @Override
        public @NonNull List<String> tabComplete(@NonNull CommandSender sender, @NonNull String alias, String @NonNull [] args) {
            return List.of();
        }
    }

    /** Replaces the registered menu commands with those of {@code menus}. */
    public void register(Menus menus, BiConsumer<Player, String> open) {
        unregister();
        CommandMap map = Bukkit.getServer().getCommandMap();
        Logger log = plugin.getLogger();
        String prefix = plugin.getName().toLowerCase();
        for (Menus.Menu m : menus.all()) {
            if (m.command() == null) {
                continue;
            }
            Command existing = map.getCommand(m.command());
            if (existing != null) {
                log.warning("Menu " + m.id() + ": command /" + m.command() + " already exists, use /" + prefix + ":"
                    + m.command() + " or pick another name");
            }
            MenuCommand cmd = new MenuCommand(m.command(), m, open);
            map.register(m.command(), prefix, cmd);
            registered.add(cmd);
        }
        Bukkit.getOnlinePlayers().forEach(Player::updateCommands);
    }

    public void unregister() {
        if (registered.isEmpty()) {
            return;
        }
        CommandMap map = Bukkit.getServer().getCommandMap();
        Map<String, Command> known = map.getKnownCommands();
        known.values().removeIf(registered::contains);
        for (Command c : registered) {
            c.unregister(map);
        }
        registered.clear();
        Bukkit.getOnlinePlayers().forEach(Player::updateCommands);
    }
}
