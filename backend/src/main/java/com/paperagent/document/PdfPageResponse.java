package com.paperagent.document;

/** PDF 某一页的像素尺寸；桌面端据此把鼠标坐标换算成 PDF 坐标。 */
public record PdfPageResponse(
        int pageNumber,
        int widthPixels,
        int heightPixels
) {
}
