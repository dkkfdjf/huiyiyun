import http from '../utils/request'

// 数据字典(按类型拉下拉选项,替代写死的选项)
// 常用类型:user_role / audit_status / drug_status / demand_status / demand_type / demand_urgency / policy_type
export const listDict = (type) => http.get(`/dicts/${type}`)
