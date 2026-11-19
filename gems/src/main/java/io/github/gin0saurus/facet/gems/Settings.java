package io.github.gin0saurus.facet.gems;

import io.github.gin0saurus.facet.core.Issues;
import io.github.gin0saurus.facet.core.Num;
import org.bukkit.configuration.file.FileConfiguration;

/** Every behaviour switch from config.yml, read once per load/reload and clamped. */
public record Settings(
    String theme,
    boolean allowFreeStarter,
    long swapCooldownMillis,
    long recoverCooldownMillis,
    boolean preventDropping,
    boolean preventStoring,
    boolean dropOnDeath,
    double miningShardChance,
    int shardsPerKill,
    long shardFarmWindowMillis,
    int serverCap,
    boolean affectPlayers,
    boolean affectMobs,
    boolean particles,
    double particleScale,
    boolean ambientParticles,
    boolean actionBar,
    boolean packEnabled,
    String packUrl,
    String packSha1,
    String packPrompt,
    boolean packForce,
    int autosaveMinutes,
    int sweepSeconds) {

    public static Settings read(FileConfiguration c, Issues issues) {
        String sha1 = c.getString("resource-pack.sha1", "").trim().toLowerCase();
        String url = c.getString("resource-pack.url", "").trim();
        boolean pack = c.getBoolean("resource-pack.enabled", false);
        if (pack && (url.isEmpty() || !sha1.matches("[0-9a-f]{40}"))) {
            issues.warn("config.yml resource-pack: enabled but url is empty or sha1 is not a 40-character hash - pack disabled. See docs/RESOURCE-PACK.md");
            pack = false;
        }
        return new Settings(
            c.getString("theme", "elements").toLowerCase().trim(),
            c.getBoolean("gems.allow-free-starter", true),
            (long) (Num.clamp(c.getDouble("gems.swap-cooldown-seconds", 30), 0, 86_400, "gems.swap-cooldown-seconds", issues) * 1000),
            (long) (Num.clamp(c.getDouble("gems.recover-cooldown-minutes", 10), 0, 1440, "gems.recover-cooldown-minutes", issues) * 60_000),
            c.getBoolean("gems.prevent-dropping", true),
            c.getBoolean("gems.prevent-storing", true),
            c.getBoolean("gems.drop-on-death", true),
            Num.clamp(c.getDouble("shards.mining-drop-chance", 0.04), 0, 1, "shards.mining-drop-chance", issues),
            (int) Num.clamp(c.getInt("pvp.shards-per-kill", 2), 0, 64, "pvp.shards-per-kill", issues),
            (long) (Num.clamp(c.getDouble("pvp.shard-farm-cooldown-minutes", 10), 0, 1440, "pvp.shard-farm-cooldown-minutes", issues) * 60_000),
            c.getInt("tiers.server-cap", 20),
            c.getBoolean("abilities.affect-players", true),
            c.getBoolean("abilities.affect-mobs", true),
            c.getBoolean("effects.particles", true),
            Num.clamp(c.getDouble("effects.particle-scale", 1.0), 0, 2, "effects.particle-scale", issues),
            c.getBoolean("effects.ambient-particles", true),
            c.getBoolean("effects.action-bar", true),
            pack, url, sha1,
            c.getString("resource-pack.prompt", "Gem textures - purely cosmetic."),
            c.getBoolean("resource-pack.force", false),
            (int) Num.clamp(c.getInt("storage.autosave-minutes", 5), 1, 120, "storage.autosave-minutes", issues),
            (int) Num.clamp(c.getInt("storage.integrity-check-seconds", 10), 2, 300, "storage.integrity-check-seconds", issues));
    }
}
