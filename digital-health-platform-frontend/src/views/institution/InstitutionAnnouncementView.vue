<template>
  <div class="page">
    <!-- 撰写 -->
    <div class="panel compose">
      <div class="panel__hd"><h3>发布医师公告</h3>
        <span class="recip">将通知本院 <b>{{ doctorCount }}</b> 名在职医师</span>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="72px" class="form">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="如:系统升级通知 / 例会安排" />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="5"
            maxlength="1000"
            show-word-limit
            placeholder="公告正文,发送后即时投递到每位本院医师的站内通知。"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="sending" @click="submit">发送公告</el-button>
          <el-button @click="resetForm">清空</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 历史 -->
    <div class="panel hist">
      <div class="panel__hd"><h3>已发公告</h3></div>
      <el-table v-loading="loading" :data="rows" stripe class="tbl" empty-text="暂无已发公告">
        <el-table-column label="标题" min-width="200">
          <template #default="{ row }"><span class="cell-primary">{{ stripPrefix(row.title) }}</span></template>
        </el-table-column>
        <el-table-column prop="content" label="内容" min-width="200" show-overflow-tooltip />
        <el-table-column label="投递人数" width="100" align="right" header-align="right">
          <template #default="{ row }"><b class="cnt">{{ row.recipientCount }}</b> 人</template>
        </el-table-column>
        <el-table-column label="发送时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.sendTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 详情(只读结构化:标题/投递人数/发送时间/全文) -->
    <el-dialog v-model="detailDlg" title="公告详情" width="560px">
      <el-descriptions v-if="detail" :column="2" border>
        <el-descriptions-item label="标题" :span="2">{{ stripPrefix(detail.title) }}</el-descriptions-item>
        <el-descriptions-item label="投递人数">{{ detail.recipientCount }} 人</el-descriptions-item>
        <el-descriptions-item label="发送时间">{{ fmtTime(detail.sendTime) }}</el-descriptions-item>
        <el-descriptions-item label="正文" :span="2">
          <div class="detail-body">{{ detail.content || '—' }}</div>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { sendAnnouncement, listAnnouncements, institutionHome } from '../../api/institution'

const loading = ref(false)
const rows = ref([])
const doctorCount = ref(0)

/* 详情(只读结构化) */
const detailDlg = ref(false)
const detail = ref(null)
function openDetail(row) { detail.value = row; detailDlg.value = true }

const formRef = ref(null)
const sending = ref(false)
const form = reactive({ title: '', content: '' })
const rules = {
  title: [{ required: true, message: '请输入公告标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入公告内容', trigger: 'blur' }]
}

// 后端为区分来源给标题加了"机构名 · 公告:"前缀,历史展示时剥掉更清爽
function stripPrefix(t) {
  if (!t) return '—'
  const idx = t.indexOf(':')
  return idx > 0 ? t.slice(idx + 1) : t
}
function fmtTime(t) {
  if (!t) return '—'
  return String(t).replace('T', ' ').slice(0, 16)
}

async function load() {
  loading.value = true
  try {
    rows.value = (await listAnnouncements()) || []
  } finally { loading.value = false }
}

async function submit() {
  if (!formRef.value) return
  try { await formRef.value.validate() } catch { return }
  sending.value = true
  try {
    const n = await sendAnnouncement({ title: form.title, content: form.content })
    ElMessage.success(`已发送给 ${n} 名本院医师`)
    resetForm()
    load()
  } finally { sending.value = false }
}
function resetForm() {
  form.title = ''
  form.content = ''
  formRef.value?.clearValidate()
}

onMounted(async () => {
  try { doctorCount.value = (await institutionHome())?.doctorCount ?? 0 } catch { /* 统一处理 */ }
  load()
})
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 16px; }
.panel { background: var(--surface); border: 1px solid var(--line); border-radius: var(--rad); box-shadow: var(--sh-sm); }
.panel__hd { display: flex; align-items: center; justify-content: space-between; padding: 15px 20px; border-bottom: 1px solid var(--line-2); }
.panel__hd h3 { font-family: var(--font-d); font-weight: 700; font-size: 15px; }
.recip { font-size: 12.5px; color: var(--ink-3); }
.recip b { color: var(--indigo); font-family: var(--font-m); }
.form { padding: 18px 20px 4px; }
.tbl { border-radius: 0 0 var(--rad) var(--rad); }
.cnt { font-family: var(--font-m); color: var(--indigo); }
.detail-body { white-space: pre-wrap; word-break: break-word; font-size: 13px; color: var(--ink); line-height: 1.7; }
</style>
