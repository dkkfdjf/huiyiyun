# 慧医云 · 本地知识库 AI 问答(RAG)框架

> 状态:**框架已搭好,可编译可跑通链路**,但三大核心件(向量库 / 嵌入模型 / LLM)目前是零依赖 stub。
> 选型是**留给你的决策**——本文给矩阵和推荐,你拍板后各加一个 Bean 即可,业务层零改动。

## 一句话定位
管理员把知识(文档/文本)灌进系统,用户提问时系统**先向量检索相关片段,再喂给大模型作答,并附来源**——
答案可溯源、防幻觉。所有件可插拔,框架已就位,换件不动业务。

## 架构

```
                       ┌─────────────── 可插拔件(三选一换) ───────────────┐
导入 ingest:           │                                                  │
  原文 → TextChunker ─→ EmbeddingProvider ─→ VectorStore(存向量)          │
        (切块)          (文本→向量)           ↑                           │
                          ↓                  │ MySQL 只存文本+元信息        │
                        kb_chunk(落库)        │ (kb_document/kb_chunk)      │
                                              │                              │
提问 ask:                                     │                              │
  问题 → EmbeddingProvider ─→ VectorStore.search(topK) → 回表取文本           │
                                                          ↓                  │
                                          命中片段 + 问题 → ChatProvider → 自然语言回答
                                                          ↓
                                            KbAnswerVO{answer, sources[]}
```

三大可插拔件(均在 `com.huiyi.modules.knowledge`):

| 件 | 接口 | 默认 stub(现在跑的就是它) | 何时该换 |
|---|---|---|---|
| 嵌入 | `embed/EmbeddingProvider` | `HashingEmbeddingProvider`(特征哈希,**词面相似非语义**) | 一上来就该换——**它对检索质量影响最大** |
| 向量库 | `store/VectorStore` | `InMemoryVectorStore`(进程内,重启即丢,O(n) 扫描) | 数据量大 / 要持久化 / 多实例时换 |
| 作答 | `llm/ChatProvider` | `TemplatedChatProvider`(不调模型,只回显命中片段) | 要"自然语言回答"时换 |

**默认 stub 的意义**:让整条链路在没接任何外部服务时也能端到端跑通(导入→检索→命中→返回),
方便先验证管道、再逐步换上真实件。每个默认实现都标了 `@ConditionalOnMissingBean`——
你一旦注册自己的实现,默认 stub 自动让位,`KnowledgeService` 一行不改。

## 现在就能跑(默认 stub)
启动后端(V9 建表 + `kb.enabled=true`),用管理员 token:
```
POST /api/v1/kb/ingest   {"title":"退货政策","text":"药品一经售出,无质量问题不予退货..."}
POST /api/v1/kb/ask      {"query":"药品能退吗","topK":5}
GET  /api/v1/kb/docs
DELETE /api/v1/kb/docs/{id}
```
`ask` 会返回命中的片段(按词面重合度排序)+ 一句"未接 LLM"提示。**这证明管道通了**;
真正语义召回 + 自然语言回答需要换上真实嵌入和 LLM。

## 三个决策点(留给你)+ 推荐

### 决策 1:嵌入模型【最高杠杆,优先换它】
| 选项 | 部署 | 成本 | 中文质量 | 备注 |
|---|---|---|---|---|
| **本地 Ollama + bge-m3 / bge-large-zh** | 本地起 Ollama | 0 调用费,吃显存 | 好 | **推荐**:数据不出本机,契合"本地知识库";Ollama HTTP 一把梭 |
| DashScope `text-embedding-v3` | API | 按 token 计费 | 很好 | 你已在用阿里云(滑块),复用 AccessKey;数据上云 |
| OpenAI `text-embedding-3-small` | API | 按 token 计费 | 中(非中文专精) | 需科学上网 |

**推荐:本地 Ollama + bge-m3**。"本地知识库"就该本地嵌入,数据不外泄;bge-m3 中文强、免费。
换法见下「怎么换」。换嵌入后须清空向量库重建索引(维度变了)。

### 决策 2:向量库
| 选项 | 新增基建 | 适合规模 | 备注 |
|---|---|---|---|
| **沿用 InMemoryVectorStore** | 无 | <1万块 / 单机 demo | **课设演示够用**,配合真实嵌入即可语义检索;重启丢、不可扩 |
| **Qdrant** | 1 个 Docker 容器 | 中小 | **推荐升级**:专用、轻、有官方 Java 客户端、过滤强 |
| Milvus | Docker Compose 多容器 | 大 | 重,课设杀鸡用牛刀 |
| Redis Stack(RediSearch) | 换带模块的 Redis | 中 | 你现有 Redis 是 Win 移植版,**不带 RediSearch 模块**,需换 Docker 版;基建成本不小 |
| pgvector | 换 Postgres | 中 | 你是 MySQL,换库成本最高,不划算 |

**推荐:短期沿用 InMemory;要持久化/规模时上 Qdrant(单容器)。**
向量库只是"管道",换它对回答质量无影响——质量在嵌入模型,别在这里花太多功夫。

### 决策 3:作答 LLM
| 选项 | 部署 | 备注 |
|---|---|---|
| **本地 Ollama + qwen2.5 / qwen3** | 本地 | **推荐**:与嵌入同走 Ollama,数据不出本机 |
| DashScope `qwen-plus` | API | 复用阿里云 AccessKey |
| OpenAI 兼容接口 | API | 需科学上网 |

**推荐:本地 Ollama + qwen**。真实实现里务必:把命中片段塞进 system prompt 当上下文,
并要求"仅依据提供的片段作答、无依据则说明",抑制幻觉。

## 怎么换(每个加一个 Bean 即可)
默认 stub 因 `@ConditionalOnMissingBean` 自动让位。示例骨架(放进 `com.huiyi.modules.knowledge` 下,
按需 `@Component`/`@Service`;密钥走 `@Value` 读 `application-local.yml`,别进 commit):

```java
// 决策1:Ollama 嵌入
@Component
public class OllamaEmbeddingProvider implements EmbeddingProvider {
    private final String base = "http://localhost:11434";   // Ollama
    private final String model = "bge-m3";
    private final RestTemplate http = new RestTemplate();
    public float[] embed(String text) {
        // POST /api/embeddings {model, prompt} → 解析 embedding 数组 → L2 归一化
        ...
    }
    public int dimension() { return 1024; }   // bge-m3 实际维度
    public String name() { return "ollama-bge-m3"; }
}

// 决策2:Qdrant 向量库
@Component
public class QdrantVectorStore implements VectorStore {
    // 用 io.qdrant:client,upsert/search/delete 映射到 Qdrant REST/gRPC
    ...
}

// 决策3:Ollama 作答
@Component
public class OllamaChatProvider implements ChatProvider {
    public String answer(String query, List<String> passages) {
        String prompt = "仅依据以下资料回答,无依据请说明:\n资料:\n" + String.join("\n---\n", passages)
                + "\n问题:" + query;
        // POST /api/generate {model:"qwen2.5", prompt, stream:false} → 取 response
        ...
    }
}
```
> 三者独立,可逐步换(先嵌入 → 再 LLM → 最后向量库),每步都不破坏链路。

## API(均 `/api/v1/kb`,JWT + 管理员)
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/ingest` | `{title,text,sourceType?,sourceRef?}` → `{id}`(同内容去重) |
| POST | `/ask` | `{query,topK?}` → `{answer, sources:[{docId,title,ordinal,snippet,score}]}` |
| GET | `/docs` | 文档列表 |
| DELETE | `/docs/{id}` | 删文档 + 其切块 + 向量 |

## 配置(`application.yml`,`huiyi.kb.*`;密钥放 `application-local.yml`)
```yaml
huiyi.kb:
  enabled: true
  embedding.dimension: 256     # 换真实模型时由模型决定
  chunk.size: 500
  chunk.overlap: 60
```

## 数据模型(MySQL,向量不落 MySQL)
- `kb_document`:id / title / source_type / source_ref / content_hash(SHA-256 去重)/ chunk_count / status / created_by / created_at
- `kb_chunk`:id / doc_id / ordinal / text / token_count / metadata(JSON 文本) / created_at;(doc_id,ordinal) 与向量库 id 对齐

## 演进路线(建议顺序)
1. **现在**:默认 stub 跑通管道(已完成,可编译)。
2. 换真实嵌入(Ollama bge-m3)→ 检索立刻变语义。**清空向量库重建**。
3. 换 LLM(Ollama qwen)→ 拿到自然语言回答。
4. 数据量上来或要持久化时,向量库换 Qdrant。
5. (可选)前端加文件上传/URL 抓取(现仅手输文本);加 `sourceType=file/url` 分支。

> 决策定了告诉我,我按矩阵里的推荐直接落地对应的 Bean。
