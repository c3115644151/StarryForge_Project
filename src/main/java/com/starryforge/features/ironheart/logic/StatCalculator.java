package com.starryforge.features.ironheart.logic;

import com.starryforge.features.ironheart.config.ResonanceConfig;
import com.starryforge.features.ironheart.data.model.IronHeartWeapon;
import com.starryforge.features.ironheart.data.model.QualifiedComponent;
import com.starryforge.features.ironheart.data.model.WeaponComponent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class StatCalculator {

    private final ResonanceConfig resonanceConfig;

    public StatCalculator(ResonanceConfig resonanceConfig) {
        this.resonanceConfig = resonanceConfig;
    }

    public CalculationResult calculate(Collection<QualifiedComponent> components) {
        double totalDamage = 0;
        double totalSpeed = 0;
        double totalReach = 0;

        // 1. Sum Component Stats (With Quality Multiplier)
        for (QualifiedComponent qc : components) {
            if (qc == null || qc.base() == null) continue;
            
            double multiplier = qc.getMultiplier();
            WeaponComponent comp = qc.base();
            
            // Apply multiplier to positive stats only? Or all stats?
            // Usually negative stats (like speed penalty) should also scale?
            // "Heavier weight" -> More damage, more slowness.
            // So simply multiplying the value works for both.
            
            totalDamage += comp.stats().damage() * multiplier;
            totalSpeed += comp.stats().speed() * multiplier;
            totalReach += comp.stats().reach() * multiplier;
        }

        // 2. Check Resonances
        List<ResonanceConfig.Resonance> activeResonances = new ArrayList<>();
        Set<String> componentIds = components.stream()
                .filter(qc -> qc != null && qc.base() != null)
                .map(qc -> qc.base().id())
                .collect(Collectors.toSet());

        for (ResonanceConfig.Resonance resonance : resonanceConfig.getAllResonances()) {
            if (componentIds.containsAll(resonance.requiredComponents())) {
                activeResonances.add(resonance);
                
                // Apply Resonance Bonus (Resonance is static magic, no quality scaling?)
                // Let's keep resonance static for now.
                totalDamage += resonance.bonusStats().damage();
                totalSpeed += resonance.bonusStats().speed();
                totalReach += resonance.bonusStats().reach();
            }
        }

        // 3. Return result (Cache)
        // poiseDmg defaulted to 0 for now as it's not in ComponentStats yet
        IronHeartWeapon.StatsCache stats = new IronHeartWeapon.StatsCache(totalDamage, totalSpeed, totalReach, 0.0);
        return new CalculationResult(stats, activeResonances);
    }

    public record CalculationResult(
            IronHeartWeapon.StatsCache stats,
            List<ResonanceConfig.Resonance> activeResonances
    ) {}
}
