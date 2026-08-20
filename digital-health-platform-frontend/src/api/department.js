import http from '../utils/request'

// 科室(管理员全局目录)
export const listDepartments = (params) => http.get('/departments', { params })   // params.institutionId 为空=全部
export const createDepartment = (data) => http.post('/departments', data)
export const updateDepartment = (id, data) => http.put(`/departments/${id}`, data)
export const deleteDepartment = (id) => http.delete(`/departments/${id}`)
