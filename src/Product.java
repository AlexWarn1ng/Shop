import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;

public class Product {
    private final String productID;
    private final String steamLogin;
    private final String steamPassword;
    private final String emailAddress;
    private final String emailPassword;

    public Product( String productID, String steamLogin, String steamPassword, String emailAddress, String emailPassword) {
        this.productID = productID;
        this.steamLogin = steamLogin;
        this.steamPassword = steamPassword;
        this.emailAddress = emailAddress;
        this.emailPassword = emailPassword;
    }

    public String getproductID() {
        return productID;
    }
    public String getSteamLogin() {
        return steamLogin;
    }

    public String getSteamPassword() {
        return steamPassword;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public String getEmailPassword() {
        return emailPassword;
    }
}

