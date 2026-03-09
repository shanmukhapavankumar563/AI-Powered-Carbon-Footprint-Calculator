package com.carbonwise.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class RecommendationService {
    private final String apiKey;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RecommendationService(@Value("${gemini.api.key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    public List<Map<String, Object>> generateRecommendations(Map<String, Object> userData, Map<String, Object> emissions) {
        try {
            String prompt = buildRecommendationPrompt(userData, emissions);
            String response = callGeminiAPI(prompt);
            return parseRecommendations(response);
        } catch (Exception e) {
            System.err.println("RecommendationService error: " + e.getMessage());
            return getFallbackRecommendations(emissions);
        }
    }

    private String buildRecommendationPrompt(Map<String, Object> userData, Map<String, Object> emissions) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an expert environmental consultant. Analyze this user's carbon footprint data and provide 5-7 personalized, actionable recommendations.\n\n");
        
        prompt.append("USER DATA:\n");
        prompt.append("- Transport: ").append(emissions.get("transport")).append(" kg CO2/year\n");
        prompt.append("- Home Energy: ").append(emissions.get("home")).append(" kg CO2/year\n");
        prompt.append("- Diet: ").append(emissions.get("diet")).append(" kg CO2/year\n");
        prompt.append("- Shopping: ").append(emissions.get("shopping")).append(" kg CO2/year\n");
        prompt.append("- Total: ").append(emissions.get("total")).append(" kg CO2/year\n");
        prompt.append("- World Average: 4800 kg CO2/year\n\n");
        
        prompt.append("Provide recommendations in this JSON format:\n");
        prompt.append("{\n");
        prompt.append("  \"recommendations\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"category\": \"transport|home|diet|shopping|lifestyle\",\n");
        prompt.append("      \"title\": \"Short action title\",\n");
        prompt.append("      \"description\": \"Detailed explanation\",\n");
        prompt.append("      \"impact\": \"Estimated CO2 reduction in kg/year\",\n");
        prompt.append("      \"difficulty\": \"easy|medium|hard\",\n");
        prompt.append("      \"timeframe\": \"immediate|short-term|long-term\",\n");
        prompt.append("      \"cost\": \"free|low|medium|high\",\n");
        prompt.append("      \"actionSteps\": [\"Step 1\", \"Step 2\", \"Step 3\"]\n");
        prompt.append("    }\n");
        prompt.append("  ]\n");
        prompt.append("}\n\n");
        prompt.append("Focus on the highest impact areas and provide practical, achievable steps.");
        
        return prompt.toString();
    }

    private String callGeminiAPI(String prompt) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + apiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> content = new HashMap<>();

        // include role for better compatibility
        content.put("role", "user");

        Map<String, Object> part = new HashMap<>();
        part.put("text", prompt);
        content.put("parts", new Object[]{part});

        requestBody.put("contents", new Object[]{content});

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", 0.7);
        generationConfig.put("maxOutputTokens", 2048);
        requestBody.put("generationConfig", generationConfig);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        var responseEntity = restTemplate.postForEntity(url, request, String.class);
        int status = responseEntity.getStatusCode().value();
        String body = responseEntity.getBody();

        System.out.println("Gemini Reco Status: " + status);
        System.out.println("Gemini Reco Body: " + body);

        if (status < 200 || status >= 300) {
            throw new RuntimeException("Gemini returned status " + status);
        }
        return body;
    }

    private List<Map<String, Object>> parseRecommendations(String response) {
        try {
            JsonNode jsonNode = objectMapper.readTree(response);
            JsonNode candidates = jsonNode.path("candidates");
            if (candidates.isArray() && candidates.size() > 0) {
                JsonNode contentNode = candidates.get(0).path("content");
                JsonNode parts = contentNode.path("parts");
                if (parts.isArray() && parts.size() > 0) {
                    JsonNode text = parts.get(0).path("text");
                    if (text.isTextual()) {
                        String responseText = text.asText();
                        // Try to extract JSON from the response
                        // Strip markdown code fences if present
                        String cleaned = responseText
                            .replaceAll("(?s)```json\\s*", "")
                            .replaceAll("(?s)```", "")
                            .trim();

                        int jsonStart = cleaned.indexOf("{");
                        int jsonEnd = cleaned.lastIndexOf("}") + 1;
                        if (jsonStart >= 0 && jsonEnd > jsonStart) {
                            String jsonStr = cleaned.substring(jsonStart, jsonEnd);
                            JsonNode recommendationsNode = objectMapper.readTree(jsonStr);
                            JsonNode recommendations = recommendationsNode.path("recommendations");
                            if (recommendations.isArray()) {
                                List<Map<String, Object>> result = new ArrayList<>();
                                for (JsonNode rec : recommendations) {
                                    Map<String, Object> recMap = new HashMap<>();
                                    recMap.put("category", rec.path("category").asText());
                                    recMap.put("title", rec.path("title").asText());
                                    recMap.put("description", rec.path("description").asText());
                                    recMap.put("impact", rec.path("impact").asText());
                                    recMap.put("difficulty", rec.path("difficulty").asText());
                                    recMap.put("timeframe", rec.path("timeframe").asText());
                                    recMap.put("cost", rec.path("cost").asText());
                                    recMap.put("actionSteps", rec.path("actionSteps"));
                                    result.add(recMap);
                                }
                                return result;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing recommendations: " + e.getMessage());
        }
        return getFallbackRecommendations(new HashMap<>());
    }

    private List<Map<String, Object>> getFallbackRecommendations(Map<String, Object> emissions) {
        List<Map<String, Object>> recommendations = new ArrayList<>();
        
        recommendations.add(Map.of(
            "category", "transport",
            "title", "Use Public Transportation",
            "description", "Switch to public transport for your daily commute to reduce emissions significantly.",
            "impact", "Reduce by 500-1000 kg CO2/year",
            "difficulty", "easy",
            "timeframe", "immediate",
            "cost", "low",
            "actionSteps", Arrays.asList("Find local bus/train routes", "Get a monthly pass", "Try it for a week")
        ));
        
        recommendations.add(Map.of(
            "category", "home",
            "title", "Switch to LED Bulbs",
            "description", "Replace traditional bulbs with energy-efficient LEDs to reduce electricity consumption.",
            "impact", "Reduce by 200-400 kg CO2/year",
            "difficulty", "easy",
            "timeframe", "immediate",
            "cost", "low",
            "actionSteps", Arrays.asList("Count your current bulbs", "Buy LED replacements", "Install them gradually")
        ));
        
        recommendations.add(Map.of(
            "category", "diet",
            "title", "Reduce Meat Consumption",
            "description", "Try meatless Mondays and reduce overall meat intake to lower your carbon footprint.",
            "impact", "Reduce by 300-600 kg CO2/year",
            "difficulty", "easy",
            "timeframe", "immediate",
            "cost", "free",
            "actionSteps", Arrays.asList("Start with meatless Mondays", "Explore plant-based recipes", "Reduce meat servings by 50%")
        ));
        
        return recommendations;
    }
}
