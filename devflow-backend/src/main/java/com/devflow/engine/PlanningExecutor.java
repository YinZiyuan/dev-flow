package com.devflow.engine;

import com.devflow.ai.ClaudeClient;
import com.devflow.ai.ContextBuilder;
import com.devflow.domain.pipeline.model.*;
import com.devflow.domain.pipeline.model.StageRun.StageStatus;
import com.devflow.domain.pipeline.repository.*;
import com.devflow.realtime.EventPublisher;
import org.springframework.stereotype.Component;

@Component
public class PlanningExecutor extends BaseStageExecutor {

    public PlanningExecutor(ClaudeClient c, ContextBuilder cb, PipelineEngine e,
            MessageRepository mr, ArtifactRepository ar, AgentConfigRepository acr, EventPublisher ep) {
        super(c, cb, e, mr, ar, acr, ep);
    }

    @Override public boolean supports(StageType type) { return type == StageType.planning; }

    @Override
    protected void handleResponse(StageRun stageRun, String response, AgentConfig config) {
        var json = parseControlJson(response);
        if (json == null) { pipelineEngine.transitionTo(stageRun, StageStatus.waiting_answer); return; }

        String type = json.path("type").asText();
        switch (type) {
            case "question" -> {
                var qMsg = new Message();
                qMsg.setStageRun(stageRun);
                qMsg.setRole(Message.Role.assistant);
                qMsg.setContent(json.path("content").asText());
                qMsg.setType(Message.MessageType.question);
                messageRepository.save(qMsg);
                pipelineEngine.transitionTo(stageRun, StageStatus.waiting_answer);
            }
            case "artifact" -> {
                String title = json.path("title").asText("Implementation Plan");
                String content = response.substring(response.indexOf('}') + 1).trim();
                saveArtifact(stageRun, Artifact.ArtifactType.plan, title, content);
                pipelineEngine.transitionTo(stageRun, StageStatus.waiting_approval);
            }
            default -> pipelineEngine.transitionTo(stageRun, StageStatus.waiting_answer);
        }
    }
}
