package com.starryforge.features.forging.visual;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.EntityType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public class Stardust {

    private final BlockDisplay display;
    private final Location target;
    private final double speed;
    private boolean isDead = false;

    public Stardust(Location spawnLoc, Location target, double speed) {
        this.target = target;
        this.speed = speed;

        display = (BlockDisplay) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.BLOCK_DISPLAY);
        display.setBlock(Material.AMETHYST_CLUSTER.createBlockData()); // Star-like appearance
        display.setBillboard(Billboard.CENTER); // Make it face camera if using item display, but block is 3D
        // Make it small
        display.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f(),
                new Vector3f(0.3f, 0.3f, 0.3f), // Scale down
                new AxisAngle4f()));

        display.setGlowing(true);
        // Set glow color if possible (requires Team packet or API)
    }

    public void tick() {
        if (isDead)
            return;

        Location current = display.getLocation();
        double distance = current.distance(target);

        if (distance < 0.2) {
            // Reached target (Miss)
            remove();
            return;
        }

        // Move towards target
        Vector direction = target.toVector().subtract(current.toVector()).normalize().multiply(speed);

        // Add some spiral motion? For simple V1, direct line is fine.
        // Or adding a small sine wave offset.

        Location newLoc = current.add(direction);
        display.teleport(newLoc);
    }

    public Location getLocation() {
        return display.getLocation();
    }

    public void remove() {
        if (!isDead) {
            display.remove();
            isDead = true;
        }
    }

    public boolean isDead() {
        return isDead;
    }
}
