package com.devflow.domain.project;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    public List<ProjectService.ProjectDto> list(@AuthenticationPrincipal String email) {
        return projectService.list(email);
    }

    @PostMapping
    public ProjectService.ProjectDto create(@AuthenticationPrincipal String email,
                                            @RequestBody ProjectService.CreateProjectRequest req) {
        return projectService.create(email, req);
    }

    @GetMapping("/{id}")
    public ProjectService.ProjectDto get(@AuthenticationPrincipal String email, @PathVariable UUID id) {
        return projectService.get(email, id);
    }

    @PutMapping("/{id}")
    public ProjectService.ProjectDto update(@AuthenticationPrincipal String email,
                                            @PathVariable UUID id,
                                            @RequestBody ProjectService.CreateProjectRequest req) {
        return projectService.update(email, id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal String email, @PathVariable UUID id) {
        projectService.delete(email, id);
        return ResponseEntity.noContent().build();
    }
}
