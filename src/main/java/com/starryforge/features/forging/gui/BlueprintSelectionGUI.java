package com.starryforge.features.forging.gui;

import com.starryforge.features.forging.ForgingRecipeManager;
import com.starryforge.utils.Keys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.starryforge.StarryForge;
import com.starryforge.features.core.PDCManager;
import com.starryforge.features.ironheart.config.BlueprintConfig;

import java.util.*;

/**
 * 蓝图选择 GUI
 * <p>
 * 只有通过右键星魂台打开的 GUI 才能将蓝图写入为工程蓝图。
 */
public class BlueprintSelectionGUI implements Listener {

    private final StarryForge plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final NamespacedKey blueprintTargetKey;

    private enum Category {
        MENU, ASSEMBLY, FORGING
    }

    private final Set<UUID> switchingMenus = new HashSet<>();

    private final Map<UUID, Category> playerCategories = new HashMap<>();

    public BlueprintSelectionGUI(StarryForge plugin) {
        this.plugin = plugin;
        this.blueprintTargetKey = new NamespacedKey(plugin, "blueprint_target");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private Component getTitle(Category category) {
        String key = switch (category) {
            case MENU -> "forging.gui.title_menu"; // "Blueprint Selection"
            case ASSEMBLY -> "forging.gui.title_assembly"; // "Assembly Blueprints"
            case FORGING -> "forging.gui.title_forging"; // "Mythic Blueprints"
        };
        // Fallback if keys missing
        String defaultTitle = switch (category) {
            case MENU -> "<dark_gray>蓝图系统";
            case ASSEMBLY -> "<dark_gray>基础装备蓝图";
            case FORGING -> "<dark_gray>神话武器蓝图";
        };

        String val = plugin.getConfigManager().getMessage(key);
        if (val == null || val.startsWith("Missing"))
            return mm.deserialize(defaultTitle);
        return mm.deserialize(val);
    }

    public void openGUI(Player player) {
        openMainMenu(player);
    }

    private void switchMenu(Player player, Category category, Inventory inv) {
        switchingMenus.add(player.getUniqueId());
        playerCategories.put(player.getUniqueId(), category);
        player.openInventory(inv);
        switchingMenus.remove(player.getUniqueId());
    }

    private void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 45, getTitle(Category.MENU));

        // Fill background
        ItemStack bg = createBackground();
        for (int i = 0; i < 45; i++)
            inv.setItem(i, bg);

        // Assembly Category Icon (Slot 20) - Using Iron Chestplate as icon
        ItemStack assemblyIcon = new ItemStack(Material.IRON_CHESTPLATE);
        ItemMeta asmMeta = assemblyIcon.getItemMeta();
        asmMeta.displayName(mm.deserialize("<!i><#ffaa00>基础装备蓝图"));
        List<Component> asmLore = new ArrayList<>();
        asmLore.add(mm.deserialize("<!i><gray>点击查看组装台可制作的"));
        asmLore.add(mm.deserialize("<!i><gray>基础装备与组件蓝图"));
        asmMeta.lore(asmLore);
        asmMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        assemblyIcon.setItemMeta(asmMeta);
        inv.setItem(20, assemblyIcon);

        // Forging Category Icon (Slot 24) - Using Nether Star as icon
        ItemStack forgingIcon = new ItemStack(Material.NETHER_STAR);
        ItemMeta forgMeta = forgingIcon.getItemMeta();
        forgMeta.displayName(mm.deserialize("<!i><#ff5555>神话武器蓝图"));
        List<Component> forgLore = new ArrayList<>();
        forgLore.add(mm.deserialize("<!i><gray>点击查看星魂祭坛可制作的"));
        forgLore.add(mm.deserialize("<!i><gray>神话级武器蓝图"));
        forgMeta.lore(forgLore);
        forgingIcon.setItemMeta(forgMeta);
        inv.setItem(24, forgingIcon);

        switchMenu(player, Category.MENU, inv);
    }

    private void openAssemblyCategory(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, getTitle(Category.ASSEMBLY));

        // TODO: 如需分页可后续实现，目前假定物品少于 54 个。

        int slot = 0;
        // Updated to use IronHeartManager's BlueprintConfig
        for (BlueprintConfig.Blueprint bp : plugin.getIronHeartManager().getBlueprintConfig().getAllBlueprints()) {
            if (slot >= 45)
                break;
            inv.setItem(slot++, createAssemblyIcon(bp));
        }

        addNavigation(inv);
        switchMenu(player, Category.ASSEMBLY, inv);
    }

    private void openForgingCategory(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, getTitle(Category.FORGING));

        int slot = 0;
        // From ForgingRecipeManager (Mythic)
        ForgingRecipeManager recipeManager = plugin.getForgingRecipeManager();
        if (recipeManager != null) {
            for (ForgingRecipeManager.ForgingRecipe recipe : recipeManager.getRecipes().values()) {
                if (slot >= 45)
                    break;
                inv.setItem(slot++, createForgingIcon(recipe));
            }
        }

        addNavigation(inv);
        switchMenu(player, Category.FORGING, inv);
    }

    private void addNavigation(Inventory inv) {
        // Back Button at 49
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta meta = back.getItemMeta();
        meta.displayName(mm.deserialize("<!i><yellow>返回上一级"));
        back.setItemMeta(meta);
        inv.setItem(49, back);
    }

    private ItemStack createBackground() {
        ItemStack bg = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = bg.getItemMeta();
        meta.displayName(Component.empty());
        bg.setItemMeta(meta);
        return bg;
    }

    private ItemStack createAssemblyIcon(BlueprintConfig.Blueprint bp) {
        // Use the written blueprint visual but as an icon
        // For Phase 1 of migration, we just create a simple icon since we deleted BlueprintManager's createBlueprintItem
        // We will need to re-implement visual generation later in IronHeart
        ItemStack icon = new ItemStack(Material.PAPER);
        ItemMeta meta = icon.getItemMeta();
        
        meta.displayName(mm.deserialize("<!i><aqua>" + bp.displayName()));
        List<Component> lore = new ArrayList<>();
        lore.add(mm.deserialize("<!i><gray>Target: " + bp.targetItem()));
        lore.add(Component.empty());
        lore.add(mm.deserialize("<!i><green>点击写入此蓝图"));
        meta.lore(lore);
        
        icon.setItemMeta(meta);
        PDCManager.setString(icon, Keys.ITEM_ID_KEY, bp.id());
        return icon;
    }

    private ItemStack createForgingIcon(ForgingRecipeManager.ForgingRecipe recipe) {
        ItemStack icon = plugin.getItemManager().getItem(recipe.getResultItem());
        if (icon == null) {
            icon = new ItemStack(Material.PAPER);
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(mm.deserialize("<red>Unknown: " + recipe.getResultItem()));
            icon.setItemMeta(meta);
        } else {
            icon = icon.clone();
        }

        ItemMeta meta = icon.getItemMeta();
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();

        lore.add(Component.empty());
        lore.add(mm.deserialize(getMessage("forging.gui.req").replace("{item}", recipe.getDisplayName())));
        lore.add(mm.deserialize(
                getMessage("forging.gui.difficulty").replace("{diff}", String.valueOf(recipe.getDifficulty()))));
        lore.add(mm.deserialize(
                getMessage("forging.gui.tier").replace("{tier}", String.valueOf(recipe.getTierRequired()))));
        lore.add(mm.deserialize("<green>点击写入此蓝图"));

        meta.lore(lore);
        icon.setItemMeta(meta);

        PDCManager.setString(icon, Keys.ITEM_ID_KEY, recipe.getId()); // Recipe ID
        return icon;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;

        Category cat = playerCategories.get(player.getUniqueId());
        if (cat == null)
            return;

        // Verify Title matches (Double check)
        if (!event.getView().title().equals(getTitle(cat))) {
            // Maybe they switched GUI or it's another GUI?
            // If title mismatch, ignore? Or force remove?
            return;
        }

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR)
            return;

        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);

        if (cat == Category.MENU) {
            if (event.getSlot() == 20) {
                openAssemblyCategory(player);
            } else if (event.getSlot() == 24) {
                openForgingCategory(player);
            }
            return;
        }

        // Sub-menus
        if (event.getSlot() == 49 && clicked.getType() == Material.ARROW) {
            openMainMenu(player);
            return;
        }

        // Handle Blueprint Selection
        if (cat == Category.ASSEMBLY || cat == Category.FORGING) {
            String id = PDCManager.getString(clicked, Keys.ITEM_ID_KEY);
            if (id == null)
                return;

            // Give Blueprint
            ItemStack hand = player.getInventory().getItemInMainHand();
            String handId = PDCManager.getString(hand, Keys.ITEM_ID_KEY);

            // Re-verify they are holding blank blueprint (in case they swapped)
            if (!"BLANK_BLUEPRINT".equals(handId) || PDCManager.hasKey(hand, blueprintTargetKey)) {
                player.closeInventory();
                sendMessage(player, "forging.gui.no_blueprint_in_hand");
                return;
            }

            // Write logic
            if (cat == Category.ASSEMBLY) {
                // Assembly: ID matches blueprint ID in BlueprintManager
                BlueprintConfig.Blueprint bp = plugin.getIronHeartManager().getBlueprintConfig().getBlueprint(id);
                if (bp != null) {
                    writeBlueprint(player, hand, id, bp.displayName(), cat);
                }
            } else {
                // Forging: ID matches recipe ID in ForgingRecipeManager (which is usually
                // result item ID key?? Check loadRecipes)
                // In ForgingRecipeManager: recipes.put(key, recipe); Key is from section keys.
                // In createForgingIcon: PDC set to recipe.getId() (which is key).
                ForgingRecipeManager.ForgingRecipe recipe = plugin.getForgingRecipeManager().getRecipeById(id);
                if (recipe != null) {
                    writeBlueprint(player, hand, recipe.getResultItem(), recipe.getDisplayName(), cat);
                }
            }
        }
    }

    // Unified write logic
    private void writeBlueprint(Player player, ItemStack hand, String targetId, String displayName, Category cat) {
        ItemStack singleBlueprint = hand.clone();
        singleBlueprint.setAmount(1);

        ItemMeta meta = singleBlueprint.getItemMeta();

        // Name
        String baseName = getMessage("items.written_blueprint.name");
        if (baseName.contains("Missing"))
            baseName = "<aqua>工程蓝图";
        // Differentiate type in name
        // User requested removing "[基础]" prefix for Assembly blueprints
        String typePrefix = (cat == Category.ASSEMBLY) ? "" : "<gold>[神话] ";
        
        // Remove "蓝图" suffix from displayName if present (for item name)
        String cleanDisplayName = displayName;
        if (cleanDisplayName.endsWith("蓝图")) {
            cleanDisplayName = cleanDisplayName.substring(0, cleanDisplayName.length() - 2);
        }
        
        meta.displayName(mm.deserialize(typePrefix + baseName + ": " + cleanDisplayName));

        // Enchants
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        // Lore
        List<Component> lore = new ArrayList<>();
        lore.add(mm.deserialize(getMessage("forging.gui.blueprint_lore_target").replace("{item}", cleanDisplayName)));

        if (cat == Category.ASSEMBLY) {
             // Fetch Blueprint to get requirements
             BlueprintConfig.Blueprint bp = plugin.getIronHeartManager().getBlueprintConfig().getBlueprint(targetId);
             if (bp != null) {
                 String header = getMessage("forging.gui.blueprint_lore_components_header");
                 if (header != null && !header.contains("Missing")) {
                     lore.add(mm.deserialize(header));
                 } else {
                     lore.add(mm.deserialize("<!i><gray>所需组件:"));
                 }
                 
                 String format = getMessage("forging.gui.blueprint_lore_component_format");
                 if (format == null || format.contains("Missing")) {
                     format = "<dark_gray>- <white>{amount}x {component}";
                 }
                 
                 final String finalFormat = format;
                 bp.requiredComponents().forEach((type, amount) -> {
                     String typeName = getMessage("component_types." + type.name().toLowerCase());
                     if (typeName == null || typeName.contains("Missing")) typeName = type.name();
                     
                     String line = finalFormat.replace("{amount}", String.valueOf(amount))
                                              .replace("{component}", typeName);
                     lore.add(mm.deserialize("<!i>" + line));
                 });
                 
                 // Add usage hint at the bottom
                 String usage = getMessage("forging.gui.blueprint_lore_usage");
                 if (usage != null && !usage.contains("Missing")) {
                     lore.add(Component.empty());
                     lore.add(mm.deserialize("<!i>" + usage));
                 }
             } else {
                  lore.add(mm.deserialize("<!i><red>Error: Blueprint data missing"));
             }
        } else {
            // Forging details
            lore.add(mm.deserialize(getMessage("forging.gui.difficulty").replace("{diff}", "?"))); // Or specific data
            lore.add(mm.deserialize(getMessage("forging.gui.blueprint_lore_hint")));
        }
        meta.lore(lore);

        singleBlueprint.setItemMeta(meta);
        PDCManager.setString(singleBlueprint, Keys.BLUEPRINT_TARGET, targetId); // Use constant Key

        // Deduct and Give
        hand.setAmount(hand.getAmount() - 1);
        player.getInventory().addItem(singleBlueprint).values()
                .forEach(overflow -> player.getWorld().dropItemNaturally(player.getLocation(), overflow));

        player.sendMessage(mm.deserialize(getMessage("forging.gui.written").replace("{item}", displayName)));
        player.closeInventory();
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (switchingMenus.contains(event.getPlayer().getUniqueId()))
            return;
        playerCategories.remove(event.getPlayer().getUniqueId());
    }

    private String getMessage(String key) {
        return plugin.getConfigManager().getMessage(key);
    }

    private void sendMessage(Player player, String key) {
        player.sendMessage(mm.deserialize(getMessage(key)));
    }
}
