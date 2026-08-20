import http from '../utils/request'

// 销售网点(公司 / 管理员)
export const listLocations = () => http.get('/locations')                 // 全量(下拉/地图)
export const pageLocations = (params) => http.get('/locations/page', { params })
export const createLocation = (data) => http.post('/locations', data)
export const updateLocation = (id, data) => http.put(`/locations/${id}`, data)
export const deleteLocation = (id) => http.delete(`/locations/${id}`)
