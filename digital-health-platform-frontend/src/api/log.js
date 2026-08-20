import http from '../utils/request'

// 审计日志(管理员只读)。start/end 为 yyyy-MM-dd 字符串
export const pageLoginLogs = (params) => http.get('/logs/login', { params })
export const pageOperationLogs = (params) => http.get('/logs/operation', { params })
