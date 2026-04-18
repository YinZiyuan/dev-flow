package com.devflow.engine;

import com.devflow.ai.ClaudeClient;
import com.devflow.ai.ContextBuilder;
import com.devflow.domain.pipeline.model.*;
import com.devflow.domain.pipeline.model.StageRun.StageStatus;
import com.devflow.domain.pipeline.repository.*;
import com.devflow.realtime.EventPublisher;
import com.devflow.sandbox.DockerSandbox;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Component @Slf4j
public class TestingExecutor extends BaseStageExecutor {

    private final CodeFileRepository codeFileRepository;
    private final DockerSandbox sandbox;
    private final StageRunRepository stageRunRepository2; // alias to avoid name clash

    public TestingExecutor(ClaudeClient c, ContextBuilder cb, PipelineEngine e,
            MessageRepository mr, ArtifactRepository ar, AgentConfigRepository acr,
            EventPublisher ep, CodeFileRepository cfr, DockerSandbox sb,
            StageRunRepository srr) {
        super(c, cb, e, mr, ar, acr, ep);
        this.codeFileRepository = cfr;
        this.sandbox = sb;
        this.stageRunRepository2 = srr;
    }

    @Override public boolean supports(StageType type) { return type == StageType.testing; }

    @Override
    protected void handleResponse(StageRun stageRun, String response, AgentConfig config) {
        // Testing stage doesn't use LLM directly — it runs tests in Docker
        // Get the latest coding artifact files
        var allStages = stageRunRepository2.findByPipelineRunOrderByOrderIndexAsc(stageRun.getPipelineRun());
        Optional<StageRun> latestCoding = allStages.stream()
            .filter(sr -> sr.getStageType() == StageType.coding
                && sr.getStatus() == StageStatus.completed)
            .reduce((a, b) -> b); // last one

        if (latestCoding.isEmpty()) {
            pipelineEngine.transitionTo(stageRun, StageStatus.failed);
            return;
        }

        // Build file map for sandbox
        Map<String, String> files = new HashMap<>();
        artifactRepository.findByStageRun(latestCoding.get()).ifPresent(artifact ->
            codeFileRepository.findByArtifactOrderByPathAsc(artifact)
                .forEach(f -> files.put(f.getPath(), f.getContent()))
        );

        String techStack = stageRun.getPipelineRun().getProject().getTechStack();

        try {
            var result = sandbox.runTests(files, techStack,
                line -> eventPublisher.publishStreamChunk(stageRun.getId(), line));

            // Save test result artifact
            saveArtifact(stageRun, Artifact.ArtifactType.test_result,
                "Test Results", result.output());

            if (result.exitCode() == 0) {
                // All tests passed
                pipelineEngine.transitionTo(stageRun, StageStatus.waiting_approval);
            } else {
                // Tests failed — check retry count
                long testingIterations = allStages.stream()
                    .filter(sr -> sr.getStageType() == StageType.testing).count();

                AgentConfig cfg = agentConfigRepository.findByStageType(StageType.testing).orElseThrow();
                if (testingIterations >= cfg.getMaxRetries()) {
                    log.warn("Max retries ({}) reached for pipeline {}",
                        cfg.getMaxRetries(), stageRun.getPipelineRun().getId());
                    pipelineEngine.transitionTo(stageRun, StageStatus.failed);
                    return;
                }

                // Ask LLM to produce fixes
                triggerAutoFix(stageRun, result.output(), files, config);
            }
        } catch (Exception e) {
            log.error("Test execution failed", e);
            pipelineEngine.transitionTo(stageRun, StageStatus.failed);
        }
    }

    @Override
    public void execute(StageRun stageRun) {
        // Testing executor runs Docker directly, not via LLM streaming
        handleResponse(stageRun, "", null);
    }

    private void triggerAutoFix(StageRun testingStageRun, String testOutput,
                                  Map<String, String> currentFiles, AgentConfig config) {
        AgentConfig codingConfig = agentConfigRepository
            .findByStageType(StageType.coding).orElseThrow();

        // Build fix prompt
        String fixPrompt = "Test execution failed. Output:\n```\n" + testOutput + "\n```\n\n"
            + "Current files:\n" + currentFiles.entrySet().stream()
                .map(e -> "File: " + e.getKey() + "\n```\n" + e.getValue() + "\n```")
                .reduce("", (a, b) -> a + "\n" + b);

        var fixMessages = List.of(new ClaudeClient.ChatMessage("user", fixPrompt));

        StringBuilder fixResponse = new StringBuilder();
        claudeClient.streamChat(
            codingConfig.getModel(), codingConfig.getMaxTokens(), codingConfig.getSystemPrompt(),
            fixMessages,
            chunk -> {
                fixResponse.append(chunk);
                eventPublisher.publishStreamChunk(testingStageRun.getId(), chunk);
            },
            complete -> {}
        );

        // Parse fix JSON and create new coding stage run
        var json = parseControlJson(fixResponse.toString());
        if (json == null || !"fix".equals(json.path("type").asText())) {
            pipelineEngine.transitionTo(testingStageRun, StageStatus.failed);
            return;
        }

        // Create new coding stage run with fixes applied
        PipelineRun run = testingStageRun.getPipelineRun();
        int nextIndex = (int) stageRunRepository2.findByPipelineRunOrderByOrderIndexAsc(run).size();
        StageRun fixRun = pipelineEngine.createAndStartStage(run, StageType.coding, nextIndex);

        // Create artifact and apply file fixes
        var fixArtifact = new Artifact();
        fixArtifact.setStageRun(fixRun);
        fixArtifact.setType(Artifact.ArtifactType.code);
        fixArtifact.setTitle("Auto-fix Iteration");
        fixArtifact = artifactRepository.save(fixArtifact);

        // Start with current files, apply fixes on top
        Map<String, String> mergedFiles = new HashMap<>(currentFiles);
        for (var fileNode : json.path("files")) {
            mergedFiles.put(fileNode.path("path").asText(), fileNode.path("content").asText());
        }
        for (var entry : mergedFiles.entrySet()) {
            var cf = new com.devflow.domain.pipeline.model.CodeFile();
            cf.setArtifact(fixArtifact);
            cf.setPath(entry.getKey());
            cf.setContent(entry.getValue());
            codeFileRepository.save(cf);
        }

        // Mark fix coding run as completed, then create new testing run
        pipelineEngine.transitionTo(fixRun, StageStatus.waiting_approval);
        pipelineEngine.transitionTo(fixRun, StageStatus.completed);

        int testIndex = (int) stageRunRepository2.findByPipelineRunOrderByOrderIndexAsc(run).size();
        StageRun newTestRun = pipelineEngine.createAndStartStage(run, StageType.testing, testIndex);
        pipelineEngine.executeStage(newTestRun);
    }
}
