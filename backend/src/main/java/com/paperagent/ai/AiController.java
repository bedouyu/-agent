package com.paperagent.ai;

import com.paperagent.agent.AgentDescriptor;
import com.paperagent.agent.AgentOrchestrator;
import com.paperagent.document.PaperDocumentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** AI 接口独立于文档接口，方便以后扩展更多模型和任务。 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiModelGateway modelGateway;
    private final PaperDocumentService documentService;
    private final AgentOrchestrator agentOrchestrator;

    public AiController(AiModelGateway modelGateway, PaperDocumentService documentService,
                        AgentOrchestrator agentOrchestrator) {
        this.modelGateway = modelGateway;
        this.documentService = documentService;
        this.agentOrchestrator = agentOrchestrator;
    }

    @GetMapping("/status")
    public AiStatusResponse status() {
        return modelGateway.status();
    }

    @GetMapping("/agents")
    public List<AgentDescriptor> agents() {
        return agentOrchestrator.agents();
    }

    @PostMapping("/projects/{projectId}/documents/{documentId}/suggest")
    public AiSuggestionResponse suggest(
            @PathVariable String projectId,
            @PathVariable String documentId,
            @RequestBody AiSuggestionRequest request
    ) {
        return documentService.suggestWithAi(projectId, documentId, request);
    }
}
