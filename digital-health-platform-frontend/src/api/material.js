import http from '../utils/request'

// 必备材料(管理员维护 / 全角色只读)
export const pageMaterials = (params) => http.get('/materials/page', { params })
export const createMaterial = (data) => http.post('/materials', data)
export const updateMaterial = (id, data) => http.put(`/materials/${id}`, data)
export const deleteMaterial = (id) => http.delete(`/materials/${id}`)

// 常用类别建议(类别为自由文本,前端用 allow-create 下拉兜底自定义)
// 必备材料 = 报销/办事资料清单,故类别按事项划分
export const MATERIAL_CATEGORIES = ['报销类', '慢病类', '特殊病种类', '其他']
