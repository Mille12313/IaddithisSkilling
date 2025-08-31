package bloody.devmules.iaddithisSkilling;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class SlayerShopGUI implements Listener {

    private static final String TITLE = ChatColor.DARK_RED + "Slayer Shop";
    private static final int SIZE = 54;
    private static final int PREV_SLOT = 45;
    private static final int NEXT_SLOT = 53;

    private static final Map<UUID, Integer> currentPage = new HashMap<>();

    public static void open(Player p, int page) {
        page = Math.max(1, page);
        currentPage.put(p.getUniqueId(), page);

        // **Altijd** verse config lezen
        SlayerService.reloadFromDisk();

        Inventory inv = Bukkit.createInventory(p, SIZE, TITLE + " §8(p." + page + ")");

        // background
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta pm = pane.getItemMeta();
        pm.setDisplayName(" ");
        pane.setItemMeta(pm);
        for (int i = 0; i < SIZE; i++) inv.setItem(i, pane);

        inv.setItem(PREV_SLOT, makeItem(Material.ARROW, ChatColor.YELLOW + "Previous page"));
        inv.setItem(NEXT_SLOT, makeItem(Material.ARROW, ChatColor.YELLOW + "Next page"));

        List<Map<String,Object>> list = SlayerService.shop();
        final int perPage = 45;
        int start = (page - 1) * perPage;
        int end = Math.min(list.size(), start + perPage);

        for (int i = start, slot = 0; i < end && slot < 45; i++, slot++) {
            Map<String,Object> m = list.get(i);
            String key = String.valueOf(m.get("key"));
            int price = intOf(m.get("price"), 0);

            ItemStack icon = SlayerService.entryToItem(m);
            if (icon == null || icon.getType() == Material.AIR) icon = new ItemStack(Material.BARRIER);

            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName(ChatColor.GOLD + key);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Price: " + ChatColor.GOLD + price + " pts");
            lore.add("");
            lore.add(ChatColor.GREEN + "Click to buy");
            meta.setLore(lore);
            icon.setItemMeta(meta);

            inv.setItem(slot, icon);
        }

        p.openInventory(inv);
    }

    private static ItemStack makeItem(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(name);
        if (lore != null && lore.length > 0) im.setLore(Arrays.asList(lore));
        it.setItemMeta(im);
        return it;
    }

    private static boolean isShop(InventoryView view) {
        return view != null && view.getTitle() != null && view.getTitle().startsWith(TITLE);
    }

    private static int pageOf(Player p) {
        return currentPage.getOrDefault(p.getUniqueId(), 1);
    }

    private static int intOf(Object o, int def) {
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return def; }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!isShop(e.getView())) return;

        Player p = (Player) e.getWhoClicked();
        int raw = e.getRawSlot();

        // Block top inv edits
        if (raw < SIZE) {
            e.setCancelled(true);

            if (raw == PREV_SLOT) { open(p, Math.max(1, pageOf(p) - 1)); return; }
            if (raw == NEXT_SLOT) { open(p, pageOf(p) + 1); return; }

            // Buy flow
            if (raw >= 0 && raw < 45) {
                // Determine which entry
                List<Map<String,Object>> list = SlayerService.shop();
                int index = (pageOf(p) - 1) * 45 + raw;
                if (index < 0 || index >= list.size()) return;

                Map<String,Object> entry = list.get(index);
                int price = intOf(entry.get("price"), 0);

                if (!SlayerService.spendPoints(p.getUniqueId(), price)) {
                    p.sendMessage(ChatColor.RED + "Not enough Slayer points.");
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }

                // Give item
                ItemStack item = SlayerService.entryToItem(entry);
                HashMap<Integer, ItemStack> left = p.getInventory().addItem(item);
                if (!left.isEmpty()) { // inventory full -> drop
                    left.values().forEach(it -> p.getWorld().dropItemNaturally(p.getLocation(), it));
                }

                p.sendMessage(ChatColor.GREEN + "Purchased for " + price + " pts.");
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                // Re-open to reflect any dynamic changes
                open(p, pageOf(p));
            }
            return;
        }

        // Player inv shift-moves into top: block
        if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) e.setCancelled(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!isShop(e.getView())) return;
        for (int slot : e.getRawSlots()) {
            if (slot < SIZE) { e.setCancelled(true); return; }
        }
    }
}
