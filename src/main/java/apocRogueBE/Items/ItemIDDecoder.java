package apocRogueBE.Items;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public class ItemIDDecoder {
    public static class Decoded {
        public final String typeID;
        public final Map<String,Integer> stats;
        public Decoded(String typeID, Map<String,Integer> stats) {
            this.typeID = typeID;
            this.stats  = stats;
        }
    }


    public static Decoded decode(String id) {
        if (!id.startsWith("IT") || id.length() < 4) {
            throw new IllegalArgumentException("Invalid item ID: " + id);
        }

        String typeID = id.substring(2, 4);
        String hexPart = id.substring(4);
        int statCount = hexPart.length() / 2;
        List<String> keys = new ArrayList<>(ItemTypeInfo.STAT_KEYS);
        Collections.sort(keys);

        if (statCount > keys.size()) {
            throw new IllegalArgumentException("ID contains more stats than we know: " + id);
        }
        Map<String,Integer> stats = new LinkedHashMap<>();
        int pos = 0;
        for (int i = 0; i < statCount; i++) {
            String key = keys.get(i);
            int hi = fromHex(hexPart.charAt(pos++));
            int lo = fromHex(hexPart.charAt(pos++));
            stats.put(key, (hi << 4) + lo);
        }
        return new Decoded(typeID, stats);
    }

    private static int fromHex(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';                     //  0– 9
        }
        if (c >= 'A' && c <= 'Z') {
            return 10 + (c - 'A');              // 10–35
        }
        if (c >= 'a' && c <= 'z') {
            return 36 + (c - 'a');              // 36–61
        }
        throw new IllegalArgumentException(
                "Invalid hex character: " + c
        );
    }
}
