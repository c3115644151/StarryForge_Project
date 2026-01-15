package com.starryforge.utils;

import com.starryforge.StarryForge;
import org.bukkit.NamespacedKey;

public final class Keys {

    private Keys() {
        throw new UnsupportedOperationException("Utility class");
    }

    private static NamespacedKey create(String key) {
        return new NamespacedKey(StarryForge.getInstance(), key);
    }

    // Item Management
    // Item Management
    // Delegated to NexusCore for standardization
    public static final String ITEM_ID_KEY_STRING = "sf_item_id"; // Legacy string keep for internal use if needed
    public static final NamespacedKey ITEM_ID_KEY = com.nexuscore.api.NexusKeys.ITEM_ID;
    public static final NamespacedKey CLUSTER_QUALITY_KEY = com.nexuscore.api.NexusKeys.QUALITY;
    public static final NamespacedKey ORE_STAR_KEY = com.nexuscore.api.NexusKeys.STAR_RATING;

    // Sluice Machine
    public static final NamespacedKey SLUICE_MACHINE = create("sf_sluice_machine");
    public static final NamespacedKey SLUICE_TIER = create("sluice_tier");
    public static final NamespacedKey SLUICE_PROCESSING_TIME = create("sf_sluice_time");
    public static final NamespacedKey SLUICE_MAX_TIME = create("sf_sluice_max_time");
    public static final NamespacedKey SLUICE_QUALITY = create("sf_sluice_quality");
    public static final NamespacedKey SLUICE_HAS_SOLVENT = create("sf_sluice_solvent");

    // Enchantments
    public static final NamespacedKey ENCHANTMENT_LITHIC_INSIGHT = create("lithic_insight");

    // Thermodynamics & Forging
    public static final NamespacedKey TEMPERATURE_KEY = create("sf_temperature");
    public static final NamespacedKey MAX_TEMPERATURE_KEY = create("sf_max_temperature");
    public static final NamespacedKey FORGE_STAGE_KEY = create("sf_forge_stage");

    // Alloy Machine Persistence
    public static final NamespacedKey ALLOY_OUTPUT_ITEM = create("sf_alloy_output_item");
    public static final NamespacedKey ALLOY_OUTPUT_TIME = create("sf_alloy_output_time");
    public static final NamespacedKey ALLOY_INPUT_0 = create("sf_alloy_input_0");
    public static final NamespacedKey ALLOY_INPUT_1 = create("sf_alloy_input_1");
    public static final NamespacedKey ALLOY_INPUT_2 = create("sf_alloy_input_2");
    public static final NamespacedKey ALLOY_FLUX_ITEM = create("sf_alloy_flux");

    // Mineral Slag
    public static final NamespacedKey SLAG_TARGET_ITEM = create("sf_slag_target");
    public static final NamespacedKey SLAG_SPECIAL_ORE_TYPE = create("sf_slag_ore_type");
    public static final NamespacedKey SLAG_SPECIAL_ORE_STARS = create("sf_slag_ore_stars");
    public static final NamespacedKey SLAG_SPECIAL_ORE_AMOUNT = create("sf_slag_ore_amount");
    public static final NamespacedKey SLAG_SPECIAL_ORE_ITEM = create("sf_slag_ore_item");

    // Thermodynamics - Last Update Time for Lazy Cooling
    public static final NamespacedKey LAST_UPDATE_TIME = create("sf_last_update_time");

    // Astral Altar (Forging) Persistence
    public static final NamespacedKey ALTAR_BLUEPRINT = create("sf_altar_blueprint");
    public static final NamespacedKey ALTAR_PLAYER_ID = create("sf_altar_player_id");
    public static final NamespacedKey ALTAR_CURRENT_PHASE = create("sf_altar_current_phase");
    public static final NamespacedKey ALTAR_MAX_PHASES = create("sf_altar_max_phases");
    public static final NamespacedKey ALTAR_QUALITY_SCORE = create("sf_altar_quality_score");
    public static final NamespacedKey ALTAR_CURRENT_INGOT = create("sf_altar_current_ingot");
    public static final NamespacedKey ALTAR_RECIPE_ID = create("sf_altar_recipe_id");
}
