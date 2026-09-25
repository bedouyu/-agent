package com.paperagent.ai;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeepSeekServiceTest {

    @Test
    void missingKeyShouldNotMakeANetworkRequest() {
        DeepSeekService service = new DeepSeekService(
                new ObjectMapper(),
                HttpClient.newHttpClient(),
                URI.create("http://127.0.0.1:1/chat/completions"),
                ""
        );

        assertThat(service.status().configured()).isFalse();
        assertThatThrownBy(() -> service.suggest("main.tex", "测试", "POLISH", "deepseek-flash"))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("DEEPSEEK_API_KEY");
    }

    @Test
    void suggestionShouldSendOnlySelectedSnippetAndParseResult() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String answer = mapper.writeValueAsString(Map.of(
                    "suggestedTex", "这是润色后的句子。",
                    "explanation", "语句更精炼。"
            ));
            String response = mapper.writeValueAsString(Map.of(
                    "choices", List.of(Map.of(
                            "finish_reason", "stop",
                            "message", Map.of("content", answer)
                    ))
            ));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        try {
            DeepSeekService service = new DeepSeekService(
                    mapper,
                    HttpClient.newHttpClient(),
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/chat/completions"),
                    "local-test-key"
            );

            AiSuggestionResponse suggestion = service.suggest(
                    "chapters/body.tex", "这是原句。", "POLISH", "deepseek-flash"
            );

            assertThat(suggestion.suggestedTex()).isEqualTo("这是润色后的句子。");
            assertThat(suggestion.explanation()).isEqualTo("语句更精炼。");
            assertThat(authorization.get()).isEqualTo("Bearer local-test-key");
            JsonNode sent = mapper.readTree(requestBody.get());
            assertThat(sent.path("model").asText()).isEqualTo("deepseek-flash");
            assertThat(sent.path("messages").path(1).path("content").asText())
                    .contains("这是原句。")
                    .doesNotContain("chapters/body.tex");
            assertThat(sent.path("thinking").path("type").asText()).isEqualTo("disabled");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void reviewerShouldReceiveOnlyOriginalAndCandidate() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String answer = mapper.writeValueAsString(Map.of(
                    "approved", true,
                    "explanation", "保留了原意"
            ));
            String response = mapper.writeValueAsString(Map.of(
                    "choices", List.of(Map.of("finish_reason", "stop", "message", Map.of("content", answer)))
            ));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        try {
            DeepSeekService service = new DeepSeekService(
                    mapper, HttpClient.newHttpClient(),
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/chat/completions"),
                    "local-test-key"
            );

            ModelReviewDecision decision = service.review("原文", "候选", "FORMAT", "deepseek-flash");

            assertThat(decision.approved()).isTrue();
            JsonNode sent = mapper.readTree(requestBody.get());
            String content = sent.path("messages").path(1).path("content").asText();
            assertThat(content).contains("原文", "候选").doesNotContain("main.tex");
        } finally {
            server.stop(0);
        }
    }
}
