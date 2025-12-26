import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;


public class ProductRepository {

    private final DataBaseConnector db;

    public ProductRepository(DataBaseConnector db) {
        this.db = db;
    }

    private static boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    }


    public List<Product> getAll() {
        List<Product> list = new ArrayList<>();

        try {
            Connection conn = db.getConnection();
            PreparedStatement stmt =
                    conn.prepareStatement("SELECT * FROM products");

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                list.add(new Product(
                        rs.getString("steamlogin"),
                        rs.getString("steampassword"),
                        rs.getString("emailaddress"),
                        rs.getString("emailpassword")
                ));
            }

            rs.close();
            stmt.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }


 static public boolean importAccounts(DataBaseConnector db,String dataString) throws SQLException {
 int Counter = 0;
  try {
      Connection conn = db.getConnection();
      PreparedStatement stmt = conn.prepareStatement("INSERT INTO products (steamlogin, steampassword, emailaddress, emailpassword) VALUES (?, ?, ?, ?)");
      for (String line : dataString.split("\\R")) {
          if (line.isBlank()) {
              continue;
          }
          String[] parts = line.split(":");
          if (parts.length != 4) {
              continue;
          }
          if (!isValidEmail(parts[2])) {
              continue;
          }

          Counter++;
          stmt.setString(1, parts[0]);
          stmt.setString(2, parts[1]);
          stmt.setString(3, parts[2]);
          stmt.setString(4, parts[3]);
          stmt.addBatch();

      }
      System.out.println("Successfully imported " + Counter + " accounts");
      stmt.executeBatch();
      return true;
  }
  catch (Exception e) {
      System.out.println("ERROR: Something went wrong while importing accounts!");
        e.printStackTrace();
        return false;
    }
}
}
