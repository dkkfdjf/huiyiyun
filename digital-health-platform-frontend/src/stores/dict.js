import { defineStore } from 'pinia'

// 字典:Phase 0 仅结构占位;Phase 1+ 接 /dict 接口按 dictType 拉取并缓存。
export const useDictStore = defineStore('dict', {
  state: () => ({
    map: {} // { dictType: [{ key, value }] }
  }),
  getters: {
    labelOf: (s) => (type, key) => {
      const arr = s.map[type] || []
      const hit = arr.find((d) => String(d.key) === String(key))
      return hit ? hit.value : key
    }
  },
  actions: {
    set(type, rows) {
      this.map[type] = rows
    }
  }
})
