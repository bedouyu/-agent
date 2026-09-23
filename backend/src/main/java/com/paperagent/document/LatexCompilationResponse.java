package com.paperagent.document;

import java.time.Instant;

/** 一次 LaTeX 编译的结果，供桌面端显示日志和刷新 PDF 预览。 */
public record LatexCompilationResponse(
        boolean success,
        int exitCode,
        long durationMillis,
        String engine,
        String mainFile,
        boolean pdfAvailable,
        int pageCount,
        String log,
        Instant compiledAt
) {
}
