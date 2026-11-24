package io.github.wintermarkinteractive.crates;

import io.github.wintermarkinteractive.crates.Crate.Reward;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * The opening animation: a row of rewards rolls past a pointer, slows down and stops on the prize.
 * The prize is decided before the animation starts, so closing the menu can't change it,
 * and it is always handed out exactly once (also when the player closes the menu or logs out).
 */
public final class OpeningManager {

    /** Delay in ticks between steps: fast at the start, slowing down at the end. */
    private static final int[] STEP_DELAYS = {
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            2, 2, 2, 2, 2, 2, 2, 2,
            3, 3, 3, 3, 3,
            4, 4, 4,
            6, 6,
            9
    };
    private static final int ROW_START = 9;   // middle row of a 3-row chest
    private static final int CENTER = 13;
    private static final int CLOSE_DELAY_TICKS = 40;

    private final class Opening {
        final Player player;
        final Crate crate;
        final Reward prize;
        final List<ItemStack> strip = new ArrayList<>();
        final Inventory inventory;
        int step;
        int waitTicks;
        boolean given;
        BukkitTask task;

        Opening(Player player, Crate crate, Reward prize, Inventory inventory) {
            this.player = player;
            this.crate = crate;
            this.prize = prize;
            this.inventory = inventory;
        }
    }

    private final CratesPlugin plugin;
    private final Random random = new Random();
    private final Map<UUID, Opening> openings = new HashMap<>();

    public OpeningManager(CratesPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isOpening(Player player) {
        return openings.containsKey(player.getUniqueId());
    }

    /** Starts the animation (or gives the prize directly when animations are off). */
    public void open(Player player, Crate crate) {
        Reward prize = crate.roll(random);
        if (!plugin.getConfig().getBoolean("animation.enabled")) {
            plugin.crates().giveReward(player, crate, prize);
            playSound(player, "sounds.win", 1f);
            return;
        }

        CrateMenus.MenuHolder holder = new CrateMenus.MenuHolder(crate.id(), true);
        Messages m = plugin.messages();
        Inventory inv = Bukkit.createInventory(holder, 27,
                m.parse(m.raw("opening-title"), Placeholder.parsed("crate", crate.name())));
        holder.inventory(inv);

        ItemStack filler = CrateMenus.pane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack pointer = CrateMenus.pane(Material.LIME_STAINED_GLASS_PANE);
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }
        inv.setItem(4, pointer);
        inv.setItem(22, pointer);

        Opening o = new Opening(player, crate, prize, inv);
        // Strip: random rewards, with the prize placed so that it ends in the center slot.
        int length = STEP_DELAYS.length + 9;
        for (int i = 0; i < length; i++) {
            o.strip.add(crate.roll(random).display());
        }
        o.strip.set(STEP_DELAYS.length + (CENTER - ROW_START), prize.display());

        openings.put(player.getUniqueId(), o);
        drawRow(o);
        player.openInventory(inv);
        o.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(o), 1L, 1L);
    }

    private void tick(Opening o) {
        if (o.given) {
            // Prize shown: wait, then close the menu.
            if (++o.waitTicks >= CLOSE_DELAY_TICKS) {
                finish(o, true);
            }
            return;
        }
        if (++o.waitTicks < STEP_DELAYS[Math.min(o.step, STEP_DELAYS.length - 1)]) {
            return;
        }
        o.waitTicks = 0;
        o.step++;
        drawRow(o);
        playSound(o.player, "sounds.tick", 0.8f + 0.6f * o.step / STEP_DELAYS.length);
        if (o.step >= STEP_DELAYS.length) {
            reveal(o);
        }
    }

    private void drawRow(Opening o) {
        for (int i = 0; i < 9; i++) {
            o.inventory.setItem(ROW_START + i, o.strip.get(o.step + i));
        }
    }

    /** Animation stopped on the prize: highlight it and hand it out. */
    private void reveal(Opening o) {
        // Only the prize stays visible, in the center, framed in yellow.
        ItemStack filler = CrateMenus.pane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack win = CrateMenus.pane(Material.YELLOW_STAINED_GLASS_PANE);
        for (int i = 0; i < 27; i++) {
            if (i != CENTER) {
                o.inventory.setItem(i, filler);
            }
        }
        for (int slot : new int[]{3, 4, 5, 12, 14, 21, 22, 23}) {
            o.inventory.setItem(slot, win);
        }
        give(o);
        o.waitTicks = 0;
    }

    private void give(Opening o) {
        if (o.given) {
            return;
        }
        o.given = true;
        plugin.crates().giveReward(o.player, o.crate, o.prize);
        playSound(o.player, "sounds.win", 1f);
    }

    /** Ends the opening. The prize is given if it wasn't yet (e.g. the player closed the menu early). */
    private void finish(Opening o, boolean closeMenu) {
        if (o.task != null) {
            o.task.cancel();
        }
        openings.remove(o.player.getUniqueId());
        give(o);
        if (closeMenu && o.player.getOpenInventory().getTopInventory().equals(o.inventory)) {
            o.player.closeInventory();
        }
    }

    /** Called when the player closes the menu or quits. */
    public void abort(Player player) {
        Opening o = openings.get(player.getUniqueId());
        if (o != null) {
            finish(o, false);
        }
    }

    /** On disable: hand out every running prize. */
    public void finishAll() {
        for (Opening o : new ArrayList<>(openings.values())) {
            finish(o, true);
        }
    }

    public void playSound(Player player, String path, float pitch) {
        String key = plugin.getConfig().getString(path, "");
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            player.playSound(Sound.sound(Key.key(key), Sound.Source.MASTER, 1f, pitch));
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Bad sound '" + key + "' at " + path + " in config.yml.");
        }
    }

    /** Opens many keys at once without animation. Returns how many were opened. */
    public int massOpen(Player player, Crate crate, int amount) {
        for (int i = 0; i < amount; i++) {
            plugin.crates().giveReward(player, crate, crate.roll(random));
        }
        playSound(player, "sounds.win", 1f);
        return amount;
    }
}
