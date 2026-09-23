package com.paperagent.document;

/** 工作区中一个 UTF-8 LaTeX 源文件的内容。 */
public record SourceFileResponse(String path, String content) {
}
