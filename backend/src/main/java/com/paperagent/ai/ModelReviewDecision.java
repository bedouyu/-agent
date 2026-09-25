package com.paperagent.ai;

/** 复核模型的原始结论；最终结论还要经过本地安全检查。 */
public record ModelReviewDecision(boolean approved, String explanation) {
}
