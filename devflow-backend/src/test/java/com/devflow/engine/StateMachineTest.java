package com.devflow.engine;

import com.devflow.domain.pipeline.model.*;
import com.devflow.domain.pipeline.model.StageRun.StageStatus;
import com.devflow.domain.pipeline.repository.*;
import com.devflow.realtime.EventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StateMachineTest {

    @Mock StageRunRepository stageRunRepository;
    @Mock PipelineRunRepository pipelineRunRepository;
    @Mock MessageRepository messageRepository;
    @Mock ArtifactRepository artifactRepository;
    @Mock EventPublisher eventPublisher;
    @InjectMocks PipelineEngine engine;

    private StageRun stageRunWith(StageStatus status) {
        var sr = new StageRun();
        sr.setId(UUID.randomUUID());
        sr.setStatus(status);
        sr.setStageType(StageType.requirements);
        var run = new PipelineRun();
        run.setId(UUID.randomUUID());
        sr.setPipelineRun(run);
        return sr;
    }

    @Test
    void transitionToWaitingAnswer_fromRunning_succeeds() {
        var sr = stageRunWith(StageStatus.running);
        when(stageRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(stageRunRepository.findByPipelineRunOrderByOrderIndexAsc(any()))
            .thenReturn(List.of(sr));
        when(pipelineRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        engine.transitionTo(sr, StageStatus.waiting_answer);

        assertThat(sr.getStatus()).isEqualTo(StageStatus.waiting_answer);
    }

    @Test
    void transitionToRunning_fromPending_succeeds() {
        var sr = stageRunWith(StageStatus.pending);
        when(stageRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(stageRunRepository.findByPipelineRunOrderByOrderIndexAsc(any()))
            .thenReturn(List.of(sr));
        when(pipelineRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        engine.transitionTo(sr, StageStatus.running);

        assertThat(sr.getStatus()).isEqualTo(StageStatus.running);
    }

    @Test
    void illegalTransition_fromCompleted_throws() {
        var sr = stageRunWith(StageStatus.completed);

        assertThatThrownBy(() -> engine.transitionTo(sr, StageStatus.running))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot transition");
    }
}
