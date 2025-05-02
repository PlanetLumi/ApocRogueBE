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
    public static String salt(String plain) {
        return BCrypt.gensalt(12);
    }
}
