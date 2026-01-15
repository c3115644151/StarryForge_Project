package com.starryforge.features.core;

import com.starryforge.StarryForge;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class DebugStickManager implements Listener {

    private final StarryForge plugin;
    private final NamespacedKey debugKey;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public DebugStickManager(StarryForge plugin) {
        this.plugin = plugin;
        this.debugKey = new NamespacedKey(plugin, "debug_stick");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public ItemStack getDebugStick() {
        ItemStack stick = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = stick.getItemMeta();

        String name = plugin.getConfigManager().getMessage("debug.name");
        meta.displayName(mm.deserialize(name));

        List<String> loreList = plugin.getConfigManager().getMessageList("debug.lore");
        List<Component> lore = new ArrayList<>();
        for (String line : loreList) {
            lore.add(mm.deserialize(line));
        }

        meta.lore(lore);
        meta.getPersistentDataContainer().set(debugKey, PersistentDataType.BYTE, (byte) 1);
        stick.setItemMeta(meta);
        return stick;
    }

    public void giveDebugStick(Player player) {
        player.getInventory().addItem(getDebugStick());
        player.sendMessage(mm.deserialize(plugin.getConfigManager().getMessage("debug.given")));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // 检查是否持有调试棒
        if (item == null || item.getType() == Material.AIR)
            return;
        if (!item.hasItemMeta())
            return;
        if (!item.getItemMeta().getPersistentDataContainer().has(debugKey, PersistentDataType.BYTE))
            return;

        // 权限检查
        if (!player.hasPermission("starryforge.debug")) {
            player.sendMessage(mm.deserialize(plugin.getConfigManager().getMessage("debug.no_permission")));
            return;
        }

        event.setCancelled(true);

        // 左键方块: 查看方块信息
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            Block block = event.getClickedBlock();
            if (block != null) {
                player.sendMessage(mm.deserialize(plugin.getConfigManager().getMessage("debug.block_info_header")));

                String typeMsg = plugin.getConfigManager().getMessage("debug.block_type")
                        .replace("{type}", block.getType().toString());
                player.sendMessage(mm.deserialize(typeMsg));

                String locMsg = plugin.getConfigManager().getMessage("debug.block_location")
                        .replace("{x}", String.valueOf(block.getX()))
                        .replace("{y}", String.valueOf(block.getY()))
                        .replace("{z}", String.valueOf(block.getZ()));
                player.sendMessage(mm.deserialize(locMsg));

                // 噪声强度
                double potency = plugin.getNoiseManager().getRawPotency(block.getX(), block.getY(), block.getZ());
                player.sendMessage(mm.deserialize("<gray>当前位置噪声强度: <white>" + String.format("%.4f", potency)));
            }
        }
        // 右键空气/方块: 查看副手物品信息
        else if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack target = player.getInventory().getItemInOffHand();
            if (target != null && target.getType() != Material.AIR) {
                player.sendMessage(mm.deserialize(plugin.getConfigManager().getMessage("debug.offhand_info_header")));

                String typeMsg = plugin.getConfigManager().getMessage("debug.offhand_type")
                        .replace("{type}", target.getType().toString());
                player.sendMessage(mm.deserialize(typeMsg));

                if (target.hasItemMeta()) {
                    ItemMeta meta = target.getItemMeta();
                    if (meta.hasCustomModelData()) {
                        String modelMsg = plugin.getConfigManager().getMessage("debug.offhand_model_data")
                                .replace("{data}", String.valueOf(meta.getCustomModelData()));
                        player.sendMessage(mm.deserialize(modelMsg));
                    }
                    // 转储 PDC 键值
                    player.sendMessage(mm.deserialize(plugin.getConfigManager().getMessage("debug.pdc_header")));
                    meta.getPersistentDataContainer().getKeys().forEach(key -> {
                        // 尝试猜测类型? String, Int, Double...
                        String valStr = "N/A";
                        if (PDCManager.hasKey(target, key)) {
                            // 尝试 String
                            String s = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
                            if (s != null)
                                valStr = s;
                            else {
                                try {
                                    Double d = meta.getPersistentDataContainer().get(key, PersistentDataType.DOUBLE);
                                    if (d != null)
                                        valStr = String.valueOf(d);
                                    else {
                                        Integer i = meta.getPersistentDataContainer().get(key,
                                                PersistentDataType.INTEGER);
                                        if (i != null)
                                            valStr = String.valueOf(i);
                                    }
                                } catch (Exception e) {
                                }
                            }
                        }
                        String entryMsg = plugin.getConfigManager().getMessage("debug.pdc_entry")
                                .replace("{key}", key.toString())
                                .replace("{value}", valStr);
                        player.sendMessage(mm.deserialize(entryMsg));
                    });

                    // 温度特殊检查
                    if (PDCManager.hasTemperature(target)) {
                        String tempMsg = plugin.getConfigManager().getMessage("debug.temperature")
                                .replace("{temp}", String.valueOf(PDCManager.getTemperature(target)));
                        player.sendMessage(mm.deserialize(tempMsg));
                    }
                }
            } else {
                player.sendMessage(mm.deserialize(plugin.getConfigManager().getMessage("debug.offhand_hint")));
            }
        }
    }
}
