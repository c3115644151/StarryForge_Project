package com.starryforge.features.ironheart;

import com.starryforge.StarryForge;
import com.starryforge.features.ironheart.config.BlueprintConfig;
import com.starryforge.features.ironheart.config.ComponentConfig;
import com.starryforge.features.ironheart.config.ResonanceConfig;
import com.starryforge.features.ironheart.data.PDCAdapter;
import com.starryforge.features.ironheart.data.model.IronHeartWeapon;
import com.starryforge.features.ironheart.data.model.VeteranStats;
import com.starryforge.features.ironheart.integration.IronHeartStatProvider;
import com.starryforge.features.ironheart.logic.FabricationService;
import com.starryforge.features.ironheart.logic.IntegrityValidator;
import com.starryforge.features.ironheart.logic.StatCalculator;
import com.starryforge.features.scrap.ScrapManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;

public class IronHeartManager implements CommandExecutor {

    @SuppressWarnings("unused")
    private final StarryForge plugin;
    private final ComponentConfig componentConfig;
    private final BlueprintConfig blueprintConfig;
    private final ResonanceConfig resonanceConfig;
    private final com.starryforge.features.ironheart.gui.AssemblerGUI assemblerGUI;
    private final ScrapManager scrapManager;
    
    // Logic Services
    private final IntegrityValidator integrityValidator;
    private final StatCalculator statCalculator;
    private final FabricationService fabricationService;

    public IronHeartManager(StarryForge plugin) {
        this.plugin = plugin;
        this.componentConfig = new ComponentConfig(plugin);
        this.blueprintConfig = new BlueprintConfig(plugin);
        this.resonanceConfig = new ResonanceConfig(plugin);
        this.scrapManager = plugin.getScrapManager();
        this.assemblerGUI = new com.starryforge.features.ironheart.gui.AssemblerGUI(plugin);
        
        // Initialize Logic
        this.integrityValidator = new IntegrityValidator();
        this.statCalculator = new StatCalculator(resonanceConfig);
        this.fabricationService = new FabricationService(integrityValidator, statCalculator, componentConfig, blueprintConfig, scrapManager);
        
        plugin.getCommand("ih").setExecutor(this);
        
        registerIntegrations();
    }

    private void registerIntegrations() {
        if (Bukkit.getPluginManager().isPluginEnabled("NexusCore")) {
            try {
                com.nexuscore.NexusCore core = com.nexuscore.NexusCore.getInstance();
                if (core != null) {
                    core.getRpgManager().registerStatProvider(new IronHeartStatProvider());
                    plugin.getLogger().info("Registered IronHeartStatProvider with NexusCore.");
                }
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Failed to register NexusCore integration", t);
            }
        }
    }

    public ComponentConfig getComponentConfig() {
        return componentConfig;
    }

    public BlueprintConfig getBlueprintConfig() {
        return blueprintConfig;
    }

    public ResonanceConfig getResonanceConfig() {
        return resonanceConfig;
    }

    public com.starryforge.features.ironheart.gui.AssemblerGUI getAssemblerGUI() {
        return assemblerGUI;
    }
    
    public FabricationService getFabricationService() {
        return fabricationService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (args.length >= 2 && args[0].equalsIgnoreCase("test") && args[1].equalsIgnoreCase("item")) {
            // /ih test item <blueprint>
            if (args.length < 3) {
                player.sendMessage("Usage: /ih test item <blueprint_id>");
                return true;
            }

            String bpId = args[2];
            BlueprintConfig.Blueprint bp = blueprintConfig.getBlueprint(bpId);
            if (bp == null) {
                player.sendMessage("Blueprint not found: " + bpId);
                return true;
            }

            // Create a dummy weapon for testing Phase 1
            IronHeartWeapon weapon = new IronHeartWeapon(
                    UUID.randomUUID().toString(),
                    bpId,
                    1, // Revision
                    1, // Tier
                    new IronHeartWeapon.Integrity(10, 10),
                    new HashMap<>(), // Empty components for now
                    new HashMap<>(), // Empty component qualities
                    new IronHeartWeapon.StatsCache(10.0, 1.2, 5.0, 0.0),
                    new IronHeartWeapon.History(player.getName(), System.currentTimeMillis(), new VeteranStats(0, 0, 0))
            );

            ItemStack item = new ItemStack(Material.IRON_SWORD); // Placeholder material
            PDCAdapter.writeWeaponData(item, weapon);
            
            player.getInventory().addItem(item);
            player.sendMessage("Gave test IronHeart weapon for blueprint: " + bp.displayName());
            return true;
        }

        return false;
    }
}
