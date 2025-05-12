package apocRogueBE.Shop;


/** One line in `SellerInfo.items`. */
public class ShopEntry {
    public String itemCode;   // encoded ID ("ID..." or "IT...")
    public String typeID;     // two-char weapon/item type
    public int    price;      // gold cost
    public int    remaining;  // stock left after previous purchases
}
