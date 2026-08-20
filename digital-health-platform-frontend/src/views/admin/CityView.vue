<template>
  <div class="page">
    <div class="pagehead">
      <div>
        <h1>城市与覆盖</h1>
        <div class="sub">每座城市的网点 / 机构 / 药企 / 销售足迹,并维护城市基础数据</div>
      </div>
    </div>

    <!-- 覆盖 KPI -->
    <div class="kpis">
      <KpiCard label="城市总数" :value="rows.length" unit="座" />
      <KpiCard label="有网点城市" :value="citiesWithLoc" unit="座" tone="in" />
      <KpiCard label="有机构城市" :value="citiesWithInst" unit="座" tone="net" />
      <KpiCard label="累计销售额" :value="totalSales" prefix="¥" />
    </div>

    <!-- 城市销售额分布:真实量级(累计销售额),横向条形 Top 8;无销售数据时不渲染 -->
    <ChartCard
      v-if="salesTop.length"
      class="sales-chart"
      title="城市销售额分布"
      more="累计 · Top 8"
      :option="salesOption"
      :height="chartHeight"
    />

    <!-- 城市维护:网点/机构分布属小计数,原 CoverageBars 像"进度条"且与下方表格重复,故移除;分布改由表格可排序列承载 -->
    <div class="bar">
      <div class="filters">
        <el-input v-model="q.name" placeholder="城市 / 省份" clearable style="width: 200px" @keyup.enter="onSearch" />
        <el-button type="primary" @click="onSearch">查询</el-button>
        <el-button link type="info" @click="onReset">重置</el-button>
      </div>
      <span class="city-hint">城市由网点录入自动建档,此处可维护省份/区划或删除</span>
    </div>

    <el-table v-loading="loading" :data="filtered" stripe class="tbl">
      <el-table-column prop="name" label="城市" min-width="100" />
      <el-table-column prop="province" label="省份" width="110" />
      <el-table-column prop="regionCode" label="区划代码" width="110" />
      <el-table-column label="网点数" width="90" align="right">
        <template #default="{ row }"><b :class="{ zero: !row.locationCount }">{{ row.locationCount }}</b></template>
      </el-table-column>
      <el-table-column label="机构数" width="90" align="right">
        <template #default="{ row }"><b :class="{ zero: !row.institutionCount }">{{ row.institutionCount }}</b></template>
      </el-table-column>
      <el-table-column label="覆盖药企" width="100" align="right">
        <template #default="{ row }">{{ row.companyCount }}</template>
      </el-table-column>
      <el-table-column label="累计销售额" width="150" align="right">
        <template #default="{ row }"><span class="amt">¥{{ fmt(row.salesAmount) }}</span></template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dlg" :title="form.id ? '编辑城市' : '新增城市'" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-form-item label="城市名称" prop="name"><el-input v-model="form.name" maxlength="50" /></el-form-item>
        <el-form-item label="省份" prop="province"><el-input v-model="form.province" maxlength="50" /></el-form-item>
        <el-form-item label="区划代码"><el-input v-model="form.regionCode" maxlength="20" /></el-form-item>
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
import { cityCoverage, createCity, updateCity, deleteCity } from '../../api/city'
import KpiCard from '../../components/KpiCard.vue'
import ChartCard from '../../components/ChartCard.vue'

const loading = ref(false)
const rows = ref([]) // CityCoverageVO[]
const q = reactive({ name: '' })
// 城市为基础数据,数量少,客户端过滤即可
const filtered = computed(() => {
  const k = q.name.trim()
  return k ? rows.value.filter((c) => c.name.includes(k) || (c.province || '').includes(k)) : rows.value
})

const citiesWithLoc = computed(() => rows.value.filter((c) => c.locationCount > 0).length)
const citiesWithInst = computed(() => rows.value.filter((c) => c.institutionCount > 0).length)
const totalSales = computed(() => rows.value.reduce((s, c) => s + Number(c.salesAmount || 0), 0))
const fmt = (v) => Number(v || 0).toLocaleString('zh-CN', { maximumFractionDigits: 0 })

/* —— 城市销售额分布(横向条形 Top 8):销售额是真实量级,非小计数,适合条形对比 —— */
const salesTop = computed(() =>
  [...rows.value]
    .map((c) => ({ name: c.name, v: Number(c.salesAmount) || 0 }))
    .filter((c) => c.v > 0)
    .sort((a, b) => b.v - a.v)
    .slice(0, 8)
)
const chartHeight = computed(() => Math.max(150, salesTop.value.length * 36 + 40))
const salesOption = computed(() => {
  const top = salesTop.value
  const names = top.map((c) => c.name).slice().reverse()   // 反转使最大值显示在顶部
  const vals = top.map((c) => c.v).slice().reverse()
  return {
    grid: { left: 10, right: 78, top: 14, bottom: 14, containLabel: true },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, valueFormatter: (v) => '¥' + Number(v || 0).toLocaleString('zh-CN') },
    xAxis: {
      type: 'value',
      axisLine: { show: false }, axisTick: { show: false },
      splitLine: { lineStyle: { color: '#EDF0F7' } },
      axisLabel: { color: '#8B93A6', fontFamily: 'DM Mono', fontSize: 10, formatter: (v) => (v >= 10000 ? (v / 10000).toFixed(v >= 100000 ? 0 : 1) + '万' : v) }
    },
    yAxis: {
      type: 'category', data: names,
      axisLine: { lineStyle: { color: '#E2E7F2' } }, axisTick: { show: false },
      axisLabel: { color: '#475063', fontSize: 12.5 }
    },
    series: [{
      type: 'bar', data: vals, barMaxWidth: 14,
      itemStyle: { borderRadius: [0, 5, 5, 0], color: { type: 'linear', x: 0, y: 0, x2: 1, y2: 0, colorStops: [{ offset: 0, color: '#6366f1' }, { offset: 1, color: '#4338ca' }] } },
      label: { show: true, position: 'right', color: '#475063', fontFamily: 'DM Mono', fontSize: 11, formatter: (p) => '¥' + Number(p.value).toLocaleString('zh-CN') }
    }]
  }
})

async function load() {
  loading.value = true
  try { rows.value = (await cityCoverage()) || [] } finally { loading.value = false }
}
function onSearch() { /* 客户端过滤,computed 即时生效 */ }
function onReset() { q.name = '' }

const dlg = ref(false)
const saving = ref(false)
const formRef = ref(null)
const form = reactive({ id: null, name: '', province: '', regionCode: '' })
const rules = {
  name: [{ required: true, message: '请输入城市名称', trigger: 'blur' }],
  province: [{ required: true, message: '请输入所属省份', trigger: 'blur' }]
}
function openCreate() {
  Object.assign(form, { id: null, name: '', province: '', regionCode: '' })
  dlg.value = true
}
function openEdit(row) {
  Object.assign(form, { id: row.id, name: row.name, province: row.province, regionCode: row.regionCode || '' })
  dlg.value = true
}
async function submit() {
  if (!formRef.value) return
  try { await formRef.value.validate() } catch { return }
  saving.value = true
  try {
    const { id, ...data } = form
    if (id) await updateCity(id, data)
    else await createCity(data)
    ElMessage.success(id ? '已更新' : '已新增')
    dlg.value = false
    load()
  } finally {
    saving.value = false
  }
}
async function remove(row) {
  await ElMessageBox.confirm(`确认删除城市「${row.name}」?若其下存在机构或网点将无法删除。`, '删除确认', { type: 'warning' })
  await deleteCity(row.id)
  ElMessage.success('已删除')
  load()
}
onMounted(() => load())
</script>

<style scoped>
.pagehead { margin-bottom: 14px; }
.pagehead h1 { font-family: var(--font-d); font-weight: 800; font-size: 24px; letter-spacing: -0.02em; }
.pagehead .sub { font-family: var(--font-m); font-size: 12.5px; color: var(--ink-3); margin-top: 5px; }
.kpis { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 16px; }
.sales-chart { margin-bottom: 16px; }
.bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; margin-bottom: 12px; }
.filters { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.tbl { border-radius: var(--rad); border: 1px solid var(--line); overflow: hidden; }
.amt { font-family: var(--font-m); font-weight: 600; color: var(--indigo); }
b.zero { color: var(--ink-3); font-weight: 500; }
.city-hint { font-family: var(--font-m); font-size: 12.5px; color: var(--ink-3); }
@media (max-width: 1040px) { .kpis { grid-template-columns: repeat(2, 1fr); } }
</style>
