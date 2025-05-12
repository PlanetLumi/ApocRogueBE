package apocRogueBE.Weapons;


public class WeaponData {
    public String typeID;

    public String name;
    public int damage;
    public String texturePath;
    public boolean projectileType;
    public int projectileValue;
    public String ammoTexture;
    public int animationSpeed;
    public int noiseLevel;
    public float dashSpeed;
    public float dashDuration;
    public float dashCooldown;
    public int getStat(String statKey) {
        return switch(statKey) {
            case "damage"           -> damage;
            case "projectileValue"  -> projectileValue;
            case "animationSpeed"   -> animationSpeed;
            case "noiseLevel"       -> noiseLevel;
            case "dashSpeed"        -> Math.round(dashSpeed);
            case "dashDuration"     -> Math.round(dashDuration);
            case "dashCooldown"     -> Math.round(dashCooldown);
            default                  -> 0;
        };
    }

}