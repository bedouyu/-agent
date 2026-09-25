package com.paperagent.agent;

import com.paperagent.ai.AiServiceException;
import com.paperagent.ai.AiModelGateway;
import com.paperagent.ai.AiSuggestionResponse;
import com.paperagent.ai.ModelReviewDecision;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class AgentOrchestratorTest {

    @Test
    void editorAndReviewerShouldCooperateInOrder() {
        AiModelGateway model = mock(AiModelGateway.class);
        when(model.suggest("main.tex", "原文", "POLISH", "deepseek-flash"))
                .thenReturn(new AiSuggestionResponse("main.tex", "原文", "润色后", "更清晰", "POLISH", "deepseek-flash"));
        when(model.review("原文", "润色后", "POLISH", "deepseek-flash"))
                .thenReturn(new ModelReviewDecision(true, "未发现明显问题"));
        AgentOrchestrator orchestrator = new AgentOrchestrator(List.of(
                new LatexEditAgent(model), new LatexReviewAgent(model, new LatexSafetyGuard())
        ));

        AiSuggestionResponse response = orchestrator.suggest("main.tex", "原文", "POLISH", "deepseek-flash");

        assertThat(response.suggestedTex()).isEqualTo("润色后");
        assertThat(response.review().approved()).isTrue();
        assertThat(response.agentSteps()).extracting(AgentStep::agentId)
                .containsExactly("latex-editor", "latex-reviewer");
    }

    @Test
    void localGuardShouldStopSecondModelCall() {
        AiModelGateway model = mock(AiModelGateway.class);
        when(model.suggest("main.tex", "见 \\cite{a}", "POLISH", "deepseek-flash"))
                .thenReturn(new AiSuggestionResponse("main.tex", "见 \\cite{a}", "见 \\cite{b}", "误改引用", "POLISH", "deepseek-flash"));
        AgentOrchestrator orchestrator = new AgentOrchestrator(List.of(
                new LatexEditAgent(model), new LatexReviewAgent(model, new LatexSafetyGuard())
        ));

        AiSuggestionResponse response = orchestrator.suggest("main.tex", "见 \\cite{a}", "POLISH", "deepseek-flash");

        assertThat(response.review().approved()).isFalse();
        assertThat(response.review().warnings()).isNotEmpty();
        verify(model).suggest("main.tex", "见 \\cite{a}", "POLISH", "deepseek-flash");
        verifyNoMoreInteractions(model);
    }

    @Test
    void reviewerFailureShouldKeepTheFirstSuggestion() {
        AiModelGateway model = mock(AiModelGateway.class);
        when(model.suggest("main.tex", "原文", "FORMAT", "deepseek-flash"))
                .thenReturn(new AiSuggestionResponse("main.tex", "原文", "候选", "格式调整", "FORMAT", "deepseek-flash"));
        when(model.review("原文", "候选", "FORMAT", "deepseek-flash"))
                .thenThrow(new AiServiceException(HttpStatus.BAD_GATEWAY, "AI_CONNECTION_FAILED", "网络中断"));
        AgentOrchestrator orchestrator = new AgentOrchestrator(List.of(
                new LatexEditAgent(model), new LatexReviewAgent(model, new LatexSafetyGuard())
        ));

        AiSuggestionResponse response = orchestrator.suggest("main.tex", "原文", "FORMAT", "deepseek-flash");

        assertThat(response.suggestedTex()).isEqualTo("候选");
        assertThat(response.review().approved()).isFalse();
        assertThat(response.agentSteps().get(1).status()).isEqualTo("UNAVAILABLE");
    }
}
