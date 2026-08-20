# 外部药品数据接入设计文档

**日期:** 2025-08-06
**项目:** 数字医疗平台 (huiyiyiyun-frontend)
**状态:** 设计完成，待实现

---

## 1. 需求概述

### 1.1 目标
将外部实时药品数据接入到医院内部系统，供医生/药师使用。

### 1.2 核心需求
- **数据同步**: 从外部 API 同步药品基础数据到本地
- **价格监控**: 实时显示药品价格变动
- **无后端**: 纯前端方案，使用本地缓存

### 1.3 更新频率
| 数据类型 | 更新频率 |
|---------|---------|
| 药品基础数据 | 每天一次 |
| 药品价格数据 | 每小时一次 |

---

## 2. 架构设计

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                          前端应用                              │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌───────────────┐  ┌───────────────┐  ┌───────────────┐  │
│  │  药品管理页面  │  │  价格监控面板  │  │  药品详情页    │  │
│  └───────┬───────┘  └───────┬───────┘  └───────┬───────┘  │
│          │                  │                  │          │
│          └──────────────────┼──────────────────┘          │
│                             ▼                               │
│  ┌─────────────────────────────────────────────────────────┐│
│  │               药品数据服务层 (DrugDataService)           ││
│  │  ┌─────────────────┐  ┌─────────────────┐              ││
│  │  │   基础数据模块   │  │   价格数据模块   │              ││
│  │  │  (1天/次更新)    │  │  (1小时/次更新)  │              ││
│  │  └────────┬────────┘  └────────┬────────┘              ││
│  └───────────┼──────────────────┼─────────────────────────┘│
│              ▼                  ▼                            │
│  ┌─────────────────────┐  ┌─────────────────────────────┐  │
│  │   localStorage       │  │   IndexedDB                 │  │
│  │   药品基础信息        │  │   价格历史记录 (可存大量)     │  │
│  └─────────────────────┘  └─────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
                    ┌──────────────────┐
                    │  外部药品数据 API │
                    │  (待选择服务商)   │
                    └──────────────────┘
```

### 2.2 分层缓存策略

| 存储层 | 存储内容 | 容量 | 过期时间 |
|-------|---------|------|---------|
| localStorage | 药品基础信息字典 | ~5MB | 24小时 |
| IndexedDB | 价格历史记录 | 大容量 | 保留30天 |

---

## 3. 数据源接入层

### 3.1 抽象接口

```javascript
interface DrugDataSource {
  // 获取单个药品基础信息
  getDrugBaseInfo(code: string): Promise<DrugInfo>

  // 获取药品价格
  getDrugPrice(code: string): Promise<PriceData>

  // 搜索药品
  searchDrugs(keyword: string): Promise<Drug[]>

  // 批量获取药品信息
  getDrugsBatch(codes: string[]): Promise<Drug[]>
}
```

### 3.2 适配器模式

支持多种数据源，通过配置切换：

| 适配器 | 状态 | 说明 |
|--------|------|------|
| MockAdapter | 待实现 | 开发阶段使用模拟数据 |
| YaozhiAdapter | 待实现 | 药智网 API |
| NMPAAdapter | 待实现 | 国家药监局 API |

### 3.3 配置切换

```javascript
// src/config/drug-data-source.js
export const DRUG_DATA_SOURCE = 'mock' // 'mock' | 'yaozhi' | 'nmpa'
```

---

## 4. 目录结构

```
src/
├── api/
│   └── drug.js                    # 现有的药品 API
├── services/
│   └── drug-data/                 # 新增：药品数据服务
│       ├── index.js              # 服务入口
│       ├── adapters/             # API 适配器
│       │   ├── base.js           # 基础接口定义
│       │   ├── mock.js           # 模拟数据适配器
│       │   ├── yaozhi.js         # 药智网适配器（待实现）
│       │   └── nmpa.js           # 国家药监局适配器（待实现）
│       ├── storage/              # 存储层
│       │   ├── base-cache.js     # 基础数据缓存 (localStorage)
│       │   └── price-history.js  # 价格历史 (IndexedDB)
│       └── sync.js               # 同步调度器
├── stores/
│   └── drug-data.js              # Pinia 状态管理
├── composables/
│   └── useDrugData.js            # 组合式 API
└── utils/
    └── request.js                # 现有的 HTTP 请求工具
```

---

## 5. 数据源评估

### 5.1 推荐数据源

| 数据源 | 类型 | 特点 | 适合场景 |
|--------|------|------|---------|
| 药智网 | 商业API | 数据全面，含价格 | 综合数据 |
| 国家药监局 | 政府数据 | 权威，免费 | 药品批文信息 |
| 丁香园 | 医疗专业 | 临床数据准确 | 用药指导 |

### 5.2 不推荐的数据源

- **淘宝/电商平台**: 无官方 API，存在法律风险，数据不规范
- **非授权爬虫**: 违反服务条款，稳定性差

---

## 6. 核心功能模块

### 6.1 基础数据模块 (BaseDataService)

**职责:**
- 管理药品基础信息的缓存
- 每天自动同步一次
- 提供药品查询接口

**接口:**
```javascript
class BaseDataService {
  async getDrugInfo(code: string): Promise<DrugInfo | null>
  async searchDrugs(keyword: string): Promise<Drug[]>
  async sync(): Promise<void>
  private isExpired(): boolean
}
```

### 6.2 价格数据模块 (PriceDataService)

**职责:**
- 管理价格历史数据
- 每小时更新价格
- 支持价格趋势分析

**接口:**
```javascript
class PriceDataService {
  async getCurrentPrice(code: string): Promise<Price | null>
  async getPriceHistory(code: string, days: number): Promise<Price[]>
  async sync(): Promise<void>
  private saveToIndexedDB(data: Price[]): Promise<void>
}
```

### 6.3 同步调度器 (SyncScheduler)

**职责:**
- 管理定时同步任务
- 页面可见性检测优化
- 错误重试机制

---

## 7. 状态管理 (Pinia Store)

```javascript
// stores/drug-data.js
export const useDrugDataStore = defineStore('drugData', {
  state: () => ({
    drugs: {},           // 药品基础信息字典
    prices: {},          // 当前价格
    lastSync: null,      // 最后同步时间
    syncing: false       // 同步中状态
  }),

  actions: {
    async fetchDrug(code) { /* ... */ },
    async searchDrugs(keyword) { /* ... */ },
    async syncData() { /* ... */ }
  }
})
```

---

## 8. 实施步骤

1. ✅ **架构设计完成** - 本文档
2. ⏳ **数据源选择** - 待确定具体 API 服务商
3. ⏳ **基础框架搭建** - 目录结构、接口定义
4. ⏳ **Mock 适配器实现** - 用于开发测试
5. ⏳ **存储层实现** - localStorage + IndexedDB
6. ⏳ **同步调度器实现** - 定时任务管理
7. ⏳ **真实 API 适配器** - 根据选择的服务商实现
8. ⏳ **UI 集成** - 药品管理、价格监控页面

---

## 9. 技术风险与应对

| 风险 | 应对措施 |
|------|---------|
| 外部 API CORS 问题 | 需要确认 API 是否支持前端调用，必要时考虑代理 |
| API 调用配额限制 | 本地缓存减少调用，错峰请求 |
| 数据格式不一致 | 适配器层统一转换格式 |
| IndexedDB 兼容性 | 添加 polyfill 降级方案 |

---

## 10. 待确认事项

- [ ] 确定具体的药品数据 API 服务商
- [ ] 确认 API 是否支持前端直接调用（CORS）
- [ ] 获取 API 密钥/访问凭证
- [ ] 确认 API 调用配额和计费方式

---

**设计版本:** 1.0
**更新日期:** 2025-08-06
