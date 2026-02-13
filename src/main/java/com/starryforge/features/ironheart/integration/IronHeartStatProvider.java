package com.starryforge.features.ironheart.integration;

import com.nexuscore.rpg.stats.NexusStat;
import com.nexuscore.rpg.provider.NexusStatProvider;
import com.starryforge.features.ironheart.data.PDCAdapter;
import com.starryforge.features.ironheart.data.model.IronHeartWeapon;
import org.bukkit.inventory.ItemStack;

public class IronHeartStatProvider implements NexusStatProvider {

    @Override
    public String getNamespace() {
        return "starryforge";
    }

    @Override
    public double getStat(ItemStack item, NexusStat stat) {
        if (item == null || !item.hasItemMeta()) return 0;

        IronHeartWeapon weapon = PDCAdapter.readWeaponData(item);
        if (weapon == null) return 0;

        return switch (stat) {
            case ATTACK_DAMAGE -> weapon.statsCache().damage();
            case ATTACK_SPEED -> weapon.statsCache().speed(); // NexusCore usually expects total value or modifier? Assuming total.
            case ATTACK_RANGE -> weapon.statsCache().reach();
            default -> 0;
        };
    }
}
