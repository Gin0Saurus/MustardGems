package io.github.gin0saurus.facet.gems;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import io.github.gin0saurus.facet.core.Ledger;
import io.github.gin0saurus.facet.core.Tiers;
import io.github.gin0saurus.facet.core.Token;
import io.github.gin0saurus.facet.engine.Text;
import io.github.gin0saurus.facet.engine.Theme;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** /gem and its subcommands. Player commands need facet.use; admin commands need facet.admin. */
public final class GemCommand implements TabExecutor {

    private static final List<String> PLAYER = List.of("info", "list", "choose", "upgrade", "unbind", "recover", "help");
    private static final List<String> ADMIN = List.of("give", "giveshard", "settier", "clear", "inspect", "reload", "debug", "selftest");

    private final FacetGems plugin;
    private final GemService gems;

    public GemCommand(FacetGems plugin, GemService gems) {
        this.plugin = plugin;
        this.gems = gems;
    }

    private Messages msg() { return gems.msg(); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        if (ADMIN.contains(sub)) {
            if (!sender.hasPermission("facet.admin")) { msg().send(sender, "no-permission"); return true; }
            admin(sender, sub, args);
            return true;
        }
        if (sub.equals("help")) { help(sender); return true; }
        if (sub.equals("list")) { list(sender); return true; }
        if (sub.equals("info")) {
            if (args.length < 2) { msg().send(sender, "usage", "usage", "/gem info <gem>"); return true; }
            info(sender, args[1].toLowerCase(Locale.ROOT));
            return true;
        }
        if (!(sender instanceof Player p)) { msg().send(sender, "players-only"); return true; }
        if (!p.hasPermission("facet.use")) { msg().send(p, "no-permission"); return true; }
        switch (sub) {
            case "status" -> status(p);
            case "choose" -> {
                if (!p.hasPermission("facet.starter")) { msg().send(p, "no-permission"); return true; }
                if (args.length < 2) { list(p); msg().send(p, "starter.how"); return true; }
                gems.chooseStarter(p, args[1].toLowerCase(Locale.ROOT));
            }
            case "upgrade" -> gems.upgrade(p);
            case "unbind" -> gems.unbindSelf(p);
            case "recover" -> gems.recover(p);
            default -> help(p);
        }
        return true;
    }

    private void help(CommandSender s) {
        msg().send(s, "help.player");
        if (s.hasPermission("facet.admin")) msg().send(s, "help.admin");
    }

    private void list(CommandSender s) {
        msg().send(s, "list.header", "theme", gems.theme().displayName());
        for (GemDef d : gems.defs().values()) {
            Theme.Skin skin = gems.theme().skin(d.id());
            msg().send(s, "list.row", "id", d.id(), "gem", gems.gemName(d.id()), "color", skin.tag(), "ability", skin.abilityName());
        }
    }

    private void info(CommandSender s, String id) {
        GemDef d = gems.defs().get(id);
        if (d == null) { msg().send(s, "gem.unknown-type", "type", Text.escape(id)); return; }
        Theme.Skin skin = gems.theme().skin(id);
        msg().send(s, "info.header", "gem", gems.gemName(id));
        msg().send(s, "info.ability", "color", skin.tag(), "ability", skin.abilityName(), "description", skin.abilityDescription());
        msg().send(s, "info.cooldown", "base", String.valueOf(d.cooldownSeconds()),
            "best", String.valueOf(gems.cooldownSeconds(d, gems.tiers().maxIndex())));
        for (GemDef.Passive p : d.passives()) {
            msg().send(s, "info.passive", "effect", Text.title(p.type().getKey().getKey()), "level", String.valueOf(p.amplifier() + 1));
        }
    }

    private void status(Player p) {
        Optional<Ledger.GemRecord> a = gems.active(p);
        if (a.isEmpty()) {
            msg().send(p, "status.none");
            if (gems.settings().allowFreeStarter() && !plugin.data().usedStarter().contains(p.getUniqueId())) msg().send(p, "starter.prompt");
            return;
        }
        Ledger.GemRecord r = a.get();
        Tiers.Tier t = gems.tiers().get(r.tier());
        long cd = gems.cooldowns().remainingSeconds(p.getUniqueId(), GemService.CD_ABILITY);
        msg().send(p, "status.gem", "gem", gems.gemName(r.type()), "tier", gems.theme().tierLabel(t.id()),
            "ready", cd > 0 ? msg().get("status.cooling", "seconds", String.valueOf(cd)) : msg().get("status.ready"));
        if (!gems.holdsValid(p, r)) msg().send(p, "status.missing");
        if (r.tier() < gems.tiers().maxIndex()) {
            Tiers.Tier next = gems.tiers().get(r.tier() + 1);
            String extra = next.upgradeItem().isEmpty() ? "" : " + " + next.upgradeItemAmount() + "x " + Text.title(next.upgradeItem());
            msg().send(p, "status.next", "tier", gems.theme().tierLabel(next.id()),
                "have", String.valueOf(gems.countShards(p, r.type())), "need", String.valueOf(next.upgradeShards()), "extra", extra);
            if (next.capped()) msg().send(p, "status.cap", "left", gems.cap().unlimited() ? "∞" : String.valueOf(gems.cap().remaining()));
        } else {
            msg().send(p, "status.max");
        }
    }

    // ------------------------------------------------------------- admin

    private void admin(CommandSender s, String sub, String[] args) {
        switch (sub) {
            case "reload" -> {
                List<String> issues = plugin.reload();
                msg().send(s, issues.isEmpty() ? "admin.reloaded" : "admin.reloaded-issues", "count", String.valueOf(issues.size()));
                if (!(s instanceof Player)) return;
                for (String w : issues) s.sendMessage(Text.parse("<yellow>• </yellow><gray>" + Text.escape(w)));
            }
            case "debug" -> debug(s);
            case "selftest" -> SelfTest.run(plugin, gems, s);
            case "give" -> {
                if (args.length < 3) { msg().send(s, "usage", "usage", "/gem give <player> <gem> [tier]"); return; }
                Player target = Bukkit.getPlayerExact(args[1]);
                String type = args[2].toLowerCase(Locale.ROOT);
                if (target == null) { msg().send(s, "admin.no-player", "player", Text.escape(args[1])); return; }
                if (!gems.defs().containsKey(type)) { msg().send(s, "gem.unknown-type", "type", Text.escape(type)); return; }
                int tier = args.length >= 4 ? tierArg(args[3]) : 0;
                if (tier < 0) { msg().send(s, "admin.bad-tier", "tiers", tierIds()); return; }
                Ledger.GemRecord r = gems.ledger().create(type, tier, null);
                gems.give(target, gems.gemItem(r));
                plugin.saveSoon();
                msg().send(s, "admin.given", "player", Text.escape(target.getName()), "gem", gems.gemName(type));
            }
            case "giveshard" -> {
                if (args.length < 3) { msg().send(s, "usage", "usage", "/gem giveshard <player> <gem> [amount]"); return; }
                Player target = Bukkit.getPlayerExact(args[1]);
                String type = args[2].toLowerCase(Locale.ROOT);
                if (target == null) { msg().send(s, "admin.no-player", "player", Text.escape(args[1])); return; }
                if (!gems.defs().containsKey(type)) { msg().send(s, "gem.unknown-type", "type", Text.escape(type)); return; }
                int amount = 1;
                if (args.length >= 4) {
                    try { amount = Math.max(1, Math.min(2304, Integer.parseInt(args[3]))); } catch (NumberFormatException e) { msg().send(s, "usage", "usage", "/gem giveshard <player> <gem> [amount]"); return; }
                }
                for (int left = amount; left > 0; left -= 64) gems.give(target, gems.shardItem(type, Math.min(64, left)));
                msg().send(s, "admin.given-shards", "player", Text.escape(target.getName()), "amount", String.valueOf(amount), "gem", gems.gemName(type));
            }
            case "settier" -> {
                if (args.length < 3) { msg().send(s, "usage", "usage", "/gem settier <player> <tier>"); return; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { msg().send(s, "admin.no-player", "player", Text.escape(args[1])); return; }
                int tier = tierArg(args[2]);
                if (tier < 0) { msg().send(s, "admin.bad-tier", "tiers", tierIds()); return; }
                Optional<Ledger.GemRecord> a = gems.active(target);
                if (a.isEmpty()) { msg().send(s, "admin.no-gem", "player", Text.escape(target.getName())); return; }
                gems.ledger().setTier(a.get().id(), tier);
                gems.refresh(target, a.get());
                plugin.saveSoon();
                msg().send(s, "admin.tier-set", "player", Text.escape(target.getName()), "tier", gems.theme().tierLabel(gems.tiers().get(tier).id()));
            }
            case "clear" -> {
                if (args.length < 2) { msg().send(s, "usage", "usage", "/gem clear <player>"); return; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { msg().send(s, "admin.no-player", "player", Text.escape(args[1])); return; }
                Optional<Ledger.GemRecord> cleared = gems.ledger().clearPlayer(target.getUniqueId());
                gems.stripPassives(target);
                gems.sweep(List.of(target));
                plugin.saveSoon();
                msg().send(s, cleared.isPresent() ? "admin.cleared" : "admin.no-gem", "player", Text.escape(target.getName()));
            }
            case "inspect" -> {
                if (!(s instanceof Player p)) { msg().send(s, "players-only"); return; }
                Token t = gems.codec().token(p.getInventory().getItemInMainHand());
                if (t == null) { msg().send(p, "admin.inspect-none"); return; }
                Ledger.Verdict v = gems.ledger().check(t, p.getUniqueId());
                String rec = gems.ledger().get(t.id()).map(Object::toString).orElse("no record");
                msg().send(p, "admin.inspect", "verdict", v.name(), "record", Text.escape(rec));
            }
            default -> help(s);
        }
    }

    private int tierArg(String raw) {
        int i = gems.tiers().indexOf(raw);
        if (i >= 0) return i;
        try {
            int n = Integer.parseInt(raw) - 1;
            return n >= 0 && n <= gems.tiers().maxIndex() ? n : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String tierIds() {
        return gems.tiers().all().stream().map(Tiers.Tier::id).collect(Collectors.joining(", "));
    }

    private void debug(CommandSender s) {
        List<String> lines = new ArrayList<>();
        lines.add("Facet Gems " + plugin.getPluginMeta().getVersion() + " (" + plugin.edition() + ")");
        lines.add("Server: " + Bukkit.getName() + " " + Bukkit.getVersion() + " / API " + Bukkit.getBukkitVersion());
        lines.add("Java: " + System.getProperty("java.version") + " " + System.getProperty("java.vendor"));
        lines.add("Theme: " + gems.theme().id() + ", gems: " + String.join(",", gems.defs().keySet()));
        lines.add("Tiers: " + tierIds() + ", cap left: " + (gems.cap().unlimited() ? "unlimited" : gems.cap().remaining()));
        lines.add("Ledger: " + gems.ledger().liveCount() + " live gems, pack: " + (gems.settings().packEnabled() ? "on" : "off"));
        lines.add("Config issues: " + plugin.lastIssues().size());
        for (String w : plugin.lastIssues()) lines.add("  - " + w);
        StringBuilder other = new StringBuilder();
        for (Plugin pl : Bukkit.getPluginManager().getPlugins()) {
            if (pl == plugin) continue;
            if (other.length() > 0) other.append(", ");
            other.append(pl.getName()).append(' ').append(pl.getPluginMeta().getVersion());
        }
        lines.add("Other plugins: " + other);
        msg().send(s, "admin.debug-header");
        for (String l : lines) {
            s.sendMessage(l);
            plugin.getLogger().info("[debug] " + l);
        }
    }

    // ------------------------------------------------------------- tab completion

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(PLAYER);
            if (sender.hasPermission("facet.admin")) options.addAll(ADMIN);
        } else {
            String sub = args[0].toLowerCase(Locale.ROOT);
            boolean admin = sender.hasPermission("facet.admin");
            if (args.length == 2 && (sub.equals("info") || sub.equals("choose"))) options.addAll(gems.defs().keySet());
            else if (args.length == 2 && admin && List.of("give", "giveshard", "settier", "clear").contains(sub)) {
                for (Player p : Bukkit.getOnlinePlayers()) options.add(p.getName());
            } else if (args.length == 3 && admin && (sub.equals("give") || sub.equals("giveshard"))) options.addAll(gems.defs().keySet());
            else if (args.length == 3 && admin && sub.equals("settier")) for (Tiers.Tier t : gems.tiers().all()) options.add(t.id());
            else if (args.length == 4 && admin && sub.equals("give")) for (Tiers.Tier t : gems.tiers().all()) options.add(t.id());
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(last)).collect(Collectors.toList());
    }
}
