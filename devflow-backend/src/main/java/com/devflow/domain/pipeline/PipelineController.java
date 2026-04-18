package com.devflow.domain.pipeline;

import com.devflow.domain.pipeline.dto.CreatePipelineRequest;
import com.devflow.domain.pipeline.model.*;
import com.devflow.domain.pipeline.repository.*;
import com.devflow.domain.project.ProjectRepository;
import com.devflow.domain.user.UserRepository;
import com.devflow.engine.PipelineEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController @RequestMapping("/api/projects/{projectId}/pipelines")
@RequiredArgsConstructor
public class PipelineController {

    private final PipelineRunRepository pipelineRunRepository;
    private final StageRunRepository stageRunRepository;
    private final MessageRepository messageRepository;
    private final ArtifactRepository artifactRepository;
    private final CodeFileRepository codeFileRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final PipelineEngine pipelineEngine;

    @PostMapping
    public ResponseEntity<PipelineRunDto> create(
            @AuthenticationPrincipal String email,
            @PathVariable UUID projectId,
            @RequestBody CreatePipelineRequest req) {

        var project = projectRepository.findById(projectId).orElseThrow();
        if (!project.getUser().getEmail().equals(email)) throw new SecurityException("Forbidden");

        var run = new PipelineRun();
        run.setProject(project);
        run.setRequirement(req.requirement());
        run = pipelineRunRepository.save(run);

        // Start requirements stage async
        StageRun firstStage = pipelineEngine.createAndStartStage(run, StageType.requirements, 0);
        pipelineEngine.executeStage(firstStage);

        return ResponseEntity.ok(toDto(run));
    }

    @GetMapping
    public List<PipelineRunDto> list(@AuthenticationPrincipal String email,
                                     @PathVariable UUID projectId) {
        var project = projectRepository.findById(projectId).orElseThrow();
        if (!project.getUser().getEmail().equals(email)) throw new SecurityException("Forbidden");
        return pipelineRunRepository.findByProjectOrderByCreatedAtDesc(project)
            .stream().map(this::toDto).toList();
    }

    @GetMapping("/{runId}")
    public PipelineRunDto get(@AuthenticationPrincipal String email,
                               @PathVariable UUID projectId,
                               @PathVariable UUID runId) {
        var run = pipelineRunRepository.findById(runId).orElseThrow();
        if (!run.getProject().getId().equals(projectId)) throw new IllegalArgumentException();
        return toDto(run);
    }

    @GetMapping("/{runId}/stages")
    public List<StageRunDto> stages(@PathVariable UUID runId) {
        var run = pipelineRunRepository.findById(runId).orElseThrow();
        return stageRunRepository.findByPipelineRunOrderByOrderIndexAsc(run)
            .stream().map(this::toStageDto).toList();
    }

    @GetMapping("/{runId}/stages/{stageId}/messages")
    public List<MessageDto> messages(@PathVariable UUID stageId) {
        var stage = stageRunRepository.findById(stageId).orElseThrow();
        return messageRepository.findByStageRunOrderByCreatedAtAsc(stage)
            .stream().map(this::toMessageDto).toList();
    }

    @GetMapping("/{runId}/stages/{stageId}/artifact")
    public ResponseEntity<ArtifactDto> artifact(@PathVariable UUID stageId) {
        var stage = stageRunRepository.findById(stageId).orElseThrow();
        return artifactRepository.findByStageRun(stage)
            .map(this::toArtifactDto)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{runId}/stages/{stageId}/files")
    public List<CodeFileDto> files(@PathVariable UUID stageId) {
        var stage = stageRunRepository.findById(stageId).orElseThrow();
        return artifactRepository.findByStageRun(stage)
            .map(a -> codeFileRepository.findByArtifactOrderByPathAsc(a)
                .stream().map(this::toFileDto).toList())
            .orElse(List.of());
    }

    // --- DTOs ---
    public record PipelineRunDto(UUID id, String requirement, String status,
                                  String currentStage, java.time.Instant createdAt) {}
    public record StageRunDto(UUID id, String stageType, String status,
                               int orderIndex, java.time.Instant startedAt, java.time.Instant completedAt) {}
    public record MessageDto(UUID id, String role, String content, String type,
                              String options, String selectedOption, java.time.Instant createdAt) {}
    public record ArtifactDto(UUID id, String type, String title, String content,
                               java.time.Instant approvedAt, java.time.Instant createdAt) {}
    public record CodeFileDto(UUID id, String path, String language, java.time.Instant updatedAt) {}

    private PipelineRunDto toDto(PipelineRun r) {
        return new PipelineRunDto(r.getId(), r.getRequirement(),
            r.getStatus().name(), r.getCurrentStage().name(), r.getCreatedAt());
    }
    private StageRunDto toStageDto(StageRun sr) {
        return new StageRunDto(sr.getId(), sr.getStageType().name(),
            sr.getStatus().name(), sr.getOrderIndex(), sr.getStartedAt(), sr.getCompletedAt());
    }
    private MessageDto toMessageDto(Message m) {
        return new MessageDto(m.getId(), m.getRole().name(), m.getContent(),
            m.getType().name(), m.getOptions(), m.getSelectedOption(), m.getCreatedAt());
    }
    private ArtifactDto toArtifactDto(Artifact a) {
        return new ArtifactDto(a.getId(), a.getType().name(), a.getTitle(),
            a.getContent(), a.getApprovedAt(), a.getCreatedAt());
    }
    private CodeFileDto toFileDto(CodeFile f) {
        return new CodeFileDto(f.getId(), f.getPath(), f.getLanguage(), f.getUpdatedAt());
    }
}
