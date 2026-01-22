package com.starryforge.features.items.shadowdagger;

import com.starryforge.StarryForge;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffectType;

public class ShadowDaggerListener implements Listener {

    private final StarryForge plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public ShadowDaggerListener(StarryForge plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && player.hasMetadata("starryforge_void_step")) {
            // Check if 2 seconds have passed since activation (Configurable)
            try {
                long startTime = player.getMetadata("starryforge_void_step").get(0).asLong();
                long threshold = plugin.getConfigManager().getLegendaryConfig().getLong("shadow_dagger.void_step.damage_break_threshold_ms", 2000);
                if (System.currentTimeMillis() - startTime > threshold) {
                     // Remove Invisibility if damaged after threshold
                     if (player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                         player.removePotionEffect(PotionEffectType.INVISIBILITY);
                         player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 0.5f);
                         player.sendActionBar(mm.deserialize("<red>隐身被打破!"));
                     }
                }
            } catch (Exception ignored) {
                // Handle potential metadata casting errors gracefully
            }
        }
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            // Prevent Thorns from triggering offensive effects
            if (event.getCause() == EntityDamageEvent.DamageCause.THORNS) {
                return;
            }

            // 1. Check if player is in Void Step (Active Skill)
            if (player.hasMetadata("starryforge_void_step")) {
                
                // Infinite Attack Speed Logic
                // Only if holding Shadow Dagger to prevent abuse
                org.bukkit.inventory.ItemStack handItem = player.getInventory().getItemInMainHand();
                boolean isShadowDagger = isShadowDagger(handItem);

                if (isShadowDagger && event.getEntity() instanceof org.bukkit.entity.LivingEntity target) {
                    // Bypass i-frames (Robust Method)
                    // 1. Force current cooldown to 0
                    target.setNoDamageTicks(0);
                    
                    // 2. Temporarily remove global cooldown protection
                    // We must restore this immediately to prevent "Permanent Loss"
                    final int originalMax = target.getMaximumNoDamageTicks();
                    target.setMaximumNoDamageTicks(0);
                    
                    // 3. Restore after 1 tick
                    new org.bukkit.scheduler.BukkitRunnable() {
                        @Override
                        public void run() {
                            if (target.isValid()) {
                                target.setMaximumNoDamageTicks(originalMax);
                            }
                        }
                    }.runTaskLater(plugin, 1L);

                    // Guaranteed Crit if Invisible during Skill
                    if (player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                         double critStat = com.nexuscore.NexusCore.getInstance().getRpgManager()
                            .getItemStat(handItem, com.nexuscore.rpg.stats.NexusStat.CRITICAL_DAMAGE);
                         
                         double fallback = plugin.getConfigManager().getLegendaryConfig().getDouble("shadow_dagger.backstab.crit_fallback", 1.5);
                         if (critStat <= 0.001) critStat = fallback;
                         
                         event.setDamage(event.getDamage() * critStat);
                         
                         // Visuals
                         player.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 2.0f);
                         player.getWorld().spawnParticle(org.bukkit.Particle.CRIT, target.getLocation().add(0, 1, 0), 10, 0.2, 0.2, 0.2, 0.1);
                         
                         player.sendActionBar(mm.deserialize("<dark_purple>影袭 <gray>| <red>致命一击!"));
                    }
                }

                // If not stealth (visible but speed buff active), just normal hit with i-frame bypass (handled above)
                return; 
            }

            // 2. Check for "Shadow Meld" (Backstab Passive)
            // Triggers if player is holding Shadow Dagger and behind target
            org.bukkit.inventory.ItemStack item = player.getInventory().getItemInMainHand();
            if (item.hasItemMeta()) {
                org.bukkit.persistence.PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
                String id = pdc.get(com.nexuscore.util.NexusKeys.ITEM_ID, org.bukkit.persistence.PersistentDataType.STRING);
                
                if ("SHADOW_DAGGER".equals(id)) {
                     org.bukkit.entity.Entity target = event.getEntity();
                     org.bukkit.util.Vector playerDir = player.getLocation().getDirection().setY(0).normalize();
                     org.bukkit.util.Vector targetDir = target.getLocation().getDirection().setY(0).normalize();
                     
                     // Dot Product > 0.6 means they are facing roughly the same direction (within ~53 degrees)
                     // which means player is behind target.
                     double angleThreshold = plugin.getConfigManager().getLegendaryConfig().getDouble("shadow_dagger.backstab.angle_threshold", 0.6);
                     
                     if (playerDir.dot(targetDir) > angleThreshold) {
                         // Backstab Bonus: Guaranteed Crit
                         double critStat = com.nexuscore.NexusCore.getInstance().getRpgManager()
                            .getItemStat(item, com.nexuscore.rpg.stats.NexusStat.CRITICAL_DAMAGE);
                         
                         double fallback = plugin.getConfigManager().getLegendaryConfig().getDouble("shadow_dagger.backstab.crit_fallback", 1.5);
                         if (critStat <= 0.001) critStat = fallback;
                         
                         event.setDamage(event.getDamage() * critStat);
                         
                         // Visuals
                         player.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 2.0f);
                         player.getWorld().spawnParticle(org.bukkit.Particle.CRIT, target.getLocation().add(0, 1, 0), 10, 0.2, 0.2, 0.2, 0.1);
                         
                         player.sendActionBar(mm.deserialize("<dark_purple>暗影背刺 <gray>| <red>暴击!"));
                     }
                }
            }
        }
    }

    @EventHandler
    public void onEntityTarget(org.bukkit.event.entity.EntityTargetEvent event) {
        if (event.getTarget() instanceof Player player && player.hasMetadata("starryforge_void_step")) {
            // Enhanced Invisibility: Prevent mobs from targeting the player
            // This bypasses vanilla AI detection which sometimes ignores Invisibility effect
            event.setCancelled(true);
        }
    }

    private boolean isShadowDagger(org.bukkit.inventory.ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        String id = item.getItemMeta().getPersistentDataContainer()
                .get(com.nexuscore.util.NexusKeys.ITEM_ID, org.bukkit.persistence.PersistentDataType.STRING);
        return "SHADOW_DAGGER".equals(id);
    }
}
