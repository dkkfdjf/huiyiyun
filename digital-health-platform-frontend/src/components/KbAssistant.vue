<template>
  <teleport to="body">
    <!-- 悬浮精灵:可拖动的启动器(点=开/关面板,拖=移动位置) -->
    <button
      class="kb-fab"
      :style="{ left: pos.left + 'px', top: pos.top + 'px' }"
      title="慧医云 · 知识助手"
      @pointerdown="onDown"
    >
      <span class="kb-fab__halo"></span>
      <svg class="kb-fab__spark" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
        <path d="M12 3l1.7 4.8L18.5 9.5l-4.8 1.7L12 16l-1.7-4.8L5.5 9.5l4.8-1.7L12 3z" />
        <path d="M5 15l.8 2.2L8 18l-2.2.8L5 21l-.8-2.2L2 18l2.2-.8L5 15z" />
      </svg>
      <span v-if="!open" class="kb-fab__pulse"></span>
    </button>

    <!-- 对话面板:位置跟随精灵(向上开,贴顶则向下开) -->
    <transition name="kb-pop">
      <section v-if="open" class="kb-panel" :style="panelStyle">
        <header class="kb-panel__head" @pointerdown="onPanelDown">
          <div class="kb-panel__title">
            <span class="kb-panel__sigil">慧</span>
            <span class="kb-panel__name">知识助手<small>本地知识库 · 答案可溯源</small></span>
          </div>
          <div class="kb-panel__head-actions">
            <button class="kb-panel__x" title="清空对话历史" @click="clearChat">清空</button>
            <button class="kb-panel__x" title="收起" @click="open = false">✕</button>
          </div>
        </header>

        <div v-if="canUse" ref="listEl" class="kb-panel__msgs">
          <div v-if="!kbEnabled" class="kb-notice">知识库问答已对其他用户关闭,你作为管理员仍可使用。</div>
          <div v-if="!messages.length && !loading" class="kb-empty">
            问点什么吧 —— 优先基于本地知识库作答(标注来源);本地未命中时,会用通用知识兜底并标注「通用知识」。支持连续追问,会记住最近 5 轮对话。
          </div>

          <div v-for="(m, i) in messages" :key="i" class="kb-msg" :class="m.role">
            <div v-if="m.role === 'assistant'" class="kb-msg__avatar">慧</div>
            <div class="kb-msg__body">
              <div v-if="m.role === 'assistant' && m.mode === 'general'" class="kb-msg__general">通用知识 · 非本平台数据</div>
              <div v-if="m.role === 'assistant'" class="kb-msg__text md-body" v-html="renderMd(m.text)"></div>
              <div v-else class="kb-msg__text">{{ m.text }}</div>
              <div v-if="m.actions && m.actions.length" class="kb-msg__actions">
                <button v-for="(a, k) in m.actions" :key="k" class="kb-action" @click="go(a.target)">↗ {{ a.label }}</button>
              </div>
              <!-- 签名:可溯源的循证来源面板。序号对应正文里的 [1][2] 标注(召回顺序)。 -->
              <details v-if="m.sources && m.sources.length" class="kb-src">
                <summary class="kb-src__head">
                  <svg class="kb-src__ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3l9 5-9 5-9-5 9-5z"/><path d="M3 13l9 5 9-5"/></svg>
                  <span>依据来源 · {{ m.sources.length }} 条</span>
                  <span class="kb-src__chev">▾</span>
                </summary>
                <div class="kb-src__list">
                  <div v-for="(s, j) in m.sources" :key="j" class="kb-src__row">
                    <span class="kb-src__n">{{ j + 1 }}</span>
                    <div class="kb-src__main">
                      <span class="kb-src__t">{{ s.title || '文档 #' + s.docId }}</span>
                      <span class="kb-src__snip">{{ s.snippet }}</span>
                    </div>
                  </div>
                </div>
              </details>
            </div>
          </div>

          <div v-if="loading" class="kb-msg assistant">
            <div class="kb-msg__avatar">慧</div>
            <div class="kb-msg__body"><div class="kb-typing"><i></i><i></i><i></i></div></div>
          </div>
        </div>

        <div v-else class="kb-panel__msgs">
          <div class="kb-empty">
            本地知识库问答已被管理员关闭。<br />如需使用,请联系管理员开启。
          </div>
        </div>

        <footer v-if="canUse" class="kb-panel__foot">
          <textarea
            v-model="input"
            rows="1"
            placeholder="输入问题,Enter 发送 / Shift+Enter 换行"
            @keydown.enter.exact.prevent="send"
            @input="autoGrow"
          ></textarea>
          <button class="kb-send" :disabled="!input.trim() || loading" @click="send">发送</button>
        </footer>
      </section>
    </transition>
  </teleport>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { ask } from '../api/kb'
import { getKbEnabled } from '../api/auth'
import { useUserStore, ROLE } from '../stores/user'
import { useRouter } from 'vue-router'

const open = ref(false)
const input = ref('')
const messages = ref([])
const loading = ref(false)
// 多轮记忆:最近 5 轮(10 条)持久化到 localStorage(按用户隔离),重载/重开面板不丢;发问时把上文带给后端,使追问能结合语境
const HISTORY_ROUNDS = 5
const listEl = ref(null)
const kbEnabled = ref(true)   // 管理员可关停知识库问答(防计费超支);关后展示"已被禁用"占位,不发 /ask
const user = useUserStore()
const router = useRouter()
// 对话历史按用户隔离:历史里带各角色可点的动作按钮,全局 key 会让换账号后的用户看到别人的对话 + 别人角色的按钮
// (如非管理员看到管理员的 /admin/* 按钮,点击被路由守卫拦 → 表现为"有些角色跳转不了")。按 userId 分键即各看各的。
const historyKey = () => 'kb_assistant_history_' + (user.userId ?? 'guest')
const isAdmin = computed(() => user.role === ROLE.ADMIN)
// 管理员豁免:即使知识库总开关关闭,管理员仍可问答(后端 KnowledgeController.ask 已放行);
// 非管理员关停时看到禁用占位。canUse 控制是否渲染聊天区+输入框。
const canUse = computed(() => kbEnabled.value || isAdmin.value)

// assistant 回复按 Markdown 渲染(LLM 天然输出 MD:列表/表格/加粗/代码块);DOMPurify 消毒防注入。
marked.setOptions({ breaks: true, gfm: true })
function renderMd(text) {
  if (!text) return ''
  return DOMPurify.sanitize(marked.parse(text))
}

const BALL = 56, GAP = 14, PANEL_W = 368
// 默认位置(右下,留 24px 边距)。窗口可访问时直接作为初始值,避免组件首帧闪在左上角再跳过去
const defaultPos = () => ({ left: window.innerWidth - BALL - 24, top: window.innerHeight - BALL - 24 })
const pos = ref(defaultPos())
const panelPos = ref(null)   // 拖动面板头后的绝对位置(null=跟随球锚点)

onMounted(() => {
  // 恢复上次对话(最近 5 轮,按用户隔离),使悬浮精灵重开/刷新后"记得"上文
  try {
    const h = JSON.parse(localStorage.getItem(historyKey()))
    if (Array.isArray(h) && h.length) messages.value = h
  } catch { /* 损坏忽略 */ }
  // 一次性清理旧版全局历史 key(改按用户隔离前的共享历史),避免残留串看
  localStorage.removeItem('kb_assistant_history')
  // 精灵位置:每次重进重置到默认(右下),不读旧位置——避免把别的用户/上次会话拖动后的位置当成自己的。
  // 位置仅本次会话有效(可拖动),刷新/重进即回默认,故不落 localStorage。
  pos.value = defaultPos()   // 每次重进重算默认位置(窗口尺寸可能在两次会话间变化)
  panelPos.value = null   // 面板跟随球锚点(不沿用旧的手动拖动位置)
  // 清理已废弃的全局位置键(位置不再持久化,免留死数据)
  localStorage.removeItem('kb_assistant_pos')
  localStorage.removeItem('kb_assistant_panel_pos')
  // 探活知识库开关:后端关停时本面板直接展示禁用态,既不浪费 /ask 调用、也不误导用户输入
  getKbEnabled().then((r) => { kbEnabled.value = r?.enabled !== false }).catch(() => {})
})

// 面板位置:跟随精灵;球在下半屏向上开、贴顶则向下开;水平贴边。
const panelStyle = computed(() => {
  if (panelPos.value) {   // 已被手动拖动:用绝对位置,不再跟随球
    return { left: panelPos.value.left + 'px', top: panelPos.value.top + 'px', width: PANEL_W + 'px' }
  }
  const openUp = pos.value.top > window.innerHeight * 0.45
  let left = pos.value.left
  if (left + PANEL_W > window.innerWidth - 12) left = window.innerWidth - PANEL_W - 12
  if (left < 12) left = 12
  if (openUp) {
    return { left: left + 'px', bottom: (window.innerHeight - pos.value.top + GAP) + 'px', width: PANEL_W + 'px' }
  }
  return { left: left + 'px', top: (pos.value.top + BALL + GAP) + 'px', width: PANEL_W + 'px' }
})

// 拖动:位移 > 4px 视为拖动(不切换面板),否则视为点击(开关面板)
let down = false, moved = false, sx = 0, sy = 0, ox = 0, oy = 0
function onDown(e) {
  down = true; moved = false
  sx = e.clientX; sy = e.clientY; ox = pos.value.left; oy = pos.value.top
  window.addEventListener('pointermove', onMove)
  window.addEventListener('pointerup', onUp)
}
function onMove(e) {
  if (!down) return
  const dx = e.clientX - sx, dy = e.clientY - sy
  if (Math.abs(dx) > 4 || Math.abs(dy) > 4) moved = true
  const nl = Math.max(8, Math.min(window.innerWidth - BALL - 8, ox + dx))
  const nt = Math.max(8, Math.min(window.innerHeight - BALL - 8, oy + dy))
  pos.value = { left: nl, top: nt }
}
function onUp() {
  down = false
  window.removeEventListener('pointermove', onMove)
  window.removeEventListener('pointerup', onUp)
  if (moved) {
    // 拖动球=重置面板锚点:清除手动面板位置,让面板重新跟随球(位置仅本次会话有效,不持久化)
    panelPos.value = null
  } else open.value = !open.value
}

// 面板头拖动:抓头部移动整个面板(独立于球锚点);拖球则重置面板回球锚点
let pdown = false, psx = 0, psy = 0, pox = 0, poy = 0
function onPanelDown(e) {
  if (e.target.closest('.kb-panel__x')) return   // 排除关闭按钮
  const rect = e.currentTarget.closest('.kb-panel').getBoundingClientRect()
  pdown = true; psx = e.clientX; psy = e.clientY; pox = rect.left; poy = rect.top
  window.addEventListener('pointermove', onPanelMove)
  window.addEventListener('pointerup', onPanelUp)
}
function onPanelMove(e) {
  if (!pdown) return
  const nl = Math.max(8, Math.min(window.innerWidth - PANEL_W - 8, pox + e.clientX - psx))
  const nt = Math.max(8, Math.min(window.innerHeight - 120, poy + e.clientY - psy))
  panelPos.value = { left: nl, top: nt }
}
function onPanelUp() {
  pdown = false
  window.removeEventListener('pointermove', onPanelMove)
  window.removeEventListener('pointerup', onPanelUp)
  // 面板位置仅本次会话有效,不持久化(避免串用别人位置)
}

function autoGrow(e) {
  const t = e.target
  t.style.height = 'auto'
  t.style.height = Math.min(t.scrollHeight, 96) + 'px'
}

async function send() {
  const q = input.value.trim()
  if (!q || loading.value) return
  const history = buildHistory()   // 取上文(不含本次),随请求带给后端做多轮记忆
  messages.value.push({ role: 'user', text: q })
  input.value = ''
  await nextTick(); scrollBottom()
  loading.value = true
  try {
    const data = await ask(q, { history }, { silent: true })   // silent:错误在对话框内显示,不弹 toast
    messages.value.push({ role: 'assistant', text: data.answer || '(无回答)', sources: data.sources || [], mode: data.mode, actions: data.actions || [] })
  } catch (e) {
    messages.value.push({ role: 'assistant', text: '请求失败:' + (e?.message || '网络异常') + '。知识库问答仅管理员可用,且需已导入资料。' })
  } finally {
    loading.value = false
    await nextTick(); scrollBottom()
  }
}

// 取最近 5 轮(10 条)对话转 {role,content};不含当前即将发送的问题(在 push 前调用)
function buildHistory() {
  return messages.value
    .filter(m => m.role === 'user' || m.role === 'assistant')
    .slice(-(HISTORY_ROUNDS * 2))
    .map(m => ({ role: m.role, content: m.text }))
}

// 持久化最近 5 轮(刷新/重开面板可恢复);超长历史只留窗口内,防 localStorage 无限膨胀
function persistHistory() {
  try {
    localStorage.setItem(historyKey(), JSON.stringify(messages.value.slice(-(HISTORY_ROUNDS * 2))))
  } catch { /* 配额满等忽略 */ }
}

async function clearChat() {
  if (!messages.value.length) return
  // 明示「清空」语义:既清当前显示,也删本机最近 5 轮记忆(重开不恢复);弃用浏览器原生 confirm(突兀且说不清)
  try {
    await ElMessageBox.confirm(
      '将清空当前对话，并删除本机保存的最近 5 轮对话记忆。',
      '清空对话',
      { type: 'warning', confirmButtonText: '清空', cancelButtonText: '取消' }
    )
  } catch { return }   // 用户点取消
  messages.value = []
  localStorage.removeItem(historyKey())
}

// 对话一变即持久化(deep:push 新消息、改字段都触发)
watch(messages, persistHistory, { deep: true })

function go(target) {
  if (!target) return
  open.value = false      // 跳转后收起面板,让目标页面完整呈现
  router.push(target)
}

function scrollBottom() {
  if (listEl.value) listEl.value.scrollTop = listEl.value.scrollHeight
}
</script>

<!-- teleport 到 body:非 scoped(统一 kb- 前缀,避免作用域问题) -->
<style>
/* ===== 慧医云 · 知识助手 —— 医疗蓝(#2563eb 系;AI 精灵单独用蓝,与平台青绿区分);签名=可溯源的临床循证来源面板 =====
   等宽仅用于来源编号/标签(临床读数感);非 scoped(teleport 到 body),统一 kb- 前缀。 */
.kb-fab {
  position: fixed; width: 56px; height: 56px; border-radius: 50%; border: none; padding: 0;
  background: #2563eb; color: #fff; cursor: grab; z-index: 2000;
  display: grid; place-items: center; touch-action: none; user-select: none;
  box-shadow: 0 14px 30px -8px rgba(37, 99, 235, .55), inset 0 0 0 1px rgba(255, 255, 255, .22);
  transition: transform .15s ease, box-shadow .2s ease;
}
.kb-fab:hover { transform: translateY(-2px) scale(1.04); box-shadow: 0 18px 36px -8px rgba(37, 99, 235, .65); }
.kb-fab:active { cursor: grabbing; }
.kb-fab__halo { position: absolute; inset: -7px; border-radius: 50%; background: radial-gradient(circle, rgba(37, 99, 235, .32), transparent 70%); z-index: 0; }
.kb-fab__spark { width: 25px; height: 25px; position: relative; z-index: 2; }
.kb-fab__pulse { position: absolute; inset: 0; border-radius: 50%; border: 2px solid rgba(37, 99, 235, .5); animation: kbpulse 2.4s infinite; z-index: 1; }
@keyframes kbpulse { 0% { transform: scale(1); opacity: .7; } 100% { transform: scale(1.7); opacity: 0; } }

.kb-panel {
  position: fixed; z-index: 2000; max-height: 70vh; min-height: 320px;
  background: #fff; border-radius: 18px; overflow: hidden;
  display: flex; flex-direction: column;
  box-shadow: 0 26px 64px -18px rgba(15, 23, 42, .42), 0 0 0 1px #e6eaf2;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}
.kb-panel__head {
  display: flex; align-items: center; justify-content: space-between; gap: 10px;
  padding: 13px 15px; cursor: move; touch-action: none; user-select: none;
  background: linear-gradient(135deg, #2563eb, #1d4ed8); color: #fff;
}
.kb-panel__title { display: flex; align-items: center; gap: 9px; min-width: 0; }
.kb-panel__sigil { width: 30px; height: 30px; flex: none; border-radius: 9px; display: grid; place-items: center; font-size: 15px; font-weight: 800; background: rgba(255, 255, 255, .2); box-shadow: inset 0 0 0 1px rgba(255, 255, 255, .3); }
.kb-panel__name { display: flex; flex-direction: column; line-height: 1.15; font-weight: 700; font-size: 14.5px; }
.kb-panel__name small { font-weight: 500; font-size: 10.5px; opacity: .82; margin-top: 2px; }
.kb-panel__head-actions { display: flex; gap: 6px; flex: none; }
.kb-panel__x { min-width: 26px; height: 26px; padding: 0 8px; border: none; border-radius: 8px; background: rgba(255, 255, 255, .16); color: #fff; cursor: pointer; font-size: 12.5px; }
.kb-panel__x:hover { background: rgba(255, 255, 255, .3); }

.kb-panel__msgs { flex: 1; overflow-y: auto; padding: 16px; display: flex; flex-direction: column; gap: 14px; background: #f5f7fa; }
.kb-empty { margin: auto; text-align: center; color: #64748b; font-size: 13px; max-width: 270px; line-height: 1.7; }
.kb-notice { margin-bottom: 10px; padding: 8px 11px; border-radius: 10px; background: #fef3c7; border: 1px solid #fcd34d; color: #92400e; font-size: 12px; line-height: 1.5; }

.kb-msg { display: flex; gap: 8px; max-width: 88%; }
.kb-msg.user { align-self: flex-end; flex-direction: row-reverse; }
.kb-msg__avatar { width: 28px; height: 28px; border-radius: 9px; flex: none; display: grid; place-items: center; background: #2563eb; color: #fff; font-size: 12px; font-weight: 800; box-shadow: 0 4px 10px -3px rgba(37, 99, 235, .5); }
.kb-msg__body { display: flex; flex-direction: column; gap: 6px; min-width: 0; }
.kb-msg.user .kb-msg__body { align-items: flex-end; }
.kb-msg__general { align-self: flex-start; font-size: 11px; font-weight: 600; color: #92400e; background: #fef3c7; border: 1px solid #fcd34d; padding: 2px 9px; border-radius: 999px; }
.kb-msg__actions { display: flex; flex-wrap: wrap; gap: 6px; }
.kb-action { border: 1px solid rgba(37, 99, 235, .32); background: rgba(37, 99, 235, .08); color: #1d4ed8; font-size: 12px; font-weight: 600; padding: 4px 11px; border-radius: 999px; cursor: pointer; transition: background .15s, transform .1s; }
.kb-action:hover { background: rgba(37, 99, 235, .16); transform: translateY(-1px); }
.kb-action:active { transform: translateY(0); }
.kb-msg__text { font-size: 13.5px; line-height: 1.62; padding: 10px 13px; border-radius: 14px; white-space: pre-wrap; word-break: break-word; }
.kb-msg.assistant .kb-msg__text { background: #fff; color: #0f172a; border: 1px solid #e6eaf2; border-top-left-radius: 4px; box-shadow: 0 2px 8px -4px rgba(15, 23, 42, .12); }
.kb-msg.user .kb-msg__text { background: #2563eb; color: #fff; border-top-right-radius: 4px; box-shadow: 0 4px 12px -4px rgba(37, 99, 235, .5); }

/* —— 签名:可溯源的循证来源面板(序号对应正文 [1][2]) —— */
.kb-src { margin-top: 2px; }
.kb-src__head { display: flex; align-items: center; gap: 7px; cursor: pointer; list-style: none; padding: 5px 9px; border-radius: 9px; color: #1d4ed8; font: 600 11.5px ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; letter-spacing: .01em; }
.kb-src__head::-webkit-details-marker { display: none; }
.kb-src__head:hover { background: rgba(37, 99, 235, .08); }
.kb-src__ico { width: 14px; height: 14px; flex: none; opacity: .85; }
.kb-src__chev { margin-left: auto; font-size: 10px; opacity: .55; transition: transform .18s; }
.kb-src[open] > .kb-src__head .kb-src__chev { transform: rotate(180deg); }
.kb-src__list { display: flex; flex-direction: column; gap: 6px; margin-top: 6px; }
.kb-src__row { display: flex; gap: 8px; background: #fff; border: 1px solid #e6eaf2; border-radius: 10px; padding: 8px 10px; }
.kb-src__n { flex: none; width: 20px; height: 20px; border-radius: 6px; display: grid; place-items: center; background: rgba(37, 99, 235, .12); color: #1d4ed8; font: 700 11px ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; }
.kb-src__main { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.kb-src__t { font-size: 12px; font-weight: 600; color: #0f172a; }
.kb-src__snip { font-size: 11.5px; color: #64748b; line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }

/* assistant 回复 Markdown 渲染样式:覆盖 pre-wrap(MD 自管换行) */
.md-body { white-space: normal; }
.md-body > *:first-child { margin-top: 0; }
.md-body > *:last-child { margin-bottom: 0; }
.md-body p { margin: 0 0 7px; }
.md-body ul, .md-body ol { margin: 6px 0; padding-left: 20px; }
.md-body li { margin: 2px 0; }
.md-body strong { font-weight: 700; }
.md-body em { font-style: italic; }
.md-body h1, .md-body h2, .md-body h3, .md-body h4 { font-weight: 700; margin: 10px 0 6px; line-height: 1.35; }
.md-body h1 { font-size: 16px; } .md-body h2 { font-size: 15px; } .md-body h3 { font-size: 14px; } .md-body h4 { font-size: 13.5px; }
.md-body code { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 12.5px; background: rgba(37, 99, 235, .1); color: #1d4ed8; padding: 1px 5px; border-radius: 5px; }
.md-body pre { background: #0b1f3a; color: #cfe0f7; padding: 10px 12px; border-radius: 10px; overflow-x: auto; margin: 8px 0; }
.md-body pre code { background: none; color: inherit; padding: 0; font-size: 12.5px; }
.md-body table { border-collapse: collapse; width: 100%; margin: 8px 0; font-size: 12.5px; display: block; overflow-x: auto; }
.md-body th, .md-body td { border: 1px solid #e6eaf2; padding: 5px 8px; text-align: left; }
.md-body th { background: #f5f7fa; font-weight: 600; }
.md-body blockquote { border-left: 3px solid rgba(37, 99, 235, .4); margin: 8px 0; padding: 2px 10px; color: #475569; }
.md-body a { color: #1d4ed8; text-decoration: underline; }
.md-body hr { border: 0; border-top: 1px solid #e6eaf2; margin: 10px 0; }

.kb-typing { display: flex; gap: 4px; padding: 13px 14px; background: #fff; border: 1px solid #e6eaf2; border-radius: 14px; border-top-left-radius: 4px; }
.kb-typing i { width: 7px; height: 7px; border-radius: 50%; background: #94a3b8; animation: kbbounce 1.2s infinite; }
.kb-typing i:nth-child(2) { animation-delay: .15s; }
.kb-typing i:nth-child(3) { animation-delay: .3s; }
@keyframes kbbounce { 0%, 60%, 100% { transform: translateY(0); opacity: .4; } 30% { transform: translateY(-5px); opacity: 1; } }

.kb-panel__foot { display: flex; gap: 8px; padding: 12px; border-top: 1px solid #e6eaf2; background: #fff; align-items: flex-end; }
.kb-panel__foot textarea { flex: 1; resize: none; border: 1px solid #e6eaf2; border-radius: 12px; padding: 9px 12px; font-size: 13.5px; font-family: inherit; outline: none; max-height: 96px; line-height: 1.5; color: #0f172a; background: #f5f7fa; }
.kb-panel__foot textarea::placeholder { color: #94a3b8; }
.kb-panel__foot textarea:focus { border-color: #2563eb; background: #fff; box-shadow: 0 0 0 3px rgba(37, 99, 235, .14); }
.kb-send { flex: none; background: #2563eb; color: #fff; border: none; border-radius: 12px; padding: 0 18px; height: 38px; cursor: pointer; font-size: 13.5px; font-weight: 600; transition: background .15s; }
.kb-send:hover:not(:disabled) { background: #1d4ed8; }
.kb-send:disabled { background: #cbd5e1; cursor: not-allowed; }

.kb-pop-enter-active, .kb-pop-leave-active { transition: opacity .18s ease, transform .18s ease; }
.kb-pop-enter-from, .kb-pop-leave-to { opacity: 0; transform: translateY(8px) scale(.97); }

@media (prefers-reduced-motion: reduce) {
  .kb-fab__pulse, .kb-typing i { animation: none; }
  .kb-pop-enter-active, .kb-pop-leave-active { transition: none; }
}
</style>
