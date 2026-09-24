import { defineStore } from 'pinia'
import { loginByCode, logout as logoutApi } from '@/api/auth'
import { getProfile, privacyAgree, setMode, setMotion, lightInvite } from '@/api/user'
import { clearToken, setToken } from '@/api/request'

const DEFAULT_RETENTION_DAYS = 180
const DEFAULT_ELDER_AGE_THRESHOLD = 60

/**
 * 用户态（详设 §1.3 store/user：档案、模式、动效）。
 * rootClass 供各页根节点绑定：.mode-elder / .motion-off（详设 §1.3 适老主题）。
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    isNewUser: false,
    hasPrivacyAgreed: false,
    nickname: '',
    avatarUrl: '',
    age: null,
    uiMode: 'NORMAL',
    motionEnabled: 1,
    creditScore: 0,
    lightState: 'UNLIT',
    lightPassed: 0,
    /** 隐私说明与注销文案的天数插值（sys_config compliance.retention_days，随 A1 下发） */
    retentionDays: DEFAULT_RETENTION_DAYS,
    /** 年长模式年龄阈值（sys_config elder.age_threshold，随 A1 下发） */
    elderAgeThreshold: DEFAULT_ELDER_AGE_THRESHOLD
  }),

  getters: {
    isElder: (state) => state.uiMode === 'ELDER',
    rootClass: (state) =>
      [state.uiMode === 'ELDER' ? 'mode-elder' : '', state.motionEnabled === 0 ? 'motion-off' : '']
        .filter(Boolean)
        .join(' ')
  },

  actions: {
    /** 启动链路：静默登录 → 落 token → 拉档案 */
    async bootstrap() {
      const code = await new Promise((resolve, reject) => {
        uni.login({ provider: 'weixin', success: (res) => resolve(res.code), fail: reject })
      })
      const loginResult = await loginByCode(code)
      setToken(loginResult.token)
      this.isNewUser = loginResult.isNewUser === true
      this.hasPrivacyAgreed = loginResult.needPrivacyAgree !== true
      if (loginResult.config) {
        this.retentionDays = loginResult.config.retentionDays || DEFAULT_RETENTION_DAYS
        this.elderAgeThreshold = loginResult.config.elderAgeThreshold || DEFAULT_ELDER_AGE_THRESHOLD
      }
      this.applyProfile(loginResult.profile)
      this.creditScore = loginResult.creditScore || 0
      await this.loadProfile()
    },

    async loadProfile() {
      const profile = await getProfile()
      this.applyProfile(profile)
      this.hasPrivacyAgreed = profile.hasPrivacyAgreed === true
      this.creditScore = profile.creditScore || 0
      this.lightState = profile.lightState || 'UNLIT'
      this.lightPassed = profile.lightPassed || 0
    },

    applyProfile(profile) {
      if (!profile) {
        return
      }
      this.nickname = profile.nickname || ''
      this.avatarUrl = profile.avatarUrl || ''
      this.age = profile.age === undefined ? null : profile.age
      this.uiMode = profile.uiMode || 'NORMAL'
      this.motionEnabled = profile.motionEnabled === 0 ? 0 : 1
    },

    async agreePrivacy(version) {
      await privacyAgree(version)
      this.hasPrivacyAgreed = true
    },

    async switchMode(uiMode) {
      const result = await setMode(uiMode)
      this.uiMode = result.uiMode
      this.motionEnabled = result.motionEnabled
    },

    async switchMotion(enabled) {
      const result = await setMotion(enabled)
      this.motionEnabled = result.motionEnabled
    },

    async answerLightInvite(choice) {
      await lightInvite(choice)
      await this.loadProfile()
    },

    async logout() {
      try {
        await logoutApi()
      } catch (e) {
        // 退出登录失败不阻塞前端清理
      }
      clearToken()
      this.$reset()
    }
  }
})
