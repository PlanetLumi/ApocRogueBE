package apocRogueBE.WorldGen;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.gson.Gson;
import apocRogueBE.Items.*;
import apocRogueBE.Security.AuthHelper;
import apocRogueBE.SingletonConnection.DataSourceSingleton;
import apocRogueBE.Weapons.WeaponData;
import apocRogueBE.Weapons.WeaponIDEncoder;
import apocRogueBE.Weapons.StatKeys;
import static apocRogueBE.Shop.ShopGenerator.WEAPON_MAP;

import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class LevelItemGenerate implements HttpFunction {
    private static final Gson GSON = new Gson();
    private static final ItemTypeRegistry ITEM_REGISTRY = new ItemTypeRegistry();
    static { ITEM_REGISTRY.load(); }

    static class GenerateRequest {
        public int difficulty;
        public int subLevel;
        public int radiation;
        public int count;
        public float chestX;
        public float chestY;
    }
    static class GenerateResponse {
        public String itemCode;
        public Map<String,Integer> stats;
    }

    @Override public void service(HttpRequest req, HttpResponse resp) throws Exception {
        resp.setContentType("application/json");
        BufferedWriter out = resp.getWriter();

        // 1) authenticate and get player ID
        int playerId;
        try (Connection c = DataSourceSingleton.getConnection()) {
            playerId = AuthHelper.requirePlayerId(req, c);
        }

        // 2) parse request
        GenerateRequest g = GSON.fromJson(new InputStreamReader(req.getInputStream()), GenerateRequest.class);
        if (g == null) { resp.setStatusCode(400); out.write("{\"error\":\"bad json\"}"); return; }
        int drops = Math.max(1, Math.min(g.count, 5));

        // 3) compute deterministic seed
        long seed = hashSeed(g.difficulty, g.subLevel, g.radiation,
                LocalDate.now().toString(), g.chestX, g.chestY);
        Random rng = new Random(seed);

        // 4) roll loot
        List<GenerateResponse> loot = new ArrayList<>();
        for (int i = 0; i < drops; i++) {
            boolean rollWeapon = rng.nextBoolean();
            if (rollWeapon) {
                String wType = pickWeaponTypeForLevel(rng);
                WeaponData wd = WEAPON_MAP.get(wType);
                Map<String,Integer> rolled = rollWeaponStats(rng, wd, g.difficulty, g.radiation);
                String code = WeaponIDEncoder.encode(wType, g.difficulty, g.subLevel, rolled);
                GenerateResponse gr = new GenerateResponse(); gr.itemCode = code; gr.stats = rolled;
                loot.add(gr);
            } else {
                String iType = pickItemTypeForLevel(rng);
                Map<String,Integer> rolled = rollItemStats(rng, ITEM_REGISTRY.getByTypeID(iType), g.difficulty, g.radiation);
                String code = ItemIDEncoder.encode(iType, rolled);
                GenerateResponse gr = new GenerateResponse(); gr.itemCode = code; gr.stats = rolled;
                loot.add(gr);
            }
        }

        // 5) persist each generated code to SQL table `world_loot`
        try (Connection conn = DataSourceSingleton.getConnection()) {
            String sql = "INSERT INTO world_loot(player_id, item_code) VALUES (?, ?) ON CONFLICT DO NOTHING";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (GenerateResponse gr : loot) {
                    ps.setLong(1, playerId);
                    ps.setString(2, gr.itemCode);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }

        // 6) return loot list to client
        out.write(GSON.toJson(loot));
    }

    // helper methods unchanged from original
    private String pickWeaponTypeForLevel(Random rng) {
        List<String> ids = new ArrayList<>(WEAPON_MAP.keySet());
        return ids.get(rng.nextInt(ids.size()));
    }
    private String pickItemTypeForLevel(Random rng) {
        List<String> ids = new ArrayList<>(ITEM_REGISTRY.getAllTypeIDs());
        return ids.get(rng.nextInt(ids.size()));
    }

    /* ─────────── helper: stat rollers ──────────────────────────────────── */
    private Map<String,Integer> rollWeaponStats(Random rng, WeaponData wd, int diff, int rad) {
        double scale = 1 + diff*0.4 + rad*0.01;
        Map<String,Integer> m = new HashMap<>();
        for (String k : StatKeys.ALL) {
            int base = wd.getStat(k);
            int max  = (int)Math.round(base * scale);
            int val  = base + (max>base ? rng.nextInt(max-base+1) : 0);
            m.put(k, val);
        }
        return m;
    }
    private Map<String,Integer> rollItemStats(Random rng, ItemTypeInfo info, int diff, int rad) {
        double scale = 1 + diff*0.3 + rad*0.01;
        Map<String,Integer> m = new HashMap<>();
        for (var e : info.statRanges.entrySet()) {
            int min = (int)Math.round(e.getValue().min * scale);
            int max = (int)Math.round(e.getValue().max * scale);
            int val = min + (max>min ? rng.nextInt(max-min+1) : 0);
            m.put(e.getKey(), val);
        }
        return m;
    }


    /* ─────────── seed mixer ─────────────────────────────── */
    private long hashSeed(int d, int s, int r, String date, float x, float y){
        SecureRandom sr = new SecureRandom();
        long h =sr.nextLong();   // prime seed
        h = 31*h + d;
        h = 31*h + s;
        h = 31*h + r;
        h = (long) (31*h + x);
        h = (long) (31*h + y);
        h = 31*h + date.hashCode();
        return h;
    }
}
