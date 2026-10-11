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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Baut pro Spieler das Scoreboard (Sidebar und Tab-Präfixe) und hält die gemeinsame Bossbar aktuell. */
public class StatsManager {

    private static final int MAX_LINES = 15;

    private record TabGroup(String permission, String prefix) { }

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
    //  Spieler, Sidebar und Tab
    // ------------------------------------------------------------------

    /** Gibt einem Spieler Scoreboard (Sidebar und Tab) und Bossbar. */
    public void attach(Player p) {
        rebuild(p);
        if (plugin.getConfig().getBoolean("bossbar.enabled", true) && !bossbarOff.contains(p.getUniqueId())) {
            bossBar.addPlayer(p);
        }
    }

    public void detach(Player p) {
        boards.remove(p.getUniqueId());
        bossBar.removePlayer(p);
    }

    private boolean sidebarActive(Player p) {
        return plugin.getConfig().getBoolean("sidebar.enabled", true) && !sidebarOff.contains(p.getUniqueId());
    }

    private boolean tabEnabled() {
        return plugin.getConfig().getBoolean("tab.enabled", true);
    }

    /** Baut das Scoreboard des Spielers neu, je nachdem ob Sidebar und Tab aktiv sind. */
    private void rebuild(Player p) {
        boolean sidebar = sidebarActive(p);
        if (!sidebar && !tabEnabled()) {
            boards.remove(p.getUniqueId());
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            return;
        }
        Scoreboard board = buildBoard(sidebar);
        boards.put(p.getUniqueId(), board);
        p.setScoreboard(board);
    }

    private void resetPlayer(Player p) {
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        p.setPlayerListHeader("");
        p.setPlayerListFooter("");
        bossBar.removePlayer(p);
    }

    public boolean toggleSidebar(Player p) {
        UUID id = p.getUniqueId();
        if (sidebarOff.remove(id)) {
            rebuild(p);
            return true;
        }
        sidebarOff.add(id);
        rebuild(p);
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

    private Scoreboard buildBoard(boolean sidebar) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();

        if (sidebar) {
            Objective obj = board.registerNewObjective("falixstats", "dummy",
                    plugin.color(cfg("sidebar.title", "&bFalixStats")));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);

            int lines = Math.min(MAX_LINES, plugin.getConfig().getStringList("sidebar.lines").size());
            for (int i = 0; i < lines; i++) {
                Team team = board.registerNewTeam("fs" + i);
                String entry = entry(i);
                team.addEntry(entry);
                obj.getScore(entry).setScore(lines - i);
            }
        }

        if (tabEnabled()) {
            List<TabGroup> groups = groups();
            for (int i = 0; i < groups.size(); i++) {
                Team team = board.registerNewTeam(teamName(i));
                team.setPrefix(plugin.color(groups.get(i).prefix()));
            }
        }
        return board;
    }

    /** Unsichtbarer, eindeutiger Name pro Sidebar-Zeile (nur Reset-Codes). */
    private static String entry(int i) {
        return org.bukkit.ChatColor.RESET.toString().repeat(i + 1);
    }

    /** Teamname pro Rang. Die Nummer sorgt dafür, dass die Tab-Liste in der Config-Reihenfolge sortiert. */
    private static String teamName(int i) {
        return "tab" + String.format("%02d", i);
    }

    private List<TabGroup> groups() {
        List<TabGroup> out = new ArrayList<>();
        for (Map<?, ?> m : plugin.getConfig().getMapList("tab.groups")) {
            Object perm = m.get("permission");
            Object prefix = m.get("prefix");
            out.add(new TabGroup(perm == null ? "" : perm.toString(), prefix == null ? "" : prefix.toString()));
        }
        return out;
    }

    /** Index der ersten passenden Gruppe, -1 wenn keine passt. */
    private static int groupIndex(List<TabGroup> groups, Player t) {
        for (int i = 0; i < groups.size(); i++) {
            String perm = groups.get(i).permission();
            if (perm.isBlank() || t.hasPermission(perm)) return i;
        }
        return -1;
    }

    // ------------------------------------------------------------------
    //  Aktualisierung
    // ------------------------------------------------------------------

    /** Läuft periodisch: Sidebar, Tab und Bossbar aktualisieren. */
    public void tick() {
        List<String> lines = plugin.getConfig().getStringList("sidebar.lines");
        int count = Math.min(MAX_LINES, lines.size());
        List<TabGroup> groups = groups();
        boolean tab = tabEnabled();

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Scoreboard board = boards.get(viewer.getUniqueId());

            if (board != null && sidebarActive(viewer)) {
                for (int i = 0; i < count; i++) {
                    Team team = board.getTeam("fs" + i);
                    if (team != null) {
                        team.setPrefix(limit(plugin.color(replace(lines.get(i), viewer))));
                    }
                }
            }

            if (tab) {
                if (board != null) updateTabTeams(board, groups);
                viewer.setPlayerListHeader(plugin.color(replace(joinLines("tab.header"), viewer)));
                viewer.setPlayerListFooter(plugin.color(replace(joinLines("tab.footer"), viewer)));
            }
        }

        if (plugin.getConfig().getBoolean("bossbar.enabled", true)) {
            bossBar.setTitle(plugin.color(replace(cfg("bossbar.text", ""), null)));
            bossBar.setProgress(progress());
        }
    }

    /** Ordnet jeden online Spieler dem passenden Rang-Team auf diesem Scoreboard zu. */
    private void updateTabTeams(Scoreboard board, List<TabGroup> groups) {
        for (Player target : Bukkit.getOnlinePlayers()) {
            String name = target.getName();
            int want = groupIndex(groups, target);
            for (int i = 0; i < groups.size(); i++) {
                Team team = board.getTeam(teamName(i));
                if (team == null) continue;
                if (i == want) {
                    if (!team.hasEntry(name)) team.addEntry(name);
                } else if (team.hasEntry(name)) {
                    team.removeEntry(name);
                }
            }
        }
    }

    /** Schneidet eine Zeile auf max-length sichtbare Zeichen ab. Farbcodes zählen nicht mit. */
    private String limit(String s) {
        int max = plugin.getConfig().getInt("sidebar.max-length", 30);
        StringBuilder out = new StringBuilder();
        int visible = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00a7' && i + 1 < s.length()) {
                out.append(c).append(s.charAt(i + 1));
                i++;
                continue;
            }
            if (visible >= max) break;
            out.append(c);
            visible++;
        }
        return out.toString();
    }

    private String joinLines(String path) {
        return String.join("\n", plugin.getConfig().getStringList(path));
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
        World world = p != null ? p.getWorld()
                : (Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0));
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
