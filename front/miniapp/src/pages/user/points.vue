<template>
  <view class="wg-page" :class="userStore.rootClass">
    <view class="wg-card wg-points-top">
      <view class="wg-label">当前积分</view>
      <view class="wg-bignum">{{ balance }}</view>
    </view>

    <view v-if="records.length === 0" class="wg-empty">{{ emptyText }}</view>
    <view v-for="(item, index) in records" :key="index" class="wg-card">
      <view class="wg-card-head">
        <text class="wg-tag" style="margin: 0">{{ reasonLabel(item.reasonCode) }}</text>
        <text class="wg-plus">+{{ item.delta }}</text>
      </view>
      <view class="wg-label" style="margin-top: 6px">{{ item.createdAt }} · 余额 {{ item.balanceAfter }}</view>
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
.wg-points-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.wg-plus {
  color: #4caf50;
}
</style>