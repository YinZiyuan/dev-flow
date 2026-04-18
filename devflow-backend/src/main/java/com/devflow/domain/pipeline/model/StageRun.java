package com.devflow.domain.pipeline.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Getter @Setter
public class StageRun {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private PipelineRun pipelineRun;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "stage_type", nullable = false) private StageType stageType;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "stage_status", nullable = false) private StageStatus status = StageStatus.pending;

    private int orderIndex = 0;
    private Instant startedAt;
    private Instant completedAt;

    public enum StageStatus {
        pending, running, waiting_answer, waiting_choice,
        waiting_approval, waiting_revision, completed, failed, skipped
    }
}
