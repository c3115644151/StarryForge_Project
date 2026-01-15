package com.starryforge.features.forging;

import com.nexuscore.api.NexusKeys;
import com.starryforge.StarryForge;
import com.starryforge.features.core.PDCManager;
import com.starryforge.features.forging.visual.ForgingVisuals;
import com.starryforge.utils.Keys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 锻造会话
 * <p>
 * 管理单个星魂共振台的锻造状态
 */
public class ForgingSession {

    // 目标ID到展示材质的映射
    private static final Map<String, Material> DISPLAY_MATERIALS = Map.ofEntries(
            Map.entry("PICKAXE", Material.IRON_PICKAXE),
            Map.entry("HAMMER", Material.IRON_PICKAXE),
            Map.entry("AXE", Material.IRON_AXE),
            Map.entry("BOOTS", Material.IRON_BOOTS),
            Map.entry("CHESTPLATE", Material.IRON_CHESTPLATE),
            Map.entry("HELMET", Material.IRON_HELMET),
            Map.entry("CROWN", Material.IRON_HELMET),
            Map.entry("SHIELD", Material.SHIELD),
            Map.entry("RING", Material.GOLD_INGOT),
            Map.entry("AMULET", Material.GOLD_INGOT),
            Map.entry("WAND", Material.BLAZE_ROD),
            Map.entry("DAGGER", Material.IRON_SWORD),
            Map.entry("SWORD", Material.IRON_SWORD)
    );

    private final Location anvilLocation;
    private UUID playerId;
    private final NamespacedKey blueprintTargetKey;

    private ItemStack targetBlueprint;
    private ItemStack currentIngot; // Currently processing ingot
    private ForgingRecipeManager.ForgingRecipe activeRecipe;
    private String activeRecipeId;

    // Multi-Round State
    private int roundsCompleted = 0;
    private int totalRounds = 1;
    private final List<Integer> materialStars = new ArrayList<>();
    private final List<Double> qteScores = new ArrayList<>();

    private double currentRoundScore = 0.0;
    
    // Time Attack State
    private long startTime = 0;
    private long timeLimit = 15000; // 15 seconds default
    private int totalNodes = 0;
    private final Set<Integer> hitNodes = new HashSet<>();

    private ForgingVisuals visuals;
    private boolean isFinished = false;

    public ForgingSession(Location loc, UUID playerId) {
        this.anvilLocation = loc;
        this.playerId = playerId;
        this.blueprintTargetKey = new NamespacedKey(StarryForge.getInstance(), "blueprint_target");
    }

    public void setBlueprint(ItemStack blueprint) {
        this.targetBlueprint = blueprint;
        initializeVisuals();
    }

    private void initializeVisuals() {
        if (visuals != null) {
            visuals.cleanup();
        }
        Material displayMat = resolveDisplayMaterial(getTargetId());
        this.visuals = new ForgingVisuals(anvilLocation, new ItemStack(displayMat));
        updateIdleStatus();
    }

    private String getMessageSafe(String key, String def) {
        String msg = StarryForge.getInstance().getConfigManager().getMessage(key);
        if (msg.startsWith("<red>Missing message")) {
            return def;
        }
        return msg;
    }

    private void updateIdleStatus() {
        if (visuals != null) {
            if (activeRecipe != null) {
                // In progress
                double avgStar = calculateAverageStar();
                
                // Calculate current QTE bonus
                double avgQteScore = qteScores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                if (roundsCompleted == 0) avgQteScore = 0.5; // Neutral start
                
                // Use same logic as finishSession
                double baseMod = avgQteScore - 0.5;
                com.starryforge.features.core.ConfigManager config = StarryForge.getInstance().getConfigManager();
                double levelBonus = 0.0;
                Player p = StarryForge.getInstance().getServer().getPlayer(playerId);
                if (p != null) levelBonus = getLevelBonus(p);
                double maxBonus = config.getDouble("machines.astral_altar.quality.max_bonus_percent", 0.10) + levelBonus;
                
                double modifier = baseMod;
                if (modifier > maxBonus) modifier = maxBonus;
                double penaltyBase = -config.getDouble("machines.astral_altar.quality.penalty_base", 0.50);
                if (modifier < penaltyBase) modifier = penaltyBase;
                
                String modColor = modifier >= 0 ? "green" : "red";
                String modStr = String.format("%+.0f%%", modifier * 100);
                
                String defMsg = "<gradient:gold:yellow>锻造进行中</gradient>\n<gray>进度: <white>{phase}/{max_phase}\n<gray>当前品质: <yellow>{quality}★\n<gray>(<white>{avg} <gray>| {modifier})";
                
                visuals.updateStatus(MiniMessage.miniMessage().deserialize(
                    getMessageSafe("forging.session.status_in_progress", defMsg)
                        .replace("{phase}", String.valueOf(roundsCompleted))
                        .replace("{max_phase}", String.valueOf(totalRounds))
                        .replace("{quality}", String.format("%.1f", avgStar * (1.0 + modifier)))
                        .replace("{avg}", String.format("%.1f", avgStar))
                        .replace("{modifier}", "<" + modColor + ">" + modStr)
                ));
            } else {
                // Idle
                String defMsg = "<gradient:aqua:blue>等待材料</gradient>\n<gray>请投入精炼矿物";
                visuals.updateStatus(MiniMessage.miniMessage().deserialize(
                    getMessageSafe("forging.session.status_idle", defMsg)
                ));
            }
        }
    }

    private Material resolveDisplayMaterial(String targetId) {
        if (targetId == null) return Material.IRON_SWORD;

        for (Map.Entry<String, Material> entry : DISPLAY_MATERIALS.entrySet()) {
            if (targetId.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return Material.IRON_SWORD;
    }

    public String getTargetId() {
        if (targetBlueprint == null) return null;
        return PDCManager.getString(targetBlueprint, blueprintTargetKey);
    }

    public boolean hasBlueprint() {
        return targetBlueprint != null;
    }

    public boolean isForging() {
        return currentIngot != null && activeRecipe != null && !isFinished;
    }

    public void processMaterialInput(ItemStack ingot, ForgingRecipeManager.ForgingRecipe recipe) {
        // If not started, initialize
        if (activeRecipe == null) {
            this.activeRecipe = recipe;
            this.activeRecipeId = recipe.getResultItem();
            this.totalRounds = recipe.getStrikesRequired();
            this.roundsCompleted = 0;
            this.materialStars.clear();
            this.qteScores.clear();
            
            // 首次锻造引导
            Player p = StarryForge.getInstance().getServer().getPlayer(playerId);
            if (p != null) {
                NamespacedKey tutorialKey = new NamespacedKey(StarryForge.getInstance(), "tutorial_forging_shown");
                if (!p.getPersistentDataContainer().has(tutorialKey, PersistentDataType.BYTE)) {
                    p.showTitle(net.kyori.adventure.title.Title.title(
                        MiniMessage.miniMessage().deserialize(StarryForge.getInstance().getConfigManager().getMessage("forging.session.tutorial_title")),
                        MiniMessage.miniMessage().deserialize(StarryForge.getInstance().getConfigManager().getMessage("forging.session.tutorial_subtitle")),
                        net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(500), java.time.Duration.ofMillis(4000), java.time.Duration.ofMillis(1000))
                    ));
                    p.getPersistentDataContainer().set(tutorialKey, PersistentDataType.BYTE, (byte) 1);
                } else {
                    // 常规沉浸式标题
                    p.showTitle(net.kyori.adventure.title.Title.title(
                        MiniMessage.miniMessage().deserialize(StarryForge.getInstance().getConfigManager().getMessage("forging.session.start_title")),
                        MiniMessage.miniMessage().deserialize(StarryForge.getInstance().getConfigManager().getMessage("forging.session.start_subtitle")),
                        net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(200), java.time.Duration.ofMillis(1500), java.time.Duration.ofMillis(500))
                    ));
                }
            }
        } else {
            // Validate recipe match
            if (!activeRecipe.getId().equals(recipe.getId())) {
                return; // Should be handled by listener
            }
            
            // 每一轮开始的提示
            Player p = StarryForge.getInstance().getServer().getPlayer(playerId);
            if (p != null) {
                 p.showTitle(net.kyori.adventure.title.Title.title(
                    Component.empty(),
                    MiniMessage.miniMessage().deserialize(StarryForge.getInstance().getConfigManager().getMessage("forging.session.phase_title").replace("{phase}", String.valueOf(roundsCompleted + 1))),
                    net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(100), java.time.Duration.ofMillis(1000), java.time.Duration.ofMillis(200))
                ));
            }
        }

        // Add Material Star
        int star = 0;
        if (ingot.hasItemMeta()) {
             Integer s = ingot.getItemMeta().getPersistentDataContainer().get(NexusKeys.STAR_RATING, PersistentDataType.INTEGER);
             if (s != null) {
                 star = s;
             } else {
                 // Fallback: Check legacy key just in case
                 Integer legacy = ingot.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(StarryForge.getInstance(), "nexus_star_rating"), PersistentDataType.INTEGER);
                 if (legacy != null) star = legacy;
             }
        }
        materialStars.add(star);
        
        Player p = StarryForge.getInstance().getServer().getPlayer(playerId);
        if (p != null) {
            p.sendMessage(MiniMessage.miniMessage().deserialize(StarryForge.getInstance().getConfigManager().getMessage("forging.session.material_input").replace("{star}", String.valueOf(star))));
        }

        // Start Round
        this.currentIngot = ingot;
        this.currentRoundScore = 0.0;
        
        this.timeLimit = StarryForge.getInstance().getConfigManager().getInt("machines.astral_altar.settings.time_limit_ms", 20000); 
        this.startTime = System.currentTimeMillis();
        
        this.hitNodes.clear();
        this.totalNodes = StarryForge.getInstance().getConfigManager().getInt("machines.astral_altar.process.nodes_per_round", 5);

        if (visuals != null) {
            visuals.spawnConstellation(totalNodes, 1.2);
            visuals.setStatusVisible(false); // Hide text during QTE
        }

        StarryForge.getInstance().getForgingManager().markDirty();
    }

    public void tick() {
        if (visuals == null) return;
        visuals.tick();

        if (isForging()) {
            // Check timeout
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed > timeLimit) {
                // Time's up! Finish round with what we have.
                finishRound(null); 
            } else {
                // ActionBar Update
                if (playerId != null) {
                    Player p = StarryForge.getInstance().getServer().getPlayer(playerId);
                    if (p != null && p.isOnline()) {
                        double progress = (double) hitNodes.size() / totalNodes;
                        double timeRem = (timeLimit - elapsed) / 1000.0;
                        
                        String bar = "||||||||||||||||||||";
                        int filled = (int)(progress * bar.length());
                        String progressStr = "<green>" + bar.substring(0, filled) + "<gray>" + bar.substring(filled);
                        
                        p.sendActionBar(MiniMessage.miniMessage().deserialize(
                            StarryForge.getInstance().getConfigManager().getMessage("forging.session.actionbar_timer")
                                .replace("{time}", String.format("%.1f", timeRem))
                                .replace("{progress}", progressStr)
                        ));
                    }
                }
            }
        }
    }

    public void handleHit(Player player) {
        if (!isForging()) return;
        
        // Validate Player
        if (!player.getUniqueId().equals(playerId)) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(StarryForge.getInstance().getConfigManager().getMessage("forging.session.not_owner")));
            return;
        }

        // Validate Tool (Hammer)
        ItemStack tool = player.getInventory().getItemInMainHand();
        int hammerTier = getHammerTier(tool);
        if (hammerTier <= 0) {
             player.sendMessage(MiniMessage.miniMessage().deserialize(StarryForge.getInstance().getConfigManager().getMessage("forging.session.need_hammer")));
             return;
        }

        // Dot Product Check
        Location eyeLoc = player.getEyeLocation();
        org.bukkit.util.Vector lookDir = eyeLoc.getDirection();
        
        java.util.List<Location> nodes = visuals.getNodeLocations();
        int bestIndex = -1;
        com.starryforge.features.core.ConfigManager config = StarryForge.getInstance().getConfigManager();
        double bestDot = config.getDouble("machines.astral_altar.process.aim_threshold", 0.96);

        for (int i = 0; i < nodes.size(); i++) {
            if (hitNodes.contains(i)) continue; // Skip already hit nodes

            Location nodeLoc = nodes.get(i);
            // Check distance first to avoid hitting far nodes through walls
            double maxDist = config.getDouble("machines.astral_altar.settings.interaction_distance", 6.0);
            if (nodeLoc.distanceSquared(eyeLoc) > maxDist * maxDist) continue;

            org.bukkit.util.Vector toNode = nodeLoc.toVector().subtract(eyeLoc.toVector()).normalize();
            double dot = lookDir.dot(toNode);
            
            if (dot > bestDot) {
                bestDot = dot;
                bestIndex = i;
            }
        }

        if (bestIndex != -1) {
            // Valid Hit
            hitNodes.add(bestIndex);
            if (visuals != null) {
                visuals.setNodeHit(bestIndex);
            }

            // Score Calculation
            // Base score per node (1.0 / totalNodes)
            double nodeScore = 1.0 / totalNodes;
            
            // Tier bonus (slight speed/tolerance bonus logic? For now simple multiplier)
            // But QTE score should be normalized 0.0-1.0.
            // Let's just sum up 1.0 for perfect round.
            currentRoundScore += nodeScore;

            // Check Win Condition
            if (hitNodes.size() >= totalNodes) {
                // All hit! Perfect bonus?
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed < timeLimit) {
                    double timeBonus = (double)(timeLimit - elapsed) / 1000.0; // Seconds left
                    // Bonus: max +10% score?
                    // If score > 1.0, it helps average.
                    currentRoundScore += (timeBonus * StarryForge.getInstance().getConfigManager().getDouble("machines.astral_altar.quality.time_bonus_multiplier", 0.05)); 
                    player.sendMessage(MiniMessage.miniMessage().deserialize(StarryForge.getInstance().getConfigManager().getMessage("forging.session.perfect_hit")));
                }
                finishRound(player);
            }
        }
    }

    private void finishRound(Player player) {
        // 1. Record Score
        qteScores.add(currentRoundScore);
        roundsCompleted++;
        
        // 2. Cleanup Round
        this.currentIngot = null; // Ready for next
        if (visuals != null) {
            visuals.cleanup(); // Remove constellation
            visuals = new ForgingVisuals(anvilLocation, new ItemStack(resolveDisplayMaterial(getTargetId()))); // Re-init base visuals (hologram)
            // Wait, we destroyed visuals, need to rebuild.
            // Ideally we just clear particles. ForgingVisuals.cleanup() removes entities.
            // We should keep Hologram?
            // Let's just re-create visuals to be safe and update status.
            initializeVisuals();
            visuals.setStatusVisible(true);
        }

        // 3. Check Full Completion
        if (roundsCompleted >= totalRounds) {
            finishSession(player);
        } else {
            // Need more rounds
            if (player != null) {
                player.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<yellow>阶段完成！请继续投入材料 (" + roundsCompleted + "/" + totalRounds + ")"
                ));
            }
            updateIdleStatus();
        }
        
        StarryForge.getInstance().getForgingManager().markDirty();
    }

    private boolean isCompleted = false;
    private ItemStack resultItem;

    public boolean isCompleted() {
        return isCompleted;
    }
    
    public ItemStack getResultItem() {
        return resultItem;
    }
    
    private double getLevelBonus(Player p) {
        // TODO: Implement Forging Level scaling
        return 0.0;
    }

    private void finishSession(Player player) {
        isFinished = true;
        isCompleted = true; // Mark as waiting for retrieval
        
        StarryForge plugin = StarryForge.getInstance();
        
        // Find player if null
        if (player == null) {
            player = plugin.getServer().getPlayer(playerId);
            if (player != null && (!player.isOnline() || player.getLocation().distance(anvilLocation) > 10)) {
                player = null;
            }
        }

        // Calculate Final Stats
        double avgMaterialStar = calculateAverageStar();
        double avgQteScore = qteScores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        
        // Map QTE to Modifier
        // Baseline: (Score - 0.5)
        // Bonus Cap: +10% (+0.10) + Level Bonus
        // Penalty Cap: -50% (-0.50)
        double baseMod = avgQteScore - 0.5;
        double levelBonus = player != null ? getLevelBonus(player) : 0.0;
        double maxBonus = 0.10 + levelBonus;
        
        double modifier = baseMod;
        if (modifier > maxBonus) modifier = maxBonus;
        if (modifier < -0.50) modifier = -0.50;

        // Final Star Calculation
        double rawFinalStar = avgMaterialStar * (1.0 + modifier);
        int finalStar;
        
        if (modifier < 0) {
            finalStar = (int) Math.floor(rawFinalStar);
        } else {
            finalStar = (int) Math.round(rawFinalStar);
        }
        if (finalStar < 1) finalStar = 1; // Min 1 star

        if (player != null) {
            String msg = plugin.getConfigManager().getMessage("forging.process.finished")
                    .replace("{score}", String.format("%.1f", rawFinalStar)); // Reuse score placeholder for star
            player.sendMessage(MiniMessage.miniMessage().deserialize(msg));
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                "<gray>材料均质: <yellow>" + String.format("%.1f", avgMaterialStar) + 
                " <gray>| QTE修正: <" + (modifier >= 0 ? "green" : "red") + ">" + String.format("%+.0f%%", modifier * 100) +
                " <gray>| 最终评级: <gold>" + finalStar + "★"
            ));
        }

        // Generate Result Item
        ItemStack result = plugin.getItemManager().getItem(activeRecipe.getResultItem());
        if (result == null) {
            result = new ItemStack(Material.IRON_SWORD); // Fallback
        }
        
        // Apply Tier Visuals
        org.bukkit.inventory.meta.ItemMeta resultMeta = result.getItemMeta();
        
        // 1. Set NexusCore Standard Rating
        resultMeta.getPersistentDataContainer().set(NexusKeys.STAR_RATING, PersistentDataType.INTEGER, finalStar);
        
        // 2. Set Legacy Key (for compatibility with older StarryForge modules)
        NamespacedKey legacyKey = new NamespacedKey(StarryForge.getInstance(), "nexus_has_star");
        resultMeta.getPersistentDataContainer().set(legacyKey, PersistentDataType.INTEGER, 1);
        
        result.setItemMeta(resultMeta);
        
        // 3. Apply Visuals (Lore/Name)
        // Check if item is a Custom/Fixed Quality item to preserve its name color
        boolean isCustom = PDCManager.getString(result, Keys.ITEM_ID_KEY) != null || 
                           PDCManager.getString(result, NexusKeys.ITEM_ID) != null;
                           
        // overwriteColor = !isCustom (If custom, do NOT overwrite color)
        com.nexuscore.NexusCore.getInstance().getTierVisuals().applyVisuals(result, finalStar, !isCustom);
        this.resultItem = result;
        
        // Visuals: Completion Effect
        if (visuals != null) {
            visuals.cleanup(); // Clean old visuals
            // Re-init with result item display
            visuals = new ForgingVisuals(anvilLocation, result);
            visuals.playCompletionEffect();
            
            visuals.updateStatus(MiniMessage.miniMessage().deserialize(
                "<gradient:gold:yellow>锻造完成</gradient>\n" +
                "<gray>右键点击取出成品"
            ));
        }

        // Do NOT drop item or end session yet. Wait for player interaction.
        StarryForge.getInstance().getForgingManager().markDirty();
    }
    
    private double calculateAverageStar() {
        if (materialStars.isEmpty()) return 0.0;
        return materialStars.stream().mapToInt(Integer::intValue).average().orElse(0.0);
    }

    private int getHammerTier(ItemStack item) {
        if (item == null) return 0;
        String id = PDCManager.getString(item, Keys.ITEM_ID_KEY);
        if (id == null) return 0;

        return switch (id) {
            case "FORGING_HAMMER", "FORGING_HAMMER_T1" -> 1;
            case "FORGING_HAMMER_T2" -> 2;
            case "TITANS_HAMMER", "FORGING_HAMMER_T3" -> 3;
            default -> 0;
        };
    }

    public void cleanup() {
        if (visuals != null) {
            visuals.cleanup();
            visuals = null;
        }
    }

    // ===== Restore Methods (for file persistence) =====

    public void restoreBlueprint(ItemStack blueprint) {
        this.targetBlueprint = blueprint;
    }

    public void restoreProgress(int phase, int maxPhases, double quality) {
        // Legacy restore support
        this.roundsCompleted = phase;
        this.totalRounds = maxPhases;
        // qualityScore in legacy was single double. We need list.
        // We can't fully restore. Just reset or approximate.
    }

    public void restoreLists(List<Integer> stars, List<Double> scores) {
        this.materialStars.clear();
        if (stars != null) this.materialStars.addAll(stars);
        
        this.qteScores.clear();
        if (scores != null) this.qteScores.addAll(scores);
    }

    public void restoreForging(ItemStack ingot, String recipeId) {
        this.currentIngot = ingot;
        this.activeRecipeId = recipeId;
        if (recipeId != null) {
            this.activeRecipe = StarryForge.getInstance().getForgingRecipeManager()
                    .getRecipes().get(recipeId);
        }
        
        // Only reset timer if we are actually mid-round (ingot present)
        if (ingot != null && this.activeRecipe != null) {
            this.totalRounds = activeRecipe.getStrikesRequired();
            this.totalNodes = StarryForge.getInstance().getConfigManager().getInt("machines.astral_altar.process.nodes_per_round", 5);
            this.startTime = System.currentTimeMillis(); // Reset timer on load
            this.timeLimit = StarryForge.getInstance().getConfigManager().getInt("machines.astral_altar.settings.time_limit_ms", 20000);
        } else if (this.activeRecipe != null) {
             // We are between rounds, but have an active recipe
             this.totalRounds = activeRecipe.getStrikesRequired();
        }
    }

    public void initializeVisualsIfNeeded() {
        if (targetBlueprint != null && visuals == null) {
            initializeVisuals();
            if (isForging()) {
                visuals.spawnConstellation(totalNodes, 1.2);
                visuals.setStatusVisible(false);
            }
        }
    }

    // ===== Getters =====

    public Location getLocation() { return anvilLocation; }
    public UUID getPlayerId() { return playerId; }
    public ItemStack getTargetBlueprint() { return targetBlueprint; }
    public ItemStack getCurrentIngot() { return currentIngot; }
    public String getActiveRecipeId() { return activeRecipeId; }
    public double getQualityScore() { return currentRoundScore; } // Return current round score
    public boolean isFinished() { return isFinished; }
    
    // For persistence
    public int getCurrentPhase() { return roundsCompleted; }
    public int getMaxPhases() { return totalRounds; }
    public List<Integer> getMaterialStars() { return materialStars; }
    public List<Double> getQteScores() { return qteScores; }
}
