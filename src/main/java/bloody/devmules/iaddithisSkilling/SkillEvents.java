package bloody.devmules.iaddithisSkilling;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.event.executors.TownyActionEventExecutor;
import com.palmergames.bukkit.towny.utils.CombatUtil;
import dev.lone.itemsadder.api.Events.CustomBlockBreakEvent;
import org.bukkit.Bukkit;
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

import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Level;

/**
 * Schoon, simpel:
 * - Blocks/ItemsAdder crops → XP uit config.
 * - Fishing/Exploration/Sailing → XP uit config.
 * - Combat per hit → COMBAT XP, MAAR niet op Mythic "nodes" (interne naam bevat "node").
 * - Slayer XP → alleen on-task + (Mythic internal name == task.mob) of fallback Bukkit type.
 * - Skilling nodes geven XP via Mythic skill: alleen bij *_harvest_success → /skillxp ...
 */
public class SkillEvents implements Listener {
    public static final int DEFAULT_FISHING_DROP_CHANCE = 5;

    private final Map<UUID, Location> lastStep = new HashMap<>();
    private final Map<UUID, Integer> stepCount = new HashMap<>();
    private final Map<UUID, Location> lastBoat = new HashMap<>();
    private final Map<UUID, Integer> boatCount = new HashMap<>();

    private static final Set<EntityType> EXCLUDED_ENTITIES =
            EnumSet.of(EntityType.WOLF, EntityType.HORSE, EntityType.ARMOR_STAND);

    // ---------- XP helper (respecteert EXP boost + popup) ----------
    private void giveXp(Player p, String skill, double baseXp) {
        if (p == null || baseXp <= 0) return;
        double eff = ExpBoostService.apply(baseXp);
        if (eff <= 0) return;
        SkillManager.addXP(p, skill, eff);
        sendXp(p, eff, skill);
    }

    // ---------- Blocks ----------
    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (isBlockedByTowny(e, e.getPlayer(), e.getBlock())) return;
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SURVIVAL) return;

        // Silk Touch → geen XP
        if (p.getInventory().getItemInMainHand() != null &&
                p.getInventory().getItemInMainHand().containsEnchantment(Enchantment.SILK_TOUCH)) return;

        FileConfiguration cfg = IaddithisSkilling.getInstance().getConfig();
        ConfigurationSection xpCfg = cfg.getConfigurationSection("xpConfig");
        if (xpCfg == null) return;

        String type = e.getBlock().getType().name();

        var mine = xpCfg.getConfigurationSection("mining");
        if (mine != null && mine.isDouble(type)) giveXp(p, "MINING", mine.getDouble(type));

        var wood = xpCfg.getConfigurationSection("woodcutting");
        if (wood != null && wood.isDouble(type)) giveXp(p, "WOODCUTTING", wood.getDouble(type));

        var farm = xpCfg.getConfigurationSection("farming");
        if (farm != null && farm.isDouble(type)) {
            boolean mature = true;
            Block b = e.getBlock();
            if (b.getBlockData() instanceof Ageable ageData) {
                mature = (ageData.getAge() == ageData.getMaximumAge());
            }
            if (mature) giveXp(p, "FARMING", farm.getDouble(type));
        }
    }

    // ---------- ItemsAdder crops ----------
    @EventHandler
    public void onCustomBlockBreak(CustomBlockBreakEvent event) {
        isBlockedByTowny(event, event.getPlayer(), event.getBlock());
        Player p = event.getPlayer();
        if (p.getGameMode() != GameMode.SURVIVAL) return;

        FileConfiguration cfg = IaddithisSkilling.getInstance().getConfig();
        var xpCfg = cfg.getConfigurationSection("xpConfig");
        if (xpCfg == null) return;
        var farm = xpCfg.getConfigurationSection("farming");
        if (farm == null) return;

        String namespacedId = event.getNamespacedID();
        if (!namespacedId.contains(":")) return;
        String id = namespacedId.split(":")[1];
        String[] parts = id.split("_");
        if (parts.length < 3 || !parts[1].equalsIgnoreCase("stage")) return;

        int stage;
        try { stage = Integer.parseInt(parts[2]); } catch (NumberFormatException ex) { return; }

        String key = id.toUpperCase();
        if (farm.isDouble(key)) {
            boolean finalStage = (parts[0].equalsIgnoreCase("grape") ? stage == 6 : stage == 4);
            if (finalStage) giveXp(p, "FARMING", farm.getDouble(key));
        }
    }

    // ---------- Combat per hit ----------
    @EventHandler
    public void onCombat(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) return;
        if (!(e.getDamager() instanceof Player p)) return;
        if (p.getGameMode() != GameMode.SURVIVAL) return;
        if (e.getFinalDamage() <= 0) return;
        if (CombatUtil.preventDamageCall(e.getDamager(), e.getEntity(), e.getCause())) return;
        if (e.getEntity() instanceof Player) return;
        if (!(e.getEntity() instanceof LivingEntity target) || EXCLUDED_ENTITIES.contains(target.getType())) return;

        // Mythic "nodes" → NOOIT combat XP (zo kun je met verkeerde tool geen skilling XP/cheese krijgen)
        if (MythicUtil.getInternalName(target)
                .map(n -> n.toLowerCase(Locale.ROOT).contains("node"))
                .orElse(false)) {
            return;
        }

        // Normale combat
        double xp = Math.round(e.getFinalDamage() * 2.0);
        if (xp > 0) giveXp(p, "COMBAT", xp);
    }

    // ---------- Fishing ----------
    @EventHandler
    public void onFish(PlayerFishEvent e) {
        if (e.isCancelled()) return;
        if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            Player p = e.getPlayer();
            if (p.getGameMode() != GameMode.SURVIVAL) return;
            if (e.getCaught() == null) return;

            String cat;
            switch (e.getCaught().getType()) {
                case COD, SALMON, TROPICAL_FISH, PUFFERFISH -> cat = "fish";
                case DOLPHIN -> cat = "treasure";
                default -> cat = "junk";
            }
            var fishCfg = IaddithisSkilling.getInstance().getConfig().getConfigurationSection("xpConfig.fishing");
            if (fishCfg == null) return;
            double xp = fishCfg.getDouble(cat, 0.0);
            if (xp > 0) giveXp(p, "FISHING", xp);
        } else if (e.getState() == PlayerFishEvent.State.FISHING) {
            if (afkProtectionDrop(e)) return;
        }
    }

    private static boolean afkProtectionDrop(PlayerFishEvent e) {
        Player player = e.getPlayer();
        if (PlayerSessionDataService.playerHasFishingCaptcha(player)) {
            IaddithisSkilling.getInstance().getLogger().log(Level.INFO,
                    "Player " + player.getName() + " already has a fishing captcha but tried to fish anyway");
            e.setCancelled(true);
            player.sendMessage("Your line is still tangled, untangle it using /untangle "
                    + PlayerSessionDataService.getCurrentFishingCaptcha(player));
            return true;
        }
        var sec = IaddithisSkilling.getInstance().getConfig().getConfigurationSection("other");
        if (sec == null) return true;
        int chance = sec.getInt("fishing-captcha-chance", DEFAULT_FISHING_DROP_CHANCE);
        if (chance > 0 && ChanceUtil.isRandomChance(chance)) {
            IaddithisSkilling.getInstance().getLogger().log(Level.INFO,
                    "Player " + player.getName() + " has tangled their rod!");
            e.setCancelled(true);
            PlayerSessionDataService.applyPlayerFishingCaptcha(player);
            return true;
        }
        return false;
    }

    // ---------- Exploration / Sailing ----------
    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.isCancelled()) return;
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SURVIVAL) return;
        UUID u = p.getUniqueId();
        Location to = e.getTo();
        if (to == null) return;

        var xpCfg = IaddithisSkilling.getInstance().getConfig().getConfigurationSection("xpConfig");

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
                    giveXp(p, "SAILING", xpCfg.getDouble("sailing.xpPerUnit", 0.0));
                    cnt = 0;
                }
                boatCount.put(u, cnt);
                lastBoat.put(u, loc);
            }
            return;
        }

        // Exploration
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
                    giveXp(p, "EXPLORATION", xpCfg.getDouble("exploring.xpPerUnit", 0.0));
                    cnt = 0;
                }
                stepCount.put(u, cnt);
                lastStep.put(u, loc);
            }
        }
    }

    // ---------- Bukkit death → alleen Slayer ----------
    @EventHandler
    public void onMobDeath(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof LivingEntity mob)) return;
        if (mob instanceof Player) return;
        if (EXCLUDED_ENTITIES.contains(mob.getType())) return;

        Player killer = mob.getKiller();
        if (killer == null || killer.getGameMode() != GameMode.SURVIVAL) return;

        // Nodes (internal name bevat "node") → GEEN slayer; nodes geven hun XP via Mythic skill zelf
        if (MythicUtil.getInternalName(mob)
                .map(n -> n.toLowerCase(Locale.ROOT).contains("node"))
                .orElse(false)) {
            return;
        }

        // Slayer on-task
        var st = SlayerService.getState(killer.getUniqueId());
        if (st != null && st.id != null && st.mob != null) {
            boolean match = MythicUtil.getInternalName(mob)
                    .map(n -> n.equalsIgnoreCase(st.mob))
                    .orElseGet(() -> mob.getType().name().equalsIgnoreCase(st.mob));
            if (match) {
                double mult = IaddithisSkilling.getInstance().getConfig()
                        .getDouble("xpConfig.slayer.multiplier", 2.0);
                double xp = Math.round(mob.getMaxHealth() * mult);
                giveXp(killer, "SLAYER", xp);
            }
        }
    }

    // ---------- helpers ----------
    private void sendXp(Player p, double xp, String skill) {
        var data = IaddithisSkilling.getInstance().getData();
        if (!data.getBoolean(p.getUniqueId() + ".settings.xpNotifications", true)) return;
        String suffix = ExpBoostService.isEnabled()
                ? " (x" + (int) Math.round(ExpBoostService.currentMultiplier()) + ")"
                : "";
        p.spigot().sendMessage(
                net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                new net.md_5.bungee.api.chat.TextComponent("✨ +" + (int) xp + " " + skill + " XP" + suffix)
        );
    }

    private static boolean isBlockedByTowny(Cancellable e, Player player, Block block) {
        return e.isCancelled() ||
                (TownyAPI.getInstance().isTownyWorld(block.getWorld())
                        && !TownyActionEventExecutor.canDestroy(player, block));
    }

    // ---------- Mythic util: alleen internal name ophalen ----------
    private static final class MythicUtil {
        static Optional<String> getInternalName(LivingEntity entity) {
            try {
                Class<?> mythicBukkit = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
                Object inst = mythicBukkit.getMethod("inst").invoke(null);
                Object mobManager = inst.getClass().getMethod("getMobManager").invoke(inst);
                Method getActiveMob = mobManager.getClass().getMethod("getActiveMob", UUID.class);
                Object opt = getActiveMob.invoke(mobManager, entity.getUniqueId());
                if (opt instanceof Optional<?> o && o.isPresent()) {
                    Object active = o.get();
                    Object typeObj = active.getClass().getMethod("getType").invoke(active);
                    String internal = String.valueOf(typeObj.getClass().getMethod("getInternalName").invoke(typeObj));
                    if (internal != null && !internal.isEmpty()) return Optional.of(internal);
                }
            } catch (Throwable ignored) {}
            return Optional.empty();
        }
    }
}
