package com.starryforge.features.ironheart.data.model;

import java.util.List;

public record WeaponComponent(
    String id,
    ComponentType type,
    String name,
    String itemId,
    int customModelData,
    ComponentStats stats,
    ComponentRequirements requirements,
    List<String> abilities
) {
    public enum ComponentType {
        HEAD, SPINE, GRIP, WEIGHT, GUARD, SHAFT
    }

    public record ComponentStats(
        double damage,
        double speed,
        double reach,
        int integrityProvider, // For HEAD/SPINE
        int integrityCost      // For others
    ) {}

    public record ComponentRequirements(
        int forgeLevel
    ) {}
}
