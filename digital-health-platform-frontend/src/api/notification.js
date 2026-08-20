import http from '../utils/request'

// 站内通知:铃铛未读数 / 列表 / 已读
export const pageNotifications = (params) => http.get('/notifications', { params })
export const unreadCount = () => http.get('/notifications/unread', { silent: true })
export const markRead = (id) => http.put(`/notifications/${id}/read`)
export const markAllRead = () => http.put('/notifications/read-all')
