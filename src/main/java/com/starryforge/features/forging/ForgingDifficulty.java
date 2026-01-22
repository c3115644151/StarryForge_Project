package com.starryforge.features.forging;

import com.starryforge.StarryForge;
import com.starryforge.features.core.ConfigManager;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates difficulty settings and calculation logic for Forging sessions.
 */
public class ForgingDifficulty {

    private long timeLimit;
    private int totalNodes;
    private double rotationSpeed;
    private int interferenceNodes;

    public ForgingDifficulty() {
        // Default values
        this.timeLimit = 20000;
        this.totalNodes = 5;
        this.rotationSpeed = 0.0;
        this.interferenceNodes = 0;
    }

    public void applySettings(int roundsCompleted, int totalRounds) {
        ConfigManager cfg = StarryForge.getInstance().getConfigManager();
        double progressRatio = (double) roundsCompleted / totalRounds;

        // Default tier settings
        int tierNodes = cfg.getInt("machines.astral_altar.process.nodes_per_round", 5);
        long tierTime = cfg.getInt("machines.astral_altar.settings.time_limit_ms", 20000);
        double tierRotation = 0.0;
        int tierInterference = 0;

        List<Map<?, ?>> tiers = cfg.getMapList("machines.astral_altar.difficulty_tiers");
        if (tiers != null && !tiers.isEmpty()) {
            for (Map<?, ?> tier : tiers) {
                double th = 0.0;
                Object thObj = tier.get("threshold");
                if (thObj instanceof Number)
                    th = ((Number) thObj).doubleValue();

                if (progressRatio >= th) {
                    Object nodesObj = tier.get("nodes");
                    if (nodesObj instanceof Number)
                        tierNodes = ((Number) nodesObj).intValue();

                    Object timeObj = tier.get("time_limit_sec");
                    if (timeObj instanceof Number)
                        tierTime = ((Number) timeObj).longValue() * 1000L;

                    Object rotObj = tier.get("rotation_speed");
                    if (rotObj instanceof Number)
                        tierRotation = ((Number) rotObj).doubleValue();

                    Object intObj = tier.get("interference_nodes");
                    if (intObj instanceof Number)
                        tierInterference = ((Number) intObj).intValue();
                }
            }
        }

        this.timeLimit = tierTime;
        this.totalNodes = tierNodes;
        this.rotationSpeed = tierRotation;
        this.interferenceNodes = tierInterference;
    }

    public long getTimeLimit() {
        return timeLimit;
    }

    public int getTotalNodes() {
        return totalNodes;
    }

    public double getRotationSpeed() {
        return rotationSpeed;
    }

    public int getInterferenceNodes() {
        return interferenceNodes;
    }
}
