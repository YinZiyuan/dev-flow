package com.devflow.domain.pipeline.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity @Getter @Setter
public class Message {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private StageRun stageRun;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "message_role") private Role role;
    @Column(columnDefinition = "TEXT", nullable = false) private String content;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "message_type") private MessageType type = MessageType.text;

    @JdbcTypeCode(SqlTypes.JSON)
    private String options; // JSON: [{id, label, description}]
    private String selectedOption;
    private Instant createdAt = Instant.now();

    public enum Role { user, assistant, system }
    public enum MessageType { text, question, choice_request, choice_response }
}
