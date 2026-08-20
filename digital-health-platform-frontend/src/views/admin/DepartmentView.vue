<template>
  <div class="page">
    <div class="bar">
      <div class="filters">
        <el-select v-model="instId" placeholder="所属机构" style="width: 220px" @change="onInstChange">
          <el-option v-for="i in institutions" :key="i.id" :value="i.id" :label="i.name" />
        </el-select>
        <el-input v-model="kw" placeholder="科室名称" clearable style="width: 180px" @keyup.enter="onSearch" @clear="onSearch" />
        <el-button type="primary" :disabled="!instId" @click="onSearch">查询</el-button>
        <el-button :disabled="!instId" @click="onReset">重置</el-button>
      </div>
      <el-button v-if="!isGuest" type="primary" :disabled="!instId" @click="openCreate">+ 新增科室</el-button>
    </div>

    <el-table v-loading="loading" :data="filtered" stripe class="tbl">
      <el-table-column prop="name" label="科室名称" min-width="200" />
      <el-table-column label="医师数" width="100" align="right">
        <template #default="{ row }">
          <el-button v-if="row.doctorCount" link type="primary" class="cnt" @click="goDoctors(row)">{{ row.doctorCount }}</el-button>
          <b v-else class="zero">0</b>
        </template>
      </el-table-column>
      <el-table-column prop="sort" label="排序" width="120" />
      <el-table-column v-if="!isGuest" label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && !instId" description="请先选择所属机构" />

    <el-dialog v-model="dlg" :title="form.id ? '编辑科室' : '新增科室'" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-form-item label="所属机构">
          <el-input :model-value="instName" disabled />
        </el-form-item>
        <el-form-item label="科室名称" prop="name"><el-input v-model="form.name" maxlength="30" /></el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="9999" controls-position="right" />
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
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listDepartments, createDepartment, updateDepartment, deleteDepartment } from '../../api/department'
import { listInstitutions } from '../../api/institution'
import { useUserStore } from '../../stores/user'

const isGuest = useUserStore().isGuest
const router = useRouter()
// 点击「医师数」钻取:带 institutionId+departmentId 跳医师管理并预填筛选
function goDoctors(row) {
  router.push({ path: isGuest ? '/guest/doctors' : '/admin/doctors', query: { institutionId: instId.value, departmentId: row.id } })
}

const loading = ref(false)
const rows = ref([])
const institutions = ref([])
const instId = ref(undefined)
const kw = ref('')
const instName = computed(() => institutions.value.find((i) => i.id === instId.value)?.name || '')
// 科室按名称客户端过滤(单机构科室数量少,无需服务端检索,同城市页模式)
const filtered = computed(() => {
  const k = kw.value.trim()
  return k ? rows.value.filter((d) => d.name.includes(k)) : rows.value
})

async function loadDepts() {
  if (!instId.value) { rows.value = []; return }
  loading.value = true
  try {
    rows.value = (await listDepartments({ institutionId: instId.value })) || []
  } finally {
    loading.value = false
  }
}
function onInstChange() { loadDepts() }
function onSearch() { /* 客户端过滤,computed 即时生效 */ }
function onReset() { kw.value = '' }

/* —— 新增 / 编辑 —— */
const dlg = ref(false)
const saving = ref(false)
const formRef = ref(null)
const form = reactive({ id: null, institutionId: undefined, name: '', sort: 0 })
const rules = {
  name: [{ required: true, message: '请输入科室名称', trigger: 'blur' }]
}

function openCreate() {
  Object.assign(form, { id: null, institutionId: instId.value, name: '', sort: 0 })
  dlg.value = true
}
function openEdit(row) {
  Object.assign(form, { id: row.id, institutionId: row.institutionId, name: row.name, sort: row.sort })
  dlg.value = true
}

async function submit() {
  if (!formRef.value) return
  try { await formRef.value.validate() } catch { return }
  saving.value = true
  try {
    const { id, ...data } = form
    if (id) await updateDepartment(id, data)
    else await createDepartment(data)
    ElMessage.success(id ? '已更新' : '已新增')
    dlg.value = false
    loadDepts()
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除科室「${row.name}」?若其下存在医师将无法删除。`, '删除确认', { type: 'warning' })
  await deleteDepartment(row.id)
  ElMessage.success('已删除')
  loadDepts()
}

onMounted(async () => {
  try { institutions.value = (await listInstitutions()) || [] } catch { /* 统一处理 */ }
  if (institutions.value.length) {
    instId.value = institutions.value[0].id
    loadDepts()
  }
})
</script>

<style scoped>
.bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.filters { display: flex; align-items: center; gap: 10px; }
.tbl { border-radius: var(--rad); border: 1px solid var(--line); overflow: hidden; }
b.zero { color: var(--ink-3); font-weight: 500; }
.cnt { font-weight: 700; font-family: var(--font-m); }
</style>
