package de.flyingfinger.glyphwork.core;

import de.flyingfinger.glyphwork.api.Click;
import de.flyingfinger.glyphwork.api.DisplayMode;
import de.flyingfinger.glyphwork.api.Glyphwork;
import de.flyingfinger.glyphwork.api.Scene;
import de.flyingfinger.glyphwork.api.View;
import de.flyingfinger.glyphwork.dialog.DialogFactory;
import de.flyingfinger.glyphwork.render.FallbackRenderer;
import de.flyingfinger.glyphwork.render.LineBuilder;
import de.flyingfinger.glyphwork.render.SceneRenderer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;

/** Implementation of the public API: sessions, navigation and click routing. */
public final class GlyphworkService implements Glyphwork {

    private final Plugin plugin;
    private final Predicate<Player> hasPack;
    private final Consumer<Player> sendPack;
    private final Predicate<Player> hasServerPack;
    private final NamespacedKey displayKey;
    private final FallbackRenderer fallbackRenderer;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private volatile SceneRenderer renderer;
    private volatile int maxHeight;
    private volatile boolean fallback;

    public GlyphworkService(Plugin plugin, SceneRenderer renderer, int maxHeight, boolean fallback,
                            Predicate<Player> hasPack, Consumer<Player> sendPack, Predicate<Player> hasServerPack) {
        this.hasServerPack = hasServerPack;
        this.plugin = plugin;
        this.sendPack = sendPack;
        this.displayKey = new NamespacedKey(plugin, "display");
        this.renderer = renderer;
        this.maxHeight = maxHeight;
        this.fallback = fallback;
        this.hasPack = hasPack;
        this.fallbackRenderer = new FallbackRenderer(plugin.getLogger());
    }

    /** After /glyphwork reload: new theme/pack. Open screens keep their state. */
    public void reconfigure(SceneRenderer renderer, int maxHeight, boolean fallback) {
        this.renderer = renderer;
        this.maxHeight = maxHeight;
        this.fallback = fallback;
    }

    /** Per player: the screen history and per-scene state. */
    private static final class Session {
        final UUID id;
        final Deque<Scene> stack = new ArrayDeque<>();
        final Map<String, Map<String, Object>> state = new HashMap<>();
        /** Increases with every shown screen; clicks from older screens are ignored. */
        int generation;
        /** Show the plain fallback even with the pack (admin preview). */
        boolean forceFallback;
        /** The pending redraw of a scene with refresh, or null. */
        org.bukkit.scheduler.@org.jspecify.annotations.Nullable BukkitTask refresh;

        void stopRefresh() {
            if (refresh != null) {
                refresh.cancel();
                refresh = null;
            }
        }

        Session(UUID id) {
            this.id = id;
        }
    }

    private Session session(Player player) {
        return sessions.computeIfAbsent(player.getUniqueId(), Session::new);
    }

    public void forget(Player player) {
        Session s = sessions.remove(player.getUniqueId());
        if (s != null) {
            s.stopRefresh();
        }
    }

    @Override
    public void open(Player player, Scene scene) {
        openAs(player, scene, false);
    }

    /** Opens a scene; with {@code forceFallback} it shows the plain version (preview for admins). */
    public void openAs(Player player, Scene scene, boolean forceFallback) {
        Session s = session(player);
        if (!s.stack.isEmpty()) {
            closedHook(player, s, s.stack.peek());   // another screen replaces this one
        }
        s.forceFallback = forceFallback;
        s.stack.clear();
        s.stack.push(scene);
        if (openedHook(player, s, scene)) {
            show(player, s);
        }
    }

    /** Runs the scene's onOpen; false if the hook already showed or closed something itself. */
    private boolean openedHook(Player player, Session s, Scene scene) {
        if (scene.onOpen() == null) {
            return true;
        }
        HookClick c = new HookClick(player, s, scene, false);
        try {
            scene.onOpen().accept(c);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "onOpen of " + scene.id() + " failed", e);
        }
        return !c.navigated;
    }

    /** Runs the scene's onClose (the screen is already gone). */
    private void closedHook(Player player, Session s, @org.jspecify.annotations.Nullable Scene scene) {
        if (scene == null || scene.onClose() == null || !player.isOnline()) {
            return;
        }
        try {
            scene.onClose().accept(new HookClick(player, s, scene, true));
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "onClose of " + scene.id() + " failed", e);
        }
    }

    @Override
    public void close(Player player) {
        Session s = sessions.get(player.getUniqueId());
        if (s != null) {
            Scene top = s.stack.peek();
            s.stack.clear();
            s.generation++;
            s.stopRefresh();
            closedHook(player, s, top);
        }
        player.closeDialog();
    }

    @Override
    public boolean hasPack(Player player) {
        return hasPack.test(player);
    }

    @Override
    public int maxHeight() {
        return maxHeight;
    }

    @Override
    public DisplayMode displayMode(Player player) {
        String s = player.getPersistentDataContainer().get(displayKey, PersistentDataType.STRING);
        if (s != null) {
            try {
                return DisplayMode.valueOf(s);
            } catch (IllegalArgumentException ignored) {
                // value from a newer version; use the default
            }
        }
        return DisplayMode.FULL;
    }

    @Override
    public void displayMode(Player player, DisplayMode mode) {
        if (mode == DisplayMode.FULL) {
            player.getPersistentDataContainer().remove(displayKey);
        } else {
            player.getPersistentDataContainer().set(displayKey, PersistentDataType.STRING, mode.name());
        }
    }

    @Override
    public void sendPack(Player player) {
        sendPack.accept(player);
    }

    private void show(Player player, Session s) {
        show(player, s, false);
    }

    /**
     * Draws the top scene. A {@code redraw} (refresh) keeps the generation, so clicks on the
     * previous drawing of the same scene still count.
     */
    private void show(Player player, Session s, boolean redraw) {
        s.stopRefresh();
        Scene scene = s.stack.peek();
        if (scene == null) {
            player.closeDialog();
            return;
        }
        int generation = redraw ? s.generation : ++s.generation;
        ViewImpl view = new ViewImpl(player, s, scene);
        try {
            showNow(player, s, scene, generation, view);
        } finally {
            view.drawing = false;
            scheduleRefresh(player, s, scene, generation, view.redrawAfterMs);
        }
    }

    private void showNow(Player player, Session s, Scene scene, int generation, ViewImpl view) {
        DisplayMode mode = displayMode(player);
        if (s.forceFallback || mode == DisplayMode.SIMPLE || (fallback && !hasPack.test(player))) {
            showFallback(player, s, generation, scene, view);
            return;
        }
        LineBuilder line = renderer.render(scene, view, (name, action) -> callback(player, s, generation, scene, name, action),
            mode != DisplayMode.NO_ICONS, hasServerPack.test(player));
        java.util.Set<String> missing = renderer.takeMissingFaces();
        if (!missing.isEmpty() && renderer.faces() != null) {
            // big heads whose skin is still loading: draw again once one arrives (clicks stay valid)
            renderer.faces().whenLoaded(missing, () -> {
                if (player.isOnline() && s.generation == generation && s.stack.peek() == scene && !inventoryOpen(player)) {
                    show(player, s, true);
                }
            });
        }
        DialogFactory.show(player, DialogFactory.gui(scene.title().apply(view), line.build(scene.height()),
            Math.max(scene.width(), line.width()), exitLabel(player, scene), () -> exited(player, s, generation),
            DialogFactory.callbackOptions(lifetime(scene))));
    }

    /**
     * Redraws the scene after its refresh interval, or earlier if the drawing asked for it
     * (View.redrawAfter, e.g. a cooldown running out), if it is still the one the player sees.
     */
    private void scheduleRefresh(Player player, Session s, Scene scene, int generation, long askedMs) {
        long ticks = scene.refresh() == null ? Long.MAX_VALUE : Math.max(10, scene.refresh().toMillis() / 50);
        if (askedMs >= 0) {
            ticks = Math.min(ticks, Math.max(2, (askedMs + 49) / 50));
        }
        if (ticks == Long.MAX_VALUE) {
            return;
        }
        s.refresh = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            s.refresh = null;
            if (!player.isOnline() || s.generation != generation || s.stack.peek() != scene) {
                return;
            }
            if (inventoryOpen(player)) {
                return;                             // another plugin opened an inventory over the menu: leave it alone
            }
            show(player, s, true);
        }, ticks);
    }

    /**
     * Whether an inventory screen is open. Without one, Paper reports the player's own view:
     * CRAFTING in survival, CREATIVE in creative mode.
     */
    static boolean inventoryOpen(Player player) {
        org.bukkit.event.inventory.InventoryType type = player.getOpenInventory().getType();
        return type != org.bukkit.event.inventory.InventoryType.CRAFTING
            && type != org.bukkit.event.inventory.InventoryType.CREATIVE;
    }

    /** The player closed the dialog (exit button or ESC): forget the screen, stop redrawing. */
    private void exited(Player player, Session s, int generation) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (s.generation == generation) {
                Scene top = s.stack.peek();
                s.stack.clear();
                s.generation++;
                s.stopRefresh();
                closedHook(player, s, top);
            }
        });
    }

    private void showFallback(Player player, Session s, int generation, Scene scene, ViewImpl view) {
        FallbackRenderer.Model model = fallbackRenderer.render(scene, view);
        List<Component> lines = new ArrayList<>();
        if (displayMode(player) != DisplayMode.SIMPLE && !s.forceFallback) {
            lines.add(Messages.active().get(player, "fallback.notice"));
        }
        lines.addAll(model.lines());
        List<DialogFactory.FallbackButton> buttons = model.actions().stream()
            .map(a -> new DialogFactory.FallbackButton(a.label(), a.tooltip(),
                () -> run(player, s, generation, "fallback " + scene.id(), a.onClick())))
            .toList();
        DialogFactory.show(player, DialogFactory.fallback(model.title(), lines, buttons, exitLabel(player, scene),
            () -> exited(player, s, generation)));
    }

    private static Component exitLabel(Player player, Scene scene) {
        return scene.exitLabel() != null ? scene.exitLabel() : Messages.active().get(player, "dialog.close");
    }

    private ClickEvent<?> callback(Player player, Session s, int generation, Scene scene, String name, Consumer<Click> action) {
        return ClickEvent.callback(audience -> run(player, s, generation, name, action),
            DialogFactory.callbackOptions(lifetime(scene)));
    }

    /** A scene that redraws itself makes new callbacks every time: let the old ones expire soon. */
    private static java.time.Duration lifetime(Scene scene) {
        return scene.refresh() == null ? java.time.Duration.ofMinutes(30)
            : java.time.Duration.ofMillis(Math.max(60_000, scene.refresh().toMillis() * 3));
    }

    /** Runs a click action on the main thread, unless the screen has been replaced meanwhile. */
    private void run(Player player, Session s, int generation, String name, Consumer<Click> action) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || s.generation != generation || s.stack.isEmpty()) {
                return;
            }
            try {
                action.accept(new ClickImpl(player, s, s.stack.peek()));
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Click handler " + name + " failed", e);
            }
        });
    }

    private class ViewImpl implements View {
        final Player player;
        final Session session;
        final Scene scene;
        /** Shortest redraw asked for while drawing (ms), or -1. */
        long redrawAfterMs = -1;
        boolean drawing = true;

        @Override
        public void redrawAfter(java.time.Duration delay) {
            if (drawing) {
                long ms = Math.max(0, delay.toMillis());
                redrawAfterMs = redrawAfterMs < 0 ? ms : Math.min(redrawAfterMs, ms);
            }
        }

        ViewImpl(Player player, Session session, Scene scene) {
            this.player = player;
            this.session = session;
            this.scene = scene;
        }

        @Override
        public Player player() {
            return player;
        }

        @Override
        public Scene scene() {
            return scene;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(String key, T fallback) {
            Object v = session.state.getOrDefault(scene.id(), Map.of()).get(key);
            return v == null ? fallback : (T) v;
        }

        @Override
        public void set(String key, Object value) {
            session.state.computeIfAbsent(scene.id(), k -> new HashMap<>()).put(key, value);
        }
    }

    /**
     * The Click handed to onOpen / onClose. While opening, reopen is not needed (the scene is drawn
     * next) and open/close replace it; after closing, nothing navigates.
     */
    private final class HookClick extends ClickImpl {
        private final boolean closed;
        boolean navigated;

        HookClick(Player player, Session session, Scene scene, boolean closed) {
            super(player, session, scene);
            this.closed = closed;
        }

        @Override
        public void reopen() {
        }

        @Override
        public void open(Scene next) {
            if (!closed) {
                navigated = true;
                super.open(next);
            }
        }

        @Override
        public void back() {
        }

        @Override
        public void close() {
            if (!closed) {
                navigated = true;
                super.close();
            }
        }

        @Override
        public void prompt(Component title, String initial, Consumer<String> onSubmit) {
            if (!closed) {
                navigated = true;
                super.prompt(title, initial, onSubmit);
            }
        }

        @Override
        public void form(de.flyingfinger.glyphwork.api.Form form, Consumer<de.flyingfinger.glyphwork.api.Form.Values> onSubmit) {
            if (!closed) {
                navigated = true;
                super.form(form, onSubmit);
            }
        }
    }

    private class ClickImpl extends ViewImpl implements Click {

        ClickImpl(Player player, Session session, Scene scene) {
            super(player, session, scene);
        }

        @Override
        public void reopen() {
            show(player, session);
        }

        @Override
        public void open(Scene next) {
            session.stack.push(next);
            if (openedHook(player, session, next)) {
                show(player, session);
            }
        }

        @Override
        public void back() {
            session.stack.pop();
            show(player, session);
        }

        @Override
        public void close() {
            GlyphworkService.this.close(player);
        }

        @Override
        public void prompt(Component title, String initial, Consumer<String> onSubmit) {
            int generation = ++session.generation;
            Runnable back = () -> {
                if (player.isOnline() && session.generation == generation) {
                    show(player, session);
                }
            };
            DialogFactory.show(player, DialogFactory.prompt(title, initial,
                Messages.active().get(player, "dialog.ok"), Messages.active().get(player, "dialog.cancel"),
                text -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (session.generation != generation) {
                        return;
                    }
                    try {
                        onSubmit.accept(text);
                    } catch (RuntimeException e) {
                        plugin.getLogger().log(Level.WARNING, "Prompt handler failed", e);
                    }
                    back.run();
                }),
                () -> Bukkit.getScheduler().runTask(plugin, back)));
        }

        @Override
        public void form(de.flyingfinger.glyphwork.api.Form form, Consumer<de.flyingfinger.glyphwork.api.Form.Values> onSubmit) {
            int generation = ++session.generation;
            session.stopRefresh();
            Runnable back = () -> {
                if (player.isOnline() && session.generation == generation) {
                    show(player, session);
                }
            };
            DialogFactory.show(player, DialogFactory.form(form,
                Messages.active().get(player, "dialog.ok"), Messages.active().get(player, "dialog.cancel"),
                values -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (session.generation != generation) {
                        return;
                    }
                    try {
                        onSubmit.accept(values);
                    } catch (RuntimeException e) {
                        plugin.getLogger().log(Level.WARNING, "Form handler failed", e);
                    }
                    back.run();
                }),
                () -> Bukkit.getScheduler().runTask(plugin, back)));
        }
    }
}
