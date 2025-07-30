package bloody.devmules.iaddithisSkilling;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.TreeMap;

public class SkillsCommand implements CommandExecutor {
    public static final String[] ALL_SKILLS = {
            "MINING", "WOODCUTTING", "FARMING",
            "COMBAT", "EXPLORATION", "SAILING",
            "FISHING", "INVENTION", "SLAYER"
    };

    // Progress bar utility
    private String progressBar(double current, double max, int bars) {
        double percent = Math.max(0, Math.min(1, max == 0 ? 1 : current / max));
        int filled = (int) Math.round(bars * percent);
        String bar = "§a" + "█".repeat(filled) + "§7" + "░".repeat(bars - filled);
        return "§8[" + bar + "§8]";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        FileConfiguration data = IaddithisSkilling.getInstance().getData();
        FileConfiguration cfg = IaddithisSkilling.getInstance().getConfig();
        int maxLevel = cfg.getInt("max-level", 50);

        OfflinePlayer target;
        if (args.length > 0) {
            target = Bukkit.getOfflinePlayer(args[0]);
            if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
                sender.sendMessage("§cPlayer not found!");
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Only players can use this command without arguments.");
                return true;
            }
            target = (Player) sender;
        }

        String name = target.getName() != null ? target.getName() : target.getUniqueId().toString();
        sender.sendMessage("§6Skills of " + name + ":");
        TreeMap<Integer, Integer> xpTable = SkillManager.getXpTable();

        for (String skill : ALL_SKILLS) {
            String base = target.getUniqueId() + "." + skill;
            int lvl = data.getInt(base + ".level", 1);
            double xp = data.getDouble(base + ".xp", 0.0);
            int nextLevel = lvl + 1;
            int xpThisLevel = xpTable.getOrDefault(lvl, 0);
            int xpNextLevel = xpTable.getOrDefault(nextLevel, xpTable.lastEntry().getValue());
            int progress = (int) (xp - xpThisLevel);
            int needed = xpNextLevel - xpThisLevel;
            String bar = progressBar(progress, needed, 10);

            // Optioneel: extra uitleg voor invention
            if (skill.equals("INVENTION")) {
                sender.sendMessage("§e" + skill + ": §a" + lvl + "§7/§e" + maxLevel +
                        " " + bar + " §8(" + progress + "/" + needed + " XP) §7← Salvaging XP");
            } else {
                sender.sendMessage("§e" + skill + ": §a" + lvl + "§7/§e" + maxLevel +
                        " " + bar + " §8(" + progress + "/" + needed + " XP)");
            }
        }
        return true;
    }
}
