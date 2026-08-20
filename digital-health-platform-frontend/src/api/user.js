import http from '../utils/request'

// 账号管理(管理员统一建号/改号)。与后端 RoleConstants 对齐
export const ROLE = { ADMIN: 0, COMPANY: 1, INSTITUTION: 2, DOCTOR: 3 }
export const ROLE_OPTIONS = [
  { value: ROLE.ADMIN, label: '系统管理员' },
  { value: ROLE.COMPANY, label: '医药公司' },
  { value: ROLE.INSTITUTION, label: '医疗机构' },
  { value: ROLE.DOCTOR, label: '医师' }
]
export const roleLabel = (r) => ROLE_OPTIONS.find((o) => o.value === r)?.label || '—'

export const pageUsers = (params) => http.get('/users', { params })
export const createUser = (data) => http.post('/users', data)
export const updateUser = (id, data) => http.put(`/users/${id}`, data)
export const deleteUser = (id) => http.delete(`/users/${id}`)
export const resetUserPassword = (id, password) => http.put(`/users/${id}/password`, { password })
// status 为空则翻转;传 0/1 显式设置
export const toggleUserStatus = (id, status) =>
  http.put(`/users/${id}/status`, null, { params: { status } })
export const unlockUser = (id) => http.put(`/users/${id}/unlock`)
