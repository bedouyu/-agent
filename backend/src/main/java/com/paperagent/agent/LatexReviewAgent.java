package com.paperagent.agent;

import com.paperagent.ai.AiServiceException;
import com.paperagent.ai.AiModelGateway;
import com.paperagent.ai.AiSuggestionResponse;
import com.paperagent.ai.ModelReviewDecision;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/** 第二个 Agent 独立审查编辑 Agent 的候选结果。 */
@Component
@Order(20)
public class LatexReviewAgent implements PaperAgent {

    private final AiModelGateway modelGateway;
    private final LatexSafetyGuard safetyGuard;

    public LatexReviewAgent(AiModelGateway modelGateway, LatexSafetyGuard safetyGuard) {
        this.modelGateway = modelGateway;
        this.safetyGuard = safetyGuard;
    }

    @Override
    public String id() { return "latex-reviewer"; }

    @Override
    public String description() { return "本地规则检查后，由 DeepSeek 独立复核建议"; }

    @Override
    public boolean supports(AgentContext context) {
        return context.suggestion() != null;
    }

    @Override
    public void run(AgentContext context) {
        AiSuggestionResponse suggestion = context.suggestion();
        List<String> warnings = safetyGuard.inspect(context.originalText(), suggestion.suggestedTex());
        if (!warnings.isEmpty()) {
            context.setReview(new AgentReview(false, "本地安全检查发现需要人工核对的变化，未继续发送给复核模型。", warnings));
            context.addStep(new AgentStep(id(), "NEEDS_REVIEW", "本地检查发现风险"));
            return;
        }

        try {
            ModelReviewDecision decision = modelGateway.review(
                    context.originalText(), suggestion.suggestedTex(), context.mode(), context.model()
            );
            context.setReview(new AgentReview(decision.approved(), decision.explanation(), List.of()));
            context.addStep(new AgentStep(id(), decision.approved() ? "COMPLETED" : "NEEDS_REVIEW", "已完成独立模型复核"));
        } catch (AiServiceException exception) {
            // 第一次调用已经生成了建议。第二次失败时保留建议，但明确标记为未经复核。
            context.setReview(new AgentReview(false, "复核未完成：" + exception.getMessage(), List.of()));
            context.addStep(new AgentStep(id(), "UNAVAILABLE", "模型复核未完成"));
        }
    }
}
