package io.github.wintermarkinteractive.crates;

import io.github.wintermarkinteractive.crates.Crate.Reward;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Loads crates from config.yml, creates/recognises keys and hands out rewards. */
public final class CrateManager {

    private final CratesPlugin plugin;
    private final NamespacedKey keyTag;
    private final Map<String, Crate> crates = new LinkedHashMap<>();

    public CrateManager(CratesPlugin plugin) {
        this.plugin = plugin;
        this.keyTag = new NamespacedKey(plugin, "key");
    }

    public void load() {
        crates.clear();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("crates");
        if (root == null) {
            plugin.getLogger().warning("config.yml has no 'crates' section.");
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection c = root.getConfigurationSection(id);
            if (c == null) {
                continue;
            }
            String where = "crates." + id;
            String name = c.getString("name", id);
            List<Reward> rewards = new ArrayList<>();
            ConfigurationSection rs = c.getConfigurationSection("rewards");
            if (rs != null) {
                for (String rid : rs.getKeys(false)) {
                    ConfigurationSection r = rs.getConfigurationSection(rid);
                    if (r == null) {
                        continue;
                    }
                    String rWhere = where + ".rewards." + rid;
                    List<ItemStack> items = new ArrayList<>();
                    for (String line : r.getStringList("items")) {
                        ItemStack item = parseItem(line, rWhere);
                        if (item != null) {
                            items.add(item);
                        }
                    }
                    ItemStack display = r.isString("display") ? parseItem(r.getString("display"), rWhere) : null;
                    if (display == null) {
                        display = items.isEmpty() ? new ItemStack(Material.PAPER) : items.get(0).clone();
                    }
                    String displayName = r.getString("display-name", "");
                    List<String> displayLore = r.getStringList("display-lore");
                    if (!displayName.isEmpty() || !displayLore.isEmpty()) {
                        Messages m = plugin.messages();
                        display.editMeta(meta -> {
                            if (!displayName.isEmpty()) {
                                meta.displayName(noItalic(m.parse(displayName)));
                            }
                            if (!displayLore.isEmpty()) {
                                List<Component> lore = new ArrayList<>();
                                for (String l : displayLore) {
                                    lore.add(noItalic(m.parse(l)));
                                }
                                meta.lore(lore);
                            }
                        });
                    }
                    double weight = r.getDouble("chance", 1);
                    if (weight <= 0) {
                        plugin.getLogger().warning(rWhere + ".chance must be above 0 - reward skipped.");
                        continue;
                    }
                    rewards.add(new Reward(rid, weight, display, items, r.getStringList("commands"),
                            r.getString("message", ""), r.getBoolean("broadcast", false)));
                }
            }
            if (rewards.isEmpty()) {
                plugin.getLogger().warning("Crate '" + id + "' has no rewards - skipped.");
                continue;
            }
            crates.put(id.toLowerCase(Locale.ROOT),
                    new Crate(id, name, buildKey(id, c.getConfigurationSection("key")), rewards,
                            c.getBoolean("mass-open", true),
                            c.isList("hologram") ? c.getStringList("hologram") : List.of("<crate>", "<gray>Right-click with a key")));
        }
    }

    private ItemStack buildKey(String crateId, ConfigurationSection k) {
        Material mat = k == null ? null : Material.matchMaterial(k.getString("material", "TRIPWIRE_HOOK"));
        if (mat == null || !mat.isItem() || mat.isAir()) {
            mat = Material.TRIPWIRE_HOOK;
        }
        ItemStack key = new ItemStack(mat);
        Messages m = plugin.messages();
        String name = k == null ? "<gold>" + crateId + " key" : k.getString("name", "<gold>" + crateId + " key");
        List<String> loreLines = k == null ? List.of() : k.getStringList("lore");
        boolean glow = k == null || k.getBoolean("glow", true);
        key.editMeta(meta -> {
            meta.displayName(noItalic(m.parse(name)));
            List<Component> lore = new ArrayList<>();
            for (String l : loreLines) {
                lore.add(noItalic(m.parse(l)));
            }
            meta.lore(lore);
            if (glow) {
                meta.setEnchantmentGlintOverride(true);
            }
        });
        key.editPersistentDataContainer(pdc -> pdc.set(keyTag, PersistentDataType.STRING, crateId.toLowerCase(Locale.ROOT)));
        return key;
    }

    private static Component noItalic(Component c) {
        return c.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** "DIAMOND:3" -> 3 diamonds. */
    public ItemStack parseItem(String line, String where) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String[] parts = line.split(":");
        Material mat = Material.matchMaterial(parts[0].trim());
        if (mat == null || !mat.isItem() || mat.isAir()) {
            plugin.getLogger().warning("Unknown item '" + parts[0] + "' in " + where);
            return null;
        }
        int amount = 1;
        if (parts.length > 1) {
            try {
                amount = Math.max(1, Integer.parseInt(parts[1].trim()));
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Bad amount in '" + line + "' in " + where);
            }
        }
        return new ItemStack(mat, amount);
    }

    public Crate get(String id) {
        return id == null ? null : crates.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<Crate> all() {
        return Collections.unmodifiableCollection(crates.values());
    }

    public ItemStack createKeys(Crate crate, int amount) {
        ItemStack key = crate.keyTemplate().clone();
        key.setAmount(Math.max(1, Math.min(amount, key.getMaxStackSize())));
        return key;
    }

    /** The crate id this key belongs to, or null when the item is not a key. */
    public String keyCrateId(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        return item.getPersistentDataContainer().get(keyTag, PersistentDataType.STRING);
    }

    /** Gives keys, split into stacks; what doesn't fit is dropped at the player's feet. */
    public void giveKeys(Player player, Crate crate, int amount) {
        int left = amount;
        while (left > 0) {
            ItemStack stack = createKeys(crate, left);
            left -= stack.getAmount();
            giveItem(player, stack);
        }
    }

    public void giveItem(Player player, ItemStack item) {
        for (ItemStack rest : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
    }

    /** Hands out one reward: items, console commands, message and optional broadcast. */
    public void giveReward(Player player, Crate crate, Reward reward) {
        for (ItemStack item : reward.items()) {
            giveItem(player, item.clone());
        }
        for (String cmd : reward.commands()) {
            String line = cmd.replace("<player>", player.getName()).replace("<crate>", crate.id());
            if (line.startsWith("/")) {
                line = line.substring(1);
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line);
        }
        Messages m = plugin.messages();
        Component rewardName = rewardName(reward);
        m.sendRaw(player, reward.message(),
                Placeholder.unparsed("player", player.getName()),
                Placeholder.parsed("crate", crate.name()),
                Placeholder.component("reward", rewardName));
        if (reward.broadcast()) {
            String text = m.raw("broadcast");
            if (!text.isEmpty()) {
                Bukkit.getServer().broadcast(m.parse(m.raw("prefix") + text,
                        Placeholder.unparsed("player", player.getName()),
                        Placeholder.parsed("crate", crate.name()),
                        Placeholder.component("reward", rewardName)));
            }
        }
    }

    /** The display name of a reward (custom name or the item's own name). */
    public static Component rewardName(Reward reward) {
        ItemStack d = reward.display();
        Component name = d.getItemMeta() != null && d.getItemMeta().hasDisplayName()
                ? d.getItemMeta().displayName()
                : Component.translatable(d.translationKey());
        if (d.getAmount() > 1 && !(d.getItemMeta() != null && d.getItemMeta().hasDisplayName())) {
            name = Component.text(d.getAmount() + "x ").append(name);
        }
        return name == null ? Component.text(reward.id()) : name;
    }
}
