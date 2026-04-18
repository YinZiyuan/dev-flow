package com.devflow.realtime;

import com.devflow.domain.pipeline.model.PipelineRun;
import com.devflow.domain.pipeline.model.StageRun;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class EventPublisher {

    private final SimpMessagingTemplate messaging;

    public void publishStageUpdate(StageRun stageRun) {
        messaging.convertAndSend(
            "/topic/pipeline/" + stageRun.getPipelineRun().getId() + "/stage",
            new StageUpdateEvent(stageRun.getId(), stageRun.getStatus().name())
        );
    }

    public void publishPipelineUpdate(PipelineRun run) {
        messaging.convertAndSend(
            "/topic/pipeline/" + run.getId(),
            new PipelineUpdateEvent(run.getId(), run.getStatus().name())
        );
    }

    public void publishStreamChunk(java.util.UUID stageRunId, String chunk) {
        messaging.convertAndSend(
            "/topic/stage/" + stageRunId + "/stream",
            new StreamChunkEvent(chunk)
        );
    }

    public void publishCodeFile(java.util.UUID stageRunId,
                                 java.util.UUID fileId, String path, String language) {
        messaging.convertAndSend(
            "/topic/stage/" + stageRunId + "/file",
            new CodeFileEvent(fileId, path, language)
        );
    }

    public record StageUpdateEvent(java.util.UUID stageRunId, String status) {}
    public record PipelineUpdateEvent(java.util.UUID pipelineRunId, String status) {}
    public record StreamChunkEvent(String chunk) {}
    public record CodeFileEvent(java.util.UUID fileId, String path, String language) {}
}
