import com.ourcat.app.utils.ServerAddress;

public class ServerAddressCheck {
    public static void main(String[] args) {
        assert "http://192.168.1.20:8080/".equals(ServerAddress.normalize(" http://192.168.1.20:8080 "));
        assert "https://example.com/".equals(ServerAddress.normalize("HTTPS://EXAMPLE.COM/"));
        assert "http://[::1]:8080/".equals(ServerAddress.normalize("http://[::1]:8080"));
        for (String invalid : new String[] {"", "localhost:8080", "ftp://example.com", "http://user:pass@example.com",
                "http://example.com/api", "http://example.com/?x=1", "http://example.com/#test",
                "http://example.com:0", "http://example.com:70000", "http://example.com:",
                "http://example.com\\evil", "http://bad host/"}) {
            try {
                ServerAddress.normalize(invalid);
                throw new AssertionError("Accepted: " + invalid);
            } catch (IllegalArgumentException expected) { }
        }
        System.out.println("Server address validation passed");
    }
}
