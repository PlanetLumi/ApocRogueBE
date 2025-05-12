package apocRogueBE.Items;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
public class ItemTypeInfo {
    /**
     * Ordered list of stat keys for encoding/decoding item IDs.
     * Update this list to match the fields in your item JSON.
     */
    public static final List<String> STAT_KEYS = List.of(
            "healing",
            "duration",
            "power",
            "range"
    );

    public String name;
    public String texturePath;
    public boolean stackable;
    public int     maxStack;

    /**
     * For each stat key, defines the min and max rollable values.
     */
    public Map<String, Range> statRanges = new LinkedHashMap<>();

    /**
     * Represents a min/max range for a stat.
     */
    public static class Range {
        public int min;
        public int max;
    }

    // Getters
    public String getName()        { return name; }
    public String getTexturePath() { return texturePath; }
    public boolean isStackable()   { return stackable; }
    public int     getMaxStack()   { return maxStack; }
}
