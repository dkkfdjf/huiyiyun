<template>
  <div class="login">
    <div class="login__bg" aria-hidden="true">
      <span class="blob b1"></span>
      <span class="blob b2"></span>
    </div>

    <router-link class="back" to="/" title="返回首页">← 返回首页</router-link>

    <aside class="aside">
      <router-link class="brand" to="/">
        <span class="brand__mk" aria-hidden="true">
          <svg viewBox="0 0 32 32" fill="none" stroke="#fff" stroke-width="1.8" stroke-linejoin="round" stroke-linecap="round">
            <path d="M16 4l10.4 6v12L16 28 5.6 22V10z" fill="rgba(255,255,255,.18)" />
            <path d="M16 8.4V16M16 16l6.8 3.8M16 16l-6.8 3.8" />
          </svg>
        </span>
        <span class="brand__t">慧医云<small>Huiyi Cloud</small></span>
      </router-link>
      <h2 class="aside__t">医药运营，<br />一处统管。</h2>
      <p class="aside__d">药、医、网点，散者归一；进销、调度与流向，于同一云端同步而动。</p>
      <ul class="aside__pts">
        <li><i></i>药品档案 · 网点铺货 · 销售扣减</li>
        <li><i></i>库存阈值预警 · 补货回流</li>
        <li><i></i>医师资源 · 科室与职称分布</li>
      </ul>
    </aside>

    <main class="card">
      <div class="card__panel">
      <div class="card__head">
        <span class="kick"><i></i>欢迎回来</span>
        <h1 class="card__t">登录工作台</h1>
        <p class="card__d">{{ loginHint }}</p>
      </div>

      <!-- 锁定提示横幅(不阻断表单):LOCK 仍放行管理员登录恢复,故只提示不挡 -->
      <div v-if="mode === 'lock'" class="lockban">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round" stroke-linecap="round">
          <rect x="4.5" y="10.5" width="15" height="9.5" rx="2" /><path d="M8 10.5V7.5a4 4 0 0 1 8 0v3" /><circle cx="12" cy="15" r="1.4" />
        </svg>
        <span>系统登录已锁定,仅管理员可登录恢复。</span>
      </div>

      <el-tabs v-model="tab" class="logintabs">
        <!-- 账号登录 -->
        <el-tab-pane label="账号登录" name="login">
          <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
            <el-form-item label="账号" prop="username">
              <el-input id="username" name="username" v-model="form.username" placeholder="请输入账号" size="large" autocomplete="username" />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input
                id="password"
                name="password"
                v-model="form.password"
                type="password"
                placeholder="请输入密码"
                size="large"
                show-password
                autocomplete="current-password"
                @keyup.enter="onEnter"
              />
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- 游客体验(入口开放且非锁定时才显示该 tab) -->
        <el-tab-pane v-if="guestEnabled && mode !== 'lock'" label="游客体验" name="guest">
          <div class="guest-intro">
            <p class="guest-intro__t">免账号浏览平台</p>
            <p class="guest-intro__d">以只读游客身份体验药企、医疗机构、医师、销售网点、必备材料与政策公告,并可向 AI 知识助手提问。</p>
            <ul class="guest-intro__pts">
              <li><i></i>只读浏览,无任何写权限</li>
              <li><i></i>电话 / 证照等敏感信息自动脱敏</li>
              <li><i></i>临时令牌 2 小时,提问有频率限制</li>
            </ul>
            <p class="guest-intro__login">需要完整功能?<a @click="tab = 'login'">账号登录 →</a></p>
          </div>
        </el-tab-pane>
      </el-tabs>

      <!-- 公共验证 + 提交区(tab 之外,两 tab 共用同一 embed 滑块实例;滑过自动回调按 tab 分流) -->
      <div v-if="mode === 'enabled'" class="captcha-area">
        <div class="captcha-label">滑块验证</div>
        <!-- 阿里云验证码 2.0 嵌入式:SDK 渲染到此容器,滑过即自动触发 captchaVerifyCallback(无需点按钮) -->
        <div id="captcha-element" class="captcha-box"></div>
        <div class="captcha-hint">拖动滑块完成验证即{{ tab === 'guest' ? '进入游客体验' : '登录' }}</div>
      </div>
      <el-button v-if="mode === 'enabled'" id="captcha-button" class="submit" type="primary" size="large" :loading="loading">
        {{ tab === 'guest' ? '进入游客体验' : '登 录' }} <span class="arr">→</span>
      </el-button>
      <el-button v-else class="submit" type="primary" size="large" :loading="loading" @click="submitDirect">
        {{ tab === 'guest' ? '进入游客体验' : '登 录' }} <span class="arr">→</span>
      </el-button>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login, getCaptchaMode, guestLogin, getGuestEnabled } from '../../api/auth'
import { useUserStore } from '../../stores/user'

const router = useRouter()
const store = useUserStore()
const formRef = ref(null)
const loading = ref(false)
const guestEnabled = ref(false)   // 游客体验入口是否开放(探活);关则不渲染游客 tab
const tab = ref('login')          // 'login' | 'guest' —— 当前标签;embed 滑过自动回调按此分流账号/游客
/** 滑块验证模式(enabled/pass/lock)。默认 enabled:探活失败也保持滑块,最安全。 */
const mode = ref('enabled')
const loginHint = computed(() =>
  mode.value === 'lock' ? '系统已锁定,仅管理员可登录恢复。'
  : mode.value === 'pass' ? '请使用账号登录(验证已关闭)。'
  : '请使用账号登录,拖动滑块完成验证。'
)
/** 阿里云滑块:embed 常驻渲染于 #captcha-element;登录按钮(#captcha-button)由 SDK 拦截触发验证 ——
 *  滑块通过 → captchaVerifyCallback 在此发登录请求并回传业务结果。
 *  关键:callback 由「点击 button」触发(embed 滑完不自动触发),故 button 必须指向登录按钮,否则永不回调。
 *  另:button 是 SDK 必需参数,缺失则滑块不渲染。 */
let captchaInstance = null

const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

onMounted(async () => {
  // 先探活当前模式;失败默认 enabled(保持滑块)。
  try { mode.value = (await getCaptchaMode()).mode || 'enabled' } catch { mode.value = 'enabled' }
  // 游客入口探活(失败默认关):登录页据此决定是否渲染"游客体验"按钮。
  try { guestEnabled.value = (await getGuestEnabled()).enabled === true } catch { guestEnabled.value = false }
  // 仅启用模式才加载阿里云滑块 SDK(pass/lock 不发任何阿里云请求)。
  if (mode.value === 'enabled') initCaptcha()
})

/** 阿里云验证码 2.0(embed):CDN 可能未就绪,轮询等待 window.initAliyunCaptcha。仅 enabled 模式调用。 */
function initCaptcha() {
  let waited = 0
  const init = () => {
    if (window.initAliyunCaptcha) {
      window.initAliyunCaptcha({
        SceneId: '1rdtgv6y',
        mode: 'embed',
        element: '#captcha-element',
        button: '#captcha-button',
        captchaVerifyCallback: async (captchaVerifyParam) => {
          // embed 滑过自动回调:按当前 tab 分流——账号 tab 走登录,游客 tab 走游客登录(同一滑块实例,两 tab 共用)
          const flow = tab.value === 'guest' ? 'guest' : 'login'
          if (flow === 'guest') {
            loading.value = true
            try {
              const vo = await guestLogin({ captchaVerifyParam })
              store.setLogin(vo)
              router.push(store.homePath)
              return { captchaResult: true, bizResult: true }
            } catch {
              refreshCaptcha()
              return { captchaResult: true, bizResult: false }
            } finally {
              loading.value = false
            }
          }
          // 账号登录:表单未填直接拦下(避免无谓重滑)
          try { await formRef.value.validate() } catch { ElMessage.warning('请填写账号与密码'); return { captchaResult: true, bizResult: false } }
          loading.value = true
          try {
            const vo = await login({ ...form, captchaVerifyParam })
            store.setLogin(vo)
            router.push(store.homePath)
            return { captchaResult: true, bizResult: true }
          } catch {
            // 业务码(密码错/锁定/验证码失效)已由 http 拦截器 ElMessage;刷新滑块便于重试(凭据一次性)
            refreshCaptcha()
            return { captchaResult: true, bizResult: false }
          } finally {
            loading.value = false
          }
        },
        onBizResultCallback: () => {},
        getInstance: (instance) => { captchaInstance = instance },
        slideStyle: { height: 40 },
        language: 'cn'
      })
    } else if (waited < 10000) {
      waited += 200
      setTimeout(init, 200)
    }
  }
  init()
}

/** pass 模式:不挂滑块,提交按钮直接发请求(不带 captchaVerifyParam)。按当前 tab 分流账号 / 游客。 */
async function submitDirect() {
  if (tab.value === 'guest') return guestEnter()
  try { await formRef.value.validate() } catch { return }
  loading.value = true
  try {
    const vo = await login({ ...form })
    store.setLogin(vo)
    router.push(store.homePath)
  } catch { /* http 拦截器已提示 */ }
  finally { loading.value = false }
}

/** 游客登录(pass 模式直连;enabled 模式由滑块回调触发,不走此处)。 */
async function guestEnter() {
  loading.value = true
  try {
    const vo = await guestLogin({})
    store.setLogin(vo)
    router.push(store.homePath)
  } catch { /* http 拦截器已提示 */ }
  finally { loading.value = false }
}

function refreshCaptcha() {
  if (captchaInstance && captchaInstance.refresh) captchaInstance.refresh()
}

// 切换账号/游客 tab 时刷新滑块:embed 实例两 tab 共用,滑过一次后停留在"已验证"态,
// 切到另一 tab 会带着被消费过的 captchaVerifyParam → 那个 tab 的登录拿不到有效验真串而失败。
// 刷新让每个 tab 各自独立验证,互不串用。
watch(tab, () => { if (mode.value === 'enabled') refreshCaptcha() })

/** 回车提交(密码框,仅账号 tab):enabled 点滑块按钮触发验证,pass 直接提交 */
function onEnter() {
  if (mode.value === 'enabled') document.querySelector('#captcha-button')?.click()
  else submitDirect()
}
</script>

<style scoped>
.login {
  position: relative;
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1.05fr 0.95fr;
  background: var(--bg);
  overflow: hidden;
}
.login__bg { position: absolute; inset: 0; z-index: 0; pointer-events: none; }
.blob { position: absolute; border-radius: 50%; filter: blur(60px); }
.b1 { width: 520px; height: 520px; top: -160px; right: -120px; background: var(--indigo-glow); }
.b2 { width: 460px; height: 460px; bottom: -180px; left: -120px; background: rgba(124, 58, 237, 0.1); }

/* 左:品牌 */
.aside {
  position: relative;
  z-index: 1;
  padding: 56px 64px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  color: var(--ink);
}
.brand { display: inline-flex; align-items: center; gap: 10px; margin-bottom: 48px; }
.brand__mk { width: 38px; height: 38px; border-radius: 11px; display: grid; place-items: center; flex: none; background: linear-gradient(150deg, var(--indigo-hi), var(--indigo)); box-shadow: 0 10px 22px -10px rgba(67, 56, 202, 0.6); }
.brand__mk svg { width: 22px; height: 22px; }
.brand__t { font-family: var(--font-d); font-weight: 800; font-size: 22px; letter-spacing: -0.02em; }
.brand__t small { font-family: var(--font-m); font-weight: 400; font-size: 10.5px; color: var(--ink-3); letter-spacing: 0.1em; margin-left: 8px; text-transform: uppercase; }
.aside__t { font-family: var(--font-d); font-weight: 800; font-size: clamp(34px, 3.6vw, 46px); line-height: 1.1; letter-spacing: -0.03em; }
.aside__d { margin-top: 18px; font-size: 16px; color: var(--ink-2); max-width: 420px; }
.aside__pts { list-style: none; margin-top: 30px; display: flex; flex-direction: column; gap: 12px; font-size: 14.5px; color: var(--ink-2); }
.aside__pts i { display: inline-block; width: 7px; height: 7px; border-radius: 50%; background: var(--indigo-hi); margin-right: 12px; transform: translateY(-2px); }

/* 右:玻璃登录卡 */
.card {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 56px 64px;
}
/* 右侧玻璃登录卡:半透明白底 + 模糊 + 边框阴影,表单不再漂浮在背景上 */
.card__panel {
  width: 100%;
  max-width: 400px;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: saturate(140%) blur(16px);
  -webkit-backdrop-filter: saturate(140%) blur(16px);
  border: 1px solid var(--line);
  border-radius: 20px;
  box-shadow: 0 24px 60px -28px rgba(16, 18, 32, 0.28);
  padding: 34px 36px 30px;
}
:deep(.card__panel) .el-form { width: 100%; }
.card__head { margin-bottom: 26px; }
.kick {
  display: inline-flex; align-items: center; gap: 9px; font-family: var(--font-m); font-size: 12px;
  color: var(--indigo); background: var(--indigo-glow); padding: 6px 13px; border-radius: 999px;
}
.kick i { width: 7px; height: 7px; border-radius: 50%; background: var(--indigo-hi); }
.card__t { font-family: var(--font-d); font-weight: 800; font-size: 30px; letter-spacing: -0.025em; margin-top: 16px; }
.card__d { margin-top: 8px; color: var(--ink-2); font-size: 14px; }

/* 阿里云滑块容器:强制满宽。圆角由内层 div 自带;不再用 overflow:hidden 裁切——
   否则会把滑块验证成功的绿色提示/勾选裁掉,导致"滑到底看不见成功提醒"。 */
.captcha-box { width: 100%; min-height: 44px; }
:deep(#captcha-element) { width: 100% !important; }
:deep(#captcha-element > div) { width: 100% !important; border-radius: 11px; }

/* 锁定提示横幅(lock 模式:仅提示,不挡表单——管理员仍可登录恢复) */
.lockban {
  display: flex; align-items: center; gap: 8px;
  margin-bottom: 16px; padding: 10px 14px;
  font-size: 12.5px; color: var(--red);
  background: rgba(220, 42, 69, 0.07); border: 1px solid rgba(220, 42, 69, 0.2); border-radius: 10px;
}
.lockban svg { width: 17px; height: 17px; flex: none; }

.submit {
  width: 100%;
  margin-top: 6px;
  height: 46px;
  font-family: var(--font-d);
  font-weight: 700;
  font-size: 15px;
  letter-spacing: 0.04em;
  border-radius: 12px;
  box-shadow: 0 10px 22px -10px rgba(67, 56, 202, 0.55);
}
.submit .arr { transition: transform 0.2s; }
.submit:hover .arr { transform: translateX(3px); }

/* 登录 tab:账号 / 游客切换 */
.logintabs { margin-bottom: 4px; }
:deep(.logintabs .el-tabs__header) { margin-bottom: 18px; }
:deep(.logintabs .el-tabs__nav-wrap::after) { display: none; }
:deep(.logintabs .el-tabs__item) { font-family: var(--font-d); font-weight: 600; font-size: 14.5px; height: 38px; line-height: 38px; }

/* 游客 tab 说明卡 */
.guest-intro { padding: 6px 2px 2px; }
.guest-intro__t { font-family: var(--font-d); font-weight: 700; font-size: 18px; color: var(--ink); }
.guest-intro__d { margin-top: 8px; font-size: 13.5px; color: var(--ink-2); line-height: 1.7; }
.guest-intro__pts { list-style: none; margin: 14px 0 0; display: flex; flex-direction: column; gap: 9px; font-size: 13px; color: var(--ink-2); }
.guest-intro__pts i { display: inline-block; width: 6px; height: 6px; border-radius: 50%; background: var(--indigo-hi); margin-right: 10px; transform: translateY(-2px); }
.guest-intro__login { margin-top: 16px; font-size: 13px; color: var(--ink-3); }
.guest-intro__login a { color: var(--indigo); font-weight: 600; cursor: pointer; }
.guest-intro__login a:hover { text-decoration: underline; }

/* 公共滑块区(enabled 模式,两 tab 共用) */
.captcha-area { margin-top: 4px; }
.captcha-label { font-size: 13px; color: var(--ink-2); padding-bottom: 4px; }
.captcha-hint { margin-top: 8px; font-size: 12px; color: var(--ink-3); text-align: center; }

.back {
  position: absolute;
  top: 28px;
  left: 32px;
  z-index: 5;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-family: var(--font-b);
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-2);
  padding: 8px 14px 8px 12px;
  border-radius: 999px;
  background: transparent;
  border: 1px solid transparent;
  transition: color 0.18s, background 0.18s, border-color 0.18s, transform 0.18s;
}
.back:hover { color: var(--indigo); background: var(--surface); border-color: var(--line); transform: translateX(-2px); }

:deep(.el-form-item__label) { font-size: 13px; color: var(--ink-2); padding-bottom: 4px; }
:deep(.el-input__wrapper) { border-radius: 11px; }

@media (max-width: 900px) {
  .login { grid-template-columns: 1fr; }
  .aside { display: none; }
  .card { padding: 48px 24px; }
}
</style>
