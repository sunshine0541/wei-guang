<template>
  <view class="wg-card">
    <view class="wg-card-head">
      <text>紧急电话</text>
      <text class="wg-label">{{ tip }}</text>
    </view>
    <view class="wg-emergency-btns">
      <view class="wg-btn wg-emergency-btn wg-emergency-danger" @click="call('120')">120 急救</view>
      <view class="wg-btn wg-emergency-btn" @click="call('110')">110 报警</view>
    </view>
  </view>
</template>

<script>
import { EMERGENCY_TIP } from '@/utils/constants'

/**
 * 紧急电话卡片（详设 §6.1 紧急提示三处常驻之③）：首页常驻，120 急救 / 110 报警，一键拨打。
 */
export default {
  data() {
    return {
      tip: EMERGENCY_TIP
    }
  },
  methods: {
    call(phoneNumber) {
      uni.makePhoneCall({ phoneNumber })
    }
  }
}
</script>

<style lang="scss">
.wg-emergency-btns {
  display: flex;
  margin-top: 10px;
}

.wg-emergency-btn {
  flex: 1;
  margin-right: 10px;
}

.wg-emergency-btn:last-child {
  margin-right: 0;
}

.wg-emergency-danger {
  background-color: var(--wg-danger);
}
</style>