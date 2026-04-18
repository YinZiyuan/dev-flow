package com.devflow.domain.pipeline.model;

import com.devflow.domain.project.Project;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Getter @Setter
public class PipelineRun {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private Project project;
    @Column(nullable = false, columnDefinition = "TEXT") private String requirement;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "pipeline_status") private PipelineStatus status = PipelineStatus.running;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "stage_type") private StageType currentStage = StageType.requirements;

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    public enum PipelineStatus { running, waiting_human, completed, failed }
}
