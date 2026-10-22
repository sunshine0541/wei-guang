<template>
  <view class="wg-page" :class="userStore.rootClass">
    <!-- 模式切换（U3，切换同时重置动效为该模式默认值） -->
    <view class="wg-card">
      <view class="wg-label">显示模式</view>
      <view class="wg-seg" style="margin-top: 10px">
        <view
          class="wg-seg-item"
          :class="{ 'wg-seg-item-active': !userStore.isElder }"
          @click="chooseMode('NORMAL')"
        >
          普通模式
        </view>
        <view
          class="wg-seg-item"
          :class="{ 'wg-seg-item-active': userStore.isElder }"
          @click="chooseMode('ELDER')"
        >
          大字模式
        </view>
      </view>
      <view class="wg-label">大字模式：字更大、按钮更大，动效会默认关闭，可随时在下方重新打开。</view>
    </view>

    <!-- 动效开关（U4，独立生效） -->
    <view class="wg-card">
      <view class="wg-card-head">
        <text>页面动效</text>
        <switch :key="switchKey" :checked="userStore.motionEnabled === 1" color="#7e9982" @change="onMotionChange" />
      </view>
      <view class="wg-label">关闭后，页面里的动画会停下来，更安静。</view>
    </view>

    <!-- 隐私说明查看（含版本号） -->
    <view class="wg-rows">
      <view class="wg-row" @click="goPrivacy">
        <view class="wg-set-row-lead">
          <u-icon name="info-circle" :size="16" />
          <text class="wg-set-row-text">隐私说明</text>
        </view>
        <text class="wg-row-label">版本 {{ policyVersion }}</text>
      </view>
    </view>

    <view class="wg-btn wg-btn-plain wg-set-logout" @click="onLogout">
      <u-icon name="arrow-left" :size="16" />
      <text class="wg-set-logout-text">退出登录</text>
    </view>

    <!-- 注销账号（适老差异：置底并弱化配色，详设 §6.2 P18） -->
    <view class="wg-btn wg-btn-ghost wg-set-cancel" @click="onCancelAccount">
      <u-icon name="warning" :size="16" />
      <text class="wg-set-cancel-text">注销账号</text>
    </view>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { cancelAccount } from '@/api/user'
import { clearToken } from '@/api/request'

const POLICY_VERSION = 'v1.0'

/**
 * P18 设置（详设 §6.2）：模式切换 U3 / 动效开关 U4 / 隐私说明查看 / 注销 U6 / 退出登录 A2。
 * 切换模式会重置动效为该模式默认值（后端 U3 落库），提示用户可随时单独调整。
 */
export default {
  data() {
    return {
      policyVersion: POLICY_VERSION,
      switchKey: 0
    }
  },
  computed: {
    userStore() {
      return useUserStore()
    }
  },
  methods: {
    async chooseMode(mode) {
      if (this.userStore.uiMode === mode) {
        return
      }
      try {
        await this.userStore.switchMode(mode)
        uni.showToast({
          title:
            mode === 'ELDER'
              ? '已切换为大字模式，动效已为您关闭，可随时在下方打开。'
              : '已切换为普通模式，动效已为您打开，可随时在下方关闭。',
          icon: 'none',
          duration: 3000
        })
      } catch (e) {
        // 失败提示已由请求封装统一处理
      }
    },
    async onMotionChange(event) {
      const enabled = event.detail.value ? 1 : 0
      try {
        await this.userStore.switchMotion(enabled)
      } catch (e) {
        // 失败提示已由请求封装统一处理
      } finally {
        // 无论成败都重建 switch，使其与 store 实际值保持一致（失败时回弹）
        this.switchKey += 1
      }
    },
    goPrivacy() {
      uni.navigateTo({ url: '/pages/guide/privacy?view=1' })
    },
    /** 退出登录（A2）：清本地凭证，回首页重新静默登录（不清账号） */
    async onLogout() {
      await this.userStore.logout()
      uni.reLaunch({ url: '/pages/index/index' })
    },
    /** 注销账号（U6）：二次确认（写死模板，天数运行时插值） */
    onCancelAccount() {
      const content =
        '注销后，您的个人信息将被删除或匿名化，历史订单记录将保留 ' +
        this.userStore.retentionDays +
        ' 天。确定要离开吗？'
      uni.showModal({
        title: '注销账号',
        content,
        confirmText: '确定离开',
        cancelText: '再想想',
        confirmColor: '#d9534f',
        success: (res) => {
          if (res.confirm) {
            this.doCancelAccount()
          }
        }
      })
    },
    async doCancelAccount() {
      try {
        await cancelAccount()
      } catch (e) {
        return
      }
      clearToken()
      this.userStore.$reset()
      uni.showToast({ title: '您的账号已注销，感谢您曾经的点亮。', icon: 'none', duration: 3000 })
      setTimeout(() => {
        uni.reLaunch({ url: '/pages/index/index' })
      }, 1500)
    }
  }
}
</script>

<style lang="scss">
/* 隐私说明行：图标 + 文案 */
.wg-set-row-lead {
  display: flex;
  align-items: center;
}

.wg-set-row-text {
  margin-left: 8px;
}

/* 按钮内图标与文字间距 */
.wg-set-logout {
  margin-top: 20px;
}

.wg-set-logout-text,
.wg-set-cancel-text {
  margin-left: 6px;
}

/* 注销入口：置底弱化配色（适老差异） */
.wg-set-cancel {
  margin: 48px 0 16px;
}

.mode-elder .wg-set-cancel {
  margin-top: 72px;
}
</style>