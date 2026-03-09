package com.carbonwise.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class ChatService {
    private final String apiKey;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatService(@Value("${gemini.api.key:}") String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Gemini API key is required. Set gemini.api.key in application.properties or GEMINI_API_KEY environment variable.");
        }
        this.apiKey = apiKey;
    }

    public String reply(String message, String contextJson) {
        try {
            // Enhanced prompt for carbon footprint assistance
            String systemPrompt = "You are an expert environmental consultant and carbon footprint advisor. " +
                "Provide helpful, actionable advice about reducing carbon footprints. " +
                "Be encouraging, specific, and practical. Keep responses concise but informative.";
            
            String userPrompt = (contextJson == null || contextJson.isBlank())
                ? message
                : "Context about user's carbon footprint:\n" + contextJson + "\n\nUser question: " + message;

            String fullPrompt = systemPrompt + "\n\n" + userPrompt;

            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + apiKey;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            Map<String, Object> content = new HashMap<>();

            // Include role for compatibility with some Gemini endpoints
            content.put("role", "user");

            Map<String, Object> part = new HashMap<>();
            part.put("text", fullPrompt);
            content.put("parts", new Object[]{part});

            requestBody.put("contents", new Object[]{content});
            
            // Add generation config for better responses
            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("temperature", 0.7);
            generationConfig.put("topK", 40);
            generationConfig.put("topP", 0.95);
            generationConfig.put("maxOutputTokens", 1024);
            requestBody.put("generationConfig", generationConfig);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            // Use exchange to capture status code and body
            var responseEntity = restTemplate.postForEntity(url, request, String.class);
            int status = responseEntity.getStatusCode().value();
            String response = responseEntity.getBody();

            System.out.println("Gemini API Status: " + status);
            System.out.println("Gemini API Response Body: " + response);

            if (status < 200 || status >= 300) {
                return "I'm sorry, the AI service returned an error (status " + status + "). Please try again later.";
            }

            if (response != null) {
                JsonNode jsonNode = objectMapper.readTree(response);
                JsonNode candidates = jsonNode.path("candidates");
                if (candidates.isArray() && candidates.size() > 0) {
                    JsonNode contentNode = candidates.get(0).path("content");
                    JsonNode parts = contentNode.path("parts");
                    if (parts.isArray() && parts.size() > 0) {
                        JsonNode text = parts.get(0).path("text");
                        if (text.isTextual()) {
                            return text.asText();
                        }
                    }
                }
                // some responses return directly in candidates[0].content.parts[0].text
                // or in promptFeedback/safetyRatings errors
                // Check for errors in the response
                JsonNode error = jsonNode.path("error");
                if (!error.isMissingNode()) {
                    System.err.println("Gemini API Error: " + error.toString());
                    return "I'm sorry, there was an error with the AI service. Please try again.";
                }
            }
            
            return "I'm sorry, I couldn't generate a response. Please try again.";
        } catch (Exception e) {
            System.err.println("ChatService error: " + e.getMessage());
            e.printStackTrace();
            return "I'm sorry, I'm having trouble connecting right now. Please try again later.";
        }
    }
}


