package com.starryforge.features.items;

import com.starryforge.StarryForge;
import com.starryforge.features.mining.LithicInsightEnchantment;
import com.starryforge.features.core.PDCManager;
import com.starryforge.utils.Keys;
import com.starryforge.utils.LogUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import com.nexuscore.util.NexusKeys;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SFItemManager {

    private final StarryForge plugin;
    private final Map<String, ItemStack> customItems = new HashMap<>();
    private final MiniMessage mm = MiniMessage.miniMessage();

    public SFItemManager(StarryForge plugin) {
        this.plugin = plugin;
        loadItems();
    }

    public void reload() {
        customItems.clear();
        loadItems();
    }

    private void loadItems() {
        File itemsFile = new File(plugin.getDataFolder(), "items.yml");
        if (!itemsFile.exists()) {
            plugin.saveResource("items.yml", false);
        }

        YamlConfiguration itemsConfig = YamlConfiguration.loadConfiguration(itemsFile);

        // Merge with default items.yml from JAR to ensure new items/fields are present
        try {
            java.io.InputStream defStream = plugin.getResource("items.yml");
            if (defStream != null) {
                YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(
                        new java.io.InputStreamReader(defStream, java.nio.charset.StandardCharsets.UTF_8));

                boolean changed = false;
                // Recursive merge or just top-level items? items.yml structure is depth 2
                // (items -> ID -> fields)
                // We should iterate keys under 'items'
                if (defConfig.contains("items")) {
                    ConfigurationSection defItems = defConfig.getConfigurationSection("items");
                    if (!itemsConfig.contains("items")) {
                        itemsConfig.createSection("items");
                    }
                    ConfigurationSection currentItems = itemsConfig.getConfigurationSection("items");

                    for (String key : defItems.getKeys(false)) {
                        if (!currentItems.contains(key)) {
                            currentItems.set(key, defItems.getConfigurationSection(key));
                            changed = true;
                        } else {
                            // Check for missing fields in existing item (e.g. model_data, display_name, star)
                            ConfigurationSection defItemSection = defItems.getConfigurationSection(key);
                            ConfigurationSection currItemSection = currentItems.getConfigurationSection(key);
                            for (String field : defItemSection.getKeys(false)) {
                                if (!currItemSection.contains(field)) {
                                    currItemSection.set(field, defItemSection.get(field));
                                    changed = true;
                                }
                            }
                        }
                    }
                }

                if (changed) {
                    itemsConfig.save(itemsFile);
                    plugin.getLogger().info("Updated items.yml with new default values.");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to merge default items.yml: " + e.getMessage());
        }

        ConfigurationSection itemsSection = itemsConfig.getConfigurationSection("items");
        if (itemsSection == null) {
            plugin.getLogger().warning("No 'items' section found in items.yml");
            return;
        }

        for (String key : itemsSection.getKeys(false)) {
            ConfigurationSection section = itemsSection.getConfigurationSection(key);
            if (section == null)
                continue;

            String materialName = section.getString("material");
            Material material = Material.getMaterial(materialName != null ? materialName : "STONE");
            if (material == null) {
                plugin.getLogger().warning("Invalid material for item " + key + ": " + materialName);
                continue;
            }

            String langKeyBase = "items." + key.toLowerCase();
            if ("SLUICE_MACHINE".equals(key)) {
                langKeyBase = "items.sluice_machine.base";
            }

            String nameKey = langKeyBase + ".name";
            String name = plugin.getConfigManager().getMessage(nameKey);

            if (name == null || name.startsWith("<red>Missing message:")) {
                // Try 'display_name' first (legacy/manual override)
                name = section.getString("display_name");
                if (name == null) {
                    name = section.getString("name", "<gray>" + key);
                }
            }

            List<String> loreLines = plugin.getConfigManager().getMessageList(langKeyBase + ".lore");
            if (loreLines == null || loreLines.isEmpty()) {
                loreLines = section.getStringList("lore");
            }

            int modelData = section.getInt("model_data", 0);
            boolean hasStar = section.getBoolean("star", true);
            int defaultStar = section.getInt("default_star", 1); // Allow configuring default star level

            ItemStack item = new ItemStack(material);

            // Special handling for Greatsword: Apply 1.21 Data Components for native blocking
            // We MUST construct the full item NBT (including Name, Lore, PDC) and apply it via modifyItemStack
            // because standard setItemMeta() might strip unknown Data Components like 'consumable' or 'blocks_attacks'.
            if ("greatsword".equalsIgnoreCase(key)) {
                try {
                    // We use custom_data (PublicBukkitValues) to store PDC keys manually.
                    // Dynamically get the key from Keys class to ensure match with PDCManager
                    String itemIdKey = Keys.ITEM_ID_KEY.toString(); // e.g. "nexus:item_id" or "starryforge:sf_item_id"
                    
                    // PublicBukkitValues expects "namespace:key": "value"
                    // We also include legacy keys just in case
                    String customData = "{PublicBukkitValues:{\"" + itemIdKey + "\":\"greatsword\", \"starryforge:item_id\":\"greatsword\"}}";

                    StringBuilder sb = new StringBuilder();
                    sb.append(material.getKey().toString()); // minecraft:iron_sword
                    sb.append("[");
                    sb.append("blocks_attacks={damage_reductions:[{base:0,factor:0.5}]},");
                    sb.append("consumable={consume_seconds:72000, animation:'block', has_consume_particles:false, can_always_use:true},");
                    
                    // Note: We DO NOT set custom_model_data here manually.
                    // ItemMeta.setCustomModelData() handles it correctly and safely across versions.
                    
                    // Note: We DO NOT set custom_name or lore here.
                    // We let the standard setItemMeta logic handle it below to ensure correct formatting (MiniMessage)
                    // and to avoid JSON escaping issues in the NBT string.
                    // We assume setItemMeta will preserve the 'blocks_attacks' and 'consumable' components.
                    
                    sb.append("custom_data=").append(customData);
                    sb.append("]");
                    
                    String itemDef = sb.toString();
                    // LogUtil.debug("Constructing Greatsword with NBT: " + itemDef);
                    
                    // 4. Generate Item
                    @SuppressWarnings("deprecation")
                    ItemStack tempItem = org.bukkit.Bukkit.getUnsafe().modifyItemStack(item, itemDef);
                    item = tempItem;
                    
                    // 5. Allow fall-through to standard processing (Meta, PDC, Attributes)
                    // This ensures Name/Lore are set correctly.
                    
                } catch (Throwable t) {
                    plugin.getLogger().warning("Failed to apply Data Components to Greatsword: " + t.getMessage());
                    t.printStackTrace();
                    // Fallback to normal flow if failed
                }
            }

            // Frostsigh Blade / Oblivion: Add consumable component for "Hold to Charge" (Iaido Stance)
            // Removed from here to prevent overwrite by setItemMeta

            // 1. Frostsigh Blade / Oblivion: Inject consumable component FIRST
            // This is critical because modifyItemStack resets ItemMeta if we apply it later
            if ("frost_sigh_blade".equalsIgnoreCase(key) || "frostsigh_oblivion".equalsIgnoreCase(key)) {
                try {
                    String itemIdKey = Keys.ITEM_ID_KEY.toString();
                    String customData = "{PublicBukkitValues:{\"" + itemIdKey + "\":\"" + key + "\", \"starryforge:sf_item_id\":\"" + key + "\"}}";

                    StringBuilder sb = new StringBuilder();
                    sb.append(material.getKey().toString());
                    sb.append("[");
                    // Use 'bow' animation for Charging Stance
                    sb.append("consumable={consume_seconds:72000, animation:'bow', has_consume_particles:false, can_always_use:true},");
                    
                    sb.append("custom_data=").append(customData);
                    sb.append("]");
                    
                    @SuppressWarnings("deprecation")
                    ItemStack tempItem = org.bukkit.Bukkit.getUnsafe().modifyItemStack(item, sb.toString());
                    item = tempItem;
                    
                } catch (Throwable t) {
                    plugin.getLogger().warning("Failed to apply Data Components to Frostsigh: " + t.getMessage());
                }
            }

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(mm.deserialize(name));
                if (modelData != 0) {
                    meta.setCustomModelData(modelData);
                }

                // 设置 ID
                PDCManager.setString(item, Keys.ITEM_ID_KEY, key);
                // 同时也直接在 meta 上设置，以符合当前逻辑
                meta.getPersistentDataContainer().set(Keys.ITEM_ID_KEY, PersistentDataType.STRING, key);
                // NexusCore Unified ID
                meta.getPersistentDataContainer().set(NexusKeys.ITEM_ID, PersistentDataType.STRING, key);

                // Set CraftEngine identifier for CE to recognize and apply textures
                org.bukkit.NamespacedKey ceIdKey = new org.bukkit.NamespacedKey("craft_engine", "id");
                String ceItemId = "starryforge:" + key.toLowerCase();
                meta.getPersistentDataContainer().set(ceIdKey, PersistentDataType.STRING, ceItemId);

                // Set Star Flag & Rating
                if (hasStar) {
                    // Set flag for legacy support
                    org.bukkit.NamespacedKey starKey = new org.bukkit.NamespacedKey(plugin, "nexus_has_star");
                    meta.getPersistentDataContainer().set(starKey, PersistentDataType.INTEGER, 1);
                    
                    // Set NexusCore Standard Rating (Default to config value or 1)
                    meta.getPersistentDataContainer().set(NexusKeys.STAR_RATING, PersistentDataType.INTEGER, defaultStar);
                }
                
                // Add optimization flag for FrostsighListener
                if ("frost_sigh_blade".equalsIgnoreCase(key) || "frostsigh_oblivion".equalsIgnoreCase(key)) {
                    org.bukkit.NamespacedKey flagKey = new org.bukkit.NamespacedKey(plugin, "sf_consumable_injected");
                    meta.getPersistentDataContainer().set(flagKey, PersistentDataType.BYTE, (byte) 1);
                }

                List<Component> lore = new ArrayList<>();
                for (String line : loreLines) {
                    lore.add(mm.deserialize(line));
                }
                meta.lore(lore);

                item.setItemMeta(meta);
                
                // Apply NexusCore RPG Attributes
            }
            customItems.put(key, item);
        }

        // 特殊情况: 附魔书 (动态生成，纯 YAML 较难实现)
        registerEnchantedBook();

        // 注册洗矿台变种 (I, II, III)
        registerSluiceVariants();

        // 注册锻造相关物品
        registerForgingItems();

        LogUtil.debug("Loaded " + customItems.size() + " items from items.yml");
    }

    private void registerSluiceVariants() {
        for (int i = 1; i <= 3; i++) {
            String key = "SLUICE_MACHINE_" + toRoman(i);
            
            // Force overwrite to ensure correct lang keys are used
            ItemStack item = new ItemStack(Material.BARREL);
            ItemMeta meta = item.getItemMeta();
            String nameKey = "items.sluice_machine.tier_" + i + ".name";
            String name = plugin.getConfigManager().getMessage(nameKey);

            meta.displayName(mm.deserialize(name));

            // 设置自定义模型数据区分纹理
            // I: 3001, II: 3002, III: 3003 (假设值，需资源包配合)
            meta.setCustomModelData(3000 + i);

            List<String> loreList = plugin.getConfigManager()
                    .getMessageList("items.sluice_machine.tier_" + i + ".lore");
            List<Component> lore = new ArrayList<>();
            for (String line : loreList) {
                lore.add(mm.deserialize(line));
            }
            meta.lore(lore);

            PDCManager.setString(item, Keys.ITEM_ID_KEY, key);
            meta.getPersistentDataContainer().set(Keys.ITEM_ID_KEY, PersistentDataType.STRING, key);

            item.setItemMeta(meta);
            customItems.put(key, item);
        }
    }

    private String toRoman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> String.valueOf(n);
        };
    }

    private void registerForgingItems() {
        // T1 Hammer
        // 尝试从配置加载，如果没有则使用代码默认值注册
        ItemStack t1 = registerManualItem("FORGING_HAMMER_T1", Material.IRON_PICKAXE, 6011, "items.forging_hammer_t1");
        applyHammerTier(t1, 1);

        // mappings for Legacy
        if (!customItems.containsKey("FORGING_HAMMER")) {
            customItems.put("FORGING_HAMMER", t1.clone()); // Map Legacy ID to T1 item? Or just different ID same stats?
            // If we duplicate, we need to ensure ID key is legacy.
            ItemStack legacy = t1.clone();
            PDCManager.setString(legacy, Keys.ITEM_ID_KEY, "FORGING_HAMMER");
            customItems.put("FORGING_HAMMER", legacy);
        }

        // T2 Hammer
        ItemStack t2 = registerManualItem("FORGING_HAMMER_T2", Material.DIAMOND_PICKAXE, 6012,
                "items.forging_hammer_t2");
        applyHammerTier(t2, 2);

        // T3 Hammer
        ItemStack t3 = registerManualItem("FORGING_HAMMER_T3", Material.NETHERITE_PICKAXE, 6013,
                "items.forging_hammer_t3");
        applyHammerTier(t3, 3);

        // Titan's Hammer (Legacy T3 variant)
        ItemStack titans = registerManualItem("TITANS_HAMMER", Material.NETHERITE_PICKAXE, 4002, "items.titans_hammer");
        applyHammerTier(titans, 3); // Titan is T3

        // Astral Forge Node
        registerManualItem("ASTRAL_FORGE_NODE", Material.SMITHING_TABLE, 6001, "items.astral_forge_node");

        // Blank Blueprint
        registerManualItem("BLANK_BLUEPRINT", Material.PAPER, 6020, "items.blank_blueprint");

        // Equipment Embryo
        registerManualItem("EQUIPMENT_EMBRYO", Material.CLAY_BALL, 4004, "items.equipment_embryo");
    }

    private void applyHammerTier(ItemStack item, int tier) {
        if (item == null)
            return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "hammer_tier"),
                    org.bukkit.persistence.PersistentDataType.INTEGER, tier);
            item.setItemMeta(meta);
        }
    }

    private ItemStack registerManualItem(String id, Material mat, int modelData, String langKey) {
        if (!customItems.containsKey(id)) {
            // Create default if not in yaml
            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();

            String name = plugin.getConfigManager().getMessage(langKey + ".name");
            if (name == null || name.startsWith("<red>Missing"))
                name = "<gray>" + id;

            meta.displayName(mm.deserialize(name));
            if (modelData > 0)
                meta.setCustomModelData(modelData);

            List<String> loreLines = plugin.getConfigManager().getMessageList(langKey + ".lore");
            if (loreLines != null && !loreLines.isEmpty()) {
                List<Component> lore = new ArrayList<>();
                for (String line : loreLines) {
                    lore.add(mm.deserialize(line));
                }
                meta.lore(lore);
            }

            PDCManager.setString(item, Keys.ITEM_ID_KEY, id);
            meta.getPersistentDataContainer().set(Keys.ITEM_ID_KEY, PersistentDataType.STRING, id);

            item.setItemMeta(meta);
            customItems.put(id, item);
        }
        // Always return the item (whether newly created or loaded from YAML)
        return customItems.get(id);
    }

    private void registerEnchantedBook() {
        String key = "ENCHANTED_BOOK_LITHIC_INSIGHT";
        
        // Always recreate to ensure lang updates
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta bookMeta = book.getItemMeta();
        if (bookMeta != null) {
            // 从 lang 文件加载名称和 Lore
            String name = plugin.getConfigManager().getMessage("items.enchanted_book.lithic_insight.name");
            bookMeta.displayName(mm.deserialize(name));
            bookMeta.setCustomModelData(1003);
            PDCManager.setString(book, Keys.ITEM_ID_KEY, key);

            List<String> loreList = plugin.getConfigManager()
                    .getMessageList("items.enchanted_book.lithic_insight.lore");
            List<Component> lore = new ArrayList<>();
            for (String line : loreList) {
                lore.add(mm.deserialize(line));
            }
            bookMeta.lore(lore);
            book.setItemMeta(bookMeta);
        }

        // 应用逻辑
        LithicInsightEnchantment.apply(book, 1);
        customItems.put(key, book);
    }

    public ItemStack getItem(String key) {
        if (customItems.containsKey(key)) {
            ItemStack item = customItems.get(key).clone();
            // Ensure attributes are up-to-date
            try {
                if (org.bukkit.Bukkit.getPluginManager().isPluginEnabled("NexusCore")) {
                    com.nexuscore.NexusCore.getInstance().getRpgManager().updateItemAttributes(item);
                }
            } catch (Exception e) {
                // Ignore errors during get (e.g. if NexusCore not ready)
                plugin.getLogger().warning("[SFItemManager] Failed to update attributes for " + key + ": " + e.getMessage());
            }
            return item;
        }
        return null;
    }

    public Set<String> getItemNames() {
        return customItems.keySet();
    }

    public List<ItemStack> getAllItems() {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack item : customItems.values()) {
            ItemStack clone = item.clone();
             // Ensure attributes are up-to-date
            try {
                if (org.bukkit.Bukkit.getPluginManager().isPluginEnabled("NexusCore")) {
                    com.nexuscore.NexusCore.getInstance().getRpgManager().updateItemAttributes(clone);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[SFItemManager] Failed to update attributes in getAllItems: " + e.getMessage());
            }
            items.add(clone);
        }
        return items;
    }

    public ItemStack createUnidentifiedCluster(int stars) {
        ItemStack item = getItem("UNIDENTIFIED_CLUSTER");
        if (item == null)
            return new ItemStack(Material.RAW_IRON);

        // 强制星级范围 1-5
        int finalStars = Math.max(1, Math.min(5, stars));

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // 不再存储具体的 Quality (0-100)，只存储 Star (1-5)，以便堆叠
            meta.getPersistentDataContainer().set(Keys.CLUSTER_QUALITY_KEY, PersistentDataType.INTEGER, finalStars);
            item.setItemMeta(meta); // Save PDC first

            // Unidentified Cluster is a specialized item (Custom ID), but here we WANT it
            // to have color based on stars?
            // "UNIDENTIFIED_CLUSTER" is a custom item.
            // In original code:
            // NamedTextColor color = getStarColor(finalStars);
            // meta.displayName(displayName.color(color));
            // So it ACTS like a Variable Quality item for color purposes.

            com.nexuscore.NexusCore.getInstance().getTierVisuals().applyVisuals(item, finalStars, true);
        }
        return item;
    }

    public boolean isCustomItem(ItemStack item, String key) {
        if (item == null || !item.hasItemMeta())
            return false;

        String id = PDCManager.getString(item, Keys.ITEM_ID_KEY);
        return key.equals(id);
    }

    public String getCustomItemId(ItemStack item) {
        return PDCManager.getString(item, Keys.ITEM_ID_KEY);
    }

    public void applyOreStar(ItemStack item, int stars) {
        if (item == null || item.getType() == Material.AIR)
            return;

        int finalStars = Math.max(1, Math.min(5, stars));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(Keys.ORE_STAR_KEY, PersistentDataType.INTEGER, finalStars);
            item.setItemMeta(meta);

            // Determine if variable quality (Vanilla Ore = true, Custom/Specialty = false)
            boolean isCustom = PDCManager.getString(item, Keys.ITEM_ID_KEY) != null;
            // [New] Check BiomeGifts ID (using string key to avoid dependency)
            if (!isCustom) {
                org.bukkit.NamespacedKey biomeGiftKey = org.bukkit.NamespacedKey.fromString("biomegifts:id");
                if (biomeGiftKey != null
                        && meta.getPersistentDataContainer().has(biomeGiftKey, PersistentDataType.STRING)) {
                    isCustom = true;
                }
            }

            com.nexuscore.NexusCore.getInstance().getTierVisuals().applyVisuals(item, finalStars, !isCustom);
        }
    }

    public int rollSluiceStars(int clusterQuality) {
        java.util.Random random = new java.util.Random();
        int roll = random.nextInt(100);

        // Probability Table based on Cluster Quality
        if (clusterQuality <= 20) {
            if (roll < 80)
                return 1;
            if (roll < 95)
                return 2;
            return 3;
        } else if (clusterQuality <= 40) {
            if (roll < 50)
                return 1;
            if (roll < 85)
                return 2;
            if (roll < 99)
                return 3;
            return 4;
        } else if (clusterQuality <= 60) {
            if (roll < 30)
                return 1;
            if (roll < 70)
                return 2;
            if (roll < 95)
                return 3;
            if (roll < 99)
                return 4;
            return 5;
        } else if (clusterQuality <= 80) {
            if (roll < 10)
                return 1;
            if (roll < 40)
                return 2;
            if (roll < 80)
                return 3;
            if (roll < 95)
                return 4;
            return 5;
        } else { // 81-100
            if (roll < 10)
                return 2;
            if (roll < 40)
                return 3;
            if (roll < 80)
                return 4;
            return 5;
        }
    }

}
