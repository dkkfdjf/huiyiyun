import http from '../utils/request'

// 药企公告(药企维护本公司 / 管理员全量代发 / 机构·医师只读)
export const pagePolicies = (params) => http.get('/policies/page', { params })
export const latestPolicies = (n = 8) => http.get('/policies/latest', { params: { n } })
export const createPolicy = (data) => http.post('/policies', data)
export const updatePolicy = (id, data) => http.put(`/policies/${id}`, data)
export const deletePolicy = (id) => http.delete(`/policies/${id}`)

// 枚举(与后端 company_policy.policy_type 对齐:1医保/2药企/3价格)
export const POLICY_TYPE = {
  1: { label: '医保', type: 'success' },
  2: { label: '药企', type: 'primary' },
  3: { label: '价格', type: 'warning' }
}
