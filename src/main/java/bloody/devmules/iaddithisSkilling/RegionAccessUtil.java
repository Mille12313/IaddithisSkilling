import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.TownBlock;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class RegionAccessUtil {

    public static boolean canBuild(Player player, Location loc) {
        TownBlock block = TownyAPI.getInstance().getTownBlock(loc);
        if (block != null) {
            // Speler is geen mayor, resident, vriend of ally -> geen build recht!
            try {
                // Dit is simplified! Je kan rechten verder uitsplitsen via Towny methods:
                if (!block.getTown().hasResident(TownyAPI.getInstance().getDataSource().getResident(player.getName()))) {
                    return false;
                }
            } catch (Exception ex) {
                return false;
            }
        }
        // Geen claim? Gewoon true
        return true;
    }
}
