<template>
  <div class="page">
    <div class="bar">
      <div class="filters">
        <el-input v-model="q.name" placeholder="药品名称" clearable style="width: 180px" @keyup.enter="load" />
        <el-select v-model="q.status" placeholder="状态" clearable style="width: 120px" @change="load">
          <el-option :value="1" label="上架" />
          <el-option :value="0" label="下架" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="onReset">重置</el-button>
      </div>
      <el-button type="primary" @click="openCreate">+ 新增药品</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" stripe class="tbl">
      <el-table-column label="药品名称" min-width="150">
        <template #default="{ row }">
          <span class="cell-primary">{{ row.name }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="specification" label="规格" min-width="100" />
      <el-table-column label="剂型" min-width="80">
        <template #default="{ row }">
          <span class="cell-pill">{{ row.dosageForm }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="approvalNo" label="批准文号" min-width="140" />
      <el-table-column prop="unit" label="单位" width="70" />
      <el-table-column prop="producer" label="生产企业" min-width="130" />
      <el-table-column label="状态" width="86">
        <template #default="{ row }">
          <StatusDot :tone="row.status === 1 ? 'green' : 'gray'" :label="row.status === 1 ? '上架' : '下架'" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="186" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="toggle(row)">
            {{ row.status === 1 ? '下架' : '上架' }}
          </el-button>
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
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

    <el-dialog v-model="dlg" :title="form.id ? '编辑药品' : '新增药品'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-form-item label="药品名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="规格"><el-input v-model="form.specification" placeholder="如 0.25g" /></el-form-item>
        <el-form-item label="剂型"><el-input v-model="form.dosageForm" placeholder="如 胶囊" /></el-form-item>
        <el-form-item label="批准文号" prop="approvalNo"><el-input v-model="form.approvalNo" /></el-form-item>
        <el-form-item label="单位"><el-input v-model="form.unit" placeholder="如 盒" /></el-form-item>
        <el-form-item label="生产企业"><el-input v-model="form.producer" /></el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="上架" inactive-text="下架" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageDrugs, createDrug, updateDrug, toggleDrugStatus, deleteDrug } from '../../api/drug'
import StatusDot from '../../components/StatusDot.vue'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const q = reactive({ name: '', status: undefined, pageNum: 1, pageSize: 10 })

const dlg = ref(false)
const saving = ref(false)
const formRef = ref(null)
const form = reactive({
  id: null, name: '', specification: '', dosageForm: '', approvalNo: '', unit: '', producer: '', status: 1
})
const rules = {
  name: [{ required: true, message: '请输入药品名称', trigger: 'blur' }],
  approvalNo: [{ required: true, message: '请输入批准文号', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    const r = await pageDrugs({ name: q.name || undefined, status: q.status, pageNum: q.pageNum, pageSize: q.pageSize })
    rows.value = r.records || []
    total.value = r.total || 0
  } finally {
    loading.value = false
  }
}
function onPage(n) {
  q.pageNum = n
  load()
}
function onReset() { q.name = ''; q.status = undefined; q.pageNum = 1; load() }

function openCreate() {
  Object.assign(form, { id: null, name: '', specification: '', dosageForm: '', approvalNo: '', unit: '', producer: '', status: 1 })
  dlg.value = true
}
function openEdit(row) {
  Object.assign(form, {
    id: row.id, name: row.name, specification: row.specification, dosageForm: row.dosageForm,
    approvalNo: row.approvalNo, unit: row.unit, producer: row.producer, status: row.status
  })
  dlg.value = true
}

async function submit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    const { id, ...data } = form
    if (id) await updateDrug(id, data)
    else await createDrug(data)
    ElMessage.success(id ? '已更新' : '已新增')
    dlg.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function toggle(row) {
  await toggleDrugStatus(row.id, row.status === 1 ? 0 : 1)
  ElMessage.success(row.status === 1 ? '已下架' : '已上架')
  load()
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除药品「${row.name}」? 删除后仅标记为不可用，历史记录保留。`, '删除确认', { type: 'warning' })
  await deleteDrug(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<style scoped>
.bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.filters { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.tbl { border-radius: var(--rad); border: 1px solid var(--line); overflow: hidden; }
.pg { margin-top: 4px; justify-content: flex-end; }
</style>
