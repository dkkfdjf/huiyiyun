import http from '../utils/request'

// 药企(管理员目录管理;listCompanies 只列正常态,供下拉用)
export const listCompanies = () => http.get('/companies')                       // 正常态(下拉用)
export const pageCompanies = (params) => http.get('/companies/page', { params })
export const getCompany = (id) => http.get(`/companies/${id}`)
export const createCompany = (data) => http.post('/companies', data)
export const updateCompany = (id, data) => http.put(`/companies/${id}`, data)
// 启停:status 为空则在 正常/停用 间翻转;传 1/3 显式设置
export const toggleCompanyStatus = (id, status) =>
  http.put(`/companies/${id}/status`, null, { params: { status } })
export const deleteCompany = (id) => http.delete(`/companies/${id}`)

// 本公司自管(M1,公司用户)
export const getCompanyMe = () => http.get('/companies/me')
export const updateCompanyMe = (data) => http.put('/companies/me', data)
export const getCompanyDevelopment = () => http.get('/companies/development')
