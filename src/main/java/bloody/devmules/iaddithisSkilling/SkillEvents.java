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
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.EventExecutor;

import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Level;

/**
 * XP-logic:
 *  - Blocks/crops → mining/woodcutting/farming (config)
 *  - Fishing/Exploration/Sailing → config
 *  - Combat per hit:
 *      * NIET geven als target een MythicMob is die naar NIET-COMBAT gemapt is
 *      * WEL geven voor COMBAT-gemapte of niet-gemapte mobs
 *  - Einde MythicMob (death/despawn):
 *      * NIET-COMBAT gemapt → 1× skilling XP (killer of laatste hitter)
 *      * COMBAT gemapt → geen extra death-award (per hit al gedaan)
 *      * Niet gemapt → SLAYER XP alleen on-task + match
 */
public class SkillEvents implements Listener {
    public static final int DEFAULT_FISHING_DROP_CHANCE = 5;

    private final Map<UUID, Location> lastStep = new HashMap<>();
    private final Map<UUID, Integer> stepCount = new HashMap<>();
    private final Map<UUID, Location> lastBoat = new HashMap<>();
    private final Map<UUID, Integer> boatCount = new HashMap<>();

    // laatste hitter (voor suicide/remove zonder killer)
    private final Map<UUID, UUID> lastHitBy = new HashMap<>();
    private final Map<UUID, Long> lastHitAt = new HashMap<>();
    private static final long LAST_HIT_TIMEOUT_MS = 10_000L;

    // dubbele toekenning voorkomen
    private final Set<UUID> alreadyAwarded = Collections.newSetFromMap(new WeakHashMap<>());

    private static final Set<EntityType> EXCLUDED_ENTITIES =
            EnumSet.of(EntityType.WOLF, EntityType.HORSE, EntityType.ARMOR_STAND);

    public SkillEvents() {
        // hook Mythic events via reflection (werkt ook zonder compile-dep)
        MythicBridge.tryRegister(this);
    }

    // ====== Helper: geef XP met boost + popup ======
    private void giveXp(Player p, String skill, double baseXp) {
        if (p == null || baseXp <= 0) return;
        double eff = ExpBoostService.apply(baseXp);
        if (eff <= 0) return;
        SkillManager.addXP(p, skill, eff);
        sendXp(p, eff, skill);
    }

    // -------------------- BLOCK BREAK (VANILLA) --------------------
    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (isBlockedByTowny(e, e.getPlayer(), e.getBlock())) return;
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SURVIVAL) return;

        // Silk Touch => geen XP
        if (p.getInventory().getItemInMainHand() != null &&
                p.getInventory().getItemInMainHand().containsEnchantment(Enchantment.SILK_TOUCH)) {
            return;
        }

        FileConfiguration cfg = IaddithisSkilling.getInstance().getConfig();
        ConfigurationSection xpCfg = cfg.getConfigurationSection("xpConfig");
        if (xpCfg == null) return;

        String type = e.getBlock().getType().name();

        // Mining
        ConfigurationSection mineSec = xpCfg.getConfigurationSection("mining");
        if (mineSec != null && mineSec.isDouble(type)) {
            double xp = mineSec.getDouble(type);
            giveXp(p, "MINING", xp);
        }

        // Woodcutting
        ConfigurationSection woodSec = xpCfg.getConfigurationSection("woodcutting");
        if (woodSec != null && woodSec.isDouble(type)) {
            double xp = woodSec.getDouble(type);
            giveXp(p, "WOODCUTTING", xp);
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
                giveXp(p, "FARMING", xp);
            }
        }
    }

    // -------------------- ITEMSADDER CROPS --------------------
    @EventHandler
    public void onCustomBlockBreak(CustomBlockBreakEvent event) {
        isBlockedByTowny(event, event.getPlayer(), event.getBlock());
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

        // <crop>_stage_<n>
        if (nameArray.length < 3) return;
        if (!nameArray[1].equalsIgnoreCase("stage")) return;

        int stageNum;
        try { stageNum = Integer.parseInt(nameArray[2]); }
        catch (NumberFormatException ex) { return; }

        String configKey = customBlockID.toUpperCase();

        if (farmSec.isDouble(configKey)) {
            if ((nameArray[0].equalsIgnoreCase("grape") && stageNum == 6)
                    || (!nameArray[0].equalsIgnoreCase("grape") && stageNum == 4)) {
                double xp = farmSec.getDouble(configKey);
                giveXp(p, "FARMING", xp);
            }
        }
    }

    // -------------------- COMBAT DAMAGE --------------------
    @EventHandler
    public void onCombat(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) return;
        if (!(e.getDamager() instanceof Player p)) return;
        if (p.getGameMode() != GameMode.SURVIVAL) return;
        if (e.getFinalDamage() <= 0) return;
        if (CombatUtil.preventDamageCall(e.getDamager(), e.getEntity(), e.getCause())) return;
        if (e.getEntity() instanceof Player) return;
        if (!(e.getEntity() instanceof LivingEntity target) || EXCLUDED_ENTITIES.contains(target.getType())) return;

        Optional<MobMap> mapped = MythicBridge.getMythicMapping(target);
        if (mapped.isPresent()) {
            MobMap mm = mapped.get();

            // onthoud laatste hitter voor suicide/remove
            lastHitBy.put(target.getUniqueId(), p.getUniqueId());
            lastHitAt.put(target.getUniqueId(), System.currentTimeMillis());

            if (!mm.skill.equalsIgnoreCase("COMBAT")) {
                // skilling nodes → geen per-hit combat-XP
                return;
            }
            // COMBAT-gemapte MythicMob → wél combat per hit hieronder
        }

        // niet-gemapte → normale combat per hit
        double xp = Math.round(e.getFinalDamage() * 2.0);
        if (xp > 0) {
            giveXp(p, "COMBAT", xp);
        }
    }

    // -------------------- FISHING --------------------
    @EventHandler
    public void onFish(PlayerFishEvent e) {
        if (e.isCancelled()) return;
        if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            onSuccesfulCatch(e);
        } else if (e.getState() == PlayerFishEvent.State.FISHING) {
            if (afkProtectionDrop(e)) return;
        }
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
                || caughtType == EntityType.PUFFERFISH) cat = "fish";
        else if (caughtType == EntityType.DOLPHIN)    cat = "treasure";
        else                                          cat = "junk";

        ConfigurationSection fishCfg = IaddithisSkilling.getInstance()
                .getConfig().getConfigurationSection("xpConfig.fishing");
        if (fishCfg == null) return;

        double xp = fishCfg.getDouble(cat, 0.0);
        if (xp > 0) {
            giveXp(p, "FISHING", xp);
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

        ConfigurationSection fishDropCfg = IaddithisSkilling.getInstance()
                .getConfig().getConfigurationSection("other");
        if (fishDropCfg == null) return true;
        int chance = fishDropCfg.getInt("fishing-captcha-chance", DEFAULT_FISHING_DROP_CHANCE);
        if (chance > 0 && ChanceUtil.isRandomChance(chance)) {
            IaddithisSkilling.getInstance().getLogger().log(Level.INFO,
                    "Player " + player.getName() + " has tangled their rod!");
            e.setCancelled(true);
            PlayerSessionDataService.applyPlayerFishingCaptcha(player);
            return true;
        }
        return false;
    }

    // -------------------- EXPLORATION / SAILING --------------------
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
                    giveXp(p, "SAILING", xp);
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
                    double xp = xpCfg.getDouble("exploring.xpPerUnit", 0.0);
                    giveXp(p, "EXPLORATION", xp);
                    cnt = 0;
                }
                stepCount.put(u, cnt);
                lastStep.put(u, loc);
            }
        }
    }

    // -------------------- BUKKIT DEATH (fallback; niet-Mythic of geen MM-events) --------------------
    @EventHandler
    public void onMobDeath(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof LivingEntity mob)) return;
        if (mob instanceof Player) return;
        if (EXCLUDED_ENTITIES.contains(mob.getType())) return;

        if (alreadyAwarded.contains(mob.getUniqueId())) return; // al via Mythic event afgehandeld

        Player killer = mob.getKiller();

        // Mythic mapping?
        Optional<MobMap> mapped = MythicBridge.getMythicMapping(mob);
        if (mapped.isPresent()) {
            MobMap mm = mapped.get();
            if (mm.skill.equalsIgnoreCase("COMBAT")) {
                // combat per hit; geen bonus
                return;
            }
            // NIET-COMBAT → 1× skill XP aan killer of laatste hitter
            UUID awardTo = (killer != null && killer.getGameMode() == GameMode.SURVIVAL)
                    ? killer.getUniqueId()
                    : resolveLastHitter(mob.getUniqueId());
            if (awardTo != null && mm.xp > 0) {
                Player p = Bukkit.getPlayer(awardTo);
                if (p != null) {
                    String skill = mm.skill.toUpperCase(Locale.ROOT);
                    giveXp(p, skill, mm.xp);
                }
            }
            alreadyAwarded.add(mob.getUniqueId());
            cleanupHitMemory(mob.getUniqueId());
            return;
        }

        // Niet gemapt → Slayer alleen on-task + match
        Player slayerPlayer = killer;
        if (slayerPlayer != null && slayerPlayer.getGameMode() == GameMode.SURVIVAL) {
            SlayerService.SlayerState st = SlayerService.getState(slayerPlayer.getUniqueId());
            if (st != null && st.id != null && st.mob != null && matchesSlayerTaskMob(mob, st.mob)) {
                double mult = IaddithisSkilling.getInstance().getConfig()
                        .getDouble("xpConfig.slayer.multiplier", 2.0);
                double xp = Math.round(mob.getMaxHealth() * mult);
                giveXp(slayerPlayer, "SLAYER", xp);
            }
        }
    }

    // -------------------- Helpers --------------------
    private UUID resolveLastHitter(UUID entityId) {
        Long at = lastHitAt.get(entityId);
        if (at == null) return null;
        if (System.currentTimeMillis() - at > LAST_HIT_TIMEOUT_MS) return null;
        return lastHitBy.get(entityId);
    }

    private void cleanupHitMemory(UUID entityId) {
        lastHitAt.remove(entityId);
        lastHitBy.remove(entityId);
    }

    private boolean matchesSlayerTaskMob(LivingEntity mob, String taskMob) {
        if (taskMob == null || taskMob.isEmpty()) return false;
        Optional<MobMap> mm = MythicBridge.getMythicMapping(mob);
        if (mm.isPresent()) {
            return taskMob.equalsIgnoreCase(mm.get().internalName);
        }
        return mob.getType().name().equalsIgnoreCase(taskMob);
    }

    private void sendXp(Player p, double xp, String skill) {
        var data = IaddithisSkilling.getInstance().getData();
        boolean notif = data.getBoolean(p.getUniqueId() + ".settings.xpNotifications", true);
        if (notif) {
            String suffix = ExpBoostService.isEnabled()
                    ? " (x" + (int) Math.round(ExpBoostService.currentMultiplier()) + ")"
                    : "";
            p.spigot().sendMessage(
                    net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    new net.md_5.bungee.api.chat.TextComponent("✨ +" + (int) xp + " " + skill + " XP" + suffix)
            );
        }
    }

    private static boolean isBlockedByTowny(Cancellable e, Player player, Block block) {
        return e.isCancelled() ||
                (TownyAPI.getInstance().isTownyWorld(block.getWorld())
                        && !TownyActionEventExecutor.canDestroy(player, block));
    }

    // ===== Mythic hook via reflection (geen compile-dep nodig) =====
    private static final class MobMap {
        final String internalName, skill;
        final double xp;
        MobMap(String internalName, String skill, double xp) {
            this.internalName = internalName; this.skill = skill; this.xp = xp;
        }
    }

    private static final class MythicBridge {
        private static boolean registered = false;
        private static Listener ownerListener;

        static void tryRegister(Listener owner) {
            if (registered) return;
            ownerListener = owner;
            try {
                // check presence
                Class<?> mythicBukkitCls = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
                Object mythic = mythicBukkitCls.getMethod("inst").invoke(null);
                if (mythic == null) return;

                // MythicMobDeathEvent
                register("io.lumine.mythic.bukkit.events.MythicMobDeathEvent", (listener, event) -> {
                    try {
                        Entity bukkitEntity = (Entity) event.getClass().getMethod("getEntity").invoke(event);
                        if (!(bukkitEntity instanceof LivingEntity mob)) return;

                        Player killer = (Player) event.getClass().getMethod("getKiller").invoke(event);

                        Optional<MobMap> mapped = getMythicMapping(mob);
                        if (mapped.isEmpty()) return;

                        MobMap mm = mapped.get();
                        if (mm.skill.equalsIgnoreCase("COMBAT")) return; // combat per hit al gedaan

                        UUID awardTo = (killer != null && killer.getGameMode() == GameMode.SURVIVAL)
                                ? killer.getUniqueId()
                                : ((SkillEvents) listener).resolveLastHitter(mob.getUniqueId());
                        if (awardTo != null && mm.xp > 0) {
                            Player p = Bukkit.getPlayer(awardTo);
                            if (p != null) {
                                String s = mm.skill.toUpperCase(Locale.ROOT);
                                ((SkillEvents) listener).giveXp(p, s, mm.xp);
                            }
                        }
                        ((SkillEvents) listener).alreadyAwarded.add(mob.getUniqueId());
                        ((SkillEvents) listener).cleanupHitMemory(mob.getUniqueId());
                    } catch (Throwable ignored) {}
                });

                // MythicMobDespawnEvent (cover remove{} / plugin skills)
                register("io.lumine.mythic.bukkit.events.MythicMobDespawnEvent", (listener, event) -> {
                    try {
                        Entity bukkitEntity = (Entity) event.getClass().getMethod("getEntity").invoke(event);
                        if (!(bukkitEntity instanceof LivingEntity mob)) return;

                        Object reason = event.getClass().getMethod("getReason").invoke(event);
                        String rs = String.valueOf(reason).toUpperCase(Locale.ROOT);
                        // alleen skill/plugin/remove-achtige despawns belonen
                        if (!(rs.contains("SKILL") || rs.contains("PLUGIN") || rs.contains("REMOVE"))) return;

                        Optional<MobMap> mapped = getMythicMapping(mob);
                        if (mapped.isEmpty()) return;

                        MobMap mm = mapped.get();
                        if (mm.skill.equalsIgnoreCase("COMBAT")) return;

                        UUID awardTo = ((SkillEvents) listener).resolveLastHitter(mob.getUniqueId());
                        if (awardTo != null && mm.xp > 0) {
                            Player p = Bukkit.getPlayer(awardTo);
                            if (p != null) {
                                String s = mm.skill.toUpperCase(Locale.ROOT);
                                ((SkillEvents) listener).giveXp(p, s, mm.xp);
                            }
                        }
                        ((SkillEvents) listener).alreadyAwarded.add(mob.getUniqueId());
                        ((SkillEvents) listener).cleanupHitMemory(mob.getUniqueId());
                    } catch (Throwable ignored) {}
                });

                registered = true;
                IaddithisSkilling.getInstance().getLogger().info("[SkillEvents] Mythic hooks registered.");
            } catch (Throwable ignored) {
                // MythicMobs niet aanwezig; prima
            }
        }

        private static void register(String eventClassName, EventExecutor exec) throws Exception {
            Class<? extends Event> clazz = Class.forName(eventClassName).asSubclass(Event.class);
            Bukkit.getPluginManager().registerEvent(
                    clazz,
                    ownerListener != null ? ownerListener : new Listener(){},
                    EventPriority.NORMAL,
                    exec,
                    IaddithisSkilling.getInstance(),
                    true
            );
        }

        static Optional<MobMap> getMythicMapping(LivingEntity entity) {
            try {
                Class<?> mythicBukkitCls = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
                Object mythic = mythicBukkitCls.getMethod("inst").invoke(null);
                Object mobManager = mythic.getClass().getMethod("getMobManager").invoke(mythic);

                Method getActiveMob = mobManager.getClass().getMethod("getActiveMob", UUID.class);
                Object opt = getActiveMob.invoke(mobManager, entity.getUniqueId());
                if (opt instanceof Optional<?> o && o.isPresent()) {
                    Object active = o.get();
                    Object typeObj = active.getClass().getMethod("getType").invoke(active);
                    String internal = String.valueOf(typeObj.getClass().getMethod("getInternalName").invoke(typeObj));

                    FileConfiguration cfg = IaddithisSkilling.getInstance().getConfig();
                    String base = "xpConfig.mythicMobs." + internal;
                    if (cfg.isConfigurationSection(base)) {
                        String skill = cfg.getString(base + ".skill", "SLAYER");
                        double xp = cfg.getDouble(base + ".xp", 0.0);
                        return Optional.of(new MobMap(internal, skill, xp));
                    }
                }
            } catch (Throwable ignored) { }
            return Optional.empty();
        }
    }
}
