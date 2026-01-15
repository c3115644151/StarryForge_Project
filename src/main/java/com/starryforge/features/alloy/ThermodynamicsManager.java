package com.starryforge.features.alloy;

import com.starryforge.StarryForge;
import com.starryforge.features.core.PDCManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public class ThermodynamicsManager {

    private final StarryForge plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public ThermodynamicsManager(StarryForge plugin) {
        this.plugin = plugin;
        startTemperatureTask();
    }

    private void startTemperatureTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    processPlayerInventory(player);
                }
                // Also process ground items in loaded chunks
                processGroundItems();
            }
        }.runTaskTimer(plugin, 20L, 20L); // 每秒运行一次
    }

    private void processGroundItems() {
        double ambientTemp = plugin.getConfigManager().getDouble("thermodynamics.ambient_temp", 20.0);
        double coolingRate = plugin.getConfigManager().getDouble("thermodynamics.cooling_rate", 0.05);
        double waterCoolingRate = plugin.getConfigManager().getDouble("thermodynamics.water_cooling_rate", 0.5);

        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity entity : world.getEntities()) {
                if (entity instanceof org.bukkit.entity.Item itemEntity) {
                    ItemStack item = itemEntity.getItemStack();
                    if (PDCManager.hasTemperature(item)) {
                        double temp = PDCManager.getTemperature(item);
                        if (temp > ambientTemp) {
                            double effectiveRate = coolingRate;
                            // Check if in water
                            if (itemEntity.isInWater()) {
                                effectiveRate = waterCoolingRate;
                                if (temp > 100.0) {
                                    world.spawnParticle(org.bukkit.Particle.CAMPFIRE_COSY_SMOKE,
                                            itemEntity.getLocation().add(0, 0.5, 0), 2, 0, 0, 0, 0.05);
                                    // Play sound occasionally to avoid spam? Task runs every 1s (20 ticks).
                                    // Sound is fine every second.
                                    world.playSound(itemEntity.getLocation(),
                                            org.bukkit.Sound.ENTITY_GENERIC_EXTINGUISH_FIRE, 0.5f, 1.0f);
                                }
                            }

                            double diff = temp - ambientTemp;
                            double newTemp = ambientTemp + diff * (1.0 - effectiveRate);

                            if (newTemp < ambientTemp + 0.1) {
                                newTemp = ambientTemp;
                            }

                            PDCManager.setTemperature(item, newTemp);
                            updateItemLore(item, newTemp);
                            itemEntity.setItemStack(item);
                        }
                    }
                }
            }
        }
    }

    private void processPlayerInventory(Player player) {
        double maxTemp = 0;
        boolean burning = false;

        double ambientTemp = plugin.getConfigManager().getDouble("thermodynamics.ambient_temp", 20.0);
        double coolingRate = plugin.getConfigManager().getDouble("thermodynamics.cooling_rate", 0.05);
        double burnThreshold = plugin.getConfigManager().getDouble("thermodynamics.burn_threshold", 100.0);
        double damageCap = plugin.getConfigManager().getDouble("thermodynamics.damage_cap", 10.0);

        // 检查所有物品栏槽位，包括盔甲和副手
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType().isAir())
                continue;

            if (PDCManager.hasTemperature(item)) {
                double temp = PDCManager.getTemperature(item);

                // 自然冷却逻辑
                if (temp > ambientTemp) {
                    double diff = temp - ambientTemp;
                    // 牛顿冷却定律近似
                    double newTemp = ambientTemp + diff * (1.0 - coolingRate);

                    // 如果非常接近环境温度则停止冷却
                    if (newTemp < ambientTemp + 0.1) {
                        newTemp = ambientTemp;
                    }

                    // 更新物品数据和 Lore
                    PDCManager.setTemperature(item, newTemp);
                    updateItemLore(item, newTemp);

                    // 玩家灼烧检查
                    if (temp > burnThreshold) {
                        burning = true;
                        if (temp > maxTemp)
                            maxTemp = temp;
                    }
                }
            }
        }

        if (burning) {
            // 伤害随温度增加
            // 100C = 0 额外伤害 (1.0 基础)
            // 1000C = 9.0 额外伤害 -> 10.0 总计 (5 颗心)
            double damage = 1.0 + (maxTemp - burnThreshold) / 100.0;
            player.damage(Math.min(damage, damageCap));
            player.sendActionBar(mm.deserialize(plugin.getConfigManager().getMessage("thermodynamics.burn_warning")));
        }
    }

    public void updateItemLore(ItemStack item, double temp) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return;
        List<Component> lore = meta.lore();
        if (lore == null)
            lore = new ArrayList<>();

        String prefix = plugin.getConfigManager().getMessage("thermodynamics.lore_temp_prefix");
        double ambientTemp = plugin.getConfigManager().getDouble("thermodynamics.ambient_temp", 20.0);

        // 从前缀中移除颜色以便进行纯文本搜索
        String plainPrefix = PlainTextComponentSerializer.plainText().serialize(mm.deserialize(prefix));
        PlainTextComponentSerializer serializer = PlainTextComponentSerializer.plainText();

        int foundIndex = -1;
        for (int i = 0; i < lore.size(); i++) {
            String text = serializer.serialize(lore.get(i));
            if (text.startsWith(plainPrefix.trim())) { // 使用 trim 以确保安全
                foundIndex = i;
                break;
            }
        }

        // 如果温度降到 20 度或以下，移除 Lore
        if (temp <= ambientTemp) {
            if (foundIndex != -1) {
                lore.remove(foundIndex);
                meta.lore(lore.isEmpty() ? null : lore);
                item.setItemMeta(meta);
            }
            return;
        }

        // 否则，更新或添加 Lore
        String format = plugin.getConfigManager().getMessage("thermodynamics.temp_format");
        Component tempLine = mm.deserialize(prefix)
                .append(Component.text(String.format(format, temp), getTempColor(temp)));

        if (foundIndex != -1) {
            lore.set(foundIndex, tempLine);
        } else {
            lore.add(tempLine);
        }

        meta.lore(lore);
        item.setItemMeta(meta);
    }

    private NamedTextColor getTempColor(double temp) {
        if (temp < 100)
            return NamedTextColor.WHITE;
        if (temp < 300)
            return NamedTextColor.YELLOW;
        if (temp < 800)
            return NamedTextColor.GOLD;
        return NamedTextColor.RED;
    }
}
