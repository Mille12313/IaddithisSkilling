package bloody.devmules.iaddithisSkilling;

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Slayer progress via MythicMobs death event.
 * Register this listener ONLY when MythicMobs is present (see onEnable()).
 */
public class SlayerKillListenerMM implements Listener {

    @EventHandler
    public void onMythicMobDeath(MythicMobDeathEvent e) {
        if (e.getKiller() == null || !(e.getKiller() instanceof Player p)) return;
        if (p.getGameMode() != GameMode.SURVIVAL) return;

        String internal = e.getMob().getType().getInternalName();

        var st = SlayerService.getState(p.getUniqueId());
        if (st.id == null || st.mob == null || st.remaining <= 0) return;
        if (!internal.equalsIgnoreCase(st.mob)) return;

        st.remaining -= 1;
        if (st.remaining > 0) {
            SlayerService.saveState(p.getUniqueId(), st);
            p.sendMessage("§aSlayer: §f" + internal + " killed. §e" + st.remaining + "§f to go.");
            return;
        }

        // Task complete
        st.streak += 1;
        int base = SlayerService.basePointsPerTask(new SlayerService.SlayerTask(st.id, st.mob, 0, null));
        int payout = SlayerService.computePayoutWithStreak(base, st.streak, false);
        st.points += payout;

        p.sendMessage("§aSlayer task completed! §7(" + st.id + ") §a+§e" + payout + " §apoints. Streak: §e" + st.streak);

        // Clear current task
        st.id = null; st.mob = null; st.remaining = 0;
        SlayerService.saveState(p.getUniqueId(), st);
    }
}
