package com.starryforge.features.ironheart.data.model;

public record QualifiedComponent(
    WeaponComponent base,
    int quality // 1-5 stars
) {
    public static final double QUALITY_MULTIPLIER_PER_STAR = 0.05; // 5% per star

    public double getMultiplier() {
        // 1 star = 1.0 (Base)
        // 5 stars = 1.2 (120%)
        return 1.0 + (Math.max(0, quality - 1) * QUALITY_MULTIPLIER_PER_STAR);
    }
}
