import http from '../utils/request'

// 补货入库(公司 / 管理员)
export const replenish = (data) => http.post('/replenish', data)
export const pageReplenish = (params) => http.get('/replenish', { params })
