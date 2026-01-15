package com.starryforge;

import org.bukkit.plugin.java.JavaPlugin;

import com.starryforge.features.mining.GlobalDropListener;
import com.starryforge.features.resonator.ResonatorListener;
import com.starryforge.features.core.NoiseManager;
import com.starryforge.features.sluice.SluiceManager;
import com.starryforge.features.alloy.ThermodynamicsManager;
import com.starryforge.features.core.ConfigManager;
import com.starryforge.features.resonator.ResonatorManager;
import com.starryforge.features.mining.MiningManager;
import com.starryforge.features.multiblock.MultiBlockManager;
import com.starryforge.features.alloy.AlloyManager;
import com.starryforge.features.core.DebugStickManager;
import com.starryforge.features.sluice.SluiceListener;
import com.starryforge.features.core.AnvilListener;
import com.starryforge.features.core.AntiExploitListener;
import com.starryforge.features.items.SFItemManager;
import com.starryforge.utils.LogUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.PluginCommand;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class StarryForge extends JavaPlugin {

    private static StarryForge instance;
    private NoiseManager noiseManager;
    private SFItemManager itemManager;
    private ConfigManager configManager;
    private ResonatorManager resonatorManager;
    private MiningManager miningManager;
    private ResonatorListener resonatorListener;
    private SluiceManager sluiceManager;
    private ThermodynamicsManager thermodynamicsManager;
    private MultiBlockManager multiBlockManager;
    private AlloyManager alloyManager;
    private DebugStickManager debugStickManager;
    private com.starryforge.features.forging.ForgingManager forgingManager;
    private com.starryforge.features.forging.gui.BlueprintSelectionGUI blueprintGUI;
    private com.starryforge.features.forging.ForgingRecipeManager forgingRecipeManager;

    @Override
    public void onEnable() {
        try {
            instance = this;
            getLogger().info("Starting StarryForge enablement process...");

            // 初始化管理器
            LogUtil.init(this);
            this.configManager = new ConfigManager(this);
            this.itemManager = new SFItemManager(this);

            // 初始化 NoiseManager (使用服务器种子)
            long seed = 0;
            if (!getServer().getWorlds().isEmpty()) {
                seed = getServer().getWorlds().get(0).getSeed();
            } else {
                getLogger().warning("No worlds found! Using default seed 0 for NoiseManager.");
            }
            this.noiseManager = new NoiseManager(seed);

            this.miningManager = new MiningManager(this, noiseManager);
            this.resonatorManager = new ResonatorManager(this, noiseManager);
            this.resonatorListener = new ResonatorListener(resonatorManager);
            this.sluiceManager = new SluiceManager(this);
            this.thermodynamicsManager = new ThermodynamicsManager(this);
            this.multiBlockManager = new MultiBlockManager(this);
            this.alloyManager = new AlloyManager(this, multiBlockManager);
            this.forgingRecipeManager = new com.starryforge.features.forging.ForgingRecipeManager(this);
            this.forgingManager = new com.starryforge.features.forging.ForgingManager(this);
            this.blueprintGUI = new com.starryforge.features.forging.gui.BlueprintSelectionGUI(this);
            this.debugStickManager = new DebugStickManager(this);

            // 注册监听器
            getServer().getPluginManager().registerEvents(new GlobalDropListener(miningManager), this);
            getServer().getPluginManager().registerEvents(new AnvilListener(), this);
            getServer().getPluginManager().registerEvents(this.resonatorListener, this);
            getServer().getPluginManager().registerEvents(new AntiExploitListener(), this);
            getServer().getPluginManager().registerEvents(new com.starryforge.features.forging.ForgingListener(this),
                    this);
            getServer().getPluginManager().registerEvents(this.forgingManager, this);

            // 机器监听器
            getServer().getPluginManager().registerEvents(this.sluiceManager, this);
            getServer().getPluginManager().registerEvents(new SluiceListener(this.sluiceManager), this);
            getServer().getPluginManager().registerEvents(this.alloyManager, this);
            getServer().getPluginManager().registerEvents(new com.starryforge.features.alloy.SlagRecoveryListener(this),
                    this);
            getServer().getPluginManager().registerEvents(new com.starryforge.features.alloy.ReheatListener(this),
                    this);
            getServer().getPluginManager()
                    .registerEvents(new com.starryforge.features.alloy.AlloyForgeCoreListener(this), this);
            getServer().getPluginManager()
                    .registerEvents(new com.starryforge.features.forging.AstralForgeNodeListener(this), this);
            
            // 注册命令
            PluginCommand cmd = getCommand("starryforge");
            if (cmd != null) {
                cmd.setExecutor(this);
                cmd.setTabCompleter(this);
                getLogger().info("Command 'starryforge' registered successfully.");
            } else {
                getLogger().severe("Failed to find command 'starryforge' in plugin.yml!");
            }

            registerDummyRecipes();

            // Register with NexusCore (Nexus)
            // Register with NexusCore and Listen for Reloads
            registerWithNexusCore();

            // Save nexus_recipes.yml if not exists
            if (!new java.io.File(getDataFolder(), "nexus_recipes.yml").exists()) {
                saveResource("nexus_recipes.yml", false);
            }

            // Load Nexus Recipes
            try {
                com.nexuscore.recipe.RecipeConfigLoader.load(com.nexuscore.NexusCore.getInstance(), new java.io.File(getDataFolder(), "nexus_recipes.yml"));
                getLogger().info("Loaded Nexus recipes.");
            } catch (Exception e) {
                getLogger().warning("Failed to load Nexus recipes: " + e.getMessage());
            }

            getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
                @org.bukkit.event.EventHandler
                public void onPluginEnable(org.bukkit.event.server.PluginEnableEvent event) {
                    if (event.getPlugin().getName().equals("NexusCore")) {
                        getLogger().info("NexusCore re-enabled detected. Re-registering modules...");
                        registerWithNexusCore();
                    }
                }
            }, this);

            getLogger().info("StarryForge has been enabled! Hammer at the ready.");
        } catch (Exception e) {
            getLogger().severe("Error enabling StarryForge: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (getServer().getPluginManager().isPluginEnabled("NexusCore")) {
            try {
                com.nexuscore.NexusCore.getInstance().getRegistry().unregisterProvider("starry-forge");
                getLogger().info("Unregistered from NexusCore.");
            } catch (Exception e) {
                getLogger().warning("Failed to unregister from NexusCore: " + e.getMessage());
            }
        }

        if (sluiceManager != null) {
            sluiceManager.shutdown();
        }
        if (forgingManager != null) {
            forgingManager.shutdown();
        }
        getLogger().info("StarryForge has been disabled.");
    }

    private void registerDummyRecipes() {
        // Dummy recipe for Charcoal (Slag)
        // We use CHARCOAL -> CHARCOAL so the furnace accepts the input.
        // The output is intercepted by SlagRecoveryListener.
        NamespacedKey coalKey = new NamespacedKey(this, "slag_recovery_dummy");
        if (getServer().getRecipe(coalKey) == null) {
            org.bukkit.inventory.BlastingRecipe coalRecipe = new org.bukkit.inventory.BlastingRecipe(
                    coalKey,
                    new ItemStack(Material.CHARCOAL),
                    org.bukkit.Material.CHARCOAL,
                    0.0f,
                    100 // 5 seconds
            );
            getServer().addRecipe(coalRecipe);
        }

        // Dummy recipe for Paper (Refined Items)
        // We use PAPER -> PAPER so the furnace accepts the input.
        // The output is intercepted by ReheatListener.
        NamespacedKey paperKey = new NamespacedKey(this, "refined_reheat_dummy");
        if (getServer().getRecipe(paperKey) == null) {
            org.bukkit.inventory.BlastingRecipe paperRecipe = new org.bukkit.inventory.BlastingRecipe(
                    paperKey,
                    new ItemStack(Material.PAPER),
                    org.bukkit.Material.PAPER,
                    0.0f,
                    100 // 5 seconds
            );
            getServer().addRecipe(paperRecipe);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("starryforge")) {
            if (args.length == 0)
                return false;

            // /sf reload
            if (args[0].equalsIgnoreCase("reload")) {
                if (sender.hasPermission("starryforge.admin")) {
                    reloadPlugin();
                    sender.sendMessage(
                            MiniMessage.miniMessage().deserialize("<green>StarryForge configuration reloaded!"));
                    return true;
                }
            }

            // /sf debug
            if (args[0].equalsIgnoreCase("debug")) {
                if (args.length > 1 && args[1].equalsIgnoreCase("item")) {
                     if (sender instanceof Player player) {
                        if (player.hasPermission("starryforge.debug")) {
                            ItemStack item = player.getInventory().getItemInMainHand();
                            if (item.getType() == Material.AIR) {
                                player.sendMessage(MiniMessage.miniMessage().deserialize("<red>You are not holding an item.</red>"));
                                return true;
                            }
                            ItemMeta meta = item.getItemMeta();
                            if (meta != null) {
                                player.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>Item PDC Keys:</yellow>"));
                                for (org.bukkit.NamespacedKey pdcKey : meta.getPersistentDataContainer().getKeys()) {
                                    Object value = null;
                                    // Try String
                                    try {
                                        value = meta.getPersistentDataContainer().get(pdcKey, org.bukkit.persistence.PersistentDataType.STRING);
                                    } catch (Exception ignored) {}
                                    
                                    // Try Integer
                                    if (value == null) {
                                         try {
                                            value = meta.getPersistentDataContainer().get(pdcKey, org.bukkit.persistence.PersistentDataType.INTEGER);
                                        } catch (Exception ignored) {}
                                    }
                                    
                                    String valStr = value != null ? value.toString() : "[unknown type]";
                                    player.sendMessage(MiniMessage.miniMessage().deserialize("<gray> - " + pdcKey + " = " + valStr + "</gray>"));
                                }
                            }
                            return true;
                        }
                    } else {
                        sender.sendMessage(MiniMessage.miniMessage().deserialize(configManager.getMessage("commands.no_console")));
                        return true;
                    }
                }

                if (sender instanceof Player player) {
                    if (player.hasPermission("starryforge.debug")) {
                        debugStickManager.giveDebugStick(player);
                        return true;
                    }
                } else {
                    sender.sendMessage(
                            MiniMessage.miniMessage().deserialize(configManager.getMessage("commands.no_console")));
                    return true;
                }
            }

            // /sf cedebug <item_id> - Debug command to dump CE item PDC keys
            if (args[0].equalsIgnoreCase("cedebug")) {
                if (sender.hasPermission("starryforge.admin")) {
                    if (args.length < 2) {
                        sender.sendMessage(
                                MiniMessage.miniMessage().deserialize("<red>Usage: /sf cedebug <ce_item_id></red>"));
                        return true;
                    }
                    String ceItemId = args[1];
                    try {
                        org.bukkit.plugin.Plugin cePlugin = getServer().getPluginManager().getPlugin("CraftEngine");
                        if (cePlugin == null) {
                            sender.sendMessage(
                                    MiniMessage.miniMessage().deserialize("<red>CraftEngine plugin not found!</red>"));
                            return true;
                        }

                        // Try to find a method that returns the API or ItemManager
                        // Common patterns: getAPI(), getCraftEngineAPI(), getItemManager()
                        Object api = null;
                        try {
                            api = cePlugin.getClass().getMethod("getAPI").invoke(cePlugin);
                        } catch (Exception ignored) {
                        }

                        if (api == null) {
                            // Fallback: Check if the plugin class itself has 'getItemManager'
                            api = cePlugin;
                        }

                        // Now try to find item related method on 'api' object
                        // Inspect methods to find one that returns an ItemManager-like object
                        Object itemManager = null;
                        for (java.lang.reflect.Method m : api.getClass().getMethods()) {
                            if (m.getName().equalsIgnoreCase("getItemManager")
                                    || m.getName().equalsIgnoreCase("item")) {
                                itemManager = m.invoke(api);
                                break;
                            }
                        }

                        if (itemManager == null) {
                            sender.sendMessage(MiniMessage.miniMessage()
                                    .deserialize("<red>Could not find ItemManager in CraftEngine.</red>"));
                            return true;
                        }

                        // Build item: itemManager.build(Key.key(id)) or itemManager.getItem(id)
                        Class<?> keyClass = Class.forName("net.kyori.adventure.key.Key");
                        Object keyObj = keyClass.getMethod("key", String.class).invoke(null, ceItemId);

                        Object result = null;
                        try {
                            result = itemManager.getClass().getMethod("build", keyClass).invoke(itemManager, keyObj);
                        } catch (NoSuchMethodException e) {
                            // Try 'getItem' just in case
                            try {
                                result = itemManager.getClass().getMethod("getItem", String.class).invoke(itemManager,
                                        ceItemId);
                            } catch (NoSuchMethodException e2) {
                                // Try 'get'
                                result = itemManager.getClass().getMethod("get", keyClass).invoke(itemManager, keyObj);
                            }
                        }

                        if (result instanceof ItemStack ceItem) {
                            sender.sendMessage(MiniMessage.miniMessage()
                                    .deserialize("<green>Got CE item: " + ceItemId + "</green>"));
                            ItemMeta meta = ceItem.getItemMeta();
                            if (meta != null) {
                                sender.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>PDC Keys:</yellow>"));
                                for (org.bukkit.NamespacedKey pdcKey : meta.getPersistentDataContainer().getKeys()) {
                                    String value = meta.getPersistentDataContainer().get(pdcKey,
                                            org.bukkit.persistence.PersistentDataType.STRING);
                                    if (value == null)
                                        value = "[non-string]";
                                    sender.sendMessage(MiniMessage.miniMessage()
                                            .deserialize("<gray> - " + pdcKey + " = " + value + "</gray>"));
                                }
                                if (meta.hasCustomModelData()) {
                                    sender.sendMessage(MiniMessage.miniMessage()
                                            .deserialize("<yellow>CMD: " + meta.getCustomModelData() + "</yellow>"));
                                } else {
                                    sender.sendMessage(MiniMessage.miniMessage()
                                            .deserialize("<gray>No CMD (client-bound mode)</gray>"));
                                }
                            }
                        } else {
                            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                                    "<red>CE returned null (or not ItemStack) for: " + ceItemId + "</red>"));
                        }
                    } catch (Exception e) {
                        sender.sendMessage(
                                MiniMessage.miniMessage().deserialize("<red>Error: " + e.getMessage() + "</red>"));
                        e.printStackTrace();
                    }
                    return true;
                }
            }

            // /sf give <id> [stars/amount]
            if (args[0].equalsIgnoreCase("give")) {
                if (args.length < 2) {
                    sender.sendMessage(
                            MiniMessage.miniMessage().deserialize(configManager.getMessage("commands.usage_give")));
                    return true;
                }
                if (sender instanceof Player player) {
                    if (player.hasPermission("starryforge.admin")) {
                        String id = args[1].toUpperCase();
                        ItemStack item;

                        // 特殊处理: UNIDENTIFIED_CLUSTER 支持星级参数
                        // 用法: /sf give UNIDENTIFIED_CLUSTER [stars] [amount]
                        if ("UNIDENTIFIED_CLUSTER".equals(id)) {
                            int stars = 1;
                            int amount = 1;

                            if (args.length > 2) {
                                try {
                                    stars = Integer.parseInt(args[2]);
                                } catch (NumberFormatException e) {
                                }
                            }
                            if (args.length > 3) {
                                try {
                                    amount = Integer.parseInt(args[3]);
                                } catch (NumberFormatException e) {
                                }
                            }

                            item = itemManager.createUnidentifiedCluster(stars);
                            if (item != null) {
                                item.setAmount(amount);
                            }
                        } else {
                            item = itemManager.getItem(id);
                            if (item != null) {
                                int amount = 1;
                                int stars = -1; // Default: use what's in item.yml (usually 1)
                                
                                if (args.length > 2) {
                                    // Try parsing arg 2 as stars
                                    try {
                                        stars = Integer.parseInt(args[2]);
                                    } catch (NumberFormatException e) {
                                        // Maybe it was amount? No, strict order: id [stars] [amount]
                                    }
                                }
                                if (args.length > 3) {
                                    try {
                                        amount = Integer.parseInt(args[3]);
                                    } catch (NumberFormatException e) {
                                    }
                                }
                                
                                // Apply stars if specified
                                if (stars != -1) {
                                    ItemMeta meta = item.getItemMeta();
                                    // Ensure within 1-5
                                    int finalStars = Math.max(1, Math.min(5, stars));
                                    
                                    // Update PDC
                                    meta.getPersistentDataContainer().set(com.nexuscore.api.NexusKeys.STAR_RATING, org.bukkit.persistence.PersistentDataType.INTEGER, finalStars);
                                    
                                    // Also update legacy for safety
                                    org.bukkit.NamespacedKey legacyKey = new org.bukkit.NamespacedKey(this, "nexus_has_star");
                                    meta.getPersistentDataContainer().set(legacyKey, org.bukkit.persistence.PersistentDataType.INTEGER, 1);
                                    
                                    item.setItemMeta(meta);
                                    
                                    // Apply Visuals
                                    com.nexuscore.NexusCore.getInstance().getTierVisuals().applyVisuals(item, finalStars, true);
                                }
                                
                                item.setAmount(amount);
                            }
                        }

                        if (item != null) {
                            player.getInventory().addItem(item);
                            String msg = configManager.getMessage("commands.given").replace("{item}", id);
                            player.sendMessage(MiniMessage.miniMessage().deserialize(msg));
                        } else {
                            String msg = configManager.getMessage("commands.unknown_item").replace("{id}", id);
                            player.sendMessage(MiniMessage.miniMessage().deserialize(msg));
                        }
                        return true;
                    }
                } else {
                    sender.sendMessage(
                            MiniMessage.miniMessage().deserialize(configManager.getMessage("commands.no_console")));
                    return true;
                }
            }

            // /sf list
            if (args[0].equalsIgnoreCase("list")) {
                if (sender.hasPermission("starryforge.admin")) {
                    sender.sendMessage(MiniMessage.miniMessage()
                            .deserialize(configManager.getMessage("commands.list_header")));
                    String tooltip = configManager.getMessage("commands.list_item_tooltip");
                    for (String id : itemManager.getItemNames()) {
                        sender.sendMessage(
                                MiniMessage.miniMessage().deserialize("<gray>- <click:run_command:'/sf give "
                                        + id + "'><hover:show_text:'" + tooltip + "'>" + id + "</hover></click>"));
                    }
                    return true;
                }
            }

            // /sf visualizenoise (Debug Tool)
            if (args[0].equalsIgnoreCase("visualizenoise")) {
                if (sender instanceof Player player && player.hasPermission("starryforge.debug")) {
                    player.sendMessage(MiniMessage.miniMessage()
                            .deserialize(configManager.getMessage("commands.visualize_noise_start")));
                    Location origin = player.getLocation();
                    int radius = 5;
                    for (int x = -radius; x <= radius; x++) {
                        for (int y = -radius; y <= radius; y++) {
                            for (int z = -radius; z <= radius; z++) {
                                Location loc = origin.clone().add(x, y, z);
                                double potency = noiseManager.getRawPotency(loc.getBlockX(), loc.getBlockY(),
                                        loc.getBlockZ());

                                org.bukkit.Particle.DustOptions dust;
                                if (potency > 0.8) {
                                    dust = new org.bukkit.Particle.DustOptions(org.bukkit.Color.RED, 1.0f);
                                } else if (potency > 0.5) {
                                    dust = new org.bukkit.Particle.DustOptions(org.bukkit.Color.YELLOW, 0.8f);
                                } else {
                                    dust = new org.bukkit.Particle.DustOptions(org.bukkit.Color.GRAY, 0.5f);
                                }

                                if (potency > 0.3) { // 仅显示有价值的节点
                                    player.spawnParticle(org.bukkit.Particle.DUST, loc.add(0.5, 0.5, 0.5), 1, 0, 0,
                                            0,
                                            0, dust);
                                }
                            }
                        }
                    }
                    return true;
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("starryforge")) {
            List<String> completions = new ArrayList<>();
            if (args.length == 1) {
                if (sender.hasPermission("starryforge.debug")) {
                    completions.add("debug");
                    completions.add("visualizenoise");
                }
                if (sender.hasPermission("starryforge.admin")) {
                    completions.add("give");
                    completions.add("list");
                    completions.add("reload");
                }
                return completions.stream()
                        .filter(s -> s.startsWith(args[0].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
                if (sender.hasPermission("starryforge.admin")) {
                    return itemManager.getItemNames().stream()
                            .filter(s -> s.startsWith(args[1].toUpperCase()))
                            .collect(Collectors.toList());
                }
            }
        }
        return null;
    }

    public void reloadPlugin() {
        this.configManager.loadConfig();
        this.itemManager.reload();
        this.alloyManager.loadConfig(); // Reload recipes
        registerWithNexusCore();
    }

    public static StarryForge getInstance() {
        return instance;
    }

    public NoiseManager getNoiseManager() {
        return noiseManager;
    }

    public SFItemManager getItemManager() {
        return itemManager;
    }

    public ResonatorListener getResonatorListener() {
        return resonatorListener;
    }

    public ResonatorManager getResonatorManager() {
        return resonatorManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public SluiceManager getSluiceManager() {
        return sluiceManager;
    }

    public com.starryforge.features.forging.ForgingManager getForgingManager() {
        return forgingManager;
    }

    public com.starryforge.features.forging.gui.BlueprintSelectionGUI getBlueprintGUI() {
        return blueprintGUI;
    }

    public com.starryforge.features.forging.ForgingRecipeManager getForgingRecipeManager() {
        return forgingRecipeManager;
    }

    public ThermodynamicsManager getThermodynamicsManager() {
        return thermodynamicsManager;
    }

    public MultiBlockManager getMultiBlockManager() {
        return multiBlockManager;
    }

    public AlloyManager getAlloyManager() {
        return alloyManager;
    }

    public void registerWithNexusCore() {
        org.bukkit.plugin.Plugin nexusCore = getServer().getPluginManager().getPlugin("NexusCore");
        if (nexusCore != null && nexusCore.isEnabled()) {
            try {
                // Use Reflection to register to handle ClassLoader differences on reload
                Object registry = nexusCore.getClass().getMethod("getRegistry").invoke(nexusCore);
                
                java.lang.reflect.Method registerMethod = null;
                for (java.lang.reflect.Method m : registry.getClass().getMethods()) {
                    if (m.getName().equals("register") && m.getParameterCount() == 6) {
                        registerMethod = m;
                        break;
                    }
                }
                
                if (registerMethod == null) {
                    // Fallback to 4 args if 6 arg not found (should not happen with updated NexusCore)
                     registerMethod = registry.getClass().getMethod("register", 
                        String.class, String.class, java.util.function.Supplier.class, java.util.function.Supplier.class);
                }

                java.util.function.Supplier<org.bukkit.inventory.ItemStack> iconSupplier = () -> {
                    org.bukkit.inventory.ItemStack icon = itemManager.getItem("SLUICE_MACHINE_BASE");
                    return icon != null ? icon
                            : new org.bukkit.inventory.ItemStack(org.bukkit.Material.ANVIL);
                };

                java.util.function.Supplier<java.util.List<org.bukkit.inventory.ItemStack>> itemsSupplier = () -> itemManager.getAllItems();

                // Star Filter: Check PDC
                java.util.function.Function<org.bukkit.inventory.ItemStack, Boolean> starFilter = (item) -> {
                    if (item.hasItemMeta()) {
                        org.bukkit.persistence.PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
                        org.bukkit.NamespacedKey starKey = new org.bukkit.NamespacedKey(this, "nexus_has_star");
                        if (pdc.has(starKey, org.bukkit.persistence.PersistentDataType.INTEGER)) {
                            return pdc.get(starKey, org.bukkit.persistence.PersistentDataType.INTEGER) == 1;
                        }
                    }
                    // Fallback to false if not specified (default no star)
                    return false;
                };

                if (registerMethod.getParameterCount() == 6) {
                    registerMethod.invoke(registry, "starry-forge", "Starry Forge", iconSupplier, itemsSupplier, null, starFilter);
                } else {
                    registerMethod.invoke(registry, "starry-forge", "Starry Forge", iconSupplier, itemsSupplier);
                }
                
                getLogger().info("Registered items with NexusCore Nexus (Using Reflection).");
            } catch (Exception e) {
                getLogger().warning("Failed to register with NexusCore: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
