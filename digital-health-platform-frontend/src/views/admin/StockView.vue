<template>
  <div class="page">
    <div class="pagehead">
      <h1>库存预警</h1>
      <div class="sub">全平台各销售网点的库存水位 · 管理员全局视角</div>
    </div>

    <div class="kpis">
      <KpiCard
        label="库存预警"
        :value="alerts.length"
        unit="条"
        tone="alert"
        :delta="alerts.length ? '需补货' : '库存健康'"
        :delta-tone="alerts.length ? 'down' : 'up'"
      />
      <KpiCard label="在管库存" :value="total" unit="条目" tone="net" delta="全网点铺货" delta-tone="flat" />
    </div>

    <!-- 全部库存预警 -->
    <StockAlertTable :rows="alerts" title="全部库存预警" :more="null" remindable class="block" @remind="onRemind" />

    <!-- 全部库存明细(分页) -->
    <div class="card">
      <div class="card__head">
        <h3>全部库存</h3>
        <div class="head__right">
          <el-select
            v-model="q.companyId"
            filterable
            clearable
            placeholder="归属药企"
            class="co-sel"
            @change="onCompanyChange"
          >
            <el-option v-for="c in companies" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
          <span class="muted">分页浏览 · 按更新时间倒序</span>
        </div>
      </div>
      <div class="card__body">
        <el-table v-loading="loading" :data="rows" stripe>
          <el-table-column label="药品" min-width="160">
            <template #default="{ row }"><span class="primary">{{ row.drugName }}</span></template>
          </el-table-column>
          <el-table-column prop="locationName" label="销售网点" min-width="130" />
          <el-table-column label="归属公司" min-width="130">
            <template #default="{ row }">{{ row.companyName || '—' }}</template>
          </el-table-column>
          <el-table-column label="库存" width="80">
            <template #default="{ row }"><b class="qty" :class="st(row).key">{{ row.stockQty }}</b></template>
          </el-table-column>
          <el-table-column label="安全线" width="80">
            <template #default="{ row }">{{ row.threshold }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }"><span class="pillst" :class="st(row).key">{{ st(row).label }}</span></template>
          </el-table-column>
        </el-table>
        <el-pagination
          background
          layout="total, prev, pager, next"
          :total="total"
          :page-size="q.pageSize"
          :current-page="q.pageNum"
          class="pg"
          @current-change="onPage"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageStocks, stockAlerts, remindReplenish } from '../../api/stock'
import { listCompanies } from '../../api/company'
import { stockState } from '../../utils/format'
import KpiCard from '../../components/KpiCard.vue'
import StockAlertTable from '../../components/StockAlertTable.vue'

// 管理员全局视角:后端 /stocks、/stocks/alerts 对 ADMIN 不按 companyId 过滤,即全平台库存
const alerts = ref([])
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const companies = ref([])
const q = reactive({ pageNum: 1, pageSize: 10, companyId: null })
const st = (r) => stockState(r.stockQty, r.threshold)

async function loadAlerts() {
  try {
    alerts.value = (await stockAlerts()) || []
  } catch { /* 401/失败由 request 统一处理 */ }
}
async function loadStocks() {
  loading.value = true
  try {
    const params = { pageNum: q.pageNum, pageSize: q.pageSize }
    if (q.companyId) params.companyId = q.companyId
    const r = await pageStocks(params)
    rows.value = r.records || []
    total.value = r.total || 0
  } finally {
    loading.value = false
  }
}
async function loadCompanies() {
  try {
    companies.value = (await listCompanies()) || []
  } catch { /* 401/失败由 request 统一处理 */ }
}
function onPage(n) {
  q.pageNum = n
  loadStocks()
}
function onCompanyChange() {
  q.pageNum = 1
  loadStocks()
}

async function onRemind(row) {
  const co = row.companyName ? `(${row.companyName})` : ''
  await ElMessageBox.confirm(`确认提醒${co}补货「${row.drugName || '该药品'}」?将向归属药企发送一条站内通知。`, '提醒补货', { type: 'info' })
  await remindReplenish(row.id)
  ElMessage.success('已提醒药企补货')
}

onMounted(() => {
  loadAlerts()
  loadCompanies()
  loadStocks()
})
</script>

<style scoped>
.pagehead { margin-bottom: 14px; }
.pagehead h1 { font-family: var(--font-d); font-weight: 800; font-size: 24px; letter-spacing: -0.02em; }
.pagehead .sub { font-family: var(--font-m); font-size: 12.5px; color: var(--ink-3); margin-top: 5px; }

.kpis { display: grid; grid-template-columns: repeat(2, 1fr); gap: 14px; margin-bottom: 14px; }
.block { margin-bottom: 18px; }

.card { background: var(--surface); border: 1px solid var(--line); border-radius: var(--rad); box-shadow: var(--sh-sm); }
.card__head { display: flex; align-items: center; justify-content: space-between; padding: 15px 20px; border-bottom: 1px solid var(--line-2); }
.card__head h3 { font-family: var(--font-d); font-weight: 700; font-size: 15px; }
.card__head .muted { font-family: var(--font-m); font-size: 11px; color: var(--ink-3); }
.head__right { display: flex; align-items: center; gap: 12px; }
.co-sel { width: 200px; }
.card__body { padding: 6px 20px 14px; }

.primary { font-weight: 600; font-size: 13px; }
.qty { font-family: var(--font-m); font-weight: 700; }
.qty.lo { color: var(--red); }
.qty.mid { color: var(--amber); }
.qty.ok { color: var(--green); }
.pillst { font-family: var(--font-m); font-size: 10.5px; padding: 3px 9px; border-radius: 6px; font-weight: 600; }
.pillst.lo { background: rgba(220, 42, 69, 0.1); color: var(--red); }
.pillst.mid { background: var(--amber-soft); color: var(--amber); }
.pillst.ok { background: rgba(14, 156, 143, 0.1); color: var(--green); }
.pg { margin-top: 10px; justify-content: flex-end; }

@media (max-width: 1040px) { .kpis { grid-template-columns: 1fr; } }
</style>
