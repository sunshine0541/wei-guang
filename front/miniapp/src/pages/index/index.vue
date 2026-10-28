<template>
  <view class="wg-page wg-page-tab" :class="userStore.rootClass">
    <!-- 自定义头部（pages.json navigationStyle: custom）：问候 + 光语状态 -->
    <view class="wg-home-head" :style="{ paddingTop: statusBarHeight + 'px' }">
      <text class="wg-home-hi">{{ greeting }}，{{ userStore.nickname || '邻居' }}</text>
      <text v-if="lightName" class="wg-home-sub">{{ lightName }}</text>
    </view>

    <!-- 加载：骨架屏 + 原句（§6.1 加载文案不改） -->
    <view v-if="loading" class="wg-skel">
      <view class="wg-skel-line" style="width: 56%"></view>
      <view class="wg-skel-block"></view>
      <view class="wg-skel-block"></view>
      <text class="wg-label" style="margin-top: 6px">正在为您准备，请稍等</text>
      <!-- B：加载超过 8 秒仍未完成时给出重试入口 -->
      <view v-if="retryVisible" class="wg-btn wg-btn-plain" style="margin-top: 12px" @click="bootstrap">重新加载</view>
    </view>

    <view v-else-if="loadFailed" class="wg-card">
      <view class="wg-label">刚才没加载出来，稍等一下再试试吧</view>
      <view class="wg-btn wg-btn-plain" style="margin-top: 12px" @click="bootstrap">重新加载</view>
    </view>

    <template v-else>
      <!-- 双角色 hero（A）：求助 / 助人 两个等重入口 -->
      <view class="wg-hero">
        <view class="wg-tile" style="background-color: #d9e2d6; color: #4f6b4a" @click="goPublish">
          <u-icon name="heart-fill" :size="heroIconSize" />
          <view>
            <view class="wg-tile-title">我需要帮助</view>
            <view class="wg-tile-sub">发一件小事，邻居来搭把手</view>
          </view>
        </view>
        <view class="wg-tile" style="background-color: #dbe3e7; color: #4e6b78" @click="goHelpList">
          <u-icon name="man-add-fill" :size="heroIconSize" />
          <view>
            <view class="wg-tile-title">我可以帮忙</view>
            <view class="wg-tile-sub">看看附近谁需要帮助</view>
          </view>
        </view>
      </view>

      <!-- 我的互助概览（双角色口径） -->
      <view class="wg-label" style="margin-bottom: 10px">
        我发布的进行中 {{ doingCount }} 件 · 我接取的求助 {{ helpingCount }} 单
      </view>

      <!-- 顶部通知横幅（§6.2 P03：N2 最近 1 条未读 + 聚合） -->
      <view v-if="notifyStore.hasUnread" class="wg-notice" @click="openBanner">
        <view class="wg-dot" />
        <text class="wg-notice-text">{{ notifyStore.bannerText }}</text>
        <!-- B：右侧箭头明示横幅可点 -->
        <u-icon name="arrow-right" :size="16" />
      </view>

      <!-- 紧急电话卡片（§6.1 紧急提示三处常驻之③） -->
      <EmergencyCard />

      <!-- 求助列表（主体）：Tab + 筛选（年长模式隐藏 Tab 与筛选，直接展示全部求助） -->
      <view v-if="!userStore.isElder" class="wg-seg">
        <view class="wg-seg-item" :class="{ 'wg-seg-item-active': tab === 'all' }" @click="switchTab('all')">全部求助</view>
        <view class="wg-seg-item" :class="{ 'wg-seg-item-active': tab === 'recommend' }" @click="switchTab('recommend')">为你推荐</view>
      </view>
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
        <view class="wg-chip" :class="{ 'wg-chip-active': distanceSort }" @click="toggleDistanceSort">离我最近</view>
      </view>

      <view id="wgOrderList" class="wg-section-title">附近邻居需要帮忙</view>
      <view v-if="orders.length === 0" class="wg-empty">{{ emptyText }}</view>
      <view v-for="order in orders" :key="order.id" class="wg-card" @click="openDetail(order.id)">
        <view class="wg-card-head">
          <text class="wg-subtitle">{{ order.title }}</text>
          <text v-if="order.urgentFlag === 1" class="wg-urgent">急</text>
        </view>
        <view class="wg-label">
          {{ typeLabel(order.orderType) }} · {{ timeLabel(order.timeExpect) }} · {{ order.createdAt }}
        </view>
        <!-- B：同区域距离粗算（暂不接地图，仅比对默认地址的小区/区域） -->
        <view v-if="order.addressArea" class="wg-label">
          地点：{{ order.addressArea }}
          <text v-if="nearbyText(order)" class="wg-nearby"> · {{ nearbyText(order) }}</text>
        </view>
        <view class="wg-tags">
          <text v-for="tag in order.tags" :key="tag" class="wg-tag">{{ tag }}</text>
          <text v-if="order.extraTag" class="wg-tag wg-tag-extra">{{ order.extraTag }}</text>
          <text v-if="order.mine" class="wg-tag wg-tag-mine">这是我发的</text>
        </view>
        <view class="wg-card-foot">
          <WgImage
            v-if="order.requester.avatarUrl"
            class="wg-foot-avatar"
            :src="order.requester.avatarUrl"
            :width="28"
            :height="28"
            mode="aspectFill"
          />
          <text class="wg-label">{{ order.requester.nickname || '邻居' }} · 已有 {{ order.viewCount }} 位邻居看过</text>
        </view>
      </view>

      <view class="wg-btn" @click="goPublish">我要发布求助</view>

      <!-- 首次进入紧急提示弹窗（§6.1 紧急提示三处常驻之①） -->
      <view v-if="entryTipVisible" class="wg-dialog-mask">
        <view class="wg-dialog-box">
          <text class="wg-dialog-text">{{ entryTip }}</text>
          <view class="wg-btn" @click="dismissEntryTip">我知道了</view>
        </view>
      </view>
    </template>

    <!-- 底部导航（自绘 4 项） -->
    <WgTabBar :active="0" />
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { useNotifyStore } from '@/store/notify'
import { listOrders, listMyOrders } from '@/api/order'
import { listAddresses } from '@/api/address'
import { getLight } from '@/api/user'
import { ORDER_TYPE_LABELS, TIME_EXPECT_LABELS, EMERGENCY_TIP, STORAGE_KEYS, MESSAGES } from '@/utils/constants'
import EmergencyCard from '@/components/EmergencyCard.vue'
import WgTabBar from '@/components/WgTabBar.vue'
import WgImage from '@/components/WgImage.vue'

export default {
  components: { EmergencyCard, WgTabBar, WgImage },
  data() {
    return {
      statusBarHeight: 0,
      loading: true,
      bootstrapped: false,
      tab: 'all',
      orderType: '',
      urgentOnly: false,
      orders: [],
      doingCount: 0,
      helpingCount: 0,
      lightName: '',
      entryTipVisible: false,
      entryTip: EMERGENCY_TIP,
      /** B：骨架屏超时后的重试入口 */
      retryVisible: false,
      loadFailed: false,
      retryTimer: null,
      typeFilters: [
        { label: '全部类型', value: '' },
        { label: '上门', value: 'HOME_VISIT' },
        { label: '外出', value: 'ACCOMPANY' },
        { label: '远程', value: 'REMOTE' }
      ],
      /** B：本人默认地址的小区/区域，用于"同区域=很近"粗算 */
      myArea: '',
      /** 4D：默认地址是否带地图坐标（有坐标才能按距离排序） */
      myHasGeo: false,
      /** 4D：离我最近排序 */
      distanceSort: false
    }
  },
  computed: {
    userStore() {
      return useUserStore()
    },
    notifyStore() {
      return useNotifyStore()
    },
    greeting() {
      const hour = new Date().getHours()
      if (hour < 5) return '夜深了'
      if (hour < 11) return '早上好'
      if (hour < 13) return '中午好'
      if (hour < 18) return '下午好'
      return '晚上好'
    },
    heroIconSize() {
      return this.userStore.isElder ? 28 : 24
    },
    emptyText() {
      // 年长模式固定展示全部求助，空态直接使用通用文案
      return this.userStore.isElder
        ? MESSAGES.EMPTY
        : (this.tab === 'recommend' ? '还没有和您方向匹配的求助，去"全部求助"看看吧' : MESSAGES.EMPTY)
    }
  },
  onLoad() {
    this.statusBarHeight = (this.statusBarHeightOf() || 0) + 6
    this.bootstrap()
  },
  onShow() {
    // 回到首页时刷新列表与横幅（如从详情返回后「已有 N 位邻居看过」需即时更新）
    if (this.bootstrapped) {
      // 4D-补：先刷新默认地址，再按最新坐标状态决定排序
      this.refreshAreaThenOrders()
      this.loadMyStat()
      this.loadLight()
      this.notifyStore.refresh()
    }
    // A2：隐藏左上角「返回首页⌂」（reLaunch 后页面栈只剩本页，微信会自动加该按钮）
    if (typeof wx !== 'undefined' && typeof wx.hideHomeButton === 'function') {
      wx.hideHomeButton()
    }
  },
  onUnload() {
    if (this.retryTimer) {
      clearTimeout(this.retryTimer)
    }
  },
  methods: {
    /** 兼容新版微信：优先 getWindowInfo，避免 getSystemInfoSync 弃用警告 */
    statusBarHeightOf() {
      if (typeof uni.getWindowInfo === 'function') {
        return uni.getWindowInfo().statusBarHeight || 0
      }
      return typeof uni.getSystemInfoSync === 'function'
        ? (uni.getSystemInfoSync().statusBarHeight || 0)
        : 0
    },
    async bootstrap() {
      this.loading = true
      this.loadFailed = false
      this.retryVisible = false
      if (this.retryTimer) {
        clearTimeout(this.retryTimer)
      }
      this.retryTimer = setTimeout(() => {
        this.retryVisible = true
      }, 8000)
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
        this.loadMyStat()
        this.loadLight()
        this.loadMyArea()
        this.checkEntryTip()
        this.bootstrapped = true
        this.notifyStore.refresh()
      } catch (e) {
        console.error('[微光] 启动失败', e)
        this.loadFailed = true
        uni.showToast({ title: MESSAGES.LOGIN_FAILED, icon: 'none' })
      } finally {
        if (this.retryTimer) {
          clearTimeout(this.retryTimer)
          this.retryTimer = null
        }
        this.loading = false
      }
    },
    async loadOrders() {
      const result = await listOrders({
        // 年长模式隐藏 Tab，固定展示全部求助（不能用 recommend，否则推荐为空时会误显示"没有求助"）
        tab: this.userStore.isElder ? 'all' : this.tab,
        orderType: this.orderType || undefined,
        urgentOnly: this.urgentOnly ? true : undefined,
        sort: this.distanceSort ? 'distance' : undefined
      })
      this.orders = result.records || []
    },
    /** B：取本人默认地址的小区/区域（失败静默，仅影响"很近"提示） */
    async loadMyArea() {
      try {
        const addresses = await listAddresses()
        const def = addresses.find((item) => item.isDefault === 1) || addresses[0]
        this.myArea = def ? def.areaText || '' : ''
        this.myHasGeo = !!(def && def.latitude !== null && def.latitude !== undefined
          && def.longitude !== null && def.longitude !== undefined)
        // 4D-补：默认地址丢了坐标就自动取消距离排序，避免"选中了却没排序"
        if (this.distanceSort && !this.myHasGeo) {
          this.distanceSort = false
          uni.showToast({ title: '默认地址还没有地图位置，已取消按距离排序', icon: 'none' })
        }
      } catch (e) {
        console.error('[微光] 默认地址加载失败', e)
      }
    },
    /** 4D-补：回到首页先刷新默认地址坐标状态，再拉列表 */
    async refreshAreaThenOrders() {
      await this.loadMyArea()
      await this.loadOrders()
    },
    /** B/4D：优先用真实直线距离，缺坐标时回退到同区域粗算 */
    nearbyText(order) {
      if (!order || order.mine) {
        return ''
      }
      if (order.distanceKm !== null && order.distanceKm !== undefined) {
        return this.formatDistance(order.distanceKm)
      }
      if (!this.myArea || !order.addressArea) {
        return ''
      }
      return this.myArea === order.addressArea ? '与您同区域，很近' : ''
    },
    formatDistance(km) {
      if (km < 1) {
        return '距您约 ' + Math.max(10, Math.round(km * 1000 / 10) * 10) + ' 米'
      }
      return '距您约 ' + km.toFixed(1) + ' 公里'
    },
    toggleDistanceSort() {
      if (!this.distanceSort && !this.myHasGeo) {
        uni.showToast({ title: '先在常用地址里用地图选一下默认地址', icon: 'none' })
        return
      }
      this.distanceSort = !this.distanceSort
      this.loadOrders().catch((e) => console.error('[微光] 按距离排序失败', e))
    },
    /** 双角色概览：我发布的进行中 / 我接的单（复用 O5，失败静默） */
    async loadMyStat() {
      try {
        const [doing, helping] = await Promise.all([
          listMyOrders({ tab: 'DOING' }),
          listMyOrders({ tab: 'HELPING' })
        ])
        this.doingCount = (doing.records || []).length
        this.helpingCount = (helping.records || []).length
      } catch (e) {
        console.error('[微光] 互助概览加载失败', e)
      }
    },
    /** U7 光语状态：仅取展示名（头部一行），失败静默 */
    async loadLight() {
      try {
        const light = await getLight()
        this.lightName = light ? light.lightStateName : ''
      } catch (e) {
        console.error('[微光] 光语状态加载失败', e)
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
    goHelpList() {
      const query = uni.createSelectorQuery().in(this)
      query.select('#wgOrderList').boundingClientRect()
      query.selectViewport().scrollOffset()
      query.exec((res) => {
        if (!res || !res[0]) {
          return
        }
        uni.pageScrollTo({ scrollTop: res[0].top + res[1].scrollTop - 12, duration: 220 })
      })
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
/* 自定义头部（无原生导航栏，需自行避开状态栏） */
.wg-home-head {
  margin-bottom: var(--wg-gap);
}

.wg-home-hi {
  display: block;
  font-size: var(--wg-font-title);
  font-weight: 800;
  line-height: 1.3;
}

.wg-home-sub {
  display: block;
  font-size: var(--wg-font-sm);
  color: var(--wg-primary-deep);
  margin-top: 4px;
  font-weight: 700;
}

.wg-notice-text {
  margin-left: 8px;
  flex: 1;
  font-size: var(--wg-font-base);
  line-height: 1.5;
}

/* B：同区域距离提示 */
.wg-nearby {
  color: var(--wg-primary-deep);
  font-weight: 700;
}

/* 筛选行（沿用全局 chip 类，仅做排布） */
.wg-filter {
  display: flex;
  flex-wrap: wrap;
  margin-bottom: var(--wg-space);
}

/* 卡片底部发布者行 */
.wg-card-foot {
  display: flex;
  align-items: center;
}

.wg-foot-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  margin-right: 6px;
  background-color: var(--wg-surface3);
}

/* 年长模式：卡片加大（§6.2 P03 适老差异） */
.mode-elder .wg-card {
  padding: calc(var(--wg-space) + 4px);
}
</style>
