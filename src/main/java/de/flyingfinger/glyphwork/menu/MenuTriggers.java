package de.flyingfinger.glyphwork.menu;

import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Opens menus by themselves ("open-on:" in a menu file): on joining, and with a menu item that
 * players get into their hotbar. The item carries the menu id, so only it opens the menu (not every
 * compass); it cannot be dropped and comes back after death. Players without the menu's permission
 * do not get it.
 */
public final class MenuTriggers implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Plugin plugin;
    private final Supplier<Menus> menus;
    private final BiConsumer<Player, String> open;
    private final Predicate<Player> waitsForPack;
    private final NamespacedKey key;
    /** Players whose join menu waits for the pack to finish loading (with a timeout). */
    private final Map<UUID, BukkitTask> waiting = new HashMap<>();
    private final Set<UUID> firstJoin = new HashSet<>();
    private final Set<String> warned = new HashSet<>();
    /** Kept menu items a player had when dying; given back on respawn. */
    private final Map<UUID, java.util.List<ItemStack>> kept = new HashMap<>();
    private static @Nullable MenuTriggers active;

    /** The triggers of the running plugin (for the give-item action), or null. */
    public static @Nullable MenuTriggers active() {
        return active;
    }

    /**
     * @param waitsForPack whether a joining player is being sent the pack (then the join menu opens
     *                     once it is loaded, declined or failed: the pack reload would close it)
     */
    public MenuTriggers(Plugin plugin, Supplier<Menus> menus, BiConsumer<Player, String> open, Predicate<Player> waitsForPack) {
        this.plugin = plugin;
        this.menus = menus;
        this.open = open;
        this.waitsForPack = waitsForPack;
        this.key = new NamespacedKey(plugin, "menu");
        active = this;
    }

    // ------------------------------------------------------------------ join

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        giveItems(p, !p.hasPlayedBefore());
        if (joinMenu(p, !p.hasPlayedBefore()) == null) {
            return;
        }
        if (!p.hasPlayedBefore()) {
            firstJoin.add(p.getUniqueId());
        }
        if (waitsForPack.test(p)) {
            // opened by packDone(); the timeout covers clients that never answer
            waiting.put(p.getUniqueId(), Bukkit.getScheduler().runTaskLater(plugin, () -> openJoin(p), 20 * 30));
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, () -> openJoin(p), 20);
        }
    }

    /** The pack of a player finished (loaded, declined or failed): now the join menu can open. */
    public void packDone(Player p) {
        BukkitTask t = waiting.remove(p.getUniqueId());
        if (t != null) {
            t.cancel();
            Bukkit.getScheduler().runTaskLater(plugin, () -> openJoin(p), 10);
        }
    }

    private void openJoin(Player p) {
        waiting.remove(p.getUniqueId());
        boolean first = firstJoin.remove(p.getUniqueId());
        if (!p.isOnline()) {
            return;
        }
        Menus.Menu m = joinMenu(p, first);
        if (m != null) {
            open.accept(p, m.id());
        }
    }

    /** The first menu (by file name) that opens on joining for this player. */
    private Menus.@Nullable Menu joinMenu(Player p, boolean first) {
        for (Menus.Menu m : menus.get().all()) {
            Menus.Trigger t = m.trigger();
            if (t != null && t.join() && (!t.firstJoinOnly() || first) && m.allowed(p)) {
                return m;
            }
        }
        return null;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        BukkitTask t = waiting.remove(event.getPlayer().getUniqueId());
        if (t != null) {
            t.cancel();
        }
        firstJoin.remove(event.getPlayer().getUniqueId());
        kept.remove(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ menu items

    /** The menu id an item opens, or null. */
    public @Nullable String menuOf(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        String id = menuOf(event.getItem());
        if (id == null) {
            return;
        }
        // the item only opens the menu: no compass pointing, no block placing, no eating
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        event.setCancelled(true);
        open.accept(event.getPlayer(), id);
    }

    /** The menu item spec of a tagged item, or null. */
    private Menus.@Nullable MenuItem spec(@Nullable ItemStack item) {
        String id = menuOf(item);
        Menus.Menu m = id == null ? null : menus.get().get(id);
        return m == null || m.trigger() == null ? null : m.trigger().item();
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Menus.MenuItem spec = spec(event.getItemDrop().getItemStack());
        if (spec != null && spec.keep()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        java.util.List<ItemStack> back = new java.util.ArrayList<>();
        event.getDrops().removeIf(i -> {
            Menus.MenuItem spec = spec(i);
            if (spec != null && spec.keep()) {
                back.add(i.clone());
                return true;
            }
            return false;
        });
        if (!back.isEmpty()) {
            kept.put(event.getEntity().getUniqueId(), back);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        java.util.List<ItemStack> back = kept.remove(p.getUniqueId());
        if (back != null) {
            Bukkit.getScheduler().runTask(plugin, () -> back.forEach(i -> place(p, i, slotOf(i))));
        }
    }

    private int slotOf(ItemStack item) {
        Menus.MenuItem spec = spec(item);
        return spec == null ? 8 : spec.slot();
    }

    /** After a reload: everyone online gets the items of new menus that hand theirs out on every join. */
    public void giveAll() {
        Bukkit.getOnlinePlayers().forEach(p -> giveItems(p, false));
    }

    /** Gives the player the items of all menus that hand theirs out now (join; first on a first join). */
    public void giveItems(Player p, boolean firstJoin) {
        if (!p.isOnline()) {
            return;
        }
        for (Menus.Menu m : menus.get().all()) {
            Menus.Trigger t = m.trigger();
            if (t == null || t.item() == null) {
                continue;
            }
            Menus.Give g = t.item().give();
            if (g == Menus.Give.JOIN || g == Menus.Give.FIRST && firstJoin) {
                give(p, m.id(), false);
            }
        }
    }

    /**
     * Gives the menu item of menu {@code id} unless the player carries it already (or may not open the
     * menu). {@code tell}: answer the player (give-item action, command).
     * @return whether an item was given
     */
    public boolean give(Player p, String id, boolean tell) {
        Menus.Menu m = menus.get().get(id);
        if (m == null || m.trigger() == null || m.trigger().item() == null || !m.allowed(p)) {
            return false;
        }
        if (carries(p.getInventory(), m.id())) {
            return false;
        }
        ItemStack item = item(m.id(), m.trigger().item());
        if (item == null) {
            return false;
        }
        place(p, item, m.trigger().item().slot());
        return true;
    }

    private static void place(Player p, ItemStack item, int slot) {
        ItemStack there = p.getInventory().getItem(slot);
        if (there == null || there.getType().isAir()) {
            p.getInventory().setItem(slot, item);
        } else {
            p.getInventory().addItem(item).values()
                .forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
        }
    }

    private boolean carries(PlayerInventory inv, String id) {
        for (ItemStack i : inv.getContents()) {
            if (id.equals(menuOf(i))) {
                return true;
            }
        }
        return false;
    }

    /** The menu item: the material with name, lore and the menu id; null for an unknown material. */
    public @Nullable ItemStack item(String id, Menus.MenuItem spec) {
        Material material = Material.matchMaterial(spec.material());
        if (material == null || !material.isItem() || material.isAir()) {
            if (warned.add(id + ":" + spec.material())) {
                plugin.getLogger().warning("Menu " + id + ": open-on.item '" + spec.material() + "' is no item");
            }
            return null;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (spec.name() != null) {
            meta.displayName(MM.deserialize(spec.name()).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        }
        if (!spec.lore().isEmpty()) {
            meta.lore(spec.lore().stream()
                .map(l -> MM.deserialize(l).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE))
                .toList());
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }
}
