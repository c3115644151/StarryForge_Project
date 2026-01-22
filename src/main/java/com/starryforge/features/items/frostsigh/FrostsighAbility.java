package com.starryforge.features.items.frostsigh;

import com.nexuscore.rpg.ability.AbilityTrigger;
import com.nexuscore.rpg.ability.NexusAbility;
import com.starryforge.StarryForge;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class FrostsighAbility implements NexusAbility {

    private final StarryForge plugin;
    private static final MiniMessage mm = MiniMessage.miniMessage();
    
    // State Management
    private final Map<UUID, BukkitRunnable> chargingTasks = new HashMap<>();
    public static final Map<UUID, Long> cooldowns = new HashMap<>();
    
    // Pending Shatter Sessions (Map<PlayerUUID, Session>)
    public static final Map<UUID, PendingShatterSession> pendingShatters = new HashMap<>();

    private class PendingShatterSession {
        final Set<LivingEntity> targets;
        final BukkitRunnable autoShatterTask;
        final BukkitRunnable visualTask; // Task for particle visuals
        final Location impactLoc;
        final double damage;

        public PendingShatterSession(Set<LivingEntity> targets, Location impactLoc, BukkitRunnable autoShatterTask, BukkitRunnable visualTask, double damage) {
            this.targets = targets;
            this.impactLoc = impactLoc;
            this.autoShatterTask = autoShatterTask;
            this.visualTask = visualTask;
            this.damage = damage;
        }
    }

    // Configuration Helpers
    private int getMaxHold() {
        return plugin.getConfigManager().getLegendaryConfig().getInt("frostsigh.max_hold_ticks", 300);
    }
    
    private long getCooldownMs() {
        return plugin.getConfigManager().getLegendaryConfig().getLong("frostsigh.cooldown_ms", 8000);
    }

    private int getThresholdReady() {
        return (int) (getMaxHold() * plugin.getConfigManager().getLegendaryConfig().getDouble("frostsigh.thresholds.ready_percent", 0.10));
    }

    private int getThresholdPower() {
        return (int) (getMaxHold() * plugin.getConfigManager().getLegendaryConfig().getDouble("frostsigh.thresholds.power_percent", 0.33));
    }

    private int getThresholdWarn() {
        return (int) (getMaxHold() * plugin.getConfigManager().getLegendaryConfig().getDouble("frostsigh.thresholds.warn_percent", 0.83));
    }

    public FrostsighAbility(StarryForge plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getId() {
        return "FROSTSIGH_EVENT_HORIZON";
    }

    @Override
    public @NotNull String getDisplayName() {
        return "<gradient:#00FFFF:#FFD700>霜叹·忘川</gradient>";
    }

    @Override
    public @NotNull AbilityTrigger getTrigger() {
        return AbilityTrigger.ON_RIGHT_CLICK;
    }

    @Override
    public boolean canActivate(Player player, Object context) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || !item.hasItemMeta()) return false;

        // Check Internal Cooldown
        if (cooldowns.containsKey(player.getUniqueId())) {
            long left = cooldowns.get(player.getUniqueId()) - System.currentTimeMillis();
            if (left > 0) {
                // Return false to stop NexusCore from processing,
                // but FrostsighListener handles the blocking feedback.
                return false;
            }
        }
        return true;
    }

    @Override
    public void activate(Player player, Object context) {
        // Prevent double charging
        if (chargingTasks.containsKey(player.getUniqueId())) return;

        // Start Charging Sequence
        ChargingTask task = new ChargingTask(player);
        // Start immediately (0L) to catch the interaction
        // BUT we must handle the first tick specially in run()
        task.runTaskTimer(plugin, 0L, 1L);
        chargingTasks.put(player.getUniqueId(), task);
        
        // Initial Feedback (Subtle)
        // Only play if not on cooldown
        if (!isCooldownActive(player)) {
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 0.5f);
        }
    }

    private boolean isCooldownActive(Player player) {
        if (cooldowns.containsKey(player.getUniqueId())) {
            long left = cooldowns.get(player.getUniqueId()) - System.currentTimeMillis();
            return left > 0;
        }
        return false;
    }

    @Override
    public double getCooldown() {
        return 0.5; // Minimal cooldown to prevent spam, real cooldown handled internally
    }

    // ============================================================================================
    // Charging Logic
    // ============================================================================================

    private class ChargingTask extends BukkitRunnable {
        private final Player player;
        private int ticks = 0;
        private boolean isCharging = false;

        public ChargingTask(Player player) {
            this.player = player;
        }

        @Override
        public void run() {
            // 0. Validation: Must be online and holding right click
            // We do this check first every tick to handle release/cancel
            if (!player.isOnline() || !player.isHandRaised()) {
                // Only "finish" if we were actually charging. If we were just waiting for cooldown, just cancel.
                if (isCharging) {
                    finish(false);
                } else {
                    this.cancel();
                    chargingTasks.remove(player.getUniqueId());
                }
                return;
            }

            // 1. Cooldown Check
            if (isCooldownActive(player)) {
                long left = cooldowns.get(player.getUniqueId()) - System.currentTimeMillis();
                player.sendActionBar(mm.deserialize("<red>技能冷却中: <bold>" + String.format("%.1f", left / 1000.0) + "秒</bold>"));
                return; // Wait for cooldown, do not increment ticks or apply effects
            }
            
            // Transition to Charging State
            if (!isCharging) {
                isCharging = true;
                // Initial sound if we just started charging after cooldown
                player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 0.5f);
            }

            // 2. Overcharge Check
            if (ticks > getMaxHold()) {
                finish(true); // Overcharged
                return;
            }

            // 3. Apply Focus Effects (Every tick to maintain)
            // Slowness V (Movement stop + Zoom)
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 5, 4, false, false, false));
            // Resistance I
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 5, 0, false, false, false));
            // Darkness (Vignette) - "Dark Circle"
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 0, false, false, false));
            
            // Highlight Targets (Glowing in Darkness)
            highlightTargets();

            // 4. Stage Logic & Visuals
            
            // Stage 1: Start -> READY (0 -> 30 ticks)
            if (ticks < getThresholdReady()) {
                // Building up...
            }
            // Stage 2: READY -> POWER (30 -> 100 ticks)
            else if (ticks < getThresholdPower()) {
                if (ticks == getThresholdReady()) {
                    // Visual Cue (Ready)
                    player.spawnParticle(Particle.FLASH, player.getLocation().add(0, 1, 0), 1);
                    player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 1.0f); // Heartbeat
                    player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 1.5f); // Rising tone
                }
                
                player.sendActionBar(mm.deserialize("<gradient:aqua:white><bold>⚡ READY ⚡</bold></gradient>"));
                
                // Heartbeat Loop
                if ((ticks - getThresholdReady()) % 20 == 0) {
                     player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 1.0f);
                }
            }
            // Stage 3: POWER -> WARN (100 -> 250 ticks)
            else if (ticks < getThresholdWarn()) {
                // Visuals: Snowflakes (Heavy) - "漫天飞雪"
                spawnSnowflakes();
                
                if (ticks == getThresholdPower()) {
                     player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1.0f, 2.0f);
                     player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 2.0f);
                }

                player.sendActionBar(mm.deserialize("<gradient:gold:yellow><bold>⚡ MAXIMUM POWER ⚡</bold></gradient>"));
                
                // Heartbeat Loop (Faster)
                if ((ticks - getThresholdPower()) % 15 == 0) {
                     player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 1.2f);
                }
            }
            // Stage 4: WARN -> MAX (250 -> 300 ticks)
            else {
                // Visuals: Snowflakes (Heavy)
                spawnSnowflakes();
                
                // Shaking Text (Fatigue)
                // Alternate text to simulate shaking/trembling
                if (ticks % 2 == 0) {
                    player.sendActionBar(mm.deserialize("<gradient:red:dark_red><bold>⚠ EXHAUSTION IMMINENT ⚠</bold></gradient>"));
                } else {
                     // Offset slightly using spaces or just color shift
                    player.sendActionBar(mm.deserialize("<gradient:dark_red:red> <bold>⚠ EXHAUSTION IMMINENT ⚠</bold> </gradient>"));
                }
                
                // Heartbeat Loop (Panic)
                if ((ticks - getThresholdWarn()) % 10 == 0) {
                     player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 1.5f);
                }
            }

            ticks++;
        }
        
        private void spawnSnowflakes() {
            Location loc = player.getLocation().add(0, 1, 0);
            double r = 3.0; // Wide range
            // Random spawn around
            for (int i = 0; i < 3; i++) {
                double angle = Math.random() * Math.PI * 2;
                double dist = Math.random() * r;
                double x = dist * Math.cos(angle);
                double z = dist * Math.sin(angle);
                double y = (Math.random() - 0.5) * 2.0; // +/- 1 height
                player.getWorld().spawnParticle(Particle.SNOWFLAKE, loc.clone().add(x, y, z), 0, 0, 0, 0);
            }
        }
        
        private void highlightTargets() {
            // Highlight all entities within 12 blocks (Dash range)
            double range = 12.0;
            
            for (Entity entity : player.getNearbyEntities(range, range, range)) {
                if (entity instanceof LivingEntity target && entity != player) {
                    // Apply Glowing (Short duration to auto-expire)
                    target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 5, 0, false, false, false));
                }
            }
        }

        private void finish(boolean overcharged) {
            this.cancel();
            chargingTasks.remove(player.getUniqueId());

            // Clear Effects
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            player.removePotionEffect(PotionEffectType.RESISTANCE);
            player.removePotionEffect(PotionEffectType.DARKNESS);

            if (overcharged) {
                // Punishment: Break concentration & Weakness
                player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 0.5f);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BREATH, 1.0f, 0.5f); // Gasp
                
                player.sendMessage(mm.deserialize("<red>已脱力! (Overcharged)</red>"));
                
                // Apply Weakness (Exhaustion) - 3 seconds
                player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0, false, false, true));
                
                applyCooldown();
            } else if (ticks >= getThresholdReady()) {
                // Success: Execute Skill
                // Check if Empowered (Stage 3+)
                boolean empowered = ticks >= getThresholdPower();
                executeSkill(player, empowered);
                applyCooldown();
            } else {
                // Fizzle: Not charged enough
                player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.5f);
                player.sendActionBar(mm.deserialize("<gray>专注中断...</gray>"));
                // Add short internal cooldown to prevent spam loop if player keeps holding
                cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + 500); 
            }
        }

        private void applyCooldown() {
            cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + getCooldownMs());
        }
    }

    // ============================================================================================
    // Skill Execution (Dimensional Severance)
    // ============================================================================================

    private void executeSkill(Player player, boolean empowered) {
        // Phase 2: The Strike (Dimensional Severance)
        // Velocity Dash + Visuals
        
        Location startLoc = player.getLocation();
        Vector direction = startLoc.getDirection().normalize();
        
        // Dynamic Dash Range based on Item Star/Stats or just fixed scaling
        // Let's use Attack Range stat? No, that's melee reach.
        // Let's scale based on Empowered state for now as requested.
        // Base: 12 blocks. Empowered: 18 blocks.
        // We could also add Attack Range stat to this?
        double bonusRange = com.nexuscore.NexusCore.getInstance().getRpgManager().getItemStat(player.getInventory().getItemInMainHand(), com.nexuscore.rpg.stats.NexusStat.ATTACK_RANGE);
        // Default Attack Range is usually small (3.0). If it's a bonus stat on item, it might be e.g. +2.0.
        // Let's treat ATTACK_RANGE as a flat addition to dash distance.
        
        double baseDistance = empowered ? 
            plugin.getConfigManager().getLegendaryConfig().getDouble("frostsigh.dash_distance.empowered", 18.0) : 
            plugin.getConfigManager().getLegendaryConfig().getDouble("frostsigh.dash_distance.base", 12.0);
        double maxDistance = baseDistance + (bonusRange > 0 ? bonusRange : 0);
        
        World world = player.getWorld();
        RayTraceResult rayTrace = world.rayTraceBlocks(startLoc.add(0, 1, 0), direction, maxDistance);
        
        Location endLoc;
        if (rayTrace != null && rayTrace.getHitBlock() != null) {
            // Stop slightly before the wall
            endLoc = rayTrace.getHitPosition().toLocation(world).subtract(direction.clone().multiply(1.0));
        } else {
            endLoc = startLoc.clone().add(direction.clone().multiply(maxDistance));
        }
        endLoc.setDirection(startLoc.getDirection());
        
        // Calculate Dash Vector
        Vector dashVec = endLoc.toVector().subtract(startLoc.toVector());
        double distance = dashVec.length();
        
        // Safety: If distance is too small, just skip to damage
        if (distance < 2.0) {
            triggerImpact(player, startLoc, endLoc, empowered);
            return;
        }
        
        // Start Dash
        player.playSound(startLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
        player.playSound(startLoc, Sound.ENTITY_BREEZE_WIND_BURST, 1.0f, 1.5f); // Sharp air tearing sound
        player.playSound(startLoc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.5f); // Low pitch sweep for body
        
        // Disable Gravity/Friction-like effects by setting velocity
        // We use a runnable to maintain high velocity for a few ticks if needed, 
        // but for 12 blocks, a single burst is often enough if high enough.
        // 12 blocks / 4 blocks/tick = 3 ticks.
        
        player.setVelocity(direction.multiply(4.0)); // Very fast burst
        
        // Trail Visuals (Spawn immediately so player dashes THROUGH them)
        drawConstellationPath(startLoc, endLoc);
        
        // Dash Task to stop player and deal damage
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                // Check if reached or hit something
                if (ticks >= 5 || player.getLocation().distanceSquared(startLoc) >= distance * distance) {
                    // Force stop at destination without teleporting to avoid stutter
                    player.setVelocity(new Vector(0, 0, 0));
                    // Optional: slight correction if needed, but velocity usually lands close enough
                    // player.teleport(endLoc); // REMOVED to prevent "teleport feel"
                    
                    triggerImpact(player, startLoc, endLoc, empowered);
                    this.cancel();
                    return;
                }
                
                // Keep pushing if slowed down
                if (player.getVelocity().length() < 1.0) {
                     player.setVelocity(direction.multiply(4.0));
                }
                
                // Trail particles on player
                player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0), 2, 0.1, 0.1, 0.1, 0.05);
                
                ticks++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void triggerImpact(Player player, Location startLoc, Location endLoc, boolean empowered) {
        // Teleport FX (End)
        player.getWorld().playSound(endLoc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 2.0f);
        // REMOVED: Metallic sound as requested
        
        // Calculate Hit Logic
        // Use ATTACK_RANGE stat for width/reach if needed, but here we use it for raytrace length in executeSkill.
        // For impact width, we keep it generous (2.5) to ensure hits.
        Set<LivingEntity> targets = getTargetsInPath(startLoc, endLoc, 2.5); // Slightly wider for dash
        targets.remove(player); // Don't hit self
        
        if (targets.isEmpty()) {
            // Nothing hit, just finish
            return;
        }

        // Damage Calculation
        // Base Damage = Item Attack Damage + Skill Bonus
        // We get attack damage from NexusRPG manager (which reads NBT/Lore)
        double itemDamage = com.nexuscore.NexusCore.getInstance().getRpgManager().getItemStat(player.getInventory().getItemInMainHand(), com.nexuscore.rpg.stats.NexusStat.ATTACK_DAMAGE);
        // Fallback if 0 (e.g. not registered properly)
        if (itemDamage < 1.0) itemDamage = 9.0; // Default 5-star damage
        
        // Empowered Multiplier (Skill Base)
        // Stage 2 (Ready): 1.5x Item Damage
        // Stage 3 (Empowered): 2.5x Item Damage
        double skillMultiplier = empowered ? 
            plugin.getConfigManager().getLegendaryConfig().getDouble("frostsigh.damage_multipliers.empowered", 2.5) : 
            plugin.getConfigManager().getLegendaryConfig().getDouble("frostsigh.damage_multipliers.ready", 1.5);
        
        double damage = itemDamage * skillMultiplier;

        // Apply "Stasis" / Mark
        for (LivingEntity target : targets) {
            boolean isFatal = target.getHealth() <= damage;
            
            int duration = isFatal ? 200 : 20; // 10s (effectively infinite for session) vs 1s
            
            // Visual freeze: Slowness + Jump Boost (No Gravity Hack)
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 255, false, false, false));
            target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, duration, 250, false, false, false)); 
            // Glowing for basic visibility
            target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, duration, 0, false, false, false));
            
            // Initial Mark
            target.getWorld().spawnParticle(Particle.CRIT, target.getEyeLocation().add(0, 0.5, 0), 5);
        }

        // Visual Task for Marks (Ice Chains / Sigil) & ActionBar Hint
        BukkitRunnable visualTask = new BukkitRunnable() {
            int tick = 0;
            
            @Override
            public void run() {
                // Actionbar Hint (Dynamic)
                if (tick % 10 < 5) {
                    player.sendActionBar(mm.deserialize("<gradient:gold:yellow><bold>RIGHT CLICK TO SHATTER</bold></gradient>"));
                } else {
                    player.sendActionBar(mm.deserialize("<gradient:yellow:gold><bold>» RIGHT CLICK TO SHATTER «</bold></gradient>"));
                }
                
                for (LivingEntity target : targets) {
                    if (!target.isValid()) continue;
                    
                    // Display "❄" Text above head (Simulated via particles or just specialized particles)
                    // Let's use a particle ring + rising "Frost"
                    Location center = target.getLocation().add(0, 1, 0);
                    
                    // Rising Frost
                    target.getWorld().spawnParticle(Particle.SNOWFLAKE, center, 1, 0.5, 1.0, 0.5, 0.01);
                    
                    // Cursed/Frozen Sigil (Soul Fire)
                    target.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, target.getEyeLocation().add(0, 0.5, 0), 1, 0, 0, 0, 0);
                }
                tick++;
            }
        };
        visualTask.runTaskTimer(plugin, 0L, 2L); // Faster update for smooth animation (2 ticks)

        // Create Pending Session
        // Auto-shatter after cooldown (or slightly before)
        BukkitRunnable autoShatter = new BukkitRunnable() {
            @Override
            public void run() {
                triggerShatter(player); // This will handle cleanup
            }
        };
        autoShatter.runTaskLater(plugin, (long) (getCooldownMs() / 50)); // Convert ms to ticks

        pendingShatters.put(player.getUniqueId(), new PendingShatterSession(targets, endLoc, autoShatter, visualTask, damage));
        
        // Initial Hint (handled by visualTask immediately, but safe to send once)
        // player.sendActionBar(mm.deserialize("<gradient:gold:yellow><bold>RIGHT CLICK TO SHATTER</bold></gradient>"));
    }

    public static void triggerShatterStatic(Player player) {
        PendingShatterSession session = pendingShatters.remove(player.getUniqueId());
        if (session == null) return;
        
        // Cancel tasks
        if (!session.autoShatterTask.isCancelled()) session.autoShatterTask.cancel();
        if (!session.visualTask.isCancelled()) session.visualTask.cancel();

        // Sound: Sheath Click (Noto) & Shatter
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 2.0f);
        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.5f, 2.0f);
        player.getWorld().playSound(session.impactLoc, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.6f);
        player.getWorld().playSound(session.impactLoc, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 1.0f, 1.5f); // High pitch ring

        for (LivingEntity target : session.targets) {
            if (!target.isValid()) continue;

            // Remove Stasis
            target.removePotionEffect(PotionEffectType.SLOWNESS);
            target.removePotionEffect(PotionEffectType.JUMP_BOOST);
            target.removePotionEffect(PotionEffectType.GLOWING);

            // Damage Calculation
            double baseDamage = session.damage; // From session
            
            // Frost Mark Synergy
            int stacks = FrostMarkManager.getInstance().getStacks(target);
            
            // Configurable Multiplier
            double bonusPerStack = com.starryforge.StarryForge.getInstance().getConfigManager().getLegendaryConfig()
                .getDouble("frostsigh.passive.shatter_bonus_per_stack", 0.5);
                
            double multiplier = 1.0 + (stacks * bonusPerStack); // +X% per stack
            double finalDamage = baseDamage * multiplier;
            
            target.damage(finalDamage, player);
            
            // Clear Stacks
            FrostMarkManager.getInstance().clearStacks(target);
            
            // Feedback for High Damage
            if (stacks >= 5) {
                player.sendMessage(mm.deserialize("<gradient:aqua:white>❄ 冰爆! 伤害倍率: " + String.format("%.1f", multiplier) + "x ❄</gradient>"));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0f, 0.5f);
            }
            
            // Visuals on Target
            target.getWorld().spawnParticle(Particle.BLOCK, target.getLocation().add(0,1,0), 20, 0.5, 0.5, 0.5, org.bukkit.Material.ICE.createBlockData());
            target.getWorld().spawnParticle(Particle.FLASH, target.getLocation().add(0,1,0), 1);
        }
        
        // Feedback
        player.sendActionBar(mm.deserialize("<gradient:aqua:gold>✧ 寂灭·断空 ✧</gradient>"));
    }

    public void triggerShatter(Player player) {
        triggerShatterStatic(player);
    }

    private void drawConstellationPath(Location start, Location end) {
        World world = start.getWorld();
        double distance = start.distance(end);
        Vector vec = end.toVector().subtract(start.toVector()).normalize();
        
        // StarryForge Palette: Cyan (#00FFFF) to Gold (#FFD700)
        Particle.DustOptions cyanDust = new Particle.DustOptions(Color.fromRGB(0, 255, 255), 0.8f);
        Particle.DustOptions goldDust = new Particle.DustOptions(Color.fromRGB(255, 215, 0), 0.8f);

        for (double i = 0; i <= distance; i += 0.2) {
            Location point = start.clone().add(vec.clone().multiply(i)).add(0, 1, 0); // Waist height
            
            // Main Line (Cyan)
            world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, cyanDust);
            
            // Stars (Gold points occasionally)
            if (i % 2.0 < 0.2) {
                world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, goldDust);
                world.spawnParticle(Particle.END_ROD, point, 1, 0, 0, 0, 0);
            }
        }
    }

    private Set<LivingEntity> getTargetsInPath(Location start, Location end, double width) {
        Set<LivingEntity> targets = new HashSet<>();
        World world = start.getWorld();
        double distance = start.distance(end);
        Vector vec = end.toVector().subtract(start.toVector()).normalize();
        
        // Scan along the line
        for (double i = 0; i <= distance; i += 1.0) {
            Location point = start.clone().add(vec.clone().multiply(i));
            for (Entity e : world.getNearbyEntities(point, width, width, width)) {
                if (e instanceof LivingEntity le) {
                    targets.add(le);
                }
            }
        }
        return targets;
    }
}
