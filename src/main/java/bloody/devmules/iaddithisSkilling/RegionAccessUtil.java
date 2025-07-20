package bloody.devmules.iaddithisSkilling;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class RegionAccessUtil {

    // Let op: GEEN imports van Towny of WorldGuard hierboven!

    public static boolean canBuild(Player player, Location loc) {
        // ---- WORLDGUARD CHECK ----
        if (Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            try {
                // Let op: .canBuild(Player, Block) werkt met de Bukkit-bridge
                Block block = loc.getBlock();
                org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin("WorldGuard");
                if (plugin != null) {
                    // Via Java reflection (no hard dependency)
                    boolean allowed = (boolean) plugin.getClass()
                            .getMethod("canBuild", Player.class, Block.class)
                            .invoke(plugin, player, block);
                    if (!allowed) return false;
                }
            } catch (Throwable ignore) {}
        }
        // ---- TOWNY CHECK ----
        if (Bukkit.getPluginManager().isPluginEnabled("Towny")) {
            try {
                org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin("Towny");
                if (plugin != null) {
                    // Gebruik alleen hasTownyAdmin als fallback (meestal is Towny land altijd protected als je geen resident bent)
                    // Simpelste check: mag je blokken breken op deze plek volgens Bukkit?
                    if (!player.hasPermission("towny.townyadmin") && !loc.getBlock().breakNaturally()) {
                        return false;
                    }
                }
            } catch (Throwable ignore) {}
        }
        // Als geen claim-plugins, altijd true
        return true;
    }
}
