import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DataBaseConnector {
    private final String username;
    private final String password;
    private final String addressHost;
    private final String port;
    private final String database;
    private Connection conn;

        public DataBaseConnector(String username, String password, String addressHost, String port, String database) {
         this.username = username;
            this.password = password;
                this.addressHost = addressHost;
                    this.port = port;
                      this.database = database;
        }

    public Connection getConnection() throws SQLException {
        String url = "jdbc:postgresql://" + addressHost + ":" + port + "/" + database;
        return DriverManager.getConnection(url, username, password);
    }


}
