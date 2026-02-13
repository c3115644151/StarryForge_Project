package com.starryforge.features.scrap;

import com.starryforge.StarryForge;
import com.starryforge.utils.Keys;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class ScrapRecoveryListener implements Listener {

    private final StarryForge plugin;

    public ScrapRecoveryListener(StarryForge plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        if (event.getBlock().getType() != Material.BLAST_FURNACE) {
            return;
        }

        ItemStack source = event.getSource();
        if (source == null || !source.hasItemMeta()) return;

        ItemMeta meta = source.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        String itemId = pdc.get(Keys.ITEM_ID_KEY, PersistentDataType.STRING);
        
        // Check for Unified Scrap OR Legacy Slag
        boolean isScrap = "STARRY_SCRAP".equals(itemId);
        boolean isLegacySlag = "MINERAL_SLAG".equals(itemId);

        if (!isScrap && !isLegacySlag) {
            // Prevent regular Charcoal smelting if recipe conflicts
            if (event.getResult() != null && event.getResult().getType() == Material.CHARCOAL) {
                event.setCancelled(true);
            }
            return;
        }

        // Recover Data
        String sourceId;
        int amount;
        // int quality; // Unused for now

        if (isScrap) {
            sourceId = pdc.get(Keys.SCRAP_SOURCE_ID, PersistentDataType.STRING);
            amount = pdc.getOrDefault(Keys.SCRAP_AMOUNT, PersistentDataType.INTEGER, 1);
            // quality = pdc.getOrDefault(Keys.SCRAP_QUALITY, PersistentDataType.INTEGER, 1);
        } else {
            // Legacy Mapping
            sourceId = pdc.getOrDefault(Keys.SLAG_SPECIAL_ORE_TYPE, PersistentDataType.STRING, "IRON_ORE");
            amount = pdc.getOrDefault(Keys.SLAG_SPECIAL_ORE_AMOUNT, PersistentDataType.INTEGER, 1);
            // quality = pdc.getOrDefault(Keys.SLAG_SPECIAL_ORE_STARS, PersistentDataType.INTEGER, 1);
        }

        // Generate Result
        ItemStack result = null;

        // 1. Try StarryForge Item Manager
        if (plugin.getItemManager() != null && sourceId != null) {
            result = plugin.getItemManager().getItem(sourceId.replace("BIOMEGIFTS:", "")); // Strip prefix if legacy
        }

        // 2. Fallback to Material
        if (result == null && sourceId != null) {
            Material mat = Material.matchMaterial(sourceId.replace("BIOMEGIFTS:", ""));
            if (mat == null) mat = Material.RAW_IRON; // Ultimate fallback
            result = new ItemStack(mat);
        }

        if (result != null) {
            result.setAmount(amount);
            // TODO: Apply quality/stars to result if it's an ore?
            // For components, we just return the raw material (e.g. Iron Ingot), usually no stars needed unless it's a special material.
            // If it's a StarryForge item, it might have its own stats.
            
            event.setResult(result);
        }
    }
}
