package apocRogueBE.Shop;

import apocRogueBE.Items.ItemIDEncoder;
import apocRogueBE.Items.ItemTypeInfo;
import apocRogueBE.Items.ItemTypeRegistry;
import apocRogueBE.Weapons.StatKeys;
import apocRogueBE.Weapons.WeaponData;
import apocRogueBE.Weapons.WeaponIDEncoder;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Generates a deterministic daily shop list of encoded items for a given seller and player.
 *
 * Trader mapping (id → inventory source)
 *  ─ A : Potions  (/Items/potion.json)
 *  ─ B : General items (/Items/items.json)
 *  ─ C : Weapons   (/Weapons/items.json)
 */
public class ShopGenerator {
    private static final Gson GSON = new Gson();

    /*────────────────────────────────  Static data  ────────────────────────────────*/
    private static final Map<String, WeaponData> WEAPON_MAP = loadWeaponData();
    private static final ItemTypeRegistry        ITEM_REGISTRY    = loadItemRegistry();
    private static final ItemTypeRegistry        POTION_REGISTRY  = loadPotionRegistry();

    /*────────────────────────────────  Public API  ────────────────────────────────*/
    public static List<ShopItem> generateShop(Random rng, String sellerId, int playerId) {
        List<String> typeIDs = SellerConfig.typeIDsFor(sellerId);
        List<ShopItem> shop  = new ArrayList<>();

        boolean weaponSeller  = "C".equalsIgnoreCase(sellerId);
        boolean potionSeller  = "A".equalsIgnoreCase(sellerId);
        // default ("B" or anything else) is general‑item seller

        for (String typeID : typeIDs) {
            if (weaponSeller) {
                /*───────────────  Weapon branch  ───────────────*/
                WeaponData wd = Objects.requireNonNull(WEAPON_MAP.get(typeID),
                        () -> "Unknown weapon typeID: " + typeID);

                Map<String,Integer> rolled = rollWeaponStats(rng, wd);
                String code   = WeaponIDEncoder.encode(typeID, 0, 0, rolled);
                int    stock  = 5;
                int    price  = rolled.getOrDefault("damage", 0) * 10;
                shop.add(new ShopItem(code, stock, price));

            } else {
                /*───────────────  Item / Potion branch  ───────────────*/
                ItemTypeRegistry reg = potionSeller ? POTION_REGISTRY : ITEM_REGISTRY;
                ItemTypeInfo info   = Objects.requireNonNull(reg.getByTypeID(typeID),
                        () -> "Unknown item typeID: " + typeID);

                Map<String,Integer> rolled = new HashMap<>();
                for (String stat : info.statRanges.keySet()) {
                    ItemTypeInfo.Range range = info.statRanges.get(stat);
                    int v = range.min + rng.nextInt(range.max - range.min + 1);
                    rolled.put(stat, v);
                }
                String code  = ItemIDEncoder.encode(typeID, rolled);
                int    stock = info.isStackable() ? info.getMaxStack() : 1;
                int    price = computeItemPriceForItem(info, rolled);
                shop.add(new ShopItem(code, stock, price));
            }
        }
        return shop;
    }

    /*────────────────────────────────  Loaders  ────────────────────────────────*/
    private static Map<String, WeaponData> loadWeaponData() {
        final String PATH = "Weapons/items.json";
        try (InputStream raw = Objects.requireNonNull(
                ShopGenerator.class.getClassLoader().getResourceAsStream(PATH),
                "Resource not found on class‑path: " + PATH);
             InputStreamReader rdr = new InputStreamReader(raw)) {

            Type listType = new TypeToken<List<WeaponData>>() {}.getType();
            List<WeaponData> list = GSON.fromJson(rdr, listType);
            Map<String, WeaponData> map = new HashMap<>();
            for (WeaponData wd : list) map.put(wd.typeID, wd);
            return map;

        } catch (Exception e) {
            throw new RuntimeException("Failed loading weapon data", e);
        }
    }

    private static ItemTypeRegistry loadItemRegistry() {
        final String PATH = "Items/items.json";
        return loadRegistryFromJson(PATH);
    }

    private static ItemTypeRegistry loadPotionRegistry() {
        final String PATH = "Items/potion.json";
        return loadRegistryFromJson(PATH);
    }

    /** Helper that deserialises a JSON array of {@link ItemTypeInfo} into a registry. */
    private static ItemTypeRegistry loadRegistryFromJson(String path) {
        try (InputStream raw = Objects.requireNonNull(
                ShopGenerator.class.getClassLoader().getResourceAsStream(path),
                "Resource not found on class-path: " + path);
             InputStreamReader rdr = new InputStreamReader(raw)) {

            JsonObject root = JsonParser.parseReader(rdr).getAsJsonObject();

            ItemTypeRegistry reg = new ItemTypeRegistry();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                String        typeID = e.getKey();                          // "hp-potion-S", "fire-scroll", …
                ItemTypeInfo  info   = GSON.fromJson(e.getValue(), ItemTypeInfo.class);
                reg.register(typeID, info);                                 // <── new helper
            }
            return reg;

        } catch (Exception e) {
            throw new RuntimeException("Failed loading item registry from " + path, e);
        }
    }

    /*────────────────────────────────  Helpers  ────────────────────────────────*/
    private static Map<String, Integer> rollWeaponStats(Random rng, WeaponData wd) {
        Map<String, Integer> m = new HashMap<>();
        for (String key : StatKeys.ALL) {
            int base = wd.getStat(key);
            int val  = base + (base > 0 ? rng.nextInt(base + 1) : 0);
            m.put(key, val);
        }
        return m;
    }

    private static int computeItemPriceForItem(ItemTypeInfo info, Map<String, Integer> rolled) {
        // simple: sum all rolled stats × a factor
        return rolled.values().stream().mapToInt(Integer::intValue).sum() * 2;
    }
}
