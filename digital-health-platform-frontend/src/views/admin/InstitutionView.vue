<template>
  <div class="page">
    <div class="bar">
      <div class="filters">
        <el-input v-model="q.name" placeholder="机构名称" clearable style="width: 180px" @keyup.enter="onSearch" />
        <el-select v-model="q.cityId" placeholder="城市" clearable style="width: 140px" @change="onSearch">
          <el-option v-for="c in cities" :key="c.id" :value="c.id" :label="c.name" />
        </el-select>
        <el-select v-model="q.auditStatus" placeholder="状态" clearable style="width: 120px" @change="onSearch">
          <el-option :value="0" label="待审核" />
          <el-option :value="1" label="正常" />
          <el-option :value="2" label="已驳回" />
          <el-option :value="3" label="停用" />
        </el-select>
        <el-button type="primary" @click="onSearch">查询</el-button>
        <el-button link type="info" @click="onReset">重置</el-button>
      </div>
      <el-button v-if="!isGuest" type="primary" @click="openCreate">+ 新增机构</el-button>
    </div>

    <!-- 地图在上(全量机构,琥珀环标记);点下方列表"机构名称"→放大定位到该院并高亮 -->
    <LocationMap ref="mapRef" :points="mapPoints" :height="420" tone="inst" unit="医院" class="overview" />

    <el-table v-loading="loading" :data="rows" stripe :row-class-name="rowClass" class="tbl">
      <el-table-column label="机构名称" min-width="150">
        <template #default="{ row }"><span class="cell-primary loc-name" @click="locate(row)" title="点击在地图定位">{{ row.name }}</span></template>
      </el-table-column>
      <el-table-column label="城市" width="100">
        <template #default="{ row }">{{ cityMap.get(row.cityId) || '—' }}</template>
      </el-table-column>
      <el-table-column prop="address" label="地址" min-width="200" show-overflow-tooltip />
      <el-table-column label="经纬度" width="200">
        <template #default="{ row }">{{ fmtCoord(row) }}</template>
      </el-table-column>
      <el-table-column prop="contactPerson" label="联系人" width="100" />
      <el-table-column prop="contactPhone" label="电话" width="130" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <StatusDot :tone="auditMeta(row.auditStatus).tone" :label="auditMeta(row.auditStatus).t" />
        </template>
      </el-table-column>
      <el-table-column v-if="!isGuest" label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link :type="row.auditStatus === 1 ? 'warning' : 'success'" @click="toggle(row)">
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

    <el-dialog v-model="dlg" :title="form.id ? '编辑机构' : '新增机构'" width="640px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-form-item label="机构名称" prop="name"><el-input v-model="form.name" maxlength="50" /></el-form-item>
        <el-form-item label="城市" prop="cityId">
          <el-select v-model="form.cityId" placeholder="选择城市" style="width: 100%">
            <el-option v-for="c in cities" :key="c.id" :value="c.id" :label="c.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="地址" prop="address"><el-input v-model="form.address" maxlength="100" /></el-form-item>
        <el-form-item label="定位" prop="longitude">
          <MapPicker v-model:longitude="form.longitude" v-model:latitude="form.latitude" />
        </el-form-item>
        <el-form-item v-if="form.id" label="联系人"><el-input v-model="form.contactPerson" maxlength="30" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.contactPhone" maxlength="20" /></el-form-item>
        <template v-if="!form.id">
          <el-divider content-position="left">登录账号(新建机构同时开通)</el-divider>
          <el-form-item label="用户名" prop="username"><el-input v-model="form.username" maxlength="50" placeholder="登录用" /></el-form-item>
          <el-form-item label="密码" prop="password"><el-input v-model="form.password" type="password" show-password maxlength="50" autocomplete="new-password" /></el-form-item>
          <el-form-item label="姓名" prop="realName"><el-input v-model="form.realName" maxlength="30" placeholder="同时作为联系人" /></el-form-item>
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
import { listInstitutions, pageInstitutions, createInstitution, updateInstitution, toggleInstitutionStatus, deleteInstitution } from '../../api/institution'
import { listCities } from '../../api/city'
import MapPicker from '../../components/MapPicker.vue'
import LocationMap from '../../components/LocationMap.vue'
import StatusDot from '../../components/StatusDot.vue'
import { useUserStore } from '../../stores/user'

const isGuest = useUserStore().isGuest
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const q = reactive({ name: '', cityId: undefined, auditStatus: undefined, pageNum: 1, pageSize: 10 })

const cities = ref([])
const cityMap = computed(() => new Map(cities.value.map((c) => [c.id, c.name])))

// audit_status:0待审核 / 1正常 / 2驳回 / 3停用(与后端 MedicalInstitution 实体对齐)
const AUDIT = {
  0: { t: '待审核', tone: 'amber' },
  1: { t: '正常', tone: 'green' },
  2: { t: '已驳回', tone: 'red' },
  3: { t: '停用', tone: 'gray' }
}
const auditMeta = (s) => AUDIT[s] || { t: '—', tone: 'gray' }

const fmtCoord = (r) =>
  r.longitude != null && r.latitude != null
    ? `${Number(r.longitude).toFixed(6)}, ${Number(r.latitude).toFixed(6)}`
    : '—'

// 地图复用全量机构坐标(琥珀环标记);列表分页,点"机构名称"可放大定位到该院
const allInsts = ref([])
const mapPoints = computed(() => allInsts.value.map((i) => ({ name: i.name, longitude: i.longitude, latitude: i.latitude, tone: 'inst' })))
async function ensureInsts() {
  if (!allInsts.value.length) {
    try { allInsts.value = (await listInstitutions()) || [] } catch { /* 统一处理 */ }
  }
}

const mapRef = ref(null)
const activeId = ref(null)
function locate(row) {
  if (row.longitude == null || row.latitude == null) return
  activeId.value = row.id
  mapRef.value?.focus(row.longitude, row.latitude, { name: row.name, tone: 'inst' })
}
const rowClass = ({ row }) => (row.id === activeId.value ? 'row-loc' : '')

async function load() {
  loading.value = true
  try {
    const r = await pageInstitutions({
      name: q.name || undefined, cityId: q.cityId || undefined, auditStatus: q.auditStatus,
      pageNum: q.pageNum, pageSize: q.pageSize
    })
    rows.value = r.records || []
    total.value = r.total || 0
  } finally {
    loading.value = false
  }
}
function onSearch() { q.pageNum = 1; load() }
function onReset() { q.name = ''; q.cityId = undefined; q.auditStatus = undefined; q.pageNum = 1; load() }
function onPage(n) { q.pageNum = n; load() }

/* —— 新增 / 编辑 —— */
const dlg = ref(false)
const saving = ref(false)
const formRef = ref(null)
const form = reactive({
  id: null, name: '', address: '', cityId: undefined,
  longitude: undefined, latitude: undefined, contactPerson: '', contactPhone: '',
  username: '', password: '', realName: ''
})
// 新增时才校验登录账号(编辑不碰账号)
const rules = computed(() => {
  const base = {
    name: [{ required: true, message: '请输入机构名称', trigger: 'blur' }],
    cityId: [{ required: true, message: '请选择城市', trigger: 'change' }],
    address: [{ required: true, message: '请输入地址', trigger: 'blur' }],
    longitude: [{ required: true, message: '请取点或填写经度', trigger: 'change' }],
    latitude: [{ required: true, message: '请取点或填写纬度', trigger: 'change' }]
  }
  if (!form.id) {
    base.username = [{ required: true, message: '请输入登录用户名', trigger: 'blur' }]
    base.password = [{ required: true, message: '请输入登录密码', trigger: 'blur' }]
    base.realName = [{ required: true, message: '请输入账号姓名(同时作为联系人)', trigger: 'blur' }]
  }
  return base
})

function openCreate() {
  Object.assign(form, {
    id: null, name: '', address: '', cityId: undefined,
    longitude: undefined, latitude: undefined, contactPerson: '', contactPhone: '',
    username: '', password: '', realName: ''
  })
  dlg.value = true
  refreshReferenceData()
}
function openEdit(row) {
  Object.assign(form, {
    id: row.id, name: row.name, address: row.address, cityId: row.cityId,
    longitude: row.longitude, latitude: row.latitude,
    contactPerson: row.contactPerson, contactPhone: row.contactPhone
  })
  dlg.value = true
  refreshReferenceData()  // 刷新城市列表以显示新增的城市
}

/** 刷新参考数据（城市），确保显示新增的数据 */
async function refreshReferenceData() {
  try {
    cities.value = (await listCities()) || []
  } catch { /* 忽略错误 */ }
}

async function submit() {
  if (!formRef.value) return
  try { await formRef.value.validate() } catch { return }
  saving.value = true
  try {
    const { id, ...data } = form
    if (id) await updateInstitution(id, data)
    else await createInstitution(data)
    ElMessage.success(id ? '已更新' : '已新增')
    dlg.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function toggle(row) {
  const target = row.auditStatus === 1 ? 3 : 1
  await toggleInstitutionStatus(row.id, target)
  ElMessage.success(target === 1 ? '已启用' : '已停用')
  load()
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除机构「${row.name}」?若其下存在科室或医师将无法删除。`, '删除确认', { type: 'warning' })
  await deleteInstitution(row.id)
  ElMessage.success('已删除')
  // 删除后若当前页已清空且非首页,回退一页,避免停在空页
  if (rows.value.length <= 1 && q.pageNum > 1) q.pageNum--
  load()
}

onMounted(async () => {
  try { cities.value = (await listCities()) || [] } catch { /* 401/失败由 request 统一处理 */ }
  ensureInsts()   // 地图用全量机构坐标;与分页列表并行加载
  load()
})
</script>

<style scoped>
.bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.filters { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.overview { margin-bottom: 14px; }
.tbl { border-radius: var(--rad); border: 1px solid var(--line); overflow: hidden; }
.pg { margin-top: 4px; justify-content: flex-end; }
.loc-name { cursor: pointer; transition: color 0.15s; }
.loc-name:hover { color: var(--indigo); }
:deep(.el-table .row-loc td) { background: var(--indigo-soft) !important; }
</style>
