package io.github.wintermarkinteractive.crates;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;

/**
 * Floating text above every crate, using TextDisplay entities.
 * They are not saved with the world (non-persistent): they disappear when the chunk unloads
 * and are spawned again when it loads, so they can never pile up.
 */
public final class HologramManager implements Listener {

    private final CratesPlugin plugin;
    private final NamespacedKey tag;
    private final Map<String, TextDisplay> holograms = new HashMap<>(); // block key -> entity

    public HologramManager(CratesPlugin plugin) {
        this.plugin = plugin;
        this.tag = new NamespacedKey(plugin, "hologram");
    }

    /** Removes all holograms and spawns them again for every crate in a loaded chunk. */
    public void refresh() {
        removeAll();
        if (!plugin.getConfig().getBoolean("holograms.enabled")) {
            return;
        }
        for (String key : plugin.locations().all().keySet()) {
            spawnIfLoaded(key);
        }
    }

    public void removeAll() {
        for (TextDisplay display : holograms.values()) {
            if (display.isValid()) {
                display.remove();
            }
        }
        holograms.clear();
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        // Safety: remove leftover holograms of this plugin (e.g. after a crash).
        for (Entity e : chunk.getEntities()) {
            if (e instanceof TextDisplay && e.getPersistentDataContainer().has(tag) && !holograms.containsValue(e)) {
                e.remove();
            }
        }
        if (!plugin.getConfig().getBoolean("holograms.enabled")) {
            return;
        }
        for (String key : plugin.locations().all().keySet()) {
            Location loc = LocationManager.toLocation(key);
            if (loc != null && loc.getWorld().equals(chunk.getWorld())
                    && (loc.getBlockX() >> 4) == chunk.getX() && (loc.getBlockZ() >> 4) == chunk.getZ()) {
                spawnIfLoaded(key);
            }
        }
    }

    private void spawnIfLoaded(String key) {
        TextDisplay existing = holograms.get(key);
        if (existing != null && existing.isValid()) {
            return;
        }
        Location block = LocationManager.toLocation(key);
        if (block == null || !block.getWorld().isChunkLoaded(block.getBlockX() >> 4, block.getBlockZ() >> 4)) {
            return;
        }
        Crate crate = plugin.crates().get(plugin.locations().all().get(key));
        if (crate == null || crate.hologram().isEmpty()) {
            return;
        }
        Messages m = plugin.messages();
        Component text = Component.empty();
        for (int i = 0; i < crate.hologram().size(); i++) {
            if (i > 0) {
                text = text.append(Component.newline());
            }
            text = text.append(m.parse(crate.hologram().get(i), Placeholder.parsed("crate", crate.name())));
        }
        double height = plugin.getConfig().getDouble("holograms.height", 1.4);
        Location at = block.clone().add(0.5, height, 0.5);
        Component finalText = text;
        TextDisplay display = at.getWorld().spawn(at, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.text(finalText);
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            d.setDefaultBackground(false);
            d.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
            d.setViewRange(0.5f);
            d.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
        });
        holograms.put(key, display);
    }
}
