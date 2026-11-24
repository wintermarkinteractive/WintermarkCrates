package io.github.wintermarkinteractive.crates;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** /crate (alias /crates). */
public final class CrateCommand implements BasicCommand {

    private static final List<String> ADMIN_SUBS = List.of("set", "remove", "givekey", "giveall", "list", "reload");
    private static final int MAX_KEYS = 10_000;

    private final CratesPlugin plugin;

    public CrateCommand(CratesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if (ADMIN_SUBS.contains(sub) && !sender.hasPermission(CratesPlugin.PERM_ADMIN)) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        switch (sub) {
            case "preview" -> preview(sender, args);
            case "set" -> set(sender, args);
            case "remove" -> remove(sender);
            case "givekey" -> giveKey(sender, args);
            case "giveall" -> giveAll(sender, args);
            case "list" -> list(sender);
            case "reload" -> reload(sender);
            default -> help(sender);
        }
    }

    private void help(CommandSender sender) {
        Messages m = plugin.messages();
        m.sendRaw(sender, "<gold><bold>Wintermark Crates</bold></gold> <gray>v" + plugin.getPluginMeta().getVersion());
        m.sendRaw(sender, "<gray>Right-click a crate with its key to open it (sneak to open all keys), left-click to preview.");
        if (sender.hasPermission(CratesPlugin.PERM_PREVIEW)) {
            m.sendRaw(sender, "<yellow>/crate preview <crate></yellow> <gray>- see the rewards and chances");
        }
        if (sender.hasPermission(CratesPlugin.PERM_ADMIN)) {
            m.sendRaw(sender, "<yellow>/crate set <crate></yellow> <gray>- turn the block you look at into a crate");
            m.sendRaw(sender, "<yellow>/crate remove</yellow> <gray>- remove the crate you look at");
            m.sendRaw(sender, "<yellow>/crate givekey <player> <crate> [amount]</yellow> <gray>- give keys (works from console)");
            m.sendRaw(sender, "<yellow>/crate giveall <crate> [amount]</yellow> <gray>- give keys to everyone online");
            m.sendRaw(sender, "<yellow>/crate list</yellow> <gray>- crates and their locations");
            m.sendRaw(sender, "<yellow>/crate reload</yellow> <gray>- reload config.yml");
        }
    }

    private Crate crateArg(CommandSender sender, String[] args, int index, String usage) {
        if (args.length <= index) {
            plugin.messages().sendRaw(sender, "<red>Usage: " + usage);
            return null;
        }
        Crate crate = plugin.crates().get(args[index]);
        if (crate == null) {
            plugin.messages().send(sender, "unknown-crate", Placeholder.unparsed("crate", args[index]),
                    Placeholder.unparsed("crates", crateNames()));
        }
        return crate;
    }

    private String crateNames() {
        List<String> names = new ArrayList<>();
        for (Crate c : plugin.crates().all()) {
            names.add(c.id());
        }
        return String.join(", ", names);
    }

    private int amountArg(CommandSender sender, String[] args, int index) {
        if (args.length <= index) {
            return 1;
        }
        try {
            int n = Integer.parseInt(args[index]);
            if (n >= 1 && n <= MAX_KEYS) {
                return n;
            }
        } catch (NumberFormatException ignored) {
            // handled below
        }
        plugin.messages().sendRaw(sender, "<red>Amount must be a number from 1 to " + MAX_KEYS + ".");
        return -1;
    }

    private void preview(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "players-only");
            return;
        }
        if (!player.hasPermission(CratesPlugin.PERM_PREVIEW)) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        Crate crate = crateArg(sender, args, 1, "/crate preview <crate>");
        if (crate != null) {
            plugin.menus().openPreview(player, crate);
        }
    }

    private Block targetBlock(Player player) {
        Block block = player.getTargetBlockExact(6);
        if (block == null || block.getType().isAir()) {
            plugin.messages().sendRaw(player, "<red>Look at a block (within 6 blocks).");
            return null;
        }
        return block;
    }

    private void set(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "players-only");
            return;
        }
        Crate crate = crateArg(sender, args, 1, "/crate set <crate>");
        if (crate == null) {
            return;
        }
        Block block = targetBlock(player);
        if (block == null) {
            return;
        }
        plugin.locations().set(block, crate.id());
        plugin.holograms().refresh();
        plugin.messages().sendRaw(player, "<green>This " + block.getType().name().toLowerCase(Locale.ROOT)
                + " is now a <reset>" + crate.name() + "<green>. Right-click it with a key to open.");
    }

    private void remove(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "players-only");
            return;
        }
        Block block = targetBlock(player);
        if (block == null) {
            return;
        }
        if (plugin.locations().remove(block)) {
            plugin.holograms().refresh();
            plugin.messages().sendRaw(player, "<green>Crate removed. The block is a normal block again.");
        } else {
            plugin.messages().sendRaw(player, "<red>That block is not a crate.");
        }
    }

    private void giveKey(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.messages().sendRaw(sender, "<red>Usage: /crate givekey <player> <crate> [amount]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messages().sendRaw(sender, "<red>Player <white>" + args[1] + "</white> is not online.");
            return;
        }
        Crate crate = crateArg(sender, args, 2, "/crate givekey <player> <crate> [amount]");
        int amount = amountArg(sender, args, 3);
        if (crate == null || amount < 1) {
            return;
        }
        plugin.crates().giveKeys(target, crate, amount);
        plugin.messages().send(target, "key-received",
                Placeholder.unparsed("amount", String.valueOf(amount)),
                Placeholder.parsed("crate", crate.name()));
        if (sender != target) {
            plugin.messages().sendRaw(sender, "<green>Gave <white>" + amount + "</white> key(s) for <reset>"
                    + crate.name() + "<green> to <white>" + target.getName() + "</white>.");
        }
    }

    private void giveAll(CommandSender sender, String[] args) {
        Crate crate = crateArg(sender, args, 1, "/crate giveall <crate> [amount]");
        int amount = amountArg(sender, args, 2);
        if (crate == null || amount < 1) {
            return;
        }
        int players = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            plugin.crates().giveKeys(p, crate, amount);
            plugin.messages().send(p, "key-received",
                    Placeholder.unparsed("amount", String.valueOf(amount)),
                    Placeholder.parsed("crate", crate.name()));
            players++;
        }
        plugin.messages().sendRaw(sender, "<green>Gave <white>" + amount + "</white> key(s) for <reset>"
                + crate.name() + "<green> to <white>" + players + "</white> player(s).");
    }

    private void list(CommandSender sender) {
        Messages m = plugin.messages();
        Map<String, List<String>> where = new HashMap<>();
        for (Map.Entry<String, String> e : plugin.locations().all().entrySet()) {
            where.computeIfAbsent(e.getValue().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(e.getKey());
        }
        m.sendRaw(sender, "<gold>Crates (" + plugin.crates().all().size() + "):");
        for (Crate c : plugin.crates().all()) {
            List<String> locs = where.getOrDefault(c.id().toLowerCase(Locale.ROOT), List.of());
            m.sendRaw(sender, "<yellow>" + c.id() + "</yellow> <gray>- <reset>" + c.name() + "<gray> - "
                    + c.rewards().size() + " rewards - " + (locs.isEmpty() ? "not placed" : String.join(" | ", locs)));
        }
    }

    private void reload(CommandSender sender) {
        plugin.loadAll();
        plugin.messages().sendRaw(sender, "<green>Reloaded: <white>" + plugin.crates().all().size()
                + "</white> crate(s), <white>" + plugin.locations().all().size() + "</white> placed.");
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        boolean admin = sender.hasPermission(CratesPlugin.PERM_ADMIN);
        List<String> options = new ArrayList<>();
        String first = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (args.length <= 1) {
            options.add("help");
            if (sender.hasPermission(CratesPlugin.PERM_PREVIEW)) {
                options.add("preview");
            }
            if (admin) {
                options.addAll(ADMIN_SUBS);
            }
        } else if (args.length == 2 && (first.equals("preview") || (admin && (first.equals("set") || first.equals("giveall"))))) {
            addCrates(options);
        } else if (admin && first.equals("givekey") && args.length == 2) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                options.add(p.getName());
            }
        } else if (admin && first.equals("givekey") && args.length == 3) {
            addCrates(options);
        } else if (admin && ((first.equals("givekey") && args.length == 4) || (first.equals("giveall") && args.length == 3))) {
            options.addAll(List.of("1", "5", "10", "64"));
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(last)) {
                result.add(o);
            }
        }
        return result;
    }

    private void addCrates(List<String> options) {
        for (Crate c : plugin.crates().all()) {
            options.add(c.id());
        }
    }
}
