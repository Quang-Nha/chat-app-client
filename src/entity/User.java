package entity;

public class User implements Comparable<User>{
    private String username;
    private String password;
    private boolean connecting;

    public User(String username, String password, boolean connecting) {
        this.username = username;
        this.password = password;
        this.connecting = connecting;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isConnecting() {
        return connecting;
    }

    public void setConnecting(boolean connecting) {
        this.connecting = connecting;
    }

    @Override
    public int hashCode() {
        int result = username != null ? username.hashCode() : 0;
        result = 31 * result + (password != null ? password.hashCode() : 0);
        result = 31 * result + (connecting ? 1 : 0);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof User) {
            return this.username.equalsIgnoreCase(((User) obj).username);
        }

        return false;
    }

    public boolean equals2(User user) {
        return this.username.equalsIgnoreCase(user.getUsername())
                && this.password.equalsIgnoreCase(user.getPassword());

    }

    @Override
    public String toString() {
        return username;
    }

    public String toString1() {
        return username + ":" + password + ":" + connecting;
    }

    public String toString2() {
        return username + ":" + password;
    }

    @Override
    public int compareTo(User o) {
        if (this.connecting && o.isConnecting()) {
            return 0;
        } else if (this.connecting && !o.isConnecting()) {
            return 1;
        }
        return -1;
    }
}
