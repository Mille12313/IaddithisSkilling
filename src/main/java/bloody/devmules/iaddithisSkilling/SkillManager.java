package bloody.devmules.iaddithisSkilling;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.TreeMap;
import java.util.List;

public class SkillManager {
    public static final double XP_CAP = 200_000_000.0;
    private static final TreeMap<Integer, Integer> XP_TABLE = new TreeMap<>();

    static {
        FileConfiguration cfg = IaddithisSkilling.getInstance().getConfig();
        ConfigurationSection sec = cfg.getConfigurationSection("xp-table");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                int lvl = Integer.parseInt(key);
                int xpNeeded = sec.getInt(key);
                XP_TABLE.put(lvl, xpNeeded);
            }
        }
    }

    public static TreeMap<Integer, Integer> getXpTable() {
        return XP_TABLE;
    }

    public static void addXP(Player p, String skill, double xp) {
        FileConfiguration data = IaddithisSkilling.getInstance().getData();
        FileConfiguration cfg  = IaddithisSkilling.getInstance().getConfig();
        String base = p.getUniqueId() + "." + skill;

        // 1) Per-skill XP cap
        double currentTotalXp = data.getDouble(base + ".xp", 0.0);
        double totalXp = Math.min(currentTotalXp + xp, XP_CAP);
        data.set(base + ".xp", totalXp);

        // 2) huidig en volgend level lezen
        int currentLevel = data.getInt(base + ".level", 1);
        int nextLevel = currentLevel + 1;

        // 3) zolang we genoeg XP hebben, level up
        while (XP_TABLE.containsKey(nextLevel) && totalXp >= XP_TABLE.get(nextLevel)) {
            currentLevel = nextLevel;
            nextLevel++;

            p.sendMessage("§aYou have leveled up! §e" + skill + " §ais now level §e" + currentLevel);

            ConfigurationSection rewardsForSkill = cfg.getConfigurationSection("rewards." + skill);
            if (rewardsForSkill != null) {
                for (String cmd : rewardsForSkill.getStringList(String.valueOf(currentLevel))) {
                    cmd = cmd.replace("%player%", p.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                }
            }

            if (currentLevel == XP_TABLE.lastKey()) {
                Bukkit.broadcastMessage("§6" + p.getName() + " has just mastered " + skill + "!");
            }
        }

        data.set(base + ".level", currentLevel);
        IaddithisSkilling.getInstance().saveData();

        // Cementing als ALLE skills max zijn
        checkCementing(p);
    }

    private static void checkCementing(Player p) {
        FileConfiguration data = IaddithisSkilling.getInstance().getData();
        int maxLevel = XP_TABLE.lastKey();
        boolean allMax = true;
        for (String skill : HighscoreCommand.ALL_SKILLS) {
            if (data.getInt(p.getUniqueId() + "." + skill + ".level", 1) < maxLevel) {
                allMax = false;
                break;
            }
        }
        if (allMax) {
            List<String> cemented = data.getStringList("cemented");
            String uuid = p.getUniqueId().toString();
            if (!cemented.contains(uuid)) {
                cemented.add(uuid);
                data.set("cemented", cemented);
                IaddithisSkilling.getInstance().saveData();
                Bukkit.broadcastMessage("§e" + p.getName() + " has maxed all skills and is immortalized in the highscores!");
            }
        }
    }

    public static int getPlayerTotalLevel(String uuid) {
        FileConfiguration data = IaddithisSkilling.getInstance().getData();
        int sum = 0;
        for (String skill : HighscoreCommand.ALL_SKILLS) {
            sum += data.getInt(uuid + "." + skill + ".level", 1);
        }
        return sum;
    }

    // --- TOEGEVOEGD: Geeft het huidige level van een speler voor een skill ---
    public static int getLevel(Player player, String skill) {
        FileConfiguration data = IaddithisSkilling.getInstance().getData();
        String base = player.getUniqueId() + "." + skill;
        return data.getInt(base + ".level", 1);
    }
}
