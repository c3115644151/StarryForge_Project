package com.starryforge.features.forging;

import com.starryforge.StarryForge;
import com.starryforge.features.core.ConfigManager;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates score calculation logic for Forging sessions.
 */
public class ForgingScoreCalculator {

    private final List<Integer> materialStars = new ArrayList<>();
    private final List<Double> qteScores = new ArrayList<>();

    public void addMaterialStar(int star) {
        materialStars.add(star);
    }

    public void addQteScore(double score) {
        qteScores.add(score);
    }

    public void restoreLists(List<Integer> stars, List<Double> scores) {
        materialStars.clear();
        if (stars != null)
            materialStars.addAll(stars);

        qteScores.clear();
        if (scores != null)
            qteScores.addAll(scores);
    }

    public double calculateAverageStar() {
        if (materialStars.isEmpty())
            return 0.0;
        return materialStars.stream().mapToInt(Integer::intValue).average().orElse(0.0);
    }

    public double calculateAverageQteScore() {
        return qteScores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    public double calculateLevelBonus(Player player) {
        // TODO: Implement Forging Level scaling properly
        return 0.0;
    }

    public double calculateModifier(Player player) {
        double avgQteScore = calculateAverageQteScore();

        // Baseline: (Score - 0.5)
        // Bonus Cap: +10% (+0.10) + Level Bonus
        // Penalty Cap: -50% (-0.50)
        double baseMod = avgQteScore - 0.5;

        ConfigManager config = StarryForge.getInstance().getConfigManager();
        double maxBonusBase = config.getDouble("machines.astral_altar.quality.max_bonus_percent", 0.10);
        double penaltyBase = -config.getDouble("machines.astral_altar.quality.penalty_base", 0.50);

        double levelBonus = player != null ? calculateLevelBonus(player) : 0.0;
        double maxBonus = maxBonusBase + levelBonus;

        double modifier = baseMod;
        if (modifier > maxBonus)
            modifier = maxBonus;
        if (modifier < penaltyBase)
            modifier = penaltyBase;

        return modifier;
    }

    public int calculateFinalStar(double modifier) {
        double avgMaterialStar = calculateAverageStar();
        double rawFinalStar = avgMaterialStar * (1.0 + modifier);
        int finalStar;

        if (modifier < 0) {
            finalStar = (int) Math.floor(rawFinalStar);
        } else {
            finalStar = (int) Math.round(rawFinalStar);
        }
        if (finalStar < 1)
            finalStar = 1; // Min 1 star
        return finalStar;
    }

    public List<Integer> getMaterialStars() {
        return materialStars;
    }

    public List<Double> getQteScores() {
        return qteScores;
    }

    public void clear() {
        materialStars.clear();
        qteScores.clear();
    }
}
