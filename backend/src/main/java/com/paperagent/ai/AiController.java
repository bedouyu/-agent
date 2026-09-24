package com.paperagent.ai;

import com.paperagent.document.PaperDocumentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** AI 接口独立于文档接口，方便以后扩展更多模型和任务。 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final DeepSeekService deepSeekService;
    private final PaperDocumentService documentService;

    public AiController(DeepSeekService deepSeekService, PaperDocumentService documentService) {
        this.deepSeekService = deepSeekService;
        this.documentService = documentService;
    }

    @GetMapping("/status")
    public AiStatusResponse status() {
        return deepSeekService.status();
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
