package com.devflow.domain.project;

import com.devflow.domain.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Getter @Setter
public class Project {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(nullable = false) private String name;
    private String description;
    private String techStack;
    private String repoPath;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
}
