package com.starryforge.features.items.shadowdagger;

import com.nexuscore.util.NexusKeys;
import com.nexuscore.rpg.ability.AbilityTrigger;
import com.nexuscore.rpg.ability.NexusAbility;
import com.starryforge.StarryForge;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.bukkit.WorldBorder;
import org.bukkit.metadata.FixedMetadataValue;

public class ShadowDaggerAbility implements NexusAbility {

    private final StarryForge plugin;
    private final ShadowVisuals visuals;
    public static final String METADATA_KEY = "starryforge_void_step";

    public ShadowDaggerAbility(StarryForge plugin) {
        this.plugin = plugin;
        this.visuals = new ShadowVisuals(plugin);
    }

    @Override
    public @NotNull String getId() {
        return "VOID_STEP";
    }

    @Override
    public @NotNull String getDisplayName() {
        return "血影遁";
    }

    @Override
    public @NotNull AbilityTrigger getTrigger() {
        return AbilityTrigger.ON_RIGHT_CLICK;
    }

    @Override
    public boolean canActivate(Player player, Object context) {
        // Require at least 1 Star (Unlocked by default)
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || !item.hasItemMeta()) return false;
        
        Integer star = item.getItemMeta().getPersistentDataContainer().get(NexusKeys.STAR_RATING, PersistentDataType.INTEGER);
        return star != null && star >= 1;
    }

    @Override
    public void activate(Player player, Object context) {
        // 1. Get Star Rating
        ItemStack item = player.getInventory().getItemInMainHand();
        int star = 1;
        if (item != null && item.hasItemMeta()) {
             Integer s = item.getItemMeta().getPersistentDataContainer()
                .get(NexusKeys.STAR_RATING, PersistentDataType.INTEGER);
             if (s != null) star = s;
        }
        
        // Clamp star
        if (star < 1) star = 1;
        if (star > 5) star = 5;

        // 2. Determine Stats based on Star
        String path = "shadow_dagger.void_step.star_" + star;
        // Fallback if key missing
        if (!plugin.getConfigManager().getLegendaryConfig().contains(path)) {
            // Try star 5 or just use defaults
            path = "shadow_dagger.void_step.star_" + (star > 5 ? 5 : 1);
        }
        
        int durationTicks = plugin.getConfigManager().getLegendaryConfig().getInt(path + ".duration_ticks", 40);
        int speedAmp = plugin.getConfigManager().getLegendaryConfig().getInt(path + ".speed_amp", 1);

        // Apply effects
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, durationTicks, speedAmp));
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, durationTicks, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, durationTicks, 0));
        
        // Mark player with start time
        player.setMetadata(METADATA_KEY, new FixedMetadataValue(plugin, System.currentTimeMillis()));
        
        // AI Bypass: Force nearby mobs to lose target
        for (org.bukkit.entity.Entity entity : player.getNearbyEntities(32, 32, 32)) {
            if (entity instanceof org.bukkit.entity.Mob mob) {
                if (mob.getTarget() == player) {
                    mob.setTarget(null);
                }
            }
        }
        
        // Red Screen Visuals (World Border Vignette)
        // We create a per-player border to ensure the red vignette appears without affecting the actual world border
        WorldBorder border = org.bukkit.Bukkit.createWorldBorder();
        border.setCenter(player.getLocation());
        border.setSize(100_000); // Large enough to not block movement
        border.setWarningDistance(Integer.MAX_VALUE); // Maximum warning distance to ensure red tint everywhere
        border.setWarningTime(15);
        player.setWorldBorder(border);

        visuals.playActivationEffect(player);
        
        net.kyori.adventure.text.minimessage.MiniMessage mm = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();
        player.showTitle(net.kyori.adventure.title.Title.title(
            mm.deserialize("<gradient:dark_red:black>血影遁</gradient>"),
            mm.deserialize("<gray>潜行于暗影之中..."),
            net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(200), java.time.Duration.ofMillis(1000), java.time.Duration.ofMillis(500))
        ));

        final int finalDuration = durationTicks;

        new BukkitRunnable() {
            int ticks = 0;
            int nextHeartbeatTick = 0; // Control next heartbeat time

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= finalDuration || !player.hasMetadata(METADATA_KEY)) {
                    this.cancel();
                    if (player.isOnline()) {
                        removeVoidStep(player, plugin);
                    }
                    return;
                }
                
                // Update border center to follow player (optional, but keeps the vignette consistent)
                // Actually, with MAX_VALUE warning distance, center doesn't matter much as long as we are inside.
                // But let's keep it clean.
                // border.setCenter(player.getLocation()); 
                
                // Red Haze (Vignette) Effect - Removed per user request to clear vision
                // org.bukkit.Location eye = player.getEyeLocation();
                // ... (Removed)

                visuals.spawnGhostTrail(player);

                // Dynamic Heartbeat Frequency Logic
                
                int remaining = finalDuration - ticks;
                int currentInterval = 10;
                
                int fastThreshold = plugin.getConfigManager().getLegendaryConfig().getInt("shadow_dagger.void_step.heartbeat.fast_threshold_ticks", 40);
                int mediumThreshold = plugin.getConfigManager().getLegendaryConfig().getInt("shadow_dagger.void_step.heartbeat.medium_threshold_ticks", 80);
                
                if (remaining <= fastThreshold) {
                    currentInterval = plugin.getConfigManager().getLegendaryConfig().getInt("shadow_dagger.void_step.heartbeat.interval_fast", 8);
                } else if (remaining <= mediumThreshold) {
                    currentInterval = plugin.getConfigManager().getLegendaryConfig().getInt("shadow_dagger.void_step.heartbeat.interval_medium", 9);
                } else {
                    currentInterval = plugin.getConfigManager().getLegendaryConfig().getInt("shadow_dagger.void_step.heartbeat.interval_slow", 10);
                }

                // Check if it's time for a heartbeat
                if (ticks >= nextHeartbeatTick) {
                    visuals.playHeartbeat(player);
                    player.sendActionBar(mm.deserialize("<gradient:dark_red:red>... 咚 ... 咚 ...</gradient>"));
                    
                    // Schedule next heartbeat
                    nextHeartbeatTick = ticks + currentInterval;
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
    
    public static void removeVoidStep(Player player, StarryForge plugin) {
        if (player.hasMetadata(METADATA_KEY)) {
            player.removeMetadata(METADATA_KEY, plugin);
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
            player.removePotionEffect(PotionEffectType.SPEED);
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
            
            // Reset WorldBorder Warning by setting it to null (restores world border)
            player.setWorldBorder(null);
            
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 0.5f);
        }
    }

    @Override
    public double getCooldown() {
        return plugin.getConfigManager().getLegendaryConfig().getDouble("shadow_dagger.cooldown_s", 60.0);
    }
}
