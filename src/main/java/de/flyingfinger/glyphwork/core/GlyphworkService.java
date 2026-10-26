package de.flyingfinger.glyphwork.core;

import de.flyingfinger.glyphwork.api.Click;
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
    private final FallbackRenderer fallbackRenderer;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private volatile SceneRenderer renderer;
    private volatile int maxHeight;
    private volatile boolean fallback;

    public GlyphworkService(Plugin plugin, SceneRenderer renderer, int maxHeight, boolean fallback, Predicate<Player> hasPack) {
        this.plugin = plugin;
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

        Session(UUID id) {
            this.id = id;
        }
    }

    private Session session(Player player) {
        return sessions.computeIfAbsent(player.getUniqueId(), Session::new);
    }

    public void forget(Player player) {
        sessions.remove(player.getUniqueId());
    }

    @Override
    public void open(Player player, Scene scene) {
        openAs(player, scene, false);
    }

    /** Opens a scene; with {@code forceFallback} it shows the plain version (preview for admins). */
    public void openAs(Player player, Scene scene, boolean forceFallback) {
        Session s = session(player);
        s.forceFallback = forceFallback;
        s.stack.clear();
        s.stack.push(scene);
        show(player, s);
    }

    @Override
    public void close(Player player) {
        Session s = sessions.get(player.getUniqueId());
        if (s != null) {
            s.stack.clear();
            s.generation++;
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

    private void show(Player player, Session s) {
        Scene scene = s.stack.peek();
        if (scene == null) {
            player.closeDialog();
            return;
        }
        int generation = ++s.generation;
        ViewImpl view = new ViewImpl(player, s, scene);
        if (s.forceFallback || (fallback && !hasPack.test(player))) {
            showFallback(player, s, generation, scene, view);
            return;
        }
        LineBuilder line = renderer.render(scene, view, (name, action) -> callback(player, s, generation, name, action));
        DialogFactory.show(player, DialogFactory.gui(scene.title().apply(view), line.build(scene.height()),
            Math.max(scene.width(), line.width()), scene.exitLabel()));
    }

    private void showFallback(Player player, Session s, int generation, Scene scene, ViewImpl view) {
        FallbackRenderer.Model model = fallbackRenderer.render(scene, view);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text("Vereinfachte Ansicht – Resource-Pack nicht geladen (/glyphwork pack)",
            NamedTextColor.GRAY));
        lines.addAll(model.lines());
        List<DialogFactory.FallbackButton> buttons = model.actions().stream()
            .map(a -> new DialogFactory.FallbackButton(a.label(), a.tooltip(),
                () -> run(player, s, generation, "fallback " + scene.id(), a.onClick())))
            .toList();
        DialogFactory.show(player, DialogFactory.fallback(model.title(), lines, buttons, scene.exitLabel()));
    }

    private ClickEvent<?> callback(Player player, Session s, int generation, String name, Consumer<Click> action) {
        return ClickEvent.callback(audience -> run(player, s, generation, name, action), DialogFactory.callbackOptions());
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

    private final class ClickImpl extends ViewImpl implements Click {

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
            show(player, session);
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
                Component.text("OK"), Component.text("Abbrechen"),
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
    }
}
