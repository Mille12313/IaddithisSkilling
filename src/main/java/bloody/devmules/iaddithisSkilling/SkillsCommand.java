// src/main/java/bloody/devmules/iaddithisSkilling/SkillsCommand.java
package bloody.devmules.iaddithisSkilling;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class SkillsCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        Player p = (Player) sender;
        FileConfiguration data = IaddithisSkilling.getInstance().getData();
        int maxLevel = IaddithisSkilling.getInstance().getConfig().getInt("max-level", 50);

        p.sendMessage("§6Your skills:");
        for (String skill : new String[]{
                "MINING", "WOODCUTTING", "FARMING",
                "COMBAT", "EXPLORATION", "SAILING",
                "FISHING", "SLAYER"
        }) {
            int lvl = data.getInt(p.getUniqueId() + "." + skill + ".level", 1);
            p.sendMessage("§e" + skill + ": §a" + lvl + "§7/§e" + maxLevel);
        }
        return true;
    }
}
