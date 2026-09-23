package com.paperagent.document;

import com.paperagent.project.PaperProject;
import com.paperagent.project.PaperProjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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
                latexCompileService
        );
        PaperDocumentResponse response = service.upload(project.getId(), file);

        ArgumentCaptor<PaperDocument> captor = ArgumentCaptor.forClass(PaperDocument.class);
        verify(documentRepository).save(captor.capture());
        assertThat(captor.getValue().getProject()).isSameAs(project);
        assertThat(response.projectId()).isEqualTo(project.getId());
        assertThat(response.originalFileName()).isEqualTo("论文.docx");
    }
}
