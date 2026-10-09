<template>
  <view class="wg-page" :class="userStore.rootClass">
    <view class="wg-card">
      <view class="wg-label">这封信会署您的名字，展示在对方的"我收到的感谢"里。</view>
      <textarea class="wg-textarea" v-model="content" maxlength="500" placeholder="想对邻居说点什么" style="margin-top: 8px" />
    </view>
    <!-- "不会写？我帮您起个头"（能力三 S2）在 T13 落地后加入 -->
    <view class="wg-btn" @click="send">发送</view>
    <view class="wg-btn wg-btn-plain" style="margin-top: 12px" @click="skip">放弃</view>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { submitLetter } from '@/api/incentive'
import { MESSAGES } from '@/utils/constants'

export default {
  data() {
    return {
      orderId: null,
      content: ''
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
    async send() {
      const text = this.content.trim()
      if (!text) {
        uni.showToast({ title: '写两句再发送吧', icon: 'none' })
        return
      }
      try {
        await submitLetter(this.orderId, { content: text })
        uni.showToast({ title: '已送出，谢谢您', icon: 'none' })
      } catch (e) {
        console.error('[微光] 感谢信发送失败', e)
        if (e && e.code === 40003) {
          uni.showToast({ title: '已经写过一封啦', icon: 'none' })
        } else {
          uni.showToast({ title: (e && e.message) || MESSAGES.ERROR, icon: 'none' })
          return
        }
      }
      setTimeout(() => {
        uni.redirectTo({ url: `/pages/settle/story?id=${this.orderId}` })
      }, 800)
    },
    skip() {
      uni.redirectTo({ url: `/pages/settle/story?id=${this.orderId}` })
    }
  }
}
</script>