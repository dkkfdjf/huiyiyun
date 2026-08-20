import http from '../utils/request'

// 药品管理(公司 / 管理员)
export const pageDrugs = (params) => http.get('/drugs', { params })
export const createDrug = (data) => http.post('/drugs', data)
export const updateDrug = (id, data) => http.put(`/drugs/${id}`, data)
export const toggleDrugStatus = (id, status) =>
  http.put(`/drugs/${id}/status`, null, { params: { status } })
export const deleteDrug = (id) => http.delete(`/drugs/${id}`)
