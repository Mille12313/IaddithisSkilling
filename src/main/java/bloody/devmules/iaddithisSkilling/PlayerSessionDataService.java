package bloody.devmules.iaddithisSkilling;

import org.apache.commons.lang3.RandomStringUtils;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class PlayerSessionDataService {

    // TODO: replace with concurrency-proof impl
    // TODO: when multiple session data fields; allow simple merging/update (perhaps not use record?)
    private static Map<Player, SessionData> playerSessionData = new HashMap<>();

    private static Optional<SessionData> getPlayerSessionData(Player player) {
        return playerSessionData.containsKey(player) ? Optional.of(playerSessionData.get(player)) : Optional.empty();
    }

    private static void setPlayerSessionData(Player player, SessionData sessionData) {
        playerSessionData.put(player, sessionData);
    }


    public static void applyPlayerFishingCaptcha(Player player) {
        String fishingCaptcha = RandomStringUtils.insecure().nextAlphabetic(6).toLowerCase();
        setPlayerSessionData(player, new SessionData(fishingCaptcha));
        player.sendMessage("Your line got tangled! To untangle use: ");
        player.sendMessage("/untangle " + fishingCaptcha);
    }

    public static boolean playerHasFishingCaptcha(Player player) {
        return getPlayerSessionData(player).map(SessionData::hasFishingCaptcha).orElse(false);
    }

    public static String getCurrentFishingCaptcha(Player player) {
        return getPlayerSessionData(player).map(SessionData::fishingCaptcha).orElse(null);
    }

    public static void clearFishingCaptcha(Player p) {
        // TODO: if multiple fields, only clear fishing status
        playerSessionData.remove(p);
    }
}
