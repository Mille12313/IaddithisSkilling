package bloody.devmules.iaddithisSkilling;

import dev.lone.itemsadder.api.CustomStack;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class SalvageCauldronListener implements Listener {

    private final Map<Location, SalvageJob> activeSalvages = new HashMap<>();
    private static final int SALVAGE_TIME = 7;
    private static final int MAX_QUEUE = 10;
    private static final double MAX_DIST = 8.0;

    @EventHandler
    public void onCauldronSalvage(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) return;

        Block block = event.getClickedBlock();
        Material type = block.getType();

        if (type != Material.LAVA_CAULDRON) {
            if (type == Material.CAULDRON || type == Material.WATER_CAULDRON || type == Material.POWDER_SNOW_CAULDRON) {
                event.getPlayer().sendMessage(ChatColor.RED + "[Salvage] This cauldron must be filled with lava to salvage items.");
            }
            return;
        }

        Player player = event.getPlayer();
        Location cauldronLoc = block.getLocation();

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) return;

        String handItemId;
        CustomStack cs = CustomStack.byItemStack(hand);
        if (cs != null) {
            handItemId = cs.getNamespacedID();
        } else {
            handItemId = hand.getType().toString().toUpperCase();
        }

        FileConfiguration salvageConfig = IaddithisSkilling.getInstance().getSalvageConfig();
        ConfigurationSection salvageSec = salvageConfig.getConfigurationSection("salvage");
        if (salvageSec == null) {
            player.sendMessage(ChatColor.RED + "[Salvage] No salvage mapping found.");
            return;
        }

        ConfigurationSection entry = salvageSec.getConfigurationSection(handItemId);
        if (entry == null) {
            player.sendMessage(ChatColor.RED + "[Salvage] You cannot salvage this item.");
            return;
        }

        double xp = entry.getDouble("xp", 0);

        List<Map<String, Object>> lootList = new ArrayList<>();
        if (entry.isList("loot")) {
            for (Object o : entry.getList("loot")) {
                if (o instanceof Map) {
                    //noinspection unchecked
                    lootList.add((Map<String, Object>) o);
                }
            }
        }

        SalvageJob job = activeSalvages.get(cauldronLoc);
        if (job == null) {
            job = new SalvageJob(cauldronLoc);
            activeSalvages.put(cauldronLoc, job);
            job.start();
        }
        if (job.queue.size() >= MAX_QUEUE) {
            player.sendMessage(ChatColor.RED + "[Salvage] This cauldron is already processing the maximum number of items.");
            return;
        }
        job.queue.add(new SalvageEntry(player, handItemId, hand.clone(), lootList, xp));
        player.sendMessage(ChatColor.GOLD + "[Salvage] Your item was added to the salvage queue! Estimated time: " + (job.queue.size() * SALVAGE_TIME) + "s");

        hand.setAmount(hand.getAmount() - 1);
        player.getInventory().setItemInMainHand(hand.getAmount() <= 0 ? null : hand);

        event.setCancelled(true);
        job.updateBossBar();
    }

    private class SalvageJob {
        final Location cauldronLoc;
        final Queue<SalvageEntry> queue = new LinkedList<>();
        BukkitRunnable task;
        BossBar bossBar = null;
        SalvageEntry processing = null;
        int timer = 0;
        boolean paused = false;

        SalvageJob(Location cauldronLoc) {
            this.cauldronLoc = cauldronLoc;
        }

        void start() {
            task = new BukkitRunnable() {
                @Override
                public void run() {
                    if (queue.isEmpty() && processing == null) {
                        removeBossBar();
                        activeSalvages.remove(cauldronLoc);
                        this.cancel();
                        return;
                    }

                    if (processing == null) {
                        processing = queue.poll();
                        timer = 0;
                        paused = false;
                        processing.player.sendMessage(ChatColor.AQUA + "[Salvage] Salvaging started! Stay close to the cauldron (" + SALVAGE_TIME + " seconds).");
                        cauldronLoc.getWorld().playSound(cauldronLoc, Sound.BLOCK_FURNACE_FIRE_CRACKLE, SoundCategory.BLOCKS, 1f, 1f);
                        createOrUpdateBossBar(processing.player);
                    }

                    boolean tooFar = !processing.player.isOnline()
                            || processing.player.getLocation().distanceSquared(cauldronLoc.clone().add(0.5, 1, 0.5)) > (MAX_DIST * MAX_DIST);

                    if (tooFar) {
                        if (!paused) {
                            paused = true;
                            removeBossBar();
                            processing.player.sendMessage(ChatColor.RED + "[Salvage] You moved too far from the cauldron! Salvaging paused.");
                        }
                        return;
                    } else {
                        if (paused) {
                            paused = false;
                            processing.player.sendMessage(ChatColor.AQUA + "[Salvage] You are back in range. Salvaging resumed.");
                            createOrUpdateBossBar(processing.player);
                        }
                    }

                    cauldronLoc.getWorld().spawnParticle(Particle.SMOKE, cauldronLoc.clone().add(0.5, 1.1, 0.5), 12, 0.28, 0.10, 0.28, 0.03);
                    if (timer % 4 == 0)
                        cauldronLoc.getWorld().playSound(cauldronLoc, Sound.BLOCK_LAVA_POP, SoundCategory.BLOCKS, 0.5f, 0.8f);

                    timer++;
                    updateBossBar();

                    if (timer >= SALVAGE_TIME) {
                        List<ItemStack> outputs = rollLoot(processing.player, processing.originalStack, processing.lootList);
                        boolean given = false;
                        for (ItemStack outStack : outputs) {
                            if (outStack == null) continue;
                            Map<Integer, ItemStack> left = processing.player.getInventory().addItem(outStack);
                            if (!left.isEmpty())
                                cauldronLoc.getWorld().dropItemNaturally(cauldronLoc.clone().add(0.5, 1.0, 0.5), outStack);
                            given = true;
                        }
                        if (processing.xp > 0 && given) {
                            SkillManager.addXP(processing.player, "SALVAGE", processing.xp);
                            processing.player.spigot().sendMessage(
                                    ChatMessageType.ACTION_BAR,
                                    new TextComponent("✨ +" + (int)processing.xp + " SALVAGE XP")
                            );
                        }
                        processing.player.sendMessage(ChatColor.GREEN + "[Salvage] Salvaging complete! Check your inventory.");
                        cauldronLoc.getWorld().spawnParticle(Particle.SMOKE, cauldronLoc.clone().add(0.5, 1.1, 0.5), 18, 0.32, 0.13, 0.32, 0.05);
                        cauldronLoc.getWorld().playSound(cauldronLoc, Sound.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 0.7f, 1.2f);

                        processing = null;
                        updateBossBar();
                    }
                }
            };
            task.runTaskTimer(IaddithisSkilling.getInstance(), 20, 20);
        }

        void createOrUpdateBossBar(Player player) {
            if (bossBar == null) {
                bossBar = Bukkit.createBossBar("", BarColor.PURPLE, BarStyle.SEGMENTED_10);
                bossBar.addPlayer(player);
            }
            updateBossBar();
        }

        void updateBossBar() {
            if (processing == null || bossBar == null || paused) {
                removeBossBar();
                return;
            }
            int current = queue.size() + 1;
            String barText = ChatColor.LIGHT_PURPLE + "Salvaging " + ChatColor.YELLOW + "(" + current + "/" + (current + queue.size()) + ")";
            bossBar.setTitle(barText + ChatColor.GRAY + " [" + (SALVAGE_TIME - timer) + "s]");
            bossBar.setProgress(1.0 - ((double) timer / SALVAGE_TIME));
        }

        void removeBossBar() {
            if (bossBar != null) {
                bossBar.removeAll();
                bossBar = null;
            }
        }

        private List<ItemStack> rollLoot(Player player, ItemStack inputStack, List<Map<String, Object>> lootList) {
            List<ItemStack> result = new ArrayList<>();
            int salvageLevel = SkillManager.getLevel(player, "SALVAGE");

            // Level-bonus: 0.3% per level, max +20%
            double bonusPerLevel = 0.3;
            double maxLevelBonus  = 20.0;
            double levelBonus     = Math.min(salvageLevel * bonusPerLevel, maxLevelBonus);

            // Durability-bonus: proportioneel t.o.v. maxDurability, max +20%
            int maxDurability     = inputStack.getType().getMaxDurability();
            int currentDurability = maxDurability - inputStack.getDurability();
            double maxDurabilityBonus = 20.0;
            double durabilityRatio    = maxDurability > 0
                    ? (double) currentDurability / maxDurability
                    : 0;
            double durabilityBonus    = durabilityRatio * maxDurabilityBonus;

            for (Map<String, Object> lootEntry : lootList) {
                String id = (String) lootEntry.get("id");
                int baseChance = (int) lootEntry.get("chance");

                double finalChance = Math.min(baseChance + levelBonus + durabilityBonus, 99.0);

                if (Math.random() * 100 < finalChance) {
                    ItemStack outStack = parseOutputItem(id);
                    if (outStack != null) result.add(outStack);
                }
            }
            return result;
        }
    }

    private static class SalvageEntry {
        final Player player;
        final String inputItemId;
        final ItemStack originalStack;
        final List<Map<String, Object>> lootList;
        final double xp;

        SalvageEntry(Player player, String inputItemId, ItemStack originalStack, List<Map<String, Object>> lootList, double xp) {
            this.player = player;
            this.inputItemId = inputItemId;
            this.originalStack = originalStack;
            this.lootList = lootList;
            this.xp = xp;
        }
    }

    private ItemStack parseOutputItem(String id) {
        if (id.contains(":")) {
            CustomStack cs = CustomStack.getInstance(id);
            if (cs != null) return cs.getItemStack();
        } else {
            Material mat = Material.matchMaterial(id.toUpperCase());
            if (mat != null) return new ItemStack(mat, 1);
        }
        return null;
    }
}
