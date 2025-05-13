package apocRogueBE.Shop;

import java.util.List;
import java.util.Map;

/**
 * Configuration mapping each seller ID to the list of weapon typeIDs
 * that they offer in the shop. Adjust these lists to control offers.
 */
public final class SellerConfig {

    /* Small value-object */
    public record Seller(
            String  displayName,
            String  portraitKey,
            List<String> typeIDs
    ){}

    /** Master map  sellerID → Seller  */
    private static final Map<String, Seller> TABLE = Map.of(
            //               name               portrait   typeIDs
            "A", new Seller("Bartholomew",      "traderA", List.of("20","21")),
            "B", new Seller("Destaros",         "traderB", List.of("05","06","07")),
            "C", new Seller("Igor",             "traderC", List.of("01","02","03","04"))
    );

    /* Helpers the rest of the BE calls */
    public static Seller get(String sellerID)           { return TABLE.get(sellerID); }
    public static List<String> typeIDsFor(String id)    { return TABLE.get(id).typeIDs(); }
}