import { defineStore } from 'pinia'
import { loginAccount as loginAccountApi, registerAccount as registerAccountApi, logout as logoutApi } from '@/api/auth'
import { getProfile, privacyAgree, setMode, setMotion, lightInvite } from '@/api/user'
import { clearToken, getToken, setToken } from '@/api/request'
import { wsClient } from '@/utils/ws'
import { useNotifyStore } from '@/store/notify'

const DEFAULT_RETENTION_DAYS = 180
const DEFAULT_ELDER_AGE_THRESHOLD = 60
const DEFAULT_POLICY_VERSION = 'v1.0'
/** 启动链路只跑一次：App.onLaunch 与首页 onLoad 可能同时触发 */
let bootstrapPromise = null

/**
 * 用户态（详设 §1.3 store/user；登录方式见 D31：账密主导）：
 * bootstrap = 有凭证 → 拉档案；无凭证 → needLogin=true（由页面跳登录页）。
 * rootClass 供各页根节点绑定：.mode-elder / .motion-off（详设 §1.3 适老主题）。
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    /** 无凭证待登录（首页等页面据此跳登录页） */
    needLogin: false,
    /** 启动链路是否已完成（有 token 时=档案已拉取） */
    bootstrapped: false,
    isNewUser: false,
    hasPrivacyAgreed: false,
    /** 本人用户 id（U1 下发；用于「我收到的评价」复用 U12 邻居评价接口） */
    userId: null,
    nickname: '',
    avatarUrl: '',
    age: null,
    uiMode: 'NORMAL',
    motionEnabled: 1,
    creditScore: 0,
    /** 接上门/陪同类的微光值门槛（U1 下发，sys_config credit.threshold） */
    creditThreshold: 0,
    /** 是否处于接单冷却期（U1 下发） */
    coolingDown: false,
    lightState: 'UNLIT',
    lightPassed: 0,
    /** 隐私说明与注销文案的天数插值（sys_config compliance.retention_days） */
    retentionDays: DEFAULT_RETENTION_DAYS,
    /** 年长模式年龄阈值（sys_config elder.age_threshold） */
    elderAgeThreshold: DEFAULT_ELDER_AGE_THRESHOLD,
    /** 隐私说明版本号（sys_config privacy.policy_version，D50） */
    policyVersion: DEFAULT_POLICY_VERSION
  }),

  getters: {
    isElder: (state) => state.uiMode === 'ELDER',
    rootClass: (state) =>
      [state.uiMode === 'ELDER' ? 'mode-elder' : '', state.motionEnabled === 0 ? 'motion-off' : '']
        .filter(Boolean)
        .join(' ')
  },

  actions: {
    /** 启动链路：有 token → 拉档案；无 token → 标记待登录；并发调用共享同一个 Promise */
    async bootstrap() {
      if (this.bootstrapped) {
        return
      }
      if (bootstrapPromise) {
        return bootstrapPromise
      }
      bootstrapPromise = this.doBootstrap()
      try {
        await bootstrapPromise
      } finally {
        bootstrapPromise = null
      }
    },

    async doBootstrap() {
      if (!getToken()) {
        this.needLogin = true
        this.bootstrapped = true
        return
      }
      await this.loadProfile()
      this.needLogin = false
      this.bootstrapped = true
    },

    /** 账号登录（登录页调用） */
    async loginAccount(username, password) {
      const result = await loginAccountApi({ username, password })
      this.applyLoginResult(result)
    },

    /** 账号注册（登录页调用；privacyAgreed=注册页勾选隐私说明，服务端落 A3） */
    async registerAccount(username, password, privacyAgreed) {
      const result = await registerAccountApi({
        username,
        password,
        privacyAgreed: privacyAgreed === true
      })
      this.applyLoginResult(result)
    },

    /** 登录/注册共用的落地处理 */
    applyLoginResult(result) {
      setToken(result.token)
      this.needLogin = false
      this.isNewUser = result.isNewUser === true
      this.hasPrivacyAgreed = result.needPrivacyAgree !== true
      if (result.config) {
        this.retentionDays = result.config.retentionDays || DEFAULT_RETENTION_DAYS
        this.elderAgeThreshold = result.config.elderAgeThreshold || DEFAULT_ELDER_AGE_THRESHOLD
        this.policyVersion = result.config.privacyVersion || DEFAULT_POLICY_VERSION
      }
      this.applyProfile(result.profile)
      this.creditScore = result.creditScore || 0
      this.bootstrapped = true
      wsClient.connect()
    },

    async loadProfile() {
      const profile = await getProfile()
      this.applyProfile(profile)
      this.hasPrivacyAgreed = profile.hasPrivacyAgreed === true
      this.creditScore = profile.creditScore || 0
      this.lightState = profile.lightState || 'UNLIT'
      this.lightPassed = profile.lightPassed || 0
      this.retentionDays = profile.retentionDays || DEFAULT_RETENTION_DAYS
      this.elderAgeThreshold = profile.elderAgeThreshold || DEFAULT_ELDER_AGE_THRESHOLD
      this.policyVersion = profile.privacyVersion || DEFAULT_POLICY_VERSION
      // isNewUser 只是"注册/登录瞬间"的标记：能拉到档案说明账号已落地，
      // 清掉它，避免引导完成后进首页又被弹回引导第一步（T17 修正5）
      this.isNewUser = false
    },

    applyProfile(profile) {
      if (!profile) {
        return
      }
      this.userId = profile.userId === undefined ? null : profile.userId
      this.nickname = profile.nickname || ''
      this.avatarUrl = profile.avatarUrl || ''
      this.age = profile.age === undefined ? null : profile.age
      if (profile.creditThreshold !== undefined) {
        this.creditThreshold = profile.creditThreshold
      }
      if (profile.coolingDown !== undefined) {
        this.coolingDown = profile.coolingDown === true
      }
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
      const notifyStore = useNotifyStore()
      notifyStore.unbindWs()
      notifyStore.$reset()
      wsClient.close()
    }
  }
})