package com.devflow.domain.pipeline;

import com.devflow.domain.pipeline.dto.*;
import com.devflow.domain.pipeline.model.*;
import com.devflow.domain.pipeline.model.StageRun.StageStatus;
import com.devflow.domain.pipeline.repository.*;
import com.devflow.engine.PipelineEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController @RequestMapping("/api/stages")
@RequiredArgsConstructor
public class StageController {

    private final StageRunRepository stageRunRepository;
    private final MessageRepository messageRepository;
    private final ArtifactRepository artifactRepository;
    private final CodeFileRepository codeFileRepository;
    private final PipelineEngine pipelineEngine;

    /** User answers a question asked by the Agent */
    @PostMapping("/{stageId}/answer")
    public ResponseEntity<Void> answer(@PathVariable UUID stageId,
                                        @RequestBody AnswerRequest req) {
        var stage = stageRunRepository.findById(stageId).orElseThrow();
        if (stage.getStatus() != StageStatus.waiting_answer) {
            return ResponseEntity.badRequest().build();
        }
        // Save user answer as message
        var msg = new Message();
        msg.setStageRun(stage);
        msg.setRole(Message.Role.user);
        msg.setContent(req.content());
        msg.setType(Message.MessageType.text);
        messageRepository.save(msg);

        // Transition back to running and re-execute
        pipelineEngine.transitionTo(stage, StageStatus.running);
        pipelineEngine.executeStage(stage);
        return ResponseEntity.ok().build();
    }

    /** User selects an option from a choice presented by the Agent */
    @PostMapping("/{stageId}/choose")
    public ResponseEntity<Void> choose(@PathVariable UUID stageId,
                                        @RequestBody ChoiceRequest req) {
        var stage = stageRunRepository.findById(stageId).orElseThrow();
        if (stage.getStatus() != StageStatus.waiting_choice) {
            return ResponseEntity.badRequest().build();
        }
        // Record selection
        var msg = new Message();
        msg.setStageRun(stage);
        msg.setRole(Message.Role.user);
        msg.setContent("Selected option: " + req.optionId());
        msg.setType(Message.MessageType.choice_response);
        msg.setSelectedOption(req.optionId());
        messageRepository.save(msg);

        pipelineEngine.transitionTo(stage, StageStatus.running);
        pipelineEngine.executeStage(stage);
        return ResponseEntity.ok().build();
    }

    /** User approves the artifact and advances to next stage */
    @PostMapping("/{stageId}/approve")
    public ResponseEntity<Void> approve(@PathVariable UUID stageId) {
        var stage = stageRunRepository.findById(stageId).orElseThrow();
        if (stage.getStatus() != StageStatus.waiting_approval) {
            return ResponseEntity.badRequest().build();
        }
        artifactRepository.findByStageRun(stage).ifPresent(a -> {
            a.setApprovedAt(Instant.now());
            artifactRepository.save(a);
        });
        pipelineEngine.onStageCompleted(stage);
        return ResponseEntity.ok().build();
    }

    /** User requests revision with feedback */
    @PostMapping("/{stageId}/revise")
    public ResponseEntity<Void> revise(@PathVariable UUID stageId,
                                        @RequestBody RevisionRequest req) {
        var stage = stageRunRepository.findById(stageId).orElseThrow();
        if (stage.getStatus() != StageStatus.waiting_approval) {
            return ResponseEntity.badRequest().build();
        }
        var msg = new Message();
        msg.setStageRun(stage);
        msg.setRole(Message.Role.user);
        msg.setContent("Revision requested: " + req.feedback());
        msg.setType(Message.MessageType.text);
        messageRepository.save(msg);

        pipelineEngine.transitionTo(stage, StageStatus.waiting_revision);
        pipelineEngine.transitionTo(stage, StageStatus.running);
        pipelineEngine.executeStage(stage);
        return ResponseEntity.ok().build();
    }

    /** Update a code file manually (during WAITING_APPROVAL of coding stage) */
    @PatchMapping("/files/{fileId}")
    public ResponseEntity<Void> updateFile(@PathVariable UUID fileId,
                                            @RequestBody UpdateFileRequest req) {
        var file = codeFileRepository.findById(fileId).orElseThrow();
        file.setContent(req.content());
        file.setUpdatedAt(Instant.now());
        codeFileRepository.save(file);
        return ResponseEntity.ok().build();
    }

    public record UpdateFileRequest(String content) {}
}
