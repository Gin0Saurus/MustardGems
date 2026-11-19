package io.github.gin0saurus.facet.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.gin0saurus.facet.core.Issues;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Everything players SEE: names, colours, lore, materials, model keys, tier labels.
 * Nothing in here changes how the game plays, so a theme can turn the same engine into a
 * completely different concept without touching Java.
 */
public final class Theme {

    public record Skin(String id, String name, String color, Material material, String model,
                       String abilityName, String abilityDescription, List<String> lore,
                       String castSound, Color particleColor) {
        /** "<#E0115F>" style opening tag for this skin's colour. */
        public String tag() { return "<" + color + ">"; }
    }

    private final String id;
    private final String displayName;
    private final String gemName;
    private final String shardName;
    private final Material shardMaterial;
    private final String modelNamespace;
    private final Map<String, String> tierLabels;
    private final Map<String, Skin> skins;

    private Theme(String id, String displayName, String gemName, String shardName, Material shardMaterial,
                  String modelNamespace, Map<String, String> tierLabels, Map<String, Skin> skins) {
        this.id = id;
        this.displayName = displayName;
        this.gemName = gemName;
        this.shardName = shardName;
        this.shardMaterial = shardMaterial;
        this.modelNamespace = modelNamespace;
        this.tierLabels = tierLabels;
        this.skins = skins;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public String modelNamespace() { return modelNamespace; }
    public Material shardMaterial() { return shardMaterial; }

    /** Item name format for a gem, e.g. "{color}{name} Gem". */
    public String gemNameFormat() { return gemName; }
    public String shardNameFormat() { return shardName; }

    public String tierLabel(String tierId) {
        String s = tierLabels.get(tierId);
        return s != null ? s : "<gray>" + Text.title(tierId);
    }

    public Skin skin(String gemId) {
        Skin s = skins.get(gemId);
        return s != null ? s : fallback(gemId);
    }

    public Collection<Skin> skins() { return skins.values(); }

    private static Skin fallback(String gemId) {
        return new Skin(gemId, Text.title(gemId), "#FFFFFF", Material.AMETHYST_SHARD, gemId + "_gem",
            Text.title(gemId) + " Power", "", List.of(), "", Color.WHITE);
    }

    /**
     * @param gemIds the power types the concept defines; every one gets a skin, falling back to
     *               a plain default (with a warning) if the theme forgot it.
     */
    public static Theme load(String id, ConfigurationSection root, Collection<String> gemIds, Issues issues) {
        String where = "themes/" + id + ".yml";
        if (root == null) {
            issues.warn(where + ": file missing or empty, using plain names");
            root = new org.bukkit.configuration.MemoryConfiguration();
        }
        String display = root.getString("name", Text.title(id));
        String gemName = root.getString("item-names.gem", "{color}{name}");
        String shardName = root.getString("item-names.shard", "{color}{name} Shard");
        Material shardMat = material(root.getString("shard-material", "PRISMARINE_SHARD"), Material.PRISMARINE_SHARD, where + ".shard-material", issues);
        String ns = root.getString("model-namespace", "facet").toLowerCase();
        if (!ns.matches("[a-z0-9_.-]+")) {
            issues.warn(where + ".model-namespace: '" + ns + "' is not a valid namespace, using 'facet'");
            ns = "facet";
        }

        Map<String, String> tiers = new LinkedHashMap<>();
        ConfigurationSection ts = root.getConfigurationSection("tiers");
        if (ts != null) for (String k : ts.getKeys(false)) tiers.put(k.toLowerCase(), ts.getString(k, k));

        Map<String, Skin> skins = new LinkedHashMap<>();
        ConfigurationSection gs = root.getConfigurationSection("gems");
        for (String gem : gemIds) {
            ConfigurationSection s = gs == null ? null : gs.getConfigurationSection(gem);
            if (s == null) {
                issues.warn(where + ": no skin for gem '" + gem + "', using a plain default");
                skins.put(gem, fallback(gem));
                continue;
            }
            String w = where + ".gems." + gem;
            String color = s.getString("color", "#FFFFFF").trim();
            if (!color.matches("#[0-9A-Fa-f]{6}")) {
                issues.warn(w + ".color: '" + color + "' is not #RRGGBB, using white");
                color = "#FFFFFF";
            }
            Color particle = Color.fromRGB(Integer.parseInt(color.substring(1), 16));
            skins.put(gem, new Skin(gem,
                s.getString("name", Text.title(gem)),
                color,
                material(s.getString("material", "AMETHYST_SHARD"), Material.AMETHYST_SHARD, w + ".material", issues),
                s.getString("model", gem + "_gem").toLowerCase(),
                s.getString("ability-name", Text.title(gem) + " Power"),
                s.getString("ability-description", ""),
                new ArrayList<>(s.getStringList("lore")),
                s.getString("cast-sound", ""),
                particle));
        }
        if (gs != null) {
            for (String extra : gs.getKeys(false)) {
                if (!gemIds.contains(extra)) issues.warn(where + ".gems." + extra + ": no gem with this id exists in gems.yml, ignored");
            }
        }
        return new Theme(id, display, gemName, shardName, shardMat, ns, tiers, skins);
    }

    private static Material material(String raw, Material def, String where, Issues issues) {
        Material m = raw == null ? null : Material.matchMaterial(raw);
        if (m == null || !m.isItem() || m.isAir()) {
            issues.warn(where + ": '" + raw + "' is not an item, using " + def.name());
            return def;
        }
        return m;
    }
}
