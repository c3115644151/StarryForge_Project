package com.starryforge.features.multiblock;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 表示一个多方块结构模式。
 * 支持 3D 字符矩阵定义和旋转检测。
 */
public class StructurePattern {
    private final String[][] layers; // [y][z] -> x 是字符索引
    private final Map<Character, Predicate<Block>> matchers = new HashMap<>();
    private final Map<Character, String> matcherDescriptions = new HashMap<>();
    private final Vector coreOffset; // 核心方块在模式中的相对位置 (x, y, z)

    public StructurePattern(String[][] layers, Vector coreOffset) {
        this.layers = layers;
        this.coreOffset = coreOffset;
        // 默认的空气匹配器
        matchers.put(' ', block -> block.getType() == Material.AIR);
        matcherDescriptions.put(' ', "AIR");
    }

    public StructurePattern addMatcher(char symbol, Material material) {
        matchers.put(symbol, block -> block.getType() == material);
        matcherDescriptions.put(symbol, material.name());
        return this;
    }
    
    public StructurePattern addMatcher(char symbol, Predicate<Block> predicate, String description) {
        matchers.put(symbol, predicate);
        matcherDescriptions.put(symbol, description);
        return this;
    }

    /**
     * 分析结构并返回不匹配方块（世界坐标）及其预期描述的映射。
     */
    public Map<Block, String> analyze(Block coreBlock, BlockFace facing) {
        Map<Block, String> mismatches = new HashMap<>();
        int height = layers.length;
        int depth = layers[0].length;
        int width = layers[0][0].length();

        for (int py = 0; py < height; py++) {
            for (int pz = 0; pz < depth; pz++) {
                String row = layers[py][pz];
                for (int px = 0; px < width; px++) {
                    char symbol = row.charAt(px);
                    Predicate<Block> matcher = matchers.get(symbol);
                    if (matcher == null) continue;
                    
                    int relX = px - coreOffset.getBlockX();
                    int relY = py - coreOffset.getBlockY();
                    int relZ = pz - coreOffset.getBlockZ();
                    
                    Vector worldOffset = rotate(relX, relY, relZ, facing);
                    Block targetBlock = coreBlock.getRelative(worldOffset.getBlockX(), worldOffset.getBlockY(), worldOffset.getBlockZ());
                    
                    if (!matcher.test(targetBlock)) {
                        mismatches.put(targetBlock, matcherDescriptions.getOrDefault(symbol, "Unknown"));
                    }
                }
            }
        }
        return mismatches;
    }

    public boolean check(Block coreBlock, BlockFace facing) {
        // 计算原点（基于旋转的左前下角），基于 coreOffset
        // 但更简单的方法是：相对于 coreBlock 进行迭代
        
        // 1. 根据 BlockFace 确定旋转矩阵/变换
        // 标准定义通常是朝向 NORTH (-Z)
        
        int height = layers.length;
        int depth = layers[0].length;
        int width = layers[0][0].length(); // 假设所有字符串长度相同

        // 我们遍历模式坐标 (px, py, pz)
        // 并根据 'facing' 将它们映射到世界坐标
        
        for (int py = 0; py < height; py++) {
            for (int pz = 0; pz < depth; pz++) {
                String row = layers[py][pz];
                for (int px = 0; px < width; px++) {
                    char symbol = row.charAt(px);
                    Predicate<Block> matcher = matchers.get(symbol);
                    if (matcher == null) continue; // 跳过未定义的符号（或视为任意？） -> 最好严格
                    
                    // 计算模式空间中相对于核心方块的偏移
                    int relX = px - coreOffset.getBlockX();
                    int relY = py - coreOffset.getBlockY();
                    int relZ = pz - coreOffset.getBlockZ();
                    
                    // 根据朝向将相对偏移转换为世界偏移
                    Vector worldOffset = rotate(relX, relY, relZ, facing);
                    
                    Block targetBlock = coreBlock.getRelative(worldOffset.getBlockX(), worldOffset.getBlockY(), worldOffset.getBlockZ());
                    
                    if (!matcher.test(targetBlock)) {
                        return false;
                    }
                }
            }
        }
        
        return true;
    }

    /**
     * 获取结构包含的所有方块列表。
     */
    public java.util.List<Block> getStructureBlocks(Block coreBlock, BlockFace facing) {
        java.util.List<Block> blocks = new java.util.ArrayList<>();
        int height = layers.length;
        int depth = layers[0].length;
        int width = layers[0][0].length();

        for (int py = 0; py < height; py++) {
            for (int pz = 0; pz < depth; pz++) {
                String row = layers[py][pz];
                for (int px = 0; px < width; px++) {
                    char symbol = row.charAt(px);
                    // 忽略空气或者未定义的符号
                    if (matchers.containsKey(symbol) && symbol != ' ') { 
                        int relX = px - coreOffset.getBlockX();
                        int relY = py - coreOffset.getBlockY();
                        int relZ = pz - coreOffset.getBlockZ();
                        Vector worldOffset = rotate(relX, relY, relZ, facing);
                        blocks.add(coreBlock.getRelative(worldOffset.getBlockX(), worldOffset.getBlockY(), worldOffset.getBlockZ()));
                    }
                }
            }
        }
        return blocks;
    }

    /**
     * 根据朝向旋转相对向量。
     * 假设模式是面向 NORTH 定义的。
     */
    private Vector rotate(int x, int y, int z, BlockFace facing) {
        // 模式定义约定：
        // X+ 是东 (East)，X- 是西 (West)
        // Z+ 是南 (South)，Z- 是北 (North) (通常模式是看着它定义的？让我们标准化)
        // 让我们假设标准 Minecraft 坐标：
        // 模式网格：
        // x 向东增加
        // z 向南增加
        // 所以 3x3 网格：
        // 0,0 是西北角
        // 2,2 是东南角
        
        // 如果我们面向 NORTH：无旋转
        // 如果我们面向 EAST：绕 Y 轴旋转 -90 度
        // 如果我们面向 SOUTH：绕 Y 轴旋转 180 度
        // 如果我们面向 WEST：绕 Y 轴旋转 90 度
        
        switch (facing) {
            case NORTH:
                return new Vector(x, y, z);
            case EAST:
                return new Vector(-z, y, x);
            case SOUTH:
                return new Vector(-x, y, -z);
            case WEST:
                return new Vector(z, y, -x);
            default:
                return new Vector(x, y, z);
        }
    }
}
