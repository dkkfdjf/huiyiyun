<template>
  <div class="page">
    <div class="bar">
      <div class="filters">
        <el-input v-model="kw" placeholder="科室名称" clearable style="width: 200px" @keyup.enter="onSearch" @clear="onSearch" />
        <el-button type="primary" @click="onSearch">查询</el-button>
        <el-button link type="info" @click="onReset">重置</el-button>
      </div>
      <el-button type="primary" @click="openCreate">+ 新增科室</el-button>
    </div>

    <el-table v-loading="loading" :data="filtered" stripe class="tbl">
      <el-table-column prop="name" label="科室名称" min-width="220">
        <template #default="{ row }"><span class="cell-primary">{{ row.name }}</span></template>
      </el-table-column>
      <el-table-column label="医师数" width="120" align="right" header-align="right">
        <template #default="{ row }">
          <el-button v-if="row.doctorCount" link type="primary" class="cnt" @click="goDoctors(row)">{{ row.doctorCount }}</el-button>
          <b v-else class="zero">0</b>
        </template>
      </el-table-column>
      <el-table-column prop="sort" label="排序" width="120" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dlg" :title="form.id ? '编辑科室' : '新增科室'" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-form-item label="科室名称" prop="name"><el-input v-model="form.name" maxlength="30" placeholder="本院内不可重名" /></el-form-item>
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
import { useUserStore } from '../../stores/user'

const router = useRouter()
const user = useUserStore()

const loading = ref(false)
const rows = ref([])
const kw = ref('')
// 单机构科室数量少,客户端即时过滤(同管理端科室页模式)
const filtered = computed(() => {
  const k = kw.value.trim()
  return k ? rows.value.filter((d) => d.name.includes(k)) : rows.value
})

// 点击「医师数」钻取:带 departmentId 跳本院医师管理并预填筛选
function goDoctors(row) {
  router.push({ path: '/institution/doctors', query: { departmentId: row.id } })
}

async function load() {
  loading.value = true
  try {
    // 机构角色:后端 list 强制本院(institutionId 入参为空即返回本院)
    rows.value = (await listDepartments()) || []
  } finally {
    loading.value = false
  }
}
function onSearch() { /* 客户端过滤,computed 即时生效 */ }
function onReset() { kw.value = '' }

/* —— 新增 / 编辑 —— */
const dlg = ref(false)
const saving = ref(false)
const formRef = ref(null)
const form = reactive({ id: null, name: '', sort: 0 })
const rules = {
  name: [{ required: true, message: '请输入科室名称', trigger: 'blur' }]
}

function openCreate() {
  Object.assign(form, { id: null, name: '', sort: 0 })
  dlg.value = true
}
function openEdit(row) {
  Object.assign(form, { id: row.id, name: row.name, sort: row.sort ?? 0 })
  dlg.value = true
}

async function submit() {
  if (!formRef.value) return
  try { await formRef.value.validate() } catch { return }
  saving.value = true
  try {
    // institutionId 传本院(满足入参非空);后端机构角色会再次强制归属本院
    const payload = { institutionId: user.institutionId, name: form.name, sort: form.sort }
    if (form.id) await updateDepartment(form.id, payload)
    else await createDepartment(payload)
    ElMessage.success(form.id ? '已更新' : '已新增')
    dlg.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除科室「${row.name}」?若其下存在医师将无法删除。`, '删除确认', { type: 'warning' })
  await deleteDepartment(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<style scoped>
.bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; margin-bottom: 14px; }
.filters { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.tbl { border-radius: var(--rad); border: 1px solid var(--line); }
b.zero { color: var(--ink-3); font-weight: 500; }
.cnt { font-weight: 700; font-family: var(--font-m); }
</style>
