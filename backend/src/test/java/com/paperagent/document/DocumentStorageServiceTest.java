package com.paperagent.document;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentStorageServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void storeShouldSaveAValidDocxUnderTheProjectDirectory() throws IOException {
        DocumentStorageService service = new DocumentStorageService(temporaryDirectory.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "论文初稿.docx",
                DocumentStorageService.DOCX_CONTENT_TYPE,
                createMinimalDocx()
        );

        StoredDocument stored = service.store("project-1", "document-1", file);

        assertThat(stored.originalFileName()).isEqualTo("论文初稿.docx");
        assertThat(stored.relativePath())
                .isEqualTo(Path.of("documents", "project-1", "document-1", "original.docx").toString());
        assertThat(Files.isRegularFile(temporaryDirectory.resolve(stored.relativePath()))).isTrue();
    }

    @Test
    void storeShouldRejectAFileThatOnlyHasADocxExtension() {
        DocumentStorageService service = new DocumentStorageService(temporaryDirectory.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "伪造文档.docx",
                DocumentStorageService.DOCX_CONTENT_TYPE,
                "not a zip file".getBytes()
        );

        assertThatThrownBy(() -> service.store("project-1", "document-1", file))
                .isInstanceOf(DocumentValidationException.class)
                .hasMessageContaining("有效的 DOCX");
    }

    @Test
    void storeShouldSaveUtf8LatexSourceWithoutChangingItsContent() throws IOException {
        DocumentStorageService service = new DocumentStorageService(temporaryDirectory.toString());
        byte[] source = "\\documentclass{article}\n\\begin{document}\n中文论文\n\\end{document}\n"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "main.tex",
                DocumentStorageService.LATEX_CONTENT_TYPE,
                source
        );

        StoredDocument stored = service.store("project-1", "document-2", file);

        assertThat(stored.contentType()).isEqualTo(DocumentStorageService.LATEX_CONTENT_TYPE);
        assertThat(stored.relativePath())
                .isEqualTo(Path.of("documents", "project-1", "document-2", "source.tex").toString());
        assertThat(Files.readAllBytes(temporaryDirectory.resolve(stored.relativePath())))
                .isEqualTo(source);
    }

    @Test
    void storeShouldExtractAMultiFileLatexProject() throws IOException {
        DocumentStorageService service = new DocumentStorageService(temporaryDirectory.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "投稿工程.zip",
                DocumentStorageService.LATEX_PROJECT_CONTENT_TYPE,
                createLatexProjectZip()
        );

        StoredDocument stored = service.store("project-1", "document-3", file);
        Path documentDirectory = temporaryDirectory
                .resolve("documents")
                .resolve("project-1")
                .resolve("document-3");

        assertThat(stored.contentType()).isEqualTo(DocumentStorageService.LATEX_PROJECT_CONTENT_TYPE);
        assertThat(Files.isRegularFile(documentDirectory.resolve("project.zip"))).isTrue();
        assertThat(Files.isRegularFile(documentDirectory.resolve("workspace/main.tex"))).isTrue();
        assertThat(Files.isRegularFile(documentDirectory.resolve("workspace/chapters/body.tex"))).isTrue();
    }

    @Test
    void storeShouldRejectUnsafeArchivePaths() throws IOException {
        DocumentStorageService service = new DocumentStorageService(temporaryDirectory.toString());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            addEntry(zip, "main.tex", "\\documentclass{article}");
            addEntry(zip, "../outside.tex", "unsafe");
        }
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "unsafe.zip",
                DocumentStorageService.LATEX_PROJECT_CONTENT_TYPE,
                output.toByteArray()
        );

        assertThatThrownBy(() -> service.store("project-1", "document-4", file))
                .isInstanceOf(DocumentValidationException.class)
                .hasMessageContaining("不安全");
    }

    private byte[] createMinimalDocx() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            addEntry(zip, "[Content_Types].xml", "<Types />");
            addEntry(zip, "word/document.xml", "<w:document />");
        }
        return output.toByteArray();
    }

    private byte[] createLatexProjectZip() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            addEntry(zip, "main.tex", "\\documentclass{ctexart}\n\\input{chapters/body}");
            addEntry(zip, "chapters/body.tex", "\\section{正文}");
        }
        return output.toByteArray();
    }

    private void addEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes());
        zip.closeEntry();
    }
}
