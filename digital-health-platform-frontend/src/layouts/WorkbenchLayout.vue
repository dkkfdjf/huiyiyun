<template>
  <div class="app workbench">
    <RailNav />
    <div class="right">
      <TopBar />
      <main class="content">
        <div v-if="title" class="pagehead"><h1>{{ title }}</h1></div>
        <!-- keep-alive:切回已访问页不重新挂载/重新拉数据(原裸 router-view 每次切换都重跑 onMounted 触发全量请求 + loading 闪烁)。
             max=20 兜底(全角色页面数 < 20,实际全部缓存);列表筛选/分页/弹窗/选中 tab 等组件内状态一并保留。 -->
        <router-view v-slot="{ Component }">
          <keep-alive :max="20">
            <component :is="Component" />
          </keep-alive>
        </router-view>
      </main>
    </div>
    <!-- 悬浮 AI 知识助手:对所有登录角色开放(/ask 后端按 scope 行级隔离;无效提问前置拦截省 key;多轮记忆)-->
    <KbAssistant />
  </div>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import RailNav from '../components/RailNav.vue'
import TopBar from '../components/TopBar.vue'
import KbAssistant from '../components/KbAssistant.vue'
import { useNotificationStore } from '../stores/notification'
import { useUserStore } from '../stores/user'

// 顶栏铃铛未读数:可见时每 60s 轮询,切到后台/最小化即停(避免空转与无谓请求);回到可见立即刷新。
const notif = useNotificationStore()
const isGuest = useUserStore().isGuest   // 游客无通知体系(无账号),跳过轮询以免 403 刷屏
let notifTimer = null
function startPolling() { notif.loadUnread(); notifTimer = setInterval(() => notif.loadUnread(), 60000) }
function stopPolling() { if (notifTimer) { clearInterval(notifTimer); notifTimer = null } }
function onVisibility() { document.hidden ? stopPolling() : startPolling() }
onMounted(() => {
  if (isGuest) return   // 游客不轮询通知
  startPolling(); document.addEventListener('visibilitychange', onVisibility)
})
onBeforeUnmount(() => { stopPolling(); document.removeEventListener('visibilitychange', onVisibility) })

// 列表页标题(按路由名映射)。各角色首页自带 pagehead,故不在表内
const TITLES = {
  'admin-institutions': '机构管理',
  'admin-departments': '科室管理',
  'admin-doctors': '医师管理',
  'admin-demands': '临床反馈',
  'admin-policies': '公告管理',
  'admin-materials': '必备材料',
  'admin-users': '账号管理',
  'admin-logs': '审计日志',
  'admin-kb': '知识库',
  'company-drugs': '药品管理',
  'company-stocks': '库存管理',
  'company-inventory': '流向追溯',
  'company-locations': '销售网点',
  'company-demands': '临床反馈',
  'company-policies': '公告管理',
  'doctor-demands': '我的反馈',
  'doctor-policies': '药企公告',
  'doctor-materials': '必备材料',
  'institution-departments': '科室管理',
  'institution-doctors': '本院医师',
  'institution-demands': '临床反馈',
  'institution-announce': '医师公告',
  'institution-drugs': '可售药品',
  'institution-policies': '药企公告',
  'institution-materials': '必备材料',
  'guest-companies': '药企',
  'guest-institutions': '医疗机构',
  'guest-departments': '科室',
  'guest-doctors': '医师',
  'guest-locations': '销售网点',
  'guest-materials': '必备材料',
  'guest-policies': '政策公告'
}
const route = useRoute()
const title = computed(() => TITLES[route.name])
</script>

<style scoped>
/* 工作台令牌(取自 preview/app-dashboard-v6.html) */
.workbench {
  --bg: #f3f5fa;
  --field: #f7f9fc;
  --ink: #161b2e;
  --ink-2: #525c73;
  --ink-3: #8b93a6;
  --ink-4: #b8becc;
  --line: #e2e7f2;
  --line-2: #edf0f7;
  --indigo-soft: #eef0fe;
  --indigo-glow: rgba(99, 102, 241, 0.12);
  --rad: 16px;
  --sh: 0 14px 30px -20px rgba(20, 26, 46, 0.22);
  --sh-sm: 0 6px 16px -10px rgba(20, 26, 46, 0.14);
}
.app {
  display: grid;
  grid-template-columns: 86px 1fr;
  min-height: 100vh;
  background: var(--bg);
}
.right {
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.content {
  padding: 22px 28px 40px;
  display: flex;
  flex-direction: column;
  gap: 18px;
  width: 100%;
}
/* 列表页标题:左侧靛蓝小竖条 + H1,与 RailNav 选中态语言一致 */
.pagehead { display: flex; align-items: center; gap: 10px; }
.pagehead::before {
  content: '';
  width: 4px;
  height: 18px;
  border-radius: 2px;
  background: var(--indigo);
}
.pagehead h1 {
  font-size: 19px;
  font-weight: 700;
  color: var(--ink);
  letter-spacing: -0.01em;
  line-height: 1.2;
}
@media (max-width: 1040px) {
  .app { grid-template-columns: 68px 1fr; }
  .pagehead h1 { font-size: 17px; }
}
</style>
