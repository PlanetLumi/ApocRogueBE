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

    // your Cloud Functions base URL
    private static final String BASE           =
            "https://europe-west2-studious-camp-458516-f5.cloudfunctions.net";
    private static final String LOGIN_ENDPOINT = BASE + "/loginSystem";
    private static final String PUSH_ENDPOINT  = BASE + "/inventorypush";
    private static final String PULL_ENDPOINT  = BASE + "/inventorypull";

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final Type listOfMapsType =
            new TypeToken<List<Map<String,Object>>>(){}.getType();

    public static void main(String[] args) throws Exception {
        // 1) Log in
        String loginPayload = gson.toJson(Map.of(
                "username","Lumie", "password","FuckYOU123!"
        ));
        HttpResult loginResult = doPost(LOGIN_ENDPOINT, loginPayload, null);
        System.out.printf("LOGIN → %d%n%s%n", loginResult.status, loginResult.body);

        JsonObject loginJson = JsonParser
                .parseString(loginResult.body)
                .getAsJsonObject();
        String jwt = loginJson.get("token").getAsString();

        // 2) Build a fake inventory push
        String typeID = "01";
        Map<String,Integer> baseStats = new HashMap<>();
        for (String k : apocRogueBE.Weapons.StatKeys.ALL) baseStats.put(k,5);
        String fakeId =
                apocRogueBE.Weapons.WeaponFactory
                        .rollAndEncode(typeID, baseStats,1,1);
        var decoded = apocRogueBE.Weapons.WeaponIDDecoder.decode(fakeId);

        Map<String,Object> entry = new LinkedHashMap<>();
        entry.put("typeID",     typeID);
        entry.put("skullLevel", decoded.skullLevel);
        entry.put("skullSub",   decoded.skullSub);
        entry.put("stats",      decoded.stats);
        entry.put("count",      1);

        String pushJson = gson.toJson(Map.of("inventory", List.of(entry)));
        HttpResult pushNoJwt = doPost(PUSH_ENDPOINT, "{\"inventory\":[]}", null);
        System.out.printf("PUSH (no JWT) → %d%n%s%n", pushNoJwt.status, pushNoJwt.body);

        HttpResult pullNoJwt = doPost(PULL_ENDPOINT, "{}", null);
        System.out.printf("PULL (no JWT) → %d%n%s%n", pullNoJwt.status, pullNoJwt.body);

        HttpResult pushWithJwt = doPost(PUSH_ENDPOINT, pushJson, jwt);
        System.out.printf("PUSH (with JWT) → %d%n%s%n", pushWithJwt.status, pushWithJwt.body);

        HttpResult pullWithJwt = doPost(PULL_ENDPOINT, "{}", jwt);
        System.out.printf("PULL (with JWT) → %d%n%s%n", pullWithJwt.status, pullWithJwt.body);

        if (pullWithJwt.status == 200) {
            // explicitly declare as List<Map<String,Object>>
            List<Map<String,Object>> items =
                    gson.fromJson(pullWithJwt.body, listOfMapsType);

            System.out.println("=> Parsed pull:");
            // now each `item` is a Map, so .get("itemCode") compiles
            for (Map<String,Object> item : items) {
                System.out.printf("   %s → count=%s%n",
                        item.get("itemCode"),
                        item.get("count"));
            }
        }
    }

    // A little holder so we don’t collide with Google’s HttpResponse
    private static class HttpResult {
        final int    status;
        final String body;
        HttpResult(int s, String b) { status = s; body = b; }
    }

    private static HttpResult doPost(String urlStr,
                                     String jsonBody,
                                     String jwt) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection c = (HttpURLConnection)url.openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type","application/json");
        if (jwt != null) {
            c.setRequestProperty("Authorization", "Bearer " + jwt);
        }
        c.setDoOutput(true);
        try (OutputStream os = c.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        int status = c.getResponseCode();
        InputStream in = (status>=200 && status<300)
                ? c.getInputStream()
                : c.getErrorStream();
        String body = "";
        if (in != null) {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        return new HttpResult(status, body);
    }
}
