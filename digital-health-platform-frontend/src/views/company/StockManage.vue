<template>
  <div class="page">
    <el-tabs v-model="tab" class="tabs">
      <el-tab-pane name="list">
        <template #label>
          <span>库存列表</span>
        </template>
      </el-tab-pane>
      <el-tab-pane name="alert">
        <template #label>
          <span>库存预警 <el-badge v-if="alerts.length" :value="alerts.length" class="badge" /></span>
        </template>
      </el-tab-pane>
    </el-tabs>

    <!-- 库存列表 -->
    <template v-if="tab === 'list'">
      <div class="bar">
        <div class="filters">
          <el-select v-model="q.drugId" placeholder="药品" clearable style="width: 200px" @change="onFilter">
            <el-option v-for="d in drugOptions" :key="d.value" :value="d.value" :label="d.label" />
          </el-select>
          <el-select v-model="q.locationId" placeholder="销售网点" clearable style="width: 180px" @change="onFilter">
            <el-option v-for="l in locationOptions" :key="l.value" :value="l.value" :label="l.label" />
          </el-select>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button link type="info" @click="onReset">重置</el-button>
        </div>
        <el-button type="primary" @click="openInit">+ 铺货</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" stripe class="tbl">
        <el-table-column label="药品" min-width="150">
          <template #default="{ row }">
            <span class="cell-primary">{{ row.drugName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="locationName" label="销售网点" min-width="140" />
        <el-table-column label="售价" width="110">
          <template #default="{ row }">
            <span class="cell-num">¥{{ fmtMoney(row.price) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="库存水位" min-width="200">
          <template #default="{ row }">
            <StockLevel :qty="row.stockQty" :threshold="row.threshold" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="96" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">维护</el-button>
          </template>
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
    </template>

    <!-- 库存预警(派生态) -->
    <template v-else>
      <div class="bar">
        <div class="filters"><span class="hint">库存 ≤ 预警阈值 的条目,实时派生自库存表</span></div>
        <el-button @click="loadAlerts">刷新</el-button>
      </div>
      <el-table v-loading="alertLoading" :data="alerts" stripe class="tbl">
        <el-table-column label="药品" min-width="150">
          <template #default="{ row }">
            <span class="cell-primary">{{ row.drugName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="locationName" label="销售网点" min-width="140" />
        <el-table-column label="库存水位" min-width="200">
          <template #default="{ row }">
            <StockLevel :qty="row.stockQty" :threshold="row.threshold" />
          </template>
        </el-table-column>
        <el-table-column label="缺口(阈值−库存)" width="150">
          <template #default="{ row }">
            <span class="cell-num">{{ Math.max(0, (row.threshold ?? 0) - (row.stockQty ?? 0)) }}</span><span class="cell-unit">件</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="96" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">维护</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!alertLoading && !alerts.length" description="暂无预警,库存充足" />
    </template>

    <!-- 铺货 dialog -->
    <el-dialog v-model="initDlg" title="铺货(UC-A04-1)" width="520px">
      <el-alert v-if="!onShelfOptions.length" type="warning" :closable="false" show-icon
        title="没有上架中的药品,请先在「药品管理」新增并上架药品。" class="alert" />
      <el-form ref="initFormRef" :model="initForm" :rules="initRules" label-width="96px">
        <el-form-item label="药品" prop="drugId">
          <el-select v-model="initForm.drugId" placeholder="选择上架药品" style="width: 100%">
            <el-option v-for="d in onShelfOptions" :key="d.value" :value="d.value" :label="d.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="销售网点" prop="locationId">
          <el-select v-model="initForm.locationId" placeholder="选择网点" style="width: 100%">
            <el-option v-for="l in locationOptions" :key="l.value" :value="l.value" :label="l.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="初始库存" prop="stockQty">
          <el-input-number v-model="initForm.stockQty" :min="0" :step="1" :precision="0" style="width: 180px" />
        </el-form-item>
        <el-form-item label="售价(¥)" prop="price">
          <el-input-number v-model="initForm.price" :min="0.01" :step="1" :precision="2" style="width: 180px" />
        </el-form-item>
        <el-form-item label="预警阈值">
          <el-input-number v-model="initForm.threshold" :min="0" :step="1" :precision="0" style="width: 180px" />
          <span class="tip">库存降至该值即预警(0=不预警)</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="initDlg = false">取消</el-button>
        <el-button type="primary" :loading="initSaving" :disabled="!onShelfOptions.length" @click="submitInit">铺货</el-button>
      </template>
    </el-dialog>

    <!-- 维护 dialog(乐观锁) -->
    <el-dialog v-model="editDlg" title="维护售价 / 预警阈值" width="480px">
      <div class="target">药品「{{ editForm._drug }}」· 网点「{{ editForm._loc }}」</div>
      <el-alert type="info" :closable="false" show-icon class="alert"
        title="带乐观锁:若数据已被他人修改,将提示刷新后重试。" />
      <el-form ref="editFormRef" :model="editForm" label-width="96px">
        <el-form-item label="售价(¥)">
          <el-input-number v-model="editForm.price" :min="0.01" :step="1" :precision="2" style="width: 200px" />
        </el-form-item>
        <el-form-item label="预警阈值">
          <el-input-number v-model="editForm.threshold" :min="0" :step="1" :precision="0" style="width: 200px" />
          <span class="tip">库存降至该值即预警</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDlg = false">取消</el-button>
        <el-button type="primary" :loading="editSaving" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { pageStocks, stockAlerts, initStock, updateStock } from '../../api/stock'
import { pageDrugs } from '../../api/drug'
import { listLocations } from '../../api/location'
import StockLevel from '../../components/StockLevel.vue'

const tab = ref('list')

/* —— 库存列表 —— */
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const q = reactive({ drugId: undefined, locationId: undefined, pageNum: 1, pageSize: 10 })

const drugOptions = ref([])             // {value,label,status} — 列表筛选用全量
const locationOptions = ref([])          // {value,label}
const onShelfOptions = computed(() => drugOptions.value.filter((d) => d.status === 1)) // 铺货仅上架

const fmtMoney = (v) => (v == null ? '—' : Number(v).toFixed(2))

async function loadList() {
  loading.value = true
  try {
    const r = await pageStocks({
      drugId: q.drugId || undefined, locationId: q.locationId || undefined,
      pageNum: q.pageNum, pageSize: q.pageSize
    })
    rows.value = r.records || []
    total.value = r.total || 0
  } finally { loading.value = false }
}
function onFilter() { q.pageNum = 1; loadList() }
function onSearch() { q.pageNum = 1; loadList() }
function onReset() { q.drugId = undefined; q.locationId = undefined; q.pageNum = 1; loadList() }
function onPage(n) { q.pageNum = n; loadList() }

/* —— 预警 —— */
const alertLoading = ref(false)
const alerts = ref([])
async function loadAlerts() {
  alertLoading.value = true
  try { alerts.value = (await stockAlerts()) || [] }
  finally { alertLoading.value = false }
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

/* —— 铺货 —— */
const initDlg = ref(false)
const initSaving = ref(false)
const initFormRef = ref(null)
const initForm = reactive({ drugId: undefined, locationId: undefined, stockQty: 0, price: undefined, threshold: 0 })
const initRules = {
  drugId: [{ required: true, message: '请选择药品', trigger: 'change' }],
  locationId: [{ required: true, message: '请选择销售网点', trigger: 'change' }],
  stockQty: [{ required: true, message: '请输入初始库存', trigger: 'blur' }],
  price: [{ required: true, message: '请输入售价', trigger: 'blur' }]
}
function openInit() {
  Object.assign(initForm, { drugId: undefined, locationId: undefined, stockQty: 0, price: undefined, threshold: 0 })
  initDlg.value = true
}
async function submitInit() {
  if (!initFormRef.value) return
  try { await initFormRef.value.validate() } catch { return }
  initSaving.value = true
  try {
    await initStock({ ...initForm })
    ElMessage.success('已铺货')
    initDlg.value = false
    q.pageNum = 1
    loadList(); loadAlerts()
  } finally { initSaving.value = false }
}

/* —— 维护(乐观锁) —— */
const editDlg = ref(false)
const editSaving = ref(false)
const editFormRef = ref(null)
const editForm = reactive({ id: null, version: null, price: undefined, threshold: undefined, _drug: '', _loc: '' })
function openEdit(row) {
  Object.assign(editForm, {
    id: row.id, version: row.version,
    price: row.price, threshold: row.threshold,
    _drug: row.drugName, _loc: row.locationName
  })
  editDlg.value = true
}
async function submitEdit() {
  editSaving.value = true
  try {
    await updateStock({
      id: editForm.id, version: editForm.version,
      price: editForm.price, threshold: editForm.threshold
    })
    ElMessage.success('已维护')
    editDlg.value = false
    loadList(); loadAlerts()
  } finally { editSaving.value = false }
}

onMounted(async () => {
  await loadOptions()
  loadList()
  loadAlerts()
})
</script>

<style scoped>
.tabs { margin-bottom: 4px; }
.tabs :deep(.el-tabs__header) { margin: 0 0 14px; }
.badge { margin-left: 6px; }
.bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.filters { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.hint { font-size: 12.5px; color: var(--ink-3); }
.tbl { border-radius: var(--rad); border: 1px solid var(--line); overflow: hidden; }
.pg { margin-top: 4px; justify-content: flex-end; }
.hot { color: var(--red, #dc2a45); font-weight: 700; }
.alert { margin-bottom: 14px; }
.target { font-family: var(--font-d); font-weight: 600; font-size: 14.5px; margin-bottom: 12px; }
.tip { margin-left: 10px; font-size: 12px; color: var(--ink-3); }
</style>
