<template>
  <div class="page">
    <div class="bar">
      <div class="filters">
        <el-input v-model="q.username" placeholder="登录名" clearable style="width: 160px" @keyup.enter="onSearch" />
        <el-select v-model="q.role" placeholder="角色" clearable style="width: 140px" @change="onSearch">
          <el-option v-for="r in ROLE_OPTIONS" :key="r.value" :value="r.value" :label="r.label" />
        </el-select>
        <el-select v-model="q.status" placeholder="状态" clearable style="width: 120px" @change="onSearch">
          <el-option :value="1" label="正常" />
          <el-option :value="0" label="禁用" />
        </el-select>
        <el-button type="primary" @click="onSearch">查询</el-button>
        <el-button link type="info" @click="onReset">重置</el-button>
      </div>
      <el-button type="primary" @click="openCreate">+ 新增账号</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" stripe class="tbl">
      <el-table-column prop="username" label="登录名" min-width="120" />
      <el-table-column label="姓名" min-width="100">
        <template #default="{ row }"><span class="cell-primary">{{ row.realName || row.username }}</span></template>
      </el-table-column>
      <el-table-column label="角色" width="110">
        <template #default="{ row }"><span class="cell-pill">{{ roleMeta(row.role).t }}</span></template>
      </el-table-column>
      <el-table-column label="归属" min-width="140">
        <template #default="{ row }">{{ bindName(row) }}</template>
      </el-table-column>
      <el-table-column prop="phone" label="电话" width="130" />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <StatusDot :tone="row.status === 1 ? 'green' : 'gray'" :label="row.status === 1 ? '正常' : '禁用'" />
          <StatusDot v-if="lockedNow(row)" tone="red" label="锁定" style="margin-left: 8px" />
        </template>
      </el-table-column>
      <el-table-column prop="lastLoginTime" label="最近登录" width="160">
        <template #default="{ row }">{{ row.lastLoginTime || '—' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="warning" @click="openReset(row)">重置密码</el-button>
          <el-button v-if="row.lockedUntil" link type="success" @click="unlock(row)">解锁</el-button>
          <el-button link :type="row.status === 1 ? 'info' : 'success'" @click="toggle(row)">
            {{ row.status === 1 ? '禁用' : '启用' }}
          </el-button>
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

    <!-- 新增 / 编辑 -->
    <el-dialog v-model="dlg" :title="form.id ? '编辑账号' : '新增账号'" width="600px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-form-item label="登录名" prop="username">
          <el-input v-model="form.username" :disabled="!!form.id" maxlength="50" placeholder="登录名(创建后不可改)" />
        </el-form-item>
        <el-form-item v-if="!form.id" label="初始密码" prop="password">
          <el-input v-model="form.password" type="password" show-password autocomplete="new-password" maxlength="50" placeholder="6~50 位" />
        </el-form-item>
        <el-form-item label="姓名"><el-input v-model="form.realName" maxlength="30" /></el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="form.role" placeholder="选择角色" style="width: 100%" @change="onRoleChange">
            <el-option v-for="r in ROLE_OPTIONS" :key="r.value" :value="r.value" :label="r.label" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.role === ROLE.COMPANY" label="所属公司" prop="companyId">
          <el-select v-model="form.companyId" placeholder="选择公司" style="width: 100%">
            <el-option v-for="c in companies" :key="c.id" :value="c.id" :label="c.name" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.role === ROLE.INSTITUTION || form.role === ROLE.DOCTOR" label="所属机构" prop="institutionId">
          <el-select v-model="form.institutionId" placeholder="选择机构" style="width: 100%">
            <el-option v-for="i in institutions" :key="i.id" :value="i.id" :label="i.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="电话"><el-input v-model="form.phone" maxlength="20" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" maxlength="60" /></el-form-item>
        <el-form-item v-if="form.id" label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">正常</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码 -->
    <el-dialog v-model="resetDlg" title="重置密码" width="440px">
      <el-form ref="resetFormRef" :model="resetForm" :rules="resetRules" label-width="88px">
        <el-form-item label="账号">
          <span>{{ resetTarget.username }}（{{ resetTarget.realName || '—' }}）</span>
        </el-form-item>
        <el-form-item label="新密码" prop="password">
          <el-input v-model="resetForm.password" type="password" show-password autocomplete="new-password" maxlength="50" placeholder="6~50 位" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetDlg = false">取消</el-button>
        <el-button type="primary" :loading="resetting" @click="submitReset">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pageUsers, createUser, updateUser, deleteUser, resetUserPassword,
  toggleUserStatus, unlockUser, ROLE, ROLE_OPTIONS
} from '../../api/user'
import { listInstitutions } from '../../api/institution'
import { listCompanies } from '../../api/company'
import StatusDot from '../../components/StatusDot.vue'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const q = reactive({ username: '', role: undefined, status: undefined, pageNum: 1, pageSize: 10 })

const companies = ref([])
const institutions = ref([])
const companyMap = computed(() => new Map(companies.value.map((c) => [c.id, c.name])))
const instMap = computed(() => new Map(institutions.value.map((i) => [i.id, i.name])))

const ROLE_TAG = {
  [ROLE.ADMIN]: { t: '管理员', type: 'danger' },
  [ROLE.COMPANY]: { t: '医药公司', type: 'primary' },
  [ROLE.INSTITUTION]: { t: '医疗机构', type: 'success' },
  [ROLE.DOCTOR]: { t: '医师', type: 'warning' }
}
const roleMeta = (r) => ROLE_TAG[r] || { t: '—', type: 'info' }

const bindName = (row) => {
  if (row.companyId != null) return companyMap.value.get(row.companyId) || `公司#${row.companyId}`
  if (row.institutionId != null) return instMap.value.get(row.institutionId) || `机构#${row.institutionId}`
  return '—'
}
const lockedNow = (row) => row.lockedUntil != null && new Date(row.lockedUntil) > new Date()

async function load() {
  loading.value = true
  try {
    const r = await pageUsers({
      username: q.username || undefined, role: q.role, status: q.status,
      pageNum: q.pageNum, pageSize: q.pageSize
    })
    rows.value = r.records || []
    total.value = r.total || 0
  } finally {
    loading.value = false
  }
}
function onSearch() { q.pageNum = 1; load() }
function onReset() { q.username = ''; q.role = undefined; q.status = undefined; q.pageNum = 1; load() }
function onPage(n) { q.pageNum = n; load() }

/* —— 新增 / 编辑 —— */
const dlg = ref(false)
const saving = ref(false)
const formRef = ref(null)
const form = reactive({
  id: null, username: '', password: '', realName: '', role: undefined,
  companyId: undefined, institutionId: undefined, phone: '', email: '', status: 1
})
const rules = computed(() => ({
  username: [{ required: true, message: '请输入登录名', trigger: 'blur' }],
  password: [{ required: !form.id, message: '请输入初始密码', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
  companyId: [{ required: form.role === ROLE.COMPANY, message: '请选择公司', trigger: 'change' }],
  institutionId: [{
    required: form.role === ROLE.INSTITUTION || form.role === ROLE.DOCTOR,
    message: '请选择机构', trigger: 'change'
  }]
}))

function openCreate() {
  Object.assign(form, {
    id: null, username: '', password: '', realName: '', role: undefined,
    companyId: undefined, institutionId: undefined, phone: '', email: '', status: 1
  })
  dlg.value = true
}
function openEdit(row) {
  Object.assign(form, {
    id: row.id, username: row.username, password: '', realName: row.realName, role: row.role,
    companyId: row.companyId ?? undefined, institutionId: row.institutionId ?? undefined,
    phone: row.phone || '', email: row.email || '', status: row.status
  })
  dlg.value = true
}
// 角色切换时清掉无关绑定,避免脏数据提交
function onRoleChange() {
  if (form.role !== ROLE.COMPANY) form.companyId = undefined
  if (form.role !== ROLE.INSTITUTION && form.role !== ROLE.DOCTOR) form.institutionId = undefined
}

async function submit() {
  if (!formRef.value) return
  try { await formRef.value.validate() } catch { return }
  saving.value = true
  try {
    const { id, ...data } = form
    if (id) await updateUser(id, data)
    else await createUser(data)
    ElMessage.success(id ? '已更新' : '已新增')
    dlg.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function toggle(row) {
  const target = row.status === 1 ? 0 : 1
  await toggleUserStatus(row.id, target)
  ElMessage.success(target === 1 ? '已启用' : '已禁用')
  load()
}

async function unlock(row) {
  await ElMessageBox.confirm(`确认解锁账号「${row.username}」?`, '解锁确认', { type: 'warning' })
  await unlockUser(row.id)
  ElMessage.success('已解锁')
  load()
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除账号「${row.username}」?删除后该账号无法登录。`, '删除确认', { type: 'warning' })
  await deleteUser(row.id)
  ElMessage.success('已删除')
  load()
}

/* —— 重置密码 —— */
const resetDlg = ref(false)
const resetting = ref(false)
const resetFormRef = ref(null)
const resetForm = reactive({ password: '' })
const resetTarget = reactive({ id: null, username: '', realName: '' })
const resetRules = { password: [{ required: true, message: '请输入新密码', trigger: 'blur' }] }

function openReset(row) {
  Object.assign(resetTarget, { id: row.id, username: row.username, realName: row.realName })
  resetForm.password = ''
  resetDlg.value = true
}
async function submitReset() {
  if (!resetFormRef.value) return
  try { await resetFormRef.value.validate() } catch { return }
  resetting.value = true
  try {
    await resetUserPassword(resetTarget.id, resetForm.password)
    ElMessage.success('密码已重置')
    resetDlg.value = false
  } finally {
    resetting.value = false
  }
}

onMounted(async () => {
  try {
    const [cs, is] = await Promise.all([listCompanies(), listInstitutions()])
    companies.value = cs || []
    institutions.value = is || []
  } catch { /* 401/失败由 request 统一处理 */ }
  load()
})
</script>

<style scoped>
.bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.filters { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.tbl { border-radius: var(--rad); border: 1px solid var(--line); overflow: hidden; }
.pg { margin-top: 4px; justify-content: flex-end; }
</style>
