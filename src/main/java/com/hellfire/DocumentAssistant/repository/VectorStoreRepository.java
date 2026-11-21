package com.hellfire.DocumentAssistant.repository;

import com.hellfire.DocumentAssistant.entity.VectorStoreEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VectorStoreRepository extends JpaRepository<VectorStoreEntry, UUID> {
}
