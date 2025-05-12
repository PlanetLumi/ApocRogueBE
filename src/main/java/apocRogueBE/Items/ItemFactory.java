// File: apocRogueBE.Items/ItemFactory.java
package apocRogueBE.Items;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Rolls each stat according to the ranges in ItemTypeInfo, then encodes into an ID.
 */
public class ItemFactory {
    private static final Random RNG = new Random();

    /**
     * Roll and encode a new itemCode for the given type.
     *
     * @param typeID     the 2-char item type identifier (e.g. "10", "P1")
     * @param statRanges map of statKey → Range(min,max)
     * @return a deterministic-looking itemCode string (prefixed "IT")
     */
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
