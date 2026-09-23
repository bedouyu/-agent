package com.paperagent.document;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LatexCompileServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void parseSyncTexShouldKeepNestedChineseSourcePath() throws IOException {
        Path workspaceRoot = temporaryDirectory.resolve("workspace");
        Path source = workspaceRoot.resolve("章节/正文.tex");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "第一行\n第二行\n");
        Path main = workspaceRoot.resolve("main.tex");
        Files.writeString(main, "\\input{章节/正文}");

        DocumentStorageService storageService = new DocumentStorageService(temporaryDirectory.toString());
        LatexCompileService service = new LatexCompileService(
                storageService,
                temporaryDirectory.toString(),
                30,
                144
        );
        DocumentStorageService.LatexWorkspace workspace = new DocumentStorageService.LatexWorkspace(
                workspaceRoot,
                main,
                workspaceRoot.resolve("main.pdf"),
                temporaryDirectory.resolve("preview")
        );

        SyncTexResponse response = service.parseSyncTex(
                workspace,
                "Input:" + source + "\nLine:2\nColumn:0\nContext:\n"
        );

        assertThat(response.sourcePath()).isEqualTo("章节/正文.tex");
        assertThat(response.line()).isEqualTo(2);
        assertThat(response.column()).isZero();
    }
}
