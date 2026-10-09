<template>
  <view class="wg-page" :class="userStore.rootClass">
    <view class="wg-card">
      <view class="wg-card-head">
        <text>当前微光值</text>
        <text class="wg-strong">{{ balance }}</text>
      </view>
    </view>

    <view v-if="records.length === 0" class="wg-card">
      <text>{{ emptyText }}</text>
    </view>
    <view v-for="(item, index) in records" :key="index" class="wg-card">
      <view class="wg-card-head">
        <text>{{ reasonLabel(item.reasonCode) }}</text>
        <text :class="item.delta >= 0 ? 'wg-plus' : 'wg-minus'">
          {{ item.delta >= 0 ? '+' + item.delta : item.delta }}
        </text>
      </view>
      <view class="wg-label">
        {{ item.createdAt }} · 余额 {{ item.balanceAfter }}
        <template v-if="item.remark"> · {{ item.remark }}</template>
      </view>
    </view>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { getCreditLedger } from '@/api/incentive'
import { CREDIT_REASON_LABELS, MESSAGES } from '@/utils/constants'

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
      return CREDIT_REASON_LABELS[code] || code
    },
    async load() {
      try {
        const result = await getCreditLedger({ page: 1, pageSize: 50 })
        this.balance = result.balance || 0
        this.records = result.records || []
      } catch (e) {
        console.error('[微光] 微光值明细加载失败', e)
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

.wg-minus {
  color: var(--wg-danger);
}
</style>