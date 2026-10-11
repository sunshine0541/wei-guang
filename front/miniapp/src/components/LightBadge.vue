<template>
  <view class="wg-light-badge" @click="showExplain">
    <text class="wg-light-icon">{{ icon }}</text>
    <view class="wg-light-text">
      <text class="wg-light-name">{{ name }}</text>
      <text v-if="hint" class="wg-label">{{ hint }}</text>
    </view>
  </view>
</template>

<script>
/**
 * 光语徽标（详设 §6.2 P13 / §6.4）：当前状态图标+名字，点开一句话解释。
 * name/desc 由 U7 下发（lit=true 时即"被点亮"展示文案）。
 */
export default {
  props: {
    name: { type: String, default: '未点亮' },
    desc: { type: String, default: '' },
    lightState: { type: String, default: 'UNLIT' },
    lit: { type: Boolean, default: false }
  },
  computed: {
    icon() {
      if (this.lit) {
        return '✨'
      }
      if (this.lightState === 'STEADY') {
        return '🌟'
      }
      if (this.lightState === 'RESTING') {
        return '🌙'
      }
      return '🕯️'
    },
    hint() {
      return this.lit ? '已经有人在向您走来' : ''
    }
  },
  methods: {
    showExplain() {
      uni.showModal({
        title: this.name,
        content: this.desc,
        showCancel: false,
        confirmText: '知道了'
      })
    }
  }
}
</script>

<style lang="scss">
.wg-light-badge {
  display: flex;
  align-items: center;
}

.wg-light-icon {
  font-size: 32px;
  margin-right: 10px;
}

.wg-light-text {
  display: flex;
  flex-direction: column;
}

.wg-light-name {
  font-size: var(--wg-font-lg);
  font-weight: 600;
}
</style>