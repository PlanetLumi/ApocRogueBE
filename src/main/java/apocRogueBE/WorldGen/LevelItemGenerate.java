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

import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/* gain access to WEAPON_MAP that ShopGenerator already builds */
import static apocRogueBE.Shop.ShopGenerator.WEAPON_MAP;

/**
 * Drop‑in Cloud Function that rolls <em>deterministic</em> loot for a
 * level chest or mob kill based on <code>(difficulty, subLevel, radiation)</code>.
 * <p>Rolls can be verified offline by decoding <code>itemCode</code>
 * and re‑computing the seed, giving you a built‑in tamper check.</p>
 */
public class LevelItemGenerate implements HttpFunction {

    /* ────────────────────────────── static look‑ups ────────────────────── */
    private static final Gson GSON = new Gson();

    private static final ItemTypeRegistry ITEM_REGISTRY = new ItemTypeRegistry();
    static { ITEM_REGISTRY.load(); }

    private static Map<String, ItemTypeInfo> ITEM_TYPES() {
        return ITEM_REGISTRY.getAllTypeIDs().stream()
                .collect(Collectors.toMap(id -> id, ITEM_REGISTRY::getByTypeID));
    }

    /* ─────────────────────────────── request / response ───────────────── */
    static class GenerateRequest {
        public int    difficulty;   // e.g. world tier 0‑5
        public int    subLevel;     // 1‑5 inside that tier
        public int    radiation;    // 0‑100 env bonus
        public int    count;        // how many drops to roll (1‑5 recommended)
        public float  chestX;
        public float  chestY;
    }
    static class GenerateResponse {
        public String               itemCode;
        public Map<String,Integer>  stats;
    }

    /* ─────────────────────────── HttpFunction entry ‑point ────────────── */
    @Override public void service(HttpRequest req, HttpResponse resp) throws Exception {
        resp.setContentType("application/json");
        BufferedWriter out = resp.getWriter();

        /* 1 ── auth */
        int playerId;
        try (Connection c = DataSourceSingleton.getConnection()) {
            playerId = AuthHelper.requirePlayerId(req, c);
        }

        /* 2 ── parse JSON */
        GenerateRequest g = GSON.fromJson(new InputStreamReader(req.getInputStream()), GenerateRequest.class);
        if (g == null) { resp.setStatusCode(400); out.write("{\"error\":\"bad json\"}"); return; }
        int drops = Math.max(1, Math.min(g.count, 5));

        /* 3 ── deterministic seed */
        long seed = hashSeed(
                            g.difficulty, g.subLevel, g.radiation,
                            LocalDate.now().toString(),
                            g.chestX, g.chestY
                                );
        Random rng = new Random(seed);

        List<GenerateResponse> loot = new ArrayList<>();

        try (Connection conn = DataSourceSingleton.getConnection()) {

            for (int i = 0; i < drops; i++) {
                boolean rollWeapon = rng.nextBoolean();

                if (rollWeapon) {
                    /* ‑‑‑ Weapon branch ‑‑‑ */
                    String wType = pickWeaponTypeForLevel(rng);
                    WeaponData wd = WEAPON_MAP.get(wType);
                    Map<String,Integer> rolled = rollWeaponStats(rng, wd, g.difficulty, g.radiation);
                    String code = WeaponIDEncoder.encode(wType, g.difficulty, g.subLevel, rolled);
                    GenerateResponse gr = new GenerateResponse();
                    gr.itemCode = code;
                    gr.stats    = rolled;
                    loot.add(gr);

                } else {
                    /* ‑‑‑ Item / consumable branch ‑‑‑ */
                    String iType = pickItemTypeForLevel(rng);
                    ItemTypeInfo info = ITEM_TYPES().get(iType);
                    Map<String,Integer> rolled = rollItemStats(rng, info, g.difficulty, g.radiation);
                    String code = ItemIDEncoder.encode(iType, rolled);
                    GenerateResponse gr = new GenerateResponse();
                    gr.itemCode = code;
                    gr.stats    = rolled;
                    loot.add(gr);
                }
            }
        }

        out.write(GSON.toJson(loot));
    }

    /* ─────────── helper: choose any weapon id from registry ───────────── */
    private String pickWeaponTypeForLevel(Random rng) {
        List<String> ids = new ArrayList<>(WEAPON_MAP.keySet());
        return ids.get(rng.nextInt(ids.size()));
    }

    /* ─────────── helper: choose any item id from registry ─────────────── */
    private String pickItemTypeForLevel(Random rng) {
        List<String> ids = (List<String>) ITEM_REGISTRY.getAllTypeIDs();
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
        long h = 1125899906842597L; // prime seed
        h = 31*h + d;
        h = 31*h + s;
        h = 31*h + r;
        h = (long) (31*h + x);
        h = (long) (31*h + y);
        h = 31*h + date.hashCode();
        return h;
    }
}
