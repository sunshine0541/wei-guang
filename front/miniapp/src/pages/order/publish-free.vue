<template>
  <view class="wg-page" :class="userStore.rootClass">
    <!-- A1 第 2 步：自由描述 + 智能小助手整理 -->
    <view class="wg-card">
      <text class="wg-title">用一句话说说您的需要</text>
      <textarea class="wg-textarea" v-model="freeText" placeholder="例如：帮我去药店取一下降压药" maxlength="500" />
      <view class="wg-btn" @click="doAiSuggest">{{ aiLoading ? '智能小助手正在整理…' : '让小助手帮我整理' }}</view>
    </view>

    <!-- 底部常驻紧急提示条 -->
    <view class="wg-notice wg-notice-warn">
      <u-icon name="warning" :size="18" />
      <text class="wg-emergency-text">{{ emergencyTip }}</text>
    </view>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { aiSuggest } from '@/api/order'
import { EMERGENCY_TIP, STORAGE_KEYS } from '@/utils/constants'

export default {
  data() {
    return {
      freeText: '',
      aiLoading: false,
      emergencyTip: EMERGENCY_TIP
    }
  },
  computed: {
    userStore() {
      return useUserStore()
    }
  },
  methods: {
    /** A1：整理结果写入跨页草稿，跳到表单页继续补充 */
    async doAiSuggest() {
      if (!this.freeText.trim()) {
        uni.showToast({ title: '先写一句话吧', icon: 'none' })
        return
      }
      this.aiLoading = true
      try {
        const result = await aiSuggest(this.freeText.trim())
        uni.setStorageSync(STORAGE_KEYS.PUBLISH_DRAFT, {
          entry: 'free',
          aiResult: result,
          form: {
            source: 'FREE',
            orderType: result.orderType,
            title: result.title,
            description: this.freeText.trim(),
            addressId: null,
            tags: result.tags || [],
            timeExpect: result.urgencySuggest === 'URGENT' ? 'ASAP' : 'ANY',
            urgentFlag: 0,
            extraTag: result.extraTag || '',
            repeatFromOrderId: null
          }
        })
        uni.navigateTo({ url: '/pages/order/publish-form' })
      } catch (e) {
        console.error('[微光] 智能小助手整理失败', e)
      } finally {
        this.aiLoading = false
      }
    }
  }
}
</script>

<style lang="scss">
.wg-emergency-text {
  margin-left: 8px;
  flex: 1;
}
</style>
