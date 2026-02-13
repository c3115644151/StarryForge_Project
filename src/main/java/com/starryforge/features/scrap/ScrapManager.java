package com.starryforge.features.scrap;

import com.starryforge.StarryForge;
import com.starryforge.utils.Keys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class ScrapManager {

    private final MiniMessage mm = MiniMessage.miniMessage();
    private final ScrapRecoveryListener recoveryListener;

    public ScrapManager(StarryForge plugin) {
        this.recoveryListener = new ScrapRecoveryListener(plugin);
        plugin.getServer().getPluginManager().registerEvents(recoveryListener, plugin);
    }

    public enum ScrapType {
        ALLOY_SLAG("矿物残渣", "失败的熔炼产物"),
        COMPONENT_SCRAP("组件废料", "损毁的组件");

        private final String displayName;
        private final String description;

        ScrapType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
    }

    /**
     * Create a Scrap Item
     * @param type The type of scrap (Slag or Component)
     * @param sourceId The ID of the source material (e.g. "IRON_ORE" or "starryforge:STAR_STEEL")
     * @param sourceName The display name of the source (for Lore)
     * @param quality Quality rating (Stars for Ore, Tier/Level for Component)
     * @param amount Amount of material to recover
     * @return The scrap ItemStack
     */
    public ItemStack createScrap(ScrapType type, String sourceId, String sourceName, int quality, int amount) {
        ItemStack scrap = new ItemStack(Material.CHARCOAL, 1);
        ItemMeta meta = scrap.getItemMeta();
        if (meta == null) return scrap;

        // Visuals
        meta.displayName(mm.deserialize("<!i><gray>" + type.displayName));
        
        java.util.List<Component> lore = new java.util.ArrayList<>();
        lore.add(mm.deserialize("<dark_gray>" + type.description));
        lore.add(mm.deserialize("<gray>来源: <white>" + sourceName));
        lore.add(mm.deserialize("<gray>品质: <yellow>" + quality + "★"));
        meta.lore(lore);

        // Model Data (1001 for Slag, maybe 1002 for Component?)
        meta.setCustomModelData(type == ScrapType.ALLOY_SLAG ? 1001 : 1002);

        // PDC Storage
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(Keys.ITEM_ID_KEY, PersistentDataType.STRING, "STARRY_SCRAP");
        pdc.set(Keys.SCRAP_TYPE, PersistentDataType.STRING, type.name());
        pdc.set(Keys.SCRAP_SOURCE_ID, PersistentDataType.STRING, sourceId);
        pdc.set(Keys.SCRAP_QUALITY, PersistentDataType.INTEGER, quality);
        pdc.set(Keys.SCRAP_AMOUNT, PersistentDataType.INTEGER, Math.max(1, amount));

        scrap.setItemMeta(meta);
        return scrap;
    }
}
