package com.starryforge.features.mining;

import com.starryforge.StarryForge;
import com.starryforge.features.core.PDCManager;
import com.starryforge.utils.Keys;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;

public class GlobalDropListener implements Listener {

    private final MiningManager miningManager;

    public GlobalDropListener(MiningManager miningManager) {
        this.miningManager = miningManager;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) return;
        
        miningManager.handleBlockBreak(
            event.getPlayer(), 
            event.getBlock(), 
            event.getPlayer().getInventory().getItemInMainHand()
        );
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockDropItem(BlockDropItemEvent event) {
        // Handle Vanilla Ores
        for (Item itemEntity : event.getItems()) {
            ItemStack item = itemEntity.getItemStack();
            if (isOre(item.getType()) && !PDCManager.hasKey(item, Keys.ORE_STAR_KEY)) {
                Material blockMat = getBlockMaterial(item.getType());
                if (blockMat != null) {
                    int stars = miningManager.calculateStars(itemEntity.getLocation(), blockMat);
                    StarryForge.getInstance().getItemManager().applyOreStar(item, stars);
                    itemEntity.setItemStack(item);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onItemSpawn(ItemSpawnEvent event) {
        // Handle BiomeGifts Specialty Ores (which are spawned directly)
        Item itemEntity = event.getEntity();
        ItemStack item = itemEntity.getItemStack();
        
        // Check if it's a specialty ore (CustomModelData >= 10000)
        // And ensure it doesn't already have stars (prevent re-rolling on player drop)
        if (item.hasItemMeta() && item.getItemMeta().hasCustomModelData() 
            && item.getItemMeta().getCustomModelData() >= 10000 
            && !PDCManager.hasKey(item, Keys.ORE_STAR_KEY)) {
            
            Material blockMat = getBlockMaterial(item.getType());
            // If blockMat is null, it might use fallback logic in calculateStars (Normal Biome)
            // But we need a material to pass. Use item type as fallback if mapping fails?
            // getBiomeType expects Block Material. 
            // If we pass Item Material (e.g. COAL), BiomeGifts config might not find it.
            // But if we pass null/wrong, it defaults to Normal Biome which is safe.
            
            Material targetMat = blockMat != null ? blockMat : item.getType();
            int stars = miningManager.calculateStars(itemEntity.getLocation(), targetMat);
            StarryForge.getInstance().getItemManager().applyOreStar(item, stars);
            itemEntity.setItemStack(item);
        }
    }

    private boolean isOre(Material mat) {
        return mat == Material.COAL || mat == Material.RAW_IRON || mat == Material.RAW_COPPER 
            || mat == Material.RAW_GOLD || mat == Material.REDSTONE || mat == Material.LAPIS_LAZULI 
            || mat == Material.DIAMOND || mat == Material.EMERALD || mat == Material.QUARTZ;
    }

    private Material getBlockMaterial(Material itemMat) {
        switch (itemMat) {
            case COAL: return Material.COAL_ORE;
            case RAW_IRON: return Material.IRON_ORE;
            case RAW_COPPER: return Material.COPPER_ORE;
            case RAW_GOLD: return Material.GOLD_ORE;
            case REDSTONE: return Material.REDSTONE_ORE;
            case LAPIS_LAZULI: return Material.LAPIS_ORE;
            case DIAMOND: return Material.DIAMOND_ORE;
            case EMERALD: return Material.EMERALD_ORE;
            case QUARTZ: return Material.NETHER_QUARTZ_ORE;
            default: return null;
        }
    }
}
