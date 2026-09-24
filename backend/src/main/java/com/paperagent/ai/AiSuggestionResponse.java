package com.paperagent.ai;

/** AI 建议仅用于预览与复制，不直接写回论文源文件。 */
public record AiSuggestionResponse(
        String sourcePath,
        String originalText,
        String suggestedTex,
        String explanation,
        String mode,
        String model
) {
}
