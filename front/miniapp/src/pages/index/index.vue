<template>
  <view class="wg-page" :class="userStore.rootClass">
    <view v-if="loading" class="wg-card">
      <text>正在为您准备，请稍等</text>
    </view>

    <template v-else>
      <!-- Tab：全部求助 / 为你推荐 -->
      <view class="wg-tabs">
        <view class="wg-tab" :class="{ 'wg-tab-active': tab === 'all' }" @click="switchTab('all')">全部求助</view>
        <view class="wg-tab" :class="{ 'wg-tab-active': tab === 'recommend' }" @click="switchTab('recommend')">为你推荐</view>
      </view>

      <!-- 筛选：类型 3 选 + 只看急 -->
      <view class="wg-filter">
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
        <view class="wg-label">
          {{ order.requester.nickname || '邻居' }} · 已有 {{ order.viewCount }} 位邻居看过
        </view>
      </view>

      <view class="wg-btn" @click="goPublish">我要发布求助</view>
    </template>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { listOrders } from '@/api/order'
import { ORDER_TYPE_LABELS, TIME_EXPECT_LABELS } from '@/utils/constants'

export default {
  data() {
    return {
      loading: true,
      tab: 'all',
      orderType: '',
      urgentOnly: false,
      orders: [],
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
    }
  },
  onLoad() {
    this.bootstrap()
  },
  methods: {
    async bootstrap() {
      this.loading = true
      try {
        await this.userStore.bootstrap()
        if (!this.userStore.hasPrivacyAgreed) {
          uni.redirectTo({ url: '/pages/guide/privacy' })
          return
        }
        if (this.userStore.isNewUser) {
          uni.redirectTo({ url: '/pages/guide/index' })
          return
        }
        await this.loadOrders()
      } catch (e) {
        console.error('[微光] 启动失败', e)
        uni.showToast({ title: '网络打了个盹，请再试一次，我在这儿等您。', icon: 'none' })
      } finally {
        this.loading = false
      }
    },
    async loadOrders() {
      const result = await listOrders({
        tab: this.tab,
        orderType: this.orderType || undefined,
        urgentOnly: this.urgentOnly ? true : undefined
      })
      this.orders = result.records || []
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
</style>
