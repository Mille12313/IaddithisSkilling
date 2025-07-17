// src/main/java/bloody/devmules/iaddithisSkilling/ReloadCommand.java
package bloody.devmules.iaddithisSkilling;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ReloadCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("iaddithisskilling.reload")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        IaddithisSkilling.getInstance().reloadConfig();
        sender.sendMessage("§aConfiguration reloaded.");
        return true;
    }
}
