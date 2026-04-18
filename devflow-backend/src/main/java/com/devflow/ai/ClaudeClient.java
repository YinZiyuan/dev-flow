package com.devflow.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component @RequiredArgsConstructor @Slf4j
public class ClaudeClient {

    @Value("${app.claude.api-key}") private String apiKey;
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
        .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public record ChatMessage(String role, String content) {}

    /**
     * Stream a response from Claude. Calls onChunk for each text delta,
     * onComplete with the full accumulated text when done.
     */
    public void streamChat(String model, int maxTokens, String systemPrompt,
                            List<ChatMessage> messages,
                            Consumer<String> onChunk,
                            Consumer<String> onComplete) {
        try {
            var body = mapper.writeValueAsString(Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "system", systemPrompt,
                "stream", true,
                "messages", messages.stream()
                    .map(m -> Map.of("role", m.role(), "content", m.content()))
                    .toList()
            ));

            var request = new Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .post(RequestBody.create(body, MediaType.parse("application/json")))
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("Claude API error: " + response.code()
                        + " " + response.body().string());
                }
                var sb = new StringBuilder();
                try (var reader = new BufferedReader(
                        new InputStreamReader(response.body().byteStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.startsWith("data: ")) continue;
                        String data = line.substring(6).trim();
                        if ("[DONE]".equals(data)) break;
                        try {
                            var node = mapper.readTree(data);
                            if ("content_block_delta".equals(node.path("type").asText())) {
                                String chunk = node.path("delta").path("text").asText("");
                                if (!chunk.isEmpty()) {
                                    sb.append(chunk);
                                    onChunk.accept(chunk);
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }
                onComplete.accept(sb.toString());
            }
        } catch (Exception e) {
            log.error("Claude API streaming failed", e);
            throw new RuntimeException("Claude API error: " + e.getMessage(), e);
        }
    }
}
