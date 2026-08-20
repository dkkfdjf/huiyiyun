package com.huiyi.modules.knowledge.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huiyi.modules.knowledge.KbUnavailableException;
import com.huiyi.modules.knowledge.dto.ChatTurn;
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
import java.util.List;

/**
 * SiliconFlow 作答(OpenAI 兼容 /v1/chat/completions),默认模型 deepseek-ai/DeepSeek-V3(SiliconFlow 托管)。
 * opt-in:huiyi.kb.chat.impl=siliconflow;api-key 与嵌入共享 huiyi.kb.siliconflow.api-key(application-local.yml gitignore)。
 *
 * 为什么收敛到 SiliconFlow 一个平台:嵌入(SiliconFlowEmbeddingProvider)+ 作答(本类)同平台、同一 key,
 * 部署到 2核2G 小机时全走第三方 API、零本地模型、配置最简。启用后顶替默认 TemplatedChatProvider,RagService 零改动。
 *
 * RAG prompt:system 约束"仅依据资料、无依据说明、标来源" + 命中片段 + 用户问题;temperature=0.2 贴资料少发挥。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "huiyi.kb.chat", name = "impl", havingValue = "siliconflow")
public class SiliconFlowChatProvider implements ChatProvider {

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    public SiliconFlowChatProvider(
            @Value("${huiyi.kb.siliconflow.base-url:https://api.siliconflow.cn}") String baseUrl,
            @Value("${huiyi.kb.siliconflow.api-key:}") String apiKey,
            @Value("${huiyi.kb.siliconflow.chat.model:deepseek-ai/DeepSeek-V3}") String model) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.apiKey = apiKey;
        this.model = model;
        log.info("SiliconFlow chat provider enabled: base={} model={}", this.baseUrl, model);
    }

    @Override
    public String answer(String query, List<String> passages, List<ChatTurn> history) {
        if (apiKey == null || apiKey.isBlank()) {
            return "未配置 SiliconFlow API Key(请检查 application-local.yml 的 huiyi.kb.siliconflow.api-key)。";
        }
        // 三分支判断,堵两类退化:① 垃圾话/无关输入也照搬资料复述;② 把"正经但知识库无数据"的问题误判成越界拒答。
        // 关键:拒答句「不在…服务范围」只用于①非业务问题(玩梗/脏话/闲聊);正经问题若资料答不了,走②「暂无该数据」,
        // 措辞刻意不同——使 KbRefusalDetector 只把垃圾话判越界,不给"哪个药库存最低"这类正经问题误打标记。temperature=0.2。
        StringBuilder ctx = new StringBuilder()
                .append("你是慧医云知识库助手。按下面两步判断后作答:\n")
                .append("① 用户输入是否为一个【清晰的业务问题】(关于药品/用药、政策、必备材料、药企、机构、库存、业务流程等)?")
                .append("若不是(闲聊、感叹、脏话、玩笑、玩梗、与医疗/业务明显无关),只回一句:")
                .append("「该内容不在知识库服务范围,请提出与药品、政策、必备材料或药企/机构相关的具体问题」,")
                .append("不复述资料、不改写此句;此句仅用于这类输入。\n")
                .append("② 若是清晰业务问题,再看【资料】能否回答:")
                .append("能答则依据资料简明作答并用 [序号] 标来源,资料未覆盖处可用通用知识补充并注明「(通用知识,非本平台数据)」;")
                .append("若资料不能答(如实时库存数、某网点营业额等平台动态数据资料里没有),如实说「知识库暂无该数据」,")
                .append("可一句话引导去对应业务页面,不要复述无关资料、不要使用①里的拒答句。\n")
                .append("不得编造具体平台数据(库存数、某网点/机构详情等)。回答简短不啰嗦。\n\n【资料】\n");
        if (passages == null || passages.isEmpty()) {
            ctx.append("(无相关资料)");
        } else {
            for (int i = 0; i < passages.size(); i++) {
                ctx.append('[').append(i + 1).append("] ").append(passages.get(i)).append('\n');
            }
        }
        return callLlm(ctx.toString(), query, history, 0.2);
    }

    /** 双能力回退:本地 0 命中时,用模型通用知识作答;约束其不臆造具体平台数据。temperature=0.5 略放开。 */
    @Override
    public String answerGeneral(String query, List<ChatTurn> history) {
        if (apiKey == null || apiKey.isBlank()) {
            return "未配置 SiliconFlow API Key(无法提供通用知识作答)。";
        }
        String sys = "你是慧医云助手。本地知识库未检索到相关资料。按下面判断:"
                + "① 用户输入若不是清晰业务问题(闲聊、脏话、玩笑、玩梗、与医疗/业务无关),只回一句「该内容不在知识库服务范围,请提出与药品、政策、必备材料或药企/机构相关的具体问题」;"
                + "② 若是清晰业务问题,用通用知识尽量回答;但涉及本平台专属数据(具体库存、某网点/机构/药企详情、营业额等)通用知识无从得知,如实说「我暂无该数据」并引导去对应业务页面,不要编造。"
                + "回答简短不啰嗦。";
        return callLlm(sys, query, history, 0.5);
    }

    /** 统一的 chat/completions 调用:answer(贴资料)与 answerGeneral(通用知识)共用,只差 system prompt 与 temperature。
     *  history(多轮记忆)按原顺序注入 system 之后、当前问题之前,使"它/上面的"等追问能结合上文消解指代。
     *  <p>调用失败(网络/HTTP/解析)抛 {@link KbUnavailableException}——作答不可用是异常,不是答案;
     *  由 RagService 走 error 分支提示重试,避免把「Connection reset」这类文本当成答案挂在「通用知识」标签下。 */
    private String callLlm(String systemPrompt, String query, List<ChatTurn> history, double temperature) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
            ArrayNode messages = body.putArray("messages");
            ObjectNode sys = messages.addObject();
            sys.put("role", "system");
            sys.put("content", systemPrompt);
            // 多轮记忆:历史轮次按原顺序注入(system 之后、当前问题之前)。只接受 user/assistant 两种角色,防异常 role。
            if (history != null) {
                for (ChatTurn t : history) {
                    if (t == null || t.getRole() == null || t.getContent() == null) continue;
                    if (!"user".equals(t.getRole()) && !"assistant".equals(t.getRole())) continue;
                    ObjectNode h = messages.addObject();
                    h.put("role", t.getRole());
                    h.put("content", t.getContent());
                }
            }
            ObjectNode usr = messages.addObject();
            usr.put("role", "user");
            usr.put("content", query == null ? "" : query);
            body.put("temperature", temperature);
            body.put("max_tokens", 1024);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (resp.statusCode() / 100 != 2) {
                log.warn("siliconflow chat non-2xx {}: {}", resp.statusCode(), resp.body());
                throw new KbUnavailableException("SiliconFlow 调用失败(HTTP " + resp.statusCode() + "),请检查 key / 额度 / 网络。");
            }
            JsonNode content = mapper.readTree(resp.body()).path("choices").path(0).path("message").path("content");
            log.info("siliconflow chat ok model={} temp={} q='{}'", model, temperature, abbrev(query, 40));
            return content.isMissingNode() ? "(SiliconFlow 返回为空)" : content.asText();
        } catch (KbUnavailableException e) {
            throw e;   // 自己抛的服务不可用,原样上抛,不被下面的 catch(Exception) 吞掉
        } catch (Exception e) {
            log.warn("siliconflow chat error: {}", e.getMessage());
            throw new KbUnavailableException("SiliconFlow 网络异常:" + e.getMessage(), e);
        }
    }

    @Override
    public String name() {
        return "siliconflow-" + model;
    }

    private static String abbrev(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
