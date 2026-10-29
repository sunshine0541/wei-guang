<template>
  <view class="wg-page wg-page-tab" :class="userStore.rootClass">
    <view v-if="loading" class="wg-skel">
      <view class="wg-skel-block"></view>
      <view class="wg-skel-block"></view>
      <text class="wg-label" style="margin-top: 6px">{{ loadingText }}</text>
    </view>

    <template v-else>
      <!-- 全部已读：置于列表顶部（需求方反馈：原在列表底部，长列表需滚动才够到） -->
      <view v-if="hasUnread" class="wg-notify-bar">
        <text class="wg-notify-unread">{{ unreadCount }} 条未读</text>
        <view class="wg-btn wg-btn-plain wg-notify-readall" @click="readAll">{{ readAllText }}</view>
      </view>

      <view v-if="list.length === 0" class="wg-empty">{{ emptyText }}</view>

      <!-- B：聊天消息聚合卡（点击只展开，不跳转；点某一行才进对应聊天室） -->
      <view v-if="chatGroups.length > 0" class="wg-card wg-chat-digest">
        <view class="wg-chat-digest-head" @click="chatExpanded = !chatExpanded">
          <text class="wg-subtitle">有 {{ chatGroups.length }} 位邻居给您发来 {{ chatTotal > 9 ? '9+' : chatTotal }} 条消息</text>
          <u-icon :name="chatExpanded ? 'arrow-up' : 'arrow-down'" :size="16" />
        </view>
        <view v-if="chatExpanded" class="wg-chat-digest-list">
          <view
            v-for="group in chatGroups"
            :key="group.orderId"
            class="wg-chat-digest-row"
            @click="openChatGroup(group)"
          >
            <text class="wg-chat-digest-name">{{ group.nickname || '邻居' }}</text>
            <view
              class="wg-chat-digest-count"
              :class="{ 'wg-chat-digest-count-max': group.count > 9 }"
            >
              {{ group.count > 9 ? '9+' : group.count }}
            </view>
            <u-icon name="arrow-right" :size="14" />
          </view>
        </view>
      </view>

      <!-- B：按今天 / 更早分组，长列表更易扫读 -->
      <template v-for="group in groups" :key="group.label">
        <view class="wg-notify-group">{{ group.label }}</view>
        <view
          v-for="item in group.items"
          :key="item.id"
          class="wg-notify-swipe"
          @touchstart="onTouchStart($event, item)"
          @touchmove="onTouchMove($event, item)"
          @touchend="onTouchEnd"
        >
          <!-- B：右滑露出「标记已读」（仅未读）；左滑露出删除 -->
          <view v-if="item.isRead === 0" class="wg-notify-read" @click.stop="markRead(item)">标记已读</view>
          <view class="wg-notify-del" @click.stop="remove(item)">删除</view>
          <view class="wg-card wg-notify-slide" :style="slideStyle(item)" @click="openItem(item)">
            <view class="wg-card-head">
              <view class="wg-notify-title-wrap">
                <view class="wg-notify-ico" :style="iconStyle(item.type)">
                  <u-icon :name="iconOf(item.type)" :size="17" />
                </view>
                <text class="wg-notify-title">{{ item.title }}</text>
              </view>
              <view v-if="item.isRead === 0" class="wg-dot" />
            </view>
            <!-- 通知内容两种模式都展示：悄悄话等以通知为唯一查看渠道（D76） -->
            <view class="wg-notify-content">{{ item.content }}</view>
            <view class="wg-label" style="margin-top: 6px">{{ item.createdAt }}</view>
            <!-- 年长模式：除滑动手势外提供显式按钮，避免目标用户难以发现 -->
            <view v-if="userStore.isElder" class="wg-notify-elder-actions">
              <view v-if="item.isRead === 0" class="wg-btn wg-btn-plain" @click.stop="markRead(item)">标记已读</view>
              <view class="wg-btn wg-btn-plain" @click.stop="remove(item)">删除</view>
            </view>
          </view>
        </view>
      </template>

      <view v-if="loadingMore" class="wg-feed-more wg-label">正在加载…</view>
      <view v-else-if="list.length && finished" class="wg-feed-more wg-label">已经到底啦</view>
    </template>

    <WgTabBar :active="3" />
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { useNotifyStore } from '@/store/notify'
import { listNotifications, readAllNotifications, readNotification, deleteNotification } from '@/api/notify'
import { MESSAGES } from '@/utils/constants'
import { wsClient } from '@/utils/ws'
import WgTabBar from '@/components/WgTabBar.vue'

/* P16 写死文案（详设 §6.2） */
const EMPTY_TEXT = '暂无新消息'
const READ_ALL_TEXT = '全部已读'

/* 通知类型图标（详设 §4.4 + v1.3 §15.6 + T30 新增，现 22 类）：五色低饱和分色，图标用 uview-plus 字体图标 */
const TYPE_ICONS = {
  NEW_MESSAGE: { icon: 'chat-fill', bg: '#dbe3e7', fg: '#4e6b78' },
  ACCEPTED: { icon: 'man-add', bg: '#d9e2d6', fg: '#4f6b4a' },
  SERVICE_STARTED: { icon: 'arrow-right', bg: '#dbe3e7', fg: '#4e6b78' },
  CONFIRM_REQUEST: { icon: 'edit-pen', bg: '#ede3d0', fg: '#8a7648' },
  SETTLED: { icon: 'checkmark-circle-fill', bg: '#d9e2d6', fg: '#4f6b4a' },
  RELEASED: { icon: 'reload', bg: '#e4deec', fg: '#63568c' },
  EXPIRED: { icon: 'clock', bg: '#ede3d0', fg: '#8a7648' },
  APPEAL_RESULT: { icon: 'file-text', bg: '#e4deec', fg: '#63568c' },
  COOLDOWN: { icon: 'minus-circle', bg: '#ede3d0', fg: '#8a7648' },
  POINTS_CREDITED: { icon: 'gift', bg: '#ede3d0', fg: '#8a7648' },
  CREDIT_CHANGED: { icon: 'integral', bg: '#d9e2d6', fg: '#4f6b4a' },
  ADMIN_ACTION: { icon: 'warning', bg: '#ead9d3', fg: '#8a5f52' },
  FEEDBACK_RESULT: { icon: 'email', bg: '#ead9d3', fg: '#8a5f52' },
  REPORT_RESULT: { icon: 'checkmark-circle', bg: '#d9e2d6', fg: '#4f6b4a' },
  CANCEL_NOTICE: { icon: 'close-circle', bg: '#ead9d3', fg: '#8a5f52' },
  CANCEL_REQUEST: { icon: 'chat', bg: '#e4deec', fg: '#63568c' },
  CANCEL_REJECTED: { icon: 'close-circle', bg: '#ead9d3', fg: '#8a5f52' },
  CONFIRM_REJECT: { icon: 'close', bg: '#ead9d3', fg: '#8a5f52' },
  APPEAL_OPEN: { icon: 'file-text', bg: '#e4deec', fg: '#63568c' },
  POST_REVIEW_RESULT: { icon: 'eye', bg: '#dbe3e7', fg: '#4e6b78' },
  COMMENT_REPLY: { icon: 'chat-fill', bg: '#d9e2d6', fg: '#4f6b4a' },
  WHISPER: { icon: 'heart', bg: '#ead9d3', fg: '#8a5f52' }
}

export default {
  components: { WgTabBar },
  data() {
    return {
      loading: true,
      list: [],
      page: 1,
      pageSize: 20,
      finished: false,
      loadingMore: false,
      loadingText: MESSAGES.LOADING,
      emptyText: EMPTY_TEXT,
      readAllText: READ_ALL_TEXT,
      reqSeq: 0,
      /** B：左滑删除状态（同时只展开一条） */
      slideId: null,
      slideX: 0,
      touchX: 0,
      touchY: 0,
      dragging: false,
      /** B：聊天聚合卡是否展开 */
      chatExpanded: false,
      /** B：WS 通知监听解绑函数 */
      offWs: null
    }
  },
  computed: {
    userStore() {
      return useUserStore()
    },
    notifyStore() {
      return useNotifyStore()
    },
    hasUnread() {
      return this.list.some((item) => item.isRead === 0)
    },
    unreadCount() {
      const others = this.list.filter((item) => item.isRead === 0 && item.type !== 'NEW_MESSAGE').length
      return others + this.chatTotal
    },
    /** B：未读聊天通知按会话聚合（一个会话=一位联系人） */
    chatGroups() {
      const map = {}
      this.list.forEach((item) => {
        if (item.type !== 'NEW_MESSAGE' || item.isRead !== 0) {
          return
        }
        const key = item.jumpId
        if (!map[key]) {
          map[key] = {
            orderId: item.jumpId,
            nickname: item.senderNickname || this.nicknameFromContent(item.content),
            count: 0,
            ids: []
          }
        }
        map[key].count += item.messageCount || 1
        map[key].ids.push(item.id)
      })
      return Object.values(map)
    },
    chatTotal() {
      return this.chatGroups.reduce((sum, group) => sum + group.count, 0)
    },
    /** B：今天 / 更早两组（按 createdAt 的日期部分比较；聊天聚合卡单独渲染，不混入时间线） */
    groups() {
      const today = this.todayKey()
      const buckets = [
        { label: '今天', items: [] },
        { label: '更早', items: [] }
      ]
      this.list
        .filter((item) => item.type !== 'NEW_MESSAGE')
        .forEach((item) => {
          const key = String(item.createdAt || '').replace('T', ' ').slice(0, 10)
          buckets[key === today ? 0 : 1].items.push(item)
        })
      return buckets.filter((bucket) => bucket.items.length > 0)
    }
  },
  onLoad() {
    // B：停留本页时实时刷新列表（否则要切走再回来才更新）
    wsClient.connect()
    this.offWs = wsClient.onMessage((envelope) => {
      if (envelope && envelope.type === 'NOTIFICATION') {
        this.load()
      }
    })
  },
  onReachBottom() {
    if (!this.finished && !this.loadingMore) {
      this.page += 1
      this.load(false)
    }
  },
  onUnload() {
    if (this.offWs) {
      this.offWs()
    }
  },
  onShow() {
    this.load()
    // A2：隐藏左上角「返回首页⌂」（reLaunch 后页面栈只剩本页，微信会自动加该按钮）
    if (typeof wx !== 'undefined' && typeof wx.hideHomeButton === 'function') {
      wx.hideHomeButton()
    }
  },
  methods: {
    todayKey() {
      const d = new Date()
      const pad = (n) => String(n).padStart(2, '0')
      return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
    },
    /** B：兼容旧数据——从「{昵称} 给您发来新消息」里取昵称 */
    nicknameFromContent(content) {
      const text = String(content || '')
      const index = text.indexOf(' 给您发来')
      return index > 0 ? text.slice(0, index) : ''
    },
    /** B：点聚合行——标记该会话通知已读并进入聊天室 */
    async openChatGroup(group) {
      try {
        await Promise.all(group.ids.map((id) => readNotification(id)))
        this.notifyStore.refresh()
      } catch (e) {
        console.error('[微光] 聊天通知已读失败', e)
      }
      uni.navigateTo({ url: `/pages/chat/room?orderId=${group.orderId}` })
    },
    iconOf(type) {
      const entry = TYPE_ICONS[type]
      return entry ? entry.icon : 'bell'
    },
    iconStyle(type) {
      const entry = TYPE_ICONS[type]
      return entry
        ? { backgroundColor: entry.bg, color: entry.fg }
        : { backgroundColor: '#dbe3e7', color: '#4e6b78' }
    },
    async load(reset = true) {
      if (!reset && (this.loadingMore || this.finished)) {
        return
      }
      if (reset) {
        this.page = 1
        this.finished = false
      }
      const seq = ++this.reqSeq
      this.loadingMore = true
      try {
        const result = await listNotifications({ page: this.page, pageSize: this.pageSize })
        if (seq !== this.reqSeq) {
          return
        }
        const records = result.records || []
        this.list = reset ? records : this.list.concat(records)
        const total = result.total || 0
        this.finished = records.length < this.pageSize || this.list.length >= total
      } catch (e) {
        console.error('[微光] 通知列表加载失败', e)
      } finally {
        if (seq === this.reqSeq) {
          this.loading = false
          this.loadingMore = false
        }
      }
    },
    /** B：左滑手势（仅横向拖动超过 8px 才进入滑动，避免干扰纵向滚动） */
    onTouchStart(event, item) {
      const touch = event.touches && event.touches[0]
      if (!touch) {
        return
      }
      this.touchX = touch.clientX
      this.touchY = touch.clientY
      this.slideId = item.id
      this.slideX = 0
      this.dragging = false
    },
    onTouchMove(event, item) {
      if (this.slideId !== item.id) {
        return
      }
      const touch = event.touches && event.touches[0]
      if (!touch) {
        return
      }
      const dx = touch.clientX - this.touchX
      const dy = touch.clientY - this.touchY
      if (!this.dragging && Math.abs(dx) > 8 && Math.abs(dx) > Math.abs(dy)) {
        this.dragging = true
      }
      if (this.dragging) {
        const maxRight = item.isRead === 0 ? 80 : 0
        this.slideX = Math.max(-80, Math.min(maxRight, dx))
      }
    },
    onTouchEnd() {
      if (this.dragging && this.slideX < -40) {
        this.slideX = -80
      } else if (this.dragging && this.slideX > 40) {
        this.slideX = 80
      } else {
        this.slideX = 0
        this.slideId = null
      }
      this.dragging = false
    },
    slideStyle(item) {
      return this.slideId === item.id && this.slideX !== 0
        ? `transform: translateX(${this.slideX}px); transition: transform .15s`
        : ''
    },
    /** B：右滑标记单条已读（不跳转） */
    async markRead(item) {
      try {
        await readNotification(item.id)
        item.isRead = 1
        this.slideId = null
        this.slideX = 0
        this.notifyStore.refresh()
      } catch (e) {
        console.error('[微光] 通知操作失败', e)
      }
    },
    /** B：删除单条通知 */
    remove(item) {
      uni.showModal({
        title: '删除通知',
        content: '删除后就找不回来了，确定吗？',
        success: async (res) => {
          if (!res.confirm) {
            return
          }
          try {
            await deleteNotification(item.id)
            this.list = this.list.filter((one) => one.id !== item.id)
            this.slideId = null
            this.slideX = 0
            this.notifyStore.refresh()
          } catch (e) {
            console.error('[微光] 通知操作失败', e)
          }
        }
      })
    },
    /** 点击：先已读（N3），再按 jump_type 跳转（详设 §6.2 P16） */
    async openItem(item) {
      // 已滑动展开（左滑删除或右滑已读）时，先收起，不触发跳转
      if (this.slideId === item.id && this.slideX !== 0) {
        this.slideId = null
        this.slideX = 0
        return
      }
      if (item.isRead === 0) {
        try {
          await readNotification(item.id)
          item.isRead = 1
          this.notifyStore.refresh()
        } catch (e) {
          console.error('[微光] 标记已读失败', e)
        }
      }
      this.jump(item)
    },
    /** 跳转目标页：订单详情 / 聊天 / 申诉进度 / 邻里动态（NONE 无目标则不跳） */
    jump(item) {
      let url = ''
      if (item.jumpType === 'ORDER' && item.jumpId) {
        url = `/pages/order/room?id=${item.jumpId}`
      } else if (item.jumpType === 'SESSION' && item.jumpId) {
        url = `/pages/chat/room?orderId=${item.jumpId}`
      } else if (item.jumpType === 'POST' && item.jumpId) {
        url = `/pages/community/detail?id=${item.jumpId}`
      } else if (item.jumpType === 'APPEAL') {
        url = '/pages/user/appeal'
      }
      if (!url) {
        return
      }
      uni.navigateTo({
        url,
        fail: () => {
          uni.showToast({ title: '页面没打开，请再试一次', icon: 'none' })
        }
      })
    },
    async readAll() {
      try {
        await readAllNotifications()
        this.list = this.list.map((item) => ({ ...item, isRead: 1 }))
        this.notifyStore.refresh()
      } catch (e) {
        console.error('[微光] 全部已读失败', e)
      }
    }
  }
}
</script>

<style lang="scss">
.wg-notify-title-wrap {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
}

.wg-notify-ico {
  width: 30px;
  height: 30px;
  border-radius: var(--wg-radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 8px;
  flex-shrink: 0;
}

.wg-notify-title {
  font-size: var(--wg-font-base);
  font-weight: 700;
}

.wg-notify-content {
  margin-top: 6px;
  color: var(--wg-text);
  font-size: var(--wg-font-base);
}

.wg-notify-elder-actions {
  display: flex;
  gap: 10px;
  margin-top: 10px;
}

.wg-notify-elder-actions .wg-btn {
  flex: 1;
  margin: 0;
}

.wg-notify-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--wg-space);
}

/* B：未读数与分组标题 */
.wg-notify-unread {
  font-size: var(--wg-font-sm);
  color: var(--wg-text-weak);
}

.wg-notify-group {
  margin: 4px 0 8px;
  font-size: var(--wg-font-sm);
  font-weight: 800;
  color: var(--wg-text-weak);
}

.wg-notify-readall {
  min-width: 96px;
  padding: 0 20px;
}

/* B：右滑标记已读 */
.wg-notify-read {
  position: absolute;
  top: 0;
  left: 0;
  width: 80px;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #d9e2d6;
  color: #4f6b4a;
  font-size: var(--wg-font-base);
  font-weight: 700;
}

/* B：左滑删除 */
.wg-notify-swipe {
  position: relative;
  margin-bottom: var(--wg-gap);
  overflow: hidden;
  border-radius: var(--wg-radius);
}

.wg-notify-swipe .wg-card {
  margin-bottom: 0;
}

.wg-notify-slide {
  position: relative;
  z-index: 1;
  background-color: var(--wg-surface);
}

.wg-notify-del {
  position: absolute;
  top: 0;
  right: 0;
  width: 80px;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #ead9d3;
  color: #8a5f52;
  font-size: var(--wg-font-base);
  font-weight: 700;
}

/* B：聊天消息聚合卡 */
.wg-chat-digest-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.wg-chat-digest-list {
  margin-top: 8px;
}

.wg-chat-digest-row {
  display: flex;
  align-items: center;
  padding: 12px 0;
  border-top: 1px solid var(--wg-line);
}

.wg-chat-digest-name {
  flex: 1;
  font-size: var(--wg-font-base);
}

/* 未读条数：红色家族色；超过 9 用实心红强调 */
.wg-chat-digest-count {
  min-width: 24px;
  height: 20px;
  padding: 0 6px;
  margin-right: 6px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--wg-font-sm);
  font-weight: 800;
  background-color: rgba(192, 57, 43, 0.14);
  color: #a33b2f;
}

.wg-chat-digest-count-max {
  background-color: #c0392b;
  color: #ffffff;
}

.wg-feed-more {
  margin: 10px 0;
  text-align: center;
}
</style>