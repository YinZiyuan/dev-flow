package com.devflow.engine;

import com.devflow.ai.ClaudeClient;
import com.devflow.ai.ContextBuilder;
import com.devflow.domain.pipeline.model.*;
import com.devflow.domain.pipeline.model.StageRun.StageStatus;
import com.devflow.domain.pipeline.repository.*;
import com.devflow.realtime.EventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class CodingExecutor extends BaseStageExecutor {

    private final CodeFileRepository codeFileRepository;

    public CodingExecutor(ClaudeClient c, ContextBuilder cb, PipelineEngine e,
            MessageRepository mr, ArtifactRepository ar, AgentConfigRepository acr,
            EventPublisher ep, CodeFileRepository cfr) {
        super(c, cb, e, mr, ar, acr, ep);
        this.codeFileRepository = cfr;
    }

    @Override public boolean supports(StageType type) { return type == StageType.coding; }

    @Override
    protected void handleResponse(StageRun stageRun, String response, AgentConfig config) {
        // Coding Agent emits multiple file blocks + a final artifact JSON
        List<CodeFile> files = new ArrayList<>();
        Artifact codeArtifact = new Artifact();
        codeArtifact.setStageRun(stageRun);
        codeArtifact.setType(Artifact.ArtifactType.code);
        codeArtifact.setTitle("Generated Code");
        codeArtifact = artifactRepository.save(codeArtifact);

        // Parse file blocks: {"type":"file","path":"...","language":"..."}\n```\n<content>\n```
        var filePattern = Pattern.compile(
            "\\{\"type\":\"file\",\"path\":\"([^\"]+)\",\"language\":\"([^\"]+)\"\\}\\n```[^\\n]*\\n([\\s\\S]*?)\\n```",
            Pattern.MULTILINE
        );
        var matcher = filePattern.matcher(response);
        while (matcher.find()) {
            String path = matcher.group(1);
            String language = matcher.group(2);
            String content = matcher.group(3);

            // Check if file already exists (revision case) — update instead of insert
            var existingOpt = codeFileRepository.findByArtifactOrderByPathAsc(codeArtifact)
                .stream().filter(f -> f.getPath().equals(path)).findFirst();

            CodeFile file;
            if (existingOpt.isPresent()) {
                file = existingOpt.get();
                file.setContent(content);
                file.setUpdatedAt(Instant.now());
            } else {
                file = new CodeFile();
                file.setArtifact(codeArtifact);
                file.setPath(path);
                file.setLanguage(language);
                file.setContent(content);
            }
            file = codeFileRepository.save(file);
            // Push to frontend in real-time
            eventPublisher.publishCodeFile(stageRun.getId(), file.getId(), path, language);
        }

        // Check for control JSON
        var json = parseControlJson(response);
        if (json != null) {
            String type = json.path("type").asText();
            if ("question".equals(type)) {
                var qMsg = new Message();
                qMsg.setStageRun(stageRun);
                qMsg.setRole(Message.Role.assistant);
                qMsg.setContent(json.path("content").asText());
                qMsg.setType(Message.MessageType.question);
                messageRepository.save(qMsg);
                pipelineEngine.transitionTo(stageRun, StageStatus.waiting_answer);
                return;
            }
            if ("artifact".equals(type)) {
                String summary = response.substring(response.lastIndexOf('}') + 1).trim();
                codeArtifact.setContent(summary);
                artifactRepository.save(codeArtifact);
                pipelineEngine.transitionTo(stageRun, StageStatus.waiting_approval);
                return;
            }
        }
        // If no explicit artifact signal but files were generated, go to approval
        if (!codeFileRepository.findByArtifactOrderByPathAsc(codeArtifact).isEmpty()) {
            pipelineEngine.transitionTo(stageRun, StageStatus.waiting_approval);
        } else {
            pipelineEngine.transitionTo(stageRun, StageStatus.waiting_answer);
        }
    }
}
