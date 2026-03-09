package com.carbonwise.api;

import com.carbonwise.ai.ChatService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = {"http://localhost:3000"}, allowCredentials = "true")
public class ChatController {
    private final ChatService chat;

    public ChatController(ChatService chat) {
        this.chat = chat;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, Object> body) {
        String message = String.valueOf(body.getOrDefault("message", ""));
        String context = body.get("context") == null ? "" : body.get("context").toString();
        if (message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "message required"));
        }
        String reply = chat.reply(message, context);
        return ResponseEntity.ok(Map.of("success", true, "reply", reply));
    }
}


