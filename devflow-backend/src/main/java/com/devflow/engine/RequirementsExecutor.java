package com.devflow.engine;

import com.devflow.ai.ClaudeClient;
import com.devflow.ai.ContextBuilder;
import com.devflow.domain.pipeline.model.*;
import com.devflow.domain.pipeline.model.StageRun.StageStatus;
import com.devflow.domain.pipeline.repository.*;
import com.devflow.realtime.EventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class RequirementsExecutor extends BaseStageExecutor {

    public RequirementsExecutor(ClaudeClient c, ContextBuilder cb, @Lazy PipelineEngine e,
            MessageRepository mr, ArtifactRepository ar, AgentConfigRepository acr, EventPublisher ep) {
        super(c, cb, e, mr, ar, acr, ep);
    }

    @Override public boolean supports(StageType type) { return type == StageType.requirements; }

    @Override
    protected void handleResponse(StageRun stageRun, String response, AgentConfig config) {
        var json = parseControlJson(response);
        if (json == null) {
            // No structured output — treat as continuation, ask Claude again
            pipelineEngine.transitionTo(stageRun, StageStatus.waiting_answer);
            return;
        }

        String type = json.path("type").asText();
        switch (type) {
            case "question" -> {
                // Save as question message so frontend can render input box
                var qMsg = new Message();
                qMsg.setStageRun(stageRun);
                qMsg.setRole(Message.Role.assistant);
                qMsg.setContent(json.path("content").asText());
                qMsg.setType(Message.MessageType.question);
                messageRepository.save(qMsg);
                pipelineEngine.transitionTo(stageRun, StageStatus.waiting_answer);
            }
            case "choice" -> {
                var choiceMsg = new Message();
                choiceMsg.setStageRun(stageRun);
                choiceMsg.setRole(Message.Role.assistant);
                choiceMsg.setContent(response);
                choiceMsg.setType(Message.MessageType.choice_request);
                choiceMsg.setOptions(json.path("options").toString());
                messageRepository.save(choiceMsg);
                pipelineEngine.transitionTo(stageRun, StageStatus.waiting_choice);
            }
            case "artifact" -> {
                String title = json.path("title").asText("PRD Document");
                // Content is the text after the JSON block
                String content = response.substring(response.indexOf('}') + 1).trim();
                saveArtifact(stageRun, Artifact.ArtifactType.prd, title, content);
                pipelineEngine.transitionTo(stageRun, StageStatus.waiting_approval);
            }
            default -> pipelineEngine.transitionTo(stageRun, StageStatus.waiting_answer);
        }
    }
}
