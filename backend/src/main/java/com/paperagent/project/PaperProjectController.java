package com.paperagent.project;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** HTTP 接口层：负责接收请求、校验参数并调用业务层。 */
@RestController
@RequestMapping("/api/projects")
public class PaperProjectController {

    private final PaperProjectService service;

    public PaperProjectController(PaperProjectService service) {
        this.service = service;
    }

    @GetMapping
    public List<PaperProjectResponse> findAll() {
        return service.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaperProjectResponse create(@Valid @RequestBody CreatePaperProjectRequest request) {
        return service.create(request);
    }
}

