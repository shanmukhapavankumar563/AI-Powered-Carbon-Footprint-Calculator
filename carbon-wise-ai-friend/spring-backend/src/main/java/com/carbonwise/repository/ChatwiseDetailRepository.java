package com.carbonwise.repository;

import com.carbonwise.domain.ChatwiseDetail;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatwiseDetailRepository extends JpaRepository<ChatwiseDetail, Long> {
    Optional<ChatwiseDetail> findByNameIgnoreCase(String name);
}


