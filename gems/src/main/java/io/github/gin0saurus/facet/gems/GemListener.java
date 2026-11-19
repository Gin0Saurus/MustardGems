package io.github.gin0saurus.facet.gems;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import io.github.gin0saurus.facet.core.Ledger;
import io.github.gin0saurus.facet.core.PairLimiter;
import io.github.gin0saurus.facet.core.Token;
import io.github.gin0saurus.facet.engine.ItemCodec;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Turns Minecraft events into GemService calls: casting, attuning, death drops, rewards. */
public final class GemListener implements Listener {

    private final GemService gems;
    private final ItemCodec codec;
    private final PairLimiter farm;
    private final Map<UUID, Long> lastUse = new HashMap<>();
    /** Ores placed by players this session: mining them never drops shards (stops place-and-mine farms). */
    private final Set<Location> placedOres = new LinkedHashSet<>();

    public GemListener(GemService gems, PairLimiter farm) {
        this.gems = gems;
        this.codec = gems.codec();
        this.farm = farm;
    }

    // ------------------------------------------------------------- right-click

    private boolean debounce(Player p) {
        long now = System.currentTimeMillis();
        Long last = lastUse.put(p.getUniqueId(), now);
        return last != null && now - last < 200;
    }

    private void use(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        Token t = codec.token(hand);
        if (t == null) {
            p.getInventory().setItemInMainHand(null);
            gems.msg().send(p, "gem.invalid");
            return;
        }
        Optional<Ledger.GemRecord> rec = gems.ledger().get(t.id());
        if (rec.isPresent() && !rec.get().bound()) { gems.attune(p, hand); return; }
        Ledger.Verdict v = gems.ledger().check(t, p.getUniqueId());
        if (v == Ledger.Verdict.FOREIGN) { gems.msg().send(p, "gem.not-yours"); return; }
        if (v != Ledger.Verdict.VALID) {
            p.getInventory().setItemInMainHand(null);
            gems.msg().send(p, "gem.invalid");
            return;
        }
        gems.cast(p);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        if (e.getHand() == EquipmentSlot.OFF_HAND) {
            // Never let an offhand gem be placed, eaten, thrown or put into a block.
            if (codec.isOurs(p.getInventory().getItemInOffHand())) cancel(e);
            return;
        }
        if (!codec.isOurs(e.getItem())) return;
        cancel(e);
        if (!codec.isGem(e.getItem()) || debounce(p)) return;
        use(p);
    }

    private static void cancel(PlayerInteractEvent e) {
        e.setUseItemInHand(Event.Result.DENY);
        e.setUseInteractedBlock(Event.Result.DENY);
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        ItemStack held = e.getHand() == EquipmentSlot.OFF_HAND ? p.getInventory().getItemInOffHand() : p.getInventory().getItemInMainHand();
        if (!codec.isOurs(held)) return;
        e.setCancelled(true); // no item frames, allays, armor stands, name-tag style uses
        if (e.getHand() == EquipmentSlot.HAND && codec.isGem(held) && !(e instanceof PlayerInteractAtEntityEvent) && !debounce(p)) use(p);
    }

    // ------------------------------------------------------------- death + PvP rewards

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Optional<Ledger.GemRecord> active = gems.active(victim);

        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim) && active.isPresent() && gems.settings().shardsPerKill() > 0
                && farm.tryClaim(killer.getUniqueId(), victim.getUniqueId(), gems.settings().shardFarmWindowMillis())) {
            Ledger.GemRecord r = active.get();
            int amount = gems.settings().shardsPerKill() * (1 + r.tier());
            gems.give(killer, gems.shardItem(r.type(), amount));
            gems.msg().send(killer, "pvp.reward", "amount", String.valueOf(amount), "gem", gems.gemName(r.type()));
        }

        if (e.getKeepInventory()) return; // items stay with the player; nothing to do
        List<ItemStack> add = new ArrayList<>();
        Iterator<ItemStack> it = e.getDrops().iterator();
        while (it.hasNext()) {
            ItemStack s = it.next();
            if (!codec.isGem(s)) continue;
            Token t = codec.token(s);
            if (gems.ledger().check(t, victim.getUniqueId()) != Ledger.Verdict.VALID) { it.remove(); continue; }
            Ledger.GemRecord r = gems.ledger().get(t.id()).orElseThrow();
            if (!r.bound()) continue; // carried loot drops as it is
            it.remove();
            if (gems.settings().dropOnDeath()) {
                gems.ledger().unbind(r.id());
                add.add(gems.gemItem(r));
                gems.stripPassives(victim);
                gems.msg().send(victim, "death.dropped", "gem", gems.gemName(r.type()));
            } else {
                e.getItemsToKeep().add(s);
            }
        }
        e.getDrops().addAll(add);
        if (!add.isEmpty()) gems.plugin().saveSoon();
    }

    // ------------------------------------------------------------- shards from mining

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!GemService.ORES.contains(e.getBlockPlaced().getType())) return;
        placedOres.add(e.getBlockPlaced().getLocation());
        if (placedOres.size() > 50_000) {
            Iterator<Location> it = placedOres.iterator();
            for (int i = 0; i < 10_000 && it.hasNext(); i++) { it.next(); it.remove(); }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (!GemService.ORES.contains(b.getType())) return;
        if (placedOres.remove(b.getLocation())) return;
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SURVIVAL) return;
        double chance = gems.settings().miningShardChance();
        if (chance <= 0 || gems.defs().isEmpty() || ThreadLocalRandom.current().nextDouble() >= chance) return;
        List<String> types = new ArrayList<>(gems.defs().keySet());
        String type = types.get(ThreadLocalRandom.current().nextInt(types.size()));
        b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), gems.shardItem(type, 1));
        gems.msg().send(p, "mining.shard", "gem", gems.gemName(type));
    }

    // ------------------------------------------------------------- passives that need events

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onXp(PlayerExpChangeEvent e) {
        if (e.getAmount() <= 0) return;
        double m = gems.xpMultiplier(e.getPlayer());
        if (m > 1.0) e.setAmount((int) Math.round(e.getAmount() * m));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL && e.getEntity() instanceof Player p && gems.noFall(p)) e.setCancelled(true);
    }

    // ------------------------------------------------------------- join / quit

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        gems.sweep(List.of(p));
        gems.restyle(p);
        gems.plugin().sendPack(p);
        if (gems.active(p).isEmpty() && gems.settings().allowFreeStarter()
                && !gems.plugin().data().usedStarter().contains(p.getUniqueId()) && p.hasPermission("facet.starter")) {
            gems.plugin().getServer().getScheduler().runTaskLater(gems.plugin(), () -> {
                if (p.isOnline()) gems.msg().send(p, "starter.prompt");
            }, 60L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        lastUse.remove(e.getPlayer().getUniqueId());
        gems.forget(e.getPlayer());
    }
}
