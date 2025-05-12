package apocRogueBE.Weapons;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import apocRogueBE.Security.AuthHelper;
import apocRogueBE.SingletonConnection.DataSourceSingleton;
import apocRogueBE.Weapons.StatKeys;
import apocRogueBE.Weapons.WeaponFactory;
import apocRogueBE.Weapons.WeaponIDDecoder;
import apocRogueBE.Weapons.WeaponIDEncoder;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WeaponGenerate implements HttpFunction {
    private static final Gson gson = new Gson();
    private static final Map<String,WeaponData> WEAPON_DATA_MAP = loadAllWeaponData();

    static class GenerateRequest {
        String typeID;
        int skullLevel;
        int skullSub;
    }

    static class GenerateResponse {
        String itemCode;
        Map<String,Integer> stats;
    }
    private static Map<String,WeaponData> loadAllWeaponData() {
        try (InputStream in = WeaponGenerate.class
                .getClassLoader()
                .getResourceAsStream("items.json")) {
            if (in == null) {
                throw new RuntimeException("items.json not found on classpath");
            }

            // parse JSON array → List<WeaponData>
            Type listType = new TypeToken<List<WeaponData>>(){}.getType();
            List<WeaponData> list = gson.fromJson(new InputStreamReader(in), listType);

            // index by typeID
            return list.stream()
                    .collect(Collectors.toMap(wd -> wd.typeID, wd -> wd));
        } catch (Exception e) {
            throw new RuntimeException("Failed to load base weapon data", e);
        }
    }
    @Override
    public void service(HttpRequest req, HttpResponse resp) throws Exception {
        resp.setContentType("application/json");
        BufferedWriter w = resp.getWriter();

        // 1) Auth
        Connection conn = DataSourceSingleton.getConnection();
        final int playerId;
        try {
            playerId = AuthHelper.requirePlayerId(req, conn);
        } catch (Exception e) {
            resp.setStatusCode(401);
            w.write(gson.toJson(Map.of("error","Unauthorized")));
            return;
        }

        // 2) Parse JSON body
        GenerateRequest gen;
        try {
            gen = gson.fromJson(new InputStreamReader(req.getInputStream()), GenerateRequest.class);
        } catch (Exception e) {
            resp.setStatusCode(400);
            w.write(gson.toJson(Map.of("error","Malformed JSON")));
            return;
        }

        // 3) Build baseValues map (you’ll need somewhere—DB table or in-code map—to look up each weapon’s “base” stats)
        //    Here’s a placeholder; replace with your actual data source
        Map<String,Integer> baseValues = loadBaseStatsFor(gen.typeID);

        // 4) Roll & encode
        String code = WeaponFactory.rollAndEncode(
                gen.typeID, baseValues, gen.skullLevel, gen.skullSub
        );

        // 5) Persist into Inventory (quantity += 1)
        String sql = ""
                + "INSERT INTO Inventory(playerID, itemCode, quantity) "
                + " VALUES (?,?,1) "
                + "ON DUPLICATE KEY UPDATE quantity = quantity + 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, playerId);
            ps.setString(2, code);
            ps.executeUpdate();
        }

        // 6) Prepare response with decoded stats
        var decoded = WeaponIDDecoder.decode(code);  // uses your decoder :contentReference[oaicite:0]{index=0}:contentReference[oaicite:1]{index=1}
        GenerateResponse out = new GenerateResponse();
        out.itemCode = code;
        out.stats    = decoded.stats;

        // 7) Return
        resp.setStatusCode(200);
        w.write(gson.toJson(out));
    }

    private Map<String,Integer> loadBaseStatsFor(String typeID) {
        WeaponData wd = WEAPON_DATA_MAP.get(typeID);
        if (wd == null) {
            throw new IllegalArgumentException("Unknown weapon typeID: " + typeID);
        }

        // 2. Map each StatKeys.ALL entry to the corresponding field in WeaponData :contentReference[oaicite:0]{index=0}:contentReference[oaicite:1]{index=1}:contentReference[oaicite:2]{index=2}:contentReference[oaicite:3]{index=3}
        Map<String,Integer> baseVals = new HashMap<>();
        for (String key : StatKeys.ALL) {
            switch (key) {
                case "damage":
                    baseVals.put(key, wd.damage);
                    break;
                case "projectileValue":
                    baseVals.put(key, wd.projectileValue);
                    break;
                case "animationSpeed":
                    baseVals.put(key, wd.animationSpeed);
                    break;
                case "noiseLevel":
                    baseVals.put(key, wd.noiseLevel);
                    break;
                case "dashSpeed":
                    baseVals.put(key, Math.round(wd.dashSpeed));
                    break;
                case "dashDuration":
                    baseVals.put(key, Math.round(wd.dashDuration * 100));
                    break;
                case "dashCooldown":
                    baseVals.put(key, Math.round(wd.dashCooldown * 10));
                    break;
                default:
                    baseVals.put(key, 0);
            }
        }
        return baseVals;
    }
}
