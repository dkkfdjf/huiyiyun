// token 本地持久化 + JWT payload 解析(刷新页面后无需重新登录即可还原角色/归属)
const KEY = 'huiyi_token'

export const getToken = () => localStorage.getItem(KEY)
export const setToken = (t) => localStorage.setItem(KEY, t)
export const clearToken = () => localStorage.removeItem(KEY)

// 解析 JWT payload(不校验签名,签名由后端校验;此处仅为还原前端展示态)
function decodePayload(t) {
  try {
    const bin = atob(t.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))
    const json = decodeURIComponent(
      bin
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    )
    return JSON.parse(json)
  } catch (e) {
    return null
  }
}

// token 是否已过期(只看 exp;签名由后端验)。
// 主动判定过期,避免"前端以为还登录着、进系统后端逐个接口返 401"的弹窗风暴。
export function isTokenExpired() {
  const t = getToken()
  if (!t) return true
  const p = decodePayload(t)
  if (!p) return true
  if (!p.exp) return false            // 无 exp 不判过期,交后端
  return Date.now() >= p.exp * 1000
}

export const isLoggedIn = () => !!getToken() && !isTokenExpired()

export function getProfile() {
  const t = getToken()
  if (!t) return null
  if (isTokenExpired()) { clearToken(); return null }   // 过期即清,别带着过期 token 进系统
  return decodePayload(t)
}
