<template>
  <div class="page">
    <div class="legend">
      <span><b>异常IP</b>:该账号有成功登录历史、却首次从此 IP 登录(疑似异地/换设备)→ 红色标记</span>
      <span><b>敏感操作</b>:命中「用户」模块的写操作,或操作名含 删除/停用/禁用/重置/密码/权限/角色/解锁/锁定/驳回/审核 → 红色标记</span>
      <span><b>知识库对话</b>:记录每次提问(提问 / 作答 / 命中来源数 / 异常标记);异常规则——无效提问、越界提问(过校验却被 AI 判无关而拒答)、0 命中、高频(同用户 10 分钟 >10 次)、重复(同用户同问 10 分钟 ≥3 次)→ 红色标记</span>
    </div>
    <el-tabs v-model="tab" class="tabs" @tab-change="onTabChange">
      <!-- 登录日志 -->
      <el-tab-pane label="登录日志" name="login">
        <div class="bar">
          <div class="filters">
            <el-input v-model="q1.username" placeholder="登录名" clearable style="width: 150px" @keyup.enter="search1" />
            <el-select v-model="q1.loginResult" placeholder="结果" clearable style="width: 110px" @change="search1">
              <el-option :value="1" label="成功" />
              <el-option :value="0" label="失败" />
            </el-select>
            <el-select v-model="q1.anomaly" placeholder="异常IP" clearable style="width: 120px" @change="search1">
              <el-option :value="1" label="仅异常IP" />
            </el-select>
            <el-select v-model="q1.guestOnly" placeholder="来源" clearable style="width: 120px" @change="search1">
              <el-option :value="1" label="仅游客" />
            </el-select>
            <el-date-picker
              v-model="q1.range" type="daterange" value-format="YYYY-MM-DD"
              start-placeholder="开始" end-placeholder="结束" style="width: 240px" @change="search1"
            />
            <el-button type="primary" @click="search1">查询</el-button>
            <el-button link type="info" @click="reset1">重置</el-button>
          </div>
        </div>
        <el-table v-loading="loading1" :data="rows1" stripe :row-class-name="loginRowClass" class="tbl">
          <el-table-column prop="username" label="登录名" min-width="110" show-overflow-tooltip />
          <el-table-column label="结果" width="90">
            <template #default="{ row }">
              <StatusDot :tone="row.loginResult === 1 ? 'green' : 'red'" :label="row.loginResult === 1 ? '成功' : '失败'" />
            </template>
          </el-table-column>
          <el-table-column label="异常IP" width="110">
            <template #default="{ row }">
              <el-tooltip v-if="row.anomaly === 1" :content="row.anomalyReason || '异常IP'" placement="top">
                <el-tag type="warning" size="small" effect="plain">⚠ 异常</el-tag>
              </el-tooltip>
              <span v-else class="lvl-norm">正常</span>
            </template>
          </el-table-column>
          <el-table-column prop="failReason" label="失败原因" min-width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ row.failReason || '—' }}</template>
          </el-table-column>
          <el-table-column prop="ip" label="IP" width="140" show-overflow-tooltip />
          <el-table-column prop="userAgent" label="User-Agent" min-width="200" show-overflow-tooltip />
          <el-table-column prop="loginTime" label="登录时间" width="170" />
        </el-table>
        <el-pagination background layout="total, prev, pager, next" :total="total1" :page-size="q1.pageSize"
          :current-page="q1.pageNum" class="pg" @current-change="(n) => { q1.pageNum = n; loadLogin() }" />
      </el-tab-pane>

      <!-- 操作日志 -->
      <el-tab-pane label="操作日志" name="operation">
        <div class="bar">
          <div class="filters">
            <el-input v-model="q2.username" placeholder="登录名" clearable style="width: 150px" @keyup.enter="search2" />
            <el-select v-model="q2.module" placeholder="模块" clearable style="width: 150px" @change="search2">
              <el-option v-for="m in MODULES" :key="m" :value="m" :label="m" />
            </el-select>
            <el-select v-model="q2.sensitive" placeholder="敏感/常规" clearable style="width: 140px" @change="search2">
              <el-option :value="1" label="敏感操作" />
              <el-option :value="0" label="常规操作" />
            </el-select>
            <el-date-picker
              v-model="q2.range" type="daterange" value-format="YYYY-MM-DD"
              start-placeholder="开始" end-placeholder="结束" style="width: 240px" @change="search2"
            />
            <el-button type="primary" @click="search2">查询</el-button>
            <el-button link type="info" @click="reset2">重置</el-button>
          </div>
        </div>
        <el-table v-loading="loading2" :data="rows2" stripe :row-class-name="opRowClass" class="tbl">
          <el-table-column label="级别" width="80">
            <template #default="{ row }">
              <el-tag v-if="row.sensitive === 1" type="danger" size="small" effect="plain">敏感</el-tag>
              <span v-else class="lvl-norm">普通</span>
            </template>
          </el-table-column>
          <el-table-column prop="username" label="登录名" min-width="110" show-overflow-tooltip />
          <el-table-column prop="module" label="模块" width="120" show-overflow-tooltip />
          <el-table-column prop="operation" label="操作" min-width="150" show-overflow-tooltip />
          <el-table-column label="详情" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ row.requestParam || '—' }}</template>
          </el-table-column>
          <el-table-column label="耗时" width="90">
            <template #default="{ row }">{{ row.costTime != null ? row.costTime + ' ms' : '—' }}</template>
          </el-table-column>
          <el-table-column label="IP" width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.ip || '—' }}</template>
          </el-table-column>
          <el-table-column prop="operationTime" label="操作时间" width="170" />
        </el-table>
        <el-pagination background layout="total, prev, pager, next" :total="total2" :page-size="q2.pageSize"
          :current-page="q2.pageNum" class="pg" @current-change="(n) => { q2.pageNum = n; loadOperation() }" />
      </el-tab-pane>

      <!-- 知识库对话日志(对话日志纳入平台治理→审计日志统一查阅;数据来自 kb_chat_log) -->
      <el-tab-pane label="知识库对话" name="kbchat">
        <div class="bar">
          <div class="filters">
            <el-input v-model="q3.username" placeholder="登录名" clearable style="width: 150px" @keyup.enter="search3" />
            <el-select v-model="q3.flagged" placeholder="全部" clearable style="width: 120px" @change="search3">
              <el-option :value="1" label="仅异常" />
              <el-option :value="0" label="仅正常" />
            </el-select>
            <el-date-picker
              v-model="q3.range" type="daterange" value-format="YYYY-MM-DD"
              start-placeholder="开始" end-placeholder="结束" style="width: 240px" @change="search3"
            />
            <el-button type="primary" @click="search3">查询</el-button>
            <el-button link type="info" @click="reset3">重置</el-button>
          </div>
        </div>
        <el-table v-loading="loading3" :data="rows3" stripe class="tbl">
          <el-table-column label="时间" width="160">
            <template #default="{ row }">{{ fmtKbTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="用户" min-width="140">
            <template #default="{ row }">
              <div>{{ row.username || (row.userId ? '用户#' + row.userId : (row.role === 4 ? '游客' : '匿名')) }}</div>
              <div class="lvl-norm">{{ kbRole(row.role) }}{{ row.role === 4 && row.clientIp ? ' · ' + row.clientIp : '' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="提问" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ row.queryText }}</template>
          </el-table-column>
          <el-table-column label="作答" min-width="280" show-overflow-tooltip>
            <template #default="{ row }">{{ row.answerText || '—' }}</template>
          </el-table-column>
          <el-table-column prop="hitCount" label="命中" width="70" align="center" />
          <el-table-column label="标记" width="130" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.flagged" type="danger" size="small" effect="dark">{{ row.flagReason || '异常' }}</el-tag>
              <span v-else class="lvl-norm">正常</span>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination background layout="total, prev, pager, next" :total="total3" :page-size="q3.pageSize"
          :current-page="q3.pageNum" class="pg" @current-change="(n) => { q3.pageNum = n; loadKbChat() }" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { pageLoginLogs, pageOperationLogs } from '../../api/log'
import { pageChatLogs } from '../../api/kb'
import StatusDot from '../../components/StatusDot.vue'

const tab = ref('login')

/* —— 登录日志 —— */
const loading1 = ref(false)
const rows1 = ref([])
const total1 = ref(0)
const q1 = reactive({ username: '', loginResult: undefined, anomaly: undefined, guestOnly: undefined, range: null, pageNum: 1, pageSize: 10 })

async function loadLogin() {
  loading1.value = true
  try {
    const r = await pageLoginLogs({
      username: q1.guestOnly ? '游客' : (q1.username || undefined), loginResult: q1.loginResult, anomaly: q1.anomaly,
      start: q1.range?.[0], end: q1.range?.[1], pageNum: q1.pageNum, pageSize: q1.pageSize
    })
    rows1.value = r.records || []
    total1.value = r.total || 0
  } finally {
    loading1.value = false
  }
}
function search1() { q1.pageNum = 1; loadLogin() }
function reset1() { q1.username = ''; q1.loginResult = undefined; q1.anomaly = undefined; q1.guestOnly = undefined; q1.range = null; q1.pageNum = 1; loadLogin() }
// 异常IP行高亮(复用操作日志的 row-sens 红色调)
const loginRowClass = ({ row }) => (row.anomaly === 1 ? 'row-sens' : '')

/* —— 操作日志 —— */
const loading2 = ref(false)
const rows2 = ref([])
const total2 = ref(0)
// 操作日志模块下拉(与后端 @OperationLog(module=...) 取值对齐,免得用户手输)
const MODULES = ['临床反馈', '医师', '医疗机构', '城市', '库存', '材料', '用户', '科室', '站内通知', '缓存监控', '网点', '药企', '药企政策', '药品', '知识库', '认证', '系统']
const q2 = reactive({ username: '', module: '', sensitive: undefined, range: null, pageNum: 1, pageSize: 10 })
let opLoaded = false

async function loadOperation() {
  loading2.value = true
  try {
    const r = await pageOperationLogs({
      username: q2.username || undefined, module: q2.module || undefined,
      sensitive: q2.sensitive,
      start: q2.range?.[0], end: q2.range?.[1], pageNum: q2.pageNum, pageSize: q2.pageSize
    })
    rows2.value = r.records || []
    total2.value = r.total || 0
  } finally {
    loading2.value = false
  }
}
function search2() { q2.pageNum = 1; loadOperation() }
function reset2() { q2.username = ''; q2.module = ''; q2.sensitive = undefined; q2.range = null; q2.pageNum = 1; loadOperation() }
// 敏感行高亮(红色调),一眼区分
const opRowClass = ({ row }) => (row.sensitive === 1 ? 'row-sens' : '')

// 切到操作日志 / 知识库对话时懒加载一次
function onTabChange(name) {
  if (name === 'operation' && !opLoaded) { opLoaded = true; loadOperation() }
  if (name === 'kbchat' && !kbLoaded) { kbLoaded = true; loadKbChat() }
}

/* —— 知识库对话日志(对话日志纳入平台治理→审计日志统一查阅)—— */
const loading3 = ref(false)
const rows3 = ref([])
const total3 = ref(0)
const q3 = reactive({ username: '', flagged: undefined, range: null, pageNum: 1, pageSize: 10 })
let kbLoaded = false
const KB_ROLE = { 0: '管理员', 1: '药企', 2: '机构', 3: '医师', 4: '游客' }
const kbRole = (r) => KB_ROLE[r] || ''
const fmtKbTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '—')

async function loadKbChat() {
  loading3.value = true
  try {
    const r = await pageChatLogs({
      username: q3.username || undefined, flagged: q3.flagged,
      start: q3.range?.[0], end: q3.range?.[1], pageNum: q3.pageNum, pageSize: q3.pageSize
    })
    rows3.value = r.records || []
    total3.value = r.total || 0
  } finally {
    loading3.value = false
  }
}
function search3() { q3.pageNum = 1; loadKbChat() }
function reset3() { q3.username = ''; q3.flagged = undefined; q3.range = null; q3.pageNum = 1; loadKbChat() }

onMounted(() => loadLogin())
</script>

<style scoped>
.tabs { border-radius: var(--rad); }
.legend { display: flex; gap: 12px 26px; flex-wrap: wrap; font-size: 12px; color: var(--ink-3); line-height: 1.7; margin-bottom: 10px; padding: 8px 14px; background: var(--surface-2, var(--surface)); border: 1px solid var(--line); border-radius: 10px; }
.legend b { color: var(--red); font-weight: 600; margin-right: 2px; }
.bar { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 8px; }
.filters { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.tbl { border-radius: var(--rad); border: 1px solid var(--line); overflow: hidden; }
.pg { margin-top: 4px; justify-content: flex-end; }
.lvl-norm { font-size: 12px; color: var(--ink-3); }
/* 敏感/异常行:操作日志与登录日志表现完全一致 —— 统一红底 + 左侧一条红竖条。
   ① 用 td.el-table__cell 覆盖到单元格级,盖过 stripe 的条纹灰底,保证条纹行/普通行都红;
   ② 左红条只画在首列(每个 td 都画会冒出多条竖线),形成一条干净连续的竖条;
   ③ 行内的 敏感/异常 标签用 plain 描边而非 dark 实心,红色观感来自"行"而非单个色块,消除"有的整行红、有的只有线"的错觉。 */
:deep(.el-table .row-sens td.el-table__cell) { background: rgba(220, 42, 69, 0.13) !important; }
:deep(.el-table .row-sens td.el-table__cell:first-child) { box-shadow: inset 4px 0 0 var(--red); }
</style>
