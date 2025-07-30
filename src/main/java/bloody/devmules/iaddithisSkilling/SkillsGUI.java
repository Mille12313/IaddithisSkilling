package bloody.devmules.iaddithisSkilling;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.NumberFormat;
import java.util.*;

public class SkillsGUI implements Listener {
    public static final String[] ALL_SKILLS = {
            "MINING", "WOODCUTTING", "FARMING",
            "COMBAT", "EXPLORATION", "SAILING",
            "FISHING", "INVENTION", "SLAYER"
    };

    private static final NumberFormat nf = NumberFormat.getInstance();

    public static void open(Player player, OfflinePlayer target) {
        Inventory inv = Bukkit.createInventory(null, 9 * 3, "Skills of " + target.getName());
        FileConfiguration data = IaddithisSkilling.getInstance().getData();
        TreeMap<Integer, Integer> xpTable = SkillManager.getXpTable();
        int maxLevel = IaddithisSkilling.getInstance().getConfig().getInt("max-level", 50);

        for (int i = 0; i < ALL_SKILLS.length; i++) {
            String skill = ALL_SKILLS[i];
            String base = target.getUniqueId() + "." + skill;
            int lvl = data.getInt(base + ".level", 1);
            double xp = data.getDouble(base + ".xp", 0.0);
            int nextLevel = lvl + 1;
            int xpThisLevel = xpTable.getOrDefault(lvl, 0);
            int xpNextLevel = xpTable.getOrDefault(nextLevel, xpTable.lastEntry().getValue());
            int progress = (int) (xp - xpThisLevel);
            int needed = xpNextLevel - xpThisLevel;

            Material mat = getIcon(skill);
            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            List<String> lore = new ArrayList<>();
            lore.add("§7Level: §a" + lvl + "§7/§e" + maxLevel);
            lore.add("§7XP: §b" + nf.format((long)xp) + "§7/§b" + nf.format(SkillManager.XP_CAP));
            lore.add("§7This level: §e" + progress + "§8/§e" + needed);
            if (skill.equals("INVENTION")) {
                lore.add("§7Salvage items at lava cauldrons!");
            }
            lore.add("");
            lore.add("§8[Click for details]");
            meta.setDisplayName("§e" + capitalize(skill));
            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(10 + i, item);
        }
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
    }

    // --------- SKILL DETAIL-GUI ---------
    public static void openSkillDetail(Player player, String skill) {
        FileConfiguration data = IaddithisSkilling.getInstance().getData();
        TreeMap<Integer, Integer> xpTable = SkillManager.getXpTable();
        int maxLevel = IaddithisSkilling.getInstance().getConfig().getInt("max-level", 50);

        String base = player.getUniqueId() + "." + skill;
        int lvl = data.getInt(base + ".level", 1);
        double xp = data.getDouble(base + ".xp", 0.0);
        int nextLevel = lvl + 1;
        int xpThisLevel = xpTable.getOrDefault(lvl, 0);
        int xpNextLevel = xpTable.getOrDefault(nextLevel, xpTable.lastEntry().getValue());
        int progress = (int) (xp - xpThisLevel);
        int needed = xpNextLevel - xpThisLevel;
        String bar = progressBar(progress, needed, 20);

        Inventory inv = Bukkit.createInventory(null, 9*3, "§2" + capitalize(skill) + " Details");

        // Skill info item
        ItemStack info = new ItemStack(getIcon(skill));
        ItemMeta infoMeta = info.getItemMeta();
        List<String> detailLore = new ArrayList<>();
        detailLore.add("§7Level: §a" + lvl + "§7/§e" + maxLevel);
        detailLore.add("§7Total XP: §b" + nf.format((long)xp) + "§7/§b" + nf.format(SkillManager.XP_CAP));
        detailLore.add("§7This level: §e" + progress + "§8/§e" + needed);
        detailLore.add(bar);
        if (skill.equals("INVENTION")) {
            detailLore.add("§7Level up by salvaging items!");
            detailLore.add("§7Higher level = better salvage rewards.");
        }
        infoMeta.setDisplayName("§e" + capitalize(skill) + " Progress");
        infoMeta.setLore(detailLore);
        info.setItemMeta(infoMeta);
        inv.setItem(13, info);

        // Reward info (optioneel, dummy)
        ItemStack reward = new ItemStack(Material.CHEST);
        ItemMeta rewardMeta = reward.getItemMeta();
        rewardMeta.setDisplayName("§6Next Reward");
        rewardMeta.setLore(List.of("§7Next reward at: §e" + (lvl+1)));
        reward.setItemMeta(rewardMeta);
        inv.setItem(15, reward);

        // Back button
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName("§cBack");
        back.setItemMeta(backMeta);
        inv.setItem(26, back);

        player.openInventory(inv);
    }

    // --------- CLICK HANDLING: ----------------
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = e.getView().getTitle();
        if (title.startsWith("Skills of ")) {
            e.setCancelled(true);
            int slot = e.getRawSlot();
            if (slot >= 10 && slot < 10 + ALL_SKILLS.length) {
                String skill = ALL_SKILLS[slot - 10];
                openSkillDetail(p, skill);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.5f);
            }
        } else if (title.endsWith("Details")) {
            e.setCancelled(true);
            if (e.getRawSlot() == 26) {
                open(p, p); // Back to main GUI
            }
        }
    }

    private static Material getIcon(String skill) {
        switch (skill) {
            case "MINING": return Material.IRON_PICKAXE;
            case "WOODCUTTING": return Material.IRON_AXE;
            case "FARMING": return Material.WHEAT;
            case "COMBAT": return Material.IRON_SWORD;
            case "EXPLORATION": return Material.COMPASS;
            case "SAILING": return Material.OAK_BOAT;
            case "FISHING": return Material.FISHING_ROD;
            case "SLAYER": return Material.BONE;
            case "INVENTION": return Material.ANVIL; // <-- ANVIL icon!
            default: return Material.BOOK;
        }
    }

    private static String capitalize(String str) {
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    // --------- PROGRESS BAR ---------
    private static String progressBar(double current, double max, int bars) {
        double percent = Math.max(0, Math.min(1, max == 0 ? 1 : current / max));
        int filled = (int)Math.round(bars * percent);
        String bar = "§a" + "█".repeat(filled) + "§7" + "░".repeat(bars - filled);
        return "§8[" + bar + "§8]";
    }
}
