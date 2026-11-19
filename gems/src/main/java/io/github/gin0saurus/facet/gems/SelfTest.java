package io.github.gin0saurus.facet.gems;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import io.github.gin0saurus.facet.core.DupeScan;
import io.github.gin0saurus.facet.core.Issues;
import io.github.gin0saurus.facet.core.Ledger;
import io.github.gin0saurus.facet.core.Token;
import io.github.gin0saurus.facet.engine.Theme;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/**
 * Runs inside a real server (CI boots Paper and calls /gem selftest from the console), so it
 * checks the parts the plain unit tests cannot: real ItemStacks, real item tags, the shipped
 * YAML files, potion and material lookups on this exact Minecraft version.
 */
final class SelfTest {

    private SelfTest() {}

    static void run(FacetGems plugin, GemService gems, CommandSender out) {
        List<String> fails = new ArrayList<>();
        int[] passed = {0};
        java.util.function.BiConsumer<Boolean, String> check = (ok, what) -> {
            if (ok) passed[0]++; else fails.add(what);
        };

        // 1. Shipped files parse with zero warnings.
        for (String theme : List.of("elements", "condiments")) {
            YamlConfiguration y = plugin.bundled("themes/" + theme + ".yml");
            Issues is = new Issues();
            YamlConfiguration gy = plugin.bundled("gems.yml");
            java.util.Map<String, GemDef> defs = GemDef.load(gy, plugin.abilities(), is);
            Theme.load(theme, y, defs.keySet(), is);
            check.accept(y != null && is.isEmpty(), "bundled theme '" + theme + "' + gems.yml load cleanly: " + is.warnings());
            check.accept(defs.size() == 8, "bundled gems.yml defines 8 gems, found " + defs.size());
        }
        YamlConfiguration cfg = plugin.bundled("config.yml");
        Issues cfgIssues = new Issues();
        io.github.gin0saurus.facet.core.Tiers.parse(cfg == null ? null : cfg.getMapList("tiers.ladder"), cfgIssues);
        check.accept(cfg != null && cfgIssues.isEmpty(), "bundled config.yml tiers load cleanly: " + cfgIssues.warnings());

        YamlConfiguration lang = plugin.bundled("lang/en.yml");
        for (String key : REQUIRED_LANG) check.accept(lang != null && lang.isString(key), "lang/en.yml has key " + key);

        // 2. Reload is safe at runtime.
        try {
            plugin.reload();
            passed[0]++;
        } catch (RuntimeException e) {
            fails.add("reload threw " + e);
        }

        // 3. Real items: every gem at every tier round-trips through its tags.
        AtomicLong n = new AtomicLong();
        Ledger scratch = new Ledger(() -> new UUID(0x5E1F, n.incrementAndGet()));
        UUID owner = new UUID(0xABC, 1);
        for (String type : gems.defs().keySet()) {
            for (int t = 0; t <= gems.tiers().maxIndex(); t++) {
                Ledger.GemRecord r = scratch.create(type, t, null);
                ItemStack item = gems.gemItem(r);
                check.accept(gems.codec().isGem(item), type + " t" + t + " is recognised as a gem");
                check.accept(r.token().equals(gems.codec().token(item)), type + " t" + t + " token round-trips");
                check.accept(type.equals(gems.codec().type(item)), type + " t" + t + " type tag");
                check.accept(item.getMaxStackSize() == 1, type + " gems do not stack");
            }
            ItemStack a = gems.shardItem(type, 5), b = gems.shardItem(type, 9);
            check.accept(gems.codec().isShard(a) && a.isSimilar(b), type + " shards stack with each other");
        }

        // 4. Dupe logic on real items: clone, then change state, then scan.
        if (!gems.defs().isEmpty()) {
            String type = gems.defs().keySet().iterator().next();
            Ledger.GemRecord r = scratch.create(type, 0, null);
            ItemStack original = gems.gemItem(r);
            ItemStack clone = original.clone();
            scratch.bind(r.id(), owner);
            ItemStack bound = gems.gemItem(r);
            List<DupeScan.Found> found = List.of(
                new DupeScan.Found(owner, "bound", gems.codec().token(bound), 1),
                new DupeScan.Found(owner, "clone", gems.codec().token(clone), 1),
                new DupeScan.Found(owner, "bound-copy", gems.codec().token(bound.clone()), 1));
            List<Object> purged = DupeScan.scan(scratch, found).stream().map(p -> p.found().slot()).toList();
            check.accept(purged.equals(List.of("clone", "bound-copy")), "clones are purged, real gem kept: " + purged);
            Token forged = Token.parse(UUID.randomUUID() + ":1");
            check.accept(scratch.check(forged, owner) == Ledger.Verdict.UNKNOWN, "forged token rejected");
        }

        // 5. Abilities and passives resolved on this version.
        for (GemDef d : gems.defs().values()) {
            check.accept(d.ability() != null, d.id() + " has an ability");
            check.accept(gems.cooldownSeconds(d, gems.tiers().maxIndex()) >= 1, d.id() + " cooldown at top tier >= 1s");
        }

        if (fails.isEmpty()) {
            out.sendMessage("FACET SELFTEST PASS " + passed[0]);
        } else {
            for (String f : fails) out.sendMessage("FACET SELFTEST FAIL " + f);
            out.sendMessage("FACET SELFTEST FAILED " + fails.size() + " of " + (fails.size() + passed[0]));
        }
    }

    static final List<String> REQUIRED_LANG = List.of(
        "prefix", "no-permission", "players-only", "usage",
        "item.tier-line", "item.ability-line", "item.cooldown-line", "item.passive-line", "item.xp-line", "item.no-fall-line",
        "item.bound-line", "item.use-hint", "item.unbound-line", "item.shard-lore",
        "gem.invalid", "gem.already-yours", "gem.unknown-type", "gem.swap-cooldown", "gem.previous-returned", "gem.attuned",
        "gem.attuned-subtitle", "gem.none", "gem.not-carrying", "gem.unbound", "gem.not-yours",
        "upgrade.max", "upgrade.need-shards", "upgrade.need-item", "upgrade.cap-reached", "upgrade.done", "upgrade.broadcast",
        "recover.not-needed", "recover.cooldown", "recover.done",
        "starter.disabled", "starter.used", "starter.has-gem", "starter.given", "starter.prompt", "starter.how",
        "cast.cooldown", "cast.failed", "cast.no-target", "bar.unbound", "bar.cooldown", "bar.ready",
        "protect.no-drop", "protect.no-store", "death.dropped", "pvp.reward", "mining.shard",
        "help.player", "help.admin", "list.header", "list.row", "info.header", "info.ability", "info.cooldown", "info.passive",
        "status.none", "status.gem", "status.cooling", "status.ready", "status.missing", "status.next", "status.cap", "status.max",
        "admin.reloaded", "admin.reloaded-issues", "admin.no-player", "admin.bad-tier", "admin.given", "admin.given-shards",
        "admin.no-gem", "admin.tier-set", "admin.cleared", "admin.inspect-none", "admin.inspect", "admin.debug-header");
}
