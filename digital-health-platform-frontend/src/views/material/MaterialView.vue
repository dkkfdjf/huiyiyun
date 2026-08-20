<template>
  <div class="page">
    <div class="bar">
      <div class="filters">
        <el-input v-model="q.name" placeholder="标题关键字" clearable style="width: 200px" @keyup.enter="search" @clear="search" />
        <el-select v-model="q.category" placeholder="类别" clearable style="width: 160px" @change="search">
          <el-option v-for="c in MATERIAL_CATEGORIES" :key="c" :value="c" :label="c" />
        </el-select>
        <el-button @click="search">查询</el-button>
        <el-button link type="info" @click="onReset">重置</el-button>
      </div>
      <el-button v-if="isAdmin" type="primary" @click="openCreate">+ 新增材料</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" stripe class="tbl">
      <el-table-column prop="name" label="标题" min-width="180">
        <template #default="{ row }"><span class="t-name">{{ row.name }}</span></template>
      </el-table-column>
      <el-table-column label="类别" width="130">
        <template #default="{ row }"><el-tag size="small">{{ row.category }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="content" label="携带资料说明" min-width="320" show-overflow-tooltip />
      <el-table-column prop="updateTime" label="更新时间" width="170" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openView(row)">详情</el-button>
          <template v-if="isAdmin">
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
    <el-dialog v-model="dlg" :title="dlgTitle" width="620px">
      <!-- 详情:结构化只读面板 -->
      <DetailPanel v-if="readonly" :title="form.name" :meta="materialMeta" body-label="携带资料说明">
        <template #tags>
          <el-tag size="small">{{ form.category }}</el-tag>
        </template>
        {{ form.content || '（无）' }}
      </DetailPanel>
      <!-- 新增 / 编辑:表单 -->
      <el-form v-else ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="标题" prop="name"><el-input v-model="form.name" maxlength="50" show-word-limit /></el-form-item>
        <el-form-item label="类别" prop="category">
          <el-select v-model="form.category" placeholder="选择或输入类别" filterable allow-create default-first-option style="width: 100%">
            <el-option v-for="c in MATERIAL_CATEGORIES" :key="c" :value="c" :label="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="携带资料说明">
          <el-input v-model="form.content" type="textarea" :rows="6" maxlength="2000" show-word-limit placeholder="如:门诊发票、合作医疗证历本(或病历)" />
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
import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageMaterials, createMaterial, updateMaterial, deleteMaterial, MATERIAL_CATEGORIES } from '../../api/material'
import { useUserStore, ROLE } from '../../stores/user'
import DetailPanel from '../../components/DetailPanel.vue'

const user = useUserStore()
const isAdmin = computed(() => user.role === ROLE.ADMIN)

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const q = reactive({ name: '', category: undefined, pageNum: 1, pageSize: 10 })

async function load() {
  loading.value = true
  try {
    const res = await pageMaterials(q)
    rows.value = res?.records || []
    total.value = res?.total || 0
  } finally {
    loading.value = false
  }
}
function search() { q.pageNum = 1; load() }
function onReset() { q.name = ''; q.category = undefined; q.pageNum = 1; load() }
function onPage(p) { q.pageNum = p; load() }

/* —— 详情 / 新增 / 编辑 —— */
const dlg = ref(false)
const saving = ref(false)
const readonly = ref(false)
const formRef = ref(null)
const form = reactive({ id: null, name: '', category: '', content: '', updateTime: '' })
const dlgTitle = computed(() => readonly.value ? '材料详情' : form.id ? '编辑材料' : '新增材料')
const materialMeta = computed(() => [
  { label: '类别', value: form.category },
  { label: '更新时间', value: form.updateTime }
])
const rules = {
  name: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  category: [{ required: true, message: '请选择或输入类别', trigger: 'change' }]
}

function reset() {
  Object.assign(form, { id: null, name: '', category: '', content: '', updateTime: '' })
}
function openCreate() { reset(); readonly.value = false; dlg.value = true }
function openEdit(row) {
  Object.assign(form, { id: row.id, name: row.name, category: row.category, content: row.content, updateTime: row.updateTime })
  readonly.value = false; dlg.value = true
}
function openView(row) {
  Object.assign(form, { id: row.id, name: row.name, category: row.category, content: row.content, updateTime: row.updateTime })
  readonly.value = true; dlg.value = true
}

async function submit() {
  if (!formRef.value) return
  try { await formRef.value.validate() } catch { return }
  saving.value = true
  try {
    const data = {
      name: form.name,
      category: form.category,
      content: form.content || null
    }
    if (form.id) await updateMaterial(form.id, data)
    else await createMaterial(data)
    ElMessage.success(form.id ? '已更新' : '已新增')
    dlg.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除材料「${row.name}」?`, '删除确认', { type: 'warning' })
  await deleteMaterial(row.id)
  ElMessage.success('已删除')
  load()
}

load()
</script>

<style scoped>
.bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; margin-bottom: 14px; }
.filters { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.tbl { border-radius: var(--rad); border: 1px solid var(--line); }
.t-name { font-weight: 600; color: var(--ink); }
.pager { margin-top: 14px; justify-content: flex-end; }
</style>
