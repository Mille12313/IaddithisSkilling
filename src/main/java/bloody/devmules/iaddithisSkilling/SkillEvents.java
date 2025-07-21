package bloody.devmules.iaddithisSkilling;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.event.executors.TownyActionEventExecutor;
import com.palmergames.bukkit.towny.utils.CombatUtil;
import dev.lone.itemsadder.api.Events.CustomBlockBreakEvent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Boat;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.*;

public class SkillEvents implements Listener {
    public static final int DEFAULT_FISHING_DROP_CHANCE = 35;
    private final Map<UUID, Location> lastStep = new HashMap<>();
    private final Map<UUID, Integer> stepCount = new HashMap<>();
    private final Map<UUID, Location> lastBoat = new HashMap<>();
    private final Map<UUID, Integer> boatCount = new HashMap<>();

    private static final Set<EntityType> EXCLUDED_ENTITIES = EnumSet.of(EntityType.WOLF, EntityType.HORSE, EntityType.ARMOR_STAND);

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (isBlockeEventdOrCancelledByTowny(e, e.getPlayer(), e.getBlock())) return;
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SURVIVAL) return;

        // Silk Touch check
        if (p.getInventory().getItemInMainHand() != null &&
                p.getInventory().getItemInMainHand().containsEnchantment(Enchantment.SILK_TOUCH)) {
            return; // No XP for Silk Touch
        }

        FileConfiguration cfg = IaddithisSkilling.getInstance().getConfig();
        ConfigurationSection xpCfg = cfg.getConfigurationSection("xpConfig");
        if (xpCfg == null) return;

        String type = e.getBlock().getType().name();

        // Mining
        ConfigurationSection mineSec = xpCfg.getConfigurationSection("mining");
        if (mineSec != null && mineSec.isDouble(type)) {
            double xp = mineSec.getDouble(type);
            SkillManager.addXP(p, "MINING", xp);
            sendXp(p, xp, "MINING");
        }

        // Woodcutting
        ConfigurationSection woodSec = xpCfg.getConfigurationSection("woodcutting");
        if (woodSec != null && woodSec.isDouble(type)) {
            double xp = woodSec.getDouble(type);
            SkillManager.addXP(p, "WOODCUTTING", xp);
            sendXp(p, xp, "WOODCUTTING");
        }

        // Farming (alleen volgroeid)
        ConfigurationSection farmSec = xpCfg.getConfigurationSection("farming");
        if (farmSec != null && farmSec.isDouble(type)) {
            boolean mature = true;
            Block b = e.getBlock();
            if (b.getBlockData() instanceof Ageable ageData) {
                mature = (ageData.getAge() == ageData.getMaximumAge());
            }
            if (mature) {
                double xp = farmSec.getDouble(type);
                SkillManager.addXP(p, "FARMING", xp);
                sendXp(p, xp, "FARMING");
            }
        }
    }

    // ItemsAdder Custom Crops (final stage only, config-based)
    @EventHandler
    public void onCustomBlockBreak(CustomBlockBreakEvent event) {
        isBlockeEventdOrCancelledByTowny(event, event.getPlayer(), event.getBlock());
        Player p = event.getPlayer();
        if (p.getGameMode() != GameMode.SURVIVAL) return;

        FileConfiguration cfg = IaddithisSkilling.getInstance().getConfig();
        ConfigurationSection xpCfg = cfg.getConfigurationSection("xpConfig");
        if (xpCfg == null) return;
        ConfigurationSection farmSec = xpCfg.getConfigurationSection("farming");
        if (farmSec == null) return;

        String namespacedId = event.getNamespacedID();
        if (!namespacedId.contains(":")) return;
        String customBlockID = namespacedId.split(":")[1];
        String[] nameArray = customBlockID.split("_");

        // Expect format: <crop>_stage_<nummer>
        if (nameArray.length < 3) return;
        if (!nameArray[1].equalsIgnoreCase("stage")) return;

        int stageNum;
        try {
            stageNum = Integer.parseInt(nameArray[2]);
        } catch (NumberFormatException ex) {
            return;
        }
        String configKey = customBlockID.toUpperCase();

        // Only give XP for configured final stage
        if (farmSec.isDouble(configKey)) {
            if (
                    (nameArray[0].equalsIgnoreCase("grape") && stageNum == 6)
                            || (!nameArray[0].equalsIgnoreCase("grape") && stageNum == 4)
            ) {
                double xp = farmSec.getDouble(configKey);
                SkillManager.addXP(p, "FARMING", xp);
                sendXp(p, xp, "FARMING");
            }
        }
    }

    // Combat: Only XP for actual damage, no PvP, no excluded mobs
    @EventHandler
    public void onCombat(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) return;
        if (!(e.getDamager() instanceof Player p)) return;
        if (p.getGameMode() != GameMode.SURVIVAL) return;
        if (e.getFinalDamage() <= 0) return;
        if (CombatUtil.preventDamageCall(e.getDamager(), e.getEntity(), e.getCause())) return;
        if (e.getEntity() instanceof Player) return; // No PvP XP
        if (!(e.getEntity() instanceof LivingEntity target) || EXCLUDED_ENTITIES.contains(target.getType())) return;

        double xp = Math.round(e.getFinalDamage() * 2.0);
        if (xp > 0) {
            SkillManager.addXP(p, "COMBAT", xp);
            sendXp(p, xp, "COMBAT");
        }
    }

    // Fishing
    @EventHandler
    public void onFish(PlayerFishEvent e) {
        if (e.isCancelled()) return;
        if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            onSuccesfulCatch(e);
        } else if (e.getState() == PlayerFishEvent.State.FISHING) {
            if (afkProtectionDrop(e)) return;
        };
    }

    private void onSuccesfulCatch(PlayerFishEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SURVIVAL) return;
        if (e.getCaught() == null) return;

        EntityType caughtType = e.getCaught().getType();
        String cat;
        if (caughtType == EntityType.COD
                || caughtType == EntityType.SALMON
                || caughtType == EntityType.TROPICAL_FISH
                || caughtType == EntityType.PUFFERFISH) {
            cat = "fish";
        } else if (caughtType == EntityType.DOLPHIN) {
            cat = "treasure";
        } else {
            cat = "junk";
        }

        ConfigurationSection fishCfg = IaddithisSkilling.getInstance()
                .getConfig()
                .getConfigurationSection("xpConfig.fishing");
        if (fishCfg == null) return;

        double xp = fishCfg.getDouble(cat, 0.0);
        if (xp > 0) {
            SkillManager.addXP(p, "FISHING", xp);
            sendXp(p, xp, "FISHING");
        }
    }

    private static boolean afkProtectionDrop(PlayerFishEvent e) {
        Player player = e.getPlayer();
        if (PlayerSessionDataService.playerHasFishingCaptcha(player)) {
            e.setCancelled(true);
            player.sendMessage("Your line is still tangled, untangle it using /untangle " + PlayerSessionDataService.getCurrentFishingCaptcha(player));
            return true;
        }

        ConfigurationSection fishDropCfg = IaddithisSkilling.getInstance()
                .getConfig()
                .getConfigurationSection("other");
        if (fishDropCfg == null) return true;
        int chance = fishDropCfg.getInt("fishing-captcha-chance", DEFAULT_FISHING_DROP_CHANCE);
        if (chance > 0 && ChanceUtil.isRandomChance(chance)) {
            e.setCancelled(true);
            PlayerSessionDataService.applyPlayerFishingCaptcha(player);
            return true;
        }
        return false;
    }

    // Exploration/Sailing (blocks moved; only on foot for exploration)
    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.isCancelled()) return;
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SURVIVAL) return;
        UUID u = p.getUniqueId();
        Location to = e.getTo();
        if (to == null) return;

        FileConfiguration cfg = IaddithisSkilling.getInstance().getConfig();
        ConfigurationSection xpCfg = cfg.getConfigurationSection("xpConfig");

        // Sailing (in boat)
        if (p.isInsideVehicle() && p.getVehicle() instanceof Boat) {
            Location loc = to.getBlock().getLocation();
            Location last = lastBoat.get(u);
            int cnt = boatCount.getOrDefault(u, 0);

            if (last == null) {
                lastBoat.put(u, loc);
                boatCount.put(u, 0);
            } else if (!loc.equals(last)) {
                cnt++;
                int per = xpCfg.getInt("sailing.blocksPerXp", 16);
                if (cnt >= per) {
                    double xp = xpCfg.getDouble("sailing.xpPerUnit", 0.0);
                    SkillManager.addXP(p, "SAILING", xp);
                    sendXp(p, xp, "SAILING");
                    cnt = 0;
                }
                boatCount.put(u, cnt);
                lastBoat.put(u, loc);
            }
            return;
        }

        // Exploration: Only on foot, not in any vehicle
        if (!p.isInsideVehicle()) {
            Location loc = to.getBlock().getLocation();
            Location last = lastStep.get(u);
            int cnt = stepCount.getOrDefault(u, 0);

            if (last == null) {
                lastStep.put(u, loc);
                stepCount.put(u, 0);
            } else if (!loc.equals(last)) {
                cnt++;
                int per = xpCfg.getInt("exploring.blocksPerXp", 16);
                if (cnt >= per) {
                    double xp = xpCfg.getDouble("exploring.xpPerUnit", 0.0);
                    SkillManager.addXP(p, "EXPLORATION", xp);
                    sendXp(p, xp, "EXPLORATION");
                    cnt = 0;
                }
                stepCount.put(u, cnt);
                lastStep.put(u, loc);
            }
        }
    }

    // Slayer: Only when living entity dies (excluding players, armor stands, wolves, horses)
    @EventHandler
    public void onMobDeath(EntityDeathEvent e) {
        if (e.getEntity() instanceof Player) return;
        if (!(e.getEntity() instanceof LivingEntity mob)) return;
        if (EXCLUDED_ENTITIES.contains(mob.getType())) return;

        Player killer = e.getEntity().getKiller();
        if (killer == null || killer.getGameMode() != GameMode.SURVIVAL) return;

        double xp = Math.round(mob.getMaxHealth() * 2.0);
        SkillManager.addXP(killer, "SLAYER", xp);
        sendXp(killer, xp, "SLAYER");
    }

    // XP ActionBar (only if enabled)
    private void sendXp(Player p, double xp, String skill) {
        var data = IaddithisSkilling.getInstance().getData();
        boolean notif = data.getBoolean(p.getUniqueId() + ".settings.xpNotifications", true);
        if (notif) {
            p.spigot().sendMessage(
                    net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    new net.md_5.bungee.api.chat.TextComponent("✨ +" + (int) xp + " " + skill + " XP")
            );
        }
    }


    private static boolean isBlockeEventdOrCancelledByTowny(Cancellable e, Player player, Block block) {
        return e.isCancelled() ||
                (TownyAPI.getInstance().isTownyWorld(block.getWorld()) && !TownyActionEventExecutor.canDestroy(player, block));
    }
}
