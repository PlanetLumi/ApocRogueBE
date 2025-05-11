package apocRogueBE.BaseFunctions;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import apocRogueBE.Weapons.StatKeys;
import apocRogueBE.Weapons.WeaponFactory;
import apocRogueBE.Weapons.WeaponIDDecoder;

public class InventoryIntegrationTest {
    // TODO: point these at your actual deployed functions:
    private static final String BASE_URL        =
            "https://europe-west2-studious-camp-458516-f5.cloudfunctions.net";
    private static final String PUSH_ENDPOINT   = BASE_URL + "/InventoryPush";
    private static final String PULL_ENDPOINT   = BASE_URL + "/InventoryPull";

    // TODO: supply a real token
    private static final String AUTH_TOKEN      = "Bearer YOUR_JWT_HERE";

    private static final Gson gson = new Gson();

    public static void main(String[] args) throws Exception {
        // 1) Roll a fake ID + stats
        String typeID = "01";   // your two-char ID
        Map<String,Integer> base = new HashMap<>();
        for (String stat : StatKeys.ALL) base.put(stat, 5);
        String fakeId = WeaponFactory.rollAndEncode(typeID, base, 1, 1);
        WeaponIDDecoder.Decoded decoded = WeaponIDDecoder.decode(fakeId);

        // 2) Build push payload
        Map<String,Object> entry = new LinkedHashMap<>();
        entry.put("typeID",     typeID);
        entry.put("skullLevel", decoded.skullLevel);
        entry.put("skullSub",   decoded.skullSub);
        entry.put("stats",      decoded.stats);
        entry.put("count",      1);

        Map<String,Object> pushBody = Map.of("inventory", List.of(entry));
        String pushJson = gson.toJson(pushBody);

        // 3) Push it
        System.out.println("=== PUSH REQUEST ===");
        System.out.println(pushJson);
        String pushResponse = httpPost(PUSH_ENDPOINT, pushJson);
        System.out.println("=== PUSH RESPONSE ===");
        System.out.println(pushResponse);

        // 4) Pull it back
        System.out.println("\n=== PULL REQUEST ===");
        String pullResponse = httpPost(PULL_ENDPOINT, "{}");
        System.out.println("=== PULL RESPONSE ===");
        System.out.println(pullResponse);

        // 5) Parse and print counts
        List<Map<String,Object>> inventory = gson.fromJson(
                // strip the HTTP status line if you include it
                pullResponse.replaceFirst("^HTTP \\d+\\r?\\n",""),
                new TypeToken<List<Map<String,Object>>>(){}.getType()
        );
        System.out.println("\nParsed entries:");
        for (var item : inventory) {
            System.out.printf("  code=%s, count=%s%n",
                    item.get("itemCode"), item.get("count"));
        }
    }

    private static String httpPost(String urlString, String jsonBody) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(5_000);
        conn.setReadTimeout(5_000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type",  "application/json");
        conn.setRequestProperty("Authorization", AUTH_TOKEN);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        InputStream in = (status >= 200 && status < 300)
                ? conn.getInputStream()
                : conn.getErrorStream();

        String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        return "HTTP " + status + "\n" + body;
    }
}
