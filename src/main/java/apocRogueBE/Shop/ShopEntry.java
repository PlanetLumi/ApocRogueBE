package apocRogueBE.Shop;


/** One line in `SellerInfo.items`. */
public class ShopEntry {
    public String itemCode;      // encoded "ID…" or "IT…"
    public String typeID;        // 2-char item/weapon type
    public String name;          // "Iron Sword", "Med-Kit", …
    public String texturePath;   // "textures/weapons/iron_sword.png"
    public int    price;
    public int    remaining;
}