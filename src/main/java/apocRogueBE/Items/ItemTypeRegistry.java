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


public class ItemTypeRegistry {
    private final Map<String, ItemTypeInfo> infoByTypeID = new HashMap<>();
    private static final Gson GSON = new Gson();

    public void load() {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("Items/item.json");
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


    public ItemTypeInfo getByTypeID(String typeID) {
        return infoByTypeID.get(typeID);
    }


    public Set<String> getAllTypeIDs() {
        return infoByTypeID.keySet();
    }
    public void register(String typeID, ItemTypeInfo info) {
        infoByTypeID.put(typeID, info);
    }
}
