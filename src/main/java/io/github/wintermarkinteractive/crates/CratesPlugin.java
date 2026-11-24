package io.github.wintermarkinteractive.crates;

import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class CratesPlugin extends JavaPlugin {

    public static final String PERM_USE = "wintermark.crates.use";
    public static final String PERM_PREVIEW = "wintermark.crates.preview";
    public static final String PERM_ADMIN = "wintermark.crates.admin";

    private Messages messages;
    private CrateManager crates;
    private LocationManager locations;
    private CrateMenus menus;
    private OpeningManager openings;
    private HologramManager holograms;
    private CrateEffects effects;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        messages = new Messages(this);
        crates = new CrateManager(this);
        locations = new LocationManager(this);
        menus = new CrateMenus(this);
        openings = new OpeningManager(this);
        holograms = new HologramManager(this);
        effects = new CrateEffects(this);

        loadAll();

        getServer().getPluginManager().registerEvents(new CrateListener(this), this);
        getServer().getPluginManager().registerEvents(holograms, this);
        registerCommand("crate", "Wintermark Crates", List.of("crates"), new CrateCommand(this));

        getLogger().info("Loaded " + crates.all().size() + " crate(s), " + locations.all().size() + " placed.");
    }

    @Override
    public void onDisable() {
        if (openings != null) {
            openings.finishAll();
        }
        if (holograms != null) {
            holograms.removeAll();
        }
        if (effects != null) {
            effects.stop();
        }
    }

    /** (Re)loads config.yml and locations.yml and rebuilds the holograms. */
    public void loadAll() {
        reloadConfig();
        addMissingConfigKeys();
        messages.load();
        crates.load();
        locations.load();
        holograms.refresh();
        effects.start();
    }

    /**
     * Adds settings that are new in this version to an existing config.yml (with comments).
     * The "crates" section is skipped: owners may delete the example crates.
     */
    private void addMissingConfigKeys() {
        FileConfiguration config = getConfig();
        Configuration defaults = config.getDefaults();
        if (defaults == null) {
            return;
        }
        boolean changed = false;
        for (String key : defaults.getKeys(true)) {
            if (key.equals("crates") || key.startsWith("crates.") || config.isSet(key)) {
                continue;
            }
            if (defaults.isConfigurationSection(key)) {
                config.createSection(key);
            } else {
                config.set(key, defaults.get(key));
            }
            config.setComments(key, defaults.getComments(key));
            changed = true;
        }
        if (changed) {
            saveConfig();
            reloadConfig();
            getLogger().info("Added new settings to config.yml.");
        }
    }

    public Messages messages() { return messages; }
    public CrateManager crates() { return crates; }
    public LocationManager locations() { return locations; }
    public CrateMenus menus() { return menus; }
    public OpeningManager openings() { return openings; }
    public HologramManager holograms() { return holograms; }
}
