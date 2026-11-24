package io.github.wintermarkinteractive.crates;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Which blocks are crates (plugins/WintermarkCrates/locations.yml). */
public final class LocationManager {

    /** A block position as text: "world,x,y,z". */
    public static String key(Block block) {
        return block.getWorld().getName() + "," + block.getX() + "," + block.getY() + "," + block.getZ();
    }

    /** Parses "world,x,y,z" back into a block location, or null when the world is not loaded. */
    public static Location toLocation(String key) {
        String[] p = key.split(",");
        if (p.length != 4) {
            return null;
        }
        World w = org.bukkit.Bukkit.getWorld(p[0]);
        if (w == null) {
            return null;
        }
        try {
            return new Location(w, Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private final CratesPlugin plugin;
    private final File file;
    private final Map<String, String> crateAt = new LinkedHashMap<>(); // block key -> crate id

    public LocationManager(CratesPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "locations.yml");
    }

    public void load() {
        crateAt.clear();
        if (!file.exists()) {
            return;
        }
        ConfigurationSection root = YamlConfiguration.loadConfiguration(file).getConfigurationSection("crates");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            // YAML keys can't contain dots safely, so they are stored with ';' instead of ','.
            crateAt.put(key.replace(';', ','), root.getString(key));
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, String> e : crateAt.entrySet()) {
            yaml.set("crates." + e.getKey().replace(',', ';'), e.getValue());
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save locations.yml: " + ex.getMessage());
        }
    }

    /** The crate id at this block, or null. */
    public String crateAt(Block block) {
        return crateAt.get(key(block));
    }

    public void set(Block block, String crateId) {
        crateAt.put(key(block), crateId);
        save();
    }

    public boolean remove(Block block) {
        boolean removed = crateAt.remove(key(block)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public Map<String, String> all() {
        return Collections.unmodifiableMap(crateAt);
    }
}
