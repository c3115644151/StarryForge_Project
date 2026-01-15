package com.starryforge.features.forging;

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
import org.bukkit.block.TileState;

public class AstralForgeNodeListener implements Listener {

    private final StarryForge plugin;

    public AstralForgeNodeListener(StarryForge plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.isCancelled())
            return;

        ItemStack item = event.getItemInHand();
        if (item == null || !item.hasItemMeta())
            return;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer itemPdc = meta.getPersistentDataContainer();
        String itemId = itemPdc.get(Keys.ITEM_ID_KEY, PersistentDataType.STRING);

        if ("ASTRAL_FORGE_NODE".equals(itemId)) {
            Block block = event.getBlock();
            // Ensure physics doesn't instantly break it if checks fail (though Smithing
            // Table is solid)

            // Try to persist ID if it is a TileState (1.20+ Smithing Table is likely a
            // TileState)
            if (block.getState() instanceof TileState state) {
                PersistentDataContainer blockPdc = state.getPersistentDataContainer();
                blockPdc.set(Keys.ITEM_ID_KEY, PersistentDataType.STRING, "ASTRAL_FORGE_NODE");
                state.update();
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST) // Lowest to ensure we catch it before others if needed, but usually
                                                   // High is better for drops
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled())
            return;

        Block block = event.getBlock();
        if (block.getType() != Material.SMITHING_TABLE)
            return;

        if (block.getState() instanceof TileState state) {
            PersistentDataContainer pdc = state.getPersistentDataContainer();
            String id = pdc.get(Keys.ITEM_ID_KEY, PersistentDataType.STRING);

            if ("ASTRAL_FORGE_NODE".equals(id)) {
                // Drop custom item
                event.setDropItems(false);
                ItemStack customItem = plugin.getItemManager().getItem("ASTRAL_FORGE_NODE");
                if (customItem != null) {
                    block.getWorld().dropItemNaturally(block.getLocation(), customItem);
                }
            }
        }
    }
}
