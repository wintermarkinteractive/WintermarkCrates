package io.github.wintermarkinteractive.crates;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/** One crate type from config.yml: its key item and its rewards. */
public record Crate(String id, String name, ItemStack keyTemplate, List<Reward> rewards, boolean massOpen,
                    List<String> hologram) {

    /** One possible reward. weight = relative chance (shown as a percentage in the preview). */
    public record Reward(String id, double weight, ItemStack display, List<ItemStack> items,
                         List<String> commands, String message, boolean broadcast) {
    }

    public double totalWeight() {
        double total = 0;
        for (Reward r : rewards) {
            total += r.weight();
        }
        return total;
    }

    /** Chance of a reward in percent, e.g. 12.5. */
    public double percent(Reward reward) {
        double total = totalWeight();
        return total <= 0 ? 0 : reward.weight() * 100.0 / total;
    }

    /** Picks a reward by weight. */
    public Reward roll(java.util.Random random) {
        double total = totalWeight();
        double r = random.nextDouble() * total;
        for (Reward reward : rewards) {
            r -= reward.weight();
            if (r < 0) {
                return reward;
            }
        }
        return rewards.get(rewards.size() - 1);
    }
}
