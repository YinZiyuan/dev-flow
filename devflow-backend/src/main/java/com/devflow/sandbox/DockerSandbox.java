package com.devflow.sandbox;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Component @Slf4j
public class DockerSandbox {

    private final DockerClient docker;

    public DockerSandbox() {
        var config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        var httpClient = new ApacheDockerHttpClient.Builder()
            .dockerHost(config.getDockerHost())
            .maxConnections(10)
            .connectionTimeout(Duration.ofSeconds(30))
            .responseTimeout(Duration.ofMinutes(30))
            .build();
        this.docker = DockerClientImpl.getInstance(config, httpClient);
    }

    public record RunResult(int exitCode, String output) {}

    /**
     * Write code files to a temp directory, run tests inside a Docker container,
     * stream output to onOutput, return exit code + full output.
     */
    public RunResult runTests(Map<String, String> files, String techStack,
                               Consumer<String> onOutput) throws Exception {
        // Write files to temp directory
        Path workDir = Files.createTempDirectory("devflow-test-" + UUID.randomUUID());
        try {
            for (var entry : files.entrySet()) {
                Path filePath = workDir.resolve(entry.getKey());
                Files.createDirectories(filePath.getParent());
                Files.writeString(filePath, entry.getValue());
            }

            // Determine Docker image and test command based on tech stack
            String image = resolveImage(techStack);
            List<String> cmd = resolveTestCommand(techStack);

            // Create container
            var container = docker.createContainerCmd(image)
                .withCmd(cmd)
                .withWorkingDir("/workspace")
                .withHostConfig(HostConfig.newHostConfig()
                    .withBinds(new Bind(workDir.toAbsolutePath().toString(),
                        new Volume("/workspace")))
                    .withMemory(2L * 1024 * 1024 * 1024) // 2GB
                    .withNanoCPUs(2_000_000_000L)) // 2 CPUs
                .exec();

            String containerId = container.getId();
            docker.startContainerCmd(containerId).exec();

            // Stream logs
            StringBuilder output = new StringBuilder();
            docker.logContainerCmd(containerId)
                .withStdOut(true).withStdErr(true).withFollowStream(true)
                .exec(new ResultCallback.Adapter<Frame>() {
                    @Override public void onNext(Frame frame) {
                        String line = new String(frame.getPayload(), StandardCharsets.UTF_8);
                        output.append(line);
                        onOutput.accept(line);
                    }
                }).awaitCompletion(30, TimeUnit.MINUTES);

            // Get exit code
            int exitCode = docker.inspectContainerCmd(containerId)
                .exec().getState().getExitCodeLong().intValue();

            docker.removeContainerCmd(containerId).withForce(true).exec();

            return new RunResult(exitCode, output.toString());
        } finally {
            deleteDirectory(workDir);
        }
    }

    private String resolveImage(String techStack) {
        if (techStack == null) return "maven:3.9-eclipse-temurin-21";
        String lower = techStack.toLowerCase();
        if (lower.contains("java") || lower.contains("spring")) return "maven:3.9-eclipse-temurin-21";
        if (lower.contains("node") || lower.contains("next")) return "node:20-alpine";
        if (lower.contains("python")) return "python:3.12-slim";
        if (lower.contains("go")) return "golang:1.22-alpine";
        return "ubuntu:22.04";
    }

    private List<String> resolveTestCommand(String techStack) {
        if (techStack == null) return List.of("mvn", "test");
        String lower = techStack.toLowerCase();
        if (lower.contains("java") || lower.contains("spring")) return List.of("mvn", "test", "-q");
        if (lower.contains("node")) return List.of("npm", "test");
        if (lower.contains("python")) return List.of("python", "-m", "pytest");
        if (lower.contains("go")) return List.of("go", "test", "./...");
        return List.of("mvn", "test");
    }

    private void deleteDirectory(Path path) {
        try {
            Files.walk(path).sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
        } catch (IOException ignored) {}
    }
}
