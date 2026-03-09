package com.carbonwise.api;

import com.carbonwise.ai.RecommendationService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {"http://localhost:3000"}, allowCredentials = "true")
public class ApiController {

    private final Map<String, Map<String, Object>> sessions = new HashMap<>();
    private final RecommendationService recommendationService;

    public ApiController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "service", "spring-backend", "time", Instant.now().toString());
    }

    @PostMapping("/calculate")
    public ResponseEntity<Map<String, Object>> calculate(@RequestBody Map<String, Object> body) {
        try {
            // Calculate emissions based on user input
            Map<String, Object> emissions = calculateEmissions(body);
            
            // Generate AI-powered recommendations
            List<Map<String, Object>> recommendations = recommendationService.generateRecommendations(body, emissions);
            
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("emissions", emissions);
            resp.put("recommendations", recommendations);
            resp.put("predictions", generatePredictions(emissions));
            resp.put("worldAverage", 4800);
            resp.put("comparison", Math.round((Double.parseDouble(emissions.get("total").toString()) / 4800.0) * 100));
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            System.err.println("Calculate error: " + e.getMessage());
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "Calculation failed"));
        }
    }

    private Map<String, Object> calculateEmissions(Map<String, Object> userData) {
        // Basic emission factors (kg CO2)
        double transport = 0;
        double home = 0;
        double diet = 0;
        double shopping = 0;

        // Transport calculation
        if (userData.containsKey("transport")) {
            Map<String, Object> transportData = (Map<String, Object>) userData.get("transport");
            double carKm = Double.parseDouble(transportData.getOrDefault("carKm", 0).toString());
            double flightHours = Double.parseDouble(transportData.getOrDefault("flightHours", 0).toString());
            double publicKm = Double.parseDouble(transportData.getOrDefault("publicTransport", 0).toString());
            
            transport = (carKm * 52 * 0.12) + (flightHours * 90) + (publicKm * 52 * 0.03);
        }

        // Home energy calculation
        if (userData.containsKey("home")) {
            Map<String, Object> homeData = (Map<String, Object>) userData.get("home");
            double electricity = Double.parseDouble(homeData.getOrDefault("electricity", 0).toString());
            double gas = Double.parseDouble(homeData.getOrDefault("gas", 0).toString());
            
            home = (electricity * 12 * 0.42) + (gas * 12 * 5.3);
        }

        // Diet calculation
        if (userData.containsKey("diet")) {
            Map<String, Object> dietData = (Map<String, Object>) userData.get("diet");
            String dietType = dietData.getOrDefault("type", "mixed").toString();
            double meatServings = Double.parseDouble(dietData.getOrDefault("meatServings", 0).toString());
            
            Map<String, Double> dietMultipliers = Map.of(
                "vegan", 1.5, "vegetarian", 2.5, "pescatarian", 3.2, 
                "mixed", 4.0, "high-meat", 5.5
            );
            double baseDiet = dietMultipliers.getOrDefault(dietType, 4.0) * 365;
            diet = baseDiet + (meatServings * 52 * 0.5);
        }

        // Shopping calculation
        if (userData.containsKey("shopping")) {
            Map<String, Object> shoppingData = (Map<String, Object>) userData.get("shopping");
            double clothing = Double.parseDouble(shoppingData.getOrDefault("clothing", 0).toString());
            double electronics = Double.parseDouble(shoppingData.getOrDefault("electronics", 0).toString());
            
            shopping = (clothing * 0.03) + (electronics * 0.05);
        }

        Map<String, Object> emissions = new HashMap<>();
        emissions.put("transport", Math.round(transport));
        emissions.put("home", Math.round(home));
        emissions.put("diet", Math.round(diet));
        emissions.put("shopping", Math.round(shopping));
        emissions.put("total", Math.round(transport + home + diet + shopping));
        
        return emissions;
    }

    private List<Map<String, Object>> generatePredictions(Map<String, Object> emissions) {
        double currentTotal = Double.parseDouble(emissions.get("total").toString());
        List<Map<String, Object>> predictions = new ArrayList<>();
        
        for (int i = 1; i <= 12; i++) {
            double predicted = currentTotal * (1 + (i * 0.02)); // 2% increase per month
            predictions.add(Map.of(
                "month", i,
                "predicted", Math.round(predicted),
                "confidence", Math.max(0.5, 1 - (i * 0.05))
            ));
        }
        return predictions;
    }

    @PostMapping("/save-session")
    public ResponseEntity<Map<String, Object>> saveSession(@RequestBody Map<String, Object> body) {
        String id = UUID.randomUUID().toString();
        sessions.put(id, body);
        return ResponseEntity.ok(Map.of("success", true, "sessionId", id));
    }

    @GetMapping("/session/{id}")
    public ResponseEntity<Map<String, Object>> getSession(@PathVariable("id") String id) {
        Map<String, Object> data = sessions.get(id);
        if (data == null) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Not found"));
        }
        return ResponseEntity.ok(Map.of("success", true, "session", data));
    }

    @GetMapping("/report/{userid}")
    public ResponseEntity<byte[]> report(
        @PathVariable("userid") String userId,
        @RequestParam(name = "format", defaultValue = "csv") String format
    ) {
        if (!"csv".equalsIgnoreCase(format)) {
            // Let frontend fallback handle PDF
            return ResponseEntity.status(501).body("PDF generation handled on client".getBytes(StandardCharsets.UTF_8));
        }
        String csv = String.join("\n",
            "Category,Value,Emissions (kg CO2)",
            "Transport, ,1200",
            "Home, ,900",
            "Diet, ,700",
            "Shopping, ,400",
            "Total,,3200"
        );
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=carbon_report.csv")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(bytes);
    }
}


