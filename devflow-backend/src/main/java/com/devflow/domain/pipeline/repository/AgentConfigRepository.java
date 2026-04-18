package com.devflow.domain.pipeline.repository;
import com.devflow.domain.pipeline.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
public interface AgentConfigRepository extends JpaRepository<AgentConfig, UUID> {
    Optional<AgentConfig> findByStageType(StageType stageType);
}
