package bloody.devmules.iaddithisSkilling;

import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class SlayerShopEditorGUI implements Listener {

    private static final String TITLE = ChatColor.DARK_RED + "Slayer Shop Editor";
    private static final int SIZE = 54;

    private static final int PREV_SLOT = 45;
    private static final int NEXT_SLOT = 53;
    private static final int DROP_SLOT = 49; // drop here to create

    private static final Map<UUID, Integer> currentPage = new HashMap<>();
    private static final Map<UUID, PendingCreate> pendingCreate = new HashMap<>();
    private static final Map<UUID, PendingEdit> pendingEdit = new HashMap<>();

    private enum EditField { PRICE, AMOUNT }

    private static class PendingCreate {
        final ItemStack base;
        String key;
        Integer price;
        PendingCreate(ItemStack base) { this.base = base; }
    }

    private static class PendingEdit {
        final String key;
        final EditField field;
        PendingEdit(String key, EditField field) { this.key = key; this.field = field; }
    }

    // -------- Public: open GUI --------
    public static void openEditor(Player p, int page) {
        page = Math.max(1, page);
        currentPage.put(p.getUniqueId(), page);

        Inventory inv = Bukkit.createInventory(new SlayerShopEditorHolder(), SIZE, TITLE + " §8(p." + page + ")");

        // background
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta pm = pane.getItemMeta();
        pm.setDisplayName(" ");
        pane.setItemMeta(pm);
        for (int i = 0; i < SIZE; i++) inv.setItem(i, pane);

        inv.setItem(PREV_SLOT, makeItem(Material.ARROW, ChatColor.YELLOW + "Previous page"));
        inv.setItem(NEXT_SLOT, makeItem(Material.ARROW, ChatColor.YELLOW + "Next page"));

        inv.setItem(DROP_SLOT, makeItem(
                Material.HOPPER,
                ChatColor.GREEN + "Drop an item here",
                ChatColor.GRAY + "→ Creates a new shop entry",
                ChatColor.DARK_GRAY + "You will set key & price via chat"
        ));

        // list entries (0..44 content)
        List<Map<String,Object>> list = SlayerService.shop();
        final int perPage = 45;
        int start = (page - 1) * perPage;
        int end = Math.min(list.size(), start + perPage);

        for (int i = start, slot = 0; i < end && slot < 45; i++, slot++) {
            inv.setItem(slot, toIcon(list.get(i)));
        }

        p.openInventory(inv);
    }

    // -------- Helpers --------
    private static ItemStack toIcon(Map<String,Object> m) {
        String key = String.valueOf(m.get("key"));
        int price = toInt(m.get("price"), 0);
        int amount = toInt(m.get("amount"), 1);

        // Gebruik de service voor correcte reconstructie (itemsadder/serialized/vanilla)
        ItemStack icon = SlayerService.entryToItem(m);
        if (icon == null || icon.getType() == Material.AIR) icon = new ItemStack(Material.BARRIER);
        icon.setAmount(Math.max(1, amount));

        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + key);

        String type = String.valueOf(m.get("type")).toLowerCase(Locale.ROOT);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "type: " + type);
        if ("itemsadder".equals(type)) lore.add(ChatColor.GRAY + "id: " + String.valueOf(m.get("id")));
        if ("vanilla".equals(type))    lore.add(ChatColor.GRAY + "material: " + String.valueOf(m.get("material")));
        if (m.containsKey("commands")) lore.add(ChatColor.GRAY + "commands: " + ChatColor.DARK_GRAY + "(list)");
        if ("serialized".equals(type)) lore.add(ChatColor.DARK_GRAY + "(custom meta preserved)");
        lore.add(ChatColor.GOLD + "price: " + price + " pts   " + ChatColor.GRAY + "amount: " + amount);
        lore.add("");
        lore.add(ChatColor.GREEN + "Left-click: edit price");
        lore.add(ChatColor.AQUA + "Shift-left: edit amount");
        lore.add(ChatColor.RED + "Right-click: delete");
        meta.setLore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private static ItemStack makeItem(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(name);
        if (lore != null && lore.length > 0) im.setLore(Arrays.asList(lore));
        it.setItemMeta(im);
        return it;
    }

    private static int toInt(Object o, int def) {
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return def; }
    }

    private static boolean isEditor(Inventory inv) {
        return inv != null && inv.getHolder() instanceof SlayerShopEditorHolder;
    }

    private static int pageOf(Player p) {
        return currentPage.getOrDefault(p.getUniqueId(), 1);
    }

    // -------- Click handling --------
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!isEditor(e.getInventory())) return;

        final Player p = (Player) e.getWhoClicked();
        final int raw = e.getRawSlot();

        // Cancel ALL interactions into the top inventory by default
        if (raw < SIZE) {
            e.setCancelled(true);

            if (raw == PREV_SLOT) { openEditor(p, Math.max(1, pageOf(p) - 1)); return; }
            if (raw == NEXT_SLOT) { openEditor(p, pageOf(p) + 1); return; }

            // Create via DROP_SLOT (cursor / number key / offhand handled here)
            if (raw == DROP_SLOT) {
                ItemStack source = null;

                // 1) Cursor
                ItemStack cursor = e.getCursor();
                if (cursor != null && !cursor.getType().isAir()) {
                    source = cursor.clone();
                    p.setItemOnCursor(null);
                }

                // 2) Number-key hotbar swap
                if (source == null && e.getClick().isKeyboardClick() && e.getHotbarButton() >= 0) {
                    ItemStack hot = p.getInventory().getItem(e.getHotbarButton());
                    if (hot != null && !hot.getType().isAir()) {
                        source = hot.clone();
                        p.getInventory().setItem(e.getHotbarButton(), null);
                    }
                }

                // 3) Offhand swap
                if (source == null && e.getClick() == ClickType.SWAP_OFFHAND) {
                    ItemStack off = p.getInventory().getItemInOffHand();
                    if (off != null && !off.getType().isAir()) {
                        source = off.clone();
                        p.getInventory().setItemInOffHand(null);
                    }
                }

                if (source == null || source.getType().isAir()) {
                    p.sendMessage(ChatColor.GRAY + "Drop an item from your cursor (or number-key/offhand) onto the hopper.");
                    return;
                }

                startCreateFlow(p, source);
                return;
            }

            // Existing entries 0..44
            if (raw >= 0 && raw < 45) {
                ItemStack clicked = e.getCurrentItem();
                if (clicked == null || clicked.getType().isAir()) return;

                String key = ChatColor.stripColor(
                        Optional.ofNullable(clicked.getItemMeta()).map(ItemMeta::getDisplayName).orElse("")
                );
                if (key == null || key.isEmpty()) return;

                if (e.isRightClick()) {
                    boolean ok = SlayerService.deleteShopItemByKey(key);
                    SlayerService.saveSlayerFile(); // save + reload
                    p.sendMessage(ok ? ChatColor.GREEN + "Deleted shop item '" + key + "'." :
                            ChatColor.RED + "Could not delete '" + key + "'.");
                    openEditor(p, pageOf(p));
                    return;
                }

                if (e.isLeftClick() && e.isShiftClick()) {
                    // edit amount
                    pendingEdit.put(p.getUniqueId(), new PendingEdit(key, EditField.AMOUNT));
                    p.closeInventory();
                    p.sendMessage(ChatColor.AQUA + "Editing amount for '" + key + "'. Type the new amount (integer) in chat.");
                    return;
                }

                if (e.isLeftClick()) {
                    // edit price
                    pendingEdit.put(p.getUniqueId(), new PendingEdit(key, EditField.PRICE));
                    p.closeInventory();
                    p.sendMessage(ChatColor.GREEN + "Editing price for '" + key + "'. Type the new price (integer points) in chat.");
                    return;
                }
            }
            return;
        }

        // Bottom inventory while editor open:
        // Block shift-click to top
        if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            e.setCancelled(true);
        }
        // Block keyboard swaps by default (we only allow when clicking DROP_SLOT)
        if (e.getClick().isKeyboardClick()) {
            e.setCancelled(true);
        }
    }

    // -------- Drag handling (supports dropping onto the hopper) --------
    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!isEditor(e.getInventory())) return;

        // If drag targets any top slot other than the DROP_SLOT -> cancel
        boolean targetsTop = false;
        for (int slot : e.getRawSlots()) {
            if (slot < SIZE) {
                targetsTop = true;
                if (slot != DROP_SLOT) { e.setCancelled(true); return; }
            }
        }
        if (!targetsTop) return;

        // Drag onto DROP_SLOT: start create flow with old cursor item
        if (e.getRawSlots().contains(DROP_SLOT)) {
            e.setCancelled(true);
            Player p = (Player) e.getWhoClicked();
            ItemStack base = e.getOldCursor();
            if (base == null || base.getType().isAir()) {
                p.sendMessage(ChatColor.GRAY + "Drag the item from your cursor onto the hopper.");
                return;
            }
            // clear cursor now (sync)
            p.setItemOnCursor(null);
            startCreateFlow(p, base.clone());
        }
    }

    private static void startCreateFlow(Player p, ItemStack base) {
        PendingCreate pc = new PendingCreate(base);
        pendingCreate.put(p.getUniqueId(), pc);
        p.closeInventory();
        p.sendMessage(ChatColor.GREEN + "New item queued for shop entry.");
        p.sendMessage(ChatColor.YELLOW + "Type the " + ChatColor.GOLD + "key" + ChatColor.YELLOW + " in chat (no spaces).");
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!isEditor(e.getInventory())) return;
        // nothing needed
    }

    // -------- Chat prompts --------
    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        UUID u = e.getPlayer().getUniqueId();

        // 1) Create flow
        PendingCreate pc = pendingCreate.get(u);
        if (pc != null) {
            e.setCancelled(true);

            if (pc.key == null) {
                String key = e.getMessage().trim();
                if (key.isEmpty() || key.contains(" ")) {
                    e.getPlayer().sendMessage(ChatColor.RED + "Invalid key. No spaces, not empty. Type the key again:");
                    return;
                }
                pc.key = key;
                e.getPlayer().sendMessage(ChatColor.GREEN + "Key set to: " + ChatColor.GOLD + pc.key);
                e.getPlayer().sendMessage(ChatColor.YELLOW + "Now type the " + ChatColor.GOLD + "price in points" + ChatColor.YELLOW + " (integer).");
                return;
            }

            if (pc.price == null) {
                int price;
                try {
                    price = Integer.parseInt(e.getMessage().trim());
                    if (price < 0) throw new NumberFormatException();
                } catch (NumberFormatException ex) {
                    e.getPlayer().sendMessage(ChatColor.RED + "Invalid price. Type a positive integer.");
                    return;
                }

                final int fPrice = price;
                Bukkit.getScheduler().runTask(IaddithisSkilling.getInstance(), () -> {
                    Map<String,Object> entry = SlayerService.entryFromItem(pc.key, fPrice, pc.base);
                    SlayerService.addOrReplaceShopItem(entry);
                    SlayerService.saveSlayerFile(); // save + reload
                    pendingCreate.remove(u);
                    e.getPlayer().sendMessage(ChatColor.GREEN + "Saved shop entry '" + pc.key + "' (" + fPrice + " pts).");
                    openEditor(e.getPlayer(), 1);
                });
                return;
            }
        }

        // 2) Edit flow (price/amount)
        PendingEdit pe = pendingEdit.get(u);
        if (pe != null) {
            e.setCancelled(true);

            int value;
            try {
                value = Integer.parseInt(e.getMessage().trim());
                if (value < 0) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                e.getPlayer().sendMessage(ChatColor.RED + "Invalid number. Type a positive integer.");
                return;
            }

            final int fVal = value;
            Bukkit.getScheduler().runTask(IaddithisSkilling.getInstance(), () -> {
                Map<String,Object> item = SlayerService.findShopItem(pe.key);
                if (item == null) {
                    e.getPlayer().sendMessage(ChatColor.RED + "Shop item not found anymore.");
                    pendingEdit.remove(u);
                    openEditor(e.getPlayer(), 1);
                    return;
                }

                if (pe.field == EditField.PRICE) {
                    item.put("price", fVal);
                    e.getPlayer().sendMessage(ChatColor.GREEN + "Price of '" + pe.key + "' set to " + fVal + " pts.");
                } else {
                    item.put("amount", Math.max(1, fVal));
                    e.getPlayer().sendMessage(ChatColor.AQUA + "Amount of '" + pe.key + "' set to " + Math.max(1, fVal) + ".");
                }

                SlayerService.addOrReplaceShopItem(item); // persists & replaces by key
                SlayerService.saveSlayerFile();           // save + reload
                pendingEdit.remove(u);
                openEditor(e.getPlayer(), 1);
            });
        }
    }
}
