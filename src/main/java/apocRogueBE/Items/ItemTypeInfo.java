package apocRogueBE.Items;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
public class ItemTypeInfo {

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


    public Map<String, Range> statRanges = new LinkedHashMap<>();


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
