import { defineStore } from 'pinia'
import { getNotifyBanner } from '@/api/notify'
import { wsClient } from '@/utils/ws'

/**
 * 通知态（详设 §1.3 store/notify：红点/横幅）。
 * 横幅与红点数据同源（N2）；在线收到 NOTIFICATION 推送时自动刷新（详设 §3.5）。
 */
export const useNotifyStore = defineStore('notify', {
  state: () => ({
    hasUnread: false,
    latest: null,
    extraCount: 0,
    bannerText: '',
    wsBound: false
  }),

  actions: {
    async refresh() {
      try {
        const data = await getNotifyBanner()
        this.hasUnread = data.hasUnread === true
        this.latest = data.latest || null
        this.extraCount = data.extraCount || 0
        this.bannerText = data.bannerText || ''
      } catch (e) {
        console.error('[微光] 通知横幅刷新失败', e)
      }
      this.bindWs()
    },

    /** 仅注册一次：在线收到通知推送时刷新横幅（详设 §3.5 离线落库、在线即时推送） */
    bindWs() {
      if (this.wsBound) {
        return
      }
      this.wsBound = true
      wsClient.onMessage((envelope) => {
        if (envelope && envelope.type === 'NOTIFICATION') {
          this.refresh()
        }
      })
    }
  }
})