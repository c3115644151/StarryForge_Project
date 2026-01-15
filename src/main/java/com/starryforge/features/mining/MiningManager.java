package com.starryforge.features.mining;

import com.starryforge.StarryForge;
import com.starryforge.features.core.AntiExploitListener;
import com.starryforge.features.core.NoiseManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Random;

public class MiningManager {

    private final StarryForge plugin;
    private final NoiseManager noiseManager;
    private final Random random = new Random();

    public MiningManager(StarryForge plugin, NoiseManager noiseManager) {
        this.plugin = plugin;
        this.noiseManager = noiseManager;
    }

    public void handleBlockBreak(Player player, Block block, ItemStack handItem) {
        // 1. 判断是否是有效矿物/岩石
        if (!isValidMiningTarget(block.getType())) return;

        // 2. 防刷机制检查 (必须是自然方块)
        if (!AntiExploitListener.isNatural(block)) return;

        // 3. 检查附魔 "岩层勘探"
        int enchantLevel = LithicInsightEnchantment.getLevel(handItem);
        // Fallback to lore if PDC missing (old items)
        if (enchantLevel == 0) enchantLevel = getLithicInsightLevel(handItem);
        
        if (enchantLevel == 0) return; 

        // 4. 计算基础掉落概率
        double baseChance = plugin.getConfigManager().getDouble("mining.base_chance", 0.10);
        double enchantBonus = plugin.getConfigManager().getDouble("mining.enchant_bonus", 0.05);
        double chance = baseChance + (enchantLevel - 1) * enchantBonus;
        
        // 5. 方块类型修正
        if (isRock(block.getType())) {
            double rockPenalty = plugin.getConfigManager().getDouble("mining.rock_penalty", 0.1);
            chance *= rockPenalty;
        }

        // 6. 地质谐振仪加成
        if (plugin.getResonatorManager().isPlayerResonating(player)) {
            double potency = noiseManager.getRawPotency(block.getX(), block.getY(), block.getZ());
            double threshold = plugin.getConfigManager().getDouble("mining.resonator_threshold", 0.8);
            if (potency > threshold) {
                double resonatorBonus = plugin.getConfigManager().getDouble("mining.resonator_bonus", 1.5);
                chance *= resonatorBonus; 
            }
        }

        // 7. 随机判定
        if (random.nextDouble() > chance) return;

        // 8. 获取群系类型 (Via BiomeGifts API)
        int biomeType = getBiomeType(block);
        boolean isRich = (biomeType == 1);
        boolean isPoor = (biomeType == -1);

        // 9. 计算矿簇星级 (1-5)
        int quality = noiseManager.calculateClusterQuality(
            block.getX(), block.getY(), block.getZ(), 
            isRich, isPoor
        );
        int stars = Math.min(5, (quality / 20) + 1);

        // 10. 掉落矿簇
        ItemStack cluster = plugin.getItemManager().createUnidentifiedCluster(stars);
        block.getWorld().dropItemNaturally(block.getLocation(), cluster);
        
        // 11. 播放反馈
        playClusterDiscoveryFeedback(player, block.getLocation());
    }

    private void playClusterDiscoveryFeedback(Player player, Location location) {
        player.playSound(location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
        player.spawnParticle(Particle.ENCHANT, location.add(0.5, 0.5, 0.5), 10, 0.2, 0.2, 0.2, 0.1);
    }

    public int getBiomeType(Location loc, Material mat) {
        if (plugin.getServer().getPluginManager().isPluginEnabled("BiomeGifts")) {
            try {
                org.bukkit.plugin.Plugin biomeGifts = plugin.getServer().getPluginManager().getPlugin("BiomeGifts");
                Object configManager = biomeGifts.getClass().getMethod("getConfigManager").invoke(biomeGifts);
                Object resourceConfig = configManager.getClass().getMethod("getOreConfig", Material.class).invoke(configManager, mat);
                
                if (resourceConfig != null) {
                    String biomeKey = loc.getWorld().getBiome(loc).getKey().toString();
                    Object biomeTypeEnum = resourceConfig.getClass().getMethod("getBiomeType", String.class).invoke(resourceConfig, biomeKey);
                    
                    String typeName = ((Enum<?>) biomeTypeEnum).name();
                    if ("RICH".equals(typeName)) return 1;
                    if ("POOR".equals(typeName)) return -1;
                }
            } catch (Exception e) {
                if (plugin.getConfigManager().isDebug()) {
                    plugin.getLogger().warning("Error hooking into BiomeGifts: " + e.getMessage());
                }
            }
        }
        return 0;
    }

    public int calculateStars(Location loc, Material mat) {
        int biomeType = getBiomeType(loc, mat);
        boolean isRich = (biomeType == 1);
        boolean isPoor = (biomeType == -1);

        int quality = noiseManager.calculateClusterQuality(
            loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), 
            isRich, isPoor
        );
        return Math.min(5, (quality / 20) + 1);
    }

    private int getBiomeType(Block block) {
        return getBiomeType(block.getLocation(), block.getType());
    }

    private boolean isValidMiningTarget(Material type) {
        return type.name().endsWith("_ORE") || isRock(type);
    }
    
    private boolean isRock(Material type) {
        java.util.List<String> rocks = plugin.getConfigManager().getConfig().getStringList("mining.valid_rocks");
        if (rocks == null || rocks.isEmpty()) {
            return type == Material.STONE || 
                   type == Material.DEEPSLATE || 
                   type == Material.NETHERRACK || 
                   type == Material.END_STONE ||
                   type == Material.TUFF ||
                   type == Material.ANDESITE ||
                   type == Material.DIORITE ||
                   type == Material.GRANITE;
        }
        return rocks.contains(type.name());
    }
    
    private int getLithicInsightLevel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        if (!item.getItemMeta().hasLore()) return 0;
        
        List<Component> lore = item.getItemMeta().lore();
        if (lore == null) return 0;
        
        for (Component line : lore) {
            String text = PlainTextComponentSerializer.plainText().serialize(line);
            // 检查配置中的名称
            String configName = plugin.getConfigManager().getMessage("enchantment.lithic_insight");
            if (text.contains(configName + " III")) return 3;
            if (text.contains(configName + " II")) return 2;
            if (text.contains(configName + " I") || text.contains(configName)) return 1;
            
            // 保留旧版兼容性检查 (可选)
            if (text.contains("岩层勘探 III") || text.contains("Lithic Insight III")) return 3;
            if (text.contains("岩层勘探 II") || text.contains("Lithic Insight II")) return 2;
            if (text.contains("岩层勘探 I") || text.contains("Lithic Insight I")) return 1;
            if (text.contains("岩层勘探")) return 1; 
        }
        return 0;
    }
}