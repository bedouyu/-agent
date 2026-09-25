package com.paperagent.ai;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.paperagent.document.DocumentValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 与 DeepSeek 的唯一通信入口。只有用户确认运行后才发出网络请求。
 * 不记录请求正文、响应正文或密钥，也不把原文保存到数据库。
 */
@Service
public class DeepSeekService implements AiModelGateway {

    public static final int MAX_SELECTION_CHARACTERS = 2_000;
    private static final List<String> SUPPORTED_MODELS = List.of("deepseek-flash", "deepseek-v4-pro");
    private static final String EDIT_PROMPT = loadPrompt("/prompts/latex-editor.txt");
    private static final String REVIEW_PROMPT = loadPrompt("/prompts/latex-reviewer.txt");

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final URI completionUri;
    private final String apiKey;

    @Autowired
    public DeepSeekService(
            ObjectMapper objectMapper,
            @Value("${DEEPSEEK_API_KEY:}") String apiKey
    ) {
        this(
                objectMapper,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(),
                URI.create("https://api.deepseek.com/chat/completions"),
                apiKey
        );
    }

    // 包内测试使用本机模拟服务，不消耗模型额度。
    DeepSeekService(ObjectMapper objectMapper, HttpClient httpClient, URI completionUri, String apiKey) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.completionUri = completionUri;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    @Override
    public AiStatusResponse status() {
        return new AiStatusResponse(!apiKey.isBlank(), SUPPORTED_MODELS, MAX_SELECTION_CHARACTERS);
    }

    @Override
    public AiSuggestionResponse suggest(String sourcePath, String originalText, String mode, String model) {
        requireKey();
        if (originalText == null || originalText.isBlank()) {
            throw new DocumentValidationException("请先在左侧源码中选中要处理的文字");
        }
        if (originalText.length() > MAX_SELECTION_CHARACTERS) {
            throw new DocumentValidationException("每次最多发送 2000 个字符，请缩小选区");
        }
        if (!"POLISH".equals(mode) && !"FORMAT".equals(mode)) {
            throw new DocumentValidationException("不支持的 AI 任务类型");
        }
        requireModel(model);

        String task = "POLISH".equals(mode)
                ? "润色所选中文学术表述，改善语法、清晰度和学术语气；保留原意和 LaTeX 结构。"
                : "检查并优化所选 LaTeX 片段的排版写法与格式一致性；不要改变数学内容和论证。";
        JsonNode suggestion = requestJson(model, EDIT_PROMPT, task + "\n\n待处理 LaTeX 片段：\n" + originalText, 4096);
        String suggestedTex = suggestion.path("suggestedTex").asText();
        String explanation = suggestion.path("explanation").asText();
        if (suggestedTex.isBlank() || explanation.isBlank()) {
            throw new AiServiceException(HttpStatus.BAD_GATEWAY, "AI_INVALID_RESPONSE", "模型未返回完整建议，请重试");
        }
        return new AiSuggestionResponse(sourcePath, originalText, suggestedTex, explanation, mode, model);
    }

    @Override
    public ModelReviewDecision review(String originalText, String suggestedTex, String mode, String model) {
        requireKey();
        requireModel(model);
        if (originalText == null || suggestedTex == null || suggestedTex.isBlank()
                || originalText.length() > MAX_SELECTION_CHARACTERS || suggestedTex.length() > 6_000) {
            throw new DocumentValidationException("复核内容不合法或过长");
        }
        String task = "POLISH".equals(mode) ? "语言润色" : "LaTeX 格式修改";
        String input = "任务：" + task + "\n原文：\n" + originalText + "\n\n候选修改：\n" + suggestedTex;
        JsonNode decision = requestJson(model, REVIEW_PROMPT, input, 1024);
        JsonNode approved = decision.path("approved");
        String explanation = decision.path("explanation").asText();
        if (!approved.isBoolean() || explanation.isBlank()) {
            throw new AiServiceException(HttpStatus.BAD_GATEWAY, "AI_INVALID_RESPONSE", "复核模型未返回完整结论");
        }
        return new ModelReviewDecision(approved.asBoolean(), explanation);
    }

    private void requireKey() {
        if (apiKey.isBlank()) {
            throw new AiServiceException(HttpStatus.SERVICE_UNAVAILABLE, "AI_KEY_NOT_CONFIGURED",
                    "未读取到 DEEPSEEK_API_KEY。请重启 IDEA 或在运行配置中加入环境变量。");
        }
    }

    private void requireModel(String model) {
        if (!SUPPORTED_MODELS.contains(model)) {
            throw new DocumentValidationException("不支持的 DeepSeek 模型");
        }
    }

    private JsonNode requestJson(String model, String systemPrompt, String userPrompt, int maxTokens) {
        Map<String, Object> payload = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "thinking", Map.of("type", "disabled"),
                "response_format", Map.of("type", "json_object"),
                "max_tokens", maxTokens,
                "stream", false
        );

        try {
            String requestBody = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder(completionUri)
                    .timeout(Duration.ofSeconds(90))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );

            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new AiServiceException(HttpStatus.BAD_GATEWAY, "AI_AUTH_FAILED", "DeepSeek 拒绝了密钥，请检查或更换密钥");
            }
            if (response.statusCode() == 402) {
                throw new AiServiceException(HttpStatus.BAD_GATEWAY, "AI_BALANCE_LOW", "DeepSeek 账户余额不足");
            }
            if (response.statusCode() == 429) {
                throw new AiServiceException(HttpStatus.BAD_GATEWAY, "AI_RATE_LIMITED", "DeepSeek 请求过于频繁，请稍后再试");
            }
            if (response.statusCode() != 200) {
                throw new AiServiceException(HttpStatus.BAD_GATEWAY, "AI_UPSTREAM_FAILED", "DeepSeek 请求失败（HTTP " + response.statusCode() + "）");
            }

            JsonNode result = objectMapper.readTree(response.body());
            if (result == null) {
                throw new AiServiceException(HttpStatus.BAD_GATEWAY, "AI_INVALID_RESPONSE", "模型返回了空响应，请重试");
            }
            JsonNode firstChoice = result.path("choices").path(0);
            if (!"stop".equals(firstChoice.path("finish_reason").asText())) {
                throw new AiServiceException(HttpStatus.BAD_GATEWAY, "AI_INCOMPLETE", "模型回答不完整，请缩小选区后重试");
            }
            String content = firstChoice.path("message").path("content").asText();
            if (content.isBlank()) {
                throw new AiServiceException(HttpStatus.BAD_GATEWAY, "AI_INVALID_RESPONSE", "模型没有返回建议，请缩小选区后重试");
            }
            JsonNode parsed = objectMapper.readTree(content);
            if (parsed == null) {
                throw new AiServiceException(HttpStatus.BAD_GATEWAY, "AI_INVALID_RESPONSE", "模型没有返回建议，请重试");
            }
            return parsed;
        } catch (AiServiceException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiServiceException(HttpStatus.BAD_GATEWAY, "AI_INTERRUPTED", "模型请求被中断");
        } catch (JacksonException exception) {
            throw new AiServiceException(HttpStatus.BAD_GATEWAY, "AI_INVALID_RESPONSE", "模型返回了无法解析的建议，请缩小选区后重试");
        } catch (IOException exception) {
            // 原始异常可能含 HTTP 请求信息，不直接暴露给界面。
            throw new AiServiceException(HttpStatus.BAD_GATEWAY, "AI_CONNECTION_FAILED", "无法连接 DeepSeek，请检查网络或稍后重试");
        }
    }

    private static String loadPrompt(String path) {
        try (InputStream stream = DeepSeekService.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("缺少提示词模板：" + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取提示词模板：" + path, exception);
        }
    }
}
