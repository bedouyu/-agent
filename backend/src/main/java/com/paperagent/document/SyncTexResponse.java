package com.paperagent.document;

/** SyncTeX 反向搜索结果：从 PDF 坐标定位到源码文件和行。 */
public record SyncTexResponse(
        String sourcePath,
        int line,
        int column,
        String context
) {
}
