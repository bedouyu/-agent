package com.paperagent.project;

import java.time.Instant;

/** 返回给 Windows 客户端的项目数据，避免把数据库实体直接暴露给界面。 */
public record PaperProjectResponse(
        String id,
        String name,
        String description,
        Instant createdAt
) {
    public static PaperProjectResponse from(PaperProject project) {
        return new PaperProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getCreatedAt()
        );
    }
}

