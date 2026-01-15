package com.starryforge.features.alloy;

import com.starryforge.StarryForge;
import com.starryforge.utils.Keys;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Handles placing and breaking of Alloy Forge Core blocks.
 * When player places an ALLOY_FORGE_CORE item, writes PDC to the block.
 * When player breaks the block, drops the custom item instead of vanilla
 * Lodestone.
 */
public class AlloyForgeCoreListener implements Listener {

    private final StarryForge plugin;

    public AlloyForgeCoreListener(StarryForge plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item == null || !item.hasItemMeta()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer itemPdc = meta.getPersistentDataContainer();
        String itemId = itemPdc.get(Keys.ITEM_ID_KEY, PersistentDataType.STRING);

        if ("ALLOY_FORGE_CORE".equals(itemId)) {
            Block block = event.getBlock();
            if (block.getState() instanceof org.bukkit.block.TileState state) {
                PersistentDataContainer blockPdc = state.getPersistentDataContainer();
                blockPdc.set(Keys.ITEM_ID_KEY, PersistentDataType.STRING, "ALLOY_FORGE_CORE");
                state.update();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.BLAST_FURNACE) {
            return;
        }

        if (block.getState() instanceof org.bukkit.block.TileState state) {
            PersistentDataContainer pdc = state.getPersistentDataContainer();
            String id = pdc.get(Keys.ITEM_ID_KEY, PersistentDataType.STRING);

            if ("ALLOY_FORGE_CORE".equals(id)) {
                // Cancel vanilla drop and drop custom item instead
                event.setDropItems(false);
                ItemStack coreItem = plugin.getItemManager().getItem("ALLOY_FORGE_CORE");
                if (coreItem != null) {
                    block.getWorld().dropItemNaturally(block.getLocation(), coreItem);
                }
            }
        }
    }
}
