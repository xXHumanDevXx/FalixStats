package de.falixstats;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /fstats sidebar | bossbar | reload */
public class StatsCommand implements CommandExecutor, TabCompleter {

    private final FalixStats plugin;

    public StatsCommand(FalixStats plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (a.length == 0) {
            usage(s);
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "sidebar" -> {
                if (!(s instanceof Player p)) {
                    s.sendMessage(plugin.msg("player-only"));
                    return true;
                }
                boolean on = plugin.stats().toggleSidebar(p);
                p.sendMessage(plugin.msg(on ? "sidebar-on" : "sidebar-off"));
            }
            case "bossbar" -> {
                if (!(s instanceof Player p)) {
                    s.sendMessage(plugin.msg("player-only"));
                    return true;
                }
                boolean on = plugin.stats().toggleBossBar(p);
                p.sendMessage(plugin.msg(on ? "bossbar-on" : "bossbar-off"));
            }
            case "reload" -> {
                if (!s.hasPermission("falixstats.admin")) {
                    s.sendMessage(plugin.msg("no-permission"));
                    return true;
                }
                plugin.reloadAll();
                s.sendMessage(plugin.msg("reloaded"));
            }
            default -> usage(s);
        }
        return true;
    }

    private void usage(CommandSender s) {
        s.sendMessage(plugin.msg("usage", "{usage}", "/fstats <sidebar | bossbar | reload>"));
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            for (String o : List.of("sidebar", "bossbar", "reload")) {
                if (o.startsWith(a[0].toLowerCase(Locale.ROOT))) out.add(o);
            }
        }
        return out;
    }
}
