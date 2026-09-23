package com.paperagent.project;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 业务层：Controller 只处理 HTTP，具体业务规则集中放在这里。
 */
@Service
public class PaperProjectService {

    private final PaperProjectRepository repository;

    public PaperProjectService(PaperProjectRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PaperProjectResponse create(CreatePaperProjectRequest request) {
        String description = request.description() == null ? "" : request.description().trim();
        PaperProject project = new PaperProject(request.name().trim(), description);
        return PaperProjectResponse.from(repository.save(project));
    }

    @Transactional(readOnly = true)
    public List<PaperProjectResponse> findAll() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(PaperProjectResponse::from)
                .toList();
    }
}

