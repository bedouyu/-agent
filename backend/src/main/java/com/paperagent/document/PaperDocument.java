package com.paperagent.document;

import com.paperagent.project.PaperProject;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 论文文档实体。
 *
 * <p>数据库只保存文件的说明信息和相对路径，DOCX 本身保存在 data/documents 中。</p>
 */
@Entity
@Table(name = "paper_document")
public class PaperDocument {

    @Id
    @Column(nullable = false, updatable = false, length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private PaperProject project;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "stored_relative_path", nullable = false, length = 500)
    private String storedRelativePath;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    protected PaperDocument() {
        // JPA 创建实体时需要无参构造函数。
    }

    public PaperDocument(
            String id,
            PaperProject project,
            String originalFileName,
            String storedRelativePath,
            String contentType,
            long fileSize
    ) {
        this.id = id;
        this.project = project;
        this.originalFileName = originalFileName;
        this.storedRelativePath = storedRelativePath;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.uploadedAt = Instant.now();
    }

    @PrePersist
    void initializeMissingValues() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (uploadedAt == null) {
            uploadedAt = Instant.now();
        }
    }

    public String getId() {
        return id;
    }

    public PaperProject getProject() {
        return project;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public String getStoredRelativePath() {
        return storedRelativePath;
    }

    public String getContentType() {
        return contentType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
