<template>
  <div class="page">
    <div class="pagehead">
      <div>
        <h1>销售网点总览</h1>
        <div class="sub">全部药企的定点销售网点分布 · 地图标注</div>
      </div>
    </div>

    <div class="bar">
      <div class="filters">
        <el-select v-model="companyId" placeholder="全部药企" clearable style="width: 200px">
          <el-option v-for="c in companies" :key="c.id" :value="c.id" :label="c.name" />
        </el-select>
        <el-select v-model="cityId" placeholder="全部城市" clearable style="width: 160px">
          <el-option v-for="c in cities" :key="c.id" :value="c.id" :label="c.name" />
        </el-select>
        <span class="hint">共 {{ filtered.length }} 个网点</span>
      </div>
    </div>

    <!-- 网点全国分布地图(无 key 自动降级为占位,与公司侧一致) -->
    <LocationMap ref="mapRef" :points="mapPoints" :height="420" class="overview" />

    <el-table v-loading="loading" :data="filtered" stripe :row-class-name="rowClass" class="tbl">
      <el-table-column label="网点名称" min-width="130">
        <template #default="{ row }"><span class="loc-name" @click="locate(row)" title="点击在地图定位">{{ row.name }}</span></template>
      </el-table-column>
      <el-table-column label="所属药企" min-width="150">
        <template #default="{ row }"><span class="cell-primary">{{ companyMap.get(row.companyId) || '—' }}</span></template>
      </el-table-column>
      <el-table-column label="城市" width="100">
        <template #default="{ row }">{{ cityMap.get(row.cityId) || '—' }}</template>
      </el-table-column>
      <el-table-column prop="address" label="地址" min-width="220" show-overflow-tooltip />
      <el-table-column label="经纬度" width="200">
        <template #default="{ row }">{{ fmtCoord(row) }}</template>
      </el-table-column>
      <el-table-column prop="contactPerson" label="联系人" width="100" />
      <el-table-column prop="contactPhone" label="电话" width="130" />
    </el-table>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { listLocations } from '../../api/location'
import { listCities } from '../../api/city'
import { listCompanies } from '../../api/company'
import LocationMap from '../../components/LocationMap.vue'

const loading = ref(false)
const rows = ref([])
const cities = ref([])
const companies = ref([])
const cityId = ref(undefined)
const companyId = ref(undefined)

const cityMap = computed(() => new Map(cities.value.map((c) => [c.id, c.name])))
const companyMap = computed(() => new Map(companies.value.map((c) => [c.id, c.name])))
const filtered = computed(() =>
  rows.value.filter((r) => (!cityId.value || r.cityId === cityId.value) && (!companyId.value || r.companyId === companyId.value))
)
const mapPoints = computed(() => filtered.value.map((r) => ({ name: r.name, longitude: r.longitude, latitude: r.latitude })))
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
  r.longitude != null && r.latitude != null ? `${Number(r.longitude).toFixed(6)}, ${Number(r.latitude).toFixed(6)}` : '—'

onMounted(async () => {
  loading.value = true
  try {
    const safe = (p) => p.then((v) => v).catch(() => [])
    const [locs, cs, cos] = await Promise.all([safe(listLocations()), safe(listCities()), safe(listCompanies())])
    rows.value = locs || []
    cities.value = cs || []
    companies.value = cos || []
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.pagehead { margin-bottom: 14px; }
.pagehead h1 { font-family: var(--font-d); font-weight: 800; font-size: 24px; letter-spacing: -0.02em; }
.pagehead .sub { font-family: var(--font-m); font-size: 12.5px; color: var(--ink-3); margin-top: 5px; }
.bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 14px; flex-wrap: wrap; }
.filters { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.hint { font-family: var(--font-m); font-size: 12.5px; color: var(--ink-3); }
.overview { margin-bottom: 14px; }
.tbl { border-radius: var(--rad); border: 1px solid var(--line); overflow: hidden; }
.cell-primary { font-weight: 600; color: var(--ink); }
.loc-name { cursor: pointer; transition: color 0.15s; }
.loc-name:hover { color: var(--indigo); }
:deep(.el-table .row-loc td) { background: var(--indigo-soft) !important; }
</style>
