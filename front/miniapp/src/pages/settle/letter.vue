<template>
  <view class="wg-page" :class="userStore.rootClass">
    <view class="wg-card wg-card-soft">
      <view class="wg-label">这封信会署您的名字，展示在对方的"我收到的感谢"里。</view>
      <textarea class="wg-textarea" v-model="content" maxlength="500" placeholder="想对邻居说点什么" style="margin-top: 8px" />
      <!-- B：字数实时计数 -->
      <view class="wg-count">{{ content.length }}/500</view>
    </view>
    <!-- 能力三 S2："不会写？我帮您起个头"（详设 §6.2 P11） -->
    <view class="wg-btn wg-btn-plain" @click="aiDraft">{{ draftLoading ? '小助手正在起头…' : '不会写？我帮您起个头' }}</view>
    <view class="wg-btn" :class="{ 'wg-btn-off': sending }" style="margin-top: 12px" @click="send">
      {{ sending ? '正在发送…' : '发送' }}
    </view>
    <view class="wg-btn wg-btn-ghost" style="margin-top: 12px" @click="skip">放弃</view>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { aiDraftLetter, submitLetter } from '@/api/incentive'
import { MESSAGES } from '@/utils/constants'

export default {
  data() {
    return {
      orderId: null,
      content: '',
      draftLoading: false,
      usedAiDraft: false,
      /** 发送在途锁 */
      sending: false
    }
  },
  computed: {
    userStore() {
      return useUserStore()
    }
  },
  onLoad(options) {
    this.orderId = options.id
  },
  methods: {
    /** 能力三 S2：AI 起个头（degraded 时按 P11 文案提示"小助手在休息，给您一个简单开头"） */
    async aiDraft() {
      if (this.draftLoading) {
        return
      }
      this.draftLoading = true
      try {
        const result = await aiDraftLetter(this.orderId)
        this.content = result.letterDraft || this.content
        this.usedAiDraft = true
        if (result.degraded) {
          uni.showToast({ title: '小助手在休息，给您一个简单开头', icon: 'none' })
        }
      } catch (e) {
        console.error('[微光] 感谢信起头失败', e)
        uni.showToast({ title: MESSAGES.ERROR, icon: 'none' })
      } finally {
        this.draftLoading = false
      }
    },
    async send() {
      if (this.sending) {
        return
      }
      const text = this.content.trim()
      if (!text) {
        uni.showToast({ title: '写两句再发送吧', icon: 'none' })
        return
      }
      this.sending = true
      try {
        await submitLetter(this.orderId, { content: text, aiAssisted: this.usedAiDraft ? true : undefined })
        uni.showToast({ title: '您的感谢已送达，它会被好好珍藏。', icon: 'none' })
      } catch (e) {
        console.error('[微光] 感谢信发送失败', e)
        if (e && e.code === 40003) {
          uni.showToast({ title: '已经写过一封啦', icon: 'none' })
        } else {
          uni.showToast({ title: (e && e.message) || MESSAGES.ERROR, icon: 'none' })
          this.sending = false
          return
        }
      }
      setTimeout(() => {
        // 跳转前复位：即使 redirect 失败也不会让发送按钮永久置灰
        this.sending = false
        uni.redirectTo({ url: `/pages/settle/story?id=${this.orderId}` })
      }, 800)
    },
    skip() {
      uni.redirectTo({ url: `/pages/settle/story?id=${this.orderId}` })
    }
  }
}
</script>

<style lang="scss">
/* B：字数实时计数 */
.wg-count {
  text-align: right;
  font-size: var(--wg-font-sm);
  color: var(--wg-text-weak);
}
</style>