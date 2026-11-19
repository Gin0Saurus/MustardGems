package io.github.gin0saurus.facet.engine;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.github.gin0saurus.facet.core.Cooldowns;
import io.github.gin0saurus.facet.core.Ledger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Saves the ledger, cooldowns and server-wide counters to data.yml.
 *
 * <p>The snapshot is taken on the main thread (so it is consistent) and written on another
 * thread through a temp file + atomic move, so a crash mid-save can never leave a half file.
 * The previous file is kept as data.yml.bak.
 */
public final class DataStore {

    private static final int SCHEMA = 1;

    private final File file;
    private final Logger log;
    private final Ledger ledger;
    private final Cooldowns cooldowns;
    private final Set<UUID> usedStarter = new HashSet<>();
    private int capUsed;
    private final Object writeLock = new Object();

    public DataStore(File dataFolder, Logger log, Ledger ledger, Cooldowns cooldowns) {
        this.file = new File(dataFolder, "data.yml");
        this.log = log;
        this.ledger = ledger;
        this.cooldowns = cooldowns;
    }

    public Set<UUID> usedStarter() { return usedStarter; }
    public int capUsed() { return capUsed; }
    public void capUsed(int v) { capUsed = Math.max(0, v); }

    public void load() {
        if (!file.exists()) return;
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            File broken = new File(file.getParentFile(), "data.yml.broken-" + System.currentTimeMillis());
            log.severe("data.yml could not be read (" + e.getMessage().lines().findFirst().orElse("?") + "). It was copied to "
                + broken.getName() + " and the plugin is starting from data.yml.bak if it exists.");
            try { Files.copy(file.toPath(), broken.toPath()); } catch (IOException ignored) { /* best effort */ }
            File bak = new File(file.getParentFile(), "data.yml.bak");
            if (!bak.exists()) return;
            try { y.load(bak); } catch (IOException | InvalidConfigurationException e2) {
                log.severe("data.yml.bak is also unreadable (" + e2.getMessage().lines().findFirst().orElse("?") + "); starting with empty data.");
                return;
            }
        }
        List<Map<?, ?>> rows = y.getMapList("gems");
        for (String p : ledger.restore(rows)) log.warning("data.yml " + p);
        ConfigurationSection cds = y.getConfigurationSection("cooldowns");
        cooldowns.restore(cds == null ? null : toNested(cds));
        usedStarter.clear();
        for (String s : y.getStringList("used-starter")) {
            try { usedStarter.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) { /* skip */ }
        }
        capUsed = Math.max(0, y.getInt("server-cap-used", 0));
    }

    private static Map<String, Object> toNested(ConfigurationSection s) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        for (String k : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(k);
            out.put(k, c == null ? s.get(k) : c.getValues(false));
        }
        return out;
    }

    /** Builds the file contents now (main thread). */
    public String snapshot() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of(
            "Facet data - written by the plugin. Do not edit while the server is running.",
            "Back up this file with your world: it is the record of who owns which gem."));
        y.set("schema", SCHEMA);
        y.set("gems", ledger.snapshot());
        y.set("cooldowns", cooldowns.snapshot());
        List<String> starters = new ArrayList<>();
        for (UUID u : usedStarter) starters.add(u.toString());
        y.set("used-starter", starters);
        y.set("server-cap-used", capUsed);
        ledger.markClean();
        return y.saveToString();
    }

    /** Writes contents produced by {@link #snapshot()}. Safe to call off the main thread. */
    public void write(String contents) {
        synchronized (writeLock) {
            try {
                Path dir = file.getParentFile().toPath();
                Files.createDirectories(dir);
                Path tmp = dir.resolve("data.yml.tmp");
                Files.writeString(tmp, contents, StandardCharsets.UTF_8);
                if (file.exists()) Files.copy(file.toPath(), dir.resolve("data.yml.bak"), StandardCopyOption.REPLACE_EXISTING);
                try {
                    Files.move(tmp, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                log.log(Level.SEVERE, "Could not save data.yml - gem ownership changes since the last save may be lost if the server stops now.", e);
            }
        }
    }
}
