package com.sgd_hc.documents.repository;

import com.sgd_hc.documents.entity.DocumentOcrMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DocumentOcrMetadataRepository 
        extends JpaRepository<DocumentOcrMetadata, UUID> {

    Optional<DocumentOcrMetadata> findByDocumentId(UUID documentId);
    boolean existsByDocumentId(UUID documentId);
}