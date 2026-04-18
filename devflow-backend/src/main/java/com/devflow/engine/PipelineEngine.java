package com.devflow.engine;

import com.devflow.domain.pipeline.model.*;
import com.devflow.domain.pipeline.model.StageRun.StageStatus;
import com.devflow.domain.pipeline.repository.*;
import com.devflow.realtime.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor @Slf4j
public class PipelineEngine {

    private final StageRunRepository stageRunRepository;
    private final PipelineRunRepository pipelineRunRepository;
    private final MessageRepository messageRepository;
    private final ArtifactRepository artifactRepository;
    private final EventPublisher eventPublisher;
    private final List<StageExecutor> executors;

    // Valid transitions: from -> allowed destinations
    private static final Map<StageStatus, Set<StageStatus>> TRANSITIONS = Map.of(
        StageStatus.pending,          Set.of(StageStatus.running, StageStatus.skipped),
        StageStatus.running,          Set.of(StageStatus.waiting_answer, StageStatus.waiting_choice,
                                            StageStatus.waiting_approval, StageStatus.failed),
        StageStatus.waiting_answer,   Set.of(StageStatus.running),
        StageStatus.waiting_choice,   Set.of(StageStatus.running),
        StageStatus.waiting_approval, Set.of(StageStatus.completed, StageStatus.waiting_revision),
        StageStatus.waiting_revision, Set.of(StageStatus.running),
        StageStatus.completed,        Set.of(),  // terminal
        StageStatus.failed,           Set.of(StageStatus.running),  // allow manual retry
        StageStatus.skipped,          Set.of()   // terminal
    );

    @Transactional
    public void transitionTo(StageRun stageRun, StageStatus newStatus) {
        StageStatus current = stageRun.getStatus();
        Set<StageStatus> allowed = TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(newStatus)) {
            throw new IllegalStateException(
                "Cannot transition from " + current + " to " + newStatus);
        }
        stageRun.setStatus(newStatus);
        if (newStatus == StageStatus.running && stageRun.getStartedAt() == null) {
            stageRun.setStartedAt(Instant.now());
        }
        if (newStatus == StageStatus.completed || newStatus == StageStatus.failed) {
            stageRun.setCompletedAt(Instant.now());
        }
        stageRunRepository.save(stageRun);
        eventPublisher.publishStageUpdate(stageRun);
        updatePipelineRunStatus(stageRun.getPipelineRun());
    }

    @Transactional
    public StageRun createAndStartStage(PipelineRun pipelineRun, StageType stageType, int orderIndex) {
        var sr = new StageRun();
        sr.setPipelineRun(pipelineRun);
        sr.setStageType(stageType);
        sr.setOrderIndex(orderIndex);
        sr.setStatus(StageStatus.pending);
        sr = stageRunRepository.save(sr);
        transitionTo(sr, StageStatus.running);
        return sr;
    }

    @Async
    public void executeStage(StageRun stageRun) {
        StageExecutor executor = executors.stream()
            .filter(e -> e.supports(stageRun.getStageType()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No executor for " + stageRun.getStageType()));
        try {
            executor.execute(stageRun);
        } catch (Exception e) {
            log.error("Stage execution failed: {}", stageRun.getId(), e);
            transitionTo(stageRun, StageStatus.failed);
        }
    }

    @Transactional
    public void onStageCompleted(StageRun stageRun) {
        transitionTo(stageRun, StageStatus.completed);
        PipelineRun run = stageRun.getPipelineRun();

        // Advance to next stage
        StageType next = nextStage(stageRun.getStageType());
        if (next == null) {
            run.setStatus(PipelineRun.PipelineStatus.completed);
            pipelineRunRepository.save(run);
            eventPublisher.publishPipelineUpdate(run);
            return;
        }

        run.setCurrentStage(next);
        pipelineRunRepository.save(run);

        int nextIndex = stageRunRepository
            .findByPipelineRunOrderByOrderIndexAsc(run).size();
        StageRun nextRun = createAndStartStage(run, next, nextIndex);
        executeStage(nextRun);
    }

    private StageType nextStage(StageType current) {
        return switch (current) {
            case requirements -> StageType.planning;
            case planning     -> StageType.coding;
            case coding       -> StageType.testing;
            case testing      -> null;
        };
    }

    private void updatePipelineRunStatus(PipelineRun run) {
        boolean anyWaiting = stageRunRepository
            .findByPipelineRunOrderByOrderIndexAsc(run).stream()
            .anyMatch(sr -> sr.getStatus().name().startsWith("waiting_"));
        if (anyWaiting) {
            run.setStatus(PipelineRun.PipelineStatus.waiting_human);
        } else {
            run.setStatus(PipelineRun.PipelineStatus.running);
        }
        run.setUpdatedAt(Instant.now());
        pipelineRunRepository.save(run);
    }
}
