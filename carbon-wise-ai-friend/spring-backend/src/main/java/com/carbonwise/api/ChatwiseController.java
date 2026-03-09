package com.carbonwise.api;

import com.carbonwise.domain.ChatwiseDetail;
import com.carbonwise.service.ChatwiseDetailService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chatwise")
@CrossOrigin(origins = {"http://localhost:3000"}, allowCredentials = "true")
public class ChatwiseController {
    private final ChatwiseDetailService service;

    public ChatwiseController(ChatwiseDetailService service) {
        this.service = service;
    }

    @GetMapping("/details")
    public ResponseEntity<?> getDetails(@RequestParam(name = "name", defaultValue = "Chatwise AI") String name) {
        return service.findByName(name)
            .<ResponseEntity<?>>map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.status(404).body(Map.of(
                "success", false,
                "message", "Details not found for name: " + name
            )));
    }
}


