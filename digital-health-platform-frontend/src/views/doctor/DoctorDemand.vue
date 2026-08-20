<template>
  <div class="page">
    <div class="bar">
      <div class="filters">
        <el-select v-model="q.status" placeholder="状态" clearable style="width: 120px" @change="onSearch">
          <el-option v-for="(v, k) in DEMAND_STATUS" :key="k" :value="Number(k)" :label="v.label" />
        </el-select>
        <el-select v-model="q.demandType" placeholder="类型" clearable style="width: 150px" @change="onSearch">
          <el-option v-for="(v, k) in DEMAND_TYPE" :key="k" :value="Number(k)" :label="v" />
        </el-select>
        <el-button type="primary" @click="onSearch">查询</el-button>
        <el-button @click="onReset">重置</el-button>
      </div>
      <el-button type="primary" @click="openCreate">+ 提交反馈</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" stripe class="tbl">
      <el-table-column label="药品" min-width="150">
        <template #default="{ row }"><span class="cell-primary">{{ row.drugName }}</span></template>
      </el-table-column>
      <el-table-column label="类型" width="130">
        <template #default="{ row }"><span class="cell-pill">{{ DEMAND_TYPE[row.demandType] || '—' }}</span></template>
      </el-table-column>
      <el-table-column label="紧急度" width="86">
        <template #default="{ row }">
          <StatusDot :tone="row.urgency === 2 ? 'red' : 'gray'" :label="row.urgency === 2 ? '紧急' : '一般'" />
        </template>
      </el-table-column>
      <el-table-column label="数量" width="90" align="right" header-align="right">
        <template #default="{ row }"><span class="cell-num">{{ row.qty }}</span><span class="cell-unit">件</span></template>
      </el-table-column>
      <el-table-column label="状态" width="96">
        <template #default="{ row }">
          <StatusDot :tone="statusTone(row.status)" :label="(DEMAND_STATUS[row.status] || {}).label || '—'" />
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
      <el-table-column prop="reply" label="处置回复" min-width="140" show-overflow-tooltip />
      <el-table-column prop="createTime" label="提交时间" width="160" />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <div class="actions">
            <el-button v-if="row.status === 0" link type="warning" @click="withdraw(row)">撤回</el-button>
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          </div>
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

    <el-dialog v-model="dlg" title="提交临床反馈" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="归属机构">
          <span class="cell-primary">{{ me.institutionName || '—' }}</span>
        </el-form-item>
        <el-form-item label="药品" prop="drugName">
          <div class="drug-pick">
            <el-radio-group v-model="drugMode" size="small" @change="onDrugMode">
              <el-radio-button value="select">从药品目录选择</el-radio-button>
              <el-radio-button value="manual">手动输入</el-radio-button>
            </el-radio-group>
            <el-select
              v-if="drugMode === 'select'"
              v-model="drugSel"
              filterable
              remote
              clearable
              :remote-method="searchDrug"
              :loading="drugLoading"
              placeholder="搜索药品名称 / 通用名"
              style="width: 100%"
              @change="onDrugPick"
              @visible-change="(v) => v && !drugOpts.length && searchDrug('')"
            >
              <el-option v-for="d in drugOpts" :key="d.id" :value="d.id" :label="drugLabel(d)" />
            </el-select>
            <el-input v-else v-model="form.drugName" maxlength="100" placeholder="手填药品通用名 / 商品名(未收录药品)" />
          </div>
        </el-form-item>
        <el-form-item label="类型" prop="demandType">
          <el-radio-group v-model="form.demandType">
            <el-radio :value="1">临床用药需求</el-radio>
            <el-radio :value="2">临床用量反馈</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="紧急度" prop="urgency">
          <el-radio-group v-model="form.urgency">
            <el-radio :value="1">一般</el-radio>
            <el-radio :value="2">紧急</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="数量" prop="qty">
          <el-input-number v-model="form.qty" :min="1" :max="99999" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="适应症 / 规格 / 其他说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">提交</el-button>
      </template>
    </el-dialog>

    <!-- 详情(只读结构化) -->
    <el-dialog v-model="detailDlg" title="反馈详情" width="560px">
      <el-descriptions v-if="detail" :column="2" border>
        <el-descriptions-item label="药品">{{ detail.drugName }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ DEMAND_TYPE[detail.demandType] || '—' }}</el-descriptions-item>
        <el-descriptions-item label="紧急度">
          <StatusDot :tone="detail.urgency === 2 ? 'red' : 'gray'" :label="detail.urgency === 2 ? '紧急' : '一般'" />
        </el-descriptions-item>
        <el-descriptions-item label="数量">{{ detail.qty }} 件</el-descriptions-item>
        <el-descriptions-item label="状态">
          <StatusDot :tone="statusTone(detail.status)" :label="(DEMAND_STATUS[detail.status] || {}).label || '—'" />
        </el-descriptions-item>
        <el-descriptions-item label="提交时间">{{ detail.createTime }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.remark || '—' }}</el-descriptions-item>
        <el-descriptions-item label="处置回复" :span="2">{{ detail.reply || '—' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageDemands, createDemand, withdrawDemand, DEMAND_STATUS, DEMAND_TYPE } from '../../api/demand'
import { searchDoctorDrugs, getDoctorMe } from '../../api/doctor'
import StatusDot from '../../components/StatusDot.vue'

// 状态→状态点色调(精致商务表:状态用彩色点,不用 el-tag 彩色块)
const statusTone = (s) => ({ 0: 'amber', 1: 'indigo', 2: 'green', 3: 'red', 4: 'gray' })[s] || 'gray'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const q = reactive({ status: undefined, demandType: undefined, pageNum: 1, pageSize: 10 })

async function load() {
  loading.value = true
  try {
    const r = await pageDemands({ status: q.status, demandType: q.demandType, pageNum: q.pageNum, pageSize: q.pageSize })
    rows.value = r.records || []
    total.value = r.total || 0
  } finally {
    loading.value = false
  }
}
function onSearch() { q.pageNum = 1; load() }
function onReset() { q.status = undefined; q.demandType = undefined; q.pageNum = 1; load() }
function onPage(n) { q.pageNum = n; load() }

const dlg = ref(false)
const saving = ref(false)
const formRef = ref(null)
const form = reactive({ drugName: '', drugId: null, demandType: 1, urgency: 1, qty: 1, remark: '' })
const rules = {
  drugName: [{ required: true, message: '请输入药品名称', trigger: 'blur' }],
  demandType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  urgency: [{ required: true, message: '请选择紧急度', trigger: 'change' }],
  qty: [{ required: true, message: '请填写数量', trigger: 'change' }]
}

function openCreate() {
  Object.assign(form, { drugName: '', drugId: null, demandType: 1, urgency: 1, qty: 1, remark: '' })
  drugMode.value = 'select'
  drugSel.value = undefined
  searchDrug('')
  dlg.value = true
}

/* —— 药品选择(目录选择自动关联药品与公司;手填则入未关联池)—— */
const drugMode = ref('select')
const drugSel = ref(undefined)
const drugOpts = ref([])
const drugLoading = ref(false)
const drugLabel = (d) => d.name + (d.specification ? ' · ' + d.specification : '')

async function searchDrug(kw) {
  drugLoading.value = true
  try {
    const res = await searchDoctorDrugs({ name: kw, pageNum: 1, pageSize: 30 })
    drugOpts.value = res?.records || []
  } finally {
    drugLoading.value = false
  }
}
function onDrugPick(id) {
  const d = drugOpts.value.find((x) => x.id === id)
  if (d) { form.drugName = d.name; form.drugId = d.id }
  else { form.drugId = null }
}
function onDrugMode(m) {
  // 切到手动 → 清药品关联(仅保留手填名);切到目录 → 清空重选
  if (m === 'manual') { form.drugId = null; drugSel.value = undefined }
  else { form.drugName = ''; form.drugId = null; drugSel.value = undefined; searchDrug('') }
}

async function submit() {
  if (!formRef.value) return
  try { await formRef.value.validate() } catch { return }
  saving.value = true
  try {
    await createDemand({ ...form })
    ElMessage.success('已提交')
    dlg.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function withdraw(row) {
  await ElMessageBox.confirm(`确认撤回「${row.drugName}」的反馈?撤回后不再纳入处置。`, '撤回确认', { type: 'warning' })
  await withdrawDemand(row.id)
  ElMessage.success('已撤回')
  load()
}

/* —— 详情(只读) —— */
const detailDlg = ref(false)
const detail = ref(null)
function openDetail(row) { detail.value = row; detailDlg.value = true }

const me = ref({})
onMounted(async () => {
  load()
  try { me.value = await getDoctorMe() } catch { /* 静默:归属机构为展示,失败不阻塞 */ }
})
</script>

<style scoped>
.bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.filters { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.tbl { border-radius: var(--rad); border: 1px solid var(--line); overflow: hidden; }
.pg { margin-top: 4px; justify-content: flex-end; }
.drug-pick { display: flex; flex-direction: column; gap: 8px; width: 100%; }
.actions { display: flex; justify-content: flex-end; gap: 8px; min-width: 80px; }
</style>
