import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.Map;

public class blood {

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);

        // Serve static HTML pages
        server.createContext("/", new StaticFileHandler());

        // Register handlers for form submission
        server.createContext("/submitDonor", new DonorHandler());
        server.createContext("/submitHospital", new HospitalHandler());
        server.createContext("/submitPatient", new PatientHandler());
        server.createContext("/submitContact", new ContactHandler());
        server.createContext("/bloodBankData", new BloodBankDisplayHandler());

        server.setExecutor(null);
        System.out.println("Server started on http://localhost:8000/");
        server.start();
    }

    // Handler to serve static HTML files from src/frontend
    static class StaticFileHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();

            if (path.equals("/")) {
                path = "/dbms.html"; // default page
            }

            Path filePath = Paths.get("src/frontend" + path).toAbsolutePath();

            if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
                String notFound = "404 Not Found";
                exchange.sendResponseHeaders(404, notFound.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(notFound.getBytes());
                }
                return;
            }

            byte[] fileBytes = Files.readAllBytes(filePath);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, fileBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(fileBytes);
            }
        }
    }

    // --- Database connection ---
    private static Connection getConnection() throws Exception {
        String url = "jdbc:oracle:thin:@localhost:1521:xe";
        String user = "system";
        String password = "manager";
        Class.forName("oracle.jdbc.driver.OracleDriver");
        return DriverManager.getConnection(url, user, password);
    }

    // --- Utility to parse form data ---
    private static Map<String, String> parseForm(InputStream requestBody) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(requestBody, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);

        String[] pairs = sb.toString().split("&");
        Map<String, String> data = new HashMap<>();
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            if (idx > 0 && idx < pair.length() - 1) {
                String key = URLDecoder.decode(pair.substring(0, idx), "UTF-8");
                String value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
                data.put(key, value);
            }
        }
        return data;
    }

    private static void sendResponse(HttpExchange exchange, String responseText) throws IOException {
        exchange.sendResponseHeaders(200, responseText.getBytes(StandardCharsets.UTF_8).length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseText.getBytes(StandardCharsets.UTF_8));
        }
    }

    // --- Handlers ---
    static class BloodBankDisplayHandler implements HttpHandler {
    public void handle(HttpExchange exchange) throws IOException {
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            try (Connection conn = getConnection()) {
                PreparedStatement ps = conn.prepareStatement("SELECT * FROM BLOOD_BANK");
                var rs = ps.executeQuery();

                StringBuilder json = new StringBuilder();
                json.append("[");

                while (rs.next()) {
                    json.append(String.format(
                        "{\"B_ID\":%d,\"B_LOC\":\"%s\",\"B_AVAIL\":%d,\"D_ID\":%d},",
                        rs.getInt("B_ID"),
                        rs.getString("B_LOC"),
                        rs.getInt("B_AVAIL"),
                        rs.getInt("D_ID")
                    ));
                }
                
                if (json.charAt(json.length() - 1) == ',') {
                    json.setLength(json.length() - 1); // remove trailing comma
                }

                json.append("]");

                exchange.getResponseHeaders().set("Content-Type", "application/json");
                byte[] responseBytes = json.toString().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, responseBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }
            } catch (Exception e) {
                e.printStackTrace();
                String errorMsg = "{\"error\":\"Failed to fetch blood bank data\"}";
                byte[] errorBytes = errorMsg.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(500, errorBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(errorBytes);
                }
            }
        } else {
            exchange.sendResponseHeaders(405, -1); // Method Not Allowed
        }
    }
}

    static class DonorHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, String> data = parseForm(exchange.getRequestBody());
                try (Connection conn = getConnection()) {
                    PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO BLOOD_DONAR (D_ID, D_NAME, D_AGE, D_TYPE, NO_OF_LTRS) VALUES (?, ?, ?, ?, ?)");
                    ps.setInt(1, Integer.parseInt(data.get("donorId")));
                    ps.setString(2, data.get("donorName"));
                    ps.setInt(3, Integer.parseInt(data.get("donorAge")));
                    ps.setString(4, data.get("donorType"));
                    ps.setInt(5, Integer.parseInt(data.get("litres")));
                    ps.executeUpdate();
                    sendResponse(exchange, "Donor added successfully.");
                } catch (Exception e) {
                    e.printStackTrace();
                    sendResponse(exchange, "Error adding donor: " + e.getMessage());
                }
            }
        }
    }

    static class HospitalHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, String> data = parseForm(exchange.getRequestBody());
                try (Connection conn = getConnection()) {
                    PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO HOSPITAL (H_ID, H_NAME, H_LOC, H_REQUIREMENT, NO_OF_PAT, B_ID) VALUES (?, ?, ?, ?, ?, ?)");
                    ps.setInt(1, Integer.parseInt(data.get("hospitalId")));
                    ps.setString(2, data.get("hospitalName"));
                    ps.setString(3, data.get("hospitalLocation"));
                    ps.setInt(4, Integer.parseInt(data.get("requirement")));
                    ps.setInt(5, Integer.parseInt(data.get("noOfPatients")));
                    ps.setInt(6, Integer.parseInt(data.get("bankId")));
                    ps.executeUpdate();
                    sendResponse(exchange, "Hospital added successfully.");
                } catch (Exception e) {
                    e.printStackTrace();
                    sendResponse(exchange, "Error adding hospital: " + e.getMessage());
                }
            }
        }
    }

    static class PatientHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, String> data = parseForm(exchange.getRequestBody());
                try (Connection conn = getConnection()) {
                    PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO PATIENT (P_ID, P_NAME, P_AGE, B_TYPE, P_REQUIREMENT, H_ID) VALUES (?, ?, ?, ?, ?, ?)");
                    ps.setInt(1, Integer.parseInt(data.get("patientId")));
                    ps.setString(2, data.get("patientName"));
                    ps.setInt(3, Integer.parseInt(data.get("patientAge")));
                    ps.setString(4, data.get("bloodType"));
                    ps.setInt(5, Integer.parseInt(data.get("requirement")));
                    ps.setInt(6, Integer.parseInt(data.get("hospitalId")));
                    ps.executeUpdate();
                    sendResponse(exchange, "Patient added successfully.");
                } catch (Exception e) {
                    e.printStackTrace();
                    sendResponse(exchange, "Error adding patient: " + e.getMessage());
                }
            }
        }
    }

    static class ContactHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, String> data = parseForm(exchange.getRequestBody());
                System.out.println("Contact Form:\nName: " + data.get("name") + "\nEmail: " + data.get("email") + "\nMessage: " + data.get("message"));
                sendResponse(exchange, "Thank you for contacting us!");
            }
        }
    }
}
