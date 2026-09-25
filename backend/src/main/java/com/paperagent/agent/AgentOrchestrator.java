package com.paperagent.agent;

import com.paperagent.ai.AiSuggestionResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/** 编排层只负责按顺序运行 Agent；不直接调用模型或改动论文文件。 */
@Service
public class AgentOrchestrator {

    private final List<PaperAgent> agents;

    public AgentOrchestrator(List<PaperAgent> agents) {
        this.agents = List.copyOf(agents);
    }

    public List<AgentDescriptor> agents() {
        return agents.stream()
                .map(agent -> new AgentDescriptor(agent.id(), agent.description()))
                .toList();
    }

    public AiSuggestionResponse suggest(String sourcePath, String originalText, String mode, String model) {
        AgentContext context = new AgentContext(sourcePath, originalText, mode, model);
        for (PaperAgent agent : agents) {
            if (agent.supports(context)) {
                agent.run(context);
            }
        }
        AiSuggestionResponse suggestion = context.suggestion();
        if (suggestion == null) {
            throw new IllegalStateException("没有 Agent 生成 LaTeX 建议");
        }
        return new AiSuggestionResponse(
                suggestion.sourcePath(), suggestion.originalText(), suggestion.suggestedTex(),
                suggestion.explanation(), suggestion.mode(), suggestion.model(),
                context.review(), context.steps()
        );
    }
}
