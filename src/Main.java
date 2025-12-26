import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;

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


        DataBaseConnector dbConnection = new DataBaseConnector(username, password, addressHost, port, database);
        dbConnection.connect();
        HttpServer server;

        try {
            server = HttpServer.create(new InetSocketAddress(8080), 0);
        } catch (IOException e) {
            throw new RuntimeException("ERROR: server not created", e);
        }


        ProductRepository productobject = new ProductRepository(dbConnection);

        server.createContext("/getallproducts", exchange -> {

            StringBuilder response = new StringBuilder();

            for (Product c : productobject.getAll()) {
                response.append(c.getSteamLogin())
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

        server.createContext("/importaccounts", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                byte[] bytes = "ERROR: Only POST allowed".getBytes();
                exchange.sendResponseHeaders(405, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
                return;
            }
            String body = new String(exchange.getRequestBody().readAllBytes());
            boolean ok;
            try {
                ok = ProductRepository.importAccounts(dbConnection, body);
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


