<template>
  <view class="wg-page" :class="userStore.rootClass">
    <view class="wg-card wg-me-head">
      <image v-if="userStore.avatarUrl" class="wg-avatar" :src="fileUrl(userStore.avatarUrl)" mode="aspectFill" />
      <text class="wg-title">{{ userStore.nickname || '邻居' }}</text>
    </view>

    <view class="wg-card" @click="goMyOrders">
      <view class="wg-card-head">
        <text>我的求助</text>
        <text class="wg-label">查看进展</text>
      </view>
    </view>

    <view class="wg-card" @click="goCredit">
      <view class="wg-card-head">
        <text>我的微光值</text>
        <text class="wg-strong">{{ creditBalance }}</text>
      </view>
      <view class="wg-label">查看明细</view>
    </view>

    <view class="wg-card" @click="goPoints">
      <view class="wg-card-head">
        <text>我的积分</text>
        <text class="wg-strong">{{ pointBalance }}</text>
      </view>
      <view class="wg-label">查看明细</view>
    </view>

    <view class="wg-card">
      <text>我收到的感谢</text>
      <view v-if="thanks.length === 0" class="wg-label" style="margin-top: 8px">
        {{ emptyText }}
      </view>
      <view v-for="item in thanks" :key="item.letterId" class="wg-thanks-item">
        <view class="wg-label">{{ item.senderNickname || '邻居' }} · {{ item.title }}</view>
        <text>{{ item.content }}</text>
      </view>
    </view>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { getCreditLedger, getMyThanks, getPointLedger } from '@/api/incentive'
import { BASE_URL, MESSAGES } from '@/utils/constants'

export default {
  data() {
    return {
      creditBalance: 0,
      pointBalance: 0,
      thanks: [],
      emptyText: MESSAGES.EMPTY
    }
  },
  computed: {
    userStore() {
      return useUserStore()
    }
  },
  onShow() {
    this.load()
  },
  methods: {
    fileUrl(url) {
      if (!url) {
        return ''
      }
      return url.indexOf('http') === 0 ? url : BASE_URL + url
    },
    async load() {
      try {
        const credit = await getCreditLedger({ page: 1, pageSize: 1 })
        this.creditBalance = credit.balance || 0
        const points = await getPointLedger({ page: 1, pageSize: 1 })
        this.pointBalance = points.balance || 0
        const wall = await getMyThanks({ page: 1, pageSize: 10 })
        this.thanks = wall.records || []
      } catch (e) {
        console.error('[微光] 个人中心加载失败', e)
      }
    },
    goCredit() {
      uni.navigateTo({ url: '/pages/user/credit' })
    },
    goMyOrders() {
      uni.navigateTo({ url: '/pages/order/mine' })
    },
    goPoints() {
      uni.navigateTo({ url: '/pages/user/points' })
    }
  }
}
</script>

<style lang="scss">
.wg-me-head {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.wg-avatar {
  width: 88px;
  height: 88px;
  border-radius: 50%;
  margin-bottom: 8px;
  background-color: #eee5d8;
}

.wg-strong {
  color: #b06a00;
  font-size: var(--wg-font-lg, 20px);
}

.wg-thanks-item {
  padding: 10px 0;
  border-bottom: 1px solid #f0e9dd;
}
</style>