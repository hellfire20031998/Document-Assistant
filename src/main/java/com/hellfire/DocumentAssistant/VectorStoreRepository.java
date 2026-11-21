package com.hellfire.DocumentAssistant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VectorStoreRepository extends JpaRepository<VectorStoreEntry, UUID> {
}
