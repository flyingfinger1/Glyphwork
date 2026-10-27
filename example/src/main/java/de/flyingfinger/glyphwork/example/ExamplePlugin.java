package de.flyingfinger.glyphwork.example;

import de.flyingfinger.glyphwork.api.Glyphwork;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Registers /trader (alias /haendler), which opens the trader screen. plugin.yml: depend: [Glyphwork]. */
public final class ExamplePlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        Trader trader = new Trader();
        getCommand("trader").setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Players only."));
                return true;
            }
            // players without the resource pack get a plain native dialog of the same scene
            Glyphwork.get().open(player, trader.main);
            return true;
        });
    }
}
