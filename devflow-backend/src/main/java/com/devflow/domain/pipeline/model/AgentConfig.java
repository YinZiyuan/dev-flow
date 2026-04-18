package com.devflow.domain.pipeline.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Getter @Setter
public class AgentConfig {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "stage_type", unique = true) private StageType stageType;
    private String name;
    @Column(columnDefinition = "TEXT") private String systemPrompt;
    private String model = "claude-opus-4-6";
    private int maxTokens = 8192;
    private int maxRetries = 3;
    private Instant updatedAt = Instant.now();
}
