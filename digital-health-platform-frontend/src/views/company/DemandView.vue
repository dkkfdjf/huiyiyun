<template>
  <div class="page">
    <!-- 状态摘要:图形化 KPI(告别纯表格) -->
    <div class="kpis">
      <KpiCard label="待处理" :value="sums[0]" unit="条" tone="out" :delta="sums[0] ? '待你受理' : '无待办'" :delta-tone="sums[0] ? 'down' : 'up'" />
      <KpiCard label="跟进中" :value="sums[1]" unit="条" tone="net" delta="药企处置中" delta-tone="flat" />
      <KpiCard label="已满足" :value="sums[2]" unit="条" tone="in" delta="已闭环" delta-tone="up" />
      <KpiCard label="已驳回" :value="sums[3]" unit="条" tone="alert" delta="已关闭" delta-tone="flat" />
    </div>

    <div class="bar">
      <div class="filters">
        <el-select v-model="q.status" placeholder="状态" clearable style="width: 120px" @change="onSearch">
          <el-option v-for="(v, k) in DEMAND_STATUS" :key="k" :value="Number(k)" :label="v.label" />
        </el-select>
        <el-select v-model="q.demandType" placeholder="类型" clearable style="width: 150px" @change="onSearch">
          <el-option v-for="(v, k) in DEMAND_TYPE" :key="k" :value="Number(k)" :label="v" />
        </el-select>
        <el-select v-model="q.urgency" placeholder="紧急度" clearable style="width: 110px" @change="onSearch">
          <el-option v-for="(v, k) in URGENCY" :key="k" :value="Number(k)" :label="v" />
        </el-select>
        <el-button type="primary" @click="onSearch">查询</el-button>
        <el-button @click="onReset">重置</el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="rows" stripe class="tbl">
      <el-table-column label="药品" min-width="140">
        <template #default="{ row }"><span class="cell-primary">{{ row.drugName }}</span></template>
      </el-table-column>
      <el-table-column label="医疗机构" min-width="140">
        <template #default="{ row }">{{ row.institutionName || '—' }}</template>
      </el-table-column>
      <el-table-column label="反馈医师" width="96">
        <template #default="{ row }">{{ row.doctorName || '—' }}</template>
      </el-table-column>
      <el-table-column label="类型" width="130">
        <template #default="{ row }"><span class="cell-pill">{{ DEMAND_TYPE[row.demandType] || '—' }}</span></template>
      </el-table-column>
      <el-table-column label="紧急度" width="86">
        <template #default="{ row }">
          <StatusDot :tone="row.urgency === 2 ? 'red' : 'gray'" :label="row.urgency === 2 ? '紧急' : '一般'" />
        </template>
      </el-table-column>
      <el-table-column label="数量" width="86" align="right" header-align="right">
        <template #default="{ row }"><span class="cell-num">{{ row.qty }}</span><span class="cell-unit">件</span></template>
      </el-table-column>
      <el-table-column label="状态" width="96">
        <template #default="{ row }">
          <StatusDot :tone="statusTone(row.status)" :label="(DEMAND_STATUS[row.status] || {}).label || '—'" />
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="医师备注" min-width="150" show-overflow-tooltip />
      <el-table-column prop="reply" label="处置回复" min-width="140" show-overflow-tooltip />
      <el-table-column prop="createTime" label="提交时间" width="160" />
      <el-table-column label="操作" width="210" fixed="right">
        <template #default="{ row }">
          <div class="actions">
            <template v-if="row.status === 0">
              <el-button link type="primary" @click="accept(row)">受理</el-button>
              <el-button link type="danger" @click="reject(row)">驳回</el-button>
            </template>
            <template v-else-if="row.status === 1">
              <el-button link type="success" @click="satisfy(row)">已满足</el-button>
            </template>
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
        <el-descriptions-item label="医疗机构" :span="2">{{ detail.institutionName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="反馈医师" :span="2">{{ detail.doctorName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="医师备注" :span="2">{{ detail.remark || '—' }}</el-descriptions-item>
        <el-descriptions-item label="处置回复" :span="2">{{ detail.reply || '—' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pageDemands, acceptDemand, satisfyDemand, rejectDemand,
  DEMAND_STATUS, DEMAND_TYPE, URGENCY
} from '../../api/demand'
import KpiCard from '../../components/KpiCard.vue'
import StatusDot from '../../components/StatusDot.vue'

// 状态→状态点色调(精致商务表:状态用彩色点,不用 el-tag 彩色块)
const statusTone = (s) => ({ 0: 'amber', 1: 'indigo', 2: 'green', 3: 'red', 4: 'gray' })[s] || 'gray'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const q = reactive({ status: undefined, demandType: undefined, urgency: undefined, pageNum: 1, pageSize: 10 })

async function load() {
  loading.value = true
  try {
    const r = await pageDemands({
      status: q.status, demandType: q.demandType, urgency: q.urgency,
      pageNum: q.pageNum, pageSize: q.pageSize
    })
    rows.value = r.records || []
    total.value = r.total || 0
  } finally {
    loading.value = false
  }
}
function onSearch() { q.pageNum = 1; load() }
function onReset() { q.status = undefined; q.demandType = undefined; q.urgency = undefined; q.pageNum = 1; load() }
function onPage(n) { q.pageNum = n; load() }

async function accept(row) {
  await ElMessageBox.confirm(`确认受理「${row.drugName}」的反馈?受理后进入处理中。`, '受理确认', { type: 'info' })
  await acceptDemand(row.id)
  ElMessage.success('已受理')
  load()
}

async function satisfy(row) {
  const { value } = await ElMessageBox.prompt('处置说明(选填)', '标记已满足', {
    inputType: 'textarea',
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputPlaceholder: '可填写处置 / 供货说明'
  })
  await satisfyDemand(row.id, value || undefined)
  ElMessage.success('已标记满足')
  load()
}

async function reject(row) {
  const { value } = await ElMessageBox.prompt('请填写驳回原因(必填)', '驳回反馈(拒单)', {
    inputType: 'textarea',
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputPlaceholder: '如:不属于本药企 / 重复提交 / 无法供货',
    inputValidator: (v) => (v && v.trim()) ? true : '驳回原因必填',
    inputErrorMessage: '驳回原因必填'
  })
  await rejectDemand(row.id, value.trim())
  ElMessage.success('已驳回')
  load()
}

/* —— 状态摘要 —— */
const sums = reactive({ 0: 0, 1: 0, 2: 0, 3: 0 })
async function loadSummary() {
  const safe = (p) => p.then((v) => v).catch(() => null)
  const [a, b, c, d] = await Promise.all([
    safe(pageDemands({ status: 0, pageNum: 1, pageSize: 1 })),
    safe(pageDemands({ status: 1, pageNum: 1, pageSize: 1 })),
    safe(pageDemands({ status: 2, pageNum: 1, pageSize: 1 })),
    safe(pageDemands({ status: 3, pageNum: 1, pageSize: 1 }))
  ])
  sums[0] = a?.total || 0; sums[1] = b?.total || 0; sums[2] = c?.total || 0; sums[3] = d?.total || 0
}

/* —— 详情(只读) —— */
const detailDlg = ref(false)
const detail = ref(null)
function openDetail(row) { detail.value = row; detailDlg.value = true }

onMounted(() => { load(); loadSummary() })
</script>

<style scoped>
.kpis { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 14px; }
@media (max-width: 1040px) { .kpis { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 560px) { .kpis { grid-template-columns: 1fr; } }
.bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.filters { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.tbl { border-radius: var(--rad); border: 1px solid var(--line); overflow: hidden; }
.pg { margin-top: 4px; justify-content: flex-end; }
.actions { display: flex; justify-content: flex-end; gap: 8px; }
</style>
