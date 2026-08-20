<template>
  <div class="page">
    <div class="bar">
      <div class="filters">
        <el-input v-model="q.name" placeholder="网点名称" clearable style="width: 180px" @keyup.enter="onSearch" />
        <el-select v-model="q.cityId" placeholder="城市" clearable style="width: 140px" @change="onSearch">
          <el-option v-for="c in cities" :key="c.id" :value="c.id" :label="c.name" />
        </el-select>
        <el-button type="primary" @click="onSearch">查询</el-button>
        <el-button link type="info" @click="onReset">重置</el-button>
      </div>
      <el-button type="primary" @click="openCreate">+ 新增网点</el-button>
    </div>

    <!-- 网点分布总览(无 key 自动降级为占位) -->
    <LocationMap ref="mapRef" :points="mapPoints" :height="360" class="overview" />

    <el-table v-loading="loading" :data="rows" stripe :row-class-name="rowClass" class="tbl">
      <el-table-column label="网点名称" min-width="130">
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
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
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

    <!-- 新增 / 编辑 dialog -->
    <el-dialog v-model="dlg" :title="form.id ? '编辑网点' : '新增网点'" width="640px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-form-item label="网点名称" prop="name"><el-input v-model="form.name" maxlength="50" /></el-form-item>
        <el-form-item label="省份" prop="province">
          <el-select v-model="form.province" filterable allow-create default-first-option
            placeholder="选择或输入省份(新城市建档必填)" style="width: 100%">
            <el-option v-for="p in provinces" :key="p" :value="p" :label="p" />
          </el-select>
        </el-form-item>
        <el-form-item label="城市" prop="cityName">
          <el-select v-model="form.cityName" filterable allow-create default-first-option
            placeholder="选择或输入城市(新城市自动建档)" style="width: 100%">
            <el-option v-for="c in filteredCities" :key="c.id" :value="c.name" :label="c.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="地址" prop="address"><el-input v-model="form.address" maxlength="100" /></el-form-item>
        <el-form-item label="定位" prop="longitude">
          <MapPicker v-model:longitude="form.longitude" v-model:latitude="form.latitude" />
        </el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contactPerson" maxlength="30" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.contactPhone" maxlength="20" /></el-form-item>
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
import { pageLocations, createLocation, updateLocation, deleteLocation } from '../../api/location'
import { listCities } from '../../api/city'
import LocationMap from '../../components/LocationMap.vue'
import MapPicker from '../../components/MapPicker.vue'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const q = reactive({ name: '', cityId: undefined, pageNum: 1, pageSize: 10 })

const cities = ref([])
const cityMap = computed(() => new Map(cities.value.map((c) => [c.id, c.name])))
// 省份下拉:从已有城市去重(无需硬编码省份字典);选了省份后城市下拉联动过滤该省
const provinces = computed(() => [...new Set(cities.value.map((c) => c.province).filter(Boolean))])
const filteredCities = computed(() => (form.province ? cities.value.filter((c) => c.province === form.province) : cities.value))
const mapPoints = computed(() => rows.value.map((r) => ({ name: r.name, longitude: r.longitude, latitude: r.latitude })))
const mapRef = ref(null)
const activeId = ref(null)
// 点列表"网点名称"→地图放大定位到该点,并高亮该行
function locate(row) {
  if (row.longitude == null || row.latitude == null) return
  activeId.value = row.id
  mapRef.value?.focus(row.longitude, row.latitude, { name: row.name })
}
const rowClass = ({ row }) => (row.id === activeId.value ? 'row-loc' : '')

const fmtCoord = (r) =>
  r.longitude != null && r.latitude != null
    ? `${Number(r.longitude).toFixed(6)}, ${Number(r.latitude).toFixed(6)}`
    : '—'

async function load() {
  loading.value = true
  try {
    const r = await pageLocations({
      name: q.name || undefined, cityId: q.cityId || undefined,
      pageNum: q.pageNum, pageSize: q.pageSize
    })
    rows.value = r.records || []
    total.value = r.total || 0
  } finally {
    loading.value = false
  }
}
function onSearch() { q.pageNum = 1; load() }
function onReset() { q.name = ''; q.cityId = undefined; q.pageNum = 1; load() }
function onPage(n) { q.pageNum = n; load() }

/* —— 新增 / 编辑 —— */
const dlg = ref(false)
const saving = ref(false)
const formRef = ref(null)
const form = reactive({
  id: null, name: '', address: '', province: '', cityName: '',
  longitude: undefined, latitude: undefined, contactPerson: '', contactPhone: ''
})
const rules = {
  name: [{ required: true, message: '请输入网点名称', trigger: 'blur' }],
  province: [{ required: true, message: '请选择或输入省份', trigger: 'change' }],
  cityName: [{ required: true, message: '请选择或输入城市', trigger: 'change' }],
  address: [{ required: true, message: '请输入地址', trigger: 'blur' }],
  longitude: [{ required: true, message: '请取点或填写经度', trigger: 'change' }],
  latitude: [{ required: true, message: '请取点或填写纬度', trigger: 'change' }]
}

function openCreate() {
  Object.assign(form, {
    id: null, name: '', address: '', province: '', cityName: '',
    longitude: undefined, latitude: undefined, contactPerson: '', contactPhone: ''
  })
  dlg.value = true
  refreshReferenceData()
}
function openEdit(row) {
  const city = cities.value.find((c) => c.id === row.cityId)
  Object.assign(form, {
    id: row.id, name: row.name, address: row.address,
    province: city?.province || '', cityName: cityMap.value.get(row.cityId) || '',
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
    if (id) await updateLocation(id, data)
    else await createLocation(data)
    ElMessage.success(id ? '已更新' : '已新增')
    dlg.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除网点「${row.name}」? 删除后仅标记为不可用，历史记录保留。`, '删除确认', { type: 'warning' })
  await deleteLocation(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(async () => {
  try { cities.value = (await listCities()) || [] } catch { /* 401/失败由 request 统一处理 */ }
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
