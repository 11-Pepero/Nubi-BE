package dev.hidly.nubi.document.data.repository;

import dev.hidly.nubi.document.data.entity.ApplicationDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApplicationDocumentRepository extends JpaRepository<ApplicationDocument, Long> {
    List<ApplicationDocument> findAllByApplicationIdOrderByIdAsc(Long applicationId);
}
