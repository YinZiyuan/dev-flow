package com.devflow.domain.pipeline.repository;
import com.devflow.domain.pipeline.model.PipelineRun;
import com.devflow.domain.project.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface PipelineRunRepository extends JpaRepository<PipelineRun, UUID> {
    List<PipelineRun> findByProjectOrderByCreatedAtDesc(Project project);
}
