<template>
  <div class="home" v-loading="loading">
    <div class="pagehead">
      <div>
        <span class="kick"><i></i>医师工作台</span>
        <h1>{{ greet }}</h1>
        <div class="sub">{{ today }}</div>
      </div>
    </div>

    <!-- KPI:计数动画 + 色调 -->
    <div class="kpis">
      <KpiCard label="待处理反馈" :value="pending" unit="条" tone="out" :delta="pending ? '待提交跟进' : '暂无待办'" :delta-tone="pending ? 'down' : 'up'" />
      <KpiCard label="跟进中" :value="processing" unit="条" tone="net" delta="药企处置中" delta-tone="flat" />
      <KpiCard label="已满足" :value="satisfied" unit="条" tone="in" :delta="satisfied ? '已闭环' : '尚无'" :delta-tone="satisfied ? 'up' : 'flat'" />
    </div>

    <!-- 近期反馈时间线(全宽):各状态计数已在上方 KPI 卡呈现,不再重复画条(原 CoverageBars 像"进度条"且「待处理」被填满易误读为"完成") -->
    <ActivityTimeline title="近期反馈" :items="timeline" more="全部反馈 ›" @more="router.push('/doctor/demands')" />

    <!-- 我的信息(M5:自助维护联系方式)+ 最新必备材料公告 -->
    <div class="row c11">
      <div class="panel">
        <div class="panel__hd"><h3>我的信息</h3>
          <el-button link type="primary" @click="openProfile">编辑联系方式</el-button>
        </div>
        <div class="info">
          <div class="info__r"><span class="info__k">姓名</span><span class="info__v">{{ me.name || '—' }}</span></div>
          <div class="info__r"><span class="info__k">登录名</span><span class="info__v">{{ user.username || '—' }}</span></div>
          <div class="info__r"><span class="info__k">归属机构</span><span class="info__v">{{ me.institutionName || '—' }}</span></div>
          <div class="info__r"><span class="info__k">职称</span><span class="info__v">{{ me.title || '—' }}</span></div>
          <div class="info__r"><span class="info__k">电话</span><span class="info__v">{{ me.phone || '—' }}</span></div>
          <div class="info__r"><span class="info__k">邮箱</span><span class="info__v">{{ me.email || '—' }}</span></div>
        </div>
      </div>
      <div class="panel">
        <div class="panel__hd"><h3>最新必备材料</h3>
          <el-button link type="primary" @click="router.push('/doctor/materials')">全部材料</el-button>
        </div>
        <div class="bullets">
          <router-link v-for="m in materials" :key="m.id" to="/doctor/materials" class="bullet">
            <span class="bullet__cat">{{ m.category }}</span>
            <span class="bullet__name">{{ m.name }}</span>
            <span class="bullet__arr">›</span>
          </router-link>
          <div v-if="!materials.length" class="empty">暂无材料</div>
        </div>
      </div>
    </div>

    <!-- 提交反馈 -->
    <div class="panel">
      <div class="panel__bd">
        <router-link to="/doctor/demands" class="action-link">
          <span class="action-link__ic" v-html="SVG.edit" />
          <div class="action-link__body">
            <div class="action-link__t">临床用药反馈</div>
            <div class="action-link__d">提交用药需求 / 用量反馈 · 跟进药企处置结果</div>
          </div>
          <span class="action-link__arr">→</span>
        </router-link>
      </div>
    </div>

    <!-- 编辑联系方式 -->
    <el-dialog v-model="profileDlg" title="编辑联系方式" width="460px">
      <el-form :model="profileForm" label-width="72px">
        <el-form-item label="电话"><el-input v-model="profileForm.phone" maxlength="20" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="profileForm.email" maxlength="60" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="profileDlg = false">取消</el-button>
        <el-button type="primary" :loading="profileSaving" @click="submitProfile">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onActivated } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { pageDemands, DEMAND_STATUS } from '../../api/demand'
import { getDoctorMe, updateDoctorMe } from '../../api/doctor'
import { pageMaterials } from '../../api/material'
import { useUserStore } from '../../stores/user'
import { todayLine } from '../../utils/format'
import KpiCard from '../../components/KpiCard.vue'
import ActivityTimeline from '../../components/ActivityTimeline.vue'

const user = useUserStore()
const router = useRouter()
const today = todayLine()
const greet = computed(() => `${user.realName || user.username || '你好'}`)

const loading = ref(false)
const pending = ref(0)
const processing = ref(0)
const satisfied = ref(0)
const recent = ref([])

/* —— 我的信息 + 最新材料(M5)—— */
const me = ref({})
const materials = ref([])
const profileDlg = ref(false)
const profileSaving = ref(false)
const profileForm = reactive({ phone: '', email: '' })

function openProfile() {
  profileForm.phone = me.value.phone || ''
  profileForm.email = me.value.email || ''
  profileDlg.value = true
}
async function submitProfile() {
  profileSaving.value = true
  try {
    await updateDoctorMe(profileForm)
    ElMessage.success('已保存')
    profileDlg.value = false
    me.value = await getDoctorMe()
  } finally {
    profileSaving.value = false
  }
}

const SVG = {
  edit: '<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M14 5.5 18.5 10 8 20.5l-4.5.5.5-4.5z"/><path d="M12.5 7 17 11.5"/></svg>'
}

const dotOf = (s) => ({ 0: 'amber', 1: '', 2: 'green', 3: 'red' }[s] ?? '')
const labelOf = (s) => DEMAND_STATUS?.[s]?.label || '—'

const timeline = computed(() =>
  recent.value.map((r) => ({
    dot: dotOf(r.status),
    title: r.drugName || '未命名药品',
    sub: (r.createTime || '').slice(0, 10),
    action: labelOf(r.status)
  }))
)

async function loadHome() {
  loading.value = true
  const safe = (p) => p.then((v) => v).catch(() => null)
  const [p, pr, s, list, meData, mats] = await Promise.all([
    safe(pageDemands({ status: 0, pageNum: 1, pageSize: 1 })),
    safe(pageDemands({ status: 1, pageNum: 1, pageSize: 1 })),
    safe(pageDemands({ status: 2, pageNum: 1, pageSize: 1 })),
    safe(pageDemands({ pageNum: 1, pageSize: 6 })),
    safe(getDoctorMe()),
    safe(pageMaterials({ pageNum: 1, pageSize: 5 }))
  ])
  pending.value = p?.total || 0
  processing.value = pr?.total || 0
  satisfied.value = s?.total || 0
  recent.value = list?.records || []
  me.value = meData || {}
  materials.value = mats?.records || []
  loading.value = false
}

// keep-alive 切回时刷新;首次由 onMounted 加载,跳过避免双重请求
let firstActivation = true
onMounted(() => { loadHome() })
onActivated(() => {
  if (firstActivation) { firstActivation = false; return }
  loadHome()
})
</script>

<style scoped>
.pagehead { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.kick { display: inline-flex; align-items: center; gap: 8px; font-size: 12px; color: var(--indigo); background: var(--indigo-glow); padding: 5px 12px; border-radius: 999px; margin-bottom: 10px; }
.kick i { width: 6px; height: 6px; border-radius: 50%; background: var(--indigo-hi); }
.pagehead h1 { font-family: var(--font-d); font-weight: 800; font-size: 26px; letter-spacing: -0.02em; }
.pagehead .sub { font-family: var(--font-m); font-size: 12.5px; color: var(--ink-3); margin-top: 5px; }

.kpis { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
.row { display: grid; gap: 18px; }
.row.c11 { grid-template-columns: 1fr 1fr; }

/* 面板(我的信息 / 最新材料) */
.panel { background: var(--surface); border: 1px solid var(--line); border-radius: var(--rad); box-shadow: var(--sh-sm); }
.panel__hd { display: flex; align-items: center; justify-content: space-between; padding: 15px 20px; border-bottom: 1px solid var(--line-2); }
.panel__hd h3 { font-family: var(--font-d); font-weight: 700; font-size: 15px; }
.info { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px 24px; padding: 16px 20px; }
.info__r { display: flex; align-items: baseline; gap: 10px; min-width: 0; }
.info__k { font-family: var(--font-m); font-size: 12px; color: var(--ink-3); flex: none; white-space: nowrap; }
.info__v { font-size: 13.5px; color: var(--ink); word-break: break-all; }
.bullets { padding: 10px 18px 16px; }
.bullet { display: flex; align-items: center; gap: 10px; padding: 9px 4px; border-bottom: 1px solid var(--line-2); text-decoration: none; color: inherit; border-radius: 6px; transition: background 0.16s; }
.bullet:last-child { border-bottom: none; }
.bullet:hover { background: var(--bg-hover); }
.bullet__arr { margin-left: auto; color: var(--ink-3); }
.bullet__cat { font-family: var(--font-m); font-size: 11px; color: var(--indigo); background: var(--indigo-soft); border-radius: 6px; padding: 2px 8px; flex: none; }
.bullet__name { font-size: 13px; color: var(--ink); }
.empty { font-family: var(--font-m); font-size: 12.5px; color: var(--ink-3); padding: 18px 4px; }

.panel__bd { padding: 4px 0; }
.action-link {
  display: flex; align-items: center; gap: 16px; text-decoration: none; color: inherit;
  padding: 16px 20px; border-radius: var(--rad); transition: background 0.16s;
}
.action-link:hover { background: var(--bg-hover); }
.action-link__ic {
  width: 42px; height: 42px; border-radius: 10px; background: var(--indigo-soft);
  display: grid; place-items: center; color: var(--indigo); flex: none;
}
.action-link__body { flex: 1; }
.action-link__t { font-family: var(--font-d); font-weight: 700; font-size: 15px; color: var(--ink); }
.action-link__d { font-size: 13px; color: var(--ink-3); margin-top: 3px; font-family: var(--font-m); }
.action-link__arr { font-size: 18px; color: var(--ink-3); font-weight: 400; }

@media (max-width: 1040px) { .kpis { grid-template-columns: 1fr; } .row.c73, .row.c11 { grid-template-columns: 1fr; } }
@media (max-width: 560px) { .cta { flex-wrap: wrap; } .cta__arr { width: 100%; } }
</style>
