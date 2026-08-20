<template>
  <div class="page">
    <div class="bar">
      <div class="filters">
        <el-input v-model="q.name" placeholder="医师姓名" clearable style="width: 150px" @keyup.enter="onSearch" />
        <el-select v-model="q.institutionId" placeholder="机构" clearable style="width: 180px" @change="onInstFilter">
          <el-option v-for="i in institutions" :key="i.id" :value="i.id" :label="i.name" />
        </el-select>
        <el-select v-model="q.departmentId" placeholder="科室" clearable style="width: 150px" @change="onSearch">
          <el-option v-for="d in filterDepts" :key="d.id" :value="d.id" :label="d.name" />
        </el-select>
        <el-select v-model="q.title" placeholder="职称" clearable style="width: 130px" @change="onSearch">
          <el-option v-for="t in TITLES" :key="t" :value="t" :label="t" />
        </el-select>
        <el-button type="primary" @click="onSearch">查询</el-button>
        <el-button link type="info" @click="onReset">重置</el-button>
      </div>
      <el-button v-if="!isGuest" type="primary" @click="openCreate">+ 新增医师</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" stripe class="tbl">
      <el-table-column label="姓名" width="110">
        <template #default="{ row }"><span class="cell-primary">{{ row.name }}</span></template>
      </el-table-column>
      <el-table-column label="机构" min-width="160">
        <template #default="{ row }">{{ instMap.get(row.institutionId) || '—' }}</template>
      </el-table-column>
      <el-table-column label="科室" width="120">
        <template #default="{ row }">
          <span v-if="deptMap.get(row.departmentId)" class="cell-pill">{{ deptMap.get(row.departmentId) }}</span>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="职称" width="120">
        <template #default="{ row }">
          <span v-if="row.title" class="cell-pill">{{ row.title }}</span>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column prop="phone" label="电话" width="140" />
      <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
      <el-table-column v-if="!isGuest" label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="warning" @click="openReset(row)">重置密码</el-button>
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

    <el-dialog v-model="dlg" :title="form.id ? '编辑医师' : '新增医师'" width="600px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <!-- 仅新增时建账号 -->
        <template v-if="!form.id">
          <el-form-item label="登录名" prop="username">
            <el-input v-model="form.username" maxlength="50" placeholder="医师登录账号" />
          </el-form-item>
          <el-form-item label="初始密码" prop="password">
            <el-input v-model="form.password" type="password" maxlength="50" show-password autocomplete="new-password" placeholder="至少 6 位" />
          </el-form-item>
        </template>
        <el-form-item label="姓名" prop="name"><el-input v-model="form.name" maxlength="30" /></el-form-item>
        <el-form-item label="机构" prop="institutionId">
          <el-select v-model="form.institutionId" placeholder="选择机构" style="width: 100%" @change="onFormInstChange">
            <el-option v-for="i in institutions" :key="i.id" :value="i.id" :label="i.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="科室">
          <el-select v-model="form.departmentId" placeholder="先选机构再选科室" clearable :disabled="!form.institutionId" style="width: 100%">
            <el-option v-for="d in formDepts" :key="d.id" :value="d.id" :label="d.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="职称">
          <el-select v-model="form.title" placeholder="选择职称" clearable style="width: 100%">
            <el-option v-for="t in TITLES" :key="t" :value="t" :label="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="电话"><el-input v-model="form.phone" maxlength="20" /></el-form-item>
        <el-form-item label="邮箱" prop="email"><el-input v-model="form.email" maxlength="100" /></el-form-item>
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
          <span style="font-size: 13px; color: var(--ink-2)">{{ resetTarget?.name }}</span>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageDoctors, createDoctor, updateDoctor, deleteDoctor, resetDoctorPassword } from '../../api/doctor'
import { listInstitutions } from '../../api/institution'
import { listDepartments } from '../../api/department'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../../stores/user'

const isGuest = useUserStore().isGuest
const route = useRoute()
const router = useRouter()
const TITLES = ['医师', '主治医师', '副主任医师', '主任医师']

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const q = reactive({ name: '', institutionId: undefined, departmentId: undefined, title: undefined, pageNum: 1, pageSize: 10 })

const institutions = ref([])
const departments = ref([])
const instMap = computed(() => new Map(institutions.value.map((i) => [i.id, i.name])))
const deptMap = computed(() => new Map(departments.value.map((d) => [d.id, d.name])))
// 筛选栏:按已选机构级联科室;未选机构时展示全部(可全局按科室筛)
const filterDepts = computed(() =>
  q.institutionId ? departments.value.filter((d) => d.institutionId === q.institutionId) : departments.value
)
// 表单:必须先选机构才有科室可选
const formDepts = computed(() =>
  form.institutionId ? departments.value.filter((d) => d.institutionId === form.institutionId) : []
)

async function load() {
  loading.value = true
  try {
    const r = await pageDoctors({
      name: q.name || undefined, institutionId: q.institutionId || undefined,
      departmentId: q.departmentId || undefined, title: q.title || undefined,
      pageNum: q.pageNum, pageSize: q.pageSize
    })
    rows.value = r.records || []
    total.value = r.total || 0
  } finally {
    loading.value = false
  }
}
function onSearch() { q.pageNum = 1; load() }
function onReset() {
  q.name = ''; q.institutionId = undefined; q.departmentId = undefined; q.title = undefined; q.pageNum = 1; load()
}
function onInstFilter() { q.departmentId = undefined; onSearch() }
function onPage(n) { q.pageNum = n; load() }

/* —— 新增 / 编辑 —— */
const dlg = ref(false)
const saving = ref(false)
const formRef = ref(null)
const form = reactive({
  id: null, username: '', password: '', name: '', institutionId: undefined,
  departmentId: undefined, title: undefined, phone: '', email: ''
})
const rules = {
  username: [
    { required: true, message: '请输入登录名', trigger: 'blur' },
    { min: 3, max: 50, message: '长度 3~50', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入初始密码', trigger: 'blur' },
    { min: 6, max: 50, message: '长度 6~50', trigger: 'blur' }
  ],
  name: [{ required: true, message: '请输入医师姓名', trigger: 'blur' }],
  institutionId: [{ required: true, message: '请选择机构', trigger: 'change' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}

function onFormInstChange() { form.departmentId = undefined }

function openCreate() {
  Object.assign(form, {
    id: null, username: '', password: '', name: '', institutionId: undefined,
    departmentId: undefined, title: undefined, phone: '', email: ''
  })
  dlg.value = true
}
function openEdit(row) {
  Object.assign(form, {
    id: row.id, username: '', password: '', name: row.name, institutionId: row.institutionId,
    departmentId: row.departmentId, title: row.title, phone: row.phone, email: row.email
  })
  dlg.value = true
  refreshReferenceData()  // 刷新科室列表以显示新增的科室
}

/** 刷新参考数据（科室、机构），确保显示新增的数据 */
async function refreshReferenceData() {
  try {
    const [insts, depts] = await Promise.all([listInstitutions(), listDepartments()])
    institutions.value = insts || []
    departments.value = depts || []
  } catch { /* 忽略错误 */ }
}

async function submit() {
  if (!formRef.value) return
  try { await formRef.value.validate() } catch { return }
  saving.value = true
  try {
    if (form.id) {
      // 编辑:仅档案字段,不动账号
      const { id, name, institutionId, departmentId, title, phone, email } = form
      await updateDoctor(id, { name, institutionId, departmentId, title, phone, email })
    } else {
      // 新增:带账号字段,去掉 id
      const { id, ...data } = form
      await createDoctor(data)
    }
    ElMessage.success(form.id ? '已更新' : '已新增')
    dlg.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除医师「${row.name}」?档案软删,其登录账号将被禁用。`, '删除确认', { type: 'warning' })
  await deleteDoctor(row.id)
  ElMessage.success('已删除')
  // 删除后若当前页已清空且非首页,回退一页,避免停在空页
  if (rows.value.length <= 1 && q.pageNum > 1) q.pageNum--
  load()
}

/* —— 重置密码(与机构端一致:仅改账号密码,不动档案)—— */
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
  if (!resetTarget.value || !resetFormRef.value) return
  try { await resetFormRef.value.validate() } catch { return }
  resetting.value = true
  try {
    await resetDoctorPassword(resetTarget.value.id, resetForm.password)
    ElMessage.success('密码已重置')
    resetDlg.value = false
  } finally {
    resetting.value = false
  }
}

// 从科室「医师数」点击带入(URL 带 departmentId):预填机构+科室筛选
function applyQuery() {
  if (route.query.departmentId) {
    if (route.query.institutionId) q.institutionId = Number(route.query.institutionId)
    q.departmentId = Number(route.query.departmentId)
    q.pageNum = 1
    return true
  }
  return false
}
onMounted(async () => {
  try {
    const [insts, depts] = await Promise.all([listInstitutions(), listDepartments()])
    institutions.value = insts || []
    departments.value = depts || []
  } catch { /* 401/失败由 request 统一处理 */ }
  applyQuery()
  load()
})
// keep-alive 复用:仅当本次是"科室→医师数"点击(URL 带 departmentId)才覆盖筛选并刷新,随后清 query 防重复触发
onActivated(() => {
  if (applyQuery()) {
    load()
    router.replace({ path: route.path, query: {} })
  }
})
</script>

<style scoped>
.bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.filters { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.tbl { border-radius: var(--rad); border: 1px solid var(--line); overflow: hidden; }
.pg { margin-top: 4px; justify-content: flex-end; }
</style>
