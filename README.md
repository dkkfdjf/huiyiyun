# 智慧数字医疗平台（慧医云）

多租户医疗 SaaS 平台：面向药企 / 医疗机构的统一数字化底座，内置 **RAG 知识库问答**（答案附来源、可溯源、无命中主动拒答）。

> 在线演示：[huiyiyun.echeng.xyz](https://huiyiyun.echeng.xyz)
> 技术栈：Spring Boot 3.2 · MyBatis-Plus · MySQL · Redis · JWT · Flyway · Vue 3 · Qwen3(RAG)

## 核心特性

### 1. 多租户行级隔离 + 平台基座
- **DataScope SQL 拦截器**：基于 scope 标签在 SQL 层统一注入租户过滤条件，防止跨药企 / 机构串读，业务代码零侵入
- **JWT + Redis 双管会话**：无状态鉴权 + 服务端会话登记，支持即时吊销（登出 / 封禁立即生效）
- **Flyway 版本化数据库**：schema 变更随代码走，多环境一致迁移
- Druid 连接池监控、多角色权限体系

### 2. RAG 知识库问答
- 文档切块 → **bge-m3 向量嵌入** → 余弦相似度检索 → **Qwen3** 生成作答
- 答案附**知识来源引用**，可点开溯源到原文档片段
- 事件驱动增量更新 + 内容对账：文档变更后向量索引自动保鲜

### 3. 防幻觉三道闸
- 检索 **min-score 阈值**过滤弱命中
- 无命中 / 低置信度时**主动拒答**而非编造
- 高危操作转**人工确认**通道

## 架构

```
┌────────────┐     ┌──────────────────────────────────┐
│  Vue 3 前端 │────▶│  Spring Boot 3.2 (port 8080)      │
└────────────┘     │  ├─ JWT 认证 + Redis 会话吊销      │
                   │  ├─ DataScope SQL 拦截器(行级隔离) │
┌────────────┐     │  ├─ RAG: 切块→bge-m3→余弦检索     │
│ knowledge/ │────▶│  └─ Qwen3 生成 + 来源溯源         │
│ 文档上传    │     └──────┬───────────┬───────────────┘
└────────────┘            │           │
                    ┌─────▼────┐ ┌────▼────┐
                    │  MySQL   │ │  Redis  │
                    │ (Flyway) │ │ (会话)  │
                    └──────────┘ └─────────┘
```

## 快速开始

```bash
# 1. 准备数据库
mysql -uroot -p < digital-health-platform-backend/src/main/resources/db/migration/*.sql
# （Flyway 会在首次启动时自动执行迁移）

# 2. 启动后端（密钥走 application-local.yml，已 gitignore）
cd digital-health-platform-backend
cp application-local.yml.example application-local.yml   # 填入 MySQL 密码等
./mvnw spring-boot:run

# 3. 启动前端
cd digital-health-platform-frontend
npm install
npm run dev
```

## 目录结构

```
├── digital-health-platform-backend/    # Spring Boot 3.2 单体后端
│   └── src/main/resources/
│       ├── application.yml             # 主配置（密钥占位符）
│       └── db/migration/               # Flyway 版本化迁移脚本
├── digital-health-platform-frontend/   # Vue 3 + Vite + Element Plus
└── docs/                               # 设计文档
```

## 联系方式

- GitHub: [dkkfdjf](https://github.com/dkkfdjf)
- 邮箱: 19574486919@163.com
