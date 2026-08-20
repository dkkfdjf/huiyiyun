import { defineStore } from 'pinia'

// 全局告警计数(顶栏铃铛角标)。Phase 0 由看板库存预警数填充。
export const useAlertStore = defineStore('alert', {
  state: () => ({
    count: 0
  }),
  actions: {
    set(n) {
      this.count = Number(n) || 0
    },
    clear() {
      this.count = 0
    }
  }
})
