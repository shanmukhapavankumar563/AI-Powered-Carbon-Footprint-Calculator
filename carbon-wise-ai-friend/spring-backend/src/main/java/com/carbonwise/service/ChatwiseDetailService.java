package com.carbonwise.service;

import com.carbonwise.domain.ChatwiseDetail;
import com.carbonwise.repository.ChatwiseDetailRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ChatwiseDetailService {
    private final ChatwiseDetailRepository repository;

    public ChatwiseDetailService(ChatwiseDetailRepository repository) {
        this.repository = repository;
    }

    public Optional<ChatwiseDetail> findByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return repository.findByNameIgnoreCase(name.trim());
    }
}


