import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, clearToken } from './auth'
import router from '../router'

/**
 * 解包后端统一返回 R<T>={code,message,data}。
 * code===200 返回 data,否则提示并抛出。
 * code===401:去重处理——并发多请求同时 401 时只弹一次、只跳一次登录,避免弹窗风暴。
 */
let unauthorizedLock = false

export function unwrap(body, silent = false) {
  if (!body) throw new Error('empty response')
  if (body.code === 200) return body.data
  if (body.code === 401) {
    // silent 请求(如登出:即便 token 过期也属用户主动退出,不该弹"登录已过期")跳过全局提示,仅抛错由调用方处理。
    // 注意:轮询类(未读数等)不能 silent —— 真·会话过期需借它们的 401 弹提示并跳登录,否则用户以为还在线却处处 401。
    if (!silent) handleUnauthorized()
    throw body
  }
  ElMessage.error(body.message || '请求失败')
  throw body
}

function handleUnauthorized() {
  if (unauthorizedLock) return
  unauthorizedLock = true
  ElMessage.error('登录已过期,请重新登录')
  clearToken()
  if (router.currentRoute.path !== '/login') router.push('/login')
  // 短暂上锁,等跳转收敛;之后放开,新登录若再 401 仍能提示
  setTimeout(() => { unauthorizedLock = false }, 1500)
}

const http = axios.create({ baseURL: '/api/v1', timeout: 15000 })

http.interceptors.request.use((cfg) => {
  const t = getToken()
  if (t) cfg.headers.Authorization = `Bearer ${t}`
  return cfg
})

http.interceptors.response.use(
  (resp) => unwrap(resp.data, resp.config?.silent),
  (err) => {
    // 业务错误已在 unwrap 按 code 提示;这里只兜底网络层错误(后端不可达等)。
    // 轮询类请求带 silent:true(如缓存/未读数),失败时静默——后端短暂不可用不该刷屏弹"服务器错误"。
    if (!err?.config?.silent) {
      const msg = err?.response?.data?.message || err.message || '网络异常'
      ElMessage.error(msg)
    }
    return Promise.reject(err)
  }
)

export default http
