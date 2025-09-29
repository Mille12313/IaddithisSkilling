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

    /** Static shim zodat je vanuit commands statisch kunt openen. */
    public static void open(Player p, int page) {
        new SlayerShopGUI().openFor(p); // we pagineren niet; 'page' wordt genegeerd
    }

    /** Eigenlijke open logica (instantie-methode). */
    public void openFor(Player p) {
        Inventory inv = Bukkit.createInventory(new SlayerShopHolder(), SIZE, TITLE);

        // background
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta pm = pane.getItemMeta(); pm.setDisplayName(" "); pane.setItemMeta(pm);
        for (int i = 0; i < SIZE; i++) inv.setItem(i, pane);

        // place purchasable items by slot 0..44
        Map<Integer, Map<String,Object>> bySlot = SlayerService.shopBySlot();
        for (int slot = 0; slot < 45; slot++) {
            Map<String,Object> m = bySlot.get(slot);
            if (m == null) continue;

            int price = toInt(m.get("price"), 0);
            ItemStack base = SlayerService.entryToItem(m);
            if (base == null || base.getType() == Material.AIR) continue;

            ItemStack icon = base.clone();
            ItemMeta meta = icon.getItemMeta();
            if (meta == null) meta = Bukkit.getItemFactory().getItemMeta(icon.getType());

            // Keep real name if present, else pretty material
            if (!meta.hasDisplayName()) {
                meta.setDisplayName(ChatColor.YELLOW + prettyMaterial(icon.getType()));
            }

            // Append price line
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GOLD + "Cost: " + price + " Slayer Points");
            meta.setLore(lore);
            icon.setItemMeta(meta);

            inv.setItem(slot, icon);
        }

        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof SlayerShopHolder)) return;
        int raw = e.getRawSlot();
        if (raw >= SIZE) return; // bottom inventory allowed
        e.setCancelled(true);

        if (raw < 0 || raw >= 45) return; // ignore border

        Player p = (Player) e.getWhoClicked();
        Map<String,Object> item = SlayerService.findShopItemBySlot(raw);
        if (item == null) return;

        int price = toInt(item.get("price"), 0);
        int points = SlayerService.getPoints(p.getUniqueId());
        if (points < price) {
            p.sendMessage(ChatColor.RED + "Not enough Slayer points. You need " + price + ", you have " + points + ".");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
            return;
        }

        ItemStack give = SlayerService.entryToItem(item);
        if (give == null || give.getType() == Material.AIR) {
            p.sendMessage(ChatColor.RED + "This item is currently unavailable.");
            return;
        }

        // Try to add; drop overflow
        Map<Integer, ItemStack> over = p.getInventory().addItem(give);
        over.values().forEach(st -> p.getWorld().dropItemNaturally(p.getLocation(), st));

        SlayerService.spendPoints(p.getUniqueId(), price);
        p.sendMessage(ChatColor.GREEN + "Purchased for " + ChatColor.GOLD + price + ChatColor.GREEN + " Slayer points.");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getInventory().getHolder() instanceof SlayerShopHolder)) return;
        // Block dragging into shop
        for (int slot : e.getRawSlots()) {
            if (slot < SIZE) { e.setCancelled(true); return; }
        }
    }

    private static int toInt(Object o, int def) {
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return def; }
    }

    private static String prettyMaterial(Material m) {
        String n = m.name().toLowerCase(Locale.ROOT).replace('_',' ');
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    public static class SlayerShopHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
