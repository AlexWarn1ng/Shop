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

       public void connect() {
            if(conn != null){ return; }

            String ConnectionUrl = "jdbc:postgresql://" + addressHost + ":" + port + "/" + database;

            try{
                conn = DriverManager.getConnection(ConnectionUrl, username, password);
                System.out.println("Connected to " + ConnectionUrl);
            }

            catch (SQLException e) {
                throw new RuntimeException("DB connect failed: " + ConnectionUrl, e);
            }
       }
    public Connection getConnection() {
        if (conn == null) {
            throw new IllegalStateException("ERROR: DB is not connected");
        }
        return conn;
    }

}
