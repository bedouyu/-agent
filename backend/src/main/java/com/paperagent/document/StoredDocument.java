package com.paperagent.document;

/** 文件保存完成后返回给业务层的存储结果。 */
public record StoredDocument(
        String originalFileName,
        String relativePath,
        long fileSize,
        String contentType
) {
}
