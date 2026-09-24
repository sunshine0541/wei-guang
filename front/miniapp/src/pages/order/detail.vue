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
    </template>

    <view v-else class="wg-card">
      <text>这个页面暂时不能看哦</text>
    </view>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { getOrder } from '@/api/order'
import { ORDER_TYPE_LABELS, TIME_EXPECT_LABELS } from '@/utils/constants'

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
</style>
