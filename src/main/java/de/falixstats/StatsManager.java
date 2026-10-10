package de.falixstats;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Baut die Sidebar pro Spieler und die gemeinsame Bossbar und hält sie aktuell. */
public class StatsManager {

    private static final int MAX_LINES = 15;

    private final FalixStats plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Set<UUID> sidebarOff = new HashSet<>();
    private final Set<UUID> bossbarOff = new HashSet<>();
    private BossBar bossBar;

    public StatsManager(FalixStats plugin) {
        this.plugin = plugin;
        loadBossBar();
    }

    // ------------------------------------------------------------------
    //  Bossbar
    // ------------------------------------------------------------------

    private void loadBossBar() {
        if (bossBar != null) {
            bossBar.removeAll();
        }
        BarColor color = enumOr(BarColor.class, cfg("bossbar.color", "PURPLE"), BarColor.PURPLE);
        BarStyle style = enumOr(BarStyle.class, cfg("bossbar.style", "SOLID"), BarStyle.SOLID);
        bossBar = Bukkit.createBossBar("", color, style);
        bossBar.setVisible(plugin.getConfig().getBoolean("bossbar.enabled", true));
    }

    // ------------------------------------------------------------------
    //  Sidebar und Spieler
    // ------------------------------------------------------------------

    /** Gibt einem Spieler Sidebar und Bossbar, sofern nicht ausgeschaltet. */
    public void attach(Player p) {
        UUID id = p.getUniqueId();
        if (plugin.getConfig().getBoolean("sidebar.enabled", true) && !sidebarOff.contains(id)) {
            Scoreboard board = buildBoard();
            boards.put(id, board);
            p.setScoreboard(board);
        }
        if (plugin.getConfig().getBoolean("bossbar.enabled", true) && !bossbarOff.contains(id)) {
            bossBar.addPlayer(p);
        }
    }

    public void detach(Player p) {
        boards.remove(p.getUniqueId());
        bossBar.removePlayer(p);
    }

    /** Setzt den Spieler auf das Standard-Scoreboard zurück. */
    private void resetPlayer(Player p) {
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        bossBar.removePlayer(p);
    }

    public boolean toggleSidebar(Player p) {
        UUID id = p.getUniqueId();
        if (sidebarOff.remove(id)) {
            attach(p);
            return true;
        }
        sidebarOff.add(id);
        boards.remove(id);
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        return false;
    }

    public boolean toggleBossBar(Player p) {
        UUID id = p.getUniqueId();
        if (bossbarOff.remove(id)) {
            attach(p);
            return true;
        }
        bossbarOff.add(id);
        bossBar.removePlayer(p);
        return false;
    }

    private Scoreboard buildBoard() {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("falixstats", "dummy",
                plugin.color(plugin.getConfig().getString("sidebar.title", "&bFalixStats")));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        int lines = Math.min(MAX_LINES, plugin.getConfig().getStringList("sidebar.lines").size());
        for (int i = 0; i < lines; i++) {
            Team team = board.registerNewTeam("fs" + i);
            String entry = entry(i);
            team.addEntry(entry);
            obj.getScore(entry).setScore(lines - i);
        }
        return board;
    }

    /** Unsichtbarer, eindeutiger Name pro Zeile (nur Reset-Codes). */
    private static String entry(int i) {
        return org.bukkit.ChatColor.RESET.toString().repeat(i + 1);
    }

    // ------------------------------------------------------------------
    //  Aktualisierung
    // ------------------------------------------------------------------

    /** Läuft periodisch: Sidebar-Texte und Bossbar aktualisieren. */
    public void tick() {
        List<String> lines = plugin.getConfig().getStringList("sidebar.lines");
        int count = Math.min(MAX_LINES, lines.size());

        for (Player p : Bukkit.getOnlinePlayers()) {
            Scoreboard board = boards.get(p.getUniqueId());
            if (board == null) continue;
            for (int i = 0; i < count; i++) {
                Team team = board.getTeam("fs" + i);
                if (team != null) {
                    team.setPrefix(plugin.color(replace(lines.get(i), p)));
                }
            }
        }

        if (plugin.getConfig().getBoolean("bossbar.enabled", true)) {
            bossBar.setTitle(plugin.color(replace(cfg("bossbar.text", ""), null)));
            bossBar.setProgress(progress());
        }
    }

    private double progress() {
        String mode = cfg("bossbar.progress-mode", "fixed").toLowerCase(Locale.ROOT);
        double value;
        switch (mode) {
            case "online" -> value = Bukkit.getMaxPlayers() == 0 ? 0
                    : (double) Bukkit.getOnlinePlayers().size() / Bukkit.getMaxPlayers();
            case "time" -> value = Bukkit.getWorlds().isEmpty() ? 0
                    : Math.floorMod(Bukkit.getWorlds().get(0).getTime(), 24000L) / 24000.0;
            default -> value = plugin.getConfig().getDouble("bossbar.fixed-progress", 1.0);
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    // ------------------------------------------------------------------
    //  Platzhalter
    // ------------------------------------------------------------------

    private String replace(String text, Player p) {
        World world = p != null ? p.getWorld() : Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        Location loc = p != null ? p.getLocation() : null;

        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / 1048576L;
        long max = rt.maxMemory() / 1048576L;

        String worldName = world != null ? world.getName() : "-";
        int worldPlayers = world != null ? world.getPlayers().size() : 0;

        return text
                .replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("{max}", String.valueOf(Bukkit.getMaxPlayers()))
                .replace("{player}", p != null ? p.getName() : "")
                .replace("{world-players}", String.valueOf(worldPlayers))
                .replace("{world}", worldName)
                .replace("{x}", loc != null ? String.valueOf(loc.getBlockX()) : "-")
                .replace("{y}", loc != null ? String.valueOf(loc.getBlockY()) : "-")
                .replace("{z}", loc != null ? String.valueOf(loc.getBlockZ()) : "-")
                .replace("{time}", world != null ? ingameTime(world) : "-")
                .replace("{clock}", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")))
                .replace("{ram}", used + "/" + max + " MB");
    }

    /** Minecraft-Zeit: 0 Ticks = 06:00 Uhr. */
    private static String ingameTime(World w) {
        long t = Math.floorMod(w.getTime(), 24000L);
        int hours = (int) ((t / 1000 + 6) % 24);
        int minutes = (int) ((t % 1000) * 60 / 1000);
        return String.format("%02d:%02d", hours, minutes);
    }

    // ------------------------------------------------------------------
    //  Reload und Herunterfahren
    // ------------------------------------------------------------------

    public void reload() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            resetPlayer(p);
        }
        boards.clear();
        loadBossBar();
        for (Player p : Bukkit.getOnlinePlayers()) {
            attach(p);
        }
    }

    public void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            resetPlayer(p);
        }
        boards.clear();
        if (bossBar != null) bossBar.removeAll();
    }

    // ------------------------------------------------------------------
    //  Hilfsmethoden
    // ------------------------------------------------------------------

    private String cfg(String path, String def) {
        return plugin.getConfig().getString(path, def);
    }

    private static <E extends Enum<E>> E enumOr(Class<E> type, String value, E def) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            return def;
        }
    }
}
