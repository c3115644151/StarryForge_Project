package com.starryforge.features.ironheart.data.model;

import java.util.Map;

public record IronHeartWeapon(
    String uuid,
    String blueprintId,
    int revision,
    int tier,
    Integrity integrity,
    Map<String, String> components, // Map<ComponentType, ComponentID>
    Map<String, Integer> componentQualities, // Map<ComponentType, StarRating> (1-5)
    StatsCache statsCache,
    History history
) {
    public record Integrity(
        int current,
        int max
    ) {}

    public record StatsCache(
        double damage,
        double speed,
        double reach,
        double poiseDmg
    ) {}

    public record History(
        String crafter,
        long createdAt,
        VeteranStats veteran
    ) {}
}
