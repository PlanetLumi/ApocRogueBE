package apocRogueBE.Items;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class ItemFactory {
    private static final Random RNG = new Random();


    public static String rollAndEncode(
            String typeID,
            Map<String, ItemTypeInfo.Range> statRanges
    ) {
        Map<String,Integer> rolled = new HashMap<>();
        for (var entry : statRanges.entrySet()) {
            String key   = entry.getKey();
            ItemTypeInfo.Range r = entry.getValue();
            int min = r.min, max = r.max;
            int v = min + (max > min ? RNG.nextInt(max - min + 1) : 0);
            rolled.put(key, v);
        }
        return ItemIDEncoder.encode(typeID, rolled);
    }
}
