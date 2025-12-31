import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class NowPaymentsApi {
    private final HttpClient http = HttpClient.newHttpClient();
    private final String apiKey;

    public NowPaymentsApi(String apiKey) {
        this.apiKey = apiKey;
    }

    public String checkPaymentStatus(long paymentId) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.nowpayments.io/v1/payment/" + paymentId))
                .header("x-api-key", apiKey)
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) throw new RuntimeException(resp.body());
        String s = extractJsonString(resp.body(), "payment_status");
        return s == null ? "" : s;
    }


    public String createInvoice(double priceAmount, String orderId, String description) throws Exception {
        String json = "{"
                + "\"price_amount\":" + priceAmount + ","
                + "\"price_currency\":\"usd\","
                + "\"order_id\":\"" + escape(orderId) + "\","
                + "\"order_description\":\"" + escape(description) + "\""
                + "}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.nowpayments.io/v1/invoice"))
                .header("x-api-key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200 && resp.statusCode() != 201) throw new RuntimeException(resp.body());

        String invoiceUrl = extractJsonString(resp.body(), "invoice_url");
        String invoiceId  = extractJsonString(resp.body(), "id");  // <-- ВОТ ЭТО ВАЖНО

        if (invoiceId == null || invoiceUrl == null) throw new RuntimeException("Bad response: " + resp.body());

        return invoiceId + "|" + invoiceUrl;
    }



    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String extractJsonString(String json, String key) {
        String needle = "\"" + key + "\"";
        int i = json.indexOf(needle);
        if (i < 0) return null;
        int colon = json.indexOf(":", i);
        if (colon < 0) return null;
        int firstQuote = json.indexOf("\"", colon + 1);
        if (firstQuote < 0) return null;
        int secondQuote = json.indexOf("\"", firstQuote + 1);
        if (secondQuote < 0) return null;
        return json.substring(firstQuote + 1, secondQuote);
    }

    public static String extractIid(String url) {
        int i = url.indexOf("iid=");
        if (i < 0) return null;
        String s = url.substring(i + 4);
        int amp = s.indexOf("&");
        if (amp >= 0) s = s.substring(0, amp);
        return s.trim();
    }

    public static String newOrderId() {
        return "ORDER_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    public long createPaymentByInvoice(String invoiceId, String payCurrency) throws Exception {
        String json = "{"
                + "\"iid\":\"" + escape(invoiceId) + "\","
                + "\"pay_currency\":\"" + escape(payCurrency) + "\""
                + "}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.nowpayments.io/v1/invoice-payment"))
                .header("x-api-key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200 && resp.statusCode() != 201) throw new RuntimeException(resp.body());

        Long pid = extractJsonLong(resp.body(), "payment_id");
        if (pid == null) throw new RuntimeException("payment_id not found: " + resp.body());

        return pid;
    }


    // У тебя extractJsonString не достанет число payment_id, поэтому добавь это:
    private static Long extractJsonLong(String json, String key) {
        String needle = "\"" + key + "\"";
        int i = json.indexOf(needle);
        if (i < 0) return null;

        int colon = json.indexOf(":", i);
        if (colon < 0) return null;

        int j = colon + 1;
        while (j < json.length() && Character.isWhitespace(json.charAt(j))) j++;

        if (j < json.length() && json.charAt(j) == '"') {
            int start = j + 1;
            int end = json.indexOf('"', start);
            if (end < 0) return null;
            String s = json.substring(start, end).trim();
            if (s.isEmpty()) return null;
            return Long.parseLong(s);
        }

        int start = j;
        while (j < json.length() && Character.isDigit(json.charAt(j))) j++;
        if (start == j) return null;

        return Long.parseLong(json.substring(start, j));
    }





}
