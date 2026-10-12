<template>
  <view class="wg-page" :class="userStore.rootClass">
    <view v-if="loading" class="wg-card">
      <text>正在为您准备，请稍等</text>
    </view>

    <template v-else>
      <view class="wg-home-head">
        <text class="wg-title">微光</text>
        <text class="wg-link" @click="goMine">我的</text>
      </view>

      <!-- 顶部通知横幅（详设 §6.2 P03：N2 最近 1 条未读+聚合） -->
      <view v-if="notifyStore.hasUnread" class="wg-banner" @click="openBanner">
        <view class="wg-dot" />
        <text class="wg-banner-text">{{ notifyStore.bannerText }}</text>
      </view>

      <!-- 紧急电话卡片（详设 §6.1 紧急提示三处常驻之③） -->
      <EmergencyCard />

      <!-- Tab：全部求助 / 为你推荐（年长模式精简为单列表，详设 §6.2 P03 适老差异） -->
      <view v-if="!userStore.isElder" class="wg-tabs">
        <view class="wg-tab" :class="{ 'wg-tab-active': tab === 'all' }" @click="switchTab('all')">全部求助</view>
        <view class="wg-tab" :class="{ 'wg-tab-active': tab === 'recommend' }" @click="switchTab('recommend')">为你推荐</view>
      </view>

      <!-- 筛选：类型 3 选 + 只看急（年长模式隐藏，详设 §6.2 P03 适老差异） -->
      <view v-if="!userStore.isElder" class="wg-filter">
        <view
          v-for="item in typeFilters"
          :key="item.value"
          class="wg-chip"
          :class="{ 'wg-chip-active': orderType === item.value }"
          @click="switchType(item.value)"
        >
          {{ item.label }}
        </view>
        <view class="wg-chip" :class="{ 'wg-chip-active': urgentOnly }" @click="toggleUrgent">只看急</view>
      </view>

      <!-- 求助卡片列表 -->
      <view v-if="orders.length === 0" class="wg-card">
        <text>{{ tab === 'recommend' ? '还没有和您方向匹配的求助，去"全部求助"看看吧' : '这里还静悄悄的，去看看别的邻居吧' }}</text>
      </view>
      <view v-for="order in orders" :key="order.id" class="wg-card" @click="openDetail(order.id)">
        <view class="wg-card-head">
          <text class="wg-title">{{ order.title }}</text>
          <text v-if="order.urgentFlag === 1" class="wg-urgent">急</text>
        </view>
        <view class="wg-label">
          {{ typeLabel(order.orderType) }} · {{ timeLabel(order.timeExpect) }} · {{ order.createdAt }}
        </view>
        <view class="wg-tags">
          <text v-for="tag in order.tags" :key="tag" class="wg-tag">{{ tag }}</text>
          <text v-if="order.mine" class="wg-tag wg-tag-mine">这是我发的</text>
        </view>
        <view class="wg-card-foot">
          <image
            v-if="order.requester.avatarUrl"
            class="wg-foot-avatar"
            :src="fileUrl(order.requester.avatarUrl)"
            mode="aspectFill"
          />
          <text class="wg-label">{{ order.requester.nickname || '邻居' }} · 已有 {{ order.viewCount }} 位邻居看过</text>
        </view>
      </view>

      <view class="wg-btn" @click="goPublish">我要发布求助</view>

      <!-- 荣耀榜：助人之星 TOP10（H1 / 详设 §6.2 P03） -->
      <view v-if="honorList.length > 0" class="wg-card">
        <view class="wg-card-head">
          <text>助人之星</text>
          <text class="wg-label">近 30 天</text>
        </view>
        <view v-for="(item, index) in honorList" :key="index" class="wg-honor-row">
          <text class="wg-honor-rank">{{ index + 1 }}</text>
          <image v-if="item.avatarUrl" class="wg-honor-avatar" :src="fileUrl(item.avatarUrl)" mode="aspectFill" />
          <text class="wg-honor-name">{{ item.nickname || '邻居' }}</text>
          <text class="wg-strong">{{ item.helpCount }} 次</text>
        </view>
      </view>

      <!-- 善意回流计数（H1 / 详设 §6.2 P03） -->
      <view class="wg-card">
        <text>社区已有 {{ lightPassedCount }} 束光，从被照亮走向照亮他人</text>
      </view>

      <!-- 首次进入紧急提示弹窗（详设 §6.1 紧急提示三处常驻之①） -->
      <view v-if="entryTipVisible" class="wg-dialog-mask">
        <view class="wg-dialog-box">
          <text class="wg-dialog-text">{{ entryTip }}</text>
          <view class="wg-btn" @click="dismissEntryTip">我知道了</view>
        </view>
      </view>
    </template>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { useNotifyStore } from '@/store/notify'
import { listOrders } from '@/api/order'
import { getHomeSummary } from '@/api/home'
import { ORDER_TYPE_LABELS, TIME_EXPECT_LABELS, EMERGENCY_TIP, STORAGE_KEYS, BASE_URL } from '@/utils/constants'
import EmergencyCard from '@/components/EmergencyCard.vue'

export default {
  components: { EmergencyCard },
  data() {
    return {
      loading: true,
      bootstrapped: false,
      tab: 'all',
      orderType: '',
      urgentOnly: false,
      orders: [],
      honorList: [],
      lightPassedCount: 0,
      entryTipVisible: false,
      entryTip: EMERGENCY_TIP,
      typeFilters: [
        { label: '全部类型', value: '' },
        { label: '上门', value: 'HOME_VISIT' },
        { label: '陪同外出', value: 'ACCOMPANY' },
        { label: '远程', value: 'REMOTE' }
      ]
    }
  },
  computed: {
    userStore() {
      return useUserStore()
    },
    notifyStore() {
      return useNotifyStore()
    }
  },
  onLoad() {
    this.bootstrap()
  },
  onShow() {
    // 回到首页时刷新横幅（如刚在 P16 全部已读 → 横幅消失）
    if (this.bootstrapped) {
      this.notifyStore.refresh()
    }
  },
  methods: {
    async bootstrap() {
      this.loading = true
      try {
        await this.userStore.bootstrap()
        if (this.userStore.needLogin) {
          // D31 账密登录：无凭证 → 登录页
          uni.reLaunch({ url: '/pages/login/index' })
          return
        }
        if (!this.userStore.hasPrivacyAgreed) {
          uni.redirectTo({ url: '/pages/guide/privacy' })
          return
        }
        await this.loadOrders()
        this.loadHomeSummary()
        this.checkEntryTip()
        this.bootstrapped = true
        this.notifyStore.refresh()
      } catch (e) {
        console.error('[微光] 启动失败', e)
        uni.showToast({ title: '网络打了个盹，请再试一次，我在这儿等您。', icon: 'none' })
      } finally {
        this.loading = false
      }
    },
    async loadOrders() {
      const result = await listOrders({
        tab: this.userStore.isElder ? 'all' : this.tab,
        orderType: this.orderType || undefined,
        urgentOnly: this.urgentOnly ? true : undefined
      })
      this.orders = result.records || []
    },
    /** H1 首页聚合：荣耀榜 TOP10 + 善意回流计数 */
    async loadHomeSummary() {
      try {
        const summary = await getHomeSummary()
        this.honorList = summary.honorTop10 || []
        this.lightPassedCount = summary.lightPassedCount || 0
      } catch (e) {
        console.error('[微光] 首页聚合加载失败', e)
      }
    },
    /** 首次进入紧急提示（详设 §6.1 三处常驻之①）：本地标记，只弹一次 */
    checkEntryTip() {
      if (uni.getStorageSync(STORAGE_KEYS.ENTRY_TIP_SHOWN)) {
        return
      }
      this.entryTipVisible = true
    },
    dismissEntryTip() {
      uni.setStorageSync(STORAGE_KEYS.ENTRY_TIP_SHOWN, 1)
      this.entryTipVisible = false
    },
    fileUrl(url) {
      if (!url) {
        return ''
      }
      return url.indexOf('http') === 0 ? url : BASE_URL + url
    },
    switchTab(tab) {
      if (this.tab === tab) {
        return
      }
      this.tab = tab
      this.loadOrders()
    },
    switchType(value) {
      this.orderType = value
      this.loadOrders()
    },
    toggleUrgent() {
      this.urgentOnly = !this.urgentOnly
      this.loadOrders()
    },
    typeLabel(type) {
      return ORDER_TYPE_LABELS[type] || type
    },
    timeLabel(expect) {
      return expect ? TIME_EXPECT_LABELS[expect] || expect : '不限'
    },
    openDetail(id) {
      uni.navigateTo({ url: `/pages/order/detail?id=${id}` })
    },
    goPublish() {
      uni.navigateTo({ url: '/pages/order/publish' })
    },
    goMine() {
      uni.navigateTo({ url: '/pages/user/index' })
    },
    /** 横幅点击：按 jump_type 跳对应页；NONE 或无目标 → 通知中心（见偏差 D21） */
    openBanner() {
      const latest = this.notifyStore.latest
      if (!latest) {
        return
      }
      let url = '/pages/user/notify'
      if (latest.jumpType === 'ORDER' && latest.jumpId) {
        url = `/pages/order/room?id=${latest.jumpId}`
      } else if (latest.jumpType === 'SESSION' && latest.jumpId) {
        url = `/pages/chat/room?orderId=${latest.jumpId}`
      } else if (latest.jumpType === 'APPEAL') {
        url = '/pages/user/appeal'
      }
      uni.navigateTo({
        url,
        fail: () => {
          uni.showToast({ title: '页面没打开，请再试一次', icon: 'none' })
        }
      })
    }
  }
}
</script>

<style lang="scss">
.wg-tabs {
  display: flex;
  margin-bottom: var(--wg-space);
}

.wg-tab {
  flex: 1;
  text-align: center;
  padding: 12px 0;
  border-radius: var(--wg-radius);
  background-color: #ffffff;
  margin-right: 8px;
  font-size: var(--wg-font-base);
}

.wg-tab-active {
  background-color: var(--wg-primary-light);
  color: #b06a00;
  font-weight: 600;
}

.wg-filter {
  display: flex;
  flex-wrap: wrap;
  margin-bottom: var(--wg-space);
}

.wg-chip {
  padding: 6px 12px;
  margin: 0 8px 8px 0;
  border-radius: var(--wg-radius);
  background-color: #ffffff;
  border: 1px solid #e6ded2;
  font-size: var(--wg-font-sm);
}

.wg-chip-active {
  background-color: var(--wg-primary-light);
  border-color: var(--wg-primary);
}

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

.wg-tags {
  display: flex;
  flex-wrap: wrap;
  margin: 6px 0;
}

.wg-tag {
  padding: 2px 10px;
  margin: 0 8px 8px 0;
  border-radius: 10px;
  background-color: var(--wg-primary-light);
  font-size: var(--wg-font-sm);
}

.wg-tag-mine {
  background-color: #e8e8e8;
}

.wg-home-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.wg-link {
  color: #b06a00;
  font-size: var(--wg-font-base);
}

.wg-banner {
  display: flex;
  align-items: center;
  background-color: var(--wg-primary-light);
  border-radius: var(--wg-radius);
  padding: 12px;
  margin-bottom: var(--wg-space);
}

.wg-banner-text {
  margin-left: 8px;
  font-size: var(--wg-font-base);
  color: #b06a00;
}

/* 荣耀榜行（H1） */
.wg-honor-row {
  display: flex;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid #f0e9dd;
}

.wg-honor-rank {
  width: 26px;
  color: #b06a00;
  font-weight: 600;
}

.wg-honor-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  margin-right: 8px;
  background-color: #eee5d8;
}

.wg-honor-name {
  flex: 1;
}

/* 卡片底部发布者行（昵称+头像，详设 §6.2 P03） */
.wg-card-foot {
  display: flex;
  align-items: center;
}

.wg-foot-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  margin-right: 6px;
  background-color: #eee5d8;
}

/* 适老差异：卡片加大（详设 §6.2 P03） */
.mode-elder .wg-card {
  padding: calc(var(--wg-space) + 6px);
}
</style>
