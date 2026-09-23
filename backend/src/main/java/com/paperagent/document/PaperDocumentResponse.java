package com.paperagent.document;

import java.time.Instant;

/** 返回给客户端的文档信息，不暴露服务器上的实际存储路径。 */
public record PaperDocumentResponse(
        String id,
        String projectId,
        String originalFileName,
        String contentType,
        long fileSize,
        Instant uploadedAt
) {
    public static PaperDocumentResponse from(PaperDocument document) {
        return new PaperDocumentResponse(
                document.getId(),
                document.getProject().getId(),
                document.getOriginalFileName(),
                document.getContentType(),
                document.getFileSize(),
                document.getUploadedAt()
        );
    }
}
