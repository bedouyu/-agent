package com.paperagent.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 创建项目时客户端提交的数据。record 适合表达只承载数据的对象。 */
public record CreatePaperProjectRequest(
        @NotBlank(message = "项目名称不能为空")
        @Size(max = 100, message = "项目名称不能超过 100 个字符")
        String name,

        @Size(max = 500, message = "项目说明不能超过 500 个字符")
        String description
) {
}

