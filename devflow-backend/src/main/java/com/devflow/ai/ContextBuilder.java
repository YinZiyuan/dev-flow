package com.devflow.ai;

import com.devflow.domain.pipeline.model.*;
import com.devflow.domain.pipeline.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component @RequiredArgsConstructor
public class ContextBuilder {

    private final MessageRepository messageRepository;
    private final ArtifactRepository artifactRepository;
    private final StageRunRepository stageRunRepository;

    /**
     * Build the full message history for the current stage + context from prior stages.
     */
    public List<ClaudeClient.ChatMessage> build(StageRun stageRun) {
        var messages = new ArrayList<ClaudeClient.ChatMessage>();

        // Add prior completed artifacts as context
        var allStages = stageRunRepository
            .findByPipelineRunOrderByOrderIndexAsc(stageRun.getPipelineRun());
        for (StageRun prior : allStages) {
            if (prior.getId().equals(stageRun.getId())) break;
            if (prior.getStatus() == StageRun.StageStatus.completed) {
                artifactRepository.findByStageRun(prior).ifPresent(artifact ->
                    messages.add(new ClaudeClient.ChatMessage("user",
                        "[" + artifact.getType().name().toUpperCase() + " - " + artifact.getTitle() + "]\n"
                        + artifact.getContent()))
                );
            }
        }

        // Add current stage conversation
        messageRepository.findByStageRunOrderByCreatedAtAsc(stageRun).forEach(m ->
            messages.add(new ClaudeClient.ChatMessage(m.getRole().name(), m.getContent()))
        );

        return messages;
    }

    /** Build the initial user message for a stage */
    public String buildInitialPrompt(StageRun stageRun) {
        String requirement = stageRun.getPipelineRun().getRequirement();
        String techStack = stageRun.getPipelineRun().getProject().getTechStack();
        return "Project tech stack: " + techStack + "\n\n"
             + "User requirement:\n" + requirement;
    }
}
