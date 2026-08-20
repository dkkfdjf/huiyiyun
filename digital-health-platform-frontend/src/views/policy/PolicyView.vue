<template>
  <div class="page">
    <div class="bar">
      <div class="filters">
        <el-input v-model="q.title" placeholder="标题关键字" clearable style="width: 200px" @keyup.enter="search" @clear="search" />
        <el-select v-model="q.policyType" placeholder="公告类型" clearable style="width: 140px" @change="search">
          <el-option v-for="(v, k) in POLICY_TYPE" :key="k" :value="Number(k)" :label="v.label" />
        </el-select>
        <el-select v-if="isAdmin" v-model="q.companyId" placeholder="所属公司" clearable style="width: 240px" @change="search">
          <el-option v-for="c in companies" :key="c.id" :value="c.id" :label="c.name" />
        </el-select>
        <el-button @click="search">查询</el-button>
        <el-button link type="info" @click="onReset">重置</el-button>
      </div>
      <el-button v-if="canEdit" type="primary" @click="openCreate">+ 发布公告</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" stripe class="tbl">
      <el-table-column prop="title" label="标题" min-width="220">
        <template #default="{ row }"><span class="t-title">{{ row.title }}</span></template>
      </el-table-column>
      <el-table-column label="所属公司" min-width="160">
        <template #default="{ row }">{{ row.companyName || '—' }}</template>
      </el-table-column>
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="POLICY_TYPE[row.policyType]?.type">{{ POLICY_TYPE[row.policyType]?.label }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="有效期" width="220">
        <template #default="{ row }">
          <span>{{ row.effectiveDate }} 起</span>
          <span v-if="row.expireDate" class="muted"> / {{ row.expireDate }} 止</span>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="发布时间" width="170" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openView(row)">详情</el-button>
          <template v-if="canEdit">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pager"
      background
      :current-page="q.pageNum"
      :page-size="q.pageSize"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="onPage"
    />

    <!-- 详情 / 新增 / 编辑 共用 -->
    <el-dialog v-model="dlg" :title="dlgTitle" width="660px">
      <!-- 详情:结构化只读面板 -->
      <DetailPanel v-if="readonly" :title="form.title" :meta="policyMeta" body-label="公告正文">
        <template #tags>
          <el-tag size="small" :type="POLICY_TYPE[form.policyType]?.type">{{ POLICY_TYPE[form.policyType]?.label }}</el-tag>
          <span v-if="form.companyName" class="dp-co">{{ form.companyName }}</span>
        </template>
        {{ form.content || '（暂无正文）' }}
      </DetailPanel>
      <!-- 新增 / 编辑:表单 -->
      <el-form v-else ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-form-item label="标题" prop="title"><el-input v-model="form.title" maxlength="100" show-word-limit /></el-form-item>
        <el-form-item label="类型" prop="policyType">
          <el-select v-model="form.policyType" placeholder="选择类型" style="width: 100%">
            <el-option v-for="(v, k) in POLICY_TYPE" :key="k" :value="Number(k)" :label="v.label" />
          </el-select>
        </el-form-item>
        <div class="row2">
          <el-form-item label="生效日期" prop="effectiveDate">
            <el-date-picker v-model="form.effectiveDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
          </el-form-item>
          <el-form-item label="到期日期">
            <el-date-picker v-model="form.expireDate" type="date" value-format="YYYY-MM-DD" placeholder="空=长期有效" style="width: 100%" />
          </el-form-item>
        </div>
        <el-form-item label="正文" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="8" maxlength="5000" show-word-limit placeholder="公告正文内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">{{ readonly ? '关闭' : '取消' }}</el-button>
        <el-button v-if="!readonly" type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pagePolicies, createPolicy, updatePolicy, deletePolicy, POLICY_TYPE } from '../../api/policy'
import { listCompanies } from '../../api/company'
import { useUserStore, ROLE } from '../../stores/user'
import DetailPanel from '../../components/DetailPanel.vue'

const user = useUserStore()
const isAdmin = computed(() => user.role === ROLE.ADMIN)
const isCompany = computed(() => user.role === ROLE.COMPANY)
// 公告仅药企自发;管理员全量只读(只看、不代发)
const canEdit = computed(() => isCompany.value)

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const companies = ref([])
const q = reactive({ title: '', policyType: undefined, companyId: undefined, pageNum: 1, pageSize: 10 })

const companyName = (id) => companies.value.find((c) => c.id === id)?.name || '—'

async function load() {
  loading.value = true
  try {
    const res = await pagePolicies(q)
    rows.value = res?.records || []
    total.value = res?.total || 0
  } finally {
    loading.value = false
  }
}
function search() { q.pageNum = 1; load() }
function onReset() { q.title = ''; q.policyType = undefined; q.companyId = undefined; q.pageNum = 1; load() }
function onPage(p) { q.pageNum = p; load() }

/* —— 详情 / 新增 / 编辑 —— */
const dlg = ref(false)
const saving = ref(false)
const readonly = ref(false)
const formRef = ref(null)
const form = reactive({ id: null, title: '', policyType: undefined, companyId: undefined, effectiveDate: '', expireDate: '', content: '' })
const dlgTitle = computed(() => readonly.value ? '公告详情' : form.id ? '编辑公告' : '发布公告')
const policyMeta = computed(() => {
  const m = [
    { label: '类型', value: POLICY_TYPE[form.policyType]?.label },
    { label: '生效日期', value: form.effectiveDate },
    { label: '到期日期', value: form.expireDate || '长期有效' }
  ]
  // 只在有企业名称时添加所属公司
  if (form.companyName) {
    m.splice(1, 0, { label: '所属公司', value: form.companyName })
  }
  return m
})
const rules = computed(() => ({
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  policyType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  effectiveDate: [{ required: true, message: '请选择生效日期', trigger: 'change' }]
}))

function reset() {
  Object.assign(form, { id: null, title: '', policyType: undefined, companyId: undefined, effectiveDate: '', expireDate: '', content: '' })
}
function openCreate() {
  reset(); readonly.value = false; dlg.value = true
}
function openEdit(row) {
  Object.assign(form, { id: row.id, title: row.title, policyType: row.policyType, companyId: row.companyId, companyName: row.companyName, effectiveDate: row.effectiveDate, expireDate: row.expireDate, content: row.content })
  readonly.value = false; dlg.value = true
}
function openView(row) {
  Object.assign(form, { id: row.id, title: row.title, policyType: row.policyType, companyId: row.companyId, companyName: row.companyName, effectiveDate: row.effectiveDate, expireDate: row.expireDate, content: row.content })
  readonly.value = true; dlg.value = true
}

async function submit() {
  if (!formRef.value) return
  try { await formRef.value.validate() } catch { return }
  saving.value = true
  try {
    // companyId 由后端按当前登录药企强制归属本公司,前端不传
    const data = {
      policyType: form.policyType,
      title: form.title,
      content: form.content,
      effectiveDate: form.effectiveDate,
      expireDate: form.expireDate || null
    }
    if (form.id) await updatePolicy(form.id, data)
    else await createPolicy(data)
    ElMessage.success(form.id ? '已更新' : '已发布')
    dlg.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除公告「${row.title}」?`, '删除确认', { type: 'warning' })
  await deletePolicy(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(async () => {
  if (isAdmin.value) {
    try { companies.value = (await listCompanies()) || [] } catch { /* 统一处理 */ }
  }
  load()
})
</script>

<style scoped>
.bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; margin-bottom: 14px; }
.filters { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.tbl { border-radius: var(--rad); border: 1px solid var(--line); }
.t-title { font-weight: 600; color: var(--ink); }
.muted { color: var(--ink-3); }
.pager { margin-top: 14px; justify-content: flex-end; }
.row2 { display: grid; grid-template-columns: 1fr 1fr; gap: 0 16px; }
.dp-co { font-size: 12.5px; color: var(--ink-3); }
</style>
