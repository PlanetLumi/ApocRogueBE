package apocRogueBE.Weapons;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class WeaponFactory {
    private static final Random RNG = new Random();

    public static String rollAndEncode(
        String typeID,
        Map<String,Integer> baseValues,
        int skullLevel,
        int skullSub
    ) {
        int diffScore = skullLevel * 5 + skullSub;

        Map<String,Integer> rolled = new HashMap<>();
        for (String key : StatKeys.ALL) {
            int base = baseValues.getOrDefault(key, 0);
            int max = Math.max(base, base * diffScore);
            int val = base + (max>base ? RNG.nextInt(max-base+1) : 0);
            rolled.put(key, val);
        }

        return WeaponIDEncoder.encode(typeID, skullLevel, skullSub, rolled);
    }
}
