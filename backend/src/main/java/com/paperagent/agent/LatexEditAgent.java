package com.paperagent.agent;

import com.paperagent.ai.AiSuggestionResponse;
import com.paperagent.ai.AiModelGateway;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 原有“润色/格式建议”能力现在成为多 Agent 流程中的编辑 Agent。 */
@Component
@Order(10)
public class LatexEditAgent implements PaperAgent {

    private final AiModelGateway modelGateway;

    public LatexEditAgent(AiModelGateway modelGateway) {
        this.modelGateway = modelGateway;
    }

    @Override
    public String id() { return "latex-editor"; }

    @Override
    public String description() { return "调用 DeepSeek 生成 LaTeX 润色或格式建议"; }

    @Override
    public void run(AgentContext context) {
        AiSuggestionResponse suggestion = modelGateway.suggest(
                context.sourcePath(), context.originalText(), context.mode(), context.model()
        );
        context.setSuggestion(suggestion);
        context.addStep(new AgentStep(id(), "COMPLETED", "已生成候选建议"));
    }
}
