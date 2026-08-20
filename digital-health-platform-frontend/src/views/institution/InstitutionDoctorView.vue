<template>
  <div class="page">
    <div class="bar">
      <div class="filters">
        <el-input v-model="q.name" placeholder="医师姓名" clearable style="width: 180px" @keyup.enter="search" @clear="search" />
        <el-select v-model="q.departmentId" placeholder="科室" clearable style="width: 160px" @change="search">
          <el-option v-for="d in depts" :key="d.id" :value="d.id" :label="d.name" />
        </el-select>
        <el-select v-model="q.title" placeholder="职称" clearable style="width: 140px" @change="search">
          <el-option v-for="t in TITLES" :key="t" :value="t" :label="t" />
        </el-select>
        <el-button @click="search">查询</el-button>
        <el-button link type="info" @click="onReset">重置</el-button>
      </div>
      <el-button type="primary" @click="openCreate">+ 新增医师</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" stripe class="tbl">
      <el-table-column label="姓名" min-width="120">
        <template #default="{ row }"><span class="cell-primary">{{ row.name }}</span></template>
      </el-table-column>
      <el-table-column label="科室" min-width="140">
        <template #default="{ row }">{{ deptName(row.departmentId) }}</template>
      </el-table-column>
      <el-table-column label="职称" width="120">
        <template #default="{ row }">
          <span v-if="row.title" class="cell-pill">{{ row.title }}</span>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column prop="phone" label="电话" width="140" />
      <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="warning" @click="openReset(row)">重置密码</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination class="pager" background :current-page="q.pageNum" :page-size="q.pageSize" :total="total" layout="total, prev, pager, next" @current-change="onPage" />

    <!-- 新增 / 编辑 -->
    <el-dialog v-model="dlg" :title="form.id ? '编辑医师' : '新增医师'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="84px">
        <div v-if="!form.id" class="row2">
          <el-form-item label="登录名" prop="username"><el-input v-model="form.username" maxlength="50" /></el-form-item>
          <el-form-item label="初始密码" prop="password"><el-input v-model="form.password" type="password" show-password maxlength="50" autocomplete="new-password" /></el-form-item>
        </div>
        <el-form-item label="姓名" prop="name"><el-input v-model="form.name" maxlength="30" /></el-form-item>
        <div class="row2">
          <el-form-item label="科室">
            <el-select v-model="form.departmentId" placeholder="选择科室(可空)" clearable style="width: 100%">
              <el-option v-for="d in depts" :key="d.id" :value="d.id" :label="d.name" />
            </el-select>
          </el-form-item>
          <el-form-item label="职称">
            <el-select v-model="form.title" placeholder="选择职称" clearable style="width: 100%">
              <el-option v-for="t in TITLES" :key="t" :value="t" :label="t" />
            </el-select>
          </el-form-item>
        </div>
        <div class="row2">
          <el-form-item label="电话"><el-input v-model="form.phone" maxlength="20" /></el-form-item>
          <el-form-item label="邮箱" prop="email"><el-input v-model="form.email" maxlength="60" /></el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码 -->
    <el-dialog v-model="resetDlg" title="重置密码" width="420px">
      <el-form ref="resetFormRef" :model="resetForm" :rules="resetRules" label-width="72px">
        <el-form-item label="医师">
          <span class="rst-tip">{{ resetTarget?.name }}</span>
        </el-form-item>
        <el-form-item label="新密码" prop="password">
          <el-input v-model="resetForm.password" type="password" show-password maxlength="50" autocomplete="new-password" placeholder="6~50 位" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetDlg = false">取消</el-button>
        <el-button type="primary" :loading="resetting" @click="doReset">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageDoctors, createDoctor, updateDoctor, deleteDoctor, resetDoctorPassword } from '../../api/doctor'
import { listDepartments } from '../../api/department'
import { useUserStore } from '../../stores/user'

const user = useUserStore()
const route = useRoute()
const router = useRouter()
const TITLES = ['医师', '主治医师', '副主任医师', '主任医师']

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const depts = ref([])
const q = reactive({ name: '', departmentId: undefined, title: undefined, pageNum: 1, pageSize: 10 })

const deptName = (id) => depts.value.find((d) => d.id === id)?.name || '—'

async function load() {
  loading.value = true
  try {
    const res = await pageDoctors(q)
    rows.value = res?.records || []
    total.value = res?.total || 0
  } finally { loading.value = false }
}
function search() { q.pageNum = 1; load() }
function onReset() { q.name = ''; q.departmentId = undefined; q.title = undefined; q.pageNum = 1; load() }
function onPage(p) { q.pageNum = p; load() }

/* —— 新增 / 编辑 —— */
const dlg = ref(false)
const saving = ref(false)
const formRef = ref(null)
const form = reactive({ id: null, username: '', password: '', name: '', departmentId: undefined, title: '', phone: '', email: '' })
const rules = computed(() => ({
  username: [{ required: !form.id, message: '请输入登录名', trigger: 'blur' }],
  password: [{ required: !form.id, min: 6, message: '密码至少 6 位', trigger: 'blur' }],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}))

function openCreate() {
  Object.assign(form, { id: null, username: '', password: '', name: '', departmentId: undefined, title: '', phone: '', email: '' })
  dlg.value = true
}
function openEdit(row) {
  Object.assign(form, { id: row.id, username: '', password: '', name: row.name, departmentId: row.departmentId, title: row.title, phone: row.phone, email: row.email })
  dlg.value = true
}

async function submit() {
  if (!formRef.value) return
  try { await formRef.value.validate() } catch { return }
  saving.value = true
  try {
    // institutionId 传本院(满足入参非空);后端机构角色会再次强制校验归属
    if (form.id) {
      await updateDoctor(form.id, {
        name: form.name, institutionId: user.institutionId, departmentId: form.departmentId,
        title: form.title, phone: form.phone, email: form.email
      })
    } else {
      await createDoctor({
        username: form.username, password: form.password, name: form.name,
        institutionId: user.institutionId, departmentId: form.departmentId,
        title: form.title, phone: form.phone, email: form.email
      })
    }
    ElMessage.success(form.id ? '已更新' : '已新增')
    dlg.value = false
    load()
  } finally { saving.value = false }
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除医师「${row.name}」?将同时禁用其登录账号。`, '删除确认', { type: 'warning' })
  await deleteDoctor(row.id)
  ElMessage.success('已删除')
  // 删除后若当前页已清空且非首页,回退一页,避免停在空页
  if (rows.value.length <= 1 && q.pageNum > 1) q.pageNum--
  load()
}

/* —— 重置密码 —— */
const resetDlg = ref(false)
const resetting = ref(false)
const resetTarget = ref(null)
const resetFormRef = ref(null)
const resetForm = reactive({ password: '' })
const resetRules = {
  password: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 50, message: '密码长度 6~50', trigger: 'blur' }
  ]
}
function openReset(row) { resetTarget.value = row; resetForm.password = ''; resetDlg.value = true }
async function doReset() {
  if (!resetTarget.value) return
  if (!resetFormRef.value) return
  try { await resetFormRef.value.validate() } catch { return }
  resetting.value = true
  try {
    await resetDoctorPassword(resetTarget.value.id, resetForm.password)
    ElMessage.success('密码已重置')
    resetDlg.value = false
  } finally { resetting.value = false }
}

// 来自科室页「医师数」钻取:URL 带 departmentId 时预填科室筛选;返回 true 表示命中
function applyQuery() {
  if (route.query.departmentId) {
    q.departmentId = Number(route.query.departmentId)
    q.pageNum = 1
    return true
  }
  return false
}
onMounted(async () => {
  try { depts.value = (await listDepartments()) || [] } catch { /* 统一处理 */ }
  applyQuery()
  load()
})
// keep-alive 复用:仅当本次是「科室→医师数」钻取(URL 带 departmentId)才覆盖筛选并刷新,随后清 query 防重复触发
onActivated(() => {
  if (applyQuery()) {
    load()
    router.replace({ path: route.path, query: {} })
  }
})
</script>

<style scoped>
.bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; margin-bottom: 14px; }
.filters { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.tbl { border-radius: var(--rad); border: 1px solid var(--line); }
.pager { margin-top: 14px; justify-content: flex-end; }
.row2 { display: grid; grid-template-columns: 1fr 1fr; gap: 0 16px; }
.rst-tip { font-size: 13px; color: var(--ink-2); margin-bottom: 12px; }
</style>
