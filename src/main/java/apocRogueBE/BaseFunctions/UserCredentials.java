package apocRogueBE.BaseFunctions;

/**
 * Simple POJO for JSON <-> Java binding.
 */
public class UserCredentials {
    private String username;
    private String password;

    public UserCredentials() {}

    public String getUsername()       { return username; }
    public void setUsername(String u) { this.username = u; }

    public String getPassword()         { return password; }
    public void setPassword(String pw) { this.password = pw; }
}
