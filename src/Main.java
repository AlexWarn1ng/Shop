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
        String price = p.getProperty("price");
        if (aesKeyBase64 == null || aesKeyBase64.isEmpty()) {
            throw new RuntimeException("aes_key is missing in config.txt");
        }
        SecretKey aesKey = CryptoUtil.keyFromBase64(aesKeyBase64);

        try{
            if (price == null || username == null || password == null || addressHost == null || database == null || port == null || port.isEmpty() || username.isEmpty() || password.isEmpty() || addressHost.isEmpty() || database.isEmpty() || price.isEmpty()){
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
            System.out.println("price (PER UNIT)=" + price);

        DataBaseConnector dbConnection = new DataBaseConnector(username, password, addressHost, port, database);
        HttpServer server;

        Double pricePerUnit = Double.parseDouble(price); // CREATE NEW PRICE VAR AND MAKE IT DOUBLE

        try {
            server = HttpServer.create(new InetSocketAddress(8080), 0);
            server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(8));
        } catch (IOException e) {
            throw new RuntimeException("ERROR: server not created", e);
        }


        ProductRepository productobject = new ProductRepository(dbConnection, aesKey);
        NowPaymentsApi nowObjectForApi = new NowPaymentsApi("RNFVZ6Y-GXHMTR3-KYQZ5D8-8BQ1QMS");

        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";
            Path file = Path.of("web" + path);
            if (!Files.exists(file)) {
                exchange.sendResponseHeaders(404, 0);
                exchange.close();
                return;
            }
            exchange.getResponseHeaders()
                    .add("Content-Type", Files.probeContentType(file));
            byte[] data = Files.readAllBytes(file);
            exchange.sendResponseHeaders(200, data.length);
            exchange.getResponseBody().write(data);
            exchange.close();
        });



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
                ok = productobject.deleteProducts(body);
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

        server.createContext("/checkmafileexistence", exchange -> {
            if (!"PATCH".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            boolean ok = productobject.checkMafileExistence();
            String result = ok ? "OK" : "ERROR";
            byte[] resp = result.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(ok ? 200 : 500, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });


        server.createContext("/buyproducts", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }

            String result;
            int httpCode = 200;

            try {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
                int count = Integer.parseInt(body);
                if (count <= 0) throw new IllegalArgumentException("count must be > 0");

                String reservedIds;
                String transactionKey;

                try (BuyProduct currentPurchase = new BuyProduct(aesKeyBase64, username, password, addressHost, port, database)) {
                    currentPurchase.begin();
                    reservedIds = currentPurchase.reserveProducts(count);
                    transactionKey = currentPurchase.getTransactionKey();
                    currentPurchase.commit();
                }

                if (reservedIds == null || reservedIds.isBlank()) {
                    result = "ERROR: not enough products to reserve (Zero products exception)";
                    System.out.println(result);
                    httpCode = 409;
                } else {
                    double total = count * pricePerUnit;
                    String orderId = NowPaymentsApi.newOrderId();
                    String desc = "Accounts x" + count;

                    String packed = nowObjectForApi.createInvoice(total, orderId, desc);
                    String[] parts = packed.split("\\|", 2);
                    String invoiceId = parts[0];
                    String invoiceUrl = parts[1];

                    String payCurrency = "trx";
                    long paymentId = nowObjectForApi.createPaymentByInvoice(invoiceId, payCurrency);
                    productobject.savePendingOrder(orderId, transactionKey, count, total, invoiceUrl, invoiceId);
                    result =
                            "OK\n" +
                                    "transaction_key=" + transactionKey + "\n" +
                                    "order_id=" + orderId + "\n" +
                                    "invoice_id=" + invoiceId + "\n" +
                                    "payment_id=" + paymentId + "\n" +
                                    "pay_currency=" + payCurrency + "\n" +
                                    "invoice_url=" + invoiceUrl + "\n" +
                                    "amount_usd=" + total + "\n" +
                                    "reserved_ids=\n" + reservedIds;

                    System.out.println(result);
                }
            } catch (Exception e) {
                e.printStackTrace();
                result = "ERROR: " + e.getMessage();
                httpCode = 500;
            }
            byte[] resp = result.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(httpCode, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });


        server.createContext("/checkpayment", exchange -> {
                    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                        exchange.sendResponseHeaders(405, -1);
                        exchange.close();
                        return;
                    }
                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
                    long paymentId;
                    try {
                        paymentId = Long.parseLong(body);
                    } catch (Exception e) {
                        byte[] resp = "ERROR: send payment_id as a NUMBER".getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(400, resp.length);
                        exchange.getResponseBody().write(resp);
                        exchange.close();
                        return;
                    }

                    try(BuyProduct currentPurchase = new BuyProduct(aesKeyBase64, username, password, addressHost, port, database)) {
                        String status = nowObjectForApi.checkPaymentStatus(paymentId);

                        if ("finished".equalsIgnoreCase(status) || "confirmed".equalsIgnoreCase(status) || "paid".equalsIgnoreCase(status)) {
                            currentPurchase.begin();
                            String transkey = currentPurchase.getTransactionKey();
                            String ids = productobject.getReservedIdsByTransactionKey(transkey);

                            productobject.markOrderPaid(transkey);
                            currentPurchase.moveProductsInSoldTable(ids);
                            productobject.moveMafilesToSold(ids);

                            String result = "OK\npayment_id=" + paymentId + "\nstatus=" + status + "\naction=FULFILLED";
                            byte[] resp = result.getBytes(StandardCharsets.UTF_8);
                            exchange.sendResponseHeaders(200, resp.length);
                            exchange.getResponseBody().write(resp);
                            exchange.close();
                            return;
                        }
                        String result = "OK\npayment_id=" + paymentId + "\nstatus=" + status;
                        byte[] resp = result.getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(200, resp.length);
                        exchange.getResponseBody().write(resp);
                        exchange.close();

                    } catch (Exception e) {
                        String result = "ERROR: " + e.getMessage();
                        byte[] resp = result.getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(500, resp.length);
                        exchange.getResponseBody().write(resp);
                        exchange.close();
                    }
        });

            server.start();
        System.out.println("Server started on http://localhost:8080");
    }
}


