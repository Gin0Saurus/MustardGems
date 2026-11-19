package io.github.gin0saurus.facet.gems;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import io.github.gin0saurus.facet.core.Cooldowns;
import io.github.gin0saurus.facet.core.Issues;
import io.github.gin0saurus.facet.core.Ledger;
import io.github.gin0saurus.facet.core.PairLimiter;
import io.github.gin0saurus.facet.core.Tiers;
import io.github.gin0saurus.facet.engine.DataStore;
import io.github.gin0saurus.facet.engine.Fx;
import io.github.gin0saurus.facet.engine.ItemCodec;
import io.github.gin0saurus.facet.engine.Targets;
import io.github.gin0saurus.facet.engine.Theme;
import io.github.gin0saurus.facet.gems.abilities.Abilities;
import io.github.gin0saurus.facet.gems.abilities.Ability;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Plugin entry point: loads files, wires the pieces together, runs the timers. */
public final class FacetGems extends JavaPlugin {

    private final Map<String, Ability> abilities = Abilities.all();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private Ledger ledger;
    private Cooldowns cooldowns;
    private DataStore data;
    private GemService gems;
    private Messages messages;
    private List<String> lastIssues = List.of();
    private String edition = "full";
    private BukkitTask pendingSave;

    @Override
    public void onEnable() {
        edition = readEdition();
        saveDefaultConfig();
        for (String f : List.of("gems.yml", "themes/elements.yml", "themes/condiments.yml", "lang/en.yml")) {
            if (!new File(getDataFolder(), f).exists()) saveResource(f, false);
        }
        ledger = new Ledger();
        cooldowns = new Cooldowns(System::currentTimeMillis);
        data = new DataStore(getDataFolder(), getLogger(), ledger, cooldowns);
        data.load();
        messages = new Messages();
        gems = new GemService(this, new ItemCodec(this), ledger, cooldowns, data, new Fx(), new Targets());
        reload();
        gems.syncCapFromData();

        getServer().getPluginManager().registerEvents(new ProtectionListener(gems), this);
        getServer().getPluginManager().registerEvents(new GemListener(gems, new PairLimiter(System::currentTimeMillis)), this);
        GemCommand cmd = new GemCommand(this, gems);
        PluginCommand pc = getCommand("gem");
        if (pc != null) { pc.setExecutor(cmd); pc.setTabCompleter(cmd); }

        getLogger().info("Facet Gems " + getPluginMeta().getVersion() + " (" + edition + " edition) enabled: "
            + gems.defs().size() + " gems, theme '" + gems.theme().id() + "', " + ledger.liveCount() + " gems on record.");
    }

    @Override
    public void onDisable() {
        for (BukkitTask t : tasks) t.cancel();
        tasks.clear();
        if (data != null) data.write(data.snapshot()); // synchronous: the server is stopping
    }

    /** Re-reads every file. Safe to call at any time; bad values are clamped and reported, never fatal. */
    public List<String> reload() {
        reloadConfig();
        Issues issues = new Issues();
        messages.load(this);
        YamlConfiguration gemsYml = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "gems.yml"));
        Map<String, GemDef> allDefs = GemDef.load(gemsYml, abilities, issues);
        Map<String, GemDef> defs = allDefs;
        if (isFree()) {
            defs = new LinkedHashMap<>();
            for (GemDef d : allDefs.values()) if (d.freeEdition()) defs.put(d.id(), d);
        }
        Tiers tiers = Tiers.parse(getConfig().getMapList("tiers.ladder"), issues);
        Settings settings = Settings.read(getConfig(), issues);
        Theme theme = loadTheme(settings.theme(), allDefs.keySet(), issues);
        gems.apply(defs, tiers, theme, settings, messages);
        restartTasks(settings);
        lastIssues = issues.warnings();
        for (String w : lastIssues) getLogger().warning("Config: " + w);
        return lastIssues;
    }

    private Theme loadTheme(String id, java.util.Collection<String> gemIds, Issues issues) {
        File f = new File(getDataFolder(), "themes/" + id + ".yml");
        if (!f.exists()) {
            issues.warn("config.yml theme: '" + id + "' not found in themes/, using 'elements'");
            id = "elements";
            f = new File(getDataFolder(), "themes/elements.yml");
        }
        return Theme.load(id, YamlConfiguration.loadConfiguration(f), gemIds, issues);
    }

    private void restartTasks(Settings s) {
        for (BukkitTask t : tasks) t.cancel();
        tasks.clear();
        tasks.add(getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player p : getServer().getOnlinePlayers()) gems.passiveTick(p);
        }, 40L, 40L));
        tasks.add(getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player p : getServer().getOnlinePlayers()) gems.actionBar(p);
        }, 10L, 10L));
        tasks.add(getServer().getScheduler().runTaskTimer(this, () -> {
            if (!getServer().getOnlinePlayers().isEmpty()) gems.sweep(new ArrayList<>(getServer().getOnlinePlayers()));
        }, s.sweepSeconds() * 20L, s.sweepSeconds() * 20L));
        long save = s.autosaveMinutes() * 60L * 20L;
        tasks.add(getServer().getScheduler().runTaskTimer(this, () -> { if (ledger.dirty()) saveAsync(); }, save, save));
        tasks.add(getServer().getScheduler().runTaskTimer(this, cooldowns::prune, 72_000L, 72_000L));
    }

    /** Saves a couple of seconds from now, merging bursts of changes into one write. */
    public void saveSoon() {
        if (pendingSave != null) return;
        pendingSave = getServer().getScheduler().runTaskLater(this, () -> { pendingSave = null; saveAsync(); }, 40L);
    }

    private void saveAsync() {
        String snapshot = data.snapshot();
        getServer().getScheduler().runTaskAsynchronously(this, () -> data.write(snapshot));
    }

    public void sendPack(Player p) {
        Settings s = gems.settings();
        if (!s.packEnabled()) return;
        try {
            byte[] hash = HexFormat.of().parseHex(s.packSha1());
            UUID id = UUID.nameUUIDFromBytes(("facet-pack:" + s.packUrl()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            p.addResourcePack(id, s.packUrl(), hash, s.packPrompt(), s.packForce());
        } catch (IllegalArgumentException e) {
            getLogger().warning("resource-pack.sha1 is not valid hex; pack not sent");
        }
    }

    private String readEdition() {
        try (InputStream in = getResource("edition.properties")) {
            if (in == null) return "full";
            Properties p = new Properties();
            p.load(in);
            return p.getProperty("edition", "full").trim().toLowerCase();
        } catch (IOException e) {
            return "full";
        }
    }

    public boolean isFree() { return "free".equals(edition); }
    public String edition() { return edition; }
    public DataStore data() { return data; }
    public List<String> lastIssues() { return lastIssues; }
    public Map<String, Ability> abilities() { return abilities; }

    /** Used by the self-test to check the files shipped inside the jar, independent of the server's copies. */
    public YamlConfiguration bundled(String path) {
        InputStream in = getResource(path);
        if (in == null) return null;
        return YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
    }

    public static boolean isServerThread() { return Bukkit.isPrimaryThread(); }
}
