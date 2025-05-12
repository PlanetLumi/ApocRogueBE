package apocRogueBE.Shop;

import java.util.List;

public class ShopKeeper {
    private final String sellerID;
    private final String displayName;
    private final String portraitKey;
    private final List<String> typeIDs;
    private final String[] greetingLines;
    private final String[] thankYouLines;
    private final String[] cannotAffordLines;
    private final String[] lockedItemLines;
    private final String[] soldOutLines;

    public ShopKeeper(
            String sellerID,
            String displayName,
            String portraitKey,
            List<String> typeIDs,
            String[] greetingLines,
            String[] thankYouLines,
            String[] cannotAffordLines,
            String[] lockedItemLines,
            String[] soldOutLines
    ) {
        this.sellerID         = sellerID;
        this.displayName      = displayName;
        this.portraitKey      = portraitKey;
        this.typeIDs          = typeIDs;
        this.greetingLines    = greetingLines;
        this.thankYouLines    = thankYouLines;
        this.cannotAffordLines= cannotAffordLines;
        this.lockedItemLines  = lockedItemLines;
        this.soldOutLines     = soldOutLines;
    }

    public String getSellerID()       { return sellerID; }
    public String getDisplayName()    { return displayName; }
    public String getPortraitKey()    { return portraitKey; }
    public List<String> getTypeIDs()  { return typeIDs; }

    public String[] getGreetingLines()     { return greetingLines; }
    public String[] getThankYouLines()     { return thankYouLines; }
    public String[] getCannotAffordLines() { return cannotAffordLines; }
    public String[] getLockedItemLines()   { return lockedItemLines; }
    public String[] getSoldOutLines()      { return soldOutLines; }
}
