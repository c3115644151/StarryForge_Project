package com.starryforge.features.core;

import org.bukkit.util.noise.PerlinNoiseGenerator;
import java.util.Random;

public class NoiseManager {

    // private final long worldSeed;
    private final PerlinNoiseGenerator generator;
    private static final double SCALE = 0.005; // 频率：控制矿脉大小
    // private static final double THRESHOLD = 0.85; // 阈值：控制稀缺度 (> 0.85 约为 5%)
    
    // 二次噪声用于制造更自然的边缘抖动，避免矿脉边缘过于平滑
    private static final double DETAIL_SCALE = 0.05; 

    public NoiseManager(long seed) {
        // this.worldSeed = seed;
        this.generator = new PerlinNoiseGenerator(new Random(seed));
    }

    /**
     * 获取原始噪声潜力 (0.0 - 1.0)
     * @param x 坐标X
     * @param y 坐标Y
     * @param z 坐标Z
     * @return 归一化后的噪声值
     */
    public double getRawPotency(int x, int y, int z) {
        // 使用 3D 噪声
        double baseNoise = generator.noise(x * SCALE, y * SCALE, z * SCALE);
        
        // 添加细节噪声
        double detailNoise = generator.noise(x * DETAIL_SCALE, y * DETAIL_SCALE, z * DETAIL_SCALE) * 0.1;
        
        double finalNoise = baseNoise + detailNoise;

        // 归一化到 0.0 - 1.0 (Perlin通常输出 -1 到 1)
        double normalized = (finalNoise + 1.0) / 2.0;
        
        // 钳制到 0-1 防止溢出
        return Math.max(0.0, Math.min(1.0, normalized));
    }

    /**
     * 获取矿簇的品质 (0-100)
     * @param x
     * @param y
     * @param z
     * @param isRichBiome 是否在富集群系
     * @param isPoorBiome 是否在贫瘠群系
     * @return 矿簇品质 (0-100)
     */
    public int calculateClusterQuality(int x, int y, int z, boolean isRichBiome, boolean isPoorBiome) {
        double potency = getRawPotency(x, y, z);
        Random random = new Random();

        // 贫瘠区
        if (isPoorBiome) {
            if (potency < 0.3) return randomRange(random, 10, 30);
            if (potency < 0.7) return randomRange(random, 20, 40);
            return randomRange(random, 40, 60);
        }
        
        // 富集区
        if (isRichBiome) {
            if (potency < 0.3) return randomRange(random, 40, 60);
            if (potency < 0.7) return randomRange(random, 60, 80);
            return randomRange(random, 80, 100);
        }

        // 普通区
        if (potency < 0.3) return randomRange(random, 20, 40);
        if (potency < 0.7) return randomRange(random, 40, 60);
        return randomRange(random, 60, 80);
    }
    
    private int randomRange(Random random, int min, int max) {
        return min + random.nextInt(max - min + 1);
    }
}
