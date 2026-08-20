# Redis 缓存(阶段一)—— 团队对接说明

慧医云架构演进第一阶段:数据层接入 Redis 缓存。本文写给队友:怎么把项目带着缓存跑起来,以及接新模块的红线。

## 1. 拉代码
前端、后端各自 `git pull`(两个独立仓库)。

## 2. 本机启动 Redis(后端启动前必须先开)
后端启动前 Redis 必须是运行状态,否则两个缓存端点(`GET /materials/page`、`GET /companies`)会 500。

**方式 A —— Windows 移植版(与现环境一致,推荐)**
- 下载 tporadowski/redis 的 Windows 移植版,解压到任意盘(如 `E:\redis`)。
- **一键装服务(推荐)**:仓库带了 `backend/scripts/install-redis-service.bat`,复制到 `redis-server.exe` 同目录,**右键"以管理员身份运行"** → 装成开机自启服务(端口 6379),以后开机自动起,不用每次手动开。
- 或手动装:管理员身份运行 `redis-server.exe --service-install redis.windows-service.conf --service-name Redis`,再 `sc start Redis`。
- 卸载服务:`redis-server.exe --service-uninstall`

**方式 B —— Docker**:`docker run -d --name redis -p 6379:6379 redis:7`

**方式 C —— Memurai**:Windows 原生 Redis 兼容,有免费开发版。

默认连接 `localhost:6379`、无密码。要改就在 `application-local.yml` 覆盖 `spring.data.redis.*`。

### 关于进程 / 端口 / PID(常问)
- **进程**:装成服务后,开机自启的就是 `redis-server.exe`,由 Windows 服务管理器(SCM)拉起,服务名 `Redis`,以 SYSTEM 身份运行。`tasklist | findstr redis` 能看到。
- **端口固定 6379**:写死在 `redis.windows-service.conf` 的 `port 6379`。客户端一律按端口连,不按 PID。
- **PID 不固定**:每次服务启动操作系统分配新 PID,这是正常的,**不需要固定**。判断活没活看端口 / `redis-cli ping` 返回 PONG,不看 PID。
- **队友**:每人在自己开发机上都要把 Redis 跑起来(服务自启 / 手动起 / Docker 任选),后端才能用缓存;**不需要共用同一个 Redis**。

## 3. 后端 rebuild + 重启
本阶段新增/改了这些类,IDEA 里 rebuild 再启动:
- `common/config/RedisConfig.java`(@EnableCaching、缓存管理器、JSON 序列化)
- `modules/cache/CacheMonitorController/Service.java`(缓存状态查询端点,仅管理员)
- `modules/material/EssentialMaterialService.java`、`modules/company/PharmaCompanyService.java`(@Cacheable / @CacheEvict)

## 4. 验证缓存生效(管理员「系统监控」页 / 命令行 / 接口)
缓存监控属运维指标,**不摆在工作台概览 / 顶栏**,而是放在管理员专属的**「系统监控」页**(侧边栏底部「系统」入口,仅管理员可见)。验证用任一种:
- 管理员登录 → 侧边栏「系统」→「系统监控」页,看 Redis 面板(在线 / 命中 / 命中率 / 已缓存条目 TTL)。
- `redis-cli ping` → `PONG` 即在线;`redis-cli dbsize` 看条数;`redis-cli keys "huiyi:*"` 看具体 key;`redis-cli info stats | findstr keyspace` 看命中/未命中。
- 或直接调 `GET /api/v1/cache/stats`(仅管理员),返回在线/缓存条数/命中/命中率/各 key 的 TTL。
- 操作路径:访问「必备材料」页一两次 → 命中数 / 命中率上涨,即缓存命中生效。

> 冷启动查库约 0.86s,命中缓存后约 0.022s,约 40 倍。

## 5. ⚠️ 接新模块的红线
- **只缓存无租户隔离的全局参照数据**(改动少、人人看)。当前已缓存:必备材料分页、已审核药企列表。
- **绝不要**给带租户/行级隔离的业务数据(某公司库存/订单、某机构医师)直接加 `@Cacheable`——会跨租户串读。
- 有写入口的缓存,务必在写方法上加 `@CacheEvict(allEntries = true)`,保证不读旧值。
- 新模块接缓存前,先在群里确认数据是否属于"全局参照数据"。

## 6. 监控端点权限与展示位置
`GET /api/v1/cache/stats` 已加 `@RequiresRole(ADMIN)`,只对管理员开放。前端仅管理员**「系统监控」页**消费(**不在工作台概览 / 顶栏展示**),其它角色看不到入口。
