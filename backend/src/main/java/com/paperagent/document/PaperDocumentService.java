package com.paperagent.document;

import com.paperagent.ai.AiSuggestionRequest;
import com.paperagent.ai.AiSuggestionResponse;
import com.paperagent.agent.AgentOrchestrator;
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
    private final LatexCompileService latexCompileService;
    private final AgentOrchestrator agentOrchestrator;

    public PaperDocumentService(
            PaperProjectRepository projectRepository,
            PaperDocumentRepository documentRepository,
            DocumentStorageService storageService,
            LatexCompileService latexCompileService,
            AgentOrchestrator agentOrchestrator
    ) {
        this.projectRepository = projectRepository;
        this.documentRepository = documentRepository;
        this.storageService = storageService;
        this.latexCompileService = latexCompileService;
        this.agentOrchestrator = agentOrchestrator;
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
        PaperDocument document = requireDocument(projectId, documentId);
        Resource resource = storageService.load(document.getStoredRelativePath());
        return new DownloadableDocument(
                document.getOriginalFileName(),
                document.getContentType(),
                resource
        );
    }

    @Transactional(readOnly = true)
    public LatexCompilationResponse compileLatex(String projectId, String documentId) {
        PaperDocument document = requireDocument(projectId, documentId);
        return latexCompileService.compile(workspaceOf(document));
    }

    @Transactional(readOnly = true)
    public PdfPreviewResponse getPdfPreview(String projectId, String documentId) {
        PaperDocument document = requireDocument(projectId, documentId);
        return latexCompileService.getPreview(workspaceOf(document));
    }

    @Transactional(readOnly = true)
    public Resource getPdfPreviewPage(String projectId, String documentId, int pageNumber) {
        PaperDocument document = requireDocument(projectId, documentId);
        return latexCompileService.loadPreviewPage(workspaceOf(document), pageNumber);
    }

    @Transactional(readOnly = true)
    public Resource getPdf(String projectId, String documentId) {
        PaperDocument document = requireDocument(projectId, documentId);
        return latexCompileService.loadPdf(workspaceOf(document));
    }

    @Transactional(readOnly = true)
    public SourceFileResponse getSource(String projectId, String documentId, String path) {
        PaperDocument document = requireDocument(projectId, documentId);
        return latexCompileService.readSource(workspaceOf(document), path);
    }

    @Transactional(readOnly = true)
    public SyncTexResponse syncFromPdf(
            String projectId,
            String documentId,
            int page,
            double x,
            double y
    ) {
        PaperDocument document = requireDocument(projectId, documentId);
        return latexCompileService.syncFromPdf(workspaceOf(document), page, x, y);
    }

    @Transactional(readOnly = true)
    public void openPdf(String projectId, String documentId) {
        PaperDocument document = requireDocument(projectId, documentId);
        latexCompileService.openPdf(workspaceOf(document));
    }

    public AiSuggestionResponse suggestWithAi(
            String projectId,
            String documentId,
            AiSuggestionRequest request
    ) {
        PaperDocument document = requireDocument(projectId, documentId);
        SourceFileResponse source = latexCompileService.readSource(
                workspaceOf(document),
                request.sourcePath()
        );
        String normalizedSelection = normalizeLineEndings(request.selectedText());
        if (normalizedSelection.isBlank()
                || !normalizeLineEndings(source.content()).contains(normalizedSelection)) {
            throw new DocumentValidationException("选中的内容与当前 LaTeX 源文件不一致，请重新选择");
        }
        // 模型调用可能较慢；不要把数据库事务保持到两个 Agent 完成之后。
        return agentOrchestrator.suggest(
                source.path(),
                normalizedSelection,
                request.mode(),
                request.model()
        );
    }

    private String normalizeLineEndings(String text) {
        return text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private DocumentStorageService.LatexWorkspace workspaceOf(PaperDocument document) {
        return storageService.resolveLatexWorkspace(
                document.getStoredRelativePath(),
                document.getContentType()
        );
    }

    private PaperDocument requireDocument(String projectId, String documentId) {
        return documentRepository.findByIdAndProject_Id(documentId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("论文文档不存在"));
    }

    private void requireProject(String projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("论文项目不存在");
        }
    }

    public record DownloadableDocument(String fileName, String contentType, Resource resource) {
    }
}
