<template>
  <view class="wg-page" :class="userStore.rootClass">
    <view class="wg-card wg-credit-top">
      <view class="wg-label">当前微光值</view>
      <view class="wg-bignum">{{ balance }}</view>
    </view>

    <!-- 微光值说明（本次授权新增，文案取详设 §6.4 光语口径原句） -->
    <view class="wg-card wg-card-soft">
      <view class="wg-subtitle">关于微光值</view>
      <view class="wg-credit-line">
        <u-icon name="star-fill" :size="16" />
        <text class="wg-credit-line-text">您的光正亮着，也在照亮别人。</text>
      </view>
      <view class="wg-credit-line">
        <u-icon name="clock" :size="16" />
        <text class="wg-credit-line-text">您的光在小憩，养足精神再出发。</text>
      </view>
      <view class="wg-credit-line">
        <u-icon name="checkmark-circle-fill" :size="16" />
        <text class="wg-credit-line-text">您曾被照亮，如今也照亮了别人——光已传递。</text>
      </view>
    </view>

    <view v-if="loading" class="wg-empty">{{ loadingText }}</view>
    <view v-else-if="records.length === 0" class="wg-empty">{{ emptyText }}</view>
    <view v-for="(item, index) in records" :key="item.id || index" class="wg-card">
      <view class="wg-card-head">
        <text class="wg-tag" style="margin: 0">{{ reasonLabel(item.reasonCode) }}</text>
        <text :class="item.delta >= 0 ? 'wg-plus' : 'wg-minus'">
          {{ item.delta >= 0 ? '+' + item.delta : item.delta }}
        </text>
      </view>
      <view class="wg-label" style="margin-top: 6px">
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
      balance: '--',
      records: [],
      loading: true,
      loadingText: MESSAGES.LOADING,
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
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style lang="scss">
.wg-credit-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

/* 说明卡内每行：图标 + 光语句 */
.wg-credit-line {
  display: flex;
  align-items: center;
  margin-top: 8px;
}

.wg-credit-line-text {
  margin-left: 8px;
  font-size: var(--wg-font-base);
  line-height: 1.5;
}

.wg-plus {
  color: #4caf50;
}

.wg-minus {
  color: var(--wg-danger);
}
</style>