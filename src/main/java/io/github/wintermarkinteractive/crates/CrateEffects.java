package io.github.wintermarkinteractive.crates;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;

/** Particles circling around every crate while a player is nearby. */
public final class CrateEffects {

    private static final double NEARBY = 24;
    private static final int POINTS = 2;          // particles per crate per tick of this task
    private static final double RADIUS = 0.8;

    private final CratesPlugin plugin;
    private BukkitTask task;
    private Particle particle;
    private double angle;

    public CrateEffects(CratesPlugin plugin) {
        this.plugin = plugin;
    }

    /** Reads the settings and (re)starts the effect task. */
    public void start() {
        stop();
        if (!plugin.getConfig().getBoolean("particles.enabled")) {
            return;
        }
        String name = plugin.getConfig().getString("particles.type", "END_ROD");
        try {
            particle = Particle.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unknown particle '" + name + "' in config.yml - using END_ROD.");
            particle = Particle.END_ROD;
        }
        if (particle.getDataType() != Void.class) {
            plugin.getLogger().warning("Particle '" + name + "' needs extra data and can't be used - using END_ROD.");
            particle = Particle.END_ROD;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 2L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        angle += Math.PI / 16;
        for (String key : plugin.locations().all().keySet()) {
            Location block = LocationManager.toLocation(key);
            if (block == null || !hasPlayerNearby(block)) {
                continue;
            }
            World world = block.getWorld();
            if (!world.isChunkLoaded(block.getBlockX() >> 4, block.getBlockZ() >> 4)) {
                continue;
            }
            double cx = block.getX() + 0.5;
            double cz = block.getZ() + 0.5;
            double y = block.getY() + 0.5 + 0.4 * Math.sin(angle / 2);
            for (int i = 0; i < POINTS; i++) {
                double a = angle + i * (Math.PI * 2 / POINTS);
                world.spawnParticle(particle, cx + Math.cos(a) * RADIUS, y, cz + Math.sin(a) * RADIUS, 1, 0, 0, 0, 0);
            }
        }
    }

    private static boolean hasPlayerNearby(Location loc) {
        for (Player p : loc.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(loc) < NEARBY * NEARBY) {
                return true;
            }
        }
        return false;
    }
}
