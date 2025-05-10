
package apocRogueBE.BaseFunctions;

public class MarketFilter {
    /** Optional: only show this item */
    public String itemCode;

    /** Optional: price range, in cents */
    public Long   minPrice;
    public Long   maxPrice;

    /** "newest" | "priceAsc" | "priceDesc" */
    public String sortBy = "newest";

    /** zero‐based page index */
    public Integer page = 0;

    /** page size */
    public Integer size = 50;
}
