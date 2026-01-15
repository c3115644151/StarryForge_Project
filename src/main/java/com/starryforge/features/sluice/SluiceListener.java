package com.starryforge.features.sluice;

import com.starryforge.StarryForge;
import com.starryforge.utils.Keys;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class SluiceListener implements Listener {

    private final SluiceManager manager;

    public SluiceListener(SluiceManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        int tier = 0;
        
        if (StarryForge.getInstance().getItemManager().isCustomItem(item, "SLUICE_MACHINE_I")) tier = 1;
        else if (StarryForge.getInstance().getItemManager().isCustomItem(item, "SLUICE_MACHINE_II")) tier = 2;
        else if (StarryForge.getInstance().getItemManager().isCustomItem(item, "SLUICE_MACHINE_III")) tier = 3;
        else if (StarryForge.getInstance().getItemManager().isCustomItem(item, "SLUICE_MACHINE")) tier = 1; // Legacy fallback
        
        if (tier > 0) {
            manager.registerSluice(event.getBlock(), tier);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (manager.isSluice(block)) {
            // Get Tier
            int tier = 1;
            if (block.getState() instanceof Barrel) {
                Barrel barrel = (Barrel) block.getState();
                tier = barrel.getPersistentDataContainer().getOrDefault(Keys.SLUICE_TIER, PersistentDataType.INTEGER, 1);
            }
            
            // 掉落对应等级的洗矿机物品
            String itemId = "SLUICE_MACHINE_I";
            if (tier == 2) itemId = "SLUICE_MACHINE_II";
            if (tier == 3) itemId = "SLUICE_MACHINE_III";
            
            ItemStack sluiceItem = StarryForge.getInstance().getItemManager().getItem(itemId);
            if (sluiceItem != null) {
                block.getWorld().dropItemNaturally(block.getLocation(), sluiceItem);
            }
            
            // 清理边框
            if (block.getState() instanceof Barrel) {
                Barrel barrel = (Barrel) block.getState();
                Inventory inv = barrel.getInventory();
                // 清理边框和进度条，防止掉落玻璃板
                // 注意：由于我们现在允许某些边框槽位作为输出，我们需要更智能的清理
                // 或者简单地：清理所有非玩家物品 (玻璃板)
                for (int i = 0; i < inv.getSize(); i++) {
                    ItemStack is = inv.getItem(i);
                    if (is != null && (is.getType() == org.bukkit.Material.BLACK_STAINED_GLASS_PANE || 
                                       is.getType() == org.bukkit.Material.LIME_STAINED_GLASS_PANE ||
                                       is.getType() == org.bukkit.Material.YELLOW_STAINED_GLASS_PANE ||
                                       is.getType() == org.bukkit.Material.RED_STAINED_GLASS_PANE)) {
                        inv.setItem(i, null);
                    }
                }
            }
            
            event.setDropItems(true); // 让原版掉落其余部分 (输入/输出)
            manager.removeSluice(block.getLocation());
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inv = event.getInventory(); // 顶部库存
        InventoryHolder holder = inv.getHolder();

        // Fix: InventoryHolder for Barrel might be a BlockState (CraftBarrel) or DoubleChest etc.
        // Usually for Barrel it is org.bukkit.block.Barrel which extends BlockState
        
        if (holder instanceof BlockState) {
            Block block = ((BlockState) holder).getBlock();
            if (manager.isSluice(block)) {
                // 检查是否在洗矿机库存中点击
                if (event.getClickedInventory() == inv) {
                    int slot = event.getSlot();
                    
                    // 如果点击的是边框槽位
                    if (SluiceManager.BORDER_SLOTS.contains(slot)) {
                         int tier = 1;
                         if (holder instanceof Barrel) {
                              tier = ((Barrel)holder).getPersistentDataContainer().getOrDefault(Keys.SLUICE_TIER, PersistentDataType.INTEGER, 1);
                         }
                         
                         // 使用 Manager 获取当前等级的有效输出槽
                         java.util.List<Integer> validOutputs = manager.getOutputSlotsForTier(tier);
                         
                         if (!validOutputs.contains(slot)) {
                             // 它是真正的边框，不可交互
                             event.setCancelled(true);
                         } else {
                             // 它是输出槽，允许点击（取出）
                             // 但为了安全，防止玩家拿走意外残留的玻璃板
                             ItemStack current = event.getCurrentItem();
                             if (current != null && current.getType() == org.bukkit.Material.BLACK_STAINED_GLASS_PANE) {
                                 event.setCancelled(true);
                             }
                         }
                    }
                    
                    // 进度条/开始按钮逻辑
                    if (slot == SluiceManager.SLOT_PROGRESS) {
                        event.setCancelled(true);
                        
                        // 点击开始按钮逻辑
                        if (holder instanceof Barrel) {
                            Barrel barrel = (Barrel) holder;
                            PersistentDataContainer pdc = barrel.getPersistentDataContainer();
                            
                            // 使用 Manager 的 Session 状态判断是否运行中
                            if (!manager.isProcessing(barrel.getLocation())) {
                                if (manager.canProcess(inv)) {
                                    // startProcessing 现在内部会更新 Session 并刷新 GUI
                                    manager.startProcessing(barrel, inv, pdc);
                                    
                                    if (event.getWhoClicked() instanceof org.bukkit.entity.Player player) {
                                        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
                                        // 强制刷新客户端库存视图
                                        player.updateInventory();
                                    }
                                } else {
                                    if (event.getWhoClicked() instanceof org.bukkit.entity.Player player) {
                                        String msg = StarryForge.getInstance().getConfigManager().getMessage("sluice.missing_material");
                                        player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(msg));
                                        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
                                    }
                                }
                            } else {
                                if (event.getWhoClicked() instanceof org.bukkit.entity.Player player) {
                                    String msg = StarryForge.getInstance().getConfigManager().getMessage("sluice.already_running");
                                    player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(msg));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory inv = event.getInventory();
        InventoryHolder holder = inv.getHolder();
        
        if (holder instanceof BlockState) {
            Block block = ((BlockState) holder).getBlock();
            if (manager.isSluice(block)) {
                for (int slot : event.getRawSlots()) {
                    if (slot < inv.getSize()) { // 顶部库存
                        if (SluiceManager.BORDER_SLOTS.contains(slot) || slot == SluiceManager.SLOT_PROGRESS) {
                            event.setCancelled(true);
                            return;
                        }
                    }
                }
            }
        }
    }
}
