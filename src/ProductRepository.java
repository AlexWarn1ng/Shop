import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.crypto.SecretKey;
import java.sql.*;
import java.util.*;


public class ProductRepository {

    private final DataBaseConnector db;
    private final SecretKey aesKey;

    public ProductRepository(DataBaseConnector db, SecretKey aesKey) {
        this.db = db;
        this.aesKey = aesKey;
    }
    public static boolean isNumber(String str) {
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    private static boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    }



    public List<Product> getAllProducts() {
        List<Product> list = new ArrayList<>();
        String sqlRequest = "SELECT * FROM products";
        try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(sqlRequest);) {

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                list.add(new Product(
                        rs.getString("productid"),
                        rs.getString("steamlogin"),
                        CryptoUtil.decrypt(rs.getString("steampassword"), aesKey),
                        rs.getString("emailaddress"),
                        CryptoUtil.decrypt(rs.getString("emailpassword"), aesKey)
                ));
            }

            rs.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }


  public boolean importProducts(String dataString) throws SQLException {
 int Counter = 0;
     String sqlRequest = "INSERT INTO products (steamlogin, steampassword, emailaddress, emailpassword) VALUES (?, ?, ?, ?)";
     try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(sqlRequest);) {
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
          stmt.setString(2, CryptoUtil.encrypt(parts[1], aesKey));
          stmt.setString(3, parts[2]);
          stmt.setString(4, CryptoUtil.encrypt(parts[3], aesKey));
          stmt.addBatch();

      }
      System.out.println("Successfully imported " + Counter + " products");
      stmt.executeBatch();
      return true;
  }
  catch (Exception e) {
      System.out.println("ERROR: Something went wrong while importing products!");
        e.printStackTrace();
        return false;
    }
}

    static public boolean deleteProducts(DataBaseConnector db,String idsToDelete)  {
        int Counter = 0;
        String sqlRequest = " DELETE FROM products WHERE productid = ?";
        try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(sqlRequest);) {

            for (String line : idsToDelete.split("\\R")) {
                if (line.isBlank()) {
                    continue;
                }
                if(!isNumber(line)){
                    continue;
                }
                int idToDeleteNow = Integer.parseInt(line.trim());
                stmt.setInt(1, idToDeleteNow);
                stmt.addBatch();
            }
            int[] results = stmt.executeBatch();
            for (int r : results) {
                if (r > 0) { Counter++; }
            }
            System.out.println("Successfully deleted " + Counter + " products");
            return true;

        }
        catch(Exception e){
            System.out.println("ERROR: Something went wrong while deleting products!");
            e.printStackTrace();
            return false;
        }
    }

     public boolean editProducts(String dataString) throws SQLException {
        int Counter = 0, CounterEmailError = 0;
        String sqlRequest = """
        UPDATE products SET
            steamlogin     = COALESCE(NULLIF(?, 'null'), steamlogin),
            steampassword  = COALESCE(NULLIF(?, 'null'), steampassword),
            emailaddress   = COALESCE(NULLIF(?, 'null'), emailaddress),
            emailpassword  = COALESCE(NULLIF(?, 'null'), emailpassword)
        WHERE productid = ?;
        """;
        try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(sqlRequest)) {

            for (String line : dataString.split("\\R")) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split(":");
                if (parts.length != 5) {
                    continue;
                }
                if (!"null".equalsIgnoreCase(parts[3]) && !isValidEmail(parts[3])) {
                    CounterEmailError++; continue;
                }

                String steamPass = parts[2];
                String emailPass = parts[4];
                stmt.setString(1, parts[1]); // steamlogin (может быть "null")
                stmt.setString(2, "null".equals(steamPass) ? "null" : CryptoUtil.encrypt(steamPass, aesKey));
                stmt.setString(3, parts[3]); // emailaddress (может быть "null")
                stmt.setString(4, "null".equals(emailPass) ? "null" : CryptoUtil.encrypt(emailPass, aesKey));

                stmt.setInt(5, Integer.parseInt(parts[0]));
                stmt.addBatch();

            }
            int[] results = stmt.executeBatch();
            for (int r : results) {
                if (r == Statement.SUCCESS_NO_INFO || r > 0) { Counter++; }
            }
            System.out.println("Successfully edited " + Counter + " products | " + "Not edited " + CounterEmailError  + " products (error with invalid email address!)");
                return true;
        }
        catch (Exception e) {
            System.out.println("ERROR: Something went wrong while importing products!");
            e.printStackTrace();
            return false;
        }
    }

}
