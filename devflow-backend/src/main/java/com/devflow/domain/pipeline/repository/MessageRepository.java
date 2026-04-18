package com.devflow.domain.pipeline.repository;
import com.devflow.domain.pipeline.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface MessageRepository extends JpaRepository<Message, UUID> {
    List<Message> findByStageRunOrderByCreatedAtAsc(StageRun stageRun);
}
