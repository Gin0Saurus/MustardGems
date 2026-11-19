package io.github.gin0saurus.facet.engine;

import java.util.List;

import io.github.gin0saurus.facet.core.Ledger;
import io.github.gin0saurus.facet.core.Token;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Reads and writes the hidden tags on power items. The tags are the only thing the plugin
 * trusts on an item - names and lore are cosmetic and can be renamed in an anvil safely.
 */
public final class ItemCodec {

    public static final String KIND_GEM = "gem";
    public static final String KIND_SHARD = "shard";

    private final NamespacedKey kindKey;
    private final NamespacedKey tokenKey;
    private final NamespacedKey typeKey;
    private final NamespacedKey tierKey;

    public ItemCodec(Plugin plugin) {
        this.kindKey = new NamespacedKey(plugin, "kind");
        this.tokenKey = new NamespacedKey(plugin, "token");
        this.typeKey = new NamespacedKey(plugin, "type");
        this.tierKey = new NamespacedKey(plugin, "tier");
    }

    public ItemStack gem(Ledger.GemRecord record, Material material, Component name, List<Component> lore, NamespacedKey model) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        meta.setMaxStackSize(1);
        meta.setEnchantmentGlintOverride(true);
        if (model != null) meta.setItemModel(model);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(kindKey, PersistentDataType.STRING, KIND_GEM);
        pdc.set(tokenKey, PersistentDataType.STRING, record.token().encode());
        pdc.set(typeKey, PersistentDataType.STRING, record.type());
        pdc.set(tierKey, PersistentDataType.INTEGER, record.tier());
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack shard(String type, int amount, Material material, Component name, List<Component> lore, NamespacedKey model) {
        ItemStack item = new ItemStack(material, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        if (model != null) meta.setItemModel(model);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(kindKey, PersistentDataType.STRING, KIND_SHARD);
        pdc.set(typeKey, PersistentDataType.STRING, type);
        item.setItemMeta(meta);
        return item;
    }

    public String kind(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(kindKey, PersistentDataType.STRING);
    }

    public boolean isGem(ItemStack item) { return KIND_GEM.equals(kind(item)); }

    public boolean isShard(ItemStack item) { return KIND_SHARD.equals(kind(item)); }

    /** True for anything this plugin made (gem or shard). */
    public boolean isOurs(ItemStack item) { return kind(item) != null; }

    /** Null when the item is not a gem or its token tag is damaged. */
    public Token token(ItemStack item) {
        if (!isGem(item)) return null;
        return Token.parse(item.getItemMeta().getPersistentDataContainer().get(tokenKey, PersistentDataType.STRING));
    }

    public String type(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
    }
}
