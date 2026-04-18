package com.devflow.domain.project;

import com.devflow.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public record CreateProjectRequest(String name, String description, String techStack, String repoPath) {}
    public record ProjectDto(UUID id, String name, String description, String techStack, String repoPath, Instant createdAt) {}

    private ProjectDto toDto(Project p) {
        return new ProjectDto(p.getId(), p.getName(), p.getDescription(), p.getTechStack(), p.getRepoPath(), p.getCreatedAt());
    }

    @Transactional
    public ProjectDto create(String email, CreateProjectRequest req) {
        var user = userRepository.findByEmail(email).orElseThrow();
        var p = new Project();
        p.setUser(user);
        p.setName(req.name());
        p.setDescription(req.description());
        p.setTechStack(req.techStack());
        p.setRepoPath(req.repoPath());
        return toDto(projectRepository.save(p));
    }

    public List<ProjectDto> list(String email) {
        var user = userRepository.findByEmail(email).orElseThrow();
        return projectRepository.findByUserOrderByCreatedAtDesc(user).stream().map(this::toDto).toList();
    }

    public ProjectDto get(String email, UUID id) {
        var p = projectRepository.findById(id).orElseThrow();
        if (!p.getUser().getEmail().equals(email)) throw new SecurityException("Forbidden");
        return toDto(p);
    }

    @Transactional
    public ProjectDto update(String email, UUID id, CreateProjectRequest req) {
        var p = projectRepository.findById(id).orElseThrow();
        if (!p.getUser().getEmail().equals(email)) throw new SecurityException("Forbidden");
        p.setName(req.name()); p.setDescription(req.description());
        p.setTechStack(req.techStack()); p.setRepoPath(req.repoPath());
        p.setUpdatedAt(Instant.now());
        return toDto(projectRepository.save(p));
    }

    @Transactional
    public void delete(String email, UUID id) {
        var p = projectRepository.findById(id).orElseThrow();
        if (!p.getUser().getEmail().equals(email)) throw new SecurityException("Forbidden");
        projectRepository.delete(p);
    }
}
