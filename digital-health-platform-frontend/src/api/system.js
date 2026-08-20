import http from '../utils/request'

// 系统运行指标(管理员):JVM 堆/线程/运行时长、DB 连接池(HikariCP)、Redis 延迟
export const getSystemMetrics = () => http.get('/system/metrics')
// 设置登录滑块验证模式(管理员):enabled/pass/lock —— 关闭可止损(0 计费调用)
export const setCaptchaMode = (mode) => http.put('/system/captcha-mode', null, { params: { mode } })
// 知识库问答总开关(管理员):关闭后 /ask 拒绝、精灵提示"已被管理员禁用",防嵌入/LLM 计费超支
export const setKbEnabled = (enabled) => http.put('/system/kb-enabled', null, { params: { enabled } })
// 游客体验入口总开关(管理员):关闭后 /auth/guest 拒绝、登录页不渲染游客按钮(部署后若不想开放游客即关)
export const setGuestEnabled = (enabled) => http.put('/system/guest-enabled', null, { params: { enabled } })
