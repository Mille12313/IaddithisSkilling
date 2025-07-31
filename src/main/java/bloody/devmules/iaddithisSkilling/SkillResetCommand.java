package bloody.devmules.iaddithisSkilling;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Set;

public class SkillResetCommand implements CommandExecutor {
    private static final String[] ALL_SKILLS = {
            "MINING", "WOODCUTTING", "FARMING", "COMBAT",
            "EXPLORATION", "SAILING", "FISHING", "SLAYER", "SALVAGE"
    };

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("iaddithisskilling.resetskill")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage("§cUsage: /resetskill <skill>");
            sender.sendMessage("§7Valid skills: " + String.join(", ", ALL_SKILLS));
            return true;
        }

        String skill = args[0].toUpperCase();
        boolean valid = false;
        for (String s : ALL_SKILLS) {
            if (s.equals(skill)) {
                valid = true;
                break;
            }
        }
        if (!valid) {
            sender.sendMessage("§cInvalid skill. Use: " + String.join(", ", ALL_SKILLS));
            return true;
        }

        FileConfiguration data = IaddithisSkilling.getInstance().getData();
        Set<String> players = data.getKeys(false);
        int affected = 0;

        for (String uuidStr : players) {
            data.set(uuidStr + "." + skill + ".level", 1);
            data.set(uuidStr + "." + skill + ".xp", 0.0);
            affected++;
        }
        IaddithisSkilling.getInstance().saveData();

        sender.sendMessage("§aSkill §e" + skill + "§a has been reset for all players. (" + affected + " players)");
        return true;
    }
}
