package com.paperagent.document;

import com.paperagent.ai.AiSuggestionRequest;
import com.paperagent.agent.AgentOrchestrator;
import com.paperagent.project.PaperProject;
import com.paperagent.project.PaperProjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaperDocumentServiceTest {

    @Mock
    private PaperProjectRepository projectRepository;

    @Mock
    private PaperDocumentRepository documentRepository;

    @Mock
    private DocumentStorageService storageService;

    @Mock
    private LatexCompileService latexCompileService;

    @Mock
    private AgentOrchestrator agentOrchestrator;

    @Test
    void uploadShouldConnectTheStoredFileToItsProject() {
        PaperProject project = new PaperProject("毕业论文", "格式修改练习");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "论文.docx",
                DocumentStorageService.DOCX_CONTENT_TYPE,
                new byte[]{1}
        );
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(storageService.store(any(), any(), any())).thenReturn(new StoredDocument(
                "论文.docx",
                "documents/example/original.docx",
                1024,
                DocumentStorageService.DOCX_CONTENT_TYPE
        ));
        when(documentRepository.save(any(PaperDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaperDocumentService service = new PaperDocumentService(
                projectRepository,
                documentRepository,
                storageService,
                latexCompileService,
                agentOrchestrator
        );
        PaperDocumentResponse response = service.upload(project.getId(), file);

        ArgumentCaptor<PaperDocument> captor = ArgumentCaptor.forClass(PaperDocument.class);
        verify(documentRepository).save(captor.capture());
        assertThat(captor.getValue().getProject()).isSameAs(project);
        assertThat(response.projectId()).isEqualTo(project.getId());
        assertThat(response.originalFileName()).isEqualTo("论文.docx");
    }

    @Test
    void aiSuggestionShouldRejectTextNotInSelectedSourceFile() {
        PaperProject project = new PaperProject("论文", "测试");
        PaperDocument document = new PaperDocument(
                "document-1",
                project,
                "main.tex",
                "documents/project-1/document-1/source.tex",
                DocumentStorageService.LATEX_CONTENT_TYPE,
                100
        );
        DocumentStorageService.LatexWorkspace workspace = new DocumentStorageService.LatexWorkspace(
                Path.of("workspace"),
                Path.of("workspace/main.tex"),
                Path.of("workspace/main.pdf"),
                Path.of("preview")
        );
        when(documentRepository.findByIdAndProject_Id(document.getId(), project.getId()))
                .thenReturn(Optional.of(document));
        when(storageService.resolveLatexWorkspace(any(), any())).thenReturn(workspace);
        when(latexCompileService.readSource(workspace, "main.tex"))
                .thenReturn(new SourceFileResponse("main.tex", "只允许发送这段真实源码"));

        PaperDocumentService service = new PaperDocumentService(
                projectRepository,
                documentRepository,
                storageService,
                latexCompileService,
                agentOrchestrator
        );

        assertThatThrownBy(() -> service.suggestWithAi(
                project.getId(),
                document.getId(),
                new AiSuggestionRequest("main.tex", "不在源码中的文本", "POLISH", "deepseek-flash")
        )).isInstanceOf(DocumentValidationException.class);
        verifyNoInteractions(agentOrchestrator);
    }
}
