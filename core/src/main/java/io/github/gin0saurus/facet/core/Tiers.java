package io.github.gin0saurus.facet.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The tier ladder, read from config. Every value is clamped to a safe range and every problem
 * is reported instead of thrown, so a typo can never stop the plugin from loading.
 */
public final class Tiers {

    public record Tier(String id, double magnitude, double cooldownMultiplier, int amplifierBonus,
                       int upgradeShards, String upgradeItem, int upgradeItemAmount, boolean capped) {}

    private final List<Tier> tiers;

    private Tiers(List<Tier> tiers) { this.tiers = Collections.unmodifiableList(tiers); }

    public int size() { return tiers.size(); }
    public int maxIndex() { return tiers.size() - 1; }
    public List<Tier> all() { return tiers; }

    public Tier get(int index) { return tiers.get(Math.max(0, Math.min(index, maxIndex()))); }

    public int indexOf(String id) {
        for (int i = 0; i < tiers.size(); i++) if (tiers.get(i).id().equalsIgnoreCase(id)) return i;
        return -1;
    }

    public static Tiers defaults() {
        List<Tier> d = new ArrayList<>();
        d.add(new Tier("rough", 1.0, 1.0, 0, 0, "", 0, false));
        d.add(new Tier("cut", 1.3, 0.85, 0, 8, "", 0, false));
        d.add(new Tier("flawless", 1.7, 0.7, 1, 16, "diamond_block", 1, false));
        d.add(new Tier("mythic", 2.4, 0.55, 1, 32, "nether_star", 1, true));
        return new Tiers(d);
    }

    /**
     * @param rows ordered list of maps, one per tier (lowest first)
     * @param issues receives human-readable warnings
     */
    public static Tiers parse(List<? extends Map<?, ?>> rows, Issues issues) {
        if (rows == null || rows.isEmpty()) {
            issues.warn("tiers: none configured, using the 4 default tiers");
            return defaults();
        }
        List<Tier> out = new ArrayList<>();
        int i = 0;
        for (Map<?, ?> m : rows) {
            String where = "tiers[" + i + "]";
            String id = str(m.get("id"), "tier" + i).toLowerCase().replaceAll("[^a-z0-9_]", "_");
            for (Tier t : out) if (t.id().equals(id)) { issues.warn(where + ": duplicate id '" + id + "', renamed"); id = id + "_" + i; }
            double mag = Num.clamp(Num.d(m.get("magnitude"), 1.0), 0.1, 10.0, where + ".magnitude", issues);
            double cdm = Num.clamp(Num.d(m.get("cooldown-multiplier"), 1.0), 0.05, 5.0, where + ".cooldown-multiplier", issues);
            int amp = (int) Num.clamp(Num.i(m.get("amplifier-bonus"), 0), 0, 5, where + ".amplifier-bonus", issues);
            int shards = (int) Num.clamp(Num.i(m.get("upgrade-shards"), 0), 0, 64 * 36, where + ".upgrade-shards", issues);
            String item = str(m.get("upgrade-item"), "").toLowerCase();
            int itemAmount = (int) Num.clamp(Num.i(m.get("upgrade-item-amount"), item.isEmpty() ? 0 : 1), 0, 64, where + ".upgrade-item-amount", issues);
            boolean capped = Boolean.TRUE.equals(m.get("server-capped"));
            if (i == 0 && (shards > 0 || !item.isEmpty())) {
                issues.warn(where + ": the first tier is the starting tier, its upgrade cost is ignored");
                shards = 0; item = ""; itemAmount = 0; capped = false;
            }
            out.add(new Tier(id, mag, cdm, amp, shards, item, itemAmount, capped));
            i++;
        }
        if (out.size() > 10) {
            issues.warn("tiers: more than 10 tiers configured, only the first 10 are used");
            return new Tiers(new ArrayList<>(out.subList(0, 10)));
        }
        return new Tiers(out);
    }

    private static String str(Object o, String def) { return o == null ? def : String.valueOf(o).trim(); }
}
