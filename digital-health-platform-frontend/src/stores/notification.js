import { defineStore } from 'pinia'
import { pageNotifications, unreadCount, markRead as apiMarkRead, markAllRead as apiMarkAllRead } from '../api/notification'

// 站内通知(顶栏铃铛抽屉)。未读数由 WorkbenchLayout 挂载时拉取并 60s 轮询;列表打开抽屉时按筛选拉取。
// 筛选:category(null=全部 0库存/1反馈/2药企/3系统)、keyword(标题或正文模糊)、isRead(null=不限 0未读/1已读)。
export const useNotificationStore = defineStore('notification', {
  state: () => ({
    list: [], total: 0, unread: 0,
    category: null, keyword: '', isRead: null,
    pageNum: 1, pageSize: 20
  }),
  actions: {
    async loadUnread() {
      try { this.unread = await unreadCount() } catch { /* 静默:角标失败不打扰主流程 */ }
    },
    // 按当前 category/keyword/isRead 拉取指定页;reset=true 回到第 1 页(筛选条件变化时用)
    async loadList(pageNum, reset = false) {
      if (reset) this.pageNum = 1
      const p = pageNum || this.pageNum || 1
      const r = await pageNotifications({
        pageNum: p, pageSize: this.pageSize,
        category: this.category ?? undefined,
        keyword: (this.keyword || '').trim() || undefined,
        isRead: this.isRead ?? undefined
      })
      this.list = r?.records || []
      this.total = r?.total || 0
      this.pageNum = p
      await this.loadUnread()
    },
    // 加载更多:在当前筛选下追加下一页(供抽屉"加载更多")
    async loadMore() {
      if (this.list.length >= (this.total || 0)) return
      const next = (this.pageNum || 1) + 1
      const r = await pageNotifications({
        pageNum: next, pageSize: this.pageSize,
        category: this.category ?? undefined,
        keyword: (this.keyword || '').trim() || undefined,
        isRead: this.isRead ?? undefined
      })
      this.list = [...this.list, ...(r?.records || [])]
      this.pageNum = next
    },
    async markRead(id) {
      await apiMarkRead(id)
      const it = this.list.find(n => n.id === id)
      if (it && !it.isRead) { it.isRead = 1; this.unread = Math.max(0, this.unread - 1) }
    },
    async markAllRead() {
      await apiMarkAllRead()
      this.list.forEach(n => { n.isRead = 1 })
      this.unread = 0
    }
  }
})
