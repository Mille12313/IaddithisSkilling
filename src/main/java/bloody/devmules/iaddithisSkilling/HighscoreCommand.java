package bloody.devmules.iaddithisSkilling;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

public class HighscoreCommand implements CommandExecutor {
    private static final String[] ALL_SKILLS = {
            "MINING", "WOODCUTTING", "FARMING",
            "COMBAT", "EXPLORATION", "SAILING",
            "FISHING", "SLAYER"
    };

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        FileConfiguration data = IaddithisSkilling.getInstance().getData();
        String skill = null;
        int page = 1;

        // /highscore [skill] [page]
        if (args.length > 0) {
            if (Arrays.asList(ALL_SKILLS).contains(args[0].toUpperCase())) {
                skill = args[0].toUpperCase();
                if (args.length > 1) {
                    try { page = Math.max(1, Integer.parseInt(args[1])); } catch (NumberFormatException ignore) {}
                }
            } else {
                try { page = Math.max(1, Integer.parseInt(args[0])); } catch (NumberFormatException ignore) {}
            }
        }

        // Build scores
        Map<String, Integer> scores = new HashMap<>();
        for (String key : data.getKeys(false)) {
            int sum = 0;
            if (skill == null) {
                // Total level over all skills
                for (String s : ALL_SKILLS)
                    sum += data.getInt(key + "." + s + ".level", 1);
            } else {
                sum = data.getInt(key + "." + skill + ".level", 1);
            }
            OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(key));
            scores.put(op.getName() != null ? op.getName() : key, sum);
        }

        // Sort
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(scores.entrySet());
        sorted.sort(Map.Entry.<String, Integer>comparingByValue().reversed());

        int perPage = 10;
        int maxPages = (int) Math.ceil(sorted.size() / (double) perPage);
        page = Math.min(page, maxPages);

        sender.sendMessage("§6⬆ Highscores " +
                (skill != null ? "for §e" + skill + "§6 " : "") +
                "§7(Page " + page + "/" + maxPages + "):");

        int start = (page - 1) * perPage;
        int end = Math.min(sorted.size(), start + perPage);

        for (int i = start; i < end; i++) {
            Map.Entry<String, Integer> entry = sorted.get(i);
            sender.sendMessage("§7#" + (i + 1) + " §e" + entry.getKey() + ": §a" + entry.getValue());
        }
        if (maxPages > 1) {
            sender.sendMessage("§8Type /highscore" +
                    (skill != null ? " " + skill.toLowerCase() : "") +
                    " " + (page + 1) + " for next page.");
        }
        return true;
    }
}
