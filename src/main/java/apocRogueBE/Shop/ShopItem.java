package apocRogueBE.Shop;

import java.util.Map;

public class ShopItem {
    private final String code;
    private final int    baseStock;
    private final int    price;

    public ShopItem(String code, int baseStock, int price) {
        this.code      = code;
        this.baseStock = baseStock;
        this.price     = price;
    }

    public String getCode()      { return code; }
    public int    getBaseStock() { return baseStock; }
    public int    getPrice()     { return price; }
}
