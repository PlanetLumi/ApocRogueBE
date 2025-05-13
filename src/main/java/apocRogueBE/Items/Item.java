package apocRogueBE.Items;


import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents an item instance in the back-end domain.
 * This class holds only the ID, metadata stats, and basic flags;
 * visual data (textures) are handled in the front end.
 */
public class Item {
    private final String id;
    private final Map<String,Integer> stats = new LinkedHashMap<>();

    private final String name;
    private final String texturePath;
    private final boolean stackable;
    private final int maxStack;

    public Item(String id,
                String name,
                String texturePath,
                boolean stackable,
                int maxStack) {
        this.id           = id;
        this.name         = name;
        this.texturePath  = texturePath;
        this.stackable    = stackable;
        this.maxStack     = maxStack;

        stats.put("stackable", stackable ? 1 : 0);
        stats.put("maxStack", maxStack);
    }

    public String getID()           { return id; }
    public String getName()         { return name; }
    public String getTexturePath()  { return texturePath; }
    public boolean isStackable()    { return stackable; }
    public int getMaxStack()        { return maxStack; }
    public Map<String,Integer> getStats() { return stats; }



}

