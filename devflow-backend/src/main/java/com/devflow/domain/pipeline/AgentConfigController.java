package com.devflow.domain.pipeline;

import com.devflow.domain.pipeline.model.AgentConfig;
import com.devflow.domain.pipeline.repository.AgentConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/api/agent-configs")
@RequiredArgsConstructor
public class AgentConfigController {

    private final AgentConfigRepository repo;

    @GetMapping
    public List<AgentConfig> list() { return repo.findAll(); }

    @PutMapping("/{id}")
    public AgentConfig update(@PathVariable UUID id, @RequestBody UpdateRequest req) {
        var config = repo.findById(id).orElseThrow();
        config.setSystemPrompt(req.systemPrompt());
        config.setModel(req.model());
        config.setMaxTokens(req.maxTokens());
        config.setMaxRetries(req.maxRetries());
        config.setUpdatedAt(Instant.now());
        return repo.save(config);
    }

    public record UpdateRequest(String systemPrompt, String model, int maxTokens, int maxRetries) {}
}
