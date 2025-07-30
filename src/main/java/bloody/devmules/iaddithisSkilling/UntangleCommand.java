package bloody.devmules.iaddithisSkilling;

import org.apache.commons.lang3.StringUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class UntangleCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            sender.sendMessage("Usage: /untangle <untangleKey>");
            return false;
        }
        if (sender instanceof Player p) {
            if (handleValidCommand(args[0], p)) {
                return true;
            } else {
                sender.sendMessage("Your line is currently not tangled!");
                return false;
            }
        }
        sender.sendMessage("This command can only be executed by a player");
        return false;
        
    }

    private static boolean handleValidCommand(String givenCaptcha, Player p) {
        if (PlayerSessionDataService.playerHasFishingCaptcha(p)) {
            String captcha = PlayerSessionDataService.getCurrentFishingCaptcha(p);
            if (StringUtils.equalsIgnoreCase(captcha, givenCaptcha)) {
                PlayerSessionDataService.clearFishingCaptcha(p);
                p.sendMessage("Your rod has been untangled!");
                return true;
            } else {
                p.sendMessage("You fiddle with the wire, but fail to untangle it (Wrong untangle code! Code is " + captcha);
                return true;
            }
        }
        return false;
    }
}
