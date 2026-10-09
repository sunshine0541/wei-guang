<template>
  <view class="wg-page" :class="userStore.rootClass">
    <view v-if="loading" class="wg-card">
      <text>正在为您准备，请稍等</text>
    </view>

    <template v-else-if="order">
      <view class="wg-card">
        <text class="wg-title">{{ order.title }}</text>
        <view class="wg-label" style="margin-top: 6px">
          {{ typeLabel(order.orderType) }} · 对方标记了完成，请您确认一下
        </view>
      </view>
      <view class="wg-btn" @click="agree">确认完成</view>
      <view class="wg-btn wg-btn-plain" style="margin-top: 12px" @click="reject">还有问题（拒绝确认）</view>
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

export default {
  data() {
    return {
      loading: true,
      orderId: null,
      order: null
    }
  },
  computed: {
    userStore() {
      return useUserStore()
    }
  },
  onLoad(options) {
    this.orderId = options.id
    this.load()
  },
  methods: {
    typeLabel(type) {
      return ORDER_TYPE_LABELS[type] || type
    },
    async load() {
      this.loading = true
      try {
        this.order = await getOrder(this.orderId)
      } catch (e) {
        console.error('[微光] 确认页加载失败', e)
        this.order = null
      } finally {
        this.loading = false
      }
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
      }
    },
    reject() {
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
        uni.showToast({ title: '我们请管理员来帮忙看看', icon: 'none' })
        setTimeout(() => {
          uni.navigateBack()
        }, 1500)
      } catch (e) {
        console.error('[微光] 拒绝确认失败', e)
        uni.showToast({ title: (e && e.message) || MESSAGES.ERROR, icon: 'none' })
      }
    }
  }
}
</script>