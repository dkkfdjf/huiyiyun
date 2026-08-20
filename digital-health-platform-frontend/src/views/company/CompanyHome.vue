<template>
  <div class="home" v-loading="loading">
    <div class="pagehead">
      <div>
        <span class="kick"><i></i>药企工作台</span>
        <h1>{{ greet }}</h1>
        <div class="sub">{{ today }}</div>
      </div>
    </div>

    <!-- KPI:计数动画 + 色调,告别黑白大字 -->
    <div class="kpis">
      <KpiCard label="在管药品" :value="drugs" unit="种" tone="net" delta="按规格剂型归档" delta-tone="flat" />
      <KpiCard label="销售网点" :value="locations" unit="个" tone="in" delta="地图标注" delta-tone="flat" />
      <KpiCard label="库存预警" :value="alerts.length" unit="条" tone="alert"
               :delta="alerts.length ? '需补货' : '库存充足'" :delta-tone="alerts.length ? 'down' : 'up'" />
      <KpiCard label="待处理反馈" :value="pending" unit="条" tone="out"
               :delta="pending ? '待你受理' : '暂无待办'" :delta-tone="pending ? 'down' : 'up'" />
    </div>

    <!-- 库存水位(图形条)+ 运营流水 -->
    <div class="row c73">
      <StockAlertTable :rows="alerts" more="去库存管理 ›" @more="goStocks" />
      <OpsFeed :rows="feed" more="流向追溯 ›" @more="goInventory" />
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

    <!-- 本公司信息(M1:公司用户自助维护联系方式)-->
    <div class="panel">
      <div class="panel__hd"><h3>本公司信息</h3>
        <el-button link type="primary" @click="openProfile">编辑信息</el-button>
      </div>
      <div class="info">
        <div class="info__r"><span class="info__k">公司名称</span><span class="info__v">{{ profile.name || '—' }}</span></div>
        <div class="info__r"><span class="info__k">登录名</span><span class="info__v">{{ user.username || '—' }}</span></div>
        <div class="info__r"><span class="info__k">信用代码</span><span class="info__v">{{ profile.creditCode || '—' }}</span></div>
        <div class="info__r"><span class="info__k">联系人</span><span class="info__v">{{ profile.contactPerson || '—' }}</span></div>
        <div class="info__r"><span class="info__k">联系电话</span><span class="info__v">{{ profile.contactPhone || '—' }}</span></div>
        <div class="info__r"><span class="info__k">地址</span><span class="info__v">{{ profile.address || '—' }}</span></div>
        <div class="info__r"><span class="info__k">许可证号</span><span class="info__v">{{ profile.licenseNo || '—' }}</span></div>
      </div>
    </div>

    <!-- 编辑本公司信息 -->
    <el-dialog v-model="profileDlg" title="编辑本公司信息" width="520px">
      <el-form ref="profileFormRef" :model="profileForm" label-width="88px">
        <el-form-item label="联系人"><el-input v-model="profileForm.contactPerson" maxlength="30" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="profileForm.contactPhone" maxlength="20" /></el-form-item>
        <el-form-item label="地址"><el-input v-model="profileForm.address" maxlength="100" /></el-form-item>
        <el-form-item label="许可证号"><el-input v-model="profileForm.licenseNo" maxlength="50" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="profileDlg = false">取消</el-button>
        <el-button type="primary" :loading="profileSaving" @click="submitProfile">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { pageDrugs } from '../../api/drug'
import { stockAlerts } from '../../api/stock'
import { pageDemands } from '../../api/demand'
import { listLocations } from '../../api/location'
import { pageSales } from '../../api/sale'
import { pageReplenish } from '../../api/replenish'
import { getCompanyMe, updateCompanyMe } from '../../api/company'
import { useUserStore } from '../../stores/user'
import { todayLine } from '../../utils/format'
import KpiCard from '../../components/KpiCard.vue'
import StockAlertTable from '../../components/StockAlertTable.vue'
import OpsFeed from '../../components/OpsFeed.vue'

const user = useUserStore()
const router = useRouter()
const today = todayLine()
const greet = computed(() => `${user.realName || user.username || '你好'}`)

const goStocks = () => router.push('/company/stocks')
const goInventory = () => router.push('/company/inventory')

const loading = ref(false)
const drugs = ref(0)
const locations = ref(0)
const pending = ref(0)
const alerts = ref([])
const feed = ref([])

/* —— 本公司信息(M1)—— */
const profile = ref({})
const profileDlg = ref(false)
const profileSaving = ref(false)
const profileFormRef = ref(null)
const profileForm = reactive({ contactPerson: '', contactPhone: '', address: '', licenseNo: '' })

function openProfile() {
  Object.assign(profileForm, {
    contactPerson: profile.value.contactPerson || '',
    contactPhone: profile.value.contactPhone || '',
    address: profile.value.address || '',
    licenseNo: profile.value.licenseNo || ''
  })
  profileDlg.value = true
}
async function submitProfile() {
  profileSaving.value = true
  try {
    await updateCompanyMe(profileForm)
    ElMessage.success('已保存')
    profileDlg.value = false
    profile.value = await getCompanyMe()
  } finally {
    profileSaving.value = false
  }
}

const fmtHm = (t) => (t ? String(t).replace('T', ' ').slice(11, 16) : '--:--')

/* —— 快捷入口(彩色图标瓦)—— */
const SVG = {
  drug: '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M10.5 3.5 20.5 13.5a5 5 0 0 1-7 7L3.5 10.5a3.5 3.5 0 0 1 5-5z"/><path d="M8 8l8 8"/></svg>',
  stock: '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3.5" y="13" width="4.5" height="7.5" rx="1"/><rect x="9.75" y="9" width="4.5" height="11.5" rx="1"/><rect x="16" y="4.5" width="4.5" height="16" rx="1"/></svg>',
  flow: '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M3 7h11l-3-3M21 17H10l3 3"/></svg>',
  pin: '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 21s7-6.5 7-12a7 7 0 1 0-14 0c0 5.5 7 12 7 12z"/><circle cx="12" cy="9" r="2.5"/></svg>'
}
const actions = [
  { to: '/company/drugs', t: '药品管理', d: '档案维护 · 上下架', color: '#4338ca', svg: SVG.drug },
  { to: '/company/stocks', t: '库存管理', d: '铺货 · 维护 · 预警', color: '#0e9c8f', svg: SVG.stock },
  { to: '/company/inventory', t: '流向追溯', d: '出库扣减 · 入库回流', color: '#d97706', svg: SVG.flow },
  { to: '/company/locations', t: '销售网点', d: '网点 · 分布地图', color: '#7c3aed', svg: SVG.pin }
]

async function loadHome() {
  loading.value = true
  const safe = (p) => p.then((v) => v).catch(() => null)
  const [d, loc, p, al, sales, repl, me] = await Promise.all([
    safe(pageDrugs({ pageNum: 1, pageSize: 1 })),
    safe(listLocations()),
    safe(pageDemands({ status: 0, pageNum: 1, pageSize: 1 })),
    safe(stockAlerts()),
    safe(pageSales({ pageNum: 1, pageSize: 8 })),
    safe(pageReplenish({ pageNum: 1, pageSize: 8 })),
    safe(getCompanyMe())
  ])
  drugs.value = d?.total || 0
  locations.value = (loc && loc.length != null ? (loc.total ?? loc.length) : 0) || 0
  pending.value = p?.total || 0
  alerts.value = (al || []).slice(0, 6)
  profile.value = me || {}

  // 运营流水:销售(出库,红)+ 补货(入库,蓝),按时间倒序取最近 6 条
  const out = (sales?.records || []).map((s) => ({
    time: fmtHm(s.saleTime), event: `${s.drugName} · ${s.locationName || ''}`,
    delta: `−${s.qty}`, type: 'out'
  }))
  const inr = (repl?.records || []).map((r) => ({
    time: fmtHm(r.inTime), event: `${r.drugName} · ${r.locationName || ''}`,
    delta: `+${r.qty}`, type: 'in'
  }))
  feed.value = [...out, ...inr].slice(0, 6)

  loading.value = false
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

.kpis { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
.row { display: grid; gap: 18px; }
.row.c73 { grid-template-columns: 1.7fr 1fr; }

.panel { background: var(--surface); border: 1px solid var(--line); border-radius: var(--rad); box-shadow: var(--sh-sm); }
.panel__hd { display: flex; align-items: center; justify-content: space-between; padding: 15px 20px; border-bottom: 1px solid var(--line-2); }
.panel__hd h3 { font-family: var(--font-d); font-weight: 700; font-size: 15px; }
.acts { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; padding: 16px 18px 18px; }
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

/* 本公司信息 */
.info { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px 32px; padding: 18px 20px; }
.info__r { display: flex; align-items: baseline; gap: 10px; min-width: 0; }
.info__k { font-family: var(--font-m); font-size: 12px; color: var(--ink-3); flex: none; width: 64px; }
.info__v { font-size: 13.5px; color: var(--ink); word-break: break-all; }

@media (max-width: 1180px) { .kpis { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 1040px) {
  .row.c73 { grid-template-columns: 1fr; }
  .acts { grid-template-columns: repeat(2, 1fr); }
  .info { grid-template-columns: 1fr; }
}
@media (max-width: 560px) { .kpis { grid-template-columns: 1fr; } }
</style>
