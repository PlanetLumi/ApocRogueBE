package io.github.apocRogue.dto;

import apocRogueBE.Shop.ShopEntry;

import java.util.List;

/** JSON payload returned by /dailyShop and consumed by ShopService. */
public class SellerInfo {
    public String sellerID;        // "A", "B", "C"
    public String displayName;     // e.g. "Bartholomew the Bold"
    public String portraitPath;    // "ui/portraits/traderA.png"
    public int    spentGauge;      // gold spent / reputation
    public List<ShopEntry> items;  // today's rolled items
}
