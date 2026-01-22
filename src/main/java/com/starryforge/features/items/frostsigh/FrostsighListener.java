package com.starryforge.features.items.frostsigh;

import com.starryforge.StarryForge;
import com.nexuscore.util.NexusKeys;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import org.bukkit.Bukkit;
import org.bukkit.event.player.PlayerItemHeldEvent;

public class FrostsighListener implements Listener {

    private final StarryForge plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public FrostsighListener(StarryForge plugin) {
        this.plugin = plugin;
    }

    public static boolean isFrostsighOblivionStatic(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        String id = item.getItemMeta().getPersistentDataContainer().get(NexusKeys.ITEM_ID, PersistentDataType.STRING);
        // Map both legacy ID (if any) and new ID to be safe
        return "FROST_SIGH_BLADE".equals(id) || "FROSTSIGH_OBLIVION".equals(id);
    }

    private boolean isFrostsighOblivion(ItemStack item) {
        return isFrostsighOblivionStatic(item);
    }

    private void updateItemToConsumable(Player player, int slot, ItemStack oldItem) {
        // Inject 1.21+ Consumable Component to enable "Bow Pull" animation
        // We create a new item with the component and apply the old meta to preserve data
        try {
            ItemMeta oldMeta = oldItem.getItemMeta();
            
            // Check if we already have the component to avoid infinite updates (optimization)
            // Since we can't easily check Component, we use a PDC flag "sf_consumable_injected"
            org.bukkit.NamespacedKey flagKey = new org.bukkit.NamespacedKey(plugin, "sf_consumable_injected");
            if (oldMeta.getPersistentDataContainer().has(flagKey, PersistentDataType.BYTE)) {
                return;
            }

            // Create fresh item with component
            ItemStack newItem = new ItemStack(oldItem.getType());
            String materialKey = oldItem.getType().getKey().toString();
            String componentData = "consumable={animation:'bow', has_consume_particles:false, consume_seconds:72000, can_always_use:true}";
            String itemDef = materialKey + "[" + componentData + "]";
            
            @SuppressWarnings("deprecation")
            ItemStack temp = Bukkit.getUnsafe().modifyItemStack(newItem, itemDef);
            newItem = temp;
            
            // Copy data from oldMeta to newItem's meta (which has the component)
            // We CANNOT simply use setItemMeta(oldMeta) because it would overwrite the component!
            ItemMeta newMeta = newItem.getItemMeta();
            
            // 1. Basic Meta
            if (oldMeta.hasDisplayName()) newMeta.displayName(oldMeta.displayName());
            if (oldMeta.hasLore()) newMeta.lore(oldMeta.lore());
            if (oldMeta.hasCustomModelData()) newMeta.setCustomModelData(oldMeta.getCustomModelData());
            
            // 2. Enchantments
            if (oldMeta.hasEnchants()) {
                oldMeta.getEnchants().forEach((ench, level) -> newMeta.addEnchant(ench, level, true));
            }
            
            // 3. Attributes
            if (oldMeta.hasAttributeModifiers()) {
                newMeta.setAttributeModifiers(oldMeta.getAttributeModifiers());
            }
            
            // 4. Item Flags
            newMeta.addItemFlags(oldMeta.getItemFlags().toArray(new org.bukkit.inventory.ItemFlag[0]));
            
            // 5. PDC (Manually copy all keys)
            // Note: This is a shallow copy of keys, but values are immutable so it's fine.
            // We only need to support standard types used in our plugins.
            // Since we can't iterate ALL types easily without NMS, we rely on knowing our keys.
            // BUT, a better way is to use the PDC API to copy if available, or just copy the keys we know.
            // Wait, we can't easily iterate all keys and their types.
            // However, most important data is NexusID, StarRating, etc.
            
            // Actually, for simplicity and safety, let's just copy the raw NBT of the PDC if possible? No API for that.
            // Let's copy known keys.
            copyPDC(oldMeta, newMeta);
            
            // Mark as injected
            newMeta.getPersistentDataContainer().set(flagKey, PersistentDataType.BYTE, (byte) 1);
            
            newItem.setItemMeta(newMeta);
            
            // Replace
            player.getInventory().setItem(slot, newItem);
            
        } catch (Throwable t) {
            // Ignore if not 1.21 or error
        }
    }

    private void copyPDC(ItemMeta source, ItemMeta target) {
        // Copy NexusCore ID
        String nexusId = source.getPersistentDataContainer().get(NexusKeys.ITEM_ID, PersistentDataType.STRING);
        if (nexusId != null) target.getPersistentDataContainer().set(NexusKeys.ITEM_ID, PersistentDataType.STRING, nexusId);
        
        // Copy Star Rating
        Integer star = source.getPersistentDataContainer().get(NexusKeys.STAR_RATING, PersistentDataType.INTEGER);
        if (star != null) target.getPersistentDataContainer().set(NexusKeys.STAR_RATING, PersistentDataType.INTEGER, star);
        
        // Copy SF Item ID
        String sfId = source.getPersistentDataContainer().get(com.starryforge.utils.Keys.ITEM_ID_KEY, PersistentDataType.STRING);
        if (sfId != null) target.getPersistentDataContainer().set(com.starryforge.utils.Keys.ITEM_ID_KEY, PersistentDataType.STRING, sfId);
        
        // Copy CraftEngine ID
        org.bukkit.NamespacedKey ceIdKey = new org.bukkit.NamespacedKey("craft_engine", "id");
        String ceId = source.getPersistentDataContainer().get(ceIdKey, PersistentDataType.STRING);
        if (ceId != null) target.getPersistentDataContainer().set(ceIdKey, PersistentDataType.STRING, ceId);
        
        // Copy Nexus Has Star (Legacy)
        org.bukkit.NamespacedKey starKey = new org.bukkit.NamespacedKey(plugin, "nexus_has_star");
        Integer hasStar = source.getPersistentDataContainer().get(starKey, PersistentDataType.INTEGER);
        if (hasStar != null) target.getPersistentDataContainer().set(starKey, PersistentDataType.INTEGER, hasStar);
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getNewSlot());
        if (isFrostsighOblivion(item)) {
            updateItemToConsumable(player, event.getNewSlot(), item);
        }
    }

    private int getStarLevel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        Integer star = item.getItemMeta().getPersistentDataContainer().get(NexusKeys.STAR_RATING, PersistentDataType.INTEGER);
        return star != null ? star : 0;
    }

    @EventHandler(ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof LivingEntity target)) return;
        
        // Strict Check: Only Direct Melee Attacks
        if (event.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        
        // Strict Check: Attack Cooldown (Must be fully charged > 0.9) to prevent spamming
        if (player.getAttackCooldown() < 0.9f) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isFrostsighOblivion(item)) return;

        int star = getStarLevel(item);
        if (star < 1) return;

        // Passive: Frost Mark
        // Chance scales with Star Level (Configurable)
        double baseChance = plugin.getConfigManager().getLegendaryConfig().getDouble("frostsigh.passive.base_chance", 0.10);
        double chancePerStar = plugin.getConfigManager().getLegendaryConfig().getDouble("frostsigh.passive.chance_per_star", 0.0375);
        double chance = baseChance + ((star - 1) * chancePerStar);
        
        // Stacks up to 10 times, each layer lasts 10s (decays one by one)
        // Added Internal Cooldown check in MarkManager if needed, but cooldown check above handles most spam.
        if (Math.random() < chance) {
            FrostMarkManager.getInstance().applyStack(target, player);
            
            // Visual: Hit Effect
            player.getWorld().spawnParticle(org.bukkit.Particle.SNOWFLAKE, target.getEyeLocation(), 5, 0.2, 0.2, 0.2, 0.05);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        // Force allow usage for "Hold to Charge" mechanic
        // We need the vanilla "Item Use" state (HandRaised) to track holding duration.
        // If other plugins (like NexusCore) cancel this event, the client won't enter the "bow pull" state.
        if (event.getAction().isRightClick() && event.hasItem() && isFrostsighOblivion(event.getItem())) {
            
            // Check for Pending Shatter (Manual Trigger)
            if (FrostsighAbility.pendingShatters.containsKey(event.getPlayer().getUniqueId())) {
                // Find ability instance to trigger shatter
                // Since ability is registered in NexusCore, we might need to access it via static map or instance.
                // However, we can just instantiate it or use a static helper. 
                // Wait, triggerShatter is instance method but ability is singleton-like in manager.
                // But here we can't easily get the instance.
                // Actually, the map 'pendingShatters' is static, so we can access the session.
                // But triggerShatter logic is in the class. 
                // Let's just make triggerShatter static or move the logic here? 
                // Better: FrostsighAbility.triggerShatter(player) should be static or accessible.
                // Let's assume we can access the ability instance or just make the method static.
                // For now, I'll make triggerShatter static in FrostsighAbility.
                
                FrostsighAbility.triggerShatterStatic(event.getPlayer());
                
                event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
                event.setCancelled(true);
                return;
            }

            // Check Cooldown
            if (FrostsighAbility.cooldowns.containsKey(event.getPlayer().getUniqueId())) {
                long left = FrostsighAbility.cooldowns.get(event.getPlayer().getUniqueId()) - System.currentTimeMillis();
                if (left > 0) {
                    // Block the bow pull animation to prevent movement slowdown
                    event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
                    event.setCancelled(true);
                    
                    // Show cooldown once
                    event.getPlayer().sendActionBar(mm.deserialize("<red>技能冷却中: <bold>" + String.format("%.1f", left / 1000.0) + "秒</bold>"));
                    return;
                }
            }

            // Ensure event is not cancelled by other plugins (e.g. NexusCore)
            // This is CRITICAL for the client to enter "HandRaised" state
            event.setCancelled(false);
            event.setUseItemInHand(org.bukkit.event.Event.Result.ALLOW);
        }
    }
}
