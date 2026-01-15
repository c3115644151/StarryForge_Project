package com.starryforge.features.multiblock;

import com.starryforge.StarryForge;
import com.starryforge.utils.LogUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;

/**
 * 管理多方块结构的定义和检测。
 */
public class MultiBlockManager {
    private final StarryForge plugin;
    private final Map<String, StructurePattern> patterns = new HashMap<>();
    private final MiniMessage mm = MiniMessage.miniMessage();

    public MultiBlockManager(StarryForge plugin) {
        this.plugin = plugin;
        registerPatterns();
    }

    public void reloadPatterns() {
        patterns.clear();
        registerPatterns();
        LogUtil.debug("Multiblock patterns reloaded. Total: " + patterns.size());
    }

    private void registerPatterns() {
        // 确保先保存默认配置，以防文件不存在
        plugin.saveDefaultConfig();
        // 强制重新从磁盘读取配置
        plugin.reloadConfig();

        org.bukkit.configuration.ConfigurationSection config = plugin.getConfig()
                .getConfigurationSection("multiblocks");
        if (config == null) {
            plugin.getLogger().warning("No 'multiblocks' section found in config!");
            return;
        }

        for (String key : config.getKeys(false)) {
            try {
                org.bukkit.configuration.ConfigurationSection section = config.getConfigurationSection(key);
                if (section == null)
                    continue;

                // 1. 读取 Core Offset
                String[] offsetParts = section.getString("core_offset", "0,0,0").split(",");
                Vector coreOffset = new Vector(
                        Integer.parseInt(offsetParts[0].trim()),
                        Integer.parseInt(offsetParts[1].trim()),
                        Integer.parseInt(offsetParts[2].trim()));

                // 2. 读取 Layers
                java.util.List<?> rawLayers = section.getList("layers");
                if (rawLayers == null || rawLayers.isEmpty())
                    continue;

                int height = rawLayers.size();
                int depth = ((java.util.List<?>) rawLayers.get(0)).size();
                // 假设是矩形
                String[][] layers = new String[height][depth];

                for (int y = 0; y < height; y++) {
                    java.util.List<?> rowList = (java.util.List<?>) rawLayers.get(y);
                    for (int z = 0; z < depth; z++) {
                        layers[y][z] = (String) rowList.get(z);
                    }
                }

                StructurePattern pattern = new StructurePattern(layers, coreOffset);

                // 3. 读取 Legend
                org.bukkit.configuration.ConfigurationSection legend = section.getConfigurationSection("legend");
                if (legend != null) {
                    for (String symbolKey : legend.getKeys(false)) {
                        if (symbolKey.length() == 0)
                            continue;
                        char symbol = symbolKey.charAt(0);
                        // 特殊处理空格 key? YAML 可能会修剪。
                        // 通常建议使用特殊字符如 '_' 代表空气，如果 ' ' 不好用。
                        // 但这里我们支持 " " 作为 key (需要引号 in YAML)

                        String matName = legend.getString(symbolKey);
                        if (matName == null)
                            continue;

                        Material mat = Material.matchMaterial(matName);
                        if (mat != null) {
                            pattern.addMatcher(symbol, mat);
                        } else if (matName.equalsIgnoreCase("AIR")) {
                            pattern.addMatcher(symbol, Material.AIR);
                        } else {
                            plugin.getLogger().warning("Unknown material in multiblock " + key + ": " + matName);
                        }
                    }
                }

                patterns.put(key, pattern);
                LogUtil.debug("Loaded multiblock pattern: " + key);

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to load multiblock pattern: " + key);
                e.printStackTrace();
            }
        }
    }

    /**
     * 检查指定核心方块周围是否存在有效的结构。
     * 尝试所有4个基本方向。
     * 
     * @param coreBlock 核心方块 (例如: 高炉)
     * @param patternId 要检查的结构模式ID
     * @return 如果找到结构，返回结构的方向 BlockFace；否则返回 null
     */
    public BlockFace checkStructure(Block coreBlock, String patternId) {
        StructurePattern pattern = patterns.get(patternId);
        if (pattern == null)
            return null;

        BlockFace[] faces = { BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST };
        for (BlockFace face : faces) {
            if (pattern.check(coreBlock, face)) {
                return face;
            }
        }
        return null;
    }

    public java.util.List<Block> getStructureBlocks(Block coreBlock, String patternId, BlockFace face) {
        StructurePattern pattern = patterns.get(patternId);
        if (pattern == null)
            return new java.util.ArrayList<>();
        return pattern.getStructureBlocks(coreBlock, face);
    }

    public void debugStructure(Player player, Block coreBlock, String patternId) {
        StructurePattern pattern = patterns.get(patternId);
        if (pattern == null) {
            String msg = plugin.getConfigManager().getMessage("multiblock.unknown_id").replace("{id}", patternId);
            player.sendMessage(mm.deserialize(msg));
            return;
        }

        BlockFace[] faces = { BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST };
        Map<Block, String> bestMismatches = null;
        BlockFace bestFace = BlockFace.NORTH;
        int minMismatchCount = Integer.MAX_VALUE;

        // 寻找错误最少的旋转方向
        for (BlockFace face : faces) {
            Map<Block, String> mismatches = pattern.analyze(coreBlock, face);
            if (mismatches.size() < minMismatchCount) {
                minMismatchCount = mismatches.size();
                bestMismatches = mismatches;
                bestFace = face;
            }
        }

        if (bestMismatches != null && !bestMismatches.isEmpty()) {
            String incompleteMsg = plugin.getConfigManager().getMessage("multiblock.incomplete").replace("{face}",
                    bestFace.name());
            player.sendMessage(mm.deserialize(incompleteMsg));
            player.sendMessage(mm.deserialize(plugin.getConfigManager().getMessage("multiblock.particle_hint")));

            int count = 0;
            for (Map.Entry<Block, String> entry : bestMismatches.entrySet()) {
                Block block = entry.getKey();
                String expected = entry.getValue();

                // 生成粒子
                block.getWorld().spawnParticle(Particle.DUST, block.getLocation().add(0.5, 0.5, 0.5),
                        10, 0.2, 0.2, 0.2, 0, new Particle.DustOptions(Color.RED, 1.0f));

                if (count < 5) {
                    String blockError = plugin.getConfigManager().getMessage("multiblock.block_error")
                            .replace("{x}", String.valueOf(block.getX()))
                            .replace("{y}", String.valueOf(block.getY()))
                            .replace("{z}", String.valueOf(block.getZ()))
                            .replace("{expected}", expected);
                    player.sendMessage(mm.deserialize(blockError));
                }
                count++;
            }
            if (count > 5) {
                String moreErrors = plugin.getConfigManager().getMessage("multiblock.more_errors")
                        .replace("{count}", String.valueOf(count - 5));
                player.sendMessage(mm.deserialize(moreErrors));
            }
        } else {
            player.sendMessage(mm.deserialize(plugin.getConfigManager().getMessage("multiblock.complete")));
        }
    }

    public StructurePattern getPattern(String id) {
        return patterns.get(id);
    }
}
