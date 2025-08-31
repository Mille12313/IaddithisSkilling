package bloody.devmules.iaddithisSkilling;

import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class SlayerShopEditorCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Players only.");
            return true;
        }
        if (!p.hasPermission("iaddithisskilling.slayer.edit")) {
            p.sendMessage(ChatColor.RED + "Geen permissie.");
            return true;
        }
        SlayerShopEditorGUI.openEditor(p, 1);
        return true;
    }
}
