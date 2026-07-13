package com.example.agenticrag.infra.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Catálogo de componentes já ingeridos — dá idempotência e auditoria à ingestão. */
@Repository
public class ComponentRegistryRepository {

    private final JdbcClient jdbc;

    public ComponentRegistryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> findSourceHash(String componentId) {
        return jdbc.sql("SELECT source_hash FROM component WHERE id = :id")
                .param("id", componentId)
                .query(String.class)
                .optional();
    }

    public void upsert(String id, String name, String version, String description, String sourceHash) {
        jdbc.sql("""
                INSERT INTO component (id, name, version, description, source_hash, last_ingested)
                VALUES (:id, :name, :version, :description, :hash, now())
                ON CONFLICT (id) DO UPDATE SET
                    name = EXCLUDED.name,
                    version = EXCLUDED.version,
                    description = EXCLUDED.description,
                    source_hash = EXCLUDED.source_hash,
                    last_ingested = now()
                """)
                .param("id", id)
                .param("name", name)
                .param("version", version)
                .param("description", description)
                .param("hash", sourceHash)
                .update();
    }
}
