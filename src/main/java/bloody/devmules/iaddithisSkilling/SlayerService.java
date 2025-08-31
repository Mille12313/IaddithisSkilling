package bloody.devmules.iaddithisSkilling;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;

public final class SlayerService {
    private static File slayerFile;
    private static FileConfiguration slayerCfg;

    private SlayerService(){}

    // ---------- Lifecycle ----------
    public static void init() {
        try {
            var plugin = IaddithisSkilling.getInstance();
            slayerFile = new File(plugin.getDataFolder(), "slayer.yml");
            if (!slayerFile.exists()) {
                slayerFile.getParentFile().mkdirs();
                try (InputStream in = plugin.getResource("slayer.yml")) {
                    if (in != null) {
                        Files.copy(in, slayerFile.toPath());
                    } else {
                        slayerFile.createNewFile();
                    }
                }
            }
            reloadFromDisk();
        } catch (Exception ex) {
            IaddithisSkilling.getInstance().getLogger().severe("Failed to load slayer.yml: " + ex.getMessage());
        }
    }

    public static synchronized void reloadFromDisk() {
        slayerCfg = YamlConfiguration.loadConfiguration(slayerFile);
    }

    public static synchronized void saveSlayerFile() {
        try {
            slayerCfg.save(slayerFile);
            // direct herladen zodat GUIs de nieuwe data zien
            reloadFromDisk();
        } catch (Exception e) {
            IaddithisSkilling.getInstance().getLogger().severe("Failed to save slayer.yml: " + e.getMessage());
        }
    }

    public static FileConfiguration cfg() {
        return slayerCfg;
    }

    // ---------- Data in data.yml ----------
    public static SlayerState getState(UUID uuid) {
        var data = IaddithisSkilling.getInstance().getData();
        String base = "slayer." + uuid;
        String id = data.getString(base + ".current.id", null);
        String mob = data.getString(base + ".current.mob", null);
        int remaining = data.getInt(base + ".current.remaining", 0);
        int streak = data.getInt(base + ".streak", 0);
        int points = data.getInt(base + ".points", 0);
        return new SlayerState(id, mob, remaining, streak, points);
    }

    public static void saveState(UUID uuid, SlayerState st) {
        var inst = IaddithisSkilling.getInstance();
        var data = inst.getData();
        String base = "slayer." + uuid;

        if (st == null || st.id == null) {
            data.set(base + ".current", null);
        } else {
            data.set(base + ".current.id", st.id);
            data.set(base + ".current.mob", st.mob);
            data.set(base + ".current.remaining", st.remaining);
        }
        data.set(base + ".streak", st != null ? st.streak : 0);
        data.set(base + ".points", st != null ? st.points : 0);
        inst.saveData();
    }

    // Points helpers
    public static int getPoints(UUID uuid) {
        var data = IaddithisSkilling.getInstance().getData();
        return data.getInt("slayer." + uuid + ".points", 0);
    }

    public static void addPoints(UUID uuid, int amount) {
        if (amount == 0) return;
        var inst = IaddithisSkilling.getInstance();
        var data = inst.getData();
        String path = "slayer." + uuid + ".points";
        data.set(path, Math.max(0, data.getInt(path, 0) + amount));
        inst.saveData();
    }

    public static boolean spendPoints(UUID uuid, int amount) {
        if (amount <= 0) return true;
        var inst = IaddithisSkilling.getInstance();
        var data = inst.getData();
        String path = "slayer." + uuid + ".points";
        int cur = data.getInt(path, 0);
        if (cur < amount) return false;
        data.set(path, cur - amount);
        inst.saveData();
        return true;
    }

    // ---------- Safe list conversion ----------
    private static List<Map<String, Object>> toStringObjectMapList(List<Map<?, ?>> raw) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (raw == null) return out;
        for (Map<?, ?> m : raw) {
            Map<String, Object> mm = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) mm.put(String.valueOf(e.getKey()), e.getValue());
            out.add(mm);
        }
        return out;
    }

    // ---------- Tasks ----------
    public static List<Map<String, Object>> tasks() {
        return toStringObjectMapList(slayerCfg.getMapList("tasks"));
    }

    public static SlayerTask rollRandomTask() {
        var list = tasks();
        if (list.isEmpty()) return null;

        var raw = list.get(ThreadLocalRandom.current().nextInt(list.size()));
        String id = String.valueOf(raw.get("id"));
        String mob = String.valueOf(raw.get("mythicMob"));
        int min = toInt(raw.get("amountMin"), 5);
        int max = Math.max(min, toInt(raw.get("amountMax"), min));
        int amount = ThreadLocalRandom.current().nextInt(min, max + 1);
        Integer points = raw.containsKey("points") ? toInt(raw.get("points"), -1) : -1;

        return new SlayerTask(id, mob, amount, points);
    }

    private static int toInt(Object o, int def) {
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return def; }
    }

    // ---------- Points / streak ----------
    public static int basePointsPerTask(SlayerTask task) {
        if (task.perTaskPoints != null && task.perTaskPoints > 0) return task.perTaskPoints;
        return slayerCfg.getInt("settings.basePointsPerTask", 10);
    }

    public static int skipCost() {
        return slayerCfg.getInt("settings.skipCost", 30);
    }

    public static int computePayoutWithStreak(int base, int streak, boolean skippedJustNow) {
        var sec = slayerCfg.getConfigurationSection("settings.streakBonus");
        if (sec == null || !sec.getBoolean("enabled", true)) return base;

        int from = sec.getInt("fromStreak", 3);
        if (skippedJustNow) return base;
        if (streak < from) return base;

        String type = sec.getString("bonusType", "flat").toLowerCase(Locale.ROOT);
        if (type.equals("multiplier")) {
            double mul = sec.getDouble("multiplier", 1.0);
            return (int) Math.round(base * mul);
        } else {
            int flat = sec.getInt("flatAmount", 5);
            return base + flat;
        }
    }

    // ---------- Shop (read) ----------
    public static List<Map<String, Object>> shop() {
        return toStringObjectMapList(slayerCfg.getMapList("shop"));
    }

    public static Map<String, Object> findShopItem(String key) {
        if (key == null) return null;
        for (var it : shop()) {
            if (key.equalsIgnoreCase(String.valueOf(it.get("key")))) return it;
        }
        return null;
    }

    // ---------- Shop (write CRUD) ----------
    /** Add new or replace existing (by 'key'). */
    public static void addOrReplaceShopItem(Map<String,Object> entry) {
        if (entry == null) return;
        String key = String.valueOf(entry.get("key"));
        if (key == null || key.isEmpty()) return;

        List<Map<String,Object>> list = shop();
        boolean replaced = false;
        for (int i = 0; i < list.size(); i++) {
            if (key.equalsIgnoreCase(String.valueOf(list.get(i).get("key")))) {
                list.set(i, entry);
                replaced = true;
                break;
            }
        }
        if (!replaced) list.add(entry);
        slayerCfg.set("shop", list);
    }

    /** Remove by key. Returns true on success. */
    public static boolean deleteShopItemByKey(String key) {
        if (key == null || key.isEmpty()) return false;
        List<Map<String,Object>> list = shop();
        boolean removed = list.removeIf(m -> key.equalsIgnoreCase(String.valueOf(m.get("key"))));
        if (removed) slayerCfg.set("shop", list);
        return removed;
    }

    // ---------- Serialization helpers (complex items zoals tipped arrows) ----------
    public static boolean isComplex(ItemStack item) {
        if (item == null) return false;
        if (item.getType().name().contains("POTION")) return true;
        if (item.getType() == org.bukkit.Material.TIPPED_ARROW) return true;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        if (meta.hasEnchants() || meta.hasLore() || meta.hasDisplayName()) return true;
        try { if (meta.hasCustomModelData()) return true; } catch (NoSuchMethodError ignored) {}
        if (meta instanceof PotionMeta) return true;
        return false;
    }

    public static String serializeItem(ItemStack item) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             BukkitObjectOutputStream oos = new BukkitObjectOutputStream(baos)) {
            oos.writeObject(item);
            oos.flush();
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            IaddithisSkilling.getInstance().getLogger().severe("SerializeItem failed: " + e.getMessage());
            return null;
        }
    }

    public static ItemStack deserializeItem(String base64) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(Base64.getDecoder().decode(base64));
             BukkitObjectInputStream ois = new BukkitObjectInputStream(bais)) {
            Object obj = ois.readObject();
            return (ItemStack) obj;
        } catch (IOException | ClassNotFoundException e) {
            IaddithisSkilling.getInstance().getLogger().severe("DeserializeItem failed: " + e.getMessage());
            return new ItemStack(org.bukkit.Material.BARRIER);
        }
    }

    /**
     * Bouw een shop-entry uit een ItemStack:
     * - ItemsAdder ID (type=itemsadder) als mogelijk
     * - Vanilla (type=vanilla) voor simpele items
     * - Serialized base64 (type=serialized) voor items met complexe meta
     */
    public static Map<String,Object> entryFromItem(String key, int price, ItemStack base) {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("price", price);

        // ItemsAdder?
        if (Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            try {
                dev.lone.itemsadder.api.CustomStack cs = dev.lone.itemsadder.api.CustomStack.byItemStack(base);
                if (cs != null) {
                    m.put("type", "itemsadder");
                    String id = cs.getNamespacedID() != null ? cs.getNamespacedID() : cs.getId();
                    m.put("id", id);
                    m.put("amount", Math.max(1, base.getAmount()));
                    return m;
                }
            } catch (Throwable ignored) {}
        }

        if (isComplex(base)) {
            String b64 = serializeItem(base);
            if (b64 != null) {
                m.put("type", "serialized");
                m.put("item", b64);
                return m;
            }
        }

        // vanilla fallback
        m.put("type", "vanilla");
        m.put("material", base.getType().name());
        m.put("amount", Math.max(1, base.getAmount()));
        return m;
    }

    /** Zet entry om naar een ItemStack voor weergave/uitgifte. */
    public static ItemStack entryToItem(Map<String,Object> m) {
        String type = String.valueOf(m.get("type")).toLowerCase(Locale.ROOT);
        int amount = toInt(m.get("amount"), 1);

        switch (type) {
            case "itemsadder": {
                if (Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
                    try {
                        String id = String.valueOf(m.get("id"));
                        dev.lone.itemsadder.api.CustomStack cs = dev.lone.itemsadder.api.CustomStack.getInstance(id);
                        if (cs != null) {
                            ItemStack it = cs.getItemStack().clone();
                            it.setAmount(Math.max(1, amount));
                            return it;
                        }
                    } catch (Throwable ignored) {}
                }
                return new ItemStack(org.bukkit.Material.BARRIER);
            }
            case "serialized": {
                String b64 = String.valueOf(m.get("item"));
                ItemStack it = deserializeItem(b64);
                if (it != null) {
                    it.setAmount(Math.max(1, it.getAmount()));
                    return it;
                }
                return new ItemStack(org.bukkit.Material.BARRIER);
            }
            case "vanilla":
            default: {
                String mat = String.valueOf(m.get("material"));
                org.bukkit.Material mm = org.bukkit.Material.matchMaterial(mat);
                ItemStack it = new ItemStack(mm != null ? mm : org.bukkit.Material.BARRIER);
                it.setAmount(Math.max(1, amount));
                return it;
            }
        }
    }

    // ---------- DTOs ----------
    public record SlayerTask(String id, String mythicMob, int amount, Integer perTaskPoints) {}

    public static final class SlayerState {
        public String id, mob;
        public int remaining, streak, points;
        public SlayerState(String id, String mob, int remaining, int streak, int points) {
            this.id = id;
            this.mob = mob;
            this.remaining = remaining;
            this.streak = streak;
            this.points = points;
        }
    }
}
