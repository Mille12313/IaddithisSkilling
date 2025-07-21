package bloody.devmules.iaddithisSkilling;

import java.util.Random;

public class ChanceUtil {

    public static boolean isRandomChance(int oneInX) {
        return new Random().nextInt(1, oneInX + 1) == oneInX;
    }

}
