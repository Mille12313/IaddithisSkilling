// src/main/java/bloody/devmules/iaddithisSkilling/ToggleNotificationCommand.java
package bloody.devmules.iaddithisSkilling;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;

public class ToggleNotificationCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;
        FileConfiguration data = IaddithisSkilling.getInstance().getData();
        String path = p.getUniqueId() + ".settings.notifications";
        boolean current = data.getBoolean(path, true);
        data.set(path, !current);
        p.sendMessage("Notifications are now " + ((!current) ? "§aENABLED" : "§cDISABLED"));
        // ← save here too
        IaddithisSkilling.getInstance().saveData();
        return true;
    }
}
