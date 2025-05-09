package apocRogueBE.Security;



import org.mindrot.jbcrypt.BCrypt;

public final class PasswordUtils {
    private PasswordUtils() {} // no‐one should instantiate this

    /** Hash a plain password for storage. */
    public static String hash(String plain) {
        // work factor (12) can be tuned for your latency/SLA needs
        return BCrypt.hashpw(plain, BCrypt.gensalt(12));
    }

    /** Verify that a plain password matches the stored hash. */
    public static boolean verify(String plain, String storedHash) {
        if (storedHash == null || !storedHash.startsWith("$2a$")) {
            throw new IllegalArgumentException("Invalid hash provided for comparison");
        }
        return BCrypt.checkpw(plain, storedHash);
    }
    public static boolean validPassword(String password) {
        return password.length() > 8
                && password.matches(
                "^(?=.*[A-Z].*[A-Z])"      // two uppercase
                        + "(?=.*[!@#$&*])"           // one symbol
                        + "(?=.*[0-9].*[0-9])"       // two digits
                        + "(?=.*[a-z].*[a-z].*[a-z])"// three lower
                        + ".+$"                     // <— consume the rest
        );
    }
    public static int validatePassword(String password, String username){
        if(!validPassword(password)){
            return 1;
        }
        if(containsName(username,password)){
            return (2);
        }
        return 0;
    }
    public static boolean containsName(String name, String password){
        return password.contains(name);
    }
    public static String securityPrints(int input){
        return switch (input) {
            case 1 ->
                    "Password must be above 8 characters in length and contain numbers, symbols, upper and lower case letters";
            case 2 -> "Password must not contain your username";
            default -> "0";
        };
    }
}

