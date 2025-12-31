import java.sql.*;
import java.security.SecureRandom;
import javax.crypto.SecretKey;

public class BuyProduct implements AutoCloseable{
    private final String TransactionKey;
    private final SecretKey aesKey;
    private Connection conn;
    private static final String SymbolsForTransactionKey = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final SecureRandom RANDOM = new SecureRandom();

    public String getTransactionKey(){
        return TransactionKey;
    }

    public void close() {
        try {
            if (conn != null) conn.close();
        } catch (Exception ignored) {}
    }

    private static String generateRandomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = RANDOM.nextInt(SymbolsForTransactionKey.length());
            sb.append(SymbolsForTransactionKey.charAt(index));
        }
        return sb.toString();
    }

public BuyProduct(String aesKey, String username, String password, String addressHost, String port, String database) throws SQLException {
    String url = "jdbc:postgresql://" + addressHost + ":" + port + "/" + database;
    this.conn =  DriverManager.getConnection(url, username, password);
    this.aesKey = CryptoUtil.keyFromBase64(aesKey);
    this.TransactionKey = generateRandomString(16);
}

    public void begin() throws SQLException {
        if (conn == null) throw new IllegalStateException("Connection is closed. Create new BuyProduct object.");
        conn.setAutoCommit(false);
    }


    public void commit() throws SQLException {
        if (conn == null) return;
        conn.commit();
        conn.close();
        conn = null;
    }

    public void rollback() {
        try { if (conn != null) conn.rollback(); } catch (Exception ignored) {}
        try { if (conn != null) conn.close(); } catch (Exception ignored) {}
        conn = null;
    }

    public String reserveProducts(int count) throws SQLException {
        StringBuilder result = new StringBuilder();
        int actualCount = 0;

        String sql =
                "UPDATE products SET reservedcode = ? " +
                        "WHERE productid IN (" +
                        "   SELECT productid FROM products " +
                        "   WHERE mafileexistence = true AND reservedcode IS NULL " +
                        "   LIMIT ? FOR UPDATE" +
                        ") RETURNING productid";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, TransactionKey);
            stmt.setInt(2, count);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.append(rs.getInt("productid")).append("\n");
                    actualCount++;
                }
            }
        }

        if (actualCount < count) {
            String undoSql = "UPDATE products SET reservedcode = NULL WHERE reservedcode = ?";
            try (PreparedStatement undo = conn.prepareStatement(undoSql)) {
                undo.setString(1, TransactionKey);
                undo.executeUpdate();
            }
            throw new SQLException("Not Enough products to buy. Try to choose less of them");
        }
        return result.toString().trim();
    }





    public void moveProductsInSoldTable(String idsToMove) throws Exception {
        String sqlSelect = "SELECT * FROM products WHERE productid = ?";
        String sqlInsert = "INSERT INTO soldproducts (steamlogin, steampassword, emailaddress, emailpassword, transactioncode) VALUES (?, ?, ?, ?, ?)";
        String sqlDelete = "DELETE FROM products WHERE productid = ?";

        try (PreparedStatement stmtSelect = conn.prepareStatement(sqlSelect);
             PreparedStatement stmtInsert = conn.prepareStatement(sqlInsert);
             PreparedStatement stmtDelete = conn.prepareStatement(sqlDelete)) {

            for (String idStr : idsToMove.split("\\R")) {
                idStr = idStr.trim();
                if (idStr.isBlank()) continue;
                if (!ProductRepository.isNumber(idStr)) continue;
                int id = Integer.parseInt(idStr);
                stmtSelect.setInt(1, id);
                Product p = null;

                try (ResultSet rs = stmtSelect.executeQuery()) {
                    if (rs.next()) {
                        p = new Product(
                                rs.getString("productid"),
                                rs.getString("steamlogin"),
                                CryptoUtil.decrypt(rs.getString("steampassword"), aesKey),
                                rs.getString("emailaddress"),
                                CryptoUtil.decrypt(rs.getString("emailpassword"), aesKey)
                        );
                    }
                }

                if (p == null) { continue; }

                stmtInsert.setString(1, p.getSteamLogin());
                stmtInsert.setString(2, CryptoUtil.encrypt(p.getSteamPassword(), aesKey));
                stmtInsert.setString(3, p.getEmailAddress());
                stmtInsert.setString(4, CryptoUtil.encrypt(p.getEmailPassword(), aesKey));
                stmtInsert.setString(5, TransactionKey);
                stmtInsert.addBatch();

                stmtDelete.setInt(1, id);
                stmtDelete.addBatch();
            }

            stmtInsert.executeBatch();
            stmtDelete.executeBatch();
        }
    }



}


