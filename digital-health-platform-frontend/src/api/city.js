import http from '../utils/request'

// 城市(基础数据;列表全角色可读供下拉,增删改仅管理员)
export const listCities = () => http.get('/cities')
// 城市覆盖看板(管理员):每城市 网点/机构/覆盖药企/累计销售额
export const cityCoverage = () => http.get('/cities/coverage')
export const createCity = (data) => http.post('/cities', data)
export const updateCity = (id, data) => http.put(`/cities/${id}`, data)
export const deleteCity = (id) => http.delete(`/cities/${id}`)
