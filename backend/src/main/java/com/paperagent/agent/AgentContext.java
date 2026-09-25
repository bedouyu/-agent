package com.paperagent.agent;

import com.paperagent.ai.AiSuggestionResponse;

import java.util.ArrayList;
import java.util.List;

/** 每次请求独立创建，不在 Agent 之间共享或持久化论文片段。 */
public final class AgentContext {

    private final String sourcePath;
    private final String originalText;
    private final String mode;
    private final String model;
    private final List<AgentStep> steps = new ArrayList<>();
    private AiSuggestionResponse suggestion;
    private AgentReview review;

    public AgentContext(String sourcePath, String originalText, String mode, String model) {
        this.sourcePath = sourcePath;
        this.originalText = originalText;
        this.mode = mode;
        this.model = model;
    }

    public String sourcePath() { return sourcePath; }

    public String originalText() { return originalText; }

    public String mode() { return mode; }

    public String model() { return model; }

    public AiSuggestionResponse suggestion() { return suggestion; }

    public AgentReview review() { return review; }

    public List<AgentStep> steps() { return List.copyOf(steps); }

    public void setSuggestion(AiSuggestionResponse suggestion) { this.suggestion = suggestion; }

    public void setReview(AgentReview review) { this.review = review; }

    public void addStep(AgentStep step) { steps.add(step); }
}
