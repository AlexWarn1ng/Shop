import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class Main {
    public static void main(String[] args) throws Exception {
        Properties p = new Properties();

        try (InputStream in = Files.newInputStream(Path.of("config.txt"))) {
            p.load(in);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String username = p.getProperty("username");
        String password = p.getProperty("password");
        String addressHost = p.getProperty("address");
        String port = p.getProperty("port");
        String database = p.getProperty("database");
        String aesKeyBase64 = p.getProperty("aes_key");
        if (aesKeyBase64 == null || aesKeyBase64.isEmpty()) {
            throw new RuntimeException("aes_key is missing in config.txt");
        }
        SecretKey aesKey = CryptoUtil.keyFromBase64(aesKeyBase64);

        try{
            if (username == null || password == null || addressHost == null || database == null || port == null || port.isEmpty() || username.isEmpty() || password.isEmpty() || addressHost.isEmpty() || database.isEmpty()){
                throw new RuntimeException("ERROR: One or more fields are empty in config.txt");                }
            }
        catch(Exception e) {
            throw new Exception(e);
        }

            System.out.println("Settings from config.txt");
            System.out.println("username=" + username);
            System.out.println("password=" + password);
            System.out.println("address=" + addressHost);
            System.out.println("port=" + port);
            System.out.println("database=" + database);
            System.out.println("SecretKey (AesKey) LOADED");

        DataBaseConnector dbConnection = new DataBaseConnector(username, password, addressHost, port, database);
        HttpServer server;

        try {
            server = HttpServer.create(new InetSocketAddress(8080), 0);
            server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(8));
        } catch (IOException e) {
            throw new RuntimeException("ERROR: server not created", e);
        }


        ProductRepository productobject = new ProductRepository(dbConnection, aesKey);

        server.createContext("/getallproducts", exchange -> {

            StringBuilder response = new StringBuilder();

            for (Product c : productobject.getAllProducts()) {
                response
                        .append(c.getproductID())
                        .append(" | ")
                        .append(c.getSteamLogin())
                        .append(" | ")
                        .append(c.getSteamPassword())
                        .append(" | ")
                        .append(c.getEmailAddress())
                        .append(" | ")
                        .append(c.getEmailPassword())
                        .append("\n");
            }

            byte[] bytes = response.toString().getBytes();
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });


        server.createContext("/importproducts", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                byte[] bytes = "ERROR: Only POST allowed".getBytes();
                exchange.sendResponseHeaders(405, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
                return;
            }
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8 );
            boolean ok;
            try {
                ok = productobject.importProducts(body);
            } catch (SQLException e) {
                byte[] bytes = ("DB ERROR: " + e.getMessage()).getBytes();
                exchange.sendResponseHeaders(500, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
                return;
            }
            byte[] bytes = (ok ? "OK" : "ERROR").getBytes();
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });

        server.createContext("/deleteproducts", exchange -> {
            if (!"DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
                byte[] bytes = "ERROR: Only DELETE allowed".getBytes();
                exchange.sendResponseHeaders(405, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
                return;
            }
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8 );
            boolean ok;
            try {
                ok = ProductRepository.deleteProducts(dbConnection, body);
            } catch (Exception e) {
                byte[] bytes = ("DB ERROR: " + e.getMessage()).getBytes();
                exchange.sendResponseHeaders(500, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
                return;
            }

            byte[] bytes = (ok ? "OK" : "ERROR").getBytes();
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });

        server.createContext("/editproducts", exchange -> {
            if (!"PATCH".equalsIgnoreCase(exchange.getRequestMethod())) {
                byte[] bytes = "ERROR: Only PATCH allowed".getBytes();
                exchange.sendResponseHeaders(405, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
                return;
            }
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8 );
            boolean ok;
            try {
                ok = productobject.editProducts(body);

            } catch (SQLException e) {
                byte[] bytes = ("DB ERROR: " + e.getMessage()).getBytes();
                exchange.sendResponseHeaders(500, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
                return;
            }
            byte[] bytes = (ok ? "OK" : "ERROR").getBytes();
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });


        server.start();
        System.out.println("Server started on http://localhost:8080");
    }
}


