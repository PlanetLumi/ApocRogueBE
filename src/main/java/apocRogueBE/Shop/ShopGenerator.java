package apocRogueBE.Shop;

import apocRogueBE.Items.ItemIDEncoder;
import apocRogueBE.Items.ItemTypeInfo;
import apocRogueBE.Items.ItemTypeRegistry;
import apocRogueBE.Weapons.StatKeys;
import apocRogueBE.Weapons.WeaponData;
import apocRogueBE.Weapons.WeaponIDEncoder;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Generates a deterministic daily shop list of encoded items for a given seller and player.
 */
public class ShopGenerator {
    private static final Gson GSON = new Gson();
    // Weapon data loaded once
    private static final Map<String, WeaponData> WEAPON_MAP = loadWeaponData();
    // Item data loaded once
    private static final ItemTypeRegistry ITEM_REGISTRY = loadItemRegistry();

    public static List<ShopItem> generateShop(Random rng, String sellerId, int playerId) {
        List<String> typeIDs = SellerConfig.typeIDsFor(sellerId);
        List<ShopItem> shop = new ArrayList<>();

        for (String typeID : typeIDs) {
            WeaponData wd = WEAPON_MAP.get(typeID);
            if (wd != null) {
                // Weapon branch
                Map<String, Integer> rolled = rollWeaponStats(rng, wd);
                String code = WeaponIDEncoder.encode(typeID, 0, 0, rolled);
                int baseStock = 5;
                int price = rolled.getOrDefault("damage", 0) * 10;
                shop.add(new ShopItem(code, baseStock, price));
            } else {
                ItemTypeInfo info = ITEM_REGISTRY.getByTypeID(typeID);
                if (info != null) {
                    // Item branch
                    Map<String, Integer> rolled = new HashMap<>();
                    for (String stat : info.statRanges.keySet()) {
                        ItemTypeInfo.Range range = info.statRanges.get(stat);
                        int v = range.min + rng.nextInt(range.max - range.min + 1);
                        rolled.put(stat, v);
                    }
                    String code = ItemIDEncoder.encode(typeID, rolled);
                    int baseStock = info.isStackable() ? info.getMaxStack() : 1;
                    int price = computeItemPriceForItem(info, rolled);
                    shop.add(new ShopItem(code, baseStock, price));
                } else {
                    throw new IllegalArgumentException("Unknown typeID: " + typeID);
                }
            }
        }
        return shop;
    }

    private static Map<String, WeaponData> loadWeaponData() {
        try (InputStream in = ShopGenerator.class
                .getClassLoader()
                .getResourceAsStream("/Items/items.json")) {
            Type listType = new TypeToken<List<WeaponData>>() {}.getType();
            List<WeaponData> list = GSON.fromJson(new InputStreamReader(in), listType);
            Map<String, WeaponData> map = new HashMap<>();
            for (WeaponData wd : list) map.put(wd.typeID, wd);
            return map;
        } catch (Exception e) {
            throw new RuntimeException("Failed loading weapon data", e);
        }
    }

    private static ItemTypeRegistry loadItemRegistry() {
        ItemTypeRegistry reg = new ItemTypeRegistry();
        reg.load();
        return reg;
    }

    private static Map<String, Integer> rollWeaponStats(Random rng, WeaponData wd) {
        Map<String, Integer> m = new HashMap<>();
        for (String key : StatKeys.ALL) {
            int base = wd.getStat(key);
            int val = base + (base > 0 ? rng.nextInt(base + 1) : 0);
            m.put(key, val);
        }
        return m;
    }

    private static int computeItemPriceForItem(ItemTypeInfo info, Map<String, Integer> rolled) {
        // simple: sum all rolled stats × a factor
        return rolled.values().stream().mapToInt(Integer::intValue).sum() * 2;
    }
}
