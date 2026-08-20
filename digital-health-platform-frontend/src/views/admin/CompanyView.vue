<template>
  <div class="page">
    <div class="bar">
      <div class="filters">
        <el-input v-model="q.name" placeholder="公司名称" clearable style="width: 180px" @keyup.enter="onSearch" />
        <el-select v-model="q.auditStatus" placeholder="状态" clearable style="width: 120px" @change="onSearch">
          <el-option :value="1" label="正常" />
          <el-option :value="3" label="停用" />
        </el-select>
        <el-button type="primary" @click="onSearch">查询</el-button>
        <el-button link type="info" @click="onReset">重置</el-button>
      </div>
      <el-button v-if="!isGuest" type="primary" @click="openCreate">+ 新增药企</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" stripe class="tbl">
      <el-table-column label="公司名称" min-width="150">
        <template #default="{ row }"><span class="cell-primary">{{ row.name }}</span></template>
      </el-table-column>
      <el-table-column prop="creditCode" label="信用代码" width="150" />
      <el-table-column prop="contactPerson" label="联系人" width="100" />
      <el-table-column prop="contactPhone" label="电话" width="130" />
      <el-table-column prop="address" label="地址" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <StatusDot :tone="auditMeta(row.auditStatus).tone" :label="auditMeta(row.auditStatus).t" />
        </template>
      </el-table-column>
      <el-table-column v-if="!isGuest" label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button
            link
            :type="row.auditStatus === 1 ? 'warning' : 'success'"
            @click="toggle(row)"
          >
            {{ row.auditStatus === 1 ? '停用' : '启用' }}
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
    <el-dialog v-model="dlg" :title="form.id ? '编辑药企' : '新增药企'" width="600px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="公司名称" prop="name"><el-input v-model="form.name" maxlength="50" autocomplete="off" /></el-form-item>
        <el-form-item label="信用代码" prop="creditCode"><el-input v-model="form.creditCode" maxlength="50" autocomplete="off" /></el-form-item>
        <el-form-item label="许可证号"><el-input v-model="form.licenseNo" maxlength="50" autocomplete="off" /></el-form-item>
        <el-form-item v-if="form.id" label="联系人"><el-input v-model="form.contactPerson" maxlength="30" autocomplete="off" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.contactPhone" maxlength="20" autocomplete="off" /></el-form-item>
        <el-form-item label="地址"><el-input v-model="form.address" maxlength="100" autocomplete="off" /></el-form-item>
        <template v-if="!form.id">
          <el-divider content-position="left">登录账号(新建药企同时开通)</el-divider>
          <el-form-item label="用户名" prop="username"><el-input v-model="form.username" maxlength="50" placeholder="登录用" autocomplete="off" /></el-form-item>
          <el-form-item label="密码" prop="password"><el-input v-model="form.password" type="password" show-password maxlength="50" autocomplete="new-password" /></el-form-item>
          <el-form-item label="姓名" prop="realName"><el-input v-model="form.realName" maxlength="30" placeholder="同时作为联系人" autocomplete="off" /></el-form-item>
        </template>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageCompanies, createCompany, updateCompany, toggleCompanyStatus, deleteCompany } from '../../api/company'
import StatusDot from '../../components/StatusDot.vue'
import { useUserStore } from '../../stores/user'

const isGuest = useUserStore().isGuest
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const q = reactive({ name: '', auditStatus: undefined, pageNum: 1, pageSize: 10 })

const AUDIT = {
  1: { t: '正常', tone: 'green' },
  3: { t: '停用', tone: 'gray' }
}
const auditMeta = (s) => AUDIT[s] || { t: '—', type: 'info' }

async function load() {
  loading.value = true
  try {
    const r = await pageCompanies({ name: q.name || undefined, auditStatus: q.auditStatus, pageNum: q.pageNum, pageSize: q.pageSize })
    rows.value = r.records || []
    total.value = r.total || 0
  } finally {
    loading.value = false
  }
}
function onSearch() { q.pageNum = 1; load() }
function onReset() { q.name = ''; q.auditStatus = undefined; q.pageNum = 1; load() }
function onPage(n) { q.pageNum = n; load() }

/* —— 新增 / 编辑 —— */
const dlg = ref(false)
const saving = ref(false)
const formRef = ref(null)
const form = reactive({ id: null, name: '', creditCode: '', licenseNo: '', contactPerson: '', contactPhone: '', address: '', username: '', password: '', realName: '' })
// 新增时才校验登录账号(编辑不碰账号)
const rules = computed(() => {
  const base = {
    name: [{ required: true, message: '请输入公司名称', trigger: 'blur' }],
    creditCode: [{ required: true, message: '请输入统一社会信用代码', trigger: 'blur' }]
  }
  if (!form.id) {
    base.username = [{ required: true, message: '请输入登录用户名', trigger: 'blur' }]
    base.password = [{ required: true, message: '请输入登录密码', trigger: 'blur' }]
    base.realName = [{ required: true, message: '请输入账号姓名(同时作为联系人)', trigger: 'blur' }]
  }
  return base
})
function openCreate() {
  Object.assign(form, { id: null, name: '', creditCode: '', licenseNo: '', contactPerson: '', contactPhone: '', address: '', username: '', password: '', realName: '' })
  dlg.value = true
}
function openEdit(row) {
  Object.assign(form, {
    id: row.id, name: row.name, creditCode: row.creditCode, licenseNo: row.licenseNo || '',
    contactPerson: row.contactPerson || '', contactPhone: row.contactPhone || '', address: row.address || ''
  })
  dlg.value = true
}
async function submit() {
  if (!formRef.value) return
  try { await formRef.value.validate() } catch { return }
  saving.value = true
  try {
    const { id, ...data } = form
    if (id) await updateCompany(id, data)
    else await createCompany(data)
    ElMessage.success(id ? '已更新' : '已新增')
    dlg.value = false
    load()
  } finally {
    saving.value = false
  }
}

/* —— 启停(正常↔停用) —— */
async function toggle(row) {
  const target = row.auditStatus === 1 ? 3 : 1
  const word = target === 1 ? '启用' : '停用'
  await ElMessageBox.confirm(
    `确认${word}药企「${row.name}」?${target === 3 ? '停用后其账号将无法登录。' : ''}`,
    `${word}确认`, { type: 'warning' }
  )
  await toggleCompanyStatus(row.id, target)
  ElMessage.success(`已${word}`)
  load()
}

/* —— 删除 —— */
async function remove(row) {
  await ElMessageBox.confirm(`确认删除药企「${row.name}」?若其下存在药品/网点/账号将无法删除。`, '删除确认', { type: 'warning' })
  await deleteCompany(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(() => load())
</script>

<style scoped>
.bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.filters { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.tbl { border-radius: var(--rad); border: 1px solid var(--line); overflow: hidden; }
.pg { margin-top: 4px; justify-content: flex-end; }
</style>
