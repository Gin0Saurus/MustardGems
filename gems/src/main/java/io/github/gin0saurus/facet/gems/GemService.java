package io.github.gin0saurus.facet.gems;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import io.github.gin0saurus.facet.core.Cooldowns;
import io.github.gin0saurus.facet.core.DupeScan;
import io.github.gin0saurus.facet.core.GlobalCap;
import io.github.gin0saurus.facet.core.Ledger;
import io.github.gin0saurus.facet.core.Tiers;
import io.github.gin0saurus.facet.core.Token;
import io.github.gin0saurus.facet.engine.DataStore;
import io.github.gin0saurus.facet.engine.Fx;
import io.github.gin0saurus.facet.engine.ItemCodec;
import io.github.gin0saurus.facet.engine.Targets;
import io.github.gin0saurus.facet.engine.Text;
import io.github.gin0saurus.facet.engine.Theme;
import io.github.gin0saurus.facet.gems.abilities.CastContext;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * All gem gameplay rules in one place. Listeners and commands only translate Minecraft events
 * into calls here, which keeps every rule (ownership, costs, cooldowns) in a single spot.
 */
public final class GemService {

    public static final String CD_ABILITY = "ability";
    public static final String CD_SWAP = "swap";
    public static final String CD_RECOVER = "recover";

    private final FacetGems plugin;
    private final ItemCodec codec;
    private final Ledger ledger;
    private final Cooldowns cooldowns;
    private final DataStore data;
    private final Fx fx;
    private final Targets targets;
    private final GlobalCap cap = new GlobalCap(20, 0);
    private final Map<UUID, Set<PotionEffectType>> applied = new HashMap<>();

    private Map<String, GemDef> defs = Map.of();
    private Tiers tiers = Tiers.defaults();
    private Theme theme;
    private Settings settings;
    private Messages msg;

    public GemService(FacetGems plugin, ItemCodec codec, Ledger ledger, Cooldowns cooldowns, DataStore data, Fx fx, Targets targets) {
        this.plugin = plugin;
        this.codec = codec;
        this.ledger = ledger;
        this.cooldowns = cooldowns;
        this.data = data;
        this.fx = fx;
        this.targets = targets;
    }

    public void apply(Map<String, GemDef> defs, Tiers tiers, Theme theme, Settings settings, Messages msg) {
        this.defs = defs;
        this.tiers = tiers;
        this.theme = theme;
        this.settings = settings;
        this.msg = msg;
        cap.setCap(settings.serverCap());
        fx.configure(settings.particles(), settings.particleScale());
        targets.configure(settings.affectPlayers(), settings.affectMobs());
    }

    public void syncCapFromData() { while (cap.used() < data.capUsed()) cap.tryTake(); }

    public FacetGems plugin() { return plugin; }
    public Map<String, GemDef> defs() { return defs; }
    public Tiers tiers() { return tiers; }
    public Theme theme() { return theme; }
    public Settings settings() { return settings; }
    public Ledger ledger() { return ledger; }
    public ItemCodec codec() { return codec; }
    public Cooldowns cooldowns() { return cooldowns; }
    public GlobalCap cap() { return cap; }
    public Messages msg() { return msg; }

    // ================================================================= items

    private NamespacedKey model(String path) {
        if (!settings.packEnabled()) return null;
        return new NamespacedKey(theme.modelNamespace(), path);
    }

    public String gemName(String type) {
        Theme.Skin s = theme.skin(type);
        return Text.fill(theme.gemNameFormat(), Map.of("color", s.tag(), "name", s.name()));
    }

    public int cooldownSeconds(GemDef def, int tierIndex) {
        return Math.max(1, (int) Math.round(def.cooldownSeconds() * tiers.get(tierIndex).cooldownMultiplier()));
    }

    public ItemStack gemItem(Ledger.GemRecord r) {
        Theme.Skin s = theme.skin(r.type());
        GemDef def = defs.get(r.type());
        Tiers.Tier t = tiers.get(r.tier());
        List<String> lore = new ArrayList<>();
        lore.add(Text.fill(msg.raw("item.tier-line"), Map.of("tier", theme.tierLabel(t.id()))));
        lore.add("");
        lore.add(Text.fill(msg.raw("item.ability-line"), Map.of("color", s.tag(), "ability", s.abilityName())));
        if (!s.abilityDescription().isBlank()) lore.add("<gray>" + s.abilityDescription());
        if (def != null) {
            lore.add(Text.fill(msg.raw("item.cooldown-line"), Map.of("seconds", String.valueOf(cooldownSeconds(def, r.tier())))));
            for (GemDef.Passive p : def.passives()) {
                int amp = p.amplifier() + (p.scales() ? t.amplifierBonus() : 0);
                lore.add(Text.fill(msg.raw("item.passive-line"), Map.of("effect", Text.title(p.type().getKey().getKey()), "level", roman(amp + 1))));
            }
            if (def.xpMultiplier() > 1.0) lore.add(Text.fill(msg.raw("item.xp-line"), Map.of("multiplier", trim(def.xpMultiplier() * Math.sqrt(t.magnitude())))));
            if (def.noFallDamage()) lore.add(msg.raw("item.no-fall-line"));
        }
        if (!s.lore().isEmpty()) {
            lore.add("");
            lore.addAll(s.lore());
        }
        lore.add("");
        if (r.bound()) {
            String owner = plugin.getServer().getOfflinePlayer(r.owner()).getName();
            lore.add(Text.fill(msg.raw("item.bound-line"), Map.of("player", Text.escape(owner == null ? "?" : owner))));
            lore.add(msg.raw("item.use-hint"));
        } else {
            lore.add(msg.raw("item.unbound-line"));
        }
        return codec.gem(r, s.material(), Text.parse(gemName(r.type())), Text.lines(lore), model(s.model()));
    }

    public ItemStack shardItem(String type, int amount) {
        Theme.Skin s = theme.skin(type);
        String name = Text.fill(theme.shardNameFormat(), Map.of("color", s.tag(), "name", s.name()));
        List<String> lore = List.of(Text.fill(msg.raw("item.shard-lore"), Map.of("gem", gemName(type))));
        return codec.shard(type, amount, theme.shardMaterial(), Text.parse(name), Text.lines(lore), model(type + "_shard"));
    }

    private static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
    }

    private static String trim(double d) {
        String s = String.format(java.util.Locale.ROOT, "%.1f", d);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    // ================================================================= inventory helpers

    /** Slots in the player's own inventory (0-40 incl. armour + offhand) holding a copy of this gem. */
    public List<Integer> slotsOf(Player p, UUID gemId) {
        List<Integer> out = new ArrayList<>();
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            Token t = codec.token(inv.getItem(i));
            if (t != null && t.id().equals(gemId)) out.add(i);
        }
        return out;
    }

    public boolean holdsValid(Player p, Ledger.GemRecord r) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            Token t = codec.token(inv.getItem(i));
            if (t != null && t.id().equals(r.id()) && ledger.check(t, p.getUniqueId()) == Ledger.Verdict.VALID) return true;
        }
        return false;
    }

    /** Replaces every copy of this gem in the player's inventory with the current version. */
    public void refresh(Player p, Ledger.GemRecord r) {
        List<Integer> slots = slotsOf(p, r.id());
        if (slots.isEmpty()) return;
        p.getInventory().setItem(slots.get(0), gemItem(r));
        for (int i = 1; i < slots.size(); i++) p.getInventory().setItem(slots.get(i), null);
    }

    public void give(Player p, ItemStack item) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(item);
        for (ItemStack s : left.values()) dropProtected(p.getLocation(), s);
    }

    public Item dropProtected(Location at, ItemStack item) {
        Item e = at.getWorld().dropItem(at, item);
        protect(e);
        return e;
    }

    public void protect(Item e) {
        if (!codec.isGem(e.getItemStack())) return;
        e.setInvulnerable(true);
        e.setUnlimitedLifetime(true);
        e.setCanMobPickup(false);
    }

    /** Counts this player's shards of a type. */
    public int countShards(Player p, String type) {
        int n = 0;
        for (ItemStack s : p.getInventory().getStorageContents()) {
            if (codec.isShard(s) && type.equals(codec.type(s))) n += s.getAmount();
        }
        return n;
    }

    private void takeShards(Player p, String type, int amount) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < 36 && amount > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!codec.isShard(s) || !type.equals(codec.type(s))) continue;
            int take = Math.min(amount, s.getAmount());
            s.setAmount(s.getAmount() - take);
            inv.setItem(i, s.getAmount() <= 0 ? null : s);
            amount -= take;
        }
    }

    // ================================================================= integrity

    /**
     * Removes invalid or duplicated gem items. Online players are scanned together so the same
     * gem can never exist in two inventories at once. Returns how many items were removed.
     */
    public int sweep(List<Player> players) {
        List<DupeScan.Found> found = new ArrayList<>();
        // Owners first, so the owner's copy is the one that survives a cross-player duplicate.
        for (Player p : players) collect(p, p.getInventory(), true, found);
        for (Player p : players) collect(p, p.getEnderChest(), false, found);
        int removed = 0;
        for (DupeScan.Purge purge : DupeScan.scan(ledger, found)) {
            SlotRef ref = (SlotRef) purge.found().slot();
            ItemStack s = ref.inv().getItem(ref.index());
            if (s == null) continue;
            if ("stacked copies".equals(purge.reason())) {
                s.setAmount(1);
                ref.inv().setItem(ref.index(), s);
            } else {
                ref.inv().setItem(ref.index(), null);
            }
            removed++;
            plugin.getLogger().info("Removed a gem item from " + ref.who() + " (" + purge.reason() + ")");
        }
        return removed;
    }

    private record SlotRef(Inventory inv, int index, String who) {}

    private void collect(Player p, Inventory inv, boolean own, List<DupeScan.Found> out) {
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!codec.isGem(s)) continue;
            out.add(new DupeScan.Found(p.getUniqueId(), new SlotRef(inv, i, p.getName() + (own ? "" : " (ender chest)")), codec.token(s), s.getAmount()));
        }
    }

    /** Containers (chests, etc.) are checked when opened: stale or unknown gems in them are removed. */
    public int sweepContainer(Inventory inv) {
        int removed = 0;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!codec.isGem(s)) continue;
            Token t = codec.token(s);
            Ledger.Verdict v = ledger.check(t, null);
            boolean boundInContainer = v == Ledger.Verdict.VALID && settings.preventStoring()
                && ledger.get(t.id()).map(Ledger.GemRecord::bound).orElse(false);
            if (v != Ledger.Verdict.VALID || boundInContainer) {
                inv.setItem(i, null);
                removed++;
            }
        }
        return removed;
    }

    // ================================================================= player actions

    public Optional<Ledger.GemRecord> active(Player p) { return ledger.activeOf(p.getUniqueId()); }

    public void attune(Player p, ItemStack hand) {
        Token t = codec.token(hand);
        Ledger.Verdict v = ledger.check(t, p.getUniqueId());
        if (v != Ledger.Verdict.VALID) {
            p.getInventory().setItemInMainHand(null);
            msg.send(p, "gem.invalid");
            return;
        }
        Ledger.GemRecord r = ledger.get(t.id()).orElseThrow();
        if (r.bound()) { msg.send(p, "gem.already-yours"); return; }
        if (!defs.containsKey(r.type())) { msg.send(p, "gem.unknown-type", "type", r.type()); return; }
        if (!p.hasPermission("facet.attune")) { msg.send(p, "no-permission"); return; }
        long left = cooldowns.remainingSeconds(p.getUniqueId(), CD_SWAP);
        if (left > 0) { msg.send(p, "gem.swap-cooldown", "seconds", String.valueOf(left)); return; }

        Optional<Ledger.GemRecord> previous = ledger.bind(r.id(), p.getUniqueId());
        p.getInventory().setItemInMainHand(gemItem(r));
        previous.ifPresent(prev -> {
            stripPassives(p);
            List<Integer> slots = slotsOf(p, prev.id());
            for (int s : slots) p.getInventory().setItem(s, null);
            give(p, gemItem(prev));
            msg.send(p, "gem.previous-returned", "gem", gemName(prev.type()));
        });
        cooldowns.start(p.getUniqueId(), CD_SWAP, settings.swapCooldownMillis());
        Theme.Skin s = theme.skin(r.type());
        fx.burst(p.getLocation().add(0, 1, 0), Particle.END_ROD, 40, 0.5, 0.05);
        fx.ring(p.getLocation(), 1.2, s.particleColor());
        fx.sound(p, "block.beacon.activate", 1f, 1.4f);
        p.showTitle(net.kyori.adventure.title.Title.title(Text.parse(gemName(r.type())), Text.parse(msg.get("gem.attuned-subtitle", "tier", theme.tierLabel(tiers.get(r.tier()).id())))));
        msg.send(p, "gem.attuned", "gem", gemName(r.type()));
        plugin.saveSoon();
    }

    public void unbindSelf(Player p) {
        Optional<Ledger.GemRecord> a = active(p);
        if (a.isEmpty()) { msg.send(p, "gem.none"); return; }
        Ledger.GemRecord r = a.get();
        List<Integer> slots = slotsOf(p, r.id());
        if (slots.isEmpty() || !holdsValid(p, r)) { msg.send(p, "gem.not-carrying"); return; }
        ledger.unbind(r.id());
        stripPassives(p);
        for (int i = 1; i < slots.size(); i++) p.getInventory().setItem(slots.get(i), null);
        p.getInventory().setItem(slots.get(0), gemItem(r));
        msg.send(p, "gem.unbound", "gem", gemName(r.type()));
        plugin.saveSoon();
    }

    public void upgrade(Player p) {
        Optional<Ledger.GemRecord> a = active(p);
        if (a.isEmpty()) { msg.send(p, "gem.none"); return; }
        Ledger.GemRecord r = a.get();
        if (!holdsValid(p, r)) { msg.send(p, "gem.not-carrying"); return; }
        if (r.tier() >= tiers.maxIndex()) { msg.send(p, "upgrade.max"); return; }
        Tiers.Tier next = tiers.get(r.tier() + 1);
        int have = countShards(p, r.type());
        if (have < next.upgradeShards()) {
            msg.send(p, "upgrade.need-shards", "need", String.valueOf(next.upgradeShards()), "have", String.valueOf(have), "gem", gemName(r.type()));
            return;
        }
        Material extra = next.upgradeItem().isEmpty() ? null : Material.matchMaterial(next.upgradeItem());
        if (extra != null && !p.getInventory().containsAtLeast(new ItemStack(extra), next.upgradeItemAmount())) {
            msg.send(p, "upgrade.need-item", "amount", String.valueOf(next.upgradeItemAmount()), "item", Text.title(extra.getKey().getKey()));
            return;
        }
        if (next.capped() && !cap.tryTake()) { msg.send(p, "upgrade.cap-reached", "tier", theme.tierLabel(next.id())); return; }
        takeShards(p, r.type(), next.upgradeShards());
        if (extra != null) p.getInventory().removeItem(new ItemStack(extra, next.upgradeItemAmount()));
        ledger.upgrade(r.id());
        if (next.capped()) data.capUsed(cap.used());
        refresh(p, r);
        fx.burst(p.getLocation().add(0, 1, 0), Particle.TOTEM_OF_UNDYING, 60, 0.6, 0.3);
        fx.sound(p, "ui.toast.challenge_complete", 1f, 1.2f);
        msg.send(p, "upgrade.done", "gem", gemName(r.type()), "tier", theme.tierLabel(next.id()));
        if (next.capped()) {
            String line = msg.get("upgrade.broadcast", "player", Text.escape(p.getName()), "gem", gemName(r.type()), "tier", theme.tierLabel(next.id()),
                "left", cap.unlimited() ? "∞" : String.valueOf(cap.remaining()));
            if (!line.isBlank()) plugin.getServer().broadcast(Text.parse(line));
        }
        plugin.saveSoon();
    }

    public void recover(Player p) {
        Optional<Ledger.GemRecord> a = active(p);
        if (a.isEmpty()) { msg.send(p, "gem.none"); return; }
        Ledger.GemRecord r = a.get();
        if (holdsValid(p, r)) { msg.send(p, "recover.not-needed"); return; }
        long left = cooldowns.remainingSeconds(p.getUniqueId(), CD_RECOVER);
        if (left > 0) { msg.send(p, "recover.cooldown", "minutes", String.valueOf((left + 59) / 60)); return; }
        ledger.reissue(r.id()); // every other copy, wherever it is, is now worthless
        give(p, gemItem(r));
        cooldowns.start(p.getUniqueId(), CD_RECOVER, settings.recoverCooldownMillis());
        msg.send(p, "recover.done", "gem", gemName(r.type()));
        plugin.saveSoon();
    }

    public void chooseStarter(Player p, String type) {
        if (!settings.allowFreeStarter()) { msg.send(p, "starter.disabled"); return; }
        if (!defs.containsKey(type)) { msg.send(p, "gem.unknown-type", "type", Text.escape(type)); return; }
        if (data.usedStarter().contains(p.getUniqueId())) { msg.send(p, "starter.used"); return; }
        if (active(p).isPresent()) { msg.send(p, "starter.has-gem"); return; }
        Ledger.GemRecord r = ledger.create(type, 0, null);
        data.usedStarter().add(p.getUniqueId());
        ItemStack item = gemItem(r);
        give(p, item);
        msg.send(p, "starter.given", "gem", gemName(type));
        plugin.saveSoon();
    }

    // ================================================================= casting

    public void cast(Player p) {
        Optional<Ledger.GemRecord> a = active(p);
        if (a.isEmpty()) return;
        Ledger.GemRecord r = a.get();
        GemDef def = defs.get(r.type());
        if (def == null) { msg.send(p, "gem.unknown-type", "type", r.type()); return; }
        if (!p.hasPermission("facet.use")) { msg.send(p, "no-permission"); return; }
        long left = cooldowns.remainingSeconds(p.getUniqueId(), CD_ABILITY);
        if (left > 0) {
            msg.send(p, "cast.cooldown", "seconds", String.valueOf(left));
            fx.sound(p, "block.note_block.bass", 0.6f, 0.6f);
            return;
        }
        int cd = cooldownSeconds(def, r.tier());
        CastContext ctx = new CastContext(plugin, p, def, tiers.get(r.tier()), r.tier(), cd, theme.skin(r.type()), fx, targets);
        boolean spent;
        try {
            spent = def.ability().cast(ctx);
        } catch (RuntimeException e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Ability " + def.ability().id() + " failed for " + p.getName(), e);
            msg.send(p, "cast.failed");
            return;
        }
        if (spent) cooldowns.start(p.getUniqueId(), CD_ABILITY, cd * 1000L);
        else msg.send(p, "cast.no-target");
    }

    // ================================================================= passives + status

    /** Runs every 2 seconds: passives for players carrying their own active gem. */
    public void passiveTick(Player p) {
        Optional<Ledger.GemRecord> a = active(p);
        GemDef def = a.map(r -> defs.get(r.type())).orElse(null);
        if (def == null || !holdsValid(p, a.get())) { stripPassives(p); return; }
        Tiers.Tier t = tiers.get(a.get().tier());
        Set<PotionEffectType> mine = applied.computeIfAbsent(p.getUniqueId(), k -> new java.util.HashSet<>());
        for (GemDef.Passive ps : def.passives()) {
            int amp = ps.amplifier() + (ps.scales() ? t.amplifierBonus() : 0);
            PotionEffect cur = p.getPotionEffect(ps.type());
            // Night vision flickers under 10s left, so passives are topped up well before that.
            if (cur != null && (cur.getAmplifier() > amp || (cur.getAmplifier() == amp && cur.getDuration() > 220))) continue;
            p.addPotionEffect(new PotionEffect(ps.type(), 300, amp, true, false, true));
            mine.add(ps.type());
        }
        if (settings.ambientParticles() && codec.isGem(p.getInventory().getItemInMainHand())) {
            fx.dust(p.getLocation().add(0, 1.0, 0), theme.skin(a.get().type()).particleColor(), 0.9f, 3, 0.35);
        }
    }

    /** Removes only effects this plugin applied (ambient, ≤15s), never a potion the player drank. */
    public void stripPassives(Player p) {
        Set<PotionEffectType> mine = applied.remove(p.getUniqueId());
        if (mine == null) return;
        for (PotionEffectType type : mine) {
            PotionEffect cur = p.getPotionEffect(type);
            if (cur != null && cur.isAmbient() && cur.getDuration() <= 300) p.removePotionEffect(type);
        }
    }

    public void forget(Player p) { applied.remove(p.getUniqueId()); }

    public void actionBar(Player p) {
        if (!settings.actionBar()) return;
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!codec.isGem(hand)) return;
        Optional<Ledger.GemRecord> a = active(p);
        Token t = codec.token(hand);
        if (a.isEmpty() || t == null || !t.id().equals(a.get().id())) {
            p.sendActionBar(Text.parse(msg.get("bar.unbound")));
            return;
        }
        long left = cooldowns.remainingSeconds(p.getUniqueId(), CD_ABILITY);
        Theme.Skin s = theme.skin(a.get().type());
        String line = left > 0
            ? msg.get("bar.cooldown", "color", s.tag(), "ability", s.abilityName(), "seconds", String.valueOf(left))
            : msg.get("bar.ready", "color", s.tag(), "ability", s.abilityName());
        p.sendActionBar(Text.parse(line));
    }

    public boolean noFall(Player p) {
        Long until = io.github.gin0saurus.facet.gems.abilities.Abilities.FALL_GRACE.get(p.getUniqueId());
        if (until != null) {
            if (until > System.currentTimeMillis()) return true;
            io.github.gin0saurus.facet.gems.abilities.Abilities.FALL_GRACE.remove(p.getUniqueId());
        }
        Optional<Ledger.GemRecord> a = active(p);
        if (a.isEmpty()) return false;
        GemDef def = defs.get(a.get().type());
        return def != null && def.noFallDamage() && holdsValid(p, a.get());
    }

    public double xpMultiplier(Player p) {
        Optional<Ledger.GemRecord> a = active(p);
        if (a.isEmpty()) return 1.0;
        GemDef def = defs.get(a.get().type());
        if (def == null || def.xpMultiplier() <= 1.0 || !holdsValid(p, a.get())) return 1.0;
        return def.xpMultiplier() * Math.sqrt(tiers.get(a.get().tier()).magnitude());
    }

    /** Materials that count as ores for shard drops. */
    public static final Set<Material> ORES;
    static {
        Set<Material> s = EnumSet.noneOf(Material.class);
        for (Material m : Material.values()) {
            if (m.isLegacy() || !m.isBlock()) continue;
            String k = m.name();
            if (k.endsWith("_ORE") || k.equals("ANCIENT_DEBRIS")) s.add(m);
        }
        ORES = s;
    }
}
