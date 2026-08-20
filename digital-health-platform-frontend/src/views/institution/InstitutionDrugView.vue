<template>
  <div class="page">
    <div class="bar">
      <div class="filters">
        <el-input v-model="q.name" placeholder="药品名称" clearable style="width: 220px" @keyup.enter="search" @clear="search" />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button link type="info" @click="onReset">重置</el-button>
      </div>
      <span class="hint">仅浏览医药公司在售药品目录(只读)</span>
    </div>

    <el-table v-loading="loading" :data="rows" stripe class="tbl">
      <el-table-column label="药品名称" min-width="180">
        <template #default="{ row }"><span class="cell-primary">{{ row.name }}</span></template>
      </el-table-column>
      <el-table-column prop="specification" label="规格" min-width="140" show-overflow-tooltip />
      <el-table-column prop="dosageForm" label="剂型" width="100" />
      <el-table-column prop="unit" label="单位" width="80" />
      <el-table-column prop="producer" label="生产企业" min-width="160" show-overflow-tooltip />
      <el-table-column prop="approvalNo" label="批准文号" width="150" show-overflow-tooltip />
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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { pageInstitutionDrugs } from '../../api/institution'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const q = reactive({ name: '', pageNum: 1, pageSize: 10 })

async function load() {
  loading.value = true
  try {
    const res = await pageInstitutionDrugs({ name: q.name, pageNum: q.pageNum, pageSize: q.pageSize })
    rows.value = res?.records || []
    total.value = res?.total || 0
  } finally {
    loading.value = false
  }
}
function search() { q.pageNum = 1; load() }
function onReset() { q.name = ''; q.pageNum = 1; load() }
function onPage(p) { q.pageNum = p; load() }

onMounted(load)
</script>

<style scoped>
.bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; margin-bottom: 14px; }
.filters { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.hint { font-size: 12.5px; color: var(--ink-3); }
.tbl { border-radius: var(--rad); border: 1px solid var(--line); }
.pager { margin-top: 14px; justify-content: flex-end; }
</style>
