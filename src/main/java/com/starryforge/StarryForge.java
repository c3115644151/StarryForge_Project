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
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.command.PluginCommand;

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
    private com.starryforge.features.items.frostsigh.FrostMarkManager frostMarkManager;

    @Override
    public void onEnable() {
        try {
            instance = this;
            getLogger().info("Starting StarryForge enablement process...");

            // 初始化管理器
            LogUtil.init(this);
            this.configManager = new ConfigManager(this);
            
            // FrostMarkManager (Standalone, no dependencies)
            this.frostMarkManager = new com.starryforge.features.items.frostsigh.FrostMarkManager(this);
            this.frostMarkManager.start();

            // 注册 RPG 能力 (NexusCore) - 必须在 ItemManager 之前注册，以便加载物品时能正确计算属性
            if (getServer().getPluginManager().isPluginEnabled("NexusCore")) {
                registerRpgComponents();
                // 注册传奇武器监听器
                getServer().getPluginManager().registerEvents(
                        new com.starryforge.features.items.shadowdagger.ShadowDaggerListener(this), this);
                getServer().getPluginManager()
                        .registerEvents(new com.starryforge.features.items.frostsigh.FrostsighListener(this), this);
            } else {
                getLogger().warning("NexusCore not found! RPG features will be disabled.");
            }

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

            // 注册 RPG 能力 (NexusCore)
            // Moved to before ItemManager initialization
            /*
             * if (getServer().getPluginManager().isPluginEnabled("NexusCore")) {
             * registerRpgComponents();
             * // 注册传奇武器监听器
             * getServer().getPluginManager().registerEvents(new
             * com.starryforge.features.items.shadowdagger.ShadowDaggerListener(this),
             * this);
             * getServer().getPluginManager().registerEvents(new
             * com.starryforge.features.items.frostsigh.FrostsighListener(this), this);
             * } else {
             * getLogger().warning("NexusCore not found! RPG features will be disabled.");
             * }
             */

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
            getServer().getPluginManager()
                    .registerEvents(new com.starryforge.features.items.shadowdagger.ShadowDaggerListener(this), this);
            getServer().getPluginManager()
                    .registerEvents(new com.starryforge.features.items.greatsword.GreatswordListener(this), this);

            // 注册命令
            PluginCommand cmd = getCommand("starryforge");
            if (cmd != null) {
                com.starryforge.command.StarryForgeCommand commandExecutor = new com.starryforge.command.StarryForgeCommand(
                        this);
                cmd.setExecutor(commandExecutor);
                cmd.setTabCompleter(commandExecutor);
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
                // Use Reflection for RecipeConfigLoader to avoid class loader conflict
                Class<?> nexusCoreClass = Class.forName("com.nexuscore.NexusCore");
                Object nexusCoreInstance = nexusCoreClass.getMethod("getInstance").invoke(null);

                Class<?> loaderClass = Class.forName("com.nexuscore.recipe.RecipeConfigLoader");
                loaderClass.getMethod("load", nexusCoreClass, java.io.File.class)
                        .invoke(null, nexusCoreInstance, new java.io.File(getDataFolder(), "nexus_recipes.yml"));

                getLogger().info("Loaded Nexus recipes.");
            } catch (Exception e) {
                getLogger().warning("Failed to load Nexus recipes: " + e.getMessage());
            }

            // Force update all items again to ensure RPG stats are applied (Double Check)
            if (getServer().getPluginManager().isPluginEnabled("NexusCore")) {
                getLogger().info("Performing final RPG attribute refresh...");
                for (String key : itemManager.getItemNames()) {
                    ItemStack item = itemManager.getItem(key);
                    if (item != null) {
                        try {
                            com.nexuscore.NexusCore.getInstance().getRpgManager().updateItemAttributes(item);
                        } catch (Exception e) {
                            getLogger().warning("Final refresh failed for " + key + ": " + e.getMessage());
                        }
                    }
                }
            }

            getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
                @org.bukkit.event.EventHandler
                public void onPluginEnable(org.bukkit.event.server.PluginEnableEvent event) {
                    if (event.getPlugin().getName().equals("NexusCore")) {
                        getLogger().info("NexusCore re-enabled detected. Re-registering modules...");
                        registerWithNexusCore();
                        registerRpgComponents();
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
                // 使用反射或直接调用，取决于你的链接策略
                // 由于在重载时直接访问 NexusRegistry 类出现 LinkageError，需要小心
                // 错误信息表明：
                // loader constraint violation: loader 'StarryForge...' 想要加载类
                // com.nexuscore.registry.NexusRegistry
                // 但同名类已由 'NexusCore...' 加载

                // 这通常发生在类被不同类加载器加载，或插件重载时仍持有旧引用

                // 安全做法：在重载阶段使用反射进行注销，避免直接类依赖问题
                // 或者如果这只是 NexusCore 自己会处理的清理任务，也可以直接忽略错误

                // NexusCore 在禁用时可能会自行清理注册表，所以如果 NexusCore 也在禁用，我们可能不需要手动注销
                // 但如果只有 StarryForge 在重载...
                Object registry = com.nexuscore.NexusCore.getInstance().getRegistry();
                registry.getClass().getMethod("unregisterProvider", String.class).invoke(registry, "starry-forge");

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
        if (frostMarkManager != null) {
            frostMarkManager.stop();
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

    public DebugStickManager getDebugStickManager() {
        return debugStickManager;
    }

    public com.starryforge.features.items.frostsigh.FrostMarkManager getFrostMarkManager() {
        return frostMarkManager;
    }

    public void registerRpgComponents() {
        getLogger().info("Registering RPG hooks directly...");
        try {
            if (getServer().getPluginManager().isPluginEnabled("NexusCore")) {
                // Use direct reference since we have hard dependency
                com.nexuscore.NexusCore nexusCore = com.nexuscore.NexusCore.getInstance();
                if (nexusCore != null && nexusCore.getRpgManager() != null) {
                    com.nexuscore.rpg.NexusRpgManager rpgManager = nexusCore.getRpgManager();

                    // Register Ability
                    getLogger().info("Registering ShadowDaggerAbility...");
                    rpgManager
                            .registerAbility(new com.starryforge.features.items.shadowdagger.ShadowDaggerAbility(this));

                    getLogger().info("Registering FrostsighAbility...");
                    rpgManager.registerAbility(new com.starryforge.features.items.frostsigh.FrostsighAbility(this));

                    // Register Stat Provider
                    getLogger().info("Registering StarryStatProvider...");
                    rpgManager.registerStatProvider(new com.starryforge.features.items.StarryStatProvider());

                    // Register Item-Ability Mapping
                    getLogger().info("Mapping Shadow Dagger to Void Step...");
                    rpgManager.registerItemAbility("SHADOW_DAGGER", "VOID_STEP");

                    getLogger().info("Mapping Frostsigh Blade to Event Horizon...");
                    rpgManager.registerItemAbility("FROST_SIGH_BLADE", "FROSTSIGH_EVENT_HORIZON");
                    // Keep old mapping just in case of transition
                    rpgManager.registerItemAbility("FROSTSIGH_OBLIVION", "FROSTSIGH_EVENT_HORIZON");

                    getLogger().info("RPG hooks registered successfully.");
                } else {
                    getLogger().warning("NexusCore or RPG Manager is null.");
                }
            }
        } catch (Exception e) {
            getLogger().severe("Failed to register RPG hooks: " + e.getMessage());
            e.printStackTrace();
        }
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
                    // Fallback to 4 args if 6 arg not found (should not happen with updated
                    // NexusCore)
                    registerMethod = registry.getClass().getMethod("register",
                            String.class, String.class, java.util.function.Supplier.class,
                            java.util.function.Supplier.class);
                }

                java.util.function.Supplier<org.bukkit.inventory.ItemStack> iconSupplier = () -> {
                    org.bukkit.inventory.ItemStack icon = itemManager.getItem("SLUICE_MACHINE_BASE");
                    return icon != null ? icon
                            : new org.bukkit.inventory.ItemStack(org.bukkit.Material.ANVIL);
                };

                java.util.function.Supplier<java.util.List<org.bukkit.inventory.ItemStack>> itemsSupplier = () -> itemManager
                        .getAllItems();

                // Star Filter: Check PDC
                java.util.function.Function<org.bukkit.inventory.ItemStack, Boolean> starFilter = (item) -> {
                    if (item.hasItemMeta()) {
                        org.bukkit.persistence.PersistentDataContainer pdc = item.getItemMeta()
                                .getPersistentDataContainer();
                        org.bukkit.NamespacedKey starKey = new org.bukkit.NamespacedKey(this, "nexus_has_star");
                        if (pdc.has(starKey, org.bukkit.persistence.PersistentDataType.INTEGER)) {
                            return pdc.get(starKey, org.bukkit.persistence.PersistentDataType.INTEGER) == 1;
                        }
                    }
                    // Fallback to false if not specified (default no star)
                    return false;
                };

                if (registerMethod.getParameterCount() == 6) {
                    registerMethod.invoke(registry, "starry-forge", "Starry Forge", iconSupplier, itemsSupplier, null,
                            starFilter);
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
