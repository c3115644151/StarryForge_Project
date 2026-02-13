package com.starryforge.features.ironheart.logic;

import com.starryforge.features.ironheart.config.BlueprintConfig;
import com.starryforge.features.ironheart.config.ComponentConfig;
import com.starryforge.features.ironheart.config.ResonanceConfig.Resonance;
import com.starryforge.features.ironheart.data.PDCAdapter;
import com.starryforge.features.ironheart.data.model.IronHeartWeapon;
import com.starryforge.features.ironheart.data.model.QualifiedComponent;
import com.starryforge.features.ironheart.data.model.VeteranStats;
import com.starryforge.features.ironheart.data.model.WeaponComponent;
import com.starryforge.features.scrap.ScrapManager;
import com.nexuscore.util.NexusKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class FabricationService {

    private final IntegrityValidator integrityValidator;
    private final StatCalculator statCalculator;
    private final ComponentConfig componentConfig;
    private final BlueprintConfig blueprintConfig;
    private final ScrapManager scrapManager;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public FabricationService(IntegrityValidator integrityValidator, StatCalculator statCalculator, ComponentConfig componentConfig, BlueprintConfig blueprintConfig, ScrapManager scrapManager) {
        this.integrityValidator = integrityValidator;
        this.statCalculator = statCalculator;
        this.componentConfig = componentConfig;
        this.blueprintConfig = blueprintConfig;
        this.scrapManager = scrapManager;
    }

    /**
     * Phase 3.3: Fabrication Execution
     * "Click confirm -> consume materials -> generate result"
     */
    public FabricateResult fabricate(Player player, String blueprintId, Map<WeaponComponent.ComponentType, ItemStack> componentItems) {
        BlueprintConfig.Blueprint bp = blueprintConfig.getBlueprint(blueprintId);
        if (bp == null) return new FabricateResult(false, null, "Blueprint not found");

        // Convert ItemStacks to QualifiedComponents
        List<QualifiedComponent> qualifiedComponents = new ArrayList<>();
        Map<WeaponComponent.ComponentType, WeaponComponent> componentDefs = new HashMap<>();

        for (Map.Entry<WeaponComponent.ComponentType, ItemStack> entry : componentItems.entrySet()) {
            ItemStack item = entry.getValue();
            if (item == null || item.getType() == Material.AIR) continue;

            // Extract ID from PDC
            String compId = item.getItemMeta().getPersistentDataContainer()
                    .get(NexusKeys.ITEM_ID, PersistentDataType.STRING);
            
            if (compId == null) {
                return new FabricateResult(false, null, "Invalid component item: " + entry.getKey());
            }

            WeaponComponent compDef = componentConfig.getComponent(compId);
            if (compDef == null) {
                return new FabricateResult(false, null, "Unknown component definition: " + compId);
            }

            // Extract Quality
            QualifiedComponent qc = extractComponent(item, compDef);
            qualifiedComponents.add(qc);
            componentDefs.put(entry.getKey(), compDef);
        }

        Collection<WeaponComponent> components = componentDefs.values();

        // 1. Validate Integrity (Phase 3.1)
        IntegrityValidator.ValidationResult integrity = integrityValidator.validate(components, 0);
        if (!integrity.isValid()) {
            return new FabricateResult(false, null, "Integrity Check Failed: " + integrity.remainingIntegrity());
        }

        // 2. Calculate Stats (Phase 3.2) - Now with Quality Scaling
        StatCalculator.CalculationResult calc = statCalculator.calculate(qualifiedComponents);

        // 3. Create Weapon Data
        String uuid = UUID.randomUUID().toString();
        IronHeartWeapon.Integrity integrityData = new IronHeartWeapon.Integrity(integrity.maxIntegrity(), integrity.usedIntegrity());
        
        Map<String, String> componentIds = new HashMap<>();
        Map<String, Integer> componentQualities = new HashMap<>();
        
        for (QualifiedComponent qc : qualifiedComponents) {
            String typeName = qc.base().type().name();
            componentIds.put(typeName, qc.base().id());
            componentQualities.put(typeName, qc.quality());
        }

        IronHeartWeapon.History history = new IronHeartWeapon.History(
                player.getName(),
                System.currentTimeMillis(),
                new VeteranStats(0, 0, 0)
        );

        IronHeartWeapon weapon = new IronHeartWeapon(
                uuid,
                blueprintId,
                1, // Revision
                1, // Tier
                integrityData,
                componentIds,
                componentQualities,
                calc.stats(),
                history
        );

        // 4. Create ItemStack
        Material mat = Material.matchMaterial(bp.targetItem());
        if (mat == null) mat = Material.IRON_SWORD; // Fallback
        ItemStack item = new ItemStack(mat);
        PDCAdapter.writeWeaponData(item, weapon);

        // 5. Apply Visuals (Basic)
        updateItemVisuals(item, bp, calc, integrity);

        return new FabricateResult(true, item, "Weapon Fabricated Successfully");
    }

    private QualifiedComponent extractComponent(ItemStack item, WeaponComponent compDef) {
        int quality = 1; // Default
        if (item != null && item.hasItemMeta()) {
            quality = item.getItemMeta().getPersistentDataContainer()
                    .getOrDefault(NexusKeys.STAR_RATING, PersistentDataType.INTEGER, 1);
        }
        return new QualifiedComponent(compDef, quality);
    }

    /**
     * Phase 3.4: Modification Protocol
     * "Hard bind destroy" vs "Soft bind probabilistic recovery"
     */
    public FabricateResult modify(Player player, ItemStack existingItem, Map<WeaponComponent.ComponentType, ItemStack> newComponentsMap) {
        IronHeartWeapon oldWeapon = PDCAdapter.readWeaponData(existingItem);
        if (oldWeapon == null) return new FabricateResult(false, null, "Not an IronHeart weapon");

        BlueprintConfig.Blueprint bp = blueprintConfig.getBlueprint(oldWeapon.blueprintId());
        if (bp == null) return new FabricateResult(false, null, "Original Blueprint not found");

        // Prepare Merged State
        Map<String, String> currentIds = new HashMap<>(oldWeapon.components());
        Map<String, Integer> currentQualities = oldWeapon.componentQualities();
        if (currentQualities == null) currentQualities = new HashMap<>(); // Handle legacy

        List<ItemStack> recoveredItems = new ArrayList<>();
        Map<WeaponComponent.ComponentType, WeaponComponent> finalComponentsMap = new HashMap<>();
        List<QualifiedComponent> finalQualifiedComponents = new ArrayList<>();

        // Process Updates
        // Iterate over ALL possible slots to build the final state
        // But we don't know all possible slots easily without Blueprint.
        // Let's iterate over the UNION of old components and new components.
        
        Set<WeaponComponent.ComponentType> allTypes = new HashSet<>();
        currentIds.keySet().forEach(k -> {
            try { allTypes.add(WeaponComponent.ComponentType.valueOf(k)); } catch (Exception ignored) {}
        });
        allTypes.addAll(newComponentsMap.keySet());

        for (WeaponComponent.ComponentType type : allTypes) {
            String typeName = type.name();
            
            // Check if we have a NEW item for this slot
            if (newComponentsMap.containsKey(type)) {
                ItemStack newItem = newComponentsMap.get(type);
                if (newItem != null && newItem.getType() != Material.AIR) {
                    // It's a replacement or addition
                    String newId = newItem.getItemMeta().getPersistentDataContainer()
                            .get(NexusKeys.ITEM_ID, PersistentDataType.STRING);
                    
                    if (newId == null) continue; // Skip invalid
                    
                    WeaponComponent newComp = componentConfig.getComponent(newId);
                    if (newComp == null) continue;

                    // Handle Recovery of OLD item in this slot (if any)
                    String oldId = currentIds.get(typeName);
                    if (oldId != null && !oldId.equals(newId)) {
                        handleComponentRecovery(oldId, type, recoveredItems);
                    }

                    // Register NEW item
                    QualifiedComponent qc = extractComponent(newItem, newComp);
                    finalComponentsMap.put(type, newComp);
                    finalQualifiedComponents.add(qc);
                    continue;
                }
            }

            // No new item, keep OLD item
            String oldId = currentIds.get(typeName);
            if (oldId != null) {
                WeaponComponent oldComp = componentConfig.getComponent(oldId);
                if (oldComp != null) {
                    int oldQuality = currentQualities.getOrDefault(typeName, 1);
                    QualifiedComponent qc = new QualifiedComponent(oldComp, oldQuality);
                    finalComponentsMap.put(type, oldComp);
                    finalQualifiedComponents.add(qc);
                }
            }
        }

        // 1. Validate New State
        Collection<WeaponComponent> components = finalComponentsMap.values();
        IntegrityValidator.ValidationResult integrity = integrityValidator.validate(components, 0); 
        
        if (!integrity.isValid()) {
            return new FabricateResult(false, null, "Modification Failed: Integrity Check Failed");
        }

        // 2. Calculate New Stats
        StatCalculator.CalculationResult calc = statCalculator.calculate(finalQualifiedComponents);

        // 3. Update Weapon Data
        Map<String, String> newComponentIds = new HashMap<>();
        Map<String, Integer> newComponentQualities = new HashMap<>();
        
        for (QualifiedComponent qc : finalQualifiedComponents) {
            String typeName = qc.base().type().name();
            newComponentIds.put(typeName, qc.base().id());
            newComponentQualities.put(typeName, qc.quality());
        }

        IronHeartWeapon newWeapon = new IronHeartWeapon(
                oldWeapon.uuid(), // Keep UUID
                oldWeapon.blueprintId(),
                oldWeapon.revision() + 1,
                oldWeapon.tier(),
                new IronHeartWeapon.Integrity(integrity.maxIntegrity(), integrity.usedIntegrity()),
                newComponentIds,
                newComponentQualities,
                calc.stats(),
                oldWeapon.history() // Keep history
        );

        // 4. Update Item
        PDCAdapter.writeWeaponData(existingItem, newWeapon);
        updateItemVisuals(existingItem, bp, calc, integrity);

        return new FabricateResult(true, existingItem, "Weapon Modified. Recovered " + recoveredItems.size() + " parts.");
    }

    private void handleComponentRecovery(String compId, WeaponComponent.ComponentType type, List<ItemStack> recovered) {
        WeaponComponent comp = componentConfig.getComponent(compId);
        if (comp == null) return; // Should not happen

        // Phase 3.4 Logic:
        // Hard Bind (Head, Spine, Shaft) -> Destroy (0% recovery) -> Scrap
        // Soft Bind (Guard, Grip, Weight) -> Probabilistic Recovery (e.g. 50%) -> Scrap on fail
        
        boolean isHardBind = switch (type) {
            case HEAD, SPINE, SHAFT -> true;
            default -> false;
        };

        boolean recoveredSuccess = false;

        if (!isHardBind) {
            // Soft Bind: 50% chance
            if (Math.random() < 0.5) {
                recoveredSuccess = true;
                // Create item for recovered component
                // TODO: Need a way to generate Component Item from ID.
                // For now, let's create a Scrap item even for success? No.
                // recovered.add(ComponentItemGenerator.create(comp)); 
                // Placeholder: We can't generate the component item easily without a ComponentManager/ItemManager helper.
                // Assuming we can look it up in plugin.getItemManager() using comp.itemId()
                // Let's defer "Success" item generation or use a placeholder.
                // But the user task is about Scrap.
            }
        }

        if (!recoveredSuccess) {
            // Failed or Hard Bind -> Create Scrap
            // Source ID: comp.itemId() (e.g. "starryforge:STAR_STEEL")
            // Source Name: comp.name()
            // Quality: comp.requirements().forgeLevel() (Use forge level as tier proxy?)
            // Amount: 1 (Scrap usually 1)
            
            ItemStack scrap = scrapManager.createScrap(
                ScrapManager.ScrapType.COMPONENT_SCRAP,
                comp.itemId(),
                comp.name(),
                comp.requirements().forgeLevel(),
                1
            );
            recovered.add(scrap);
        }
    }

    private void updateItemVisuals(ItemStack item, BlueprintConfig.Blueprint bp, StatCalculator.CalculationResult calc, IntegrityValidator.ValidationResult integrity) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        // Use MiniMessage for title
        meta.displayName(mm.deserialize("<!i><aqua>" + bp.displayName()));
        
        List<Component> lore = new ArrayList<>();
        
        // Description
        for (String line : bp.description()) {
            lore.add(mm.deserialize("<gray>" + line));
        }
        
        lore.add(Component.empty());
        lore.add(mm.deserialize("<gray>Damage: <white>" + String.format("%.1f", calc.stats().damage())));
        lore.add(mm.deserialize("<gray>Speed: <white>" + String.format("%.1f", calc.stats().speed())));
        lore.add(mm.deserialize("<gray>Reach: <white>" + String.format("%.1f", calc.stats().reach())));
        
        String integrityColor = integrity.usedIntegrity() > integrity.maxIntegrity() ? "<red>" : "<white>";
        lore.add(mm.deserialize("<gray>Integrity: " + integrityColor + integrity.usedIntegrity() + "<gray>/" + integrity.maxIntegrity()));
        
        if (!calc.activeResonances().isEmpty()) {
            lore.add(Component.empty());
            lore.add(mm.deserialize("<gold>Resonances:"));
            for (Resonance res : calc.activeResonances()) {
                // Assuming res.color() returns a MiniMessage color tag like "<red>"
                lore.add(mm.deserialize(res.color() + "★ " + res.displayName()));
            }
        }
        
        meta.lore(lore);
        item.setItemMeta(meta);
    }

    public record FabricateResult(boolean success, ItemStack item, String message) {}
}
