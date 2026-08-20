<template>
  <header class="top">
    <div class="top__spacer"></div>
    <span class="pill"><i></i>实时 · <span class="mono">{{ clock }}</span></span>

    <button v-if="!store.isGuest" class="iconbtn" title="通知中心" @click="openNotif">
      <el-badge :value="notif.unread" :hidden="!notif.unread" :max="99">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M6 9a6 6 0 0 1 12 0c0 5 2 6 2 6H4s2-1 2-6z" /><path d="M10 20a2 2 0 0 0 4 0" /></svg>
      </el-badge>
    </button>

    <el-dropdown trigger="click" @command="onCmd">
      <div class="who" title="账号">
        <span class="who__av">{{ avatarText }}</span>
        <span class="who__t">{{ store.realName || store.username || '用户' }}<small>{{ store.roleLabel }}</small></span>
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M6 9l6 6 6-6" /></svg>
      </div>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="home">返回首页</el-dropdown-item>
          <el-dropdown-item v-if="!store.isGuest" command="password">修改密码</el-dropdown-item>
          <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>

    <!-- 修改密码(所有角色自助) -->
    <el-dialog v-model="pwdDlg" title="修改密码" width="420px" :close-on-click-modal="false" append-to-body>
      <el-form :model="pwdForm" label-width="88px" @submit.prevent>
        <el-form-item label="旧密码">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password autocomplete="current-password" placeholder="请输入当前密码" />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="pwdForm.newPassword" type="password" show-password autocomplete="new-password" placeholder="6~50 位" />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input v-model="pwdForm.confirm" type="password" show-password autocomplete="new-password" placeholder="再次输入新密码" @keyup.enter="submitPwd" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdDlg = false">取消</el-button>
        <el-button type="primary" :loading="pwdSaving" @click="submitPwd">确认修改</el-button>
      </template>
    </el-dialog>

    <!-- 通知中心:点铃铛从右侧滑出,支持搜索+类目筛选+加载更多 -->
    <!-- teleport 到 body:顶栏 .top 的 backdrop-filter 会为 position:fixed 后代建立包含块,
         否则抽屉被圈进 70px 高的顶栏,变成顶部窄条而非右侧全高面板。 -->
    <teleport to="body">
    <el-drawer
      v-model="drawer"
      direction="rtl"
      size="420px"
      :with-header="false"
      class="ndrawer-root"
      @open="onDrawerOpen"
    >
      <div class="ndrawer">
        <div class="ndrawer__head">
          <div>
            <span class="ndrawer__title">通知中心</span>
            <span v-if="notif.unread" class="ndrawer__cnt">{{ notif.unread }} 条未读</span>
          </div>
          <div class="ndrawer__acts">
            <button v-if="notif.unread" class="ndrawer__all" @click="onMarkAll">全部已读</button>
          </div>
        </div>
        <div class="ndrawer__filters">
          <label class="ndrawer__search">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="11" cy="11" r="7" /><path d="M21 21l-4-4" /></svg>
            <input v-model="kw" placeholder="搜索标题或正文…" @input="onSearch" @keyup.enter="onSearch(true)" />
          </label>
          <div class="ndrawer__chips">
            <button
              v-for="c in cats"
              :key="String(c.v)"
              class="chip"
              :class="{ on: notif.category === c.v }"
              @click="setCat(c.v)"
            >{{ c.l }}</button>
          </div>
        </div>
        <div class="ndrawer__list">
          <NotificationList :items="notif.list" @item-click="onClickItem" />
        </div>
        <div v-if="notif.list.length" class="ndrawer__foot">
          <span class="mono">共 {{ notif.total }} 条</span>
          <button v-if="notif.list.length < notif.total" class="ndrawer__more" :disabled="loadingMore" @click="onLoadMore">
            {{ loadingMore ? '加载中…' : '加载更多 ›' }}
          </button>
        </div>
      </div>
    </el-drawer>
    </teleport>
  </header>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useClock } from '../composables/useClock'
import { useUserStore, ROLE } from '../stores/user'
import { useNotificationStore } from '../stores/notification'
import { logout as apiLogout, changePassword as apiChangePassword } from '../api/auth'
import NotificationList from './NotificationList.vue'

const store = useUserStore()
const notif = useNotificationStore()
const router = useRouter()
const clock = useClock()
const drawer = ref(false)
const kw = ref('')
const loadingMore = ref(false)

const cats = [
  { v: null, l: '全部' },
  { v: 0, l: '库存' },
  { v: 1, l: '反馈' },
  { v: 2, l: '药企' },
  { v: 3, l: '系统' }
]

const avatarText = computed(() => (store.realName || store.username || '用').slice(0, 1))

// —— 通知抽屉 ——
async function openNotif() { drawer.value = true }
// 抽屉打开:重置筛选并拉首页
async function onDrawerOpen() {
  notif.category = null
  notif.keyword = ''
  notif.isRead = null
  kw.value = ''
  try { await notif.loadList(1, true) } catch { /* 打开失败静默,列表显示空态 */ }
}
let searchTimer = null
function onSearch(immediate = false) {
  clearTimeout(searchTimer)
  const run = async () => {
    notif.keyword = kw.value
    try { await notif.loadList(1, true) } catch { /* 静默 */ }
  }
  if (immediate) run(); else searchTimer = setTimeout(run, 350)
}
function setCat(c) {
  notif.category = c
  notif.loadList(1, true)
}
async function onLoadMore() {
  loadingMore.value = true
  try { await notif.loadMore() } finally { loadingMore.value = false }
}
async function onMarkAll() { await notif.markAllRead() }
async function onClickItem(n) {
  if (!n.isRead) await notif.markRead(n.id)
  const route = targetPath(n.refType, n.refId)
  if (route) { drawer.value = false; router.push(route) }
}
function targetPath(refType, refId) {
  const r = store.role
  const query = refId ? { id: refId } : {}
  if (refType === 'DEMAND') {
    const base = ({ [ROLE.ADMIN]: '/admin/demands', [ROLE.COMPANY]: '/company/demands', [ROLE.DOCTOR]: '/doctor/demands' })[r]
    return base ? { path: base, query } : null
  }
  if (refType === 'STOCK') return ({ [ROLE.COMPANY]: '/company/stocks' })[r] || null
  if (refType === 'COMPANY') return ({ [ROLE.ADMIN]: '/admin/companies' })[r] || null
  // 机构向医师发的公告:医师跳公告页,机构跳本院医师公告页
  if (refType === 'ANNOUNCE') return ({ [ROLE.DOCTOR]: '/doctor/policies', [ROLE.INSTITUTION]: '/institution/announce' })[r] || null
  // 药企公告(policy):投递对象是医疗机构,跳"药企公告"列表页
  if (refType === 'POLICY') return ({ [ROLE.INSTITUTION]: '/institution/policies', [ROLE.DOCTOR]: '/doctor/policies' })[r] || null
  return null
}

async function onCmd(cmd) {
  if (cmd === 'home') { router.push('/'); return }
  if (cmd === 'password') { openPwd(); return }
  if (cmd === 'logout') {
    try {
      await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
    } catch {
      return
    }
    try {
      await apiLogout()
    } catch {
      /* 忽略:即便后端未响应也本地清登录态 */
    }
    store.logout()
    router.push('/login')
  }
}

// 修改密码(所有角色自助):校验旧密码后设新密码
function openPwd() {
  pwdForm.oldPassword = ''
  pwdForm.newPassword = ''
  pwdForm.confirm = ''
  pwdDlg.value = true
}
async function submitPwd() {
  if (!pwdForm.oldPassword) { ElMessage.warning('请输入旧密码'); return }
  if (!pwdForm.newPassword || pwdForm.newPassword.length < 6 || pwdForm.newPassword.length > 50) {
    ElMessage.warning('新密码长度 6~50'); return
  }
  if (pwdForm.newPassword !== pwdForm.confirm) { ElMessage.warning('两次新密码不一致'); return }
  pwdSaving.value = true
  try {
    await apiChangePassword({ oldPassword: pwdForm.oldPassword, newPassword: pwdForm.newPassword })
    ElMessage.success('密码已修改')
    pwdDlg.value = false
  } finally {
    pwdSaving.value = false
  }
}

const pwdDlg = ref(false)
const pwdSaving = ref(false)
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirm: '' })
</script>

<style scoped>
.top {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px 26px;
  background: rgba(247, 249, 252, 0.85);
  backdrop-filter: saturate(140%) blur(12px);
  border-bottom: 1px solid var(--line);
}
.top__spacer { flex: 1; }
.pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-family: var(--font-m);
  font-size: 12px;
  color: var(--indigo);
  background: var(--indigo-soft);
  padding: 8px 13px;
  border-radius: 999px;
}
.pill i { width: 6px; height: 6px; border-radius: 50%; background: var(--indigo-hi); position: relative; }
.pill i::after {
  content: '';
  position: absolute;
  inset: -4px;
  border-radius: 50%;
  border: 1px solid var(--indigo-hi);
  animation: wping 1.9s infinite;
}
@keyframes wping { 0% { transform: scale(0.6); opacity: 0.9; } 100% { transform: scale(1.9); opacity: 0; } }
.iconbtn {
  position: relative;
  width: 40px;
  height: 40px;
  border-radius: 11px;
  border: 1px solid var(--line);
  background: var(--surface);
  display: grid;
  place-items: center;
  cursor: pointer;
  color: var(--ink-2);
  transition: border-color 0.2s, color 0.2s;
  flex: none;
}
.iconbtn:hover { border-color: var(--indigo-hi); color: var(--indigo); }
.iconbtn svg { width: 18px; height: 18px; }
.who {
  display: flex;
  align-items: center;
  gap: 9px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 11px;
  padding: 4px 11px 4px 4px;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s;
  flex: none;
  outline: none;
}
.who:hover { border-color: var(--indigo-hi); box-shadow: 0 0 0 4px var(--indigo-glow); }
.who__av {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  color: #fff;
  flex: none;
  background: linear-gradient(135deg, #818cf8, var(--indigo));
  font-family: var(--font-d);
  font-weight: 700;
  font-size: 13px;
}
.who__t { font-size: 13px; font-weight: 600; color: var(--ink); line-height: 1.2; }
.who__t small { display: block; font-family: var(--font-m); font-size: 9.5px; color: var(--ink-3); font-weight: 400; margin-top: 1px; }
.who svg { width: 15px; height: 15px; color: var(--ink-3); flex: none; }

@media (max-width: 680px) { .who__t { display: none; } .who svg { display: none; } .who { padding: 0; border: none; } }
</style>

<!-- 通知抽屉:el-drawer teleport 到 body,scoped 够不到,故用全局块 -->
<style>
.ndrawer-root .el-drawer__body { padding: 0; }
.ndrawer { display: flex; flex-direction: column; height: 100%; }
.ndrawer__head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 18px 20px 14px; border-bottom: 1px solid #edf0f7;
}
.ndrawer__title { font-size: 17px; font-weight: 700; color: #161b2e; }
.ndrawer__cnt { margin-left: 10px; font-size: 12px; color: #6366f1; font-family: 'DM Mono', ui-monospace, monospace; }
.ndrawer__acts { display: flex; gap: 8px; }
.ndrawer__all { background: none; border: 1px solid #e2e7f2; cursor: pointer; color: #4338ca; font-size: 12px; padding: 6px 12px; border-radius: 8px; transition: background 0.15s; }
.ndrawer__all:hover { background: #eef0fe; }
.ndrawer__filters { padding: 14px 20px; display: flex; flex-direction: column; gap: 10px; border-bottom: 1px solid #edf0f7; }
.ndrawer__search {
  display: flex; align-items: center; gap: 8px;
  background: #f7f9fc; border: 1px solid #e2e7f2; border-radius: 10px; padding: 8px 12px;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.ndrawer__search:focus-within { border-color: #6366f1; box-shadow: 0 0 0 4px rgba(99,102,241,0.12); }
.ndrawer__search svg { width: 16px; height: 16px; color: #8b93a6; flex: none; }
.ndrawer__search input { border: none; outline: none; background: none; font-size: 13px; width: 100%; color: #161b2e; }
.ndrawer__search input::placeholder { color: #8b93a6; }
.ndrawer__chips { display: flex; flex-wrap: wrap; gap: 7px; }
.ndrawer__chips .chip {
  background: #f7f9fc; border: 1px solid #e2e7f2; cursor: pointer;
  font-size: 12px; color: #525c73; padding: 5px 12px; border-radius: 999px;
  transition: all 0.15s;
}
.ndrawer__chips .chip:hover { border-color: #c7ccdb; }
.ndrawer__chips .chip.on { background: #4338ca; border-color: #4338ca; color: #fff; }
.ndrawer__list { flex: 1; overflow-y: auto; }
.ndrawer__foot {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 20px; border-top: 1px solid #edf0f7; font-size: 12px; color: #8b93a6;
}
.ndrawer__more { background: none; border: none; cursor: pointer; color: #4338ca; font-size: 12px; padding: 4px 8px; }
.ndrawer__more:hover { text-decoration: underline; }
.ndrawer__more:disabled { color: #b8becc; cursor: default; }
</style>
