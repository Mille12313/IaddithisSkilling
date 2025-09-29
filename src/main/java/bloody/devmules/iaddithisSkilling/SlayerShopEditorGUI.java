package bloody.devmules.iaddithisSkilling;

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
    private static final Map<UUID, Integer> moveSourceSlot = new HashMap<>(); // selected source (0..44)

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

    public static void openEditor(Player p, int page) {
        page = Math.max(1, page);
        currentPage.put(p.getUniqueId(), page);

        Inventory inv = Bukkit.createInventory(new SlayerShopEditorHolder(), SIZE, TITLE + " §8(p." + page + ")");

        // background
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta pm = pane.getItemMeta(); pm.setDisplayName(" "); pane.setItemMeta(pm);
        for (int i = 0; i < SIZE; i++) inv.setItem(i, pane);

        inv.setItem(PREV_SLOT, makeItem(Material.ARROW, ChatColor.YELLOW + "Previous page"));
        inv.setItem(NEXT_SLOT, makeItem(Material.ARROW, ChatColor.YELLOW + "Next page"));

        inv.setItem(DROP_SLOT, makeItem(
                Material.HOPPER,
                ChatColor.GREEN + "Drop an item here",
                ChatColor.GRAY + "→ Creates a new shop entry",
                ChatColor.DARK_GRAY + "You will set key & price via chat"
        ));

        // Place entries by saved slot (0..44)
        Map<Integer, Map<String, Object>> bySlot = SlayerService.shopBySlot();
        for (int slot = 0; slot < 45; slot++) {
            Map<String,Object> m = bySlot.get(slot);
            if (m != null) inv.setItem(slot, toIcon(m, slot));
        }

        p.openInventory(inv);
    }

    private static ItemStack toIcon(Map<String,Object> m, int slot) {
        String key = String.valueOf(m.get("key"));
        int price = toInt(m.get("price"), 0);
        int amount = toInt(m.get("amount"), 1);

        ItemStack icon = SlayerService.entryToItem(m);
        if (icon == null || icon.getType() == Material.AIR) icon = new ItemStack(Material.BARRIER);
        icon.setAmount(Math.max(1, amount));

        ItemMeta meta = icon.getItemMeta();

        // Use real item name if present; if none, show material; also show key subtly
        String realName = (meta != null && meta.hasDisplayName())
                ? ChatColor.RESET + meta.getDisplayName()
                : ChatColor.YELLOW + prettyMaterial(icon.getType());
        String title = realName;
        if (meta == null) meta = icon.getItemMeta();
        meta.setDisplayName(title);

        List<String> lore = (meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>());

        lore.add(ChatColor.DARK_GRAY + "— key: " + key + " (slot " + slot + ")");
        String type = String.valueOf(m.get("type")).toLowerCase(Locale.ROOT);
        if ("itemsadder".equals(type)) lore.add(ChatColor.DARK_GRAY + "itemsadder: " + String.valueOf(m.get("id")));
        if ("vanilla".equals(type))    lore.add(ChatColor.DARK_GRAY + "vanilla: " + String.valueOf(m.get("material")));
        if ("serialized".equals(type)) lore.add(ChatColor.DARK_GRAY + "custom meta preserved");
        lore.add("");
        lore.add(ChatColor.GOLD + "Price: " + price + " pts   " + ChatColor.GRAY + "Amount: " + amount);
        lore.add("");
        lore.add(ChatColor.GREEN + "Left-click: edit price");
        lore.add(ChatColor.AQUA + "Shift-left: edit amount");
        lore.add(ChatColor.RED + "Right-click: delete");
        lore.add(ChatColor.YELLOW + "Q (Drop): select to move");

        meta.setLore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private static String prettyMaterial(Material m) {
        String n = m.name().toLowerCase(Locale.ROOT).replace('_',' ');
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
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

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!isEditor(e.getInventory())) return;
        final Player p = (Player) e.getWhoClicked();
        final int raw = e.getRawSlot();

        // Cancel all top inv interactions by default
        if (raw < SIZE) {
            e.setCancelled(true);

            if (raw == PREV_SLOT) { openEditor(p, Math.max(1, pageOf(p) - 1)); return; }
            if (raw == NEXT_SLOT) { openEditor(p, pageOf(p) + 1); return; }

            // DROP_SLOT: create via cursor/number-key/offhand
            if (raw == DROP_SLOT) {
                ItemStack source = null;

                // Cursor
                ItemStack cursor = e.getCursor();
                if (cursor != null && !cursor.getType().isAir()) {
                    source = cursor.clone();
                    p.setItemOnCursor(null);
                }

                // Number key swap
                if (source == null && e.getClick().isKeyboardClick() && e.getHotbarButton() >= 0) {
                    ItemStack hot = p.getInventory().getItem(e.getHotbarButton());
                    if (hot != null && !hot.getType().isAir()) {
                        source = hot.clone();
                        p.getInventory().setItem(e.getHotbarButton(), null);
                    }
                }

                // Offhand swap
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

            // Moving logic first (Q to select, then click any target 0..44)
            if (raw >= 0 && raw < 45) {
                // Q (drop key) on a slot = select/unselect as source
                if (e.getClick() == ClickType.DROP) {
                    if (e.getCurrentItem() == null || e.getCurrentItem().getType().isAir()) {
                        p.sendMessage(ChatColor.RED + "No item in this slot to move.");
                        return;
                    }
                    moveSourceSlot.put(p.getUniqueId(), raw);
                    p.sendMessage(ChatColor.YELLOW + "Selected slot " + raw + ". Click a target slot (empty or occupied) to move/swap.");
                    return;
                }

                Integer src = moveSourceSlot.get(p.getUniqueId());
                if (src != null) {
                    int dst = raw;
                    // Get entries by slot
                    Map<String,Object> srcEntry = SlayerService.findShopItemBySlot(src);
                    if (srcEntry == null) {
                        moveSourceSlot.remove(p.getUniqueId());
                        p.sendMessage(ChatColor.RED + "Source entry vanished. Try again.");
                        openEditor(p, pageOf(p));
                        return;
                    }
                    Map<String,Object> dstEntry = SlayerService.findShopItemBySlot(dst);

                    if (dstEntry == null) {
                        // Move to empty slot
                        boolean ok = SlayerService.setShopItemSlot(String.valueOf(srcEntry.get("key")), dst);
                        SlayerService.saveSlayerFile();
                        p.sendMessage(ok ? ChatColor.GREEN + "Moved to slot " + dst + "."
                                : ChatColor.RED + "Could not move.");
                    } else {
                        // Swap
                        boolean ok = SlayerService.swapShopItemSlots(String.valueOf(srcEntry.get("key")),
                                String.valueOf(dstEntry.get("key")));
                        SlayerService.saveSlayerFile();
                        p.sendMessage(ok ? ChatColor.GREEN + "Swapped slots " + src + " ↔ " + dst + "."
                                : ChatColor.RED + "Could not swap.");
                    }
                    moveSourceSlot.remove(p.getUniqueId());
                    openEditor(p, pageOf(p));
                    return;
                }

                // No move in progress → edit/delete
                ItemStack clicked = e.getCurrentItem();
                if (clicked == null || clicked.getType().isAir()) {
                    // empty tile click does nothing
                    return;
                }

                // Extract the key we embedded in lore/title is unreliable if renamed;
                // safer: look up entry by slot
                Map<String,Object> m = SlayerService.findShopItemBySlot(raw);
                if (m == null) return;
                String key = String.valueOf(m.get("key"));

                if (e.isRightClick()) {
                    boolean ok = SlayerService.deleteShopItemByKey(key);
                    SlayerService.saveSlayerFile();
                    p.sendMessage(ok ? ChatColor.GREEN + "Deleted '" + key + "'."
                            : ChatColor.RED + "Could not delete '" + key + "'.");
                    openEditor(p, pageOf(p));
                    return;
                }

                if (e.isLeftClick() && e.isShiftClick()) {
                    pendingEdit.put(p.getUniqueId(), new PendingEdit(key, EditField.AMOUNT));
                    p.closeInventory();
                    p.sendMessage(ChatColor.AQUA + "Editing amount for '" + key + "'. Type the new amount (integer) in chat.");
                    return;
                }

                if (e.isLeftClick()) {
                    pendingEdit.put(p.getUniqueId(), new PendingEdit(key, EditField.PRICE));
                    p.closeInventory();
                    p.sendMessage(ChatColor.GREEN + "Editing price for '" + key + "'. Type the new price (integer points) in chat.");
                    return;
                }
            }

            return;
        }

        // bottom inv: block shift-click to top / keyboard swaps
        if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) e.setCancelled(true);
        if (e.getClick().isKeyboardClick()) e.setCancelled(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!isEditor(e.getInventory())) return;

        boolean targetsTop = false;
        for (int slot : e.getRawSlots()) {
            if (slot < SIZE) {
                targetsTop = true;
                if (slot != DROP_SLOT) { e.setCancelled(true); return; }
            }
        }
        if (!targetsTop) return;

        if (e.getRawSlots().contains(DROP_SLOT)) {
            e.setCancelled(true);
            Player p = (Player) e.getWhoClicked();
            ItemStack base = e.getOldCursor();
            if (base == null || base.getType().isAir()) {
                p.sendMessage(ChatColor.GRAY + "Drag the item from your cursor onto the hopper.");
                return;
            }
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
        // nothing
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        UUID u = e.getPlayer().getUniqueId();

        // Create flow
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

                    // assign first free slot
                    int slot = SlayerService.nextFreeSlot();
                    if (slot >= 0) entry.put("slot", slot);

                    SlayerService.addOrReplaceShopItem(entry);
                    SlayerService.saveSlayerFile();
                    pendingCreate.remove(u);
                    e.getPlayer().sendMessage(ChatColor.GREEN + "Saved '" + pc.key + "' (" + fPrice + " pts)"
                            + (slot >= 0 ? " at slot " + slot : " (no free slot; not placed)"));
                    openEditor(e.getPlayer(), 1);
                });
                return;
            }
        }

        // Edit flow
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

                SlayerService.addOrReplaceShopItem(item);
                SlayerService.saveSlayerFile();
                pendingEdit.remove(u);
                openEditor(e.getPlayer(), 1);
            });
        }
    }
}
