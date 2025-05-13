package apocRogueBE.WorldGen;

import apocRogueBE.Security.AuthHelper;
import apocRogueBE.SingletonConnection.DataSourceSingleton;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cloud Function to verify a player's inventory against the generated loot log
 * for a specific level. If the inventory contains all generated items,
 * the corresponding loot log file is deleted.
 */
public class CheckBucket implements HttpFunction {
    private static final Gson GSON = new Gson();
    private static final String BUCKET_ENV = "LOOT_BUCKET";

    /**
     * Payload received from client: level identifiers and current inventory.
     */
    static class CheckRequest {
        public int difficulty;
        public int subLevel;
        public String date;                        // yyyy-MM-dd, optional
        public List<InvItem> inventory;
    }
    /**
     * Represents one inventory item from client.
     */
    static class InvItem {
        public String itemCode;
        public int count;
    }
    /**
     * Represents one generated loot record (only itemCode needed for verification).
     */
    static class LoggedItem {
        public String itemCode;
    }
    /**
     * Response to client indicating pass/fail and message.
     */
    static class CheckResponse {
        public boolean passed;
        public String message;
        public CheckResponse(boolean passed, String message) {
            this.passed = passed;
            this.message = message;
        }
    }

    @Override
    public void service(HttpRequest req, HttpResponse resp) throws Exception {
        resp.setContentType("application/json");
        BufferedWriter writer = resp.getWriter();

        // 1) Parse request
        CheckRequest cr = GSON.fromJson(req.getReader(), CheckRequest.class);
        int playerId;
        try (Connection c = DataSourceSingleton.getConnection()) {
            playerId = AuthHelper.requirePlayerId(req, c);
        }
        if (cr == null || cr.inventory == null) {
            resp.setStatusCode(400);
            writer.write(GSON.toJson(new CheckResponse(false, "Invalid request payload")));
            return;
        }
        // default date to today if missing
        if (cr.date == null || cr.date.isEmpty()) {
            cr.date = LocalDate.now().toString();
        }

        // 2) Construct blob name for this level
        String blobName = String.format("levelLoot/%d_%d_%s.json",
                playerId,
                cr.difficulty, cr.subLevel, cr.date);
        String bucket = System.getenv(BUCKET_ENV);
        Storage storage = StorageOptions.getDefaultInstance().getService();
        BlobId blobId = BlobId.of(bucket, blobName);
        Blob blob = storage.get(blobId);
        if (blob == null) {
            resp.setStatusCode(404);
            writer.write(GSON.toJson(new CheckResponse(false, "Loot log not found for level")));
            return;
        }

        // 3) Load generated loot list from Cloud Storage
        String genJson = new String(blob.getContent(), StandardCharsets.UTF_8);
        Type genType = new TypeToken<List<LoggedItem>>(){}.getType();
        List<LoggedItem> generated = GSON.fromJson(genJson, genType);

        // 4) Tally counts for generated codes
        Map<String,Integer> genCounts = new HashMap<>();
        for (LoggedItem li : generated) {
            genCounts.merge(li.itemCode, 1, Integer::sum);
        }

        // 5) Tally counts for player's inventory
        Map<String,Integer> invCounts = new HashMap<>();
        for (InvItem it : cr.inventory) {
            invCounts.put(it.itemCode, it.count);
        }

        // 6) Compare: ensure inventory has >= generated for each code
        for (Map.Entry<String,Integer> e : genCounts.entrySet()) {
            int have = invCounts.getOrDefault(e.getKey(), 0);
            if (have < e.getValue()) {
                String msg = String.format("Missing %d of %s (have %d)",
                        e.getValue(), e.getKey(), have);
                writer.write(GSON.toJson(new CheckResponse(false, msg)));
                return;
            }
        }

        // 7) All checks passed: delete the log file
        storage.delete(blobId);
        writer.write(GSON.toJson(new CheckResponse(true, "Inventory matches generation; log cleared")));
    }
}
