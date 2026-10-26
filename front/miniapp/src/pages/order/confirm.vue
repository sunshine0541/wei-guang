<template>
  <view class="wg-page" :class="userStore.rootClass">
    <view v-if="loading" class="wg-skel">
      <view class="wg-skel-block"></view>
      <view class="wg-skel-block"></view>
      <text class="wg-label" style="margin-top: 6px">正在为您准备，请稍等</text>
    </view>

    <template v-else-if="order">
      <!-- B：拒绝确认后改为说明卡（替代瞬发 toast） -->
      <view v-if="rejectDone" class="wg-card wg-card-tint" style="background-color:#ead9d3;color:#8a5f52">
        <text class="wg-title">已进入申诉</text>
        <view class="wg-label" style="margin-top: 6px;color:#8a5f52;opacity:.9">
          我们已收到您的反馈，管理员会尽快了解情况；有结果时会在通知中心告诉您。
        </view>
        <view class="wg-btn wg-btn-plain" style="margin-top: 12px" @click="goRoom">返回这次求助</view>
      </view>

      <template v-else-if="canConfirmNow">
        <view class="wg-card wg-card-tint" style="background-color:#ead9d3;color:#8a5f52">
          <text class="wg-title">{{ order.title }}</text>
          <view class="wg-label" style="margin-top: 6px;color:#8a5f52;opacity:.85">
            {{ typeLabel(order.orderType) }} · 对方标记了完成，请您确认一下
          </view>
          <!-- C：完成时间 / 本次用时，帮助确认前核对服务时长 -->
          <view v-if="order.startedAt" class="wg-label" style="margin-top: 4px;color:#8a5f52;opacity:.85">
            开始于 {{ formatTime(order.startedAt) }} · 本次用时 {{ durationText }}
          </view>
        </view>
        <view class="wg-btn" @click="agree">确认完成</view>
        <view class="wg-btn wg-btn-plain" style="margin-top: 12px" @click="reject">还有问题（拒绝确认）</view>
      </template>

      <!-- A4：状态守卫——已不在「等待确认」状态时不再渲染确认按钮，避免误操作与来回死循环 -->
      <template v-else>
        <view class="wg-card">
          <text class="wg-title">{{ order.title }}</text>
          <view class="wg-label" style="margin-top: 6px">{{ stateHint }}</view>
        </view>
        <view class="wg-btn wg-btn-plain" @click="goRoom">看看这次求助</view>
      </template>
    </template>

    <view v-else class="wg-card">
      <text>这个页面暂时不能看哦</text>
    </view>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { getOrder } from '@/api/order'
import { confirmOrder } from '@/api/incentive'
import { MESSAGES, ORDER_TYPE_LABELS } from '@/utils/constants'
import { wsClient } from '@/utils/ws'

export default {
  data() {
    return {
      loading: true,
      orderId: null,
      order: null,
      /** B：拒绝确认后展示说明卡的标记 */
      rejectDone: false,
      /** 4D-补5：状态推送退订函数与防抖计时器 */
      wsOff: null,
      wsTimer: null
    }
  },
  computed: {
    userStore() {
      return useUserStore()
    },
    /** A4：仅「等待确认完成」且不是自己发起的完成时，才展示确认按钮 */
    canConfirmNow() {
      return (
        this.order &&
        this.order.status === 'PENDING_CONFIRM' &&
        this.order.myRole &&
        this.order.myRole !== this.order.confirmInitiator
      )
    },
    stateHint() {
      if (!this.order) {
        return ''
      }
      if (this.order.status === 'COMPLETED') {
        return '这次帮助已经完成啦，可以去评价，或者看看这次的光之故事卡'
      }
      if (this.order.status === 'PENDING_CONFIRM') {
        return '您已经标记了完成，等对方确认一下就好'
      }
      return '这次求助已经不在等待确认的状态了，看看最新进展吧'
    },
    /** C：本次用时——已确认的单用完成时间，待确认的单算到此刻 */
    durationText() {
      if (!this.order || !this.order.startedAt) {
        return ''
      }
      const toTime = (value) => new Date(String(value).replace(/-/g, '/').replace('T', ' ')).getTime()
      const start = toTime(this.order.startedAt)
      const end = this.order.completedAt ? toTime(this.order.completedAt) : Date.now()
      if (!start || !end || end <= start) {
        return '不到 1 分钟'
      }
      const minutes = Math.round((end - start) / 60000)
      if (minutes <= 0) {
        return '不到 1 分钟'
      }
      if (minutes < 60) {
        return `${minutes} 分钟`
      }
      const hours = Math.floor(minutes / 60)
      const rest = minutes % 60
      return rest ? `${hours} 小时 ${rest} 分钟` : `${hours} 小时`
    }
  },
  onLoad(options) {
    this.orderId = options.id
    this.load()
    this.bindOrderStatus()
  },
  onUnload() {
    if (this.wsOff) {
      this.wsOff()
      this.wsOff = null
    }
    if (this.wsTimer) {
      clearTimeout(this.wsTimer)
      this.wsTimer = null
    }
  },
  methods: {
    typeLabel(type) {
      return ORDER_TYPE_LABELS[type] || type
    },
    formatTime(value) {
      if (!value) {
        return ''
      }
      return String(value).replace('T', ' ').slice(0, 16)
    },
    /** A4：回到「这次求助」——能返回就返回，否则直接打开该单房间 */
    goRoom() {
      const pages = getCurrentPages()
      if (pages.length > 1) {
        uni.navigateBack()
      } else {
        uni.redirectTo({ url: `/pages/order/room?id=${this.orderId}` })
      }
    },
    async load(silent = false) {
      if (!silent) {
        this.loading = true
      }
      try {
        this.order = await getOrder(this.orderId)
      } catch (e) {
        console.error('[微光] 确认页加载失败', e)
        if (!silent) {
          this.order = null
        }
      } finally {
        if (!silent) {
          this.loading = false
        }
      }
    },
    /** 4D-补5：订阅状态推送，对方取消/申诉/已确认时页面按钮实时更新 */
    bindOrderStatus() {
      wsClient.connect()
      this.wsOff = wsClient.onMessage((envelope) => {
        if (!envelope || envelope.type !== 'ORDER_STATUS') {
          return
        }
        const payload = envelope.payload || {}
        if (String(payload.orderId) !== String(this.orderId)) {
          return
        }
        if (this.wsTimer) {
          clearTimeout(this.wsTimer)
        }
        this.wsTimer = setTimeout(() => {
          this.wsTimer = null
          this.load(true)
        }, 300)
      })
    },
    agree() {
      uni.showModal({
        title: '确认完成',
        content: '确认后，这次帮助就完成啦',
        success: (res) => {
          if (res.confirm) {
            this.doAgree()
          }
        }
      })
    },
    async doAgree() {
      try {
        await confirmOrder(this.orderId, { agree: true })
        uni.showToast({ title: '谢谢您', icon: 'none' })
        setTimeout(() => {
          uni.redirectTo({ url: `/pages/settle/evaluate?id=${this.orderId}` })
        }, 1000)
      } catch (e) {
        console.error('[微光] 确认失败', e)
        uni.showToast({ title: (e && e.message) || MESSAGES.ERROR, icon: 'none' })
        if (e && (e.code === 40002 || e.code === 40003)) {
          this.load(true)
        }
      }
    },
    /** B：预设原因快选，减少打字（"其他原因"仍走手写） */
    reject() {
      const presets = ['态度不好', '没有完成', '时间不符', '其他原因']
      uni.showActionSheet({
        itemList: presets,
        success: (res) => {
          const picked = presets[res.tapIndex]
          if (picked === '其他原因') {
            this.askRejectReason()
            return
          }
          this.doReject(picked)
        }
      })
    },
    askRejectReason() {
      uni.showModal({
        title: '还有问题',
        editable: true,
        placeholderText: '说说遇到了什么问题',
        success: (res) => {
          if (!res.confirm) {
            return
          }
          const reason = (res.content || '').trim()
          if (!reason) {
            uni.showToast({ title: '写一句原因吧', icon: 'none' })
            return
          }
          this.doReject(reason)
        }
      })
    },
    async doReject(reason) {
      try {
        await confirmOrder(this.orderId, { agree: false, reason })
        // B：改为页内说明卡，不再瞬发 toast 后直接退页
        this.rejectDone = true
      } catch (e) {
        console.error('[微光] 拒绝确认失败', e)
        uni.showToast({ title: (e && e.message) || MESSAGES.ERROR, icon: 'none' })
        if (e && (e.code === 40002 || e.code === 40003)) {
          this.load(true)
        }
      }
    }
  }
}
</script>