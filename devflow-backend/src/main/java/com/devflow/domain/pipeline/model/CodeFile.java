package com.devflow.domain.pipeline.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Getter @Setter
public class CodeFile {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private Artifact artifact;
    @Column(nullable = false) private String path;
    @Column(columnDefinition = "TEXT") private String content = "";
    private String language;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
}
