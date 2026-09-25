package com.paperagent.ai;

import com.paperagent.agent.AgentReview;
import com.paperagent.agent.AgentStep;

import java.util.List;

/** AI 建议仅用于预览与复制，不直接写回论文源文件。 */
public record AiSuggestionResponse(
        String sourcePath,
        String originalText,
        String suggestedTex,
        String explanation,
        String mode,
        String model,
        AgentReview review,
        List<AgentStep> agentSteps
) {
    /** 模型客户端只生成候选建议；编排层稍后补上复核结果。 */
    public AiSuggestionResponse(
            String sourcePath, String originalText, String suggestedTex,
            String explanation, String mode, String model
    ) {
        this(sourcePath, originalText, suggestedTex, explanation, mode, model, null, List.of());
    }
}
