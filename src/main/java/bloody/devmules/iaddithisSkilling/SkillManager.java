// src/main/java/bloody/devmules/iaddithisSkilling/SkillManager.java
package bloody.devmules.iaddithisSkilling;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.TreeMap;
import java.util.Map;

public class SkillManager {
    /** TreeMap met level → benodigde totale XP (TreeMap om lastKey() te kunnen gebruiken) */
    private static final TreeMap<Integer, Integer> XP_TABLE = new TreeMap<>();

    static {
        // bij klasseload de table vullen
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

    /**
     * Geef spelers xp voor een skill, sla op, check level‑up, voer rewards uit.
     */
    public static void addXP(Player p, String skill, double xp) {
        FileConfiguration data = IaddithisSkilling.getInstance().getData();
        FileConfiguration cfg  = IaddithisSkilling.getInstance().getConfig();
        String base = p.getUniqueId() + "." + skill;

        // 1) totaal xp bijwerken
        double totalXp = data.getDouble(base + ".xp", 0.0) + xp;
        data.set(base + ".xp", totalXp);

        // 2) huidig en volgend level lezen
        int currentLevel = data.getInt(base + ".level", 1);
        int nextLevel = currentLevel + 1;

        // 3) zolang we genoeg XP hebben, level up
        while (XP_TABLE.containsKey(nextLevel) && totalXp >= XP_TABLE.get(nextLevel)) {
            currentLevel = nextLevel;
            nextLevel++;

            // geef speler een kort bericht
            p.sendMessage("§aYou have leveled up! §e" + skill + " §ais now level §e" + currentLevel);

            // 4) voer eventueel ingestelde rewards uit
            ConfigurationSection rewardsForSkill = cfg.getConfigurationSection("rewards." + skill);
            if (rewardsForSkill != null) {
                // commands voor **exact** dit level
                for (String cmd : rewardsForSkill.getStringList(String.valueOf(currentLevel))) {
                    // %player% placeholder vervangen
                    cmd = cmd.replace("%player%", p.getName());
                    // voer uit als console
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                }
            }

            // 5) als dit het allerhoogste level is, broadcast mastery
            if (currentLevel == XP_TABLE.lastKey()) {
                Bukkit.broadcastMessage("§6" + p.getName() + " has just mastered " + skill + "!");
            }
        }

        // 6) sla het nieuwe level op
        data.set(base + ".level", currentLevel);

        // 7) meteen opslaan (kan asynch of op shutdown, naar eigen voorkeur)
        IaddithisSkilling.getInstance().saveData();
    }
}