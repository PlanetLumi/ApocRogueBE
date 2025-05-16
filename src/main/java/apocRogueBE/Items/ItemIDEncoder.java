package apocRogueBE.Items;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public class ItemIDEncoder {
    private static final String[] HEX = {
            "0","1","2","3","4","5","6","7","8","9",    // 0–9
            "A","B","C","D","E","F","G","H","I","J",    // 10–19
            "K","L","M","N","O","P","Q","R","S","T",    // 20–29
            "U","V","W","X","Y","Z",                    // 30–35
            "a","b","c","d","e","f","g","h","i","j",    // 36–45
            "k","l","m","n","o","p","q","r","s","t",    // 46–55
            "u","v","w","x","y","z"                     // 56–61
    };

    public static String encode(String typeID, Map<String,Integer> stats) {
        StringBuilder sb = new StringBuilder("IT");
        sb.append(typeID);

        List<String> keys = new ArrayList<>(stats.keySet());
        Collections.sort(keys);

        for (String key : keys) {
            int v = stats.getOrDefault(key, 0);
            v = Math.max(0, Math.min(v, 255));
            sb.append(HEX[(v >> 4) & 0xF])
                    .append(HEX[v & 0xF]);
        }
        return sb.toString();
    }
}
