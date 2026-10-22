<template>
  <view class="wg-page" :class="userStore.rootClass">
    <view v-if="loading" class="wg-card">
      <text>正在为您准备，请稍等</text>
    </view>

    <template v-else-if="order">
      <view class="wg-card">
        <view class="wg-card-head">
          <text class="wg-subtitle">{{ order.title }}</text>
          <text v-if="order.urgentFlag === 1" class="wg-urgent">急</text>
        </view>
        <view class="wg-label">{{ typeLabel(order.orderType) }} · {{ timeLabel(order.timeExpect) }} · {{ order.createdAt }}</view>
        <view class="wg-tags">
          <text v-for="tag in order.tags" :key="tag" class="wg-tag">{{ tag }}</text>
          <text v-if="order.extraTag" class="wg-tag wg-tag-extra">{{ order.extraTag }}</text>
        </view>
      </view>

      <!-- 情况说明：陶粉色块卡 -->
      <view v-if="order.description" class="wg-card wg-card-soft" style="background-color: #ead9d3; color: #8a5f52">
        <text>{{ order.description }}</text>
      </view>

      <view class="wg-card wg-detail-info">
        <view class="wg-detail-link" @click="goNeighbor">
          <text>发布者：{{ order.requester.nickname || '邻居' }}（点看邻居主页）</text>
          <!-- B：右侧箭头明示该行可点 -->
          <u-icon name="arrow-right" :size="14" />
        </view>
        <view v-if="order.addressArea">地址：{{ order.addressArea }}</view>
        <view v-else-if="order.orderType === 'REMOTE'" class="wg-label">远程帮助全程线上完成，不需要地址</view>
        <view class="wg-label">已有 {{ order.viewCount }} 位邻居看过</view>
        <view v-if="order.viewTip" class="wg-label">{{ order.viewTip }}</view>
      </view>

      <view v-if="order.mine" class="wg-card">
        <text class="wg-label">这是您发布的求助，接单按钮已置灰</text>
      </view>

      <view v-if="canAccept" class="wg-card">
        <!-- 年龄未填：点击跳 P14 画像编辑完善资料（详设 §6.2 P04） -->
        <view v-if="gateReason" class="wg-btn wg-btn-disabled" @click="onGateTap">我愿意帮助</view>
        <view v-else class="wg-btn" @click="confirmAccept">我愿意帮助</view>
        <view v-if="gateReason" class="wg-label" style="margin-top: 8px">{{ gateReason }}</view>
      </view>

      <view v-if="canChat" class="wg-card">
        <view class="wg-btn wg-btn-plain" @click="goChat">去聊天</view>
      </view>

      <view v-if="canMarkDone" class="wg-card">
        <view class="wg-btn" @click="doComplete">标记完成</view>
      </view>

      <view v-if="canConfirm" class="wg-card">
        <view class="wg-btn" @click="goConfirm">去确认</view>
      </view>

      <view v-if="isSettled" class="wg-card">
        <!-- C：助人者视角展示受助者给出的星级 -->
        <view v-if="order.myRole === 'HELPER' && order.helperStars" class="wg-label" style="margin-bottom: 12px">
          受助者给了你 {{ order.helperStars }} 星
        </view>
        <view class="wg-btn wg-btn-plain" @click="goEvaluate">评价 / 悄悄话</view>
        <view class="wg-btn wg-btn-plain" style="margin-top: 12px" @click="goStory">看看这次的故事卡</view>
      </view>

      <view v-if="order.myRole" class="wg-card">
        <view class="wg-btn wg-btn-plain" @click="goRoom">查看进展与操作</view>
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
import { completeOrder } from '@/api/incentive'
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
      /** B：首次进入由 onLoad 加载，之后 onShow 再刷新 */
      entered: false,
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
    /** 已接单后双方都可进入聊天（会话由接单动作创建） */
    canChat() {
      const chatStates = ['ACCEPTED', 'IN_SERVICE', 'PENDING_CONFIRM', 'APPEALING']
      return this.order && chatStates.indexOf(this.order.status) >= 0
    },
    canMarkDone() {
      return this.order && this.order.myRole && this.order.status === 'IN_SERVICE'
    },
    canConfirm() {
      return (
        this.order &&
        this.order.myRole &&
        this.order.status === 'PENDING_CONFIRM' &&
        this.order.myRole !== this.order.confirmInitiator
      )
    },
    isSettled() {
      return this.order && this.order.myRole && this.order.status === 'COMPLETED'
    },
    /**
     * 门槛置灰原因（详设 §6.2 P04 原意：三类门槛均「置灰 + 文案」）：
     * 冷却中 / 微光值不足 / 年龄未填。优先级：冷却 → 微光值 → 年龄。
     * 文案沿用 ACCEPT_MESSAGES 既有原句（30002/30006/30004），不新写。
     * 说明：冷却与微光值门槛由 U1 下发 `coolingDown` / `creditThreshold` 支撑（见《实现偏差清单》D08 更新）。
     */
    gateReason() {
      if (!this.order || this.order.orderType === 'REMOTE') {
        return ''
      }
      if (this.userStore.coolingDown) {
        return ACCEPT_MESSAGES[30002]
      }
      if ((this.userStore.creditScore || 0) < (this.userStore.creditThreshold || 0)) {
        return ACCEPT_MESSAGES[30006]
      }
      return this.userStore.age === null || this.userStore.age === undefined
        ? ACCEPT_MESSAGES[30004]
        : ''
    },
    /** 仅「年龄未填」可点击去完善资料；冷却/微光值不足按详设只置灰、不做动作 */
    gateFixable() {
      if (!this.order || this.order.orderType === 'REMOTE' || !this.gateReason) {
        return false
      }
      if (this.userStore.coolingDown) {
        return false
      }
      return (this.userStore.creditScore || 0) >= (this.userStore.creditThreshold || 0)
    }
  },
  onLoad(options) {
    this.orderId = options.id
    this.load()
  },
  onShow() {
    // B：从聊天/房间返回时刷新状态与浏览数，避免仍显示过期的"我愿意帮助"
    if (this.entered) {
      this.load()
    }
    this.entered = true
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
    goChat() {
      uni.navigateTo({ url: `/pages/chat/room?orderId=${this.orderId}` })
    },
    goNeighbor() {
      const id = this.order && this.order.requester && this.order.requester.id
      if (id) {
        uni.navigateTo({ url: `/pages/neighbor/profile?id=${id}` })
      }
    },
    goConfirm() {
      uni.navigateTo({ url: `/pages/order/confirm?id=${this.orderId}` })
    },
    goRoom() {
      uni.navigateTo({ url: `/pages/order/room?id=${this.orderId}` })
    },
    goEvaluate() {
      uni.navigateTo({ url: `/pages/settle/evaluate?id=${this.orderId}` })
    },
    goStory() {
      uni.navigateTo({ url: `/pages/settle/story?id=${this.orderId}` })
    },
    doComplete() {
      uni.showModal({
        title: '标记完成',
        content: '确认这次帮助已经完成了吗？',
        success: async (res) => {
          if (!res.confirm) {
            return
          }
          try {
            await completeOrder(this.orderId)
            uni.showToast({ title: '已标记完成，等对方确认一下', icon: 'none' })
            this.load()
          } catch (e) {
            console.error('[微光] 标记完成失败', e)
            uni.showToast({ title: (e && e.message) || MESSAGES.ERROR, icon: 'none' })
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
        if (code === 30004) {
          // 需先补填年龄：提示后跳画像编辑（详设 §6.2 P04）
          setTimeout(() => {
            this.goFixProfile()
          }, 800)
        }
      }
    },
    goFixProfile() {
      uni.navigateTo({ url: '/pages/user/profile' })
    },
    /** 置灰按钮点击：仅年龄未填时去完善资料；其余情况按 P04 原意只置灰 + 展示原因 */
    onGateTap() {
      if (this.gateFixable) {
        this.goFixProfile()
      }
    }
  }
}
</script>

<style lang="scss">
/* 发布者 / 地址 / 浏览计数：分级信息卡的行距 */
.wg-detail-info > view {
  margin-top: 6px;
}

.wg-detail-info > view:first-child {
  margin-top: 0;
}

/* B：发布者可点行的箭头排布 */
.wg-detail-link {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
