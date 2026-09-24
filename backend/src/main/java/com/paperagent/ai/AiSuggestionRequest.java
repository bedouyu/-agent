package com.paperagent.ai;

/** 用户在桌面端明确选中的 LaTeX 片段及所选任务。 */
public record AiSuggestionRequest(
        String sourcePath,
        String selectedText,
        String mode,
        String model
) {
}
