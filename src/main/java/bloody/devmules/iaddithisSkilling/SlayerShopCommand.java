package bloody.devmules.iaddithisSkilling;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SlayerShopCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!p.hasPermission("iaddithisskilling.slayer.shop")) {
            p.sendMessage("§cYou don't have permission.");
            return true;
        }
        SlayerShopGUI.open(p, 1); // ← BESTAANDE methode
        return true;
    }
}
