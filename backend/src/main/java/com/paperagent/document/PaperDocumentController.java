package com.paperagent.document;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** 文档 HTTP 接口：上传、查询和下载项目中的 LaTeX 或 DOCX 原始文件。 */
@RestController
@RequestMapping("/api/projects/{projectId}/documents")
public class PaperDocumentController {

    private final PaperDocumentService service;

    public PaperDocumentController(PaperDocumentService service) {
        this.service = service;
    }

    @GetMapping
    public List<PaperDocumentResponse> findAll(@PathVariable String projectId) {
        return service.findAll(projectId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PaperDocumentResponse upload(
            @PathVariable String projectId,
            @RequestPart("file") MultipartFile file
    ) {
        return service.upload(projectId, file);
    }

    @GetMapping("/{documentId}/content")
    public ResponseEntity<org.springframework.core.io.Resource> download(
            @PathVariable String projectId,
            @PathVariable String documentId
    ) {
        PaperDocumentService.DownloadableDocument download = service.download(projectId, documentId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(download.resource());
    }
}
