package com.paperagent.document;

import java.util.List;

/** 应用内 PDF 预览所需的页面清单。 */
public record PdfPreviewResponse(int dpi, List<PdfPageResponse> pages) {
}
