package com.devflow.domain.pipeline.repository;
import com.devflow.domain.pipeline.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface StageRunRepository extends JpaRepository<StageRun, UUID> {
    List<StageRun> findByPipelineRunOrderByOrderIndexAsc(PipelineRun run);
    Optional<StageRun> findTopByPipelineRunAndStageTypeOrderByOrderIndexDesc(PipelineRun run, StageType type);
}
