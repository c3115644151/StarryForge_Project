package com.starryforge.features.forging.visual;

import com.starryforge.StarryForge;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class ForgingVisuals {

    private final Location center; // The constellation center (high up)
    private final Location hologramLocation; // The item hologram (near anvil)

    private final ItemDisplay hologram;
    private final TextDisplay statusText;

    private final List<Location> nodeLocations = new ArrayList<>();
    private final List<Location> interferenceLocations = new ArrayList<>(); // Void Rifts
    private final List<org.bukkit.util.Vector> initialOffsets = new ArrayList<>();
    private final List<org.bukkit.util.Vector> initialInterferenceOffsets = new ArrayList<>();
    private final java.util.Set<Integer> hitIndices = new java.util.HashSet<>();
    private final java.util.Set<Integer> hitInterferenceIndices = new java.util.HashSet<>();

    // 目标ID到展示材质的映射
    private static final java.util.Map<String, Material> DISPLAY_MATERIALS = java.util.Map.ofEntries(
            java.util.Map.entry("PICKAXE", Material.IRON_PICKAXE),
            java.util.Map.entry("HAMMER", Material.IRON_PICKAXE),
            java.util.Map.entry("AXE", Material.IRON_AXE),
            java.util.Map.entry("BOOTS", Material.IRON_BOOTS),
            java.util.Map.entry("CHESTPLATE", Material.IRON_CHESTPLATE),
            java.util.Map.entry("HELMET", Material.IRON_HELMET),
            java.util.Map.entry("CROWN", Material.IRON_HELMET),
            java.util.Map.entry("SHIELD", Material.SHIELD),
            java.util.Map.entry("RING", Material.GOLD_INGOT),
            java.util.Map.entry("AMULET", Material.GOLD_INGOT),
            java.util.Map.entry("WAND", Material.BLAZE_ROD),
            java.util.Map.entry("DAGGER", Material.IRON_SWORD),
            java.util.Map.entry("SWORD", Material.IRON_SWORD));

    public static Material resolveDisplayMaterial(String targetId) {
        if (targetId == null)
            return Material.IRON_SWORD;

        for (java.util.Map.Entry<String, Material> entry : DISPLAY_MATERIALS.entrySet()) {
            if (targetId.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return Material.IRON_SWORD;
    }

    private int ticks = 0;
    private double rotationSpeed = 0.0; // Degrees per tick
    private double currentRotation = 0.0;

    public ForgingVisuals(Location anvilLocation, ItemStack displayItem) {
        double holoY = StarryForge.getInstance().getConfigManager()
                .getDouble("machines.astral_altar.visuals.hologram_offset_y", 1.5);
        double constY = StarryForge.getInstance().getConfigManager()
                .getDouble("machines.astral_altar.visuals.constellation_offset_y", 3.5);

        this.hologramLocation = anvilLocation.clone().add(0.5, holoY, 0.5); // Floating above anvil
        this.center = anvilLocation.clone().add(0.5, constY, 0.5); // High up for sky aiming

        // Cleanup existing entities
        cleanupEntities(this.center);
        cleanupEntities(this.hologramLocation);

        // Spawn Central Hologram (The Blueprint Result or Final Item)
        hologram = (ItemDisplay) hologramLocation.getWorld().spawnEntity(this.hologramLocation,
                EntityType.ITEM_DISPLAY);
        hologram.setItemStack(displayItem != null ? displayItem : new ItemStack(Material.IRON_SWORD));
        hologram.setBillboard(Billboard.FIXED);
        hologram.setPersistent(false);

        // Initial scale
        hologram.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f(),
                new Vector3f(1.0f, 1.0f, 1.0f),
                new AxisAngle4f()));

        // Spawn Status Text (Above Hologram)
        Location textLoc = hologramLocation.clone().add(0, 0.8, 0);
        statusText = (TextDisplay) textLoc.getWorld().spawnEntity(textLoc, EntityType.TEXT_DISPLAY);
        statusText.setBillboard(Billboard.CENTER);
        statusText.setPersistent(false);
        statusText.text(Component.empty());
        statusText.setBackgroundColor(Color.fromARGB(0, 0, 0, 0)); // Transparent
    }

    private void cleanupEntities(Location loc) {
        if (loc.getWorld() == null)
            return;
        // Cleanup ItemDisplay (Hologram) & TextDisplay
        for (org.bukkit.entity.Entity entity : loc.getWorld().getNearbyEntities(loc, 2.0, 3.0, 2.0)) {
            if (entity instanceof ItemDisplay || entity instanceof TextDisplay) {
                entity.remove();
            }
        }
    }

    public void updateStatus(Component text) {
        if (statusText != null && statusText.isValid()) {
            statusText.text(text);
        }
    }

    public void setStatusVisible(boolean visible) {
        if (statusText != null && statusText.isValid()) {
            statusText.setViewRange(visible ? 1.0f : 0.0f); // Hide by setting view range to 0
            // Or use opacity? View range is safer for client performance?
            // Actually, setVisible is not in API directly?
            // Let's use view range or clear text.
            if (!visible)
                statusText.text(Component.empty());
        }
    }

    public void spawnConstellation(int count, int interferenceCount, double radius) {
        nodeLocations.clear();
        interferenceLocations.clear();
        initialOffsets.clear();
        initialInterferenceOffsets.clear();
        hitIndices.clear();
        hitInterferenceIndices.clear();

        // Fibonacci Sphere Algorithm for Target Nodes (Uniform)
        double phi = Math.PI * (3.0 - Math.sqrt(5.0));

        for (int i = 0; i < count; i++) {
            double y = 1 - (i / (float) (count - 1)) * 2;
            double radiusAtY = Math.sqrt(1 - y * y);
            double theta = phi * i;

            double x = Math.cos(theta) * radiusAtY * radius;
            double z = Math.sin(theta) * radiusAtY * radius;
            double finalY = y * radius;

            initialOffsets.add(new org.bukkit.util.Vector(x, finalY, z));
            nodeLocations.add(center.clone().add(x, finalY, z));
        }

        // Random Distribution for Interference Nodes (Chaos)
        java.util.Random rand = new java.util.Random();
        for (int i = 0; i < interferenceCount; i++) {
            // Simple random point on sphere surface
            double u = rand.nextDouble();
            double v = rand.nextDouble();
            double theta = 2 * Math.PI * u;
            double phi_angle = Math.acos(2 * v - 1);

            double x = radius * Math.sin(phi_angle) * Math.cos(theta);
            double y = radius * Math.cos(phi_angle); // Use Y up model: y=cos(phi)
            double z = radius * Math.sin(phi_angle) * Math.sin(theta);

            // Adjust to match the Y-up orientation used above if needed
            // Above: finalY = y * radius. Here y is derived from spherical coords.
            // Let's stick to standard math, it should be fine as long as it's on sphere.

            initialInterferenceOffsets.add(new org.bukkit.util.Vector(x, y, z));
            interferenceLocations.add(center.clone().add(x, y, z));
        }
    }

    public void setRotationSpeed(double speed) {
        this.rotationSpeed = speed;
    }

    public void setNodeHit(int index) {
        if (index >= 0 && index < nodeLocations.size()) {
            hitIndices.add(index);

            // Play effect
            Location loc = nodeLocations.get(index);
            loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 10, 0.2, 0.2, 0.2);
            loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_HIT, 1.0f, 1.5f);

            // Beam to center (Hologram)
            drawParticleLine(loc, hologramLocation, Particle.END_ROD, 5);
        }
    }

    public void setInterferenceHit(int index) {
        if (index >= 0 && index < interferenceLocations.size()) {
            hitInterferenceIndices.add(index);

            // Bad Effect (Void Collapse)
            Location loc = interferenceLocations.get(index);
            loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 1);
            loc.getWorld().spawnParticle(Particle.SMOKE, loc, 10, 0.3, 0.3, 0.3, 0.05);
            loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 0.5f);
            loc.getWorld().playSound(loc, Sound.ENTITY_ENDERMAN_SCREAM, 0.5f, 0.5f);
        }
    }

    public void playCompletionEffect() {
        // Star Light Effect: Bright burst
        Location loc = hologramLocation.clone();
        loc.getWorld().spawnParticle(Particle.FLASH, loc, 1);
        loc.getWorld().spawnParticle(Particle.END_ROD, loc, 20, 0.5, 0.5, 0.5, 0.1);
        loc.getWorld().playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 0.5f);

        // Ring Effect (Will be ticked)
        // Set a flag or special mode?
        // Let's just spawn initial burst here. The tick() method can handle continuous
        // ring.
        this.isCompletionMode = true;
    }

    private boolean isCompletionMode = false;

    public void tick() {
        ticks++;

        if (isCompletionMode) {
            // Completion Ring Effect (Cosmic + Rustic)
            Location loc = hologramLocation.clone();
            double radius = 1.0;
            double speed = 0.1;
            double angle = ticks * speed;

            // Two orbiting stars
            double x1 = Math.cos(angle) * radius;
            double z1 = Math.sin(angle) * radius;
            loc.clone().add(x1, 0, z1).getWorld().spawnParticle(Particle.WAX_ON, loc.clone().add(x1, 0, z1), 1, 0, 0, 0,
                    0);

            double x2 = Math.cos(angle + Math.PI) * radius;
            double z2 = Math.sin(angle + Math.PI) * radius;
            loc.clone().add(x2, 0, z2).getWorld().spawnParticle(Particle.DUST, loc.clone().add(x2, 0, z2), 1, 0, 0, 0,
                    0, new Particle.DustOptions(Color.AQUA, 1));

            // Gentle glow
            if (ticks % 10 == 0) {
                loc.getWorld().spawnParticle(Particle.END_ROD, loc, 1, 0.2, 0.5, 0.2, 0.01);
            }
        }

        // Hologram Bobbing
        if (hologram != null && hologram.isValid()) {
            Location loc = hologram.getLocation();
            loc.setYaw(loc.getYaw() + 1.0f);
            double bobbing = Math.sin(ticks * 0.05) * 0.1;
            loc.setY(hologramLocation.getY() + bobbing);
            hologram.teleport(loc);
        }

        // Status Text Bobbing (Follows Hologram)
        if (statusText != null && statusText.isValid()) {
            Location loc = statusText.getLocation();
            loc.setYaw(loc.getYaw() + 1.0f); // Maybe rotate too?
            double bobbing = Math.sin(ticks * 0.05) * 0.1;
            loc.setY(hologramLocation.getY() + 0.8 + bobbing);
            statusText.teleport(loc);
        }

        // Update Constellation Rotation
        if (Math.abs(rotationSpeed) > 0.01) {
            currentRotation += rotationSpeed;
            double rad = Math.toRadians(currentRotation);
            double cos = Math.cos(rad);
            double sin = Math.sin(rad);

            for (int i = 0; i < initialOffsets.size(); i++) {
                org.bukkit.util.Vector original = initialOffsets.get(i);
                // Rotate around Y axis
                double nx = original.getX() * cos - original.getZ() * sin;
                double nz = original.getX() * sin + original.getZ() * cos;

                Location newLoc = center.clone().add(nx, original.getY(), nz);
                nodeLocations.set(i, newLoc);
            }

            for (int i = 0; i < initialInterferenceOffsets.size(); i++) {
                org.bukkit.util.Vector original = initialInterferenceOffsets.get(i);
                // Counter-Rotate Interference for Chaos Effect? Or same direction?
                // Let's rotate same direction for now to simulate orbit.
                double nx = original.getX() * cos - original.getZ() * sin;
                double nz = original.getX() * sin + original.getZ() * cos;

                Location newLoc = center.clone().add(nx, original.getY(), nz);
                interferenceLocations.set(i, newLoc);
            }
        }

        // Render Nodes (Particles)
        for (int i = 0; i < nodeLocations.size(); i++) {
            Location loc = nodeLocations.get(i);
            boolean isHit = hitIndices.contains(i);

            if (isHit) {
                // Gold Node
                if (ticks % 5 == 0) {
                    Particle.DustOptions gold = new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.5f);
                    loc.getWorld().spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0, gold);
                }
            } else {
                // Active/Target Node (Cyan Pulsing)
                float size = 1.0f + (float) Math.sin(ticks * 0.1) * 0.2f;
                Particle.DustOptions cyan = new Particle.DustOptions(Color.AQUA, size);
                loc.getWorld().spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0, cyan);

                // Occasional sparkle
                if (Math.random() < 0.05) {
                    loc.getWorld().spawnParticle(Particle.END_ROD, loc, 1, 0, 0, 0, 0.01);
                }
            }
        }

        // Render Interference Nodes (Purple Void Rifts)
        for (Location loc : interferenceLocations) {
            // Pulsing Purple/Black
            float size = 1.2f + (float) Math.cos(ticks * 0.15) * 0.3f;
            Particle.DustOptions voidPurple = new Particle.DustOptions(Color.fromRGB(148, 0, 211), size); // Dark Violet
            loc.getWorld().spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0, voidPurple);

            // Core Black
            if (ticks % 3 == 0) {
                Particle.DustOptions black = new Particle.DustOptions(Color.BLACK, 0.8f);
                loc.getWorld().spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0, black);
            }

            // Smoke effect
            if (Math.random() < 0.1) {
                loc.getWorld().spawnParticle(Particle.SMOKE, loc, 1, 0.05, 0.05, 0.05, 0.01);
            }
        }
    }

    public List<Location> getNodeLocations() {
        return nodeLocations;
    }

    public List<Location> getInterferenceLocations() {
        return interferenceLocations;
    }

    private void drawParticleLine(Location start, Location end, Particle particle, int points) {
        double dist = start.distance(end);
        double interval = dist / points;
        org.bukkit.util.Vector dir = end.toVector().subtract(start.toVector()).normalize().multiply(interval);

        Location current = start.clone();
        for (int i = 0; i < points; i++) {
            start.getWorld().spawnParticle(particle, current, 1, 0, 0, 0, 0);
            current.add(dir);
        }
    }

    public void cleanup() {
        if (hologram != null)
            hologram.remove();
        if (statusText != null)
            statusText.remove();
        nodeLocations.clear();
        hitIndices.clear();
    }
}
