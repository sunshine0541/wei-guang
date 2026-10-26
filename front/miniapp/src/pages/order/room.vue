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
        <view class="wg-label" style="margin-top: 6px">{{ typeLabel(order.orderType) }} · {{ statusLabel }}</view>
      </view>

      <view v-if="session && (session.addressDetail || session.addressArea)" class="wg-card wg-label">
        <text v-if="session.addressDetail">楼栋门牌：{{ session.addressDetail }}</text>
        <text v-else>地点：{{ session.addressArea }}</text>
      </view>

      <view class="wg-card">
        <view class="wg-label">对方：{{ session ? session.peerNickname || '邻居' : '邻居' }}</view>
      </view>

      <view class="wg-card">
        <view class="wg-label">进展</view>
        <view v-if="timeline.length === 0" class="wg-label" style="margin-top: 6px">还没有记录</view>
        <view v-for="(item, index) in timeline" :key="index" class="wg-room-tl-row">
          <u-icon name="clock" :size="14" />
          <view class="wg-label wg-room-tl-text">
            {{ timelineLabel(item) }}<template v-if="item.remark"> · {{ item.remark }}</template>
          </view>
        </view>
      </view>

      <view v-if="canChat" class="wg-card">
        <view class="wg-btn wg-btn-plain" @click="goChat">去聊天</view>
      </view>

      <view v-if="isHelper && order.status === 'ACCEPTED'" class="wg-card">
        <view class="wg-btn" @click="doStart">开始帮忙</view>
        <view class="wg-btn wg-btn-danger" style="margin-top: 8px" @click="doQuit">放弃这次帮忙</view>
      </view>

      <view v-if="isHelper && order.status === 'IN_SERVICE'" class="wg-card">
        <view class="wg-btn wg-btn-plain" @click="doHelpless">实在帮不上</view>
        <view class="wg-btn wg-btn-danger" style="margin-top: 8px" @click="doLost">中途退出</view>
      </view>

      <view v-if="order.status === 'IN_SERVICE'" class="wg-card">
        <view class="wg-btn wg-btn-plain" @click="doNegotiate">商量取消</view>
      </view>

      <view v-if="canComplete" class="wg-card">
        <view class="wg-btn" @click="doComplete">标记完成</view>
      </view>

      <view v-if="canAnswerCancel" class="wg-card">
        <view class="wg-label">对方发起了"商量取消"</view>
        <view class="wg-btn" style="margin-top: 8px" @click="answerCancel(true)">同意取消</view>
        <view class="wg-btn wg-btn-plain" style="margin-top: 8px" @click="answerCancel(false)">不同意</view>
      </view>

      <view v-if="isRequester && order.status === 'PENDING'" class="wg-card">
        <view class="wg-btn wg-btn-plain" @click="goEdit">修改这条求助</view>
        <view class="wg-btn wg-btn-plain" style="margin-top: 8px" @click="confirmWithdraw">撤回这条求助</view>
      </view>

      <view v-if="isRequester && canCancel" class="wg-card">
        <view class="wg-btn wg-btn-plain" @click="doCancel">取消这次求助</view>
      </view>

      <view v-if="canConfirm" class="wg-card">
        <view class="wg-btn" @click="goConfirm">去确认</view>
      </view>

      <view v-if="isSettled" class="wg-card">
        <!-- B：评价与悄悄话拆成两个按钮，同进 P10，分别锚点定位 -->
        <view v-if="isRequester" class="wg-btn wg-btn-plain" @click="goEvaluate">去评价</view>
        <view class="wg-btn wg-btn-plain" :style="isRequester ? 'margin-top: 8px' : ''" @click="goWhisper">写悄悄话</view>
        <view class="wg-btn wg-btn-plain" style="margin-top: 8px" @click="goStory">看看这次的故事卡</view>
      </view>

      <view v-if="order.canAppeal" class="wg-card">
        <view class="wg-btn wg-btn-plain" @click="goAppeal">去申诉</view>
      </view>
    </template>

    <view v-else class="wg-card">
      <text>这个页面暂时不能看哦</text>
    </view>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { cancelConfirmOrder, cancelOrder, getOrder, getOrderTimeline, releaseOrder, startOrder } from '@/api/order'
import { completeOrder } from '@/api/incentive'
import { getSession } from '@/api/chat'
import { MESSAGES, ORDER_TYPE_LABELS, TIMELINE_LABELS } from '@/utils/constants'
import { wsClient } from '@/utils/ws'

const STATUS_LABELS = {
  PENDING: '正在等邻居看到',
  ACCEPTED: '邻居正在赶来',
  IN_SERVICE: '邻居正在帮忙',
  PENDING_CONFIRM: '等待确认完成',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  EXPIRED: '暂时没有邻居接单',
  APPEALING: '正在请管理员帮忙看看'
}

/* P07 写死文案（详设 §6.2；扣分数值与免罚次数取 sys_config 默认值，见《实现偏差清单》D14） */
const QUIT_CONFIRM = '确定要放弃吗？这会影响您的微光值（-5）。'
const HELPLESS_PROMPT = '如果确实帮不上，可以如实告诉我们原因，这次不会影响您的微光值（7 天内最多 3 次）。'
const LOST_CONFIRM = '中途退出会被记为违约（微光值 -15），确定要退出吗？'

export default {
  data() {
    return {
      loading: true,
      orderId: null,
      order: null,
      session: null,
      timeline: [],
      /** A4：首次进入由 onLoad 完成加载，之后每次 onShow（从确认/评价等页返回）再刷新 */
      entered: false,
      /** 4D-补3：状态推送退订函数与防抖计时器 */
      wsOff: null,
      wsTimer: null
    }
  },
  computed: {
    userStore() {
      return useUserStore()
    },
    isRequester() {
      return this.order && this.order.myRole === 'REQUESTER'
    },
    isHelper() {
      return this.order && this.order.myRole === 'HELPER'
    },
    statusLabel() {
      return this.order ? STATUS_LABELS[this.order.status] || this.order.status : ''
    },
    canChat() {
      const states = ['ACCEPTED', 'IN_SERVICE', 'PENDING_CONFIRM', 'APPEALING']
      return this.order && this.order.myRole && states.indexOf(this.order.status) >= 0
    },
    canComplete() {
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
    canAnswerCancel() {
      return (
        this.order &&
        this.order.myRole &&
        this.order.cancelPendingBy &&
        this.order.cancelPendingBy !== this.order.myRole
      )
    },
    canCancel() {
      return this.order && ['ACCEPTED', 'IN_SERVICE'].indexOf(this.order.status) >= 0
    },
    isSettled() {
      return this.order && this.order.myRole && this.order.status === 'COMPLETED'
    }
  },
  onLoad(options) {
    this.orderId = options.id
    this.load()
    this.bindOrderStatus()
  },
  onShow() {
    // A4：从「确认完成」（redirectTo 评价页后返回）等页面回到本页时重新拉取状态，
    // 否则状态陈旧会一直显示"去确认"，与确认页来回形成死循环
    if (this.entered) {
      this.load(true)
    }
    this.entered = true
  },
  onUnload() {
    // 4D-补3：离开页面退订，避免旧页面继续刷新
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
    timelineLabel(item) {
      return TIMELINE_LABELS[item.eventType] || item.eventType
    },
    /**
     * 拉取房间数据。silent=true 用于状态推送/操作后的后台刷新，不闪骨架屏、失败不把页面清空。
     */
    async load(silent = false) {
      if (!silent) {
        this.loading = true
      }
      try {
        this.order = await getOrder(this.orderId)
        this.timeline = await getOrderTimeline(this.orderId)
      } catch (e) {
        console.error('[微光] 订单房间加载失败', e)
        if (!silent) {
          this.order = null
        }
        if (e && e.code === 20003) {
          // 已非参与方（如已释放/被取消后从旧入口进入）：稍后自动返回，不停留在错误页
          setTimeout(() => {
            uni.navigateBack({
              fail: () => {
                uni.reLaunch({ url: '/pages/index/index' })
              }
            })
          }, 1500)
        }
      } finally {
        if (!silent) {
          this.loading = false
        }
      }
      // A3：仅在「可聊天」状态才拉会话；PENDING/已结束等状态本就没有会话，
      // 之前无条件拉取会被后端以 40002 拒绝并弹「当前状态不允许该操作」，属误报。
      if (this.canChat) {
        try {
          this.session = await getSession(this.orderId)
        } catch (e) {
          this.session = null
        }
      } else {
        this.session = null
      }
    },
    /** 4D-补3：订阅本单状态推送，另一端点完成/取消时按钮实时变化 */
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
    askReason(title, placeholder, onDone) {
      uni.showModal({
        title,
        editable: true,
        placeholderText: placeholder,
        success: (res) => {
          if (!res.confirm) {
            return
          }
          const reason = (res.content || '').trim()
          if (!reason) {
            uni.showToast({ title: '写一句原因吧', icon: 'none' })
            return
          }
          onDone(reason)
        }
      })
    },
    async action(promise, okText) {
      try {
        await promise
        uni.showToast({ title: okText, icon: 'none' })
        this.load(true)
      } catch (e) {
        console.error('[微光] 操作失败', e)
        uni.showToast({ title: (e && e.message) || MESSAGES.ERROR, icon: 'none' })
        // 4D-补3：状态已被对方改掉时自动刷新，避免继续点旧按钮
        if (e && (e.code === 40002 || e.code === 40003)) {
          this.load(true)
        }
      }
    },
    /**
     * 释放类动作成功后的去向：释放后本单已没有接单者（重派中），刷新房间会取不到数据（20003），
     * 故提示后返回上一页（列表/详情），避免停在房间显示"暂时不能看"。
     */
    async releaseAction(promise, okText) {
      try {
        await promise
        uni.showToast({ title: okText, icon: 'none' })
        setTimeout(() => {
          uni.navigateBack({
            fail: () => {
              uni.reLaunch({ url: '/pages/index/index' })
            }
          })
        }, 1200)
      } catch (e) {
        console.error('[微光] 释放失败', e)
        uni.showToast({ title: (e && e.message) || MESSAGES.ERROR, icon: 'none' })
      }
    },
    doStart() {
      this.action(startOrder(this.orderId), '已告诉对方您开始帮忙啦。')
    },
    doQuit() {
      uni.showModal({
        title: '放弃这次帮忙',
        content: QUIT_CONFIRM,
        success: (res) => {
          if (res.confirm) {
            this.releaseAction(releaseOrder(this.orderId, { reasonType: 'HELPER_QUIT', reasonText: '主动放弃' }), '已为您重新寻找邻居')
          }
        }
      })
    },
    doHelpless() {
      this.askReason('实在帮不上', HELPLESS_PROMPT, (reason) => {
        this.releaseAction(releaseOrder(this.orderId, { reasonType: 'HELPLESS', reasonText: reason }), '已为您重新寻找邻居')
      })
    },
    doLost() {
      uni.showModal({
        title: '中途退出',
        content: LOST_CONFIRM,
        success: (res) => {
          if (res.confirm) {
            this.releaseAction(releaseOrder(this.orderId, { reasonType: 'HELPER_LOST', reasonText: '中途退出' }), '已为您重新寻找邻居')
          }
        }
      })
    },
    doNegotiate() {
      this.askReason('商量取消', '说说想商量的原因', (reason) => {
        this.action(cancelOrder(this.orderId, { reason, mode: 'NEGOTIATE' }), '已经问过对方啦')
      })
    },
    doCancel() {
      this.askReason('取消这次求助', '说说取消的原因', (reason) => {
        this.action(cancelOrder(this.orderId, { reason, mode: 'CANCEL' }), '已取消')
      })
    },
    goEdit() {
      uni.navigateTo({ url: `/pages/order/edit?id=${this.orderId}` })
    },
    confirmWithdraw() {
      uni.showModal({
        title: '撤回这条求助',
        content: '撤回后，这条求助就不再展示给邻居了。确定撤回吗？',
        confirmText: '撤回',
        cancelText: '再想想',
        success: (res) => {
          if (res.confirm) {
            this.doWithdraw()
          }
        }
      })
    },
    async doWithdraw() {
      try {
        // 撤回仅针对仍处于待响应的单；若刚被接单，刷新页面让入口自然消失
        const latest = await getOrder(this.orderId)
        if (!latest || latest.status !== 'PENDING') {
          this.order = latest
          return
        }
        await cancelOrder(this.orderId, { reason: '发布者撤回', mode: 'CANCEL' })
        uni.showToast({ title: '已撤回这条求助', icon: 'none' })
        setTimeout(() => {
          uni.navigateBack({ fail: () => uni.reLaunch({ url: '/pages/order/mine' }) })
        }, 800)
      } catch (e) {
        console.error('[微光] 撤回失败', e)
        uni.showToast({ title: (e && e.message) || MESSAGES.ERROR, icon: 'none' })
      }
    },
    answerCancel(agree) {
      this.action(cancelConfirmOrder(this.orderId, { agree }), agree ? '已取消' : '已回复对方')
    },
    doComplete() {
      uni.showModal({
        title: '标记完成',
        content: '确认这次帮助已经完成了吗？',
        success: (res) => {
          if (res.confirm) {
            this.action(completeOrder(this.orderId), '已标记完成，等对方确认一下')
          }
        }
      })
    },
    goChat() {
      uni.navigateTo({ url: `/pages/chat/room?orderId=${this.orderId}` })
    },
    goConfirm() {
      uni.navigateTo({ url: `/pages/order/confirm?id=${this.orderId}` })
    },
    goEvaluate() {
      uni.navigateTo({ url: `/pages/settle/evaluate?id=${this.orderId}&focus=evaluate` })
    },
    goWhisper() {
      uni.navigateTo({ url: `/pages/settle/evaluate?id=${this.orderId}&focus=whisper` })
    },
    goStory() {
      uni.navigateTo({ url: `/pages/settle/story?id=${this.orderId}` })
    },
    goAppeal() {
      uni.navigateTo({
        url: `/pages/user/appeal?orderId=${this.orderId}`,
        fail: (err) => {
          console.error('[微光] 打开申诉页失败', err)
          uni.showToast({ title: '页面没打开，请再试一次', icon: 'none' })
        }
      })
    }
  }
}
</script>

<style lang="scss">
/* 进展时间线：时钟图标 + 说明文案 */
.wg-room-tl-row {
  display: flex;
  align-items: flex-start;
  margin-top: 6px;
}

.wg-room-tl-text {
  flex: 1;
  margin-left: 6px;
}
</style>