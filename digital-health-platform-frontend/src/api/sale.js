import http from '../utils/request'

// 销售出库(公司 / 管理员)— 后端 @Transactional 防超卖
export const sell = (data) => http.post('/sales', data)
export const pageSales = (params) => http.get('/sales', { params })

// 进销存台账(药品×网点 对账汇总:累计入库 − 累计销售 vs 当前库存)
export const ledger = (params) => http.get('/sales/ledger', { params })

// 一键对账修正:以当前库存为准,补历史维护对账修正流水(库存数字不变,仅消台账差异)
export const reconcileLedger = () => http.post('/sales/ledger/reconcile')
