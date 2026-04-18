package com.devflow.domain.pipeline.repository;
import com.devflow.domain.pipeline.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
public interface ArtifactRepository extends JpaRepository<Artifact, UUID> {
    Optional<Artifact> findByStageRun(StageRun stageRun);
}
