package apocRogueBE.Weapons;

import java.util.Map;

public class WeaponIDEncoder {
    private static final String[] HEX = {
            "0","1","2","3","4","5","6","7","8","9",    // 0–9
            "A","B","C","D","E","F","G","H","I","J",    // 10–19
            "K","L","M","N","O","P","Q","R","S","T",    // 20–29
            "U","V","W","X","Y","Z",                    // 30–35
            "a","b","c","d","e","f","g","h","i","j",    // 36–45
            "k","l","m","n","o","p","q","r","s","t",    // 46–55
            "u","v","w","x","y","z"                     // 56–61
    };

    public static String encode(
        String typeID,
        int skullLevel, int skullSub,
        Map<String,Integer> stats
    ) {
        StringBuilder sb = new StringBuilder("ID");
        sb.append(typeID)
            .append(HEX[skullLevel]).append(HEX[skullSub]);

        for (String key : StatKeys.ALL) {
            int v = stats.getOrDefault(key, 0);

            v = Math.max(0, Math.min(v, 255));
            sb.append( HEX[(v>>4)&0xF] )
                .append( HEX[v & 0xF] );
        }
        return sb.toString();
    }
}
