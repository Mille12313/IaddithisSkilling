package bloody.devmules.iaddithisSkilling;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.TimeUnit;

public final class ExpBoostService {

    private static boolean enabled;
    private static double multiplier;
    private static long untilEpochMs; // 0 = geen eindtijd
    private static BukkitTask expiryTask;

    private ExpBoostService() {}

    public static void init() {
        FileConfiguration data = IaddithisSkilling.getInstance().getData();
        enabled      = data.getBoolean("expBoost.enabled", false);
        multiplier   = Math.max(0.0, data.getDouble("expBoost.multiplier", 1.0));
        untilEpochMs = data.getLong("expBoost.until", 0L);
        startExpiryWatcher();
        logStatus("loaded");
    }

    public static void shutdown() {
        if (expiryTask != null) {
            expiryTask.cancel();
            expiryTask = null;
        }
    }

    private static void startExpiryWatcher() {
        if (expiryTask != null) expiryTask.cancel();
        expiryTask = Bukkit.getScheduler().runTaskTimer(
                IaddithisSkilling.getInstance(),
                () -> {
                    if (!enabled) return;
                    if (untilEpochMs <= 0) return;
                    if (System.currentTimeMillis() >= untilEpochMs) {
                        setEnabled(false);
                        IaddithisSkilling.getInstance().getLogger().info("[EXP-Boost] expired automatically.");
                    }
                },
                20L, 20L * 30
        );
    }

    public static boolean isEnabled() {
        return enabled && multiplier > 1.0;
    }

    /** Huidige multiplier; 1.0 als uit. */
    public static double currentMultiplier() {
        return isEnabled() ? multiplier : 1.0;
    }

    /** Pas boost toe op basis XP. */
    public static double apply(double baseXp) {
        if (baseXp <= 0) return 0;
        return baseXp * currentMultiplier();
    }

    public static void set(double mult, long minutes) {
        multiplier = Math.max(1.0, mult);
        enabled = multiplier > 1.0;
        untilEpochMs = (minutes > 0) ? System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(minutes) : 0L;
        save();
        startExpiryWatcher();
        logStatus("updated");
    }

    public static void setEnabled(boolean on) {
        enabled = on && multiplier > 1.0;
        save();
        logStatus(on ? "enabled" : "disabled");
    }

    public static long getUntilEpochMs() {
        return untilEpochMs;
    }

    private static void save() {
        var inst = IaddithisSkilling.getInstance();
        FileConfiguration data = inst.getData();
        data.set("expBoost.enabled", enabled);
        data.set("expBoost.multiplier", multiplier);
        data.set("expBoost.until", untilEpochMs);
        inst.saveData();
    }

    private static void logStatus(String reason) {
        var log = IaddithisSkilling.getInstance().getLogger();
        if (!enabled) {
            log.info("[EXP-Boost] " + reason + " → OFF");
            return;
        }
        if (untilEpochMs > 0) {
            long minsLeft = Math.max(0, (untilEpochMs - System.currentTimeMillis()) / 60000);
            log.info("[EXP-Boost] " + reason + " → ON x" + multiplier + " (" + minsLeft + " min left)");
        } else {
            log.info("[EXP-Boost] " + reason + " → ON x" + multiplier + " (no end)");
        }
    }
}
