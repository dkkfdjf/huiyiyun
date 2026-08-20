<template>
  <div class="dash">
    <div class="pagehead">
      <div>
        <h1>工作台概览</h1>
        <div class="sub">{{ today }}</div>
      </div>
      <div class="acct">登录名 <span class="acct__v">{{ user.username || '—' }}</span></div>
    </div>

    <template v-if="data">
      <!-- KPI -->
      <div class="kpis">
        <KpiCard
          v-for="(k, i) in kpis"
          :key="i"
          :label="k.label"
          :value="k.value"
          :unit="k.unit"
          :tone="k.tone"
          :delta="k.delta"
          :delta-tone="k.deltaTone"
          :spark="k.spark"
        />
      </div>

      <!-- 趋势 + 流水 -->
      <div class="row c73">
        <ChartCard
          title="销售与出库趋势"
          more="近 12 周"
          :legend="[
            { label: '销售额（元）', color: '#4338CA' },
            { label: '出库量（件）', color: '#D97706' }
          ]"
          :option="trendOption"
          :height="250"
        />
        <OpsFeed :rows="feedRows" more="最近" />
      </div>

      <!-- 库存预警 + 职称分布 -->
      <div class="row c11">
        <StockAlertTable :rows="alertRows" more="查看全部 ›" @more="goStocks" />
        <ChartCard
          title="医师职称分布"
          :legend="titleLegend"
          :option="titleOption"
          :height="250"
        />
      </div>

      <!-- 近期通知(当前管理员通知流,与铃铛同源;不再混入审计日志,审计日志仍在 /admin/logs) -->
      <div class="card ncard">
        <div class="ncard__head">
          <h3>近期通知</h3>
          <span class="mono ncard__sub">站内通知</span>
        </div>
        <NotificationList :items="recentNotif" />
      </div>
    </template>

    <el-empty v-else-if="error" :description="errMsg" />
    <div v-else class="loading">加载中…</div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { getDashboard } from '../../api/dashboard'
import { pageNotifications } from '../../api/notification'
import { useAlertStore } from '../../stores/alert'
import { useUserStore } from '../../stores/user'
import { todayLine, fmtDateTime } from '../../utils/format'
import KpiCard from '../../components/KpiCard.vue'
import ChartCard from '../../components/ChartCard.vue'
import OpsFeed from '../../components/OpsFeed.vue'
import StockAlertTable from '../../components/StockAlertTable.vue'
import NotificationList from '../../components/NotificationList.vue'

const alerts = useAlertStore()
const user = useUserStore()
const data = ref(null)
const recentNotif = ref([])
const error = ref(false)
const errMsg = ref('加载失败')
const today = todayLine()
const router = useRouter()
const goStocks = () => router.push('/admin/stocks')

async function loadDashboard() {
  try {
    data.value = await getDashboard()
    alerts.set(data.value.stockAlerts?.length || 0)
  } catch (e) {
    error.value = true
    errMsg.value = e?.message || '无权限或加载失败'
  }
  // 近期通知独立加载(当前管理员通知流):静默失败不影响主看板
  try { const r = await pageNotifications({ pageNum: 1, pageSize: 6 }); recentNotif.value = r?.records || [] } catch (_) { recentNotif.value = [] }
}

// keep-alive 切回时刷新;首次由 onMounted 加载,跳过避免双重请求
let firstActivation = true
onMounted(() => { loadDashboard() })
onActivated(() => {
  if (firstActivation) { firstActivation = false; return }
  loadDashboard()
})

const totals = computed(() => data.value?.totals || {})
const trend = computed(() => data.value?.trend12w || [])
const stockAlerts = computed(() => data.value?.stockAlerts || [])
const opsFeed = computed(() => data.value?.opsFeed || [])
const titleDist = computed(() => data.value?.titleDist || [])

const kpis = computed(() => [
  {
    label: '合作医疗机构', value: totals.value.institutionCount || 0, unit: '家', tone: 'net',
    delta: '覆盖全国', deltaTone: 'flat'
  },
  {
    label: '在管药品', value: totals.value.drugCount || 0, unit: '种',
    delta: '按规格剂型归档', deltaTone: 'flat'
  },
  {
    label: '合作药企', value: totals.value.companyCount || 0, unit: '家',
    delta: '已审核入驻', deltaTone: 'flat'
  },
  {
    label: '销售网点', value: totals.value.locationCount || 0, unit: '个',
    delta: '地图标注', deltaTone: 'flat'
  },
  {
    label: '库存预警', value: stockAlerts.value.length, unit: '条', tone: 'alert',
    delta: '需补货', deltaTone: 'down'
  }
])

const trendOption = computed(() => {
  const weeks = trend.value.map((_, i) => 'W' + (i + 1))
  const sales = trend.value.map((p) => p.salesAmount)
  const out = trend.value.map((p) => p.outboundQty)
  return {
    grid: { left: 48, right: 52, top: 18, bottom: 28 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: weeks,
      boundaryGap: false,
      axisLine: { lineStyle: { color: '#E2E7F2' } },
      axisTick: { show: false },
      axisLabel: { color: '#8B93A6', fontFamily: 'DM Mono', fontSize: 10, interval: 1 }
    },
    yAxis: [
      {
        type: 'value', name: '元', nameTextStyle: { color: '#B8BECC', fontSize: 10 },
        splitLine: { lineStyle: { color: '#EDF0F7' } },
        axisLabel: { color: '#8B93A6', fontFamily: 'DM Mono', fontSize: 10 }
      },
      {
        type: 'value', name: '件', nameTextStyle: { color: '#B8BECC', fontSize: 10 },
        splitLine: { show: false },
        axisLabel: { color: '#8B93A6', fontFamily: 'DM Mono', fontSize: 10 }
      }
    ],
    series: [
      {
        name: '销售额（元）', type: 'line', smooth: true, data: sales, yAxisIndex: 0,
        symbol: 'circle', symbolSize: 6,
        lineStyle: { color: '#4338CA', width: 2.6 },
        itemStyle: { color: '#4338CA' },
        areaStyle: {
          color: {
            type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(99,102,241,0.28)' },
              { offset: 1, color: 'rgba(99,102,241,0)' }
            ]
          }
        }
      },
      {
        name: '出库量（件）', type: 'line', smooth: true, data: out, yAxisIndex: 1,
        symbol: 'none',
        lineStyle: { color: '#D97706', width: 1.8, type: 'dashed', opacity: 0.85 },
        itemStyle: { color: '#D97706' }
      }
    ]
  }
})

const feedRows = computed(() =>
  opsFeed.value.map((r) => ({
    time: fmtDateTime(r.time) || '--:--',
    event: r.event,
    delta: r.delta,
    type: r.type
  }))
)

const alertRows = computed(() => stockAlerts.value)

/* —— 医师职称分布:甜甜圈(职称是 part-of-whole 占比,环图比横向填充条诚实)—— */
const PIE_COLORS = ['#4338ca', '#6366f1', '#0ea5e9', '#10b981']
const titleRows = computed(() => (data.value?.titleDist || []).map((d) => ({ name: d.name, value: d.value })).filter((d) => d.value > 0))
const titleTotal = computed(() => titleRows.value.reduce((s, d) => s + d.value, 0))
const titleLegend = computed(() => titleRows.value.map((d, i) => ({ label: d.name, color: PIE_COLORS[i % PIE_COLORS.length] })))
const titleOption = computed(() => {
  const rows = titleRows.value
  return {
    color: PIE_COLORS,
    tooltip: { trigger: 'item', formatter: '{b}: {c} 人 ({d}%)' },
    title: titleTotal.value
      ? { text: String(titleTotal.value), subtext: '在职医师', left: '50%', top: '41%', textAlign: 'center', textStyle: { fontSize: 22, fontWeight: 700, color: '#161b2e', fontFamily: 'DM Mono' }, subtextStyle: { fontSize: 11, color: '#8B93A6' } }
      : null,
    series: [{
      type: 'pie', radius: ['54%', '78%'], center: ['50%', '50%'], avoidLabelOverlap: true,
      itemStyle: { borderColor: '#fff', borderWidth: 2, borderRadius: 4 },
      label: { show: true, formatter: '{b}\n{d}%', fontSize: 11, color: '#525c73' },
      labelLine: { length: 8, length2: 8 },
      data: rows.map((d) => ({ name: d.name, value: d.value }))
    }]
  }
})
</script>

<style scoped>
.pagehead { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.pagehead h1 { font-family: var(--font-d); font-weight: 800; font-size: 24px; letter-spacing: -0.02em; }
.pagehead .sub { font-family: var(--font-m); font-size: 12.5px; color: var(--ink-3); margin-top: 5px; }
.acct { font-family: var(--font-m); font-size: 12.5px; color: var(--ink-3); background: var(--surface); border: 1px solid var(--line); border-radius: 999px; padding: 6px 14px; align-self: center; white-space: nowrap; }
.acct__v { color: var(--indigo); font-weight: 600; }

.kpis { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 20px; }
.row { display: grid; gap: 18px; }
.row.c73 { grid-template-columns: 1.7fr 1fr; }
.row.c11 { grid-template-columns: 1fr 1fr; }

.loading { padding: 60px 0; text-align: center; color: var(--ink-3); font-family: var(--font-m); }

/* 近期通知卡:与各 ChartCard 同语言(白底/边框/阴影/圆角) */
.ncard { background: var(--surface); border: 1px solid var(--line); border-radius: var(--rad); box-shadow: var(--sh-sm); overflow: hidden; }
.ncard__head { display: flex; align-items: baseline; gap: 10px; padding: 16px 18px; border-bottom: 1px solid var(--line-2); }
.ncard__head h3 { font-size: 15px; font-weight: 700; color: var(--ink); }
.ncard__sub { font-size: 11px; color: var(--ink-3); }

@media (max-width: 1180px) { .kpis { grid-template-columns: repeat(3, 1fr); } }
@media (max-width: 1040px) {
  .row.c73, .row.c11 { grid-template-columns: 1fr; }
}
@media (max-width: 680px) {
  .kpis { grid-template-columns: repeat(2, 1fr); }
}
</style>
