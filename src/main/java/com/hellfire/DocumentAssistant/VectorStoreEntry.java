package com.hellfire.DocumentAssistant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.util.UUID;

/**
 * Read-only view of a row in the vector_store table.
 * The table is owned by Spring AI's PgVectorStore, which inserts rows (including the
 * embedding column, intentionally not mapped here). @Immutable stops Hibernate from writing to it.
 */
@Entity
@Immutable
@Table(name = "vector_store")
public class VectorStoreEntry {

    @Id
    private UUID id;

    @Column(columnDefinition = "text")
    private String content;

    protected VectorStoreEntry() {
    }

    public UUID getId() {
        return id;
    }

    public String getContent() {
        return content;
    }
}
