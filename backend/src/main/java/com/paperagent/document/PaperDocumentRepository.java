package com.paperagent.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaperDocumentRepository extends JpaRepository<PaperDocument, String> {

    List<PaperDocument> findAllByProject_IdOrderByUploadedAtDesc(String projectId);

    Optional<PaperDocument> findByIdAndProject_Id(String id, String projectId);
}
