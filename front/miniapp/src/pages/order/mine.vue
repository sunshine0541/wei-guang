<template>
  <view class="wg-page" :class="userStore.rootClass">
    <view class="wg-tabs">
      <view class="wg-tab" :class="{ 'wg-tab-active': tab === 'DOING' }" @click="switchTab('DOING')">进行中</view>
      <view class="wg-tab" :class="{ 'wg-tab-active': tab === 'DONE' }" @click="switchTab('DONE')">已了结</view>
      <view class="wg-tab" :class="{ 'wg-tab-active': tab === 'HELPING' }" @click="switchTab('HELPING')">我接的单</view>
    </view>

    <view v-if="orders.length === 0" class="wg-card">
      <text>{{ emptyText }}</text>
    </view>

    <view v-for="order in orders" :key="order.id" class="wg-card" @click="open(order)">
      <view class="wg-card-head">
        <text class="wg-title">{{ order.title }}</text>
        <text v-if="order.urgentFlag === 1" class="wg-urgent">急</text>
      </view>
      <view class="wg-label" style="margin-top: 6px">{{ statusText(order) }}</view>
      <view
        v-if="order.status === 'EXPIRED' && tab !== 'HELPING'"
        class="wg-btn wg-btn-plain"
        style="margin-top: 8px"
        @click.stop="republish(order)"
      >
        一键重发
      </view>
    </view>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { listMyOrders, republishOrder } from '@/api/order'
import { MESSAGES } from '@/utils/constants'

const EMPTY_DOING = "您还没有发布过求助，需要邻居搭把手时，点首页的'发布'吧"

export default {
  data() {
    return {
      tab: 'DOING',
      orders: []
    }
  },
  computed: {
    userStore() {
      return useUserStore()
    },
    emptyText() {
      if (this.tab === 'DOING') {
        return EMPTY_DOING
      }
      if (this.tab === 'HELPING') {
        return '您还没有接过邻居的求助'
      }
      return MESSAGES.EMPTY
    }
  },
  onShow() {
    this.load()
  },
  methods: {
    switchTab(tab) {
      this.tab = tab
      this.load()
    },
    async load() {
      try {
        const result = await listMyOrders({ tab: this.tab, page: 1, pageSize: 50 })
        this.orders = result.records || []
      } catch (e) {
        console.error('[微光] 我的求助加载失败', e)
      }
    },
    statusText(order) {
      // 「我接的单」视角：显示是谁的求助
      if (this.tab === 'HELPING') {
        const who = order.requesterNickname || '邻居'
        const stateMap = {
          ACCEPTED: '等您开始帮忙',
          IN_SERVICE: '正在帮 ' + who,
          PENDING_CONFIRM: '等 ' + who + ' 确认完成',
          COMPLETED: '已帮 ' + who + ' 完成',
          CANCELLED: who + ' 已取消',
          APPEALING: '正在请管理员帮忙看看',
          EXPIRED: '已超时'
        }
        return stateMap[order.status] || order.status
      }
      const name = order.helperNickname || '邻居'
      switch (order.status) {
        case 'PENDING':
          return `正在等邻居看到（已有 ${order.viewCount || 0} 位邻居看过）`
        case 'ACCEPTED':
          return `${name} 正在赶来`
        case 'IN_SERVICE':
          return `${name} 正在帮您`
        case 'PENDING_CONFIRM':
          return '等待确认完成'
        case 'APPEALING':
          return '正在请管理员帮忙看看'
        case 'COMPLETED':
          return '已完成'
        case 'CANCELLED':
          return '已取消'
        case 'EXPIRED':
          return '暂时没有邻居接单'
        default:
          return order.status
      }
    },
    open(order) {
      uni.navigateTo({ url: `/pages/order/room?id=${order.id}` })
    },
    republish(order) {
      uni.showModal({
        title: '一键重发',
        content: '把这条求助再发一次，让更多邻居看到吗？',
        success: async (res) => {
          if (!res.confirm) {
            return
          }
          try {
            await republishOrder(order.id)
            uni.showToast({ title: '已经重新发出去了', icon: 'none' })
            this.load()
          } catch (e) {
            console.error('[微光] 一键重发失败', e)
            uni.showToast({ title: (e && e.message) || MESSAGES.ERROR, icon: 'none' })
          }
        }
      })
    }
  }
}
</script>