import http from '../utils/request'

// 医师(管理员全局目录;新增同时建 role=3 账号)
export const pageDoctors = (params) => http.get('/doctors', { params })
export const createDoctor = (data) => http.post('/doctors', data)
export const updateDoctor = (id, data) => http.put(`/doctors/${id}`, data)
export const deleteDoctor = (id) => http.delete(`/doctors/${id}`)
export const resetDoctorPassword = (id, password) => http.put(`/doctors/${id}/password`, { password })

// 本医师自管(M5,医师)
export const getDoctorMe = () => http.get('/doctors/me')
export const updateDoctorMe = (data) => http.put('/doctors/me', data)

// 可选药品(上架药品;提交临床反馈时从已有药品选择,自动关联药品与公司)
export const searchDoctorDrugs = (params) => http.get('/doctor-portal/drugs', { params })
