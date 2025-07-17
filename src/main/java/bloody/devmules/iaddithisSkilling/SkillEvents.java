// src/main/java/bloody/devmules/iaddithisSkilling/SkillEvents.java
package bloody.devmules.iaddithisSkilling;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Boat;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SkillEvents implements Listener {
    private final Map<UUID, Location> lastStep = new HashMap<>();
    private final Map<UUID, Integer> stepCount = new HashMap<>();
    private final Map<UUID, Location> lastBoat = new HashMap<>();
    private final Map<UUID, Integer> boatCount = new HashMap<>();

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (e.isCancelled()) return;
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SURVIVAL) return;

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

        // Farming
        ConfigurationSection farmSec = xpCfg.getConfigurationSection("farming");
        if (farmSec != null && farmSec.isDouble(type)) {
            double xp = farmSec.getDouble(type);
            SkillManager.addXP(p, "FARMING", xp);
            sendXp(p, xp, "FARMING");
        }
    }

    @EventHandler
    public void onCombat(EntityDamageByEntityEvent e) {
        if (e.isCancelled() || !(e.getDamager() instanceof Player)) return;
        Player p = (Player) e.getDamager();
        if (p.getGameMode() != GameMode.SURVIVAL) return;

        double xp = Math.round(e.getDamage() * 2.0);
        if (xp > 0) {
            SkillManager.addXP(p, "COMBAT", xp);
            sendXp(p, xp, "COMBAT");
        }
    }

    @EventHandler
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SURVIVAL) return;

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

        // Sailing
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

        // Exploration
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

    @EventHandler
    public void onMobDeath(EntityDeathEvent e) {
        if (e.getEntity() instanceof Player) return;
        Player killer = e.getEntity().getKiller();
        if (killer == null || killer.getGameMode() != GameMode.SURVIVAL) return;

        double xp = Math.round(((LivingEntity) e.getEntity()).getMaxHealth() * 2.0);
        SkillManager.addXP(killer, "SLAYER", xp);
        sendXp(killer, xp, "SLAYER");
    }

    private void sendXp(Player p, double xp, String skill) {
        var data = IaddithisSkilling.getInstance().getData();
        boolean notif = data.getBoolean(p.getUniqueId() + ".settings.xpNotifications", true);
        if (notif) {
            p.sendMessage("✨ +" + (int) xp + " " + skill + " XP");
        }
    }
}
