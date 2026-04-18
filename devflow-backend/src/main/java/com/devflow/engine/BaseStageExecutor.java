package com.devflow.engine;

import com.devflow.ai.ClaudeClient;
import com.devflow.ai.ContextBuilder;
import com.devflow.domain.pipeline.model.*;
import com.devflow.domain.pipeline.model.StageRun.StageStatus;
import com.devflow.domain.pipeline.repository.*;
import com.devflow.realtime.EventPublisher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j @RequiredArgsConstructor
public abstract class BaseStageExecutor implements StageExecutor {

    protected final ClaudeClient claudeClient;
    protected final ContextBuilder contextBuilder;
    protected final PipelineEngine pipelineEngine;
    protected final MessageRepository messageRepository;
    protected final ArtifactRepository artifactRepository;
    protected final AgentConfigRepository agentConfigRepository;
    protected final EventPublisher eventPublisher;
    protected final ObjectMapper mapper = new ObjectMapper();

    @Override
    @Transactional
    public void execute(StageRun stageRun) {
        AgentConfig config = agentConfigRepository.findByStageType(stageRun.getStageType())
            .orElseThrow(() -> new IllegalStateException("No agent config for " + stageRun.getStageType()));

        // Build initial message if this is the first run for this stage
        var history = contextBuilder.build(stageRun);
        if (history.isEmpty() || isAllPriorContext(stageRun, history)) {
            String initial = contextBuilder.buildInitialPrompt(stageRun);
            var initMsg = new Message();
            initMsg.setStageRun(stageRun);
            initMsg.setRole(Message.Role.user);
            initMsg.setContent(initial);
            initMsg.setType(Message.MessageType.text);
            messageRepository.save(initMsg);
            history.add(new ClaudeClient.ChatMessage("user", initial));
        }

        // Stream response from Claude
        StringBuilder fullResponse = new StringBuilder();
        claudeClient.streamChat(
            config.getModel(), config.getMaxTokens(), config.getSystemPrompt(),
            history,
            chunk -> {
                fullResponse.append(chunk);
                eventPublisher.publishStreamChunk(stageRun.getId(), chunk);
            },
            complete -> {}
        );

        // Save assistant message
        var assistantMsg = new Message();
        assistantMsg.setStageRun(stageRun);
        assistantMsg.setRole(Message.Role.assistant);
        assistantMsg.setContent(fullResponse.toString());
        messageRepository.save(assistantMsg);

        // Parse response and determine next state
        handleResponse(stageRun, fullResponse.toString(), config);
    }

    protected abstract void handleResponse(StageRun stageRun, String response, AgentConfig config);

    /**
     * Parse the first JSON object embedded in the response text.
     * Agents output control JSON like {"type":"question","content":"..."} or {"type":"artifact",...}
     */
    protected JsonNode parseControlJson(String response) {
        // Find first { ... } block in response
        int start = response.indexOf('{');
        if (start == -1) return null;
        // Find matching closing brace
        int depth = 0, end = start;
        for (int i = start; i < response.length(); i++) {
            if (response.charAt(i) == '{') depth++;
            else if (response.charAt(i) == '}') { depth--; if (depth == 0) { end = i; break; } }
        }
        try {
            return mapper.readTree(response.substring(start, end + 1));
        } catch (Exception e) { return null; }
    }

    protected void saveArtifact(StageRun stageRun, Artifact.ArtifactType type,
                                 String title, String content) {
        var artifact = new Artifact();
        artifact.setStageRun(stageRun);
        artifact.setType(type);
        artifact.setTitle(title);
        artifact.setContent(content);
        artifactRepository.save(artifact);
    }

    private boolean isAllPriorContext(StageRun stageRun, List<ClaudeClient.ChatMessage> history) {
        return messageRepository.findByStageRunOrderByCreatedAtAsc(stageRun).isEmpty();
    }
}
