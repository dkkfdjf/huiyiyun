import http from '../utils/request'

// 临床用药反馈(医师提交 / 公司处理 / 管理员指派)
export const pageDemands = (params) => http.get('/demands', { params })
export const createDemand = (data) => http.post('/demands', data)
export const withdrawDemand = (id) => http.put(`/demands/${id}/withdraw`)
export const acceptDemand = (id) => http.put(`/demands/${id}/accept`)
// reply 选填:标记已满足时可空
export const satisfyDemand = (id, reply) => http.put(`/demands/${id}/satisfy`, { reply })
// reply 必填:驳回须填写原因(后端服务层校验)
export const rejectDemand = (id, reply) => http.put(`/demands/${id}/reject`, { reply })
export const assignDemand = (id, companyId) => http.put(`/demands/${id}/assign`, { companyId })

// 枚举映射(与后端 drug_demand 的 status / demand_type / urgency 对齐)
export const DEMAND_STATUS = {
  0: { label: '待处理', type: 'info' },
  1: { label: '处理中', type: 'warning' },
  2: { label: '已满足', type: 'success' },
  3: { label: '已驳回', type: 'danger' },
  4: { label: '已撤回', type: 'info' }
}
export const DEMAND_TYPE = { 1: '临床用药需求', 2: '临床用量反馈' }
export const URGENCY = { 1: '一般', 2: '紧急' }
