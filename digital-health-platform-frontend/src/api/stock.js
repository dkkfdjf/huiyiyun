import http from '../utils/request'

// 库存管理(公司 / 管理员)
export const initStock = (data) => http.post('/stocks/init', data)
export const pageStocks = (params) => http.get('/stocks', { params })
export const stockAlerts = () => http.get('/stocks/alerts')
// 管理员提醒归属药企补货(站内通知)
export const remindReplenish = (stockId) => http.post(`/stocks/alerts/${stockId}/remind`)
export const updateStock = (data) => http.put('/stocks', data)
