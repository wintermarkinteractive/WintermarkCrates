package io.github.wintermarkinteractive.crates;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Locale;

public final class CrateListener implements Listener {

    private final CratesPlugin plugin;

    public CrateListener(CratesPlugin plugin) {
        this.plugin = plugin;
    }

    /** Right-click a crate = open with a key (sneak = open all keys), left-click = preview. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCrateClick(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        String crateId = plugin.locations().crateAt(block);
        if (crateId == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        Messages m = plugin.messages();
        Crate crate = plugin.crates().get(crateId);
        if (crate == null) {
            if (player.hasPermission(CratesPlugin.PERM_ADMIN)) {
                m.sendRaw(player, "<red>This block is linked to crate <white>" + crateId
                        + "</white>, which is not in config.yml anymore. Use <yellow>/crate remove</yellow>.");
            }
            return;
        }

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (player.hasPermission(CratesPlugin.PERM_PREVIEW)) {
                plugin.menus().openPreview(player, crate);
            }
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!player.hasPermission(CratesPlugin.PERM_USE)) {
            m.send(player, "no-permission");
            return;
        }
        if (plugin.openings().isOpening(player)) {
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        String keyFor = plugin.crates().keyCrateId(hand);
        if (keyFor == null || !keyFor.equals(crate.id().toLowerCase(Locale.ROOT))) {
            m.send(player, "need-key", Placeholder.parsed("crate", crate.name()));
            plugin.openings().playSound(player, "sounds.no-key", 1f);
            if (plugin.getConfig().getBoolean("pushback")) {
                Vector away = player.getLocation().toVector().subtract(block.getLocation().add(0.5, 0, 0.5).toVector());
                away.setY(0);
                if (away.lengthSquared() > 0.0001) {
                    player.setVelocity(away.normalize().multiply(0.6).setY(0.3));
                }
            }
            return;
        }

        if (player.isSneaking() && crate.massOpen()) {
            int amount = Math.min(hand.getAmount(), Math.max(1, plugin.getConfig().getInt("mass-open-limit")));
            hand.subtract(amount);
            plugin.openings().massOpen(player, crate, amount);
            m.send(player, "mass-opened",
                    Placeholder.unparsed("amount", String.valueOf(amount)),
                    Placeholder.parsed("crate", crate.name()));
            return;
        }

        hand.subtract(1);
        plugin.openings().open(player, crate);
    }

    /** Keys can never be placed as blocks (e.g. tripwire hooks). */
    @EventHandler(ignoreCancelled = true)
    public void onKeyPlace(BlockPlaceEvent event) {
        if (plugin.crates().keyCrateId(event.getItemInHand()) != null) {
            event.setCancelled(true);
        }
    }

    /** Crate blocks can't be broken; admins remove them with /crate remove. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCrateBreak(BlockBreakEvent event) {
        if (plugin.locations().crateAt(event.getBlock()) == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getPlayer().hasPermission(CratesPlugin.PERM_ADMIN)) {
            plugin.messages().sendRaw(event.getPlayer(),
                    "<gray>This is a crate. Look at it and use <yellow>/crate remove</yellow> first.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(b -> plugin.locations().crateAt(b) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(b -> plugin.locations().crateAt(b) != null);
    }

    // ---------------------------------------------------------------- menus

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof CrateMenus.MenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof CrateMenus.MenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMenuClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof CrateMenus.MenuHolder holder && holder.opening()
                && event.getPlayer() instanceof Player player) {
            plugin.openings().abort(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.openings().abort(event.getPlayer());
    }
}
