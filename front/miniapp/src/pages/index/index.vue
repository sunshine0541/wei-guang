<template>
  <view class="wg-page" :class="userStore.rootClass">
    <view class="wg-card">
      <text class="wg-title">微光</text>
      <text class="wg-label">T05 工程骨架验证页（首页空白壳）</text>
    </view>

    <view v-if="loading" class="wg-card">
      <text>正在为您准备，请稍等</text>
    </view>

    <view v-else class="wg-card">
      <view>昵称：{{ userStore.nickname || '（未设置）' }}</view>
      <view>模式：{{ userStore.uiMode }} / motionEnabled={{ userStore.motionEnabled }}</view>
      <view>根节点 class：{{ userStore.rootClass || '（无）' }}</view>
      <view>微光值：{{ userStore.creditScore }}</view>
      <view>光语状态：{{ userStore.lightState }}</view>
      <view>隐私已同意：{{ userStore.hasPrivacyAgreed ? '是' : '否' }}</view>
      <view>留存天数（配置下发）：{{ userStore.retentionDays }}</view>
    </view>

    <view class="wg-btn" @click="toggleMode">
      切换为{{ userStore.isElder ? '普通' : '年长' }}模式
    </view>
    <view class="wg-btn wg-btn-plain" style="margin-top: 12px" @click="toggleMotion">
      {{ userStore.motionEnabled === 1 ? '关闭动效' : '开启动效' }}
    </view>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'

export default {
  data() {
    return {
      loading: true
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
      } catch (e) {
        uni.showToast({ title: '网络打了个盹，请再试一次，我在这儿等您。', icon: 'none' })
      } finally {
        this.loading = false
      }
    },
    async toggleMode() {
      await this.userStore.switchMode(this.userStore.isElder ? 'NORMAL' : 'ELDER')
    },
    async toggleMotion() {
      await this.userStore.switchMotion(this.userStore.motionEnabled === 1 ? 0 : 1)
    }
  }
}
</script>
