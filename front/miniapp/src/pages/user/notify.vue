<template>
  <view class="wg-page" :class="userStore.rootClass">
    <view v-if="loading" class="wg-card">
      <text>{{ loadingText }}</text>
    </view>

    <template v-else>
      <!-- 全部已读：置于列表顶部（需求方反馈：原在列表底部，长列表需滚动才够到） -->
      <view v-if="hasUnread" class="wg-notify-bar">
        <view class="wg-notify-readall" @click="readAll">{{ readAllText }}</view>
      </view>

      <view v-if="list.length === 0" class="wg-card">
        <text>{{ emptyText }}</text>
      </view>

      <view v-for="item in list" :key="item.id" class="wg-card" @click="openItem(item)">
        <view class="wg-card-head">
          <text class="wg-notify-title">{{ iconOf(item.type) }} {{ item.title }}</text>
          <view v-if="item.isRead === 0" class="wg-dot" />
        </view>
        <!-- 适老差异：年长模式仅保留标题+时间两行（详设 §6.2 P16） -->
        <view v-if="!userStore.isElder" class="wg-notify-content">{{ item.content }}</view>
        <view class="wg-label" style="margin-top: 6px">{{ item.createdAt }}</view>
      </view>
    </template>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { useNotifyStore } from '@/store/notify'
import { listNotifications, readAllNotifications, readNotification } from '@/api/notify'
import { MESSAGES } from '@/utils/constants'

/* P16 写死文案（详设 §6.2） */
const EMPTY_TEXT = '暂无新消息'
const READ_ALL_TEXT = '全部已读'

/* 通知类型图标（详设 §4.4 十三类，展示用） */
const TYPE_ICONS = {
  NEW_MESSAGE: '💬',
  ACCEPTED: '🤝',
  SERVICE_STARTED: '🚶',
  CONFIRM_REQUEST: '📝',
  SETTLED: '🌟',
  RELEASED: '🔄',
  EXPIRED: '⏳',
  APPEAL_RESULT: '📮',
  COOLDOWN: '☕',
  POINTS_CREDITED: '🎁',
  CREDIT_CHANGED: '📊',
  ADMIN_ACTION: '📢',
  FEEDBACK_RESULT: '💌'
}

export default {
  data() {
    return {
      loading: true,
      list: [],
      loadingText: MESSAGES.LOADING,
      emptyText: EMPTY_TEXT,
      readAllText: READ_ALL_TEXT
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
    }
  },
  onShow() {
    this.load()
  },
  methods: {
    iconOf(type) {
      return TYPE_ICONS[type] || '🔔'
    },
    async load() {
      try {
        const result = await listNotifications({ page: 1, pageSize: 50 })
        this.list = result.records || []
      } catch (e) {
        console.error('[微光] 通知列表加载失败', e)
      } finally {
        this.loading = false
      }
    },
    /** 点击：先已读（N3），再按 jump_type 跳转（详设 §6.2 P16） */
    async openItem(item) {
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
    /** 跳转目标页：订单详情 / 聊天 / 申诉进度（NONE 无目标则不跳） */
    jump(item) {
      let url = ''
      if (item.jumpType === 'ORDER' && item.jumpId) {
        url = `/pages/order/room?id=${item.jumpId}`
      } else if (item.jumpType === 'SESSION' && item.jumpId) {
        url = `/pages/chat/room?orderId=${item.jumpId}`
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
.wg-notify-title {
  font-size: var(--wg-font-base);
  font-weight: 600;
}

.wg-notify-content {
  margin-top: 6px;
  color: var(--wg-text);
  font-size: var(--wg-font-base);
}

.wg-notify-bar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: var(--wg-space);
}

.wg-notify-readall {
  min-height: var(--wg-btn-min-height);
  line-height: var(--wg-btn-min-height);
  padding: 0 20px;
  border-radius: var(--wg-radius);
  background-color: #ffffff;
  border: 1px solid #e6ded2;
  color: #b06a00;
  font-size: var(--wg-font-base);
}
</style>