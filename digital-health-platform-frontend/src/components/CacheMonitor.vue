<template>
  <div class="card cm">
    <div class="card__head">
      <h3>缓存监控 · Redis</h3>
      <div class="cm__head-r">
        <el-button size="small" type="danger" plain :loading="clearing" @click="onClear">清空缓存</el-button>
        <span class="cm__rate" :class="state"><i></i>{{ statusText }}</span>
      </div>
    </div>

    <div class="cm__body">
      <!-- 左:状态 + 命中率 + 键数 -->
      <div class="cm__metrics">
        <div class="cm__hero" :class="state">
          <span class="cm__dot"></span>
          <div>
            <div class="cm__hero-t">{{ online ? '缓存层在线' : 'Redis 未连接' }}</div>
            <div class="cm__hero-s">{{ online ? `已缓存 ${dbsize} 条业务键` : '请确认 Redis 服务已启动' }}</div>
          </div>
        </div>

        <!-- 命中率:应用内计数(自启动/上次清空累计),重启或清空归零 -->
        <div class="cm__rate-card">
          <div class="cm__rate-top">
            <span class="cm__rate-k">缓存命中率</span>
            <b class="cm__rate-v" :class="rateTone">{{ rateText }}</b>
          </div>
          <div class="cm__bar"><i :class="rateTone" :style="{ width: barWidth + '%' }"></i></div>
          <div class="cm__rate-sub">
            <span>命中 <b>{{ hits }}</b></span>
            <span>未命中 <b>{{ misses }}</b></span>
            <span v-if="sinceStr" class="cm__rate-since">{{ sinceStr }}</span>
          </div>
        </div>

        <div class="cm__nums">
          <div class="cm__num"><span class="k">已缓存键数</span><b>{{ dbsize }}</b></div>
          <div class="cm__num"><span class="k">预热键</span><b class="g">{{ prewarmCount }}</b></div>
        </div>

        <div class="cm__note">
          启动时预热 <code>materials:page</code> / <code>companies:active</code> 两条全局参照数据,首请求即命中。
          命中率按应用内存统计(自启动 / 上次清空累计),<b>重启应用或清空缓存即归零</b>。
        </div>
      </div>

      <!-- 右:已缓存条目明细 -->
      <div class="cm__entries">
        <div class="cm__ent-head"><span>已缓存条目</span><span class="cm__cnt">{{ keyTotal }}</span></div>
        <div v-if="keyTotal > keys.length" class="cm__ent-cap">共 {{ keyTotal }} 条,仅显示前 {{ keys.length }} 条</div>
        <div v-if="!online" class="cm__empty">Redis 未连接 · 无法读取缓存明细</div>
        <div v-else-if="!keys.length" class="cm__empty">暂无缓存 · 访问「必备材料」页或触发药企下拉后出现条目</div>
        <div v-else class="cm__list">
          <div v-for="(k, i) in pagedKeys" :key="i" class="cm__row">
            <div class="cm__row-main">
              <span class="cm__row-label">{{ labelOf(k.key) }}</span>
              <span class="cm__row-key">{{ k.key }}</span>
            </div>
            <span class="cm__ttl">已缓存</span>
          </div>
        </div>
        <!-- 已缓存条目量大时(后端最多回填 200 条)分页浏览,每页 5 条 -->
        <el-pagination
          v-if="keys.length > PAGE_SIZE"
          small
          background
          layout="prev, pager, next"
          :total="keys.length"
          :page-size="PAGE_SIZE"
          :pager-count="5"
          v-model:current-page="page"
          class="cm__pager"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onActivated, onDeactivated, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cacheStats, clearCache } from '../api/cache'

const online = ref(false)
const dbsize = ref(0)
const keys = ref([])
const keyTotal = ref(0)   // huiyi:* 真实匹配总数(后端封顶 200 返回 keys,keyTotal 才是准数)
const hits = ref(0)
const misses = ref(0)
const since = ref('')
const clearing = ref(false)
let timer = null

// 已缓存条目分页:每页 5 条(条目可达上百,一次铺满难扫);轮询刷新时若当前页越界则回退到末页,不打断浏览
const PAGE_SIZE = 5
const page = ref(1)
const pagedKeys = computed(() => keys.value.slice((page.value - 1) * PAGE_SIZE, page.value * PAGE_SIZE))
watch(() => keys.value.length, (len) => {
  const maxPage = Math.max(1, Math.ceil(len / PAGE_SIZE))
  if (page.value > maxPage) page.value = maxPage
})

const state = computed(() => (!online.value ? 'off' : dbsize.value > 0 ? 'on' : 'idle'))
const statusText = computed(() => (!online.value ? '未连接' : dbsize.value > 0 ? '运行中' : '待缓存'))

// 命中率:应用进程内统计(hits/(hits+misses)),无数据时显示"—",并给一条极细底线表示"待统计"
const total = computed(() => hits.value + misses.value)
const hitRate = computed(() => (total.value === 0 ? 0 : Math.round((hits.value * 100) / total.value)))
const rateText = computed(() => (total.value === 0 ? '—' : hitRate.value + '%'))
const rateTone = computed(() => {
  if (total.value === 0) return 'idle'
  if (hitRate.value >= 70) return 'on'
  if (hitRate.value >= 40) return 'warn'
  return 'off'
})
const barWidth = computed(() => (total.value === 0 ? 4 : hitRate.value))
const sinceStr = computed(() => {
  if (!since.value) return ''
  const t = new Date(since.value)
  if (isNaN(t.getTime())) return ''
  const p = (n) => String(n).padStart(2, '0')
  return `自 ${p(t.getHours())}:${p(t.getMinutes())} 统计`
})

// 预热键 = 启动时 CacheWarmer 主动填的两条全局参照数据(必备材料分页 / 药企列表)
const prewarmCount = computed(() => keys.value.filter((k) => /materials:page|companies:active/.test(k.key)).length)

async function load() {
  const safe = (p) => p.then((v) => v).catch(() => null)
  const r = await safe(cacheStats())
  if (r) {
    online.value = !!r.online
    dbsize.value = r.dbsize ?? 0
    keys.value = r.keys ?? []
    keyTotal.value = r.keyTotal ?? r.keys?.length ?? 0
    hits.value = r.hits ?? 0
    misses.value = r.misses ?? 0
    since.value = r.since ?? ''
  } else {
    online.value = false
  }
}

async function onClear() {
  await ElMessageBox.confirm('确认清空全部业务缓存?命中率计数会同步归零,下次访问将回查数据库(材料/药企等会重新加载)。', '清空缓存', { type: 'warning' })
  clearing.value = true
  try {
    await clearCache()
    ElMessage.success('已清空缓存,命中率已重置')
    await load()
  } finally {
    clearing.value = false
  }
}

function labelOf(key) {
  if (!key) return ''
  if (key.includes('materials:page')) return '必备材料 · 分页查询'
  if (key.includes('companies:active')) return '药企 · 列表'
  return key.replace(/^huiyi:/, '')
}
// keep-alive 下用 activated/deactivated:切回本页立即刷新、切走停轮询(避免后台空转 + 回来仍看旧数)
onActivated(() => { load(); timer = setInterval(load, 5000) })
onDeactivated(() => clearInterval(timer))
</script>

<style scoped>
.card { background: var(--surface); border: 1px solid var(--line); border-radius: var(--rad); box-shadow: var(--sh-sm); }
.card__head { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 15px 20px; border-bottom: 1px solid var(--line-2); }
.card__head h3 { font-family: var(--font-d); font-weight: 700; font-size: 15px; }
.cm__head-r { display: flex; align-items: center; gap: 12px; }
.cm__rate { display: inline-flex; align-items: center; gap: 7px; font-family: var(--font-m); font-size: 11.5px; font-weight: 600; }
.cm__rate i { width: 7px; height: 7px; border-radius: 50%; flex: none; }
.cm__rate.on { color: var(--green); }
.cm__rate.on i { background: var(--green); animation: cmbeat 2s infinite; }
.cm__rate.idle { color: var(--amber); }
.cm__rate.idle i { background: var(--amber); }
.cm__rate.off { color: var(--red); }
.cm__rate.off i { background: var(--red); }

.cm__body { display: grid; grid-template-columns: 1.05fr 1fr; gap: 24px; padding: 18px 20px; }

.cm__hero { display: flex; align-items: center; gap: 12px; }
.cm__dot { width: 13px; height: 13px; border-radius: 50%; flex: none; }
.cm__hero.on .cm__dot { background: var(--green); box-shadow: 0 0 0 4px rgba(14, 156, 143, 0.16); animation: cmbeat 2s infinite; }
.cm__hero.idle .cm__dot { background: var(--amber); }
.cm__hero.off .cm__dot { background: var(--red); }
.cm__hero-t { font-family: var(--font-d); font-weight: 700; font-size: 16px; }
.cm__hero-s { font-family: var(--font-m); font-size: 11.5px; color: var(--ink-3); margin-top: 3px; }

/* 命中率卡 */
.cm__rate-card { margin-top: 18px; background: var(--line-2); border-radius: 10px; padding: 13px 14px; }
.cm__rate-top { display: flex; align-items: baseline; justify-content: space-between; }
.cm__rate-k { font-family: var(--font-m); font-size: 11px; color: var(--ink-3); }
.cm__rate-v { font-family: var(--font-d); font-weight: 800; font-size: 26px; letter-spacing: -0.02em; }
.cm__rate-v.on { color: var(--green); }
.cm__rate-v.warn { color: var(--amber); }
.cm__rate-v.off { color: var(--red); }
.cm__rate-v.idle { color: var(--ink-3); }
.cm__bar { height: 8px; border-radius: 5px; background: var(--surface); margin: 11px 0 10px; overflow: hidden; }
.cm__bar i { display: block; height: 100%; border-radius: 5px; transition: width 0.5s ease; }
.cm__bar i.on { background: var(--green); }
.cm__bar i.warn { background: var(--amber); }
.cm__bar i.off { background: var(--red); }
.cm__bar i.idle { background: var(--line); }
.cm__rate-sub { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; font-family: var(--font-m); font-size: 11px; color: var(--ink-3); }
.cm__rate-sub b { color: var(--ink-2); font-weight: 700; }
.cm__rate-since { margin-left: auto; }

.cm__nums { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; margin-top: 18px; }
.cm__num { background: var(--line-2); border-radius: 10px; padding: 11px 12px; }
.cm__num .k { font-family: var(--font-m); font-size: 10.5px; color: var(--ink-3); }
.cm__num b { font-family: var(--font-d); font-weight: 800; font-size: 22px; letter-spacing: -0.02em; display: block; margin-top: 5px; }
.cm__num b.g { color: var(--green); }

.cm__note { margin-top: 16px; font-family: var(--font-m); font-size: 11px; line-height: 1.6; color: var(--ink-3); background: var(--line-2); border-radius: 10px; padding: 10px 12px; }
.cm__note b { color: var(--ink-2); }
.cm__note code { font-family: var(--font-m); font-size: 10.5px; color: var(--indigo); background: var(--indigo-soft); border-radius: 4px; padding: 1px 5px; }

.cm__ent-head { display: flex; align-items: center; justify-content: space-between; font-size: 12px; color: var(--ink-2); margin-bottom: 10px; }
.cm__cnt { font-family: var(--font-m); background: var(--indigo-soft); color: var(--indigo); border-radius: 999px; padding: 2px 9px; font-size: 11px; font-weight: 600; }
.cm__ent-cap { font-family: var(--font-m); font-size: 11px; color: var(--ink-3); margin: -4px 0 8px; }
.cm__empty { font-family: var(--font-m); font-size: 12px; color: var(--ink-3); padding: 26px 0; text-align: center; border: 1px dashed var(--line); border-radius: 10px; }
.cm__list { display: flex; flex-direction: column; gap: 2px; }
.cm__pager { margin-top: 12px; justify-content: center; display: flex; }
.cm__row { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 10px 0; border-bottom: 1px solid var(--line-2); }
.cm__row:last-child { border-bottom: none; }
.cm__row-main { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
.cm__row-label { font-size: 13px; font-weight: 600; color: var(--ink); }
.cm__row-key { font-family: var(--font-m); font-size: 10.5px; color: var(--ink-3); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.cm__ttl { font-family: var(--font-m); font-size: 11.5px; font-weight: 600; color: var(--indigo); background: var(--indigo-soft); border-radius: 999px; padding: 4px 11px; flex: none; }

@keyframes cmbeat { 0%, 100% { transform: scale(1); } 50% { transform: scale(1.3); } }

@media (max-width: 1040px) { .cm__body { grid-template-columns: 1fr; } }
</style>
