import http from '../utils/request'

export const login = (data) => http.post('/auth/login', data)
// silent:登出是"尽力而为"——token 已过期时后端会 401,但用户是主动退出,不该弹"登录已过期"(本地照常清登录态)
export const logout = () => http.post('/auth/logout', null, { silent: true })
export const changePassword = (data) => http.put('/users/me/password', data)
// 当前登录滑块验证模式(登录页探活,免登录):enabled/pass/lock。silent:后端不可用时静默,默认回 enabled
export const getCaptchaMode = () => http.get('/auth/captcha-mode', { silent: true })
// 知识库问答是否开启(免登录探活):精灵据此决定展示问答还是"已被管理员禁用"占位
export const getKbEnabled = () => http.get('/auth/kb-enabled', { silent: true })
// 游客体验登录(免账号,签发 GUEST 只读临时令牌;受 guest.enabled + captcha.mode + IP 限流约束)
export const guestLogin = (data) => http.post('/auth/guest', data || {})
// 游客体验入口是否开放(免登录探活):登录页据此决定是否渲染"游客体验"按钮
export const getGuestEnabled = () => http.get('/auth/guest-enabled', { silent: true })
