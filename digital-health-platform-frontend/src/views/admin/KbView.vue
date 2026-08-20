<template>
  <div class="page">
    <el-tabs v-model="tab" class="kb-tabs">
      <!-- ============ 知识文档 ============ -->
      <el-tab-pane label="知识文档" name="docs">
        <el-alert type="info" :closable="false" show-icon class="intro">
          <template #title>知识库已按行级 scope 隔离:公共资料(必备材料 / 医保政策 / 药品 / 药企·机构主数据)全员可见;药企私有公告与网点仅本药企 + 管理员可见;科室仅所属机构 + 管理员可见。提问时按当前用户可见范围先过滤再检索。</template>
        </el-alert>

        <div class="bar">
          <div class="filters">
            <el-input v-model="kw" placeholder="搜标题 / 来源标识" clearable style="width: 210px" />
            <el-select v-model="fType" placeholder="来源" clearable style="width: 140px">
              <el-option v-for="(label, key) in TYPE_LABEL" :key="key" :value="key" :label="label" />
            </el-select>
            <el-select v-model="fScope" placeholder="可见范围" clearable style="width: 140px">
              <el-option value="global" label="全员可见" />
              <el-option value="company" label="药企私有" />
              <el-option value="institution" label="机构私有" />
            </el-select>
            <el-date-picker
              v-model="fRange" type="daterange" value-format="YYYY-MM-DD"
              start-placeholder="导入起" end-placeholder="导入止" style="width: 240px" />
            <el-button @click="resetFilters">重置</el-button>
            <el-button :loading="loading" @click="load">刷新</el-button>
            <span class="cnt">{{ filtered.length === rows.length ? `共 ${rows.length} 篇` : `${filtered.length} / ${rows.length} 篇` }}</span>
          </div>
          <el-button type="primary" :loading="rebuilding" @click="rebuild">
            <span class="ico-rot">↻</span> 从业务数据重建
          </el-button>
        </div>

        <!-- 重建结果统计(重建后显示) -->
        <div v-if="lastStat" class="result">
          <div class="result__sum">
            最近一次重建:清理旧业务文档 <b>{{ lastStat.removedOld }}</b> 篇 · 新建 <b>{{ lastStat.docs }}</b> 篇
          </div>
          <div class="result__grid">
            <div v-for="(v, k) in lastStat.byType" :key="k" class="result__tile">
              <span class="result__tile-n">{{ v }}</span>
              <span class="result__tile-l">{{ k }}</span>
            </div>
          </div>
        </div>

        <el-table v-loading="loading" :data="paged" stripe class="tbl"
                  :empty-text="loading ? '加载中…' : '无符合条件的文档'">
          <el-table-column label="标题" min-width="240">
            <template #default="{ row }"><span class="cell-primary">{{ row.title || '—' }}</span></template>
          </el-table-column>
          <el-table-column label="来源" width="130">
            <template #default="{ row }"><span class="cell-pill">{{ typeLabel(row.sourceType) }}</span></template>
          </el-table-column>
          <el-table-column label="可见范围" min-width="190">
            <template #default="{ row }">
              <el-tag :type="scopeMeta(row.scope).type" size="small" effect="light">{{ scopeMeta(row.scope).t }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="chunkCount" label="切块数" width="90" align="center" />
          <el-table-column label="导入时间" width="170">
            <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button link type="danger" @click="remove(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 客户端分页:KB 文档是有界集合(业务数据派生,几十~低百篇),全量拉取后本地分页即可;
             若日后做大批量导入致文档暴涨,再翻成服务端分页(同 chat-logs) -->
        <el-pagination
          v-if="filtered.length > pageSize"
          small background layout="total, prev, pager, next" :total="filtered.length"
          :page-size="pageSize" v-model:current-page="page" class="pager" />
      </el-tab-pane>

    </el-tabs>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listDocs, deleteDoc, rebuild as rebuildKb } from '../../api/kb'
import { listCompanies } from '../../api/company'
import { listInstitutions } from '../../api/institution'

const tab = ref('docs')

// ---- 知识文档 ----
const loading = ref(false)
const rebuilding = ref(false)
const rows = ref([])
const lastStat = ref(null)

// scope id→名称:COMPANY/INSTITUTION 的 id 解析成真实名称(药企名/机构名),名表拉取失败回退 #id
const companyMap = ref({})
const instMap = ref({})

// 筛选条件:关键词(标题+来源标识)/ 来源类型 / 可见范围类别 / 导入日期区间
const kw = ref('')
const fType = ref('')
const fScope = ref('')
const fRange = ref(null)

// 客户端分页:每页 10 条;任一筛选条件变化回到第 1 页,结果缩到当前页之外时回退到末页
const pageSize = 10
const page = ref(1)

const TYPE_LABEL = {
  manual: '手动导入',
  drug: '药品',
  essential_material: '必备材料',
  company_policy: '政策公告',
  pharma_company: '药企',
  medical_institution: '机构',
  department: '科室',
  sales_location: '网点'
}
const typeLabel = (t) => TYPE_LABEL[t] || (t || '—')

// scope → 中文标签 + el-tag 类型(颜色暗示访问级别:绿=公共 / 橙=药企私有 / 蓝=机构私有)
// COMPANY/INSTITUTION 的 id 优先解析成真实名称(药企 · XX / 机构 · XX),解析不到再回退 #id
const scopeMeta = (scope) => {
  if (!scope || scope === 'GLOBAL') return { t: '全员可见', type: 'success' }
  if (scope.startsWith('COMPANY:')) {
    const id = scope.slice(8)
    const name = companyMap.value[id]
    return { t: name ? `药企 · ${name}` : `药企#${id}`, type: 'warning' }
  }
  if (scope.startsWith('INSTITUTION:')) {
    const id = scope.slice(12)
    const name = instMap.value[id]
    return { t: name ? `机构 · ${name}` : `机构#${id}`, type: 'primary' }
  }
  return { t: scope, type: 'info' }
}
// scope → 筛选用类别(global/company/institution),把带 id 的具体 scope 归并成一档
const scopeCat = (scope) => {
  if (!scope || scope === 'GLOBAL') return 'global'
  if (scope.startsWith('COMPANY:')) return 'company'
  if (scope.startsWith('INSTITUTION:')) return 'institution'
  return 'other'
}
// 取日期前 10 位(YYYY-MM-DD)做区间比较;ISO 日期串比字典序即时间序
const dayPart = (dt) => (dt ? String(dt).slice(0, 10) : '')
// 展示用:去掉 T,截到分钟
const fmt = (dt) => {
  if (!dt) return '—'
  const s = String(dt).replace('T', ' ')
  return s.length > 16 ? s.slice(0, 16) : s
}

const filtered = computed(() => {
  const k = kw.value.trim().toLowerCase()
  const t = fType.value
  const s = fScope.value
  const r = fRange.value
  return rows.value.filter((row) => {
    if (k && !(`${row.title || ''} ${row.sourceRef || ''}`).toLowerCase().includes(k)) return false
    if (t && row.sourceType !== t) return false
    if (s && scopeCat(row.scope) !== s) return false
    if (r && r.length === 2) {
      const d = dayPart(row.createdAt)
      if (d < r[0] || d > r[1]) return false
    }
    return true
  })
})
const paged = computed(() => filtered.value.slice((page.value - 1) * pageSize, page.value * pageSize))

// 任一筛选变化 → 回第 1 页;结果缩水到当前页之外 → 回退末页(不打断浏览)
watch([kw, fType, fScope, fRange], () => { page.value = 1 })
watch(() => filtered.value.length, (len) => {
  const maxPage = Math.max(1, Math.ceil(len / pageSize))
  if (page.value > maxPage) page.value = maxPage
})

function resetFilters() {
  kw.value = ''
  fType.value = ''
  fScope.value = ''
  fRange.value = null
}

async function load() {
  loading.value = true
  try {
    rows.value = (await listDocs()) || []
  } finally {
    loading.value = false
  }
}

// 拉药企/机构名表建 id→name 映射,供 scope 解析;失败不影响列表展示(回退 #id)
async function loadMaps() {
  try {
    const [cs, is] = await Promise.all([listCompanies(), listInstitutions()])
    companyMap.value = Object.fromEntries((cs || []).map((c) => [c.id, c.name]))
    instMap.value = Object.fromEntries((is || []).map((i) => [i.id, i.name]))
  } catch { /* 名表拉取失败忽略,scope 回退 #id */ }
}

async function rebuild() {
  await ElMessageBox.confirm(
    '将从现有业务表(药品 / 必备材料 / 政策公告 / 药企 / 机构 / 科室 / 网点)全量重建:先清除旧业务文档(保留手动导入),再逐表向量化灌库,可能耗时数十秒。确认继续?',
    '从业务数据重建',
    { type: 'warning' }
  )
  rebuilding.value = true
  try {
    const stat = await rebuildKb()
    lastStat.value = stat
    ElMessage.success(`重建完成:新建 ${stat.docs} 篇(清理旧业务文档 ${stat.removedOld} 篇)`)
    await load()
  } finally {
    rebuilding.value = false
  }
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除「${row.title || '该文档'}」?其全部切块与向量将被清除。`, '删除确认', { type: 'warning' })
  await deleteDoc(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(() => { load(); loadMaps() })
</script>

<style scoped>
.kb-tabs { margin-top: -4px; }
.intro { border-radius: var(--rad); }
.bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; margin: 14px 0; }
.filters { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.cnt { font-size: 12.5px; color: var(--ink-3); }
.cell-sub { font-size: 11.5px; color: var(--ink-3); }
.ico-rot { display: inline-block; margin-right: 2px; }
.tbl { border-radius: var(--rad); border: 1px solid var(--line); overflow: hidden; }
.pager { margin-top: 14px; display: flex; justify-content: flex-end; }

/* 重建结果统计 */
.result { display: flex; flex-direction: column; gap: 14px; padding: 16px 18px; border: 1px solid var(--line); border-radius: var(--rad); background: var(--field); }
.result__sum { font-size: 13.5px; color: var(--ink-2); }
.result__sum b { color: var(--indigo); font-weight: 700; }
.result__grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(120px, 1fr)); gap: 10px; }
.result__tile { display: flex; flex-direction: column; gap: 2px; padding: 10px 12px; background: #fff; border: 1px solid var(--line-2); border-radius: 12px; }
.result__tile-n { font-size: 20px; font-weight: 700; color: var(--ink); }
.result__tile-l { font-size: 12px; color: var(--ink-3); }
</style>
