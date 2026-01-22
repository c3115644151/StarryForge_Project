package com.starryforge.features.items.frostsigh;

import com.starryforge.StarryForge;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the "Frost Mark" (冰霜印记) stacking mechanic.
 * Singleton-like manager handled by StarryForge.
 */
public class FrostMarkManager {

    private static FrostMarkManager instance;
    private final StarryForge plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    
    // Map<EntityUUID, MarkData>
    private final Map<UUID, MarkData> activeMarks = new ConcurrentHashMap<>();
    
    private BukkitRunnable task;

    public FrostMarkManager(StarryForge plugin) {
        this.plugin = plugin;
        instance = this;
    }

    public static FrostMarkManager getInstance() {
        return instance;
    }

    public void start() {
        // Run every tick (1L) to handle high-frequency DoT at high stacks
        task = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        };
        task.runTaskTimer(plugin, 1L, 1L);
    }

    public void stop() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        activeMarks.clear();
    }

    /**
     * Applies a stack to the target.
     * @param target The victim
     * @param attacker The attacker (for attribution)
     */
    public void applyStack(LivingEntity target, Player attacker) {
        int maxStacks = plugin.getConfigManager().getLegendaryConfig().getInt("frostsigh.passive.stack_limit", 10);
        long decayMs = plugin.getConfigManager().getLegendaryConfig().getLong("frostsigh.passive.decay_ms", 10000);

        activeMarks.compute(target.getUniqueId(), (uuid, data) -> {
            if (data == null) {
                return new MarkData(target, attacker, decayMs);
            }
            data.addStack(attacker, maxStacks, decayMs);
            return data;
        });
        
        // Visual/Audio Feedback for application
        int stacks = getStacks(target);
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_GLASS_HIT, 1.0f, 0.5f + (stacks * 0.1f));
        
        // Actionbar to attacker
        attacker.sendActionBar(mm.deserialize("<gradient:aqua:white>❄ 冰霜印记: " + stacks + "/10 ❄</gradient>"));
    }

    public int getStacks(LivingEntity target) {
        MarkData data = activeMarks.get(target.getUniqueId());
        return data == null ? 0 : data.stacks;
    }

    public void clearStacks(LivingEntity target) {
        if (activeMarks.remove(target.getUniqueId()) != null) {
            // Shatter sound/visual
            target.getWorld().playSound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.2f, 0.8f);
            target.getWorld().spawnParticle(Particle.BLOCK, target.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, org.bukkit.Material.ICE.createBlockData());
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        long decayMs = plugin.getConfigManager().getLegendaryConfig().getLong("frostsigh.passive.decay_ms", 10000);
        int freezeMaintain = plugin.getConfigManager().getLegendaryConfig().getInt("frostsigh.passive.freeze_ticks_maintain", 150);
        double damagePerStack = plugin.getConfigManager().getLegendaryConfig().getDouble("frostsigh.passive.scaling_damage_per_stack", 0.5);
        int dotInterval = plugin.getConfigManager().getLegendaryConfig().getInt("frostsigh.passive.dot_interval_ticks", 40);
        int dotOffset = plugin.getConfigManager().getLegendaryConfig().getInt("frostsigh.passive.scaling_damage_offset_ticks", 20);

        Iterator<Map.Entry<UUID, MarkData>> it = activeMarks.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<UUID, MarkData> entry = it.next();
            MarkData data = entry.getValue();

            // Validity check
            if (!data.entity.isValid() || data.entity.isDead()) {
                it.remove();
                continue;
            }
            
            // HUD Logic (RayTrace Check)
            if (data.attacker != null && data.attacker.isOnline()) {
                // Check if attacker is looking at this entity
                // Simple dot product check for performance
                org.bukkit.util.Vector toEntity = data.entity.getEyeLocation().toVector().subtract(data.attacker.getEyeLocation().toVector()).normalize();
                org.bukkit.util.Vector direction = data.attacker.getEyeLocation().getDirection();
                double dot = toEntity.dot(direction);
                
                // If looking at entity (approx < 15 degrees) and within distance (20 blocks)
                if (dot > 0.96 && data.attacker.getLocation().distanceSquared(data.entity.getLocation()) < 400) {
                     // Check if player is holding Frostsigh Oblivion
                     // This optimization prevents HUD spam for non-Frostsigh users
                     ItemStack item = data.attacker.getInventory().getItemInMainHand();
                     if (FrostsighListener.isFrostsighOblivionStatic(item)) {
                         // Show persistent HUD
                         data.attacker.sendActionBar(mm.deserialize("<gradient:aqua:white>❄ 目标印记: " + data.stacks + "/10 ❄</gradient>"));
                     }
                }
            }

            // 1. Decay Logic
            if (now > data.nextDecayTime) {
                data.stacks--;
                if (data.stacks <= 0) {
                    it.remove(); // Removed completely
                    continue;
                }
                // Reset timer for next stack drop
                data.nextDecayTime = now + decayMs; 
                
                // Visual for decay
                data.entity.getWorld().playSound(data.entity.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 1.5f);
            }

            // 2. Freeze Logic (Replaces DoT)
            // Instead of dealing direct damage, we maintain the entity in a "Frozen" state.
            // Vanilla 1.17+ Freeze mechanic deals 1 damage every 2 seconds (40 ticks) when ticks >= 140.
            // This provides a subtle, aesthetic damage source without the red flash spam.
            
            // Maintain Freeze Ticks at max to ensure continuous freezing effect
            // 140 is the threshold for damage. We set to 150 to be safe.
            if (data.entity.getFreezeTicks() < 140) {
                data.entity.setFreezeTicks(freezeMaintain);
            }
            
            // Custom Scaling Damage (Every 2 seconds / 40 ticks)
            // Vanilla freeze deals ~1.0 damage every 2s.
            // We ADD scaling damage on top: +0.5 damage per stack.
            // 1 Stack: 1.0 (Vanilla) + 0.5 = 1.5 total
            // 10 Stacks: 1.0 (Vanilla) + 5.0 = 6.0 total
            // We use DAMAGE_INDICATOR suppression or setCause to CUSTOM to avoid infinite stacking loops
            // The FrostsighListener already checks for ENTITY_ATTACK cause, so using generic damage (CUSTOM) is safe.
            if (data.tickCounter % dotInterval == 0) {
                // Delayed damage to separate from vanilla freeze damage tick (usually at 0, 40, 80...)
                // We run at offset 20 to stagger damage numbers
            }
            if ((data.tickCounter + dotOffset) % dotInterval == 0) {
                 double extraDamage = data.stacks * damagePerStack;
                 // Use damage(amount) to avoid ENTITY_ATTACK cause and prevent recursion
                 data.entity.damage(extraDamage);
                 data.entity.setNoDamageTicks(0);
            }
            
            // Visuals (Snowflakes) - Less frequent than before to reduce noise
            if (data.tickCounter % 20 == 0) {
                 data.entity.getWorld().spawnParticle(Particle.SNOWFLAKE, data.entity.getLocation().add(0, 1, 0), 2, 0.2, 0.2, 0.2, 0.0);
            }
            
            data.tickCounter++;

            // 3. Slowness Logic (Apply every second to ensure persistence)
            if (data.tickCounter % 20 == 0) {
                // Slowness: Level = stacks / 2 (0 to 5)
                // 1-2 stacks: Slowness 0 (I)
                // 3-4 stacks: Slowness 1 (II)
                // ...
                // 9-10 stacks: Slowness 4 (V) - Very slow
                int amplifier = Math.max(0, (data.stacks / 2));
                data.entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, amplifier, false, false, false));
            }
        }
    }

    private static class MarkData {
        final LivingEntity entity;
        final Player attacker; // Keep track of last attacker for credit?
        int stacks;
        long nextDecayTime;
        long tickCounter;

        MarkData(LivingEntity entity, Player attacker, long decayMs) {
            this.entity = entity;
            this.attacker = attacker;
            this.stacks = 1;
            this.nextDecayTime = System.currentTimeMillis() + decayMs;
            this.tickCounter = 0;
        }

        void addStack(Player newAttacker, int maxStacks, long decayMs) {
            if (stacks < maxStacks) {
                stacks++;
            }
            // Refresh current layer timer
            this.nextDecayTime = System.currentTimeMillis() + decayMs;
        }
    }
}
