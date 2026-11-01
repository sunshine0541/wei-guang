import { defineStore } from 'pinia'
import { listAppeals, listCommunityPosts, listOrders, listReports } from '../api/admin'

/**
 * 管理端待办：复用现有列表接口取第一页 + total，供侧栏角标与工作台队列。
 * 不新增后端接口；单个请求失败不影响其他队列。
 */
export const useTodoStore = defineStore('adminTodo', {
  state: () => ({
    counts: { reports: 0, appeals: 0, posts: 0, risk: 0 },
    queues: { reports: [], appeals: [], posts: [], risk: [] },
    loading: false,
    loadedAt: 0
  }),
  getters: {
    total(state) {
      return state.counts.reports + state.counts.appeals + state.counts.posts + state.counts.risk
    }
  },
  actions: {
    async load(force = false) {
      if (this.loading) {
        return
      }
      if (!force && this.loadedAt && Date.now() - this.loadedAt < 30000) {
        return
      }
      this.loading = true
      try {
        const results = await Promise.allSettled([
          listReports({ status: 'PENDING', page: 1, pageSize: 5 }),
          listAppeals({ status: 'PENDING', page: 1, pageSize: 5 }),
          listCommunityPosts({ status: 'PENDING_MANUAL', page: 1, pageSize: 5 }),
          listOrders({ riskStatus: 'SUSPICIOUS', page: 1, pageSize: 5 })
        ])
        const keys = ['reports', 'appeals', 'posts', 'risk']
        results.forEach((result, index) => {
          if (result.status === 'fulfilled' && result.value) {
            this.queues[keys[index]] = result.value.records || []
            this.counts[keys[index]] = result.value.total || 0
          }
        })
        this.loadedAt = Date.now()
      } finally {
        this.loading = false
      }
    }
  }
})
