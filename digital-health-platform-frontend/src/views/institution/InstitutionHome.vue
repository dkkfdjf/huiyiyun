<template>
  <div class="home" v-loading="loading">
    <div class="pagehead">
      <div>
        <span class="kick"><i></i>医疗机构工作台</span>
        <h1>{{ home?.institutionName || '本院' }}</h1>
        <div class="sub">本院医师资源与科室分布概览</div>
      </div>
    </div>

    <!-- KPI -->
    <div class="kpis">
      <KpiCard label="本院医师" :value="home?.doctorCount ?? 0" unit="人" tone="in" delta="在册执业" delta-tone="flat" />
      <KpiCard label="科室数量" :value="home?.departmentCount ?? 0" unit="个" tone="net" delta="按学科归类" delta-tone="flat" />
      <KpiCard label="可售药品" :value="drugCount" unit="种" tone="out" delta="医药公司供应" delta-tone="flat" />
    </div>

    <!-- 本院信息 + 科室分布(等宽 c11,与医师端"我的信息|必备材料"同构) -->
    <div class="row c11">
      <div class="panel">
        <div class="panel__hd"><h3>本院信息</h3></div>
        <div class="info">
          <div class="info__r"><span class="info__k">机构名称</span><span class="info__v">{{ home?.institutionName || '—' }}</span></div>
          <div class="info__r"><span class="info__k">登录名</span><span class="info__v">{{ user.username || '—' }}</span></div>
          <div class="info__r"><span class="info__k">地址</span><span class="info__v">{{ home?.address || '—' }}</span></div>
          <div class="info__r"><span class="info__k">所属城市</span><span class="info__v">{{ home?.cityName || '—' }}</span></div>
          <div class="info__r"><span class="info__k">联系人</span><span class="info__v">{{ home?.contactPerson || '—' }}</span></div>
        </div>
      </div>
      <ChartCard
        title="科室人员分布"
        :legend="deptLegend"
        :option="deptOption"
        :height="250"
      />
    </div>

    <div class="row c11">
      <!-- 最新政策公告 -->
      <div class="panel">
        <div class="panel__hd">
          <h3>最新政策公告</h3>
          <router-link to="/institution/policies" class="more">查看全部 →</router-link>
        </div>
        <div class="bullets">
          <div v-for="p in policies" :key="p.id" class="bullet">
            <span class="bullet__type" :class="'type-' + p.policyType">{{ policyTypeLabel(p.policyType) }}</span>
            <span class="bullet__title">{{ p.title }}</span>
            <span class="bullet__company">{{ p.companyName || '—' }}</span>
            <span class="bullet__date">{{ formatDate(p.effectiveDate) }}</span>
          </div>
          <div v-if="!policies.length" class="empty">暂无政策公告</div>
        </div>
      </div>

      <!-- 快捷入口 -->
      <div class="panel">
        <div class="panel__hd"><h3>快捷入口</h3></div>
        <div class="acts">
          <router-link v-for="a in actions" :key="a.to" class="act" :to="a.to" :style="{ '--ac': a.color }">
            <span class="act__ic" v-html="a.svg" />
            <div class="act__t">{{ a.t }}</div>
            <div class="act__d">{{ a.d }}</div>
            <i class="act__arr">→</i>
          </router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onActivated } from 'vue'
import { institutionHome, pageInstitutionDrugs } from '../../api/institution'
import { pagePolicies } from '../../api/policy'
import { useUserStore } from '../../stores/user'
import KpiCard from '../../components/KpiCard.vue'
import ChartCard from '../../components/ChartCard.vue'

const user = useUserStore()
const loading = ref(false)
const home = ref(null)
const policies = ref([])
const drugCount = ref(0)

// 科室人员分布圆环(与管理员端"职称分布"同款):前5科室 + "其他",
// 中心 echarts.title 显总医师数,扇区 label 显"科室 占比%",顶部用 ChartCard 图例。
const PIE_COLORS = ['#4338ca', '#6366f1', '#0ea5e9', '#10b981']
const deptRows = computed(() => {
  const rows = (home.value?.departmentStats || [])
    .map((s) => ({ name: s.departmentName || '未命名', value: s.count || 0 }))
    .filter((d) => d.value > 0)
    .sort((a, b) => b.value - a.value)
  const top = rows.slice(0, 5)
  const restSum = rows.slice(5).reduce((s, r) => s + r.value, 0)
  if (restSum > 0) top.push({ name: '其他', value: restSum })
  return top
})
const deptTotal = computed(() => deptRows.value.reduce((s, d) => s + d.value, 0))
const deptLegend = computed(() => deptRows.value.map((d, i) => ({ label: d.name, color: PIE_COLORS[i % PIE_COLORS.length] })))
const deptOption = computed(() => ({
  color: PIE_COLORS,
  tooltip: { trigger: 'item', formatter: '{b}: {c} 人 ({d}%)' },
  title: deptTotal.value
    ? { text: String(deptTotal.value), subtext: '总医师', left: '50%', top: '41%', textAlign: 'center',
        textStyle: { fontSize: 22, fontWeight: 700, color: '#161b2e', fontFamily: 'DM Mono' },
        subtextStyle: { fontSize: 11, color: '#8B93A6' } }
    : null,
  series: [{
    type: 'pie', radius: ['54%', '78%'], center: ['50%', '50%'], avoidLabelOverlap: true,
    itemStyle: { borderColor: '#fff', borderWidth: 2, borderRadius: 4 },
    label: { show: true, formatter: '{b}\n{d}%', fontSize: 11, color: '#525c73' },
    labelLine: { length: 8, length2: 8 },
    data: deptRows.value.length
      ? deptRows.value.map((d) => ({ name: d.name, value: d.value }))
      : [{ name: '暂无数据', value: 1, itemStyle: { color: '#e2e8f0' } }]
  }]
}))

const formatDate = (d) => {
  if (!d) return '—'
  return String(d).slice(0, 10)
}

const policyTypeLabel = (type) => {
  const labels = { 1: '医保', 2: '药企', 3: '价格' }
  return labels[type] || '其他'
}

const SVG = {
  doctor: '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="9" cy="8" r="3.2"/><path d="M3.5 20a5.5 5.5 0 0 1 11 0"/><path d="M16 11a3 3 0 0 0 0-6"/><path d="M17 20a5.5 5.5 0 0 0-3-5"/></svg>',
  policy: '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M6 3h8l4 4v14H6z"/><path d="M14 3v4h4"/><path d="M9 12h6M9 16h6"/></svg>',
  box: '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 3 21 8v8l-9 5-9-5V8z"/><path d="M3 8l9 5 9-5M12 13v8"/></svg>',
  pill: '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M10.5 3.5 20.5 13.5a5 5 0 0 1-7 7L3.5 10.5a3.5 3.5 0 0 1 5-5z"/></svg>'
}
const actions = [
  { to: '/institution/doctors', t: '医师管理', d: '本院医师档案 · 重置密码', color: '#4338ca', svg: SVG.doctor },
  { to: '/institution/policies', t: '政策公告', d: '查看医药公司政策', color: '#d97706', svg: SVG.policy },
  { to: '/institution/materials', t: '必备材料', d: '运营物资 · 合规文档', color: '#0e9c8f', svg: SVG.box },
  { to: '/institution/drugs', t: '可售药品', d: '查看供应药品目录', color: '#7c3aed', svg: SVG.pill }
]

async function loadHome() {
  loading.value = true
  try {
    const safe = (p) => p.then((v) => v).catch(() => null)
    const [h, pol, drugs] = await Promise.all([
      safe(institutionHome()),
      safe(pagePolicies({ pageNum: 1, pageSize: 5 })),
      safe(pageInstitutionDrugs({ pageNum: 1, pageSize: 1 }))
    ])
    home.value = h || {}
    policies.value = pol?.records || []
    drugCount.value = drugs?.total || 0
  } finally {
    loading.value = false
  }
}

// keep-alive 切回时刷新;首次由 onMounted 加载,跳过避免双重请求
let firstActivation = true
onMounted(() => { loadHome() })
onActivated(() => {
  if (firstActivation) { firstActivation = false; return }
  loadHome()
})
</script>

<style scoped>
.pagehead { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.kick { display: inline-flex; align-items: center; gap: 8px; font-size: 12px; color: var(--indigo); background: var(--indigo-glow); padding: 5px 12px; border-radius: 999px; margin-bottom: 10px; }
.kick i { width: 6px; height: 6px; border-radius: 50%; background: var(--indigo-hi); }
.pagehead h1 { font-family: var(--font-d); font-weight: 800; font-size: 26px; letter-spacing: -0.02em; }
.pagehead .sub { font-family: var(--font-m); font-size: 12.5px; color: var(--ink-3); margin-top: 5px; }

.kpis { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
.row { display: grid; gap: 18px; }
.row.c73 { grid-template-columns: 1fr 1.2fr; }
.row.c11 { grid-template-columns: 1fr 1fr; }

.panel { background: var(--surface); border: 1px solid var(--line); border-radius: var(--rad); box-shadow: var(--sh-sm); }
.panel__hd { display: flex; align-items: center; justify-content: space-between; padding: 15px 20px; border-bottom: 1px solid var(--line-2); }
.panel__hd h3 { font-family: var(--font-d); font-weight: 700; font-size: 15px; }
.more { font-size: 12.5px; color: var(--indigo); text-decoration: none; }
.more:hover { text-decoration: underline; }

/* 本院信息 */
.info { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px 24px; padding: 18px 20px; }
.info__r { display: flex; align-items: baseline; gap: 10px; min-width: 0; }
.info__k { font-family: var(--font-m); font-size: 12px; color: var(--ink-3); flex: none; width: 64px; }
.info__v { font-size: 13.5px; color: var(--ink); word-break: break-all; }

/* 科室分布(圆环,复用 ChartCard)—— 本院信息后接圆环卡,视觉与 panel 一致 */
.bullets { padding: 10px 18px 16px; }
.bullet { display: flex; align-items: center; gap: 10px; padding: 9px 2px; border-bottom: 1px solid var(--line-2); }
.bullet:last-child { border-bottom: none; }
.bullet__type { font-family: var(--font-m); font-size: 11px; color: #fff; border-radius: 6px; padding: 2px 8px; flex: none; }
.bullet__type.type-1 { background: #4338ca; }
.bullet__type.type-2 { background: #d97706; }
.bullet__type.type-3 { background: #0e9c8f; }
.bullet__title { font-size: 13px; color: var(--ink); flex: 1; }
.bullet__company { font-family: var(--font-m); font-size: 11.5px; color: var(--indigo); background: var(--indigo-soft); padding: 2px 8px; border-radius: 4px; }
.bullet__date { font-family: var(--font-m); font-size: 11.5px; color: var(--ink-3); }
.empty { font-family: var(--font-m); font-size: 12.5px; color: var(--ink-3); padding: 18px 4px; }

/* 快捷入口 */
.acts { display: grid; grid-template-columns: repeat(2, 1fr); gap: 14px; padding: 16px 18px 18px; }
.act {
  position: relative; text-decoration: none; color: inherit; display: block;
  background: var(--field); border: 1px solid var(--line); border-radius: var(--rad-sm);
  padding: 16px 16px 18px; transition: transform 0.18s, box-shadow 0.18s, border-color 0.18s;
}
a.act:hover { transform: translateY(-3px); box-shadow: var(--sh); border-color: var(--ac); }
.act__ic {
  width: 38px; height: 38px; border-radius: 10px; display: grid; place-items: center;
  color: var(--ac); background: color-mix(in srgb, var(--ac) 12%, transparent); margin-bottom: 12px;
}
.act__t { font-family: var(--font-d); font-weight: 700; font-size: 15px; }
.act__d { font-size: 12.5px; color: var(--ink-3); margin-top: 4px; }
.act__arr { position: absolute; right: 16px; top: 16px; color: var(--ac); font-style: normal; font-weight: 700; opacity: 0.7; }

@media (max-width: 1040px) {
  .kpis { grid-template-columns: repeat(2, 1fr); }
  .row.c73, .row.c11 { grid-template-columns: 1fr; }
  .info { grid-template-columns: 1fr; }
}
@media (max-width: 560px) { .kpis { grid-template-columns: 1fr; } }
</style>
