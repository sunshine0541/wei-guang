<template>
  <view class="wg-page" :class="userStore.rootClass">
    <view class="wg-card">
      <text class="wg-title">微光 · 隐私说明</text>
      <text class="wg-label">版本 {{ policyVersion }}</text>
    </view>

    <view class="wg-card">
      <text>微光是一个社区邻里互助的小家。我们会收集您主动填写的昵称、头像、年龄、常去地址和"需要留意的小习惯"等信息，只用于让邻居更好地帮到您：</text>
    </view>
    <view class="wg-card">
      <text>您的"小习惯"只会在您的求助被接单后展示给接单的那位邻居；</text>
    </view>
    <view class="wg-card">
      <text>聊天记录仅用于回顾与争议处理，管理员只有在处理申诉或安全上报时才能查看；</text>
    </view>
    <view class="wg-card">
      <text>您可以随时在"设置"中注销账号，注销后个人信息将删除或匿名化，历史订单与争议记录按法规要求保留 {{ userStore.retentionDays }} 天后清除。</text>
    </view>

    <view class="wg-btn" @click="agree">同意并开始使用</view>
    <view class="wg-btn wg-btn-plain" style="margin-top: 12px" @click="refuse">暂不同意</view>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'

const POLICY_VERSION = 'v1.0'

export default {
  data() {
    return {
      policyVersion: POLICY_VERSION
    }
  },
  computed: {
    userStore() {
      return useUserStore()
    }
  },
  methods: {
    async agree() {
      try {
        await this.userStore.agreePrivacy(this.policyVersion)
        if (this.userStore.isNewUser) {
          uni.redirectTo({ url: '/pages/guide/index' })
        } else {
          uni.redirectTo({ url: '/pages/index/index' })
        }
      } catch (e) {
        // 统一 toast 已由请求封装提示
      }
    },
    refuse() {
      uni.showToast({ title: '同意后才能使用微光哦', icon: 'none' })
    }
  }
}
</script>
