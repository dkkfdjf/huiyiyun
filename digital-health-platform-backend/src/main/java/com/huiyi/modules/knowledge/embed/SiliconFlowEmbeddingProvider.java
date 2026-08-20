package com.huiyi.modules.knowledge.embed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * SiliconFlow 嵌入 API(OpenAI 兼容 /v1/embeddings),默认模型 BAAI/bge-m3(1024 维语义向量)。
 * opt-in:huiyi.kb.embedding.impl=siliconflow;api-key 与作答共享 huiyi.kb.siliconflow.api-key(application-local.yml gitignore)。
 *
 * 与 SiliconFlowChatProvider 同一平台、同一 key:嵌入+作答都走第三方 API,不依赖本地模型(适配 2核2G 全第三方部署)。
 * DeepSeek 无 embedding 接口,而 RAG 必须向量化——SiliconFlow 同时提供嵌入与托管 DeepSeek 作答,正好一平台搞定。
 * 启用后顶替默认 HashingEmbeddingProvider,KnowledgeService 零改动;返回向量已 L2 归一化。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "huiyi.kb.embedding", name = "impl", havingValue = "siliconflow")
public class SiliconFlowEmbeddingProvider implements EmbeddingProvider {

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final int dim;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    public SiliconFlowEmbeddingProvider(
            @Value("${huiyi.kb.siliconflow.base-url:https://api.siliconflow.cn}") String baseUrl,
            @Value("${huiyi.kb.siliconflow.api-key:}") String apiKey,
            @Value("${huiyi.kb.siliconflow.embedding.model:BAAI/bge-m3}") String model,
            @Value("${huiyi.kb.siliconflow.embedding.dimension:1024}") int dim) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.apiKey = apiKey;
        this.model = model;
        this.dim = dim;
        log.info("SiliconFlow embedding provider enabled: base={} model={} dim={}", this.baseUrl, model, dim);
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[dim];
        }
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
            body.put("input", text);
            body.put("encoding_format", "float");
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/embeddings"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) {
                log.warn("siliconflow embed non-2xx {}: {}", resp.statusCode(), resp.body());
                return new float[dim];
            }
            JsonNode arr = mapper.readTree(resp.body()).path("data").path(0).path("embedding");
            if (!arr.isArray()) {
                return new float[dim];
            }
            float[] v = new float[arr.size()];
            double norm = 0;
            for (int i = 0; i < arr.size(); i++) {
                v[i] = (float) arr.get(i).asDouble();
                norm += (double) v[i] * v[i];
            }
            if (norm > 0) {                      // L2 归一化,使余弦=点积(VectorStore 依赖)
                double s = Math.sqrt(norm);
                for (int i = 0; i < v.length; i++) {
                    v[i] /= s;
                }
            }
            return v;
        } catch (Exception e) {
            log.warn("siliconflow embed error: {}", e.getMessage());
            return new float[dim];
        }
    }

    @Override
    public int dimension() {
        return dim;
    }

    @Override
    public String name() {
        return "siliconflow-" + model;
    }
}
