package io.github.gin0saurus.facet.gems;

import io.github.gin0saurus.facet.core.Ledger;
import io.github.gin0saurus.facet.core.Token;
import io.github.gin0saurus.facet.engine.ItemCodec;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Keeps gems where they belong. Bound gems cannot be dropped, stored in any container (chests,
 * shulkers, bundles, hoppers, item frames, crafting grids...) or destroyed on the ground.
 * Everything here fails closed: if in doubt, the click is cancelled.
 */
public final class ProtectionListener implements Listener {

    private final GemService gems;
    private final ItemCodec codec;

    public ProtectionListener(GemService gems) {
        this.gems = gems;
        this.codec = gems.codec();
    }

    private boolean isBound(ItemStack s) {
        Token t = codec.token(s);
        return t != null && gems.ledger().get(t.id()).map(Ledger.GemRecord::bound).orElse(false);
    }

    private static boolean isBundle(ItemStack s) {
        return s != null && s.getType().getKey().getKey().endsWith("bundle");
    }

    // ------------------------------------------------------------- dropping

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        ItemStack s = e.getItemDrop().getItemStack();
        if (gems.settings().preventDropping() && isBound(s)) {
            e.setCancelled(true);
            gems.msg().send(e.getPlayer(), "protect.no-drop");
        }
    }

    // ------------------------------------------------------------- containers

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!gems.settings().preventStoring() || !(e.getWhoClicked() instanceof Player p)) return;
        ItemStack current = e.getCurrentItem();
        ItemStack cursor = e.getCursor();
        ItemStack hotbar = e.getHotbarButton() >= 0 ? p.getInventory().getItem(e.getHotbarButton()) : null;
        ItemStack offhand = e.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND ? p.getInventory().getItemInOffHand() : null;
        boolean involvesBound = isBound(current) || isBound(cursor) || isBound(hotbar) || isBound(offhand);
        if (!involvesBound) return;

        // Bundles: putting a gem in a bundle, or a bundle sucking up a gem.
        if (isBundle(current) || isBundle(cursor)) { deny(e, p); return; }
        InventoryAction action = e.getAction();
        if (action.name().contains("BUNDLE")) { deny(e, p); return; }

        // With only the player's own inventory open (top = 2x2 crafting grid), moving gems around
        // it is fine - except into the crafting grid itself. Shift-click there never targets the grid.
        Inventory top = e.getView().getTopInventory();
        if (top.getType() == InventoryType.CRAFTING) {
            if (e.getClickedInventory() == top && (isBound(cursor) || isBound(hotbar))) deny(e, p);
            return;
        }
        // Any other inventory open (chest, furnace, anvil, villager, ender chest...): no bound gem may move at all.
        deny(e, p);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!gems.settings().preventStoring() || !isBound(e.getOldCursor())) return;
        int topSize = e.getView().getTopInventory().getSize();
        for (int raw : e.getRawSlots()) {
            if (raw < topSize) {
                e.setCancelled(true);
                if (e.getWhoClicked() instanceof Player p) gems.msg().send(p, "protect.no-store");
                return;
            }
        }
    }

    private void deny(InventoryClickEvent e, HumanEntity who) {
        e.setCancelled(true);
        if (who instanceof Player p) gems.msg().send(p, "protect.no-store");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        InventoryType t = e.getInventory().getType();
        if (t == InventoryType.PLAYER || t == InventoryType.CRAFTING) return;
        int n = gems.sweepContainer(e.getInventory());
        if (n > 0) gems.plugin().getLogger().info("Removed " + n + " invalid gem item(s) from a " + t + " opened by " + e.getPlayer().getName());
    }

    /** Gems and shards are never crafting ingredients (stops vanilla recipes eating them). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        for (ItemStack s : e.getInventory().getMatrix()) {
            if (codec.isOurs(s)) { e.getInventory().setResult(null); return; }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent e) {
        if (codec.isGem(e.getItem().getItemStack())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        if (codec.isGem(e.getPlayerItem())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player) && codec.isGem(e.getItem().getItemStack())) e.setCancelled(true);
    }

    // ------------------------------------------------------------- ground items

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(ItemSpawnEvent e) { gems.protect(e.getEntity()); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Item i && codec.isGem(i.getItemStack())) {
            e.setCancelled(true);
            if (e.getCause() == EntityDamageEvent.DamageCause.VOID) {
                // Fell out of the world: bring it back up rather than lose it.
                i.teleport(i.getWorld().getSpawnLocation());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCombust(EntityCombustEvent e) {
        if (e.getEntity() instanceof Item i && codec.isGem(i.getItemStack())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDespawn(ItemDespawnEvent e) {
        if (codec.isGem(e.getEntity().getItemStack())) e.setCancelled(true);
    }
}
