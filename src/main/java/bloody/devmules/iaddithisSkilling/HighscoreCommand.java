package bloody.devmules.iaddithisSkilling;

import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

public class HighscoreCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        FileConfiguration data = IaddithisSkilling.getInstance().getData();
        Map<String, Integer> totals = new HashMap<>();

        for (String key : data.getKeys(false)) {
            int sum = 0;
            for (String skill : List.of("COMBAT","MINING","WOODCUTTING","FARMING","EXPLORATION","SAILING","FISHING","SLAYER")) {
                sum += data.getInt(key + "." + skill + ".level", 1);
            }
            OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(key));
            totals.put(op.getName() != null ? op.getName() : key, sum);
        }

        sender.sendMessage("§6⬆ Highscores (Top 10):");
        totals.entrySet().stream()
                .sorted(Map.Entry.<String,Integer>comparingByValue().reversed())
                .limit(10)
                .forEach(e -> sender.sendMessage("§e" + e.getKey() + ": §a" + e.getValue()));
        return true;
    }
}
