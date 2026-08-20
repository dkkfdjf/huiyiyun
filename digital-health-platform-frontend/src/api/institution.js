import http from '../utils/request'

// 医疗机构(管理员全局目录)
export const listInstitutions = () => http.get('/institutions')                       // 全量(下拉用)
export const pageInstitutions = (params) => http.get('/institutions/page', { params })
export const createInstitution = (data) => http.post('/institutions', data)
export const updateInstitution = (id, data) => http.put(`/institutions/${id}`, data)
// status 为空则在 正常/停用 间翻转;传 1/3 则显式设置
export const toggleInstitutionStatus = (id, status) =>
  http.put(`/institutions/${id}/status`, null, { params: { status } })
export const deleteInstitution = (id) => http.delete(`/institutions/${id}`)

// 医疗机构工作台(机构管理员视角:本院统计)
export const institutionHome = () => http.get('/institution-portal/home')

// 可售药品目录(机构端只读浏览:仅上架药品)
export const pageInstitutionDrugs = (params) => http.get('/institution-portal/drugs', { params })

// 机构向本院医师群发公告(站内通知)
export const sendAnnouncement = (data) => http.post('/institution-portal/announcements', data)
// 本机构已发公告历史(按公告聚合 + 投递人数)
export const listAnnouncements = () => http.get('/institution-portal/announcements')
