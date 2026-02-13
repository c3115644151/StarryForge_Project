package com.starryforge.features.ironheart.logic;

import com.starryforge.features.ironheart.data.model.WeaponComponent;

import java.util.Collection;

public class IntegrityValidator {

    public ValidationResult validate(Collection<WeaponComponent> components, int veteranBonus) {
        int maxIntegrity = 0;
        int usedIntegrity = 0;

        for (WeaponComponent comp : components) {
            if (comp == null) continue;
            maxIntegrity += comp.stats().integrityProvider();
            usedIntegrity += comp.stats().integrityCost();
        }

        // Apply veteran bonus (e.g. +1 max integrity)
        maxIntegrity += veteranBonus;

        int remaining = maxIntegrity - usedIntegrity;
        boolean isValid = remaining >= 0;

        return new ValidationResult(isValid, maxIntegrity, usedIntegrity, remaining);
    }

    public record ValidationResult(
            boolean isValid,
            int maxIntegrity,
            int usedIntegrity,
            int remainingIntegrity
    ) {}
}
