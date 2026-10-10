package de.falixstats;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Hauptklasse: startet Sidebar- und Bossbar-Aktualisierung. */
public class FalixStats extends JavaPlugin {

    private StatsManager stats;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        stats = new StatsManager(this);

        Bukkit.getPluginManager().registerEvents(new StatsListener(this), this);

        StatsCommand executor = new StatsCommand(this);
        PluginCommand cmd = getCommand("fstats");
        if (cmd != null) {
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            stats.attach(p);
        }

        long period = Math.max(1L, getConfig().getLong("settings.update-ticks", 20L));
        Bukkit.getScheduler().runTaskTimer(this, stats::tick, 20L, period);
        getLogger().info("FalixStats aktiviert.");
    }

    @Override
    public void onDisable() {
        if (stats != null) stats.shutdown();
    }

    public StatsManager stats() {
        return stats;
    }

    /** Config neu laden und Sidebar und Bossbar neu aufbauen. */
    public void reloadAll() {
        reloadConfig();
        stats.reload();
    }

    public String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    /** Nachricht aus messages.<key>, Platzhalter als Paare: "{player}", name, ... */
    public String msg(String key, String... pairs) {
        String raw = getConfig().getString("messages." + key, "&cFehlende Nachricht: " + key);
        raw = raw.replace("{prefix}", getConfig().getString("messages.prefix", ""));
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            raw = raw.replace(pairs[i], pairs[i + 1]);
        }
        return color(raw);
    }
}
