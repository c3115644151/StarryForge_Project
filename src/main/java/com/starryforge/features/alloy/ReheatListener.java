package com.starryforge.features.alloy;

import com.starryforge.StarryForge;
import com.starryforge.features.core.PDCManager;
import com.starryforge.utils.Keys;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Handles the re-heating of StarryForge alloy items in Vanilla Blast Furnaces.
 * When a refined item is placed in a Blast Furnace, it becomes heated to max
 * temperature.
 */
public class ReheatListener implements Listener {

    private final StarryForge plugin;

    public ReheatListener(StarryForge plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        if (event.getBlock().getType() != Material.BLAST_FURNACE) {
            return;
        }

        ItemStack source = event.getSource();
        if (source == null || !source.hasItemMeta()) {
            return;
        }

        ItemMeta meta = source.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // Check if this is a StarryForge item by checking for ITEM_ID_KEY
        String itemId = pdc.get(Keys.ITEM_ID_KEY, PersistentDataType.STRING);
        if (itemId == null) {
            // This event matched our PAPER -> PAPER recipe, but the input is not a SF item.
            // We must cancel it to prevent regular Paper from being smelted.
            if (event.getResult() != null && event.getResult().getType() == Material.PAPER) {
                event.setCancelled(true);
            }
            return;
        }

        // Skip Mineral Slag (handled by SlagRecoveryListener)
        if ("MINERAL_SLAG".equals(itemId)) {
            return;
        }

        // Check if this item can be reheated (has MAX_TEMPERATURE_KEY or is a known refined item)
        Double maxTemp = pdc.get(Keys.MAX_TEMPERATURE_KEY, PersistentDataType.DOUBLE);

        // If no max temp stored, check if it's a refined item that should have one
        if (maxTemp == null) {
            // Try to get max temp from recipes config based on item ID
            maxTemp = getMaxTempForItem(itemId);
            if (maxTemp == null) {
                // Not a reheatable item, cancel to prevent PAPER output
                event.setCancelled(true);
                return;
            }
        }

        // Create a copy of the item as the result, but at max temp
        // Important: Only output 1 item per smelt (vanilla behavior)
        ItemStack result = source.clone();
        result.setAmount(1);
        PDCManager.setTemperature(result, maxTemp);

        // Also ensure MAX_TEMPERATURE_KEY is set for future reheats
        ItemMeta resultMeta = result.getItemMeta();
        resultMeta.getPersistentDataContainer().set(Keys.MAX_TEMPERATURE_KEY, PersistentDataType.DOUBLE, maxTemp);
        result.setItemMeta(resultMeta);

        plugin.getThermodynamicsManager().updateItemLore(result, maxTemp);

        event.setResult(result);
    }

    /**
     * Gets the max temperature for a known refined item ID.
     * Returns null if the item is not a reheatable refined item.
     */
    private Double getMaxTempForItem(String itemId) {
        // Check alloy forge recipes for output temperature
        for (AlloyManager.AlloyRecipe recipe : plugin.getAlloyManager().getRecipes()) {
            if (recipe.resultId().equalsIgnoreCase(itemId)) {
                return recipe.outputTemperature();
            }
        }
        return null;
    }
}
