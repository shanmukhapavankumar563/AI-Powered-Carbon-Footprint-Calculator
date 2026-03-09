package com.carbonwise.config;

import com.carbonwise.domain.ChatwiseDetail;
import com.carbonwise.repository.ChatwiseDetailRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataSeeder {
    @Bean
    ApplicationRunner seedChatwise(ChatwiseDetailRepository repository) {
        return args -> {
            repository.findByNameIgnoreCase("Chatwise AI").orElseGet(() -> {
                ChatwiseDetail detail = new ChatwiseDetail();
                detail.setName("Chatwise AI");
                detail.setDescription("Chatwise AI is an AI assistant focused on carbon-wise insights, helping users understand and reduce emissions.");
                detail.setWebsite("https://example.com/chatwise-ai");
                return repository.save(detail);
            });
        };
    }
}


