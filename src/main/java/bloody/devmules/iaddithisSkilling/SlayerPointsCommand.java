package bloody.devmules.iaddithisSkilling;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SlayerPointsCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (!(s instanceof Player p)) { s.sendMessage("Players only."); return true; }
        if (!p.hasPermission("iaddithisskilling.slayer.points")) {
            p.sendMessage("§cJe hebt geen toegang om Slayer points te bekijken.");
            return true;
        }
        var st = SlayerService.getState(p.getUniqueId());
        p.sendMessage("§aSlayer points: §e" + st.points + " §7| Streak: §e" + st.streak);
        return true;
    }
}
