<template>
  <view class="wg-page" :class="userStore.rootClass">
    <view class="wg-card">
      <view class="wg-card-head">
        <text>当前积分</text>
        <text class="wg-strong">{{ balance }}</text>
      </view>
    </view>

    <view v-if="records.length === 0" class="wg-card">
      <text>{{ emptyText }}</text>
    </view>
    <view v-for="(item, index) in records" :key="index" class="wg-card">
      <view class="wg-card-head">
        <text>{{ reasonLabel(item.reasonCode) }}</text>
        <text class="wg-plus">+{{ item.delta }}</text>
      </view>
      <view class="wg-label">{{ item.createdAt }} · 余额 {{ item.balanceAfter }}</view>
    </view>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { getPointLedger } from '@/api/incentive'
import { MESSAGES, POINT_REASON_LABELS } from '@/utils/constants'

export default {
  data() {
    return {
      balance: 0,
      records: [],
      emptyText: MESSAGES.EMPTY
    }
  },
  computed: {
    userStore() {
      return useUserStore()
    }
  },
  onLoad() {
    this.load()
  },
  methods: {
    reasonLabel(code) {
      return POINT_REASON_LABELS[code] || code
    },
    async load() {
      try {
        const result = await getPointLedger({ page: 1, pageSize: 50 })
        this.balance = result.balance || 0
        this.records = result.records || []
      } catch (e) {
        console.error('[微光] 积分明细加载失败', e)
      }
    }
  }
}
</script>

<style lang="scss">
.wg-strong {
  color: #b06a00;
  font-size: var(--wg-font-lg, 20px);
}

.wg-plus {
  color: #4caf50;
}
</style>