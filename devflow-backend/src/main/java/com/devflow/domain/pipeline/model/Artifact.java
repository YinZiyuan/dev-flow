package com.devflow.domain.pipeline.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Getter @Setter
public class Artifact {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private StageRun stageRun;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "artifact_type") private ArtifactType type;
    private String title;
    @Column(columnDefinition = "TEXT") private String content = "";
    private Instant approvedAt;
    private Instant createdAt = Instant.now();

    public enum ArtifactType { prd, plan, code, test_result }
}
