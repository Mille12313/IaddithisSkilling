package bloody.devmules.iaddithisSkilling;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class SkillsGuiCommand implements CommandExecutor, Listener {
    public static final String[] ALL_SKILLS = {
            "MINING", "WOODCUTTING", "FARMING",
            "COMBAT", "EXPLORATION", "SAILING",
            "FISHING", "SLAYER", "SALVAGE"
    };

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        openMainGui(p);
        return true;
    }

    public void openMainGui(Player p) {
        Inventory inv = Bukkit.createInventory(null, 9, "§2Your Skills");
        FileConfiguration data = IaddithisSkilling.getInstance().getData();
        TreeMap<Integer, Integer> xpTable = SkillManager.getXpTable();
        int maxLevel = IaddithisSkilling.getInstance().getConfig().getInt("max-level", 50);

        for (int i = 0; i < ALL_SKILLS.length; i++) {
            String skill = ALL_SKILLS[i];
            String base = p.getUniqueId() + "." + skill;
            int lvl = data.getInt(base + ".level", 1);
            double xp = data.getDouble(base + ".xp", 0.0);
            int nextLevel = lvl + 1;
            int xpThisLevel = xpTable.getOrDefault(lvl, 0);
            int xpNextLevel = xpTable.getOrDefault(nextLevel, xpTable.lastEntry().getValue());
            int progress = (int) (xp - xpThisLevel);
            int needed = xpNextLevel - xpThisLevel;

            String bar = progressBar(progress, needed, 18);

            ItemStack skillItem = new ItemStack(getIcon(skill));
            ItemMeta meta = skillItem.getItemMeta();
            meta.setDisplayName("§a" + capitalize(skill) + " §7(" + lvl + "§8/§7" + maxLevel + ")");
            meta.setLore(List.of(
                    "§7XP: §e" + progress + "§8/§e" + needed,
                    bar,
                    "",
                    skillDesc(skill),
                    "",
                    "§7§oClick for details"
            ));
            skillItem.setItemMeta(meta);

            inv.setItem(i, skillItem);
        }
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.15f);
    }

    public void openDetailGui(Player p, String skill) {
        FileConfiguration data = IaddithisSkilling.getInstance().getData();
        FileConfiguration cfg = IaddithisSkilling.getInstance().getConfig();
        TreeMap<Integer, Integer> xpTable = SkillManager.getXpTable();
        int maxLevel = cfg.getInt("max-level", 50);

        String base = p.getUniqueId() + "." + skill;
        int lvl = data.getInt(base + ".level", 1);
        double xp = data.getDouble(base + ".xp", 0.0);
        int nextLevel = lvl + 1;
        int xpThisLevel = xpTable.getOrDefault(lvl, 0);
        int xpNextLevel = xpTable.getOrDefault(nextLevel, xpTable.lastEntry().getValue());
        int progress = (int) (xp - xpThisLevel);
        int needed = xpNextLevel - xpThisLevel;
        String bar = progressBar(progress, needed, 22);

        String rewardText = "§cNo more rewards.";
        ConfigurationSection rewards = cfg.getConfigurationSection("rewards." + skill);
        if (rewards != null) {
            int showLevel = -1;
            StringBuilder nextReward = new StringBuilder();
            for (String key : rewards.getKeys(false)) {
                int level;
                try { level = Integer.parseInt(key); } catch (NumberFormatException ignore) { continue; }
                if (level > lvl) {
                    showLevel = level;
                    for (String r : rewards.getStringList(key)) {
                        if (r.toLowerCase().contains("iagive") || r.toLowerCase().contains("tell") || r.toLowerCase().contains("give")) {
                            nextReward.append("§7").append(r.replace("%player%", p.getName())).append("\n");
                        }
                    }
                    break;
                }
            }
            if (showLevel > 0) {
                rewardText = "§eNext reward at level " + showLevel + ":\n" + nextReward.toString().trim();
            }
        }

        int rank = getSkillRank(skill, p.getUniqueId());
        String rankString = (rank > 0 ? "§b#" + rank : "§7Not ranked");

        List<String> topPlayers = getTop3(skill);

        Inventory inv = Bukkit.createInventory(null, 27, "§9" + capitalize(skill) + " Details");

        ItemStack skillItem = new ItemStack(getIcon(skill));
        ItemMeta meta = skillItem.getItemMeta();
        meta.setDisplayName("§b" + capitalize(skill) + " §7(" + lvl + "/" + maxLevel + ")");
        meta.setLore(List.of(
                "§7XP: §e" + progress + "§8/§e" + needed,
                bar,
                "",
                rewardText
        ));
        skillItem.setItemMeta(meta);
        inv.setItem(13, skillItem);

        ItemStack rankItem = new ItemStack(Material.NAME_TAG);
        ItemMeta rankMeta = rankItem.getItemMeta();
        rankMeta.setDisplayName("§aYour Ranking");
        rankMeta.setLore(List.of(rankString));
        rankItem.setItemMeta(rankMeta);
        inv.setItem(15, rankItem);

        ItemStack topItem = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta topMeta = topItem.getItemMeta();
        topMeta.setDisplayName("§eTop 3 in " + capitalize(skill));
        List<String> lore = new ArrayList<>();
        int place = 1;
        for (String s : topPlayers) {
            lore.add("§6#" + (place++) + "§7: §e" + s);
        }
        if (lore.isEmpty()) lore.add("§7No data yet.");
        topMeta.setLore(lore);
        topItem.setItemMeta(topMeta);
        inv.setItem(11, topItem);

        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName("§cBack");
        back.setItemMeta(bm);
        inv.setItem(26, back);

        ItemStack hs = new ItemStack(Material.BOOK);
        ItemMeta hsm = hs.getItemMeta();
        hsm.setDisplayName("§eSee global highscore");
        hsm.setLore(List.of("§7Type: §b/highscore " + skill.toLowerCase()));
        hs.setItemMeta(hsm);
        inv.setItem(17, hs);

        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.1f);
    }

    @EventHandler
    public void onInvClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = e.getView().getTitle();
        if (title.equals("§2Your Skills")) {
            e.setCancelled(true);
            int slot = e.getRawSlot();
            if (slot >= 0 && slot < ALL_SKILLS.length) {
                p.closeInventory();
                Bukkit.getScheduler().runTaskLater(IaddithisSkilling.getInstance(), () -> openDetailGui(p, ALL_SKILLS[slot]), 2);
            }
        }
        if (title.endsWith("Details")) {
            e.setCancelled(true);
            int slot = e.getRawSlot();
            if (slot == 26) {
                p.closeInventory();
                Bukkit.getScheduler().runTaskLater(IaddithisSkilling.getInstance(), () -> openMainGui(p), 2);
            }
            if (slot == 17) {
                p.closeInventory();
                p.sendMessage("§7Type §b/highscore " + title.replace(" Details", "").trim().toLowerCase() + "§7 in chat!");
            }
        }
    }

    private List<String> getTop3(String skill) {
        FileConfiguration data = IaddithisSkilling.getInstance().getData();
        Map<String, Integer> scores = new HashMap<>();
        for (String key : data.getKeys(false)) {
            int lvl = data.getInt(key + "." + skill + ".level", 1);
            scores.put(key, lvl);
        }
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(scores.entrySet());
        sorted.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        List<String> res = new ArrayList<>();
        int shown = 0;
        for (Map.Entry<String, Integer> e : sorted) {
            if (++shown > 3) break;
            String name = Bukkit.getOfflinePlayer(UUID.fromString(e.getKey())).getName();
            res.add((name != null ? name : "Unknown") + " §7(" + e.getValue() + ")");
        }
        return res;
    }

    private int getSkillRank(String skill, UUID uuid) {
        FileConfiguration data = IaddithisSkilling.getInstance().getData();
        Map<String, Integer> scores = new HashMap<>();
        for (String key : data.getKeys(false)) {
            int lvl = data.getInt(key + "." + skill + ".level", 1);
            scores.put(key, lvl);
        }
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(scores.entrySet());
        sorted.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        int rank = 1;
        for (Map.Entry<String, Integer> e : sorted) {
            if (e.getKey().equals(uuid.toString())) return rank;
            rank++;
        }
        return -1;
    }

    private String progressBar(int current, int max, int bars) {
        if (max == 0) return "";
        int full = (int) ((double) current / max * bars);
        StringBuilder bar = new StringBuilder("§8[");
        for (int i = 0; i < bars; i++) {
            bar.append(i < full ? "§a|" : "§7|");
        }
        bar.append("§8]");
        return bar.toString();
    }

    private Material getIcon(String skill) {
        switch (skill) {
            case "MINING": return Material.DIAMOND_PICKAXE;
            case "WOODCUTTING": return Material.DIAMOND_AXE;
            case "FARMING": return Material.WHEAT;
            case "COMBAT": return Material.IRON_SWORD;
            case "EXPLORATION": return Material.COMPASS;
            case "SAILING": return Material.OAK_BOAT;
            case "FISHING": return Material.FISHING_ROD;
            case "SLAYER": return Material.ZOMBIE_HEAD;
            case "SALVAGE": return Material.CAULDRON;
            default: return Material.BOOK;
        }
    }

    private String capitalize(String str) {
        return str.substring(0,1).toUpperCase() + str.substring(1).toLowerCase();
    }

    private String skillDesc(String skill) {
        switch (skill) {
            case "MINING": return "Break ores & stone to gain XP.";
            case "WOODCUTTING": return "Chop trees for Woodcutting XP.";
            case "FARMING": return "Harvest fully-grown crops for XP.";
            case "COMBAT": return "Deal damage to mobs (not players) for XP.";
            case "EXPLORATION": return "Explore the world on foot for XP.";
            case "SAILING": return "Sail with a boat for Sailing XP.";
            case "FISHING": return "Catch fish for Fishing XP.";
            case "SLAYER": return "Defeat mobs for Slayer XP.";
            case "SALVAGE": return "Salvage items at lava cauldrons for XP.";
            default: return "";
        }
    }
}
