package bloody.devmules.iaddithisSkilling;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

public class HighscoreCommand implements CommandExecutor {
    public static final String[] ALL_SKILLS = {
            "MINING", "WOODCUTTING", "FARMING",
            "COMBAT", "EXPLORATION", "SAILING",
            "FISHING","SALVAGE", "SLAYER"
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

        Set<String> allUuids = data.getKeys(false);
        List<String> cemented = data.getStringList("cemented");
        List<String> cementedOrdered = new ArrayList<>();
        for (String uuid : cemented) {
            if (allUuids.contains(uuid)) cementedOrdered.add(uuid);
        }

        // 1. Eerst cemented spelers, dan niet-cemented
        List<Map.Entry<String, Integer>> cementedList = new ArrayList<>();
        List<Map.Entry<String, Integer>> normalList = new ArrayList<>();
        for (String uuid : allUuids) {
            int score;
            if (skill == null) {
                score = SkillManager.getPlayerTotalLevel(uuid);
            } else {
                score = data.getInt(uuid + "." + skill + ".level", 1);
            }
            if (cemented.contains(uuid)) {
                cementedList.add(Map.entry(uuid, score));
            } else {
                normalList.add(Map.entry(uuid, score));
            }
        }

        // Sorteer cemented exact in cemented volgorde (eerste maxer blijft eerste!)
        List<Map.Entry<String, Integer>> cementedSorted = new ArrayList<>();
        for (String uuid : cementedOrdered) {
            cementedList.stream().filter(e -> e.getKey().equals(uuid)).findFirst().ifPresent(cementedSorted::add);
        }
        // Normale spelers: sorteer op total lvl (xp is niet relevant meer!)
        normalList.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        // Samenvoegen
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>();
        sorted.addAll(cementedSorted);
        sorted.addAll(normalList);

        int perPage = 10;
        int maxPages = (int) Math.ceil(sorted.size() / (double) perPage);
        page = Math.min(page, maxPages == 0 ? 1 : maxPages);

        sender.sendMessage("§6⬆ Highscores " +
                (skill != null ? "for §e" + skill + "§6 " : "") +
                "§7(Page " + page + "/" + maxPages + "):");

        int start = (page - 1) * perPage;
        int end = Math.min(sorted.size(), start + perPage);

        for (int i = start; i < end; i++) {
            Map.Entry<String, Integer> entry = sorted.get(i);
            String uuid = entry.getKey();
            OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
            String name = (op.getName() != null ? op.getName() : uuid);

            sender.sendMessage("§7#" + (i + 1) + " §e" + name + ": §a" + entry.getValue());
        }
        if (maxPages > 1) {
            sender.sendMessage("§8Type /highscore" +
                    (skill != null ? " " + skill.toLowerCase() : "") +
                    " " + (page + 1) + " for next page.");
        }
        return true;
    }
}
