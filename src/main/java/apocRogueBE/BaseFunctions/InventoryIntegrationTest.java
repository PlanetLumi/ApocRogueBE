package apocRogueBE.BaseFunctions;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class InventoryIntegrationTest {
    private static final String BASE          = "https://europe-west2-studious-camp-458516-f5.cloudfunctions.net";
    private static final String LOGIN_ENDPOINT= BASE + "/loginSystem";
    private static final String PUSH_ENDPOINT = BASE + "/inventorypush";
    private static final String PULL_ENDPOINT = BASE + "/inventorypull";

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final Type listOfMapsType = new TypeToken<List<Map<String,Object>>>(){}.getType();

    public static void main(String[] args) throws Exception {
        // 0) Log in to get a JWT
        Map<String,String> credentials = Map.of(
                "username", "Lumie",
                "password", "FuckYOU123!"
        );
        String loginReq  = gson.toJson(credentials);
        String loginResp = httpPost(LOGIN_ENDPOINT, loginReq, null);
        System.out.println("=== LOGIN RESPONSE ===\n" + loginResp);

        JsonObject loginJson = JsonParser.parseString(loginResp).getAsJsonObject();
        if (!loginJson.has("token")) {
            throw new IllegalStateException("Login failed, no token in response");
        }
        String jwt = loginJson.get("token").getAsString();

        // 1) Roll a fake weaponID using your server-side factory
        String typeID = "01";  // adjust to a valid two-char typeID in your system
        Map<String,Integer> baseStats = new HashMap<>();
        for (String k : apocRogueBE.Weapons.StatKeys.ALL) baseStats.put(k, 5);
        String fakeID = apocRogueBE.Weapons.WeaponFactory.rollAndEncode(typeID, baseStats, 1, 1);
        apocRogueBE.Weapons.WeaponIDDecoder.Decoded d = apocRogueBE.Weapons.WeaponIDDecoder.decode(fakeID);

        // 2) Build the JSON body for InventoryPush
        Map<String,Object> entry = new LinkedHashMap<>();
        entry.put("typeID",     typeID);
        entry.put("skullLevel", d.skullLevel);
        entry.put("skullSub",   d.skullSub);
        entry.put("stats",      d.stats);
        entry.put("count",      1);
        Map<String,Object> pushBody = Map.of("inventory", List.of(entry));
        String pushJson = gson.toJson(pushBody);

        // 3) Send the push with Authorization header
        String pushResp = httpPost(PUSH_ENDPOINT, pushJson, jwt);
        System.out.println("\n=== PUSH RESPONSE ===\n" + pushResp);

        // 4) Now pull it back—again with the same JWT
        String pullResp = httpPost(PULL_ENDPOINT, "{}", jwt);
        System.out.println("\n=== PULL RESPONSE ===\n" + pullResp);

        // 5) Parse the pull into a List<Map<String,Object>>
        List<Map<String,Object>> items = gson.fromJson(pullResp, listOfMapsType);
        System.out.println("\nParsed Pull (“count” field):");
        for (Map<String,Object> it : items) {
            System.out.printf("  %s → count=%s%n",
                    it.get("itemCode"),
                    it.get("count")
            );
        }
    }

    /**
     * Sends a POST to `urlStr` with body JSON `body`.
     * If `jwt` is non-null, attaches `Authorization: Bearer jwt`.
     * Returns the raw response-body as a String.
     */
    private static String httpPost(String urlStr, String body, String jwt) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        if (jwt != null) {
            c.setRequestProperty("Authorization", "Bearer " + jwt);
        }
        c.setDoOutput(true);
        c.setConnectTimeout(5000);
        c.setReadTimeout(5000);

        try (OutputStream os = c.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int status = c.getResponseCode();
        InputStream in = (status >= 200 && status < 300)
                ? c.getInputStream()
                : c.getErrorStream();
        String resp = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        return resp;
    }
}
