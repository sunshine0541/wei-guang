<template>
  <view class="wg-page" :class="userStore.rootClass">
    <view class="wg-card wg-login-head">
      <text class="wg-title">微光</text>
      <text class="wg-label">{{ mode === 'LOGIN' ? '欢迎回来' : '第一次来，起个账号吧' }}</text>
    </view>

    <view class="wg-card">
      <view class="wg-label">账号</view>
      <input class="wg-input" :value="username" placeholder="4-20 位字母或数字" @input="username = $event.detail.value" />
      <view class="wg-label" style="margin-top: 12px">密码</view>
      <input class="wg-input" password :value="password" placeholder="6-32 位" @input="password = $event.detail.value" />
      <template v-if="mode === 'REGISTER'">
        <view class="wg-label" style="margin-top: 12px">再输一遍密码</view>
        <input class="wg-input" password :value="password2" placeholder="两次要一样哦" @input="password2 = $event.detail.value" />
      </template>
      <view class="wg-btn" style="margin-top: 16px" @click="submit">{{ mode === 'LOGIN' ? '登录' : '注册' }}</view>
      <view class="wg-btn wg-btn-plain" style="margin-top: 10px" @click="toggleMode">
        {{ mode === 'LOGIN' ? '还没有账号？去注册' : '已经有账号？去登录' }}
      </view>
    </view>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'

/* 与后端 AccountAuthService 校验口径一致 */
const USERNAME_PATTERN = /^[a-zA-Z0-9_]{4,20}$/
const PASSWORD_MIN = 6
const PASSWORD_MAX = 32

/**
 * 登录/注册页（D31：账密登录主导，替代原微信静默登录入口）。
 * 注册成功后走既有引导链（P01 隐私 → P02 引导 → 首页）。
 */
export default {
  data() {
    return {
      mode: 'LOGIN',
      username: '',
      password: '',
      password2: '',
      submitting: false
    }
  },
  computed: {
    userStore() {
      return useUserStore()
    }
  },
  methods: {
    toggleMode() {
      this.mode = this.mode === 'LOGIN' ? 'REGISTER' : 'LOGIN'
      this.password = ''
      this.password2 = ''
    },
    async submit() {
      if (this.submitting) {
        return
      }
      const username = this.username.trim()
      const password = this.password
      if (!username || !password) {
        uni.showToast({ title: '账号和密码都要填哦', icon: 'none' })
        return
      }
      if (!USERNAME_PATTERN.test(username)) {
        uni.showToast({ title: '账号名用 4-20 位字母或数字吧', icon: 'none' })
        return
      }
      if (password.length < PASSWORD_MIN || password.length > PASSWORD_MAX) {
        uni.showToast({ title: '密码要用 6-32 位哦', icon: 'none' })
        return
      }
      if (this.mode === 'REGISTER' && password !== this.password2) {
        uni.showToast({ title: '两次密码不一样，再看看', icon: 'none' })
        return
      }
      this.submitting = true
      try {
        if (this.mode === 'LOGIN') {
          await this.userStore.loginAccount(username, password)
        } else {
          await this.userStore.registerAccount(username, password)
        }
        this.goNext()
      } catch (e) {
        if (e && e.message) {
          uni.showToast({ title: e.message, icon: 'none' })
        }
      } finally {
        this.submitting = false
      }
    },
    goNext() {
      if (!this.userStore.hasPrivacyAgreed) {
        uni.reLaunch({ url: '/pages/guide/privacy' })
      } else if (this.userStore.isNewUser) {
        uni.reLaunch({ url: '/pages/guide/index' })
      } else {
        uni.reLaunch({ url: '/pages/index/index' })
      }
    }
  }
}
</script>

<style lang="scss">
.wg-login-head {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: var(--wg-gap) var(--wg-space);
}
</style>