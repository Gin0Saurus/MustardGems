package io.github.gin0saurus.facet.gems;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.gin0saurus.facet.core.Issues;
import io.github.gin0saurus.facet.core.Num;
import io.github.gin0saurus.facet.gems.abilities.Ability;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.potion.PotionEffectType;

/** One power type from gems.yml: what it does (ability + passives + numbers), never what it looks like. */
public record GemDef(String id, Ability ability, int cooldownSeconds, List<Passive> passives,
                     boolean noFallDamage, double xpMultiplier, boolean freeEdition, ConfigurationSection params) {

    public record Passive(PotionEffectType type, int amplifier, boolean scales) {}

    public double p(String key, double def) { return params.getDouble(key, def); }
    public int pi(String key, int def) { return params.getInt(key, def); }

    public static Map<String, GemDef> load(ConfigurationSection root, Map<String, Ability> abilities, Issues issues) {
        Map<String, GemDef> out = new LinkedHashMap<>();
        ConfigurationSection gems = root == null ? null : root.getConfigurationSection("gems");
        if (gems == null || gems.getKeys(false).isEmpty()) {
            issues.warn("gems.yml: no gems defined - nothing will work until you add some (or delete the file to restore the defaults)");
            return out;
        }
        for (String rawId : gems.getKeys(false)) {
            String id = rawId.toLowerCase(Locale.ROOT);
            String w = "gems.yml gems." + rawId;
            if (!id.matches("[a-z0-9_]{1,32}")) {
                issues.warn(w + ": id must be lowercase letters, numbers or _ (max 32), skipped");
                continue;
            }
            ConfigurationSection s = gems.getConfigurationSection(rawId);
            if (s == null) { issues.warn(w + ": not a section, skipped"); continue; }
            String abilityId = s.getString("ability", "");
            Ability ability = abilities.get(abilityId);
            if (ability == null) {
                issues.warn(w + ".ability: unknown ability '" + abilityId + "' (known: " + String.join(", ", abilities.keySet()) + "), skipped");
                continue;
            }
            int cd = (int) Num.clamp(s.getDouble("cooldown-seconds", 60), 1, 3600, w + ".cooldown-seconds", issues);
            List<Passive> passives = new ArrayList<>();
            for (Map<?, ?> m : s.getMapList("passives")) {
                String eff = String.valueOf(m.get("effect")).toLowerCase(Locale.ROOT).replace(' ', '_');
                PotionEffectType type = null;
                try { type = Registry.EFFECT.get(NamespacedKey.minecraft(eff)); } catch (IllegalArgumentException ignored) { /* bad key */ }
                if (type == null) { issues.warn(w + ".passives: unknown effect '" + eff + "', skipped"); continue; }
                int amp = (int) Num.clamp(Num.i(m.get("amplifier"), 0), 0, 9, w + ".passives." + eff + ".amplifier", issues);
                boolean scales = !Boolean.FALSE.equals(m.get("scales-with-tier"));
                passives.add(new Passive(type, amp, scales));
            }
            double xp = Num.clamp(s.getDouble("xp-multiplier", 1.0), 1.0, 10.0, w + ".xp-multiplier", issues);
            ConfigurationSection params = s.getConfigurationSection("params");
            out.put(id, new GemDef(id, ability, cd, Collections.unmodifiableList(passives),
                s.getBoolean("no-fall-damage", false), xp, s.getBoolean("free-edition", false),
                params != null ? params : new MemoryConfiguration()));
        }
        return out;
    }
}
