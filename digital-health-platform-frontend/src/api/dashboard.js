import http from '../utils/request'

// 管理员全局看板(后端 @RequiresRole({0}))
export const getDashboard = () => http.get('/dashboard')

// 管理员近期操作动态(审计日志最近 20 条)。静默失败:动态栏不应因后端短暂不可用而弹错。
export const getActivity = () => http.get('/dashboard/activity', { silent: true })
