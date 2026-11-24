package io.github.wintermarkinteractive.crates;

import io.github.wintermarkinteractive.crates.Crate.Reward;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The preview menu (all rewards with chances). Clicks in our menus are blocked by CrateListener. */
public final class CrateMenus {

    /** Marks inventories that belong to this plugin, so clicks in them can be cancelled. */
    public static final class MenuHolder implements InventoryHolder {
        private final String crateId;
        private final boolean opening;
        private Inventory inventory;

        MenuHolder(String crateId, boolean opening) {
            this.crateId = crateId;
            this.opening = opening;
        }

        public String crateId() {
            return crateId;
        }

        public boolean opening() {
            return opening;
        }

        void inventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final CratesPlugin plugin;

    public CrateMenus(CratesPlugin plugin) {
        this.plugin = plugin;
    }

    public void openPreview(Player player, Crate crate) {
        int rows = Math.min(6, Math.max(1, (crate.rewards().size() + 8) / 9));
        MenuHolder holder = new MenuHolder(crate.id(), false);
        Messages m = plugin.messages();
        Component title = m.parse(m.raw("preview-title"), Placeholder.parsed("crate", crate.name()));
        Inventory inv = Bukkit.createInventory(holder, rows * 9, title);
        holder.inventory(inv);

        // Sorted from rarest to most common.
        List<Reward> sorted = new ArrayList<>(crate.rewards());
        sorted.sort((a, b) -> Double.compare(a.weight(), b.weight()));
        int slot = 0;
        for (Reward reward : sorted) {
            if (slot >= inv.getSize()) {
                break;
            }
            inv.setItem(slot++, withChanceLore(reward, crate.percent(reward)));
        }
        player.openInventory(inv);
    }

    private ItemStack withChanceLore(Reward reward, double percent) {
        ItemStack item = reward.display().clone();
        Messages m = plugin.messages();
        String chance = percent >= 10 || percent == Math.floor(percent)
                ? String.format(Locale.ROOT, "%.0f", percent)
                : String.format(Locale.ROOT, "%.1f", percent);
        Component line = m.parse(m.raw("preview-chance"), Placeholder.unparsed("chance", chance))
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
        item.editMeta(meta -> {
            List<Component> lore = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.add(Component.empty());
            lore.add(line);
            meta.lore(lore);
        });
        return item;
    }

    /** A filler pane without a name, for menu borders. */
    public static ItemStack pane(Material material) {
        ItemStack pane = new ItemStack(material);
        pane.editMeta(meta -> meta.displayName(Component.empty()));
        return pane;
    }
}
