package com.paperagent.document;

import com.paperagent.common.ResourceNotFoundException;
import com.paperagent.project.PaperProject;
import com.paperagent.project.PaperProjectRepository;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/** 文档业务层：连接项目、文件存储和数据库记录。 */
@Service
public class PaperDocumentService {

    private final PaperProjectRepository projectRepository;
    private final PaperDocumentRepository documentRepository;
    private final DocumentStorageService storageService;

    public PaperDocumentService(
            PaperProjectRepository projectRepository,
            PaperDocumentRepository documentRepository,
            DocumentStorageService storageService
    ) {
        this.projectRepository = projectRepository;
        this.documentRepository = documentRepository;
        this.storageService = storageService;
    }

    @Transactional
    public PaperDocumentResponse upload(String projectId, MultipartFile file) {
        PaperProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("论文项目不存在"));

        String documentId = UUID.randomUUID().toString();
        StoredDocument stored = storageService.store(projectId, documentId, file);

        try {
            PaperDocument document = new PaperDocument(
                    documentId,
                    project,
                    stored.originalFileName(),
                    stored.relativePath(),
                    stored.contentType(),
                    stored.fileSize()
            );
            return PaperDocumentResponse.from(documentRepository.save(document));
        } catch (RuntimeException exception) {
            storageService.deleteQuietly(stored.relativePath());
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<PaperDocumentResponse> findAll(String projectId) {
        requireProject(projectId);
        return documentRepository.findAllByProject_IdOrderByUploadedAtDesc(projectId)
                .stream()
                .map(PaperDocumentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DownloadableDocument download(String projectId, String documentId) {
        PaperDocument document = documentRepository.findByIdAndProject_Id(documentId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("论文文档不存在"));
        Resource resource = storageService.load(document.getStoredRelativePath());
        return new DownloadableDocument(
                document.getOriginalFileName(),
                document.getContentType(),
                resource
        );
    }

    private void requireProject(String projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("论文项目不存在");
        }
    }

    public record DownloadableDocument(String fileName, String contentType, Resource resource) {
    }
}
