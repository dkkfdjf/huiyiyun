<template>
  <div class="sys">
    <div class="pagehead">
      <div>
        <h1>系统监控</h1>
        <div class="sub">{{ today }} · 管理员可见的系统运行状况</div>
      </div>
    </div>

    <!-- 运维控制:把散落在各监控卡片里的可操作项(验证码模式 / 知识库开关)统一收到最前面 -->
    <div class="card ctrl">
      <div class="card__head"><h3>运维控制</h3><span class="more">可操作项集中在此 · 监控指标见下</span></div>
      <div class="card__body ctrl__body">
        <div class="cfg__row">
          <div class="cfg__txt">
            <div class="cfg__t">滑块验证模式</div>
            <div class="cfg__d">控制登录是否走阿里云滑块(计费)。关闭后产生 0 次计费调用,用于防费用超限。</div>
          </div>
          <el-select v-model="captchaMode" style="width: 232px" :disabled="cfgSaving" @change="onCaptchaModeChange">
            <el-option value="enabled" label="启用 · 滑块验证(计费)" />
            <el-option value="pass" label="关闭 · 放行(不计费)" />
            <el-option value="lock" label="关闭 · 锁定(仅管理员)" />
          </el-select>
        </div>
        <div class="cfg__warn" v-if="captchaMode === 'lock'">⚠ 锁定后<b>普通账号(药企/机构/医师)无法登录</b>;管理员仍可登录并在本页改回,不会把自己锁在门外。用于冻结止损。</div>
        <div class="cfg__row">
          <div class="cfg__txt">
            <div class="cfg__t">知识库问答开关</div>
            <div class="cfg__d">关闭后悬浮精灵提示"已被管理员禁用"、/ask 直接拒绝(防嵌入/LLM 计费超支)。监控指标不受影响。</div>
          </div>
          <el-select v-model="kbEnabled" style="width: 232px" :disabled="cfgSaving" @change="onKbEnabledChange">
            <el-option :value="true" label="启用 · 知识库问答" />
            <el-option :value="false" label="关闭 · 暂停问答(防计费)" />
          </el-select>
        </div>
        <div class="cfg__row">
          <div class="cfg__txt">
            <div class="cfg__t">游客体验入口</div>
            <div class="cfg__d">开放后登录页出现"游客体验"按钮,免账号签发只读临时令牌浏览平台;关闭则入口不可用(部署后不想开放游客即关)。</div>
          </div>
          <el-select v-model="guestEnabled" style="width: 232px" :disabled="cfgSaving" @change="onGuestEnabledChange">
            <el-option :value="true" label="开放 · 游客可体验" />
            <el-option :value="false" label="关闭 · 仅账号登录" />
          </el-select>
        </div>
      </div>
    </div>

    <div v-loading="loading" class="metrics">
    <div class="sec"><span class="sec__label">运行健康</span></div>
    <div class="kpis">
      <KpiCard label="JVM 堆内存" :value="heapPct" unit="%" tone="net" :delta="heapDetail" delta-tone="flat" />
      <KpiCard label="活跃线程" :value="m?.jvm?.threads ?? 0" unit="条" tone="in" :delta="`守护 ${m?.jvm?.daemonThreads ?? 0}`" delta-tone="flat" />
      <KpiCard label="DB 连接(活跃/上限)" :value="dbActiveTotal" tone="net" :delta="dbPoolDetail" delta-tone="flat" />
      <KpiCard label="Redis 延迟" :value="redisLatency" unit="ms" :tone="redisTone" :delta="redisText" :delta-tone="redisTone === 'alert' ? 'down' : 'up'" />
    </div>

    <div class="row">
      <!-- JVM 运行时 -->
      <div class="card">
        <div class="card__head"><h3>JVM 运行时</h3><span class="more mono">{{ uptime }}</span></div>
        <div class="card__body">
          <div class="meter">
            <div class="meter__top"><span>堆内存占用</span><b>{{ heapPct }}% · {{ heapUsed }} / {{ heapMax }}</b></div>
            <div class="meter__bar"><i :class="heapTone" :style="{ width: heapPct + '%' }"></i></div>
          </div>
          <div class="grid2">
            <div class="cell"><span class="k">已用 / 已分配</span><b>{{ heapUsed }} / {{ heapCommit }}</b></div>
            <div class="cell"><span class="k">CPU 核数</span><b>{{ m?.jvm?.cpuCores ?? '—' }}</b></div>
            <div class="cell"><span class="k">系统负载</span><b>{{ loadAvg }}</b></div>
            <div class="cell"><span class="k">运行时长</span><b>{{ uptime }}</b></div>
          </div>
        </div>
      </div>

      <!-- 数据库连接池 -->
      <div class="card">
        <div class="card__head"><h3>数据库连接池 · {{ m?.db?.type || '—' }}</h3><span class="more mono">{{ m?.db?.active ?? 0 }}/{{ m?.db?.max ?? 0 }}</span></div>
        <div class="card__body">
          <div class="meter">
            <div class="meter__top"><span>连接占用(活跃/上限)</span><b>{{ dbPct }}%</b></div>
            <div class="meter__bar"><i :class="dbTone" :style="{ width: dbPct + '%' }"></i></div>
          </div>
          <div class="grid2">
            <div class="cell"><span class="k">活跃 / 空闲</span><b>{{ m?.db?.active ?? 0 }} / {{ m?.db?.idle ?? 0 }}</b></div>
            <div class="cell"><span class="k">总连接 / 上限</span><b>{{ m?.db?.total ?? 0 }} / {{ m?.db?.max ?? 0 }}</b></div>
            <div class="cell"><span class="k">等待获取线程</span><b :class="{ warn: (m?.db?.waiting ?? 0) > 0 }">{{ m?.db?.waiting ?? 0 }}</b></div>
            <div class="cell"><span class="k">连接池类型</span><b>{{ m?.db?.type || '—' }}</b></div>
          </div>
        </div>
      </div>
    </div>

    <!-- HTTP 接口运行(Actuator + Micrometer:http.server.requests 自动采集) -->
    <div class="card">
      <div class="card__head"><h3>HTTP 接口运行</h3><span class="more mono">{{ httpTotal }} 次累计</span></div>
      <div class="card__body">
        <div class="meter">
          <div class="meter__top"><span>错误率(5xx)</span><b>{{ httpErrRate }}%</b></div>
          <div class="meter__bar"><i :class="httpTone" :style="{ width: Math.min(100, Number(httpErrRate)) + '%' }"></i></div>
        </div>
        <div class="grid2">
          <div class="cell"><span class="k">平均延迟</span><b>{{ httpAvg }} ms</b></div>
          <div class="cell"><span class="k">最大延迟</span><b>{{ httpMax }} ms</b></div>
          <div class="cell"><span class="k">总请求数</span><b>{{ httpTotal }}</b></div>
          <div class="cell"><span class="k">错误(5xx)</span><b :class="{ warn: httpErr > 0 }">{{ httpErr }}</b></div>
        </div>
      </div>
    </div>

    <!-- 成本与计费 -->
    <div class="sec"><span class="sec__label">成本与计费</span></div>

    <!-- 阿里云滑块验证码真实验真调用次数(计费依据);模式开关已上移到「运维控制」 -->
    <div class="card">
      <div class="card__head"><h3>阿里云滑块验证码</h3><span class="more mono">真实调用计数</span></div>
      <div class="card__body">
        <div class="grid2">
          <div class="cell"><span class="k">今日调用</span><b>{{ captchaToday }} 次</b></div>
          <div class="cell"><span class="k">本月调用</span><b>{{ captchaMonth }} 次</b></div>
          <div class="cell"><span class="k">累计调用</span><b>{{ captchaTotal }} 次</b></div>
          <div class="cell"><span class="k">计费说明</span><b class="note">阿里云智能验证(滑块)按"每次验真调用"计费;上列为真实调用次数(非登录次数估算)。模式切换见顶部「运维控制」。</b></div>
        </div>
      </div>
    </div>

    <!-- 本地知识库向量库占用(RAG 监控);问答开关已上移到「运维控制」。嵌入(检索)+ 作答(生成)是两个不同模型 -->
    <div class="card">
      <div class="card__head"><h3>本地知识库 · 向量库</h3><span class="more mono">作答 {{ chatModel }}</span></div>
      <div class="card__body">
        <div class="grid2">
          <div class="cell"><span class="k">已加载向量</span><b>{{ kbCount }}</b></div>
          <div class="cell"><span class="k">嵌入维度</span><b>{{ m?.kb?.dimension ?? '—' }}</b></div>
          <div class="cell"><span class="k">估算占用内存</span><b>{{ kbMem }}</b></div>
          <div class="cell"><span class="k">嵌入模型 · 检索</span><b class="note">{{ embModel }}</b></div>
          <div class="cell"><span class="k">作答模型 · 生成</span><b class="note">{{ chatModel }}</b></div>
          <div class="cell"><span class="k">说明</span><b class="note">嵌入把文本变向量用于检索(换它须清库重建);作答模型生成回答,可随时换。</b></div>
        </div>
      </div>
    </div>
    </div>

    <!-- 缓存监控(Redis · 架构演进阶段一) -->
    <div class="sec"><span class="sec__label">缓存监控</span></div>
    <CacheMonitor />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onActivated } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { todayLine } from '../../utils/format'
import { getSystemMetrics, setCaptchaMode, setKbEnabled, setGuestEnabled } from '../../api/system'
import { getCaptchaMode, getKbEnabled, getGuestEnabled } from '../../api/auth'
import KpiCard from '../../components/KpiCard.vue'
import CacheMonitor from '../../components/CacheMonitor.vue'

const today = todayLine()
const m = ref(null)
const loading = ref(false)

async function load() {
  loading.value = true
  try { m.value = await getSystemMetrics() } catch { /* 静默:403/网络由 request 统一提示 */ }
  finally { loading.value = false }
}
onMounted(() => { loadCaptchaMode(); loadKbEnabled(); loadGuestEnabled() })
// 删了手动"刷新"按钮:改为每次进入本页(含 keep-alive 重新激活)自动刷新指标
onActivated(() => { load() })

const fmtMem = (b) => {
  const n = Number(b) || 0
  if (n >= 1 << 30) return (n / (1 << 30)).toFixed(2) + ' GB'
  if (n >= 1 << 20) return (n / (1 << 20)).toFixed(0) + ' MB'
  if (n >= 1 << 10) return (n / (1 << 10)).toFixed(0) + ' KB'
  return n + ' B'
}
const fmtUptime = (ms) => {
  const s = Math.floor((Number(ms) || 0) / 1000)
  const d = Math.floor(s / 86400)
  const h = Math.floor((s % 86400) / 3600)
  const mi = Math.floor((s % 3600) / 60)
  if (d > 0) return `${d}天${h}小时`
  if (h > 0) return `${h}小时${mi}分`
  return `${mi}分`
}

const heapUsed = computed(() => fmtMem(m.value?.jvm?.heapUsed))
const heapCommit = computed(() => fmtMem(m.value?.jvm?.heapCommitted))
const heapMax = computed(() => fmtMem(m.value?.jvm?.heapMax))
const heapPct = computed(() => {
  const u = Number(m.value?.jvm?.heapUsed), x = Number(m.value?.jvm?.heapMax)
  return u && x ? Math.min(100, Math.round((u / x) * 100)) : 0
})
const heapDetail = computed(() => `${heapUsed.value} / ${heapMax.value}`)
const heapTone = computed(() => (heapPct.value >= 85 ? 'off' : heapPct.value >= 65 ? 'warn' : 'on'))

const uptime = computed(() => fmtUptime(m.value?.jvm?.uptimeMs))
const loadAvg = computed(() => {
  const l = m.value?.jvm?.systemLoad
  return (l != null && l >= 0) ? Number(l).toFixed(2) : '—'
})

const dbActiveTotal = computed(() => `${m.value?.db?.active ?? 0}/${m.value?.db?.max ?? 0}`)
const dbPoolDetail = computed(() => `空闲 ${m.value?.db?.idle ?? 0} · 等待 ${m.value?.db?.waiting ?? 0}`)
const dbPct = computed(() => {
  const a = Number(m.value?.db?.active), x = Number(m.value?.db?.max)
  return a != null && x ? Math.min(100, Math.round((a / x) * 100)) : 0
})
const dbTone = computed(() => (dbPct.value >= 85 ? 'off' : dbPct.value >= 65 ? 'warn' : 'on'))

const redisTone = computed(() => {
  if (!m.value?.redis?.online) return 'alert'
  return (m.value?.redis?.latencyMs ?? 0) > 100 ? 'net' : 'in'
})
const redisText = computed(() => (!m.value?.redis?.online ? '未连接' : (m.value?.redis?.latencyMs ?? 0) + 'ms'))
const redisLatency = computed(() => (!m.value?.redis?.online ? 0 : (m.value?.redis?.latencyMs ?? 0)))

/* —— HTTP 接口运行(Actuator + Micrometer:http.server.requests)—— */
const h = computed(() => m.value?.http || {})
const httpTotal = computed(() => (Number(h.value.totalRequests) || 0).toLocaleString('zh-CN'))
const httpAvg = computed(() => (Number(h.value.avgLatencyMs) || 0).toFixed(1))
const httpMax = computed(() => (Number(h.value.maxLatencyMs) || 0).toFixed(0))
const httpErr = computed(() => Number(h.value.errorCount) || 0)
const httpErrRate = computed(() => (Number(h.value.errorRate) || 0).toFixed(2))
const httpTone = computed(() => {
  const r = Number(h.value.errorRate) || 0
  return r >= 5 ? 'off' : r >= 1 ? 'warn' : 'on'
})

/* —— 阿里云滑块验证码真实验真调用次数(计费依据)—— */
const cap = computed(() => m.value?.captcha || {})
const captchaToday = computed(() => (Number(cap.value.today) || 0).toLocaleString('zh-CN'))
const captchaMonth = computed(() => (Number(cap.value.month) || 0).toLocaleString('zh-CN'))
const captchaTotal = computed(() => (Number(cap.value.total) || 0).toLocaleString('zh-CN'))

/* —— 滑块验证模式开关(管理员,防费用超限)—— */
const captchaMode = ref('enabled')
const cfgSaving = ref(false)
async function loadCaptchaMode() {
  try { captchaMode.value = (await getCaptchaMode()).mode || 'enabled' } catch { /* 静默 */ }
}
async function onCaptchaModeChange(v) {
  const labels = { enabled: '启用 · 滑块验证', pass: '关闭 · 放行', lock: '关闭 · 锁定(仅管理员)' }
  if (v === 'lock') {
    try {
      await ElMessageBox.confirm(
        '锁定后普通账号(药企/机构/医师)无法登录,但管理员仍可登录恢复。确认切换到「锁定」?',
        '危险操作', { type: 'warning', confirmButtonText: '确认锁定', cancelButtonText: '取消' }
      )
    } catch {
      await loadCaptchaMode()   // 取消 → 回退到服务端真实模式
      return
    }
  }
  cfgSaving.value = true
  try {
    await setCaptchaMode(v)
    ElMessage.success(`已切换:${labels[v] || v}`)
  } catch {
    await loadCaptchaMode()   // 失败 → 回退(错误由拦截器提示)
  } finally {
    cfgSaving.value = false
  }
}

/* —— 本地知识库监控 —— */
const kbCount = computed(() => (Number(m.value?.kb?.vectorCount) || 0).toLocaleString('zh-CN'))
const kbMem = computed(() => fmtMem(m.value?.kb?.estMemBytes))
// 模型名展示:去掉 provider 的 "siliconflow-" 前缀,只留模型本体(BAAI/bge-m3、Qwen/Qwen3-30B-A3B-Instruct-2507)
const cleanModel = (s) => (s || '—').replace(/^siliconflow-/, '')
const embModel = computed(() => cleanModel(m.value?.kb?.embeddingModel))
const chatModel = computed(() => cleanModel(m.value?.kb?.chatModel))

/* —— 知识库问答开关(管理员,防计费超支)—— */
const kbEnabled = ref(true)
async function loadKbEnabled() {
  try { kbEnabled.value = (await getKbEnabled()).enabled !== false } catch { /* 静默 */ }
}
async function onKbEnabledChange(v) {
  cfgSaving.value = true
  try {
    await setKbEnabled(v)
    ElMessage.success(v ? '已开启知识库问答' : '已关闭知识库问答(精灵将提示已被禁用)')
  } catch {
    await loadKbEnabled()   // 失败 → 回退
  } finally {
    cfgSaving.value = false
  }
}

/* —— 游客体验入口开关(管理员)—— */
const guestEnabled = ref(false)
async function loadGuestEnabled() {
  try { guestEnabled.value = (await getGuestEnabled()).enabled === true } catch { /* 静默 */ }
}
async function onGuestEnabledChange(v) {
  cfgSaving.value = true
  try {
    await setGuestEnabled(v)
    ElMessage.success(v ? '已开放游客体验入口' : '已关闭游客体验入口')
  } catch {
    await loadGuestEnabled()   // 失败 → 回退
  } finally {
    cfgSaving.value = false
  }
}
</script>

<style scoped>
.sys { display: flex; flex-direction: column; gap: 18px; }
.pagehead { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.pagehead h1 { font-family: var(--font-d); font-weight: 800; font-size: 24px; letter-spacing: -0.02em; }
.pagehead .sub { font-family: var(--font-m); font-size: 12.5px; color: var(--ink-3); margin-top: 5px; }
.kpis { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; }
.row { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; }
.card { background: var(--surface); border: 1px solid var(--line); border-radius: var(--rad); box-shadow: var(--sh-sm); }
.card__head { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 15px 20px; border-bottom: 1px solid var(--line-2); }
.card__head h3 { font-family: var(--font-d); font-weight: 700; font-size: 15px; }
.card__head .more { font-family: var(--font-m); font-size: 11px; color: var(--ink-3); }
.card__body { padding: 18px 20px; }
.meter { margin-bottom: 16px; }
.meter__top { display: flex; align-items: baseline; justify-content: space-between; font-family: var(--font-m); font-size: 12px; color: var(--ink-3); margin-bottom: 8px; }
.meter__top b { font-family: var(--font-d); font-weight: 700; color: var(--ink); font-size: 13px; }
.meter__bar { height: 9px; border-radius: 5px; background: var(--line-2); overflow: hidden; }
.meter__bar i { display: block; height: 100%; border-radius: 5px; transition: width 0.5s ease; }
.meter__bar i.on { background: var(--green); }
.meter__bar i.warn { background: var(--amber); }
.meter__bar i.off { background: var(--red); }
.grid2 { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; }
.cell { background: var(--line-2); border-radius: 10px; padding: 11px 12px; }
.cell .k { font-family: var(--font-m); font-size: 10.5px; color: var(--ink-3); display: block; }
.cell b { font-family: var(--font-d); font-weight: 700; font-size: 15px; color: var(--ink); display: block; margin-top: 4px; }
.cell b.warn { color: var(--red); }
.cell b.note { font-size: 12px; font-weight: 500; color: var(--ink-2); line-height: 1.5; }
.mono { font-family: var(--font-m); }
/* 滑块验证模式开关 */
.cfg { margin-top: 16px; padding-top: 16px; border-top: 1px dashed var(--line-2); }
.cfg__row { display: flex; align-items: center; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.cfg__t { font-family: var(--font-d); font-weight: 700; font-size: 14px; color: var(--ink); }
.cfg__d { font-family: var(--font-m); font-size: 12px; color: var(--ink-3); margin-top: 4px; line-height: 1.5; }
.cfg__warn { margin-top: 12px; font-size: 12.5px; color: var(--red); background: rgba(220, 42, 69, 0.07); border: 1px solid rgba(220, 42, 69, 0.2); border-radius: 8px; padding: 9px 12px; line-height: 1.6; }
.cfg__warn code { font-family: var(--font-m); background: rgba(220, 42, 69, 0.1); padding: 1px 5px; border-radius: 4px; }
.kb-note { margin-top: 14px; font-family: var(--font-m); font-size: 11.5px; line-height: 1.6; color: var(--ink-3); background: var(--line-2); border-radius: 10px; padding: 10px 12px; }

/* 分组小标题:把一长串监控卡片按"运行健康 / 成本与计费 / 缓存监控"分块,减弱"东西太多"的压迫感 */
.sec { display: flex; align-items: center; margin: 6px 0 -4px; }
.sec__label { font-family: var(--font-d); font-weight: 700; font-size: 13px; color: var(--ink-2); letter-spacing: 0.02em; }
.sec__label::before { content: ''; display: inline-block; width: 3px; height: 13px; border-radius: 2px; background: var(--indigo); margin-right: 8px; vertical-align: -2px; }
/* 运维控制卡:靛蓝细边 + 头部淡背景,强调"这是可操作区、按钮统一在此" */
.ctrl { border-color: #c7d2fe; }
.ctrl .card__head { background: linear-gradient(90deg, var(--indigo-soft), transparent); }
.ctrl__body { display: flex; flex-direction: column; gap: 18px; }
/* v-loading 包裹的监控区:复用与 .sys 一致的列间距,避免卡片贴在一起 */
.metrics { display: flex; flex-direction: column; gap: 18px; }
@media (max-width: 1040px) { .kpis { grid-template-columns: repeat(2, 1fr); } .row { grid-template-columns: 1fr; } }
</style>
