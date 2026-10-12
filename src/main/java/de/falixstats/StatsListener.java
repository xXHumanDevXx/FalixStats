package de.falixstats;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Gibt neuen Spielern Sidebar und Bossbar und räumt beim Verlassen auf. */
public class StatsListener implements Listener {

    private final FalixStats plugin;

    public StatsListener(FalixStats plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.stats().attach(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.stats().detach(e.getPlayer());
    }
}
