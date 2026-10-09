<template>
  <view class="wg-page" :class="userStore.rootClass">
    <view v-if="loading" class="wg-card">
      <text>正在为您准备，请稍等</text>
    </view>

    <template v-else-if="order">
      <view class="wg-card">
        <view class="wg-card-head">
          <text class="wg-title">{{ order.title }}</text>
          <text v-if="order.urgentFlag === 1" class="wg-urgent">急</text>
        </view>
        <view class="wg-label">{{ typeLabel(order.orderType) }} · {{ timeLabel(order.timeExpect) }} · {{ order.createdAt }}</view>
        <view class="wg-tags">
          <text v-for="tag in order.tags" :key="tag" class="wg-tag">{{ tag }}</text>
        </view>
        <text v-if="order.description">{{ order.description }}</text>
      </view>

      <view class="wg-card">
        <view>发布者：{{ order.requester.nickname || '邻居' }}</view>
        <view v-if="order.addressArea" style="margin-top: 6px">地址：{{ order.addressArea }}</view>
        <view v-else-if="order.orderType === 'REMOTE'" class="wg-label" style="margin-top: 6px">
          远程帮助全程线上完成，不需要地址
        </view>
        <view class="wg-label" style="margin-top: 6px">已有 {{ order.viewCount }} 位邻居看过</view>
        <view v-if="order.viewTip" class="wg-label" style="margin-top: 6px">{{ order.viewTip }}</view>
      </view>

      <view v-if="order.mine" class="wg-card">
        <text class="wg-label">这是您发布的求助，接单按钮已置灰</text>
      </view>

      <view v-if="canAccept" class="wg-card">
        <view v-if="gateReason" class="wg-btn wg-btn-off">我愿意帮助</view>
        <view v-else class="wg-btn" @click="confirmAccept">我愿意帮助</view>
        <view v-if="gateReason" class="wg-label" style="margin-top: 8px">{{ gateReason }}</view>
      </view>
    </template>

    <view v-else class="wg-card">
      <text>这个页面暂时不能看哦</text>
    </view>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { acceptOrder, getOrder } from '@/api/order'
import { MESSAGES, ORDER_TYPE_LABELS, TIME_EXPECT_LABELS } from '@/utils/constants'

/* 接单成功文案（详设 §6.5） */
const ACCEPT_OK = '谢谢您，这束微光因您而亮。请尽快点"开始帮忙"，让邻居知道您出发了'
/* 门槛与冲突文案（详设 §6.2 P04；微光值与冷却不在 U1 下发，故由错误码驱动，见《实现偏差清单》D08） */
const ACCEPT_MESSAGES = {
  40001: '手慢了，这束微光已被其他邻居接走啦',
  30002: '您正在接单休息期，远程的帮助依然可以照亮别人。',
  30006: '您的光需要休息一下，远程的帮助依然可以照亮别人。',
  30004: '接这类求助需要先补充年龄信息，去完善一下资料吧'
}

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
    },
    canAccept() {
      return this.order && !this.order.mine && this.order.status === 'PENDING'
    },
    /** 年龄未填的置灰文案；微光值不足/冷却中由后端 30006/30002 返回时提示 */
    gateReason() {
      if (!this.order || this.order.orderType === 'REMOTE') {
        return ''
      }
      return this.userStore.age === null || this.userStore.age === undefined
        ? ACCEPT_MESSAGES[30004]
        : ''
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
    timeLabel(expect) {
      return expect ? TIME_EXPECT_LABELS[expect] || expect : '不限'
    },
    async load() {
      this.loading = true
      try {
        this.order = await getOrder(this.orderId)
      } catch (e) {
        console.error('[微光] 详情加载失败', e)
        this.order = null
      } finally {
        this.loading = false
      }
    },
    confirmAccept() {
      uni.showModal({
        title: '确认接单',
        content: '确认后请尽快点"开始帮忙"，让邻居知道您出发了',
        success: (res) => {
          if (res.confirm) {
            this.doAccept()
          }
        }
      })
    },
    async doAccept() {
      try {
        await acceptOrder(this.orderId)
        uni.showToast({ title: ACCEPT_OK, icon: 'none' })
        this.load()
      } catch (e) {
        console.error('[微光] 接单失败', e)
        const code = e && typeof e.code === 'number' ? e.code : null
        if (code !== null && ACCEPT_MESSAGES[code]) {
          uni.showToast({ title: ACCEPT_MESSAGES[code], icon: 'none' })
        } else if (code !== null && e.message) {
          uni.showToast({ title: e.message, icon: 'none' })
        } else {
          uni.showToast({ title: MESSAGES.NETWORK, icon: 'none' })
        }
        if (code === 40001) {
          this.load()
        }
      }
    }
  }
}
</script>

<style lang="scss">
.wg-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.wg-urgent {
  color: #ffffff;
  background-color: var(--wg-danger);
  border-radius: 6px;
  padding: 2px 8px;
  font-size: var(--wg-font-sm);
}

/* 门槛不足：按钮置灰（详设 §6.2 P04） */
.wg-btn-off {
  background-color: #ded7cb;
  color: #ffffff;
}
</style>
