import http from '../utils/request'

// 首页落地页公开概览(免登录)。失败静默——落地页不应因后端短暂不可用而弹错。
export const getPublicStats = () => http.get('/public/stats', { silent: true })
