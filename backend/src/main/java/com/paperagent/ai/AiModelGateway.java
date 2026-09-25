package com.paperagent.ai;

/** 模型通信接口；以后接入 Spring AI 或其他模型时，不需要改专职 Agent。 */
public interface AiModelGateway {

    AiStatusResponse status();

    AiSuggestionResponse suggest(String sourcePath, String originalText, String mode, String model);

    ModelReviewDecision review(String originalText, String suggestedTex, String mode, String model);
}
