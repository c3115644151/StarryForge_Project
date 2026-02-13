package com.starryforge.features.ironheart.data.model;

public record VeteranStats(
    double totalDamage,
    int killCount,
    int rank // 0=Recruit, 1=Veteran, 2=Legendary
) {}
