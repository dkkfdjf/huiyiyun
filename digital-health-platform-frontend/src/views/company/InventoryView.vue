<template>
  <div class="page">
    <!-- 概览摘要:图形化 KPI(告别纯流水表) -->
    <div class="kpis">
      <KpiCard label="销售出库" :value="sums.sales" unit="单" tone="out" delta="累计流水" delta-tone="flat" />
      <KpiCard label="补货入库" :value="sums.repls" unit="单" tone="in" delta="累计流水" delta-tone="flat" />
      <KpiCard label="在架药品" :value="sums.drugs" unit="种" tone="net" delta="可售品种" delta-tone="up" />
      <KpiCard label="库存预警" :value="sums.alerts" unit="条" tone="alert" :delta="sums.alerts ? '需补货' : '库存健康'" :delta-tone="sums.alerts ? 'down' : 'up'" />
    </div>

    <el-tabs v-model="tab" class="tabs">
      <el-tab-pane name="sale"><template #label><span>销售流水</span></template></el-tab-pane>
      <el-tab-pane name="repl"><template #label><span>补货流水</span></template></el-tab-pane>
      <el-tab-pane name="ledger"><template #label><span>进销存台账</span></template></el-tab-pane>
    </el-tabs>

    <!-- 销售流水 -->
    <template v-if="tab === 'sale'">
      <div class="bar">
        <div class="filters">
          <el-select v-model="sq.drugId" placeholder="药品" clearable style="width: 200px" @change="onSaleFilter">
            <el-option v-for="d in drugOptions" :key="d.value" :value="d.value" :label="d.label" />
          </el-select>
          <el-select v-model="sq.locationId" placeholder="销售网点" clearable style="width: 180px" @change="onSaleFilter">
            <el-option v-for="l in locationOptions" :key="l.value" :value="l.value" :label="l.label" />
          </el-select>
          <el-button type="primary" @click="onSaleSearch">查询</el-button>
          <el-button link type="info" @click="onSaleReset">重置</el-button>
        </div>
        <el-button type="primary" @click="openSale">+ 销售出库</el-button>
      </div>

      <el-table v-loading="saleLoading" :data="sales" stripe class="tbl">
        <el-table-column prop="recordNo" label="单号" width="200" />
        <el-table-column label="药品" min-width="150">
          <template #default="{ row }">
            <span class="cell-primary">{{ row.drugName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="locationName" label="销售网点" min-width="120" />
        <el-table-column label="数量" width="80">
          <template #default="{ row }">
            <span class="cell-num">{{ row.qty }}</span><span class="cell-unit">件</span>
          </template>
        </el-table-column>
        <el-table-column label="售价" width="100">
          <template #default="{ row }">
            <span class="cell-num">¥{{ fmtMoney(row.price) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="120">
          <template #default="{ row }">
            <span class="cell-num">¥{{ fmtMoney(row.amount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="销售时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.saleTime) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
      </el-table>

      <el-pagination
        background
        layout="total, prev, pager, next"
        :total="saleTotal"
        :page-size="sq.pageSize"
        :current-page="sq.pageNum"
        class="pg"
        @current-change="onSalePage"
      />
    </template>

    <!-- 补货流水 -->
    <template v-else-if="tab === 'repl'">
      <div class="bar">
        <div class="filters">
          <el-select v-model="rq.drugId" placeholder="药品" clearable style="width: 200px" @change="onReplFilter">
            <el-option v-for="d in drugOptions" :key="d.value" :value="d.value" :label="d.label" />
          </el-select>
          <el-select v-model="rq.locationId" placeholder="销售网点" clearable style="width: 180px" @change="onReplFilter">
            <el-option v-for="l in locationOptions" :key="l.value" :value="l.value" :label="l.label" />
          </el-select>
          <el-button type="primary" @click="onReplSearch">查询</el-button>
          <el-button link type="info" @click="onReplReset">重置</el-button>
        </div>
        <el-button type="primary" @click="openRepl">+ 补货入库</el-button>
      </div>

      <el-table v-loading="replLoading" :data="repls" stripe class="tbl">
        <el-table-column prop="orderNo" label="单号" width="200" />
        <el-table-column label="药品" min-width="150">
          <template #default="{ row }">
            <span class="cell-primary">{{ row.drugName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="locationName" label="销售网点" min-width="120" />
        <el-table-column label="入库数量" width="110">
          <template #default="{ row }">
            <span class="cell-num">{{ row.qty }}</span><span class="cell-unit">件</span>
          </template>
        </el-table-column>
        <el-table-column label="入库时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.inTime) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
      </el-table>

      <el-pagination
        background
        layout="total, prev, pager, next"
        :total="replTotal"
        :page-size="rq.pageSize"
        :current-page="rq.pageNum"
        class="pg"
        @current-change="onReplPage"
      />
    </template>

    <!-- 进销存台账 -->
    <template v-else-if="tab === 'ledger'">
      <div class="bar">
        <div class="filters">
          <el-select v-model="lq.drugId" placeholder="药品" clearable style="width: 200px" @change="onLedgerFilter">
            <el-option v-for="d in drugOptions" :key="d.value" :value="d.value" :label="d.label" />
          </el-select>
          <el-select v-model="lq.locationId" placeholder="销售网点" clearable style="width: 180px" @change="onLedgerFilter">
            <el-option v-for="l in locationOptions" :key="l.value" :value="l.value" :label="l.label" />
          </el-select>
          <el-date-picker
            v-model="ledgerRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 360px"
            @change="onLedgerFilter"
          />
          <el-button type="primary" @click="onLedgerSearch">查询</el-button>
          <el-button link type="info" @click="onLedgerReset">重置</el-button>
        </div>
        <el-button type="warning" plain :loading="reconciling" @click="onReconcile">一键对账修正</el-button>
      </div>

      <div class="ledger-tip">
        <span>对账规则:累计入库 − 累计销售 = 当前库存</span>
        <span v-if="lq.start && lq.end" class="warn">· 已加时间窗口,「对账」仅供参考(窗口内流量 vs 实时余额)</span>
      </div>

      <el-table v-loading="ledgerLoading" :data="ledgerRows" stripe class="tbl">
        <el-table-column label="药品" min-width="150">
          <template #default="{ row }">
            <span class="cell-primary">{{ row.drugName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="locationName" label="销售网点" min-width="120" />
        <el-table-column label="累计入库" width="110">
          <template #default="{ row }">
            <span class="cell-num num-in">+{{ row.totalIn ?? 0 }}</span><span class="cell-unit">件</span>
          </template>
        </el-table-column>
        <el-table-column label="累计销售" width="110">
          <template #default="{ row }">
            <span class="cell-num num-out">−{{ row.totalOut ?? 0 }}</span><span class="cell-unit">件</span>
          </template>
        </el-table-column>
        <el-table-column label="窗口净流量" width="110">
          <template #default="{ row }">
            <span class="cell-num" :class="row.windowDiff > 0 ? 'num-in' : row.windowDiff < 0 ? 'num-out' : ''">{{ row.windowDiff > 0 ? '+' : '' }}{{ row.windowDiff ?? 0 }}</span><span class="cell-unit">件</span>
          </template>
        </el-table-column>
        <el-table-column label="当前库存" width="110">
          <template #default="{ row }">
            <span class="cell-num">{{ row.currentStock ?? 0 }}</span><span class="cell-unit">件</span>
          </template>
        </el-table-column>
        <el-table-column label="对账" width="90" align="center">
          <template #default="{ row }">
            <StatusDot :tone="row.balanced ? 'green' : 'red'" :label="row.balanced ? '平账' : '差异'" />
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        background
        layout="total, prev, pager, next"
        :total="ledgerTotal"
        :page-size="lq.pageSize"
        :current-page="lq.pageNum"
        class="pg"
        @current-change="onLedgerPage"
      />
    </template>

    <!-- 销售出库 dialog(防超卖 + 药品×网点联动筛选) -->
    <el-dialog v-model="saleDlg" title="销售出库(防超卖)" width="520px">
      <el-alert v-if="!saleDrugOptions.length" type="warning" :closable="false" show-icon
        title="暂无铺货库存,请先在「库存管理」铺货后再出库。" class="alert" />
      <div class="link-hint">药品与销售网点已按铺货关系联动筛选 · 选其一,另一个仅显示有库存的组合</div>
      <el-form ref="saleFormRef" :model="saleForm" :rules="saleRules" label-width="96px">
        <el-form-item label="药品" prop="drugId">
          <el-select v-model="saleForm.drugId" placeholder="选择药品" style="width: 100%">
            <el-option v-for="d in saleDrugOptions" :key="d.value" :value="d.value" :label="d.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="销售网点" prop="locationId">
          <el-select v-model="saleForm.locationId" placeholder="选择网点" style="width: 100%">
            <el-option v-for="l in saleLocOptions" :key="l.value" :value="l.value" :label="l.label" />
          </el-select>
        </el-form-item>
        <div v-if="saleStockReadout" class="readout" :class="{ bad: !saleStock }">{{ saleStockReadout }}</div>
        <el-form-item label="销售数量" prop="qty">
          <el-input-number v-model="saleForm.qty" :min="1" :max="saleStock ? saleStock.stockQty : undefined"
            :step="1" :precision="0" :disabled="!saleStock" style="width: 200px" />
        </el-form-item>
        <el-form-item label="售价(¥)">
          <el-input-number v-model="saleForm.price" :min="0.01" :step="1" :precision="2" style="width: 200px" disabled />
          <span class="tip">按库存挂牌价出库(防低价做账)</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="saleForm.remark" maxlength="80" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="saleDlg = false">取消</el-button>
        <el-button type="primary" :loading="saleSaving" :disabled="!saleStock" @click="submitSale">出库</el-button>
      </template>
    </el-dialog>

    <!-- 补货入库 dialog(药品×网点联动筛选) -->
    <el-dialog v-model="replDlg" title="补货入库" width="480px">
      <div class="link-hint">药品与销售网点已按铺货关系联动筛选 · 选其一,另一个仅显示有库存的组合</div>
      <el-form ref="replFormRef" :model="replForm" :rules="replRules" label-width="96px">
        <el-form-item label="药品" prop="drugId">
          <el-select v-model="replForm.drugId" placeholder="选择药品" style="width: 100%">
            <el-option v-for="d in replDrugOptions" :key="d.value" :value="d.value" :label="d.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="销售网点" prop="locationId">
          <el-select v-model="replForm.locationId" placeholder="选择网点" style="width: 100%">
            <el-option v-for="l in replLocOptions" :key="l.value" :value="l.value" :label="l.label" />
          </el-select>
        </el-form-item>
        <div v-if="replStockReadout" class="readout" :class="{ bad: !replStock }">{{ replStockReadout }}</div>
        <el-form-item label="补货数量" prop="qty">
          <el-input-number v-model="replForm.qty" :min="1" :step="1" :precision="0" style="width: 200px" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="replForm.remark" maxlength="80" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="replDlg = false">取消</el-button>
        <el-button type="primary" :loading="replSaving" @click="submitRepl">入库</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageSales, sell, ledger, reconcileLedger } from '../../api/sale'
import { pageReplenish, replenish } from '../../api/replenish'
import { pageStocks, stockAlerts } from '../../api/stock'
import { pageDrugs } from '../../api/drug'
import { listLocations } from '../../api/location'
import KpiCard from '../../components/KpiCard.vue'
import StatusDot from '../../components/StatusDot.vue'

const tab = ref('sale')

const drugOptions = ref([])             // {value,label,status}
const locationOptions = ref([])          // {value,label}
const onShelfOptions = computed(() => drugOptions.value.filter((d) => d.status === 1))
// 已铺货库存行:出库/补货 dialog「药品×网点」联动筛选数据源,兼带 stockQty/price 供读数
const stocks = ref([])

const fmtMoney = (v) => (v == null ? '—' : Number(v).toFixed(2))
const fmtTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '—')
// 库存行去重成下拉选项(保留首次出现的 label)
const dedupOptions = (rows, valueKey, labelKey) => {
  const map = new Map()
  rows.forEach((s) => { if (s[valueKey] != null && !map.has(s[valueKey])) map.set(s[valueKey], s[labelKey]) })
  return [...map.entries()].map(([value, label]) => ({ value, label }))
}

/* —— 销售流水 —— */
const saleLoading = ref(false)
const sales = ref([])
const saleTotal = ref(0)
const sq = reactive({ drugId: undefined, locationId: undefined, pageNum: 1, pageSize: 10 })
async function loadSales() {
  saleLoading.value = true
  try {
    const r = await pageSales({
      drugId: sq.drugId || undefined, locationId: sq.locationId || undefined,
      pageNum: sq.pageNum, pageSize: sq.pageSize
    })
    sales.value = r.records || []
    saleTotal.value = r.total || 0
  } finally { saleLoading.value = false }
}
function onSaleFilter() { sq.pageNum = 1; loadSales() }
function onSaleSearch() { sq.pageNum = 1; loadSales() }
function onSaleReset() { sq.drugId = undefined; sq.locationId = undefined; sq.pageNum = 1; loadSales() }
function onSalePage(n) { sq.pageNum = n; loadSales() }

/* —— 补货流水 —— */
const replLoading = ref(false)
const repls = ref([])
const replTotal = ref(0)
const rq = reactive({ drugId: undefined, locationId: undefined, pageNum: 1, pageSize: 10 })
async function loadRepls() {
  replLoading.value = true
  try {
    const r = await pageReplenish({
      drugId: rq.drugId || undefined, locationId: rq.locationId || undefined,
      pageNum: rq.pageNum, pageSize: rq.pageSize
    })
    repls.value = r.records || []
    replTotal.value = r.total || 0
  } finally { replLoading.value = false }
}
function onReplFilter() { rq.pageNum = 1; loadRepls() }
function onReplSearch() { rq.pageNum = 1; loadRepls() }
function onReplReset() { rq.drugId = undefined; rq.locationId = undefined; rq.pageNum = 1; loadRepls() }
function onReplPage(n) { rq.pageNum = n; loadRepls() }

/* —— 进销存台账(累计入库 − 累计销售 vs 当前库存) —— */
const ledgerLoading = ref(false)
const ledgerRows = ref([])
const ledgerTotal = ref(0)
const ledgerRange = ref(null)     // [start, end] | null
const lq = reactive({ drugId: undefined, locationId: undefined, start: undefined, end: undefined, pageNum: 1, pageSize: 10 })
function syncLedgerRange() {
  if (ledgerRange.value && ledgerRange.value.length === 2) {
    lq.start = ledgerRange.value[0]; lq.end = ledgerRange.value[1]
  } else { lq.start = undefined; lq.end = undefined }
}
async function loadLedger() {
  ledgerLoading.value = true
  try {
    const r = await ledger({
      drugId: lq.drugId || undefined, locationId: lq.locationId || undefined,
      start: lq.start || undefined, end: lq.end || undefined,
      pageNum: lq.pageNum, pageSize: lq.pageSize
    })
    ledgerRows.value = r.records || []
    ledgerTotal.value = r.total || 0
  } finally { ledgerLoading.value = false }
}
function onLedgerFilter() { syncLedgerRange(); lq.pageNum = 1; loadLedger() }
function onLedgerSearch() { syncLedgerRange(); lq.pageNum = 1; loadLedger() }
function onLedgerReset() {
  lq.drugId = undefined; lq.locationId = undefined; ledgerRange.value = null
  syncLedgerRange(); lq.pageNum = 1; loadLedger()
}
function onLedgerPage(n) { lq.pageNum = n; loadLedger() }

/* —— 一键对账修正(以当前库存为准,补历史维护对账修正流水;当前库存数字不变) —— */
const reconciling = ref(false)
async function onReconcile() {
  try {
    await ElMessageBox.confirm(
      '将对所有库存行补写「历史维护对账修正」流水以消除台账差异(当前库存数字不变,仅补流水,可重复执行)。\n本次修正将向归属药企及平台管理员发送对账通知。',
      '一键对账修正',
      { type: 'warning', confirmButtonText: '确认修正', cancelButtonText: '取消' }
    )
  } catch { return }
  reconciling.value = true
  try {
    const r = await reconcileLedger()
    const sum = (r.surplus || 0) + (r.deficit || 0)
    if (sum === 0) ElMessage.info('台账已平,无需修正')
    else ElMessage.success(`已对账修正 ${sum} 条(盘盈 ${r.surplus || 0} · 盘亏 ${r.deficit || 0})`)
    loadLedger()
  } finally { reconciling.value = false }
}

/* —— 概览摘要(独立于列表筛选,保持稳定) —— */
const sums = reactive({ sales: 0, repls: 0, drugs: 0, alerts: 0 })
async function loadSummary() {
  const safe = (p) => p.then((v) => v).catch(() => null)
  const [s, r, a] = await Promise.all([
    safe(pageSales({ pageNum: 1, pageSize: 1 })),
    safe(pageReplenish({ pageNum: 1, pageSize: 1 })),
    safe(stockAlerts())
  ])
  sums.sales = s?.total || 0
  sums.repls = r?.total || 0
  sums.alerts = a?.length || 0
  sums.drugs = onShelfOptions.value.length
}

/* —— 下拉数据 —— */
async function loadOptions() {
  try {
    const d = await pageDrugs({ pageNum: 1, pageSize: 200 })
    drugOptions.value = (d.records || []).map((x) => ({ value: x.id, label: x.name, status: x.status }))
  } catch { /* 401/失败由 request 统一处理 */ }
  try {
    const list = await listLocations()
    locationOptions.value = (list || []).map((x) => ({ value: x.id, label: x.name }))
  } catch { /* 同上 */ }
}

/* —— 已铺货库存快照:出库/补货 dialog 药品×网点联动筛选的数据源(兼带 stockQty/price) —— */
async function loadStocks() {
  try {
    const r = await pageStocks({ pageNum: 1, pageSize: 500 })
    stocks.value = r.records || []
  } catch { /* 401/失败由 request 统一处理 */ }
}

/* —— 销售出库 dialog(药品×网点按铺货关系联动筛选) —— */
const saleDlg = ref(false)
const saleSaving = ref(false)
const saleFormRef = ref(null)
const saleForm = reactive({ drugId: undefined, locationId: undefined, qty: 1, price: undefined, remark: '' })
const saleRules = {
  drugId: [{ required: true, message: '请选择药品', trigger: 'change' }],
  locationId: [{ required: true, message: '请选择销售网点', trigger: 'change' }],
  qty: [{ required: true, message: '请输入销售数量', trigger: 'blur' }]
}
// 联动:选网点→药品仅该网点有库存的;选药品→网点仅该药有库存的(基于铺货快照)
const saleDrugOptions = computed(() => {
  const pool = saleForm.locationId ? stocks.value.filter((s) => s.locationId === saleForm.locationId) : stocks.value
  return dedupOptions(pool, 'drugId', 'drugName')
})
const saleLocOptions = computed(() => {
  const pool = saleForm.drugId ? stocks.value.filter((s) => s.drugId === saleForm.drugId) : stocks.value
  return dedupOptions(pool, 'locationId', 'locationName')
})
// 选中组合的库存读数(取自铺货快照,免额外请求;真实扣减仍由后端 deductStock 兜底)
const saleStock = computed(() =>
  saleForm.drugId && saleForm.locationId
    ? stocks.value.find((s) => s.drugId === saleForm.drugId && s.locationId === saleForm.locationId) || null
    : null
)
const saleStockReadout = computed(() => {
  if (!saleForm.drugId || !saleForm.locationId) return ''
  if (!saleStock.value) return '⚠ 该网点未铺此药,无法出库'
  return `当前库存 ${saleStock.value.stockQty} · 库存售价 ¥${fmtMoney(saleStock.value.price)}`
})
// 联动清失效:换药时若旧网点不在新药的铺货网点里则清空,反之亦然(避免选出不存在的组合)
watch(() => saleForm.drugId, () => {
  if (saleForm.locationId && !saleLocOptions.value.some((o) => o.value === saleForm.locationId)) saleForm.locationId = undefined
})
watch(() => saleForm.locationId, () => {
  if (saleForm.drugId && !saleDrugOptions.value.some((o) => o.value === saleForm.drugId)) saleForm.drugId = undefined
})
// 读数变化 → 同步售价展示(只读,后端强制库存挂牌价)
watch(saleStock, (s) => { saleForm.price = s ? s.price : undefined })
function openSale() {
  Object.assign(saleForm, { drugId: undefined, locationId: undefined, qty: 1, price: undefined, remark: '' })
  loadStocks()
  saleDlg.value = true
}
async function submitSale() {
  if (!saleFormRef.value) return
  try { await saleFormRef.value.validate() } catch { return }
  if (!saleStock.value) { ElMessage.warning('该网点未铺此药'); return }
  saleSaving.value = true
  try {
    await sell({
      drugId: saleForm.drugId, locationId: saleForm.locationId,
      qty: saleForm.qty, price: saleForm.price, remark: saleForm.remark
    })
    ElMessage.success('已出库,库存已扣减')
    saleDlg.value = false
    sq.pageNum = 1; loadSales()
    loadSummary(); loadStocks()
  } finally { saleSaving.value = false }
}

/* —— 补货入库 dialog(药品×网点按铺货关系联动筛选) —— */
const replDlg = ref(false)
const replSaving = ref(false)
const replFormRef = ref(null)
const replForm = reactive({ drugId: undefined, locationId: undefined, qty: 1, remark: '' })
const replRules = {
  drugId: [{ required: true, message: '请选择药品', trigger: 'change' }],
  locationId: [{ required: true, message: '请选择销售网点', trigger: 'change' }],
  qty: [{ required: true, message: '请输入补货数量', trigger: 'blur' }]
}
const replDrugOptions = computed(() => {
  const pool = replForm.locationId ? stocks.value.filter((s) => s.locationId === replForm.locationId) : stocks.value
  return dedupOptions(pool, 'drugId', 'drugName')
})
const replLocOptions = computed(() => {
  const pool = replForm.drugId ? stocks.value.filter((s) => s.drugId === replForm.drugId) : stocks.value
  return dedupOptions(pool, 'locationId', 'locationName')
})
const replStock = computed(() =>
  replForm.drugId && replForm.locationId
    ? stocks.value.find((s) => s.drugId === replForm.drugId && s.locationId === replForm.locationId) || null
    : null
)
const replStockReadout = computed(() => {
  if (!replForm.drugId || !replForm.locationId) return ''
  if (!replStock.value) return '⚠ 该网点未铺此药,请先在「库存管理」铺货'
  return `当前库存 ${replStock.value.stockQty}`
})
watch(() => replForm.drugId, () => {
  if (replForm.locationId && !replLocOptions.value.some((o) => o.value === replForm.locationId)) replForm.locationId = undefined
})
watch(() => replForm.locationId, () => {
  if (replForm.drugId && !replDrugOptions.value.some((o) => o.value === replForm.drugId)) replForm.drugId = undefined
})
function openRepl() {
  Object.assign(replForm, { drugId: undefined, locationId: undefined, qty: 1, remark: '' })
  loadStocks()
  replDlg.value = true
}
async function submitRepl() {
  if (!replFormRef.value) return
  try { await replFormRef.value.validate() } catch { return }
  replSaving.value = true
  try {
    await replenish({
      drugId: replForm.drugId, locationId: replForm.locationId,
      qty: replForm.qty, remark: replForm.remark
    })
    ElMessage.success('已入库,库存已增加')
    replDlg.value = false
    rq.pageNum = 1; loadRepls()
    loadSummary(); loadStocks()
  } finally { replSaving.value = false }
}

onMounted(async () => {
  await loadOptions()
  loadStocks()
  loadSales()
  loadRepls()
  loadLedger()
  loadSummary()
})
</script>

<style scoped>
.kpis { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 14px; }
@media (max-width: 1040px) { .kpis { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 560px) { .kpis { grid-template-columns: 1fr; } }
.tabs { margin-bottom: 4px; }
.tabs :deep(.el-tabs__header) { margin: 0 0 14px; }
.bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.filters { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.tbl { border-radius: var(--rad); border: 1px solid var(--line); overflow: hidden; }
.pg { margin-top: 4px; justify-content: flex-end; }
.alert { margin-bottom: 14px; }
.link-hint {
  margin-bottom: 14px; padding: 8px 12px; border-radius: 8px;
  font-size: 12.5px; color: var(--ink-2);
  background: var(--indigo-soft); border: 1px solid var(--indigo-line, rgba(67, 56, 202, 0.2));
}
.readout {
  margin: -4px 0 14px 96px; padding: 8px 12px; border-radius: 8px;
  font-size: 12.5px; color: var(--ink-2);
  background: var(--indigo-soft); border: 1px solid var(--indigo-line, rgba(67, 56, 202, 0.2));
}
.readout.bad { color: var(--red, #dc2a45); background: rgba(220, 42, 69, 0.08); border-color: rgba(220, 42, 69, 0.25); }
.tip { margin-left: 10px; font-size: 12px; color: var(--ink-3); }
.ledger-tip { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; margin: -4px 0 14px; font-size: 12.5px; color: var(--ink-3); }
.ledger-tip .warn { color: #b7791f; }
.num-in { color: #1f9d57; font-variant-numeric: tabular-nums; }
.num-out { color: var(--red, #dc2a45); font-variant-numeric: tabular-nums; }
</style>
