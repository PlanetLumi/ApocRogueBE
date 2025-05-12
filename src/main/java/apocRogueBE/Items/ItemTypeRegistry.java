package apocRogueBE.Items;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Registry for item types on the server side.
 * Loads definitions from JSON on the classpath, keyed by local typeID.
 */
public class ItemTypeRegistry {
    private final Map<String, ItemTypeInfo> infoByTypeID = new HashMap<>();
    private static final Gson GSON = new Gson();

    /**
     * Loads item type definitions from 'data/item_types.json' on the classpath.
     */
    public void load() {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("data/item_types.json");
             InputStreamReader reader = new InputStreamReader(in)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) throw new RuntimeException("Invalid item_types.json format");
            JsonObject obj = root.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                String typeID = entry.getKey();
                ItemTypeInfo info = GSON.fromJson(entry.getValue(), ItemTypeInfo.class);
                infoByTypeID.put(typeID, info);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load item type definitions", e);
        }
    }

    /**
     * Retrieves the ItemTypeInfo for the given local typeID, or null if unknown.
     */
    public ItemTypeInfo getByTypeID(String typeID) {
        return infoByTypeID.get(typeID);
    }

    /**
     * Returns the set of all available local typeIDs.
     */
    public Set<String> getAllTypeIDs() {
        return infoByTypeID.keySet();
    }
}
