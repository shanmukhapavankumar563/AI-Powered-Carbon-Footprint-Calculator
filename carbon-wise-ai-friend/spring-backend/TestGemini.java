import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

public class TestGemini {
    public static void main(String[] args) {
        String apiKey = "AIzaSyCLcvJkUu938MkZVrgEbAE0wjOzz0526Zw";
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + apiKey;
        
        try {
            // Create request body
            Map<String, Object> requestBody = new HashMap<>();
            Map<String, Object> content = new HashMap<>();
            content.put("role", "user");
            Map<String, Object> part = new HashMap<>();
            part.put("text", "Hello, can you help me with carbon footprint reduction?");
            content.put("parts", new Object[]{part});
            requestBody.put("contents", new Object[]{content});
            
            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("temperature", 0.7);
            generationConfig.put("maxOutputTokens", 1024);
            requestBody.put("generationConfig", generationConfig);
            
            // Make HTTP request
            URL urlObj = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            
            // Send request
            ObjectMapper mapper = new ObjectMapper();
            String jsonInput = mapper.writeValueAsString(requestBody);
            System.out.println("Request JSON: " + jsonInput);
            
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInput.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            // Get response
            int responseCode = conn.getResponseCode();
            System.out.println("Response Code: " + responseCode);
            
            String response;
            if (responseCode >= 200 && responseCode < 300) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    response = br.lines().collect(java.util.stream.Collectors.joining("\n"));
                }
            } else {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
                    response = br.lines().collect(java.util.stream.Collectors.joining("\n"));
                }
            }
            
            System.out.println("Response: " + response);
            try {
                ObjectMapper mapper2 = new ObjectMapper();
                JsonNode root = mapper2.readTree(response);
                if (root.has("error")) {
                    System.err.println("API error: " + root.get("error").toString());
                } else if (root.path("candidates").isArray()
                        && root.path("candidates").size() > 0
                        && root.path("candidates").get(0).path("content").path("parts").isArray()
                        && root.path("candidates").get(0).path("content").path("parts").size() > 0) {
                    System.out.println("Text: " + root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText());
                }
            } catch (Exception ignore) {}
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
