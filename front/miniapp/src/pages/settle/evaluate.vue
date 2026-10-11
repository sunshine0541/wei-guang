<template>
  <view class="wg-page" :class="userStore.rootClass">
    <view v-if="loading" class="wg-card">
      <text>正在为您准备，请稍等</text>
    </view>

    <template v-else-if="order">
      <view v-if="!inWindow" class="wg-card">
        <text>这次帮助已完成很久啦</text>
      </view>
      <template v-else>
        <view v-if="isRequester" class="wg-card">
          <view class="wg-label">这次帮助怎么样？（必填）</view>
          <view class="wg-stars">
            <text v-for="n in 5" :key="n" class="wg-star" @click="stars = n">
              {{ n <= stars ? '★' : '☆' }}
            </text>
          </view>
          <input class="wg-input" v-model="advice" maxlength="100" placeholder="想给邻居一句建议（可选）" />
        </view>

        <view class="wg-card">
          <view class="wg-label">悄悄话（可选）</view>
          <textarea
            class="wg-textarea"
            v-model="whisper"
            maxlength="200"
            placeholder="这句话只有对方能看到，不会公开"
          />
        </view>

        <view class="wg-btn" @click="submit">提交</view>
        <view class="wg-btn wg-btn-plain" style="margin-top: 12px" @click="skip">跳过</view>
      </template>
    </template>

    <view v-else class="wg-card">
      <text>这个页面暂时不能看哦</text>
    </view>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { getOrder } from '@/api/order'
import { submitEvaluation, submitWhisper } from '@/api/incentive'
import { MESSAGES } from '@/utils/constants'

export default {
  data() {
    return {
      loading: true,
      orderId: null,
      order: null,
      stars: 0,
      advice: '',
      whisper: ''
    }
  },
  computed: {
    userStore() {
      return useUserStore()
    },
    isRequester() {
      return this.order && this.order.myRole === 'REQUESTER'
    },
    inWindow() {
      return this.order && this.order.status === 'COMPLETED'
    }
  },
  onLoad(options) {
    this.orderId = options.id
    this.load()
  },
  methods: {
    async load() {
      this.loading = true
      try {
        this.order = await getOrder(this.orderId)
      } catch (e) {
        console.error('[微光] 评价页加载失败', e)
        this.order = null
      } finally {
        this.loading = false
      }
    },
    async submit() {
      if (this.isRequester && !this.stars) {
        uni.showToast({ title: '先给个星星吧', icon: 'none' })
        return
      }
      try {
        let okText = null
        if (this.isRequester && this.stars) {
          await submitEvaluation(this.orderId, { stars: this.stars, advice: this.advice || null })
          okText = '谢谢您的评价，这会让善意被更多人看见。'
        }
        if (this.whisper.trim()) {
          await submitWhisper(this.orderId, { content: this.whisper.trim() })
          if (!okText) {
            okText = '这句话已悄悄送到。'
          }
        }
        if (okText) {
          uni.showToast({ title: okText, icon: 'none' })
        }
      } catch (e) {
        console.error('[微光] 提交失败', e)
        if (e && e.code === 40003) {
          uni.showToast({ title: '已经提交过啦', icon: 'none' })
        } else if (e && e.code === 40002) {
          this.order = { ...this.order, status: 'CLOSED' }
          uni.showToast({ title: '这次帮助已完成很久啦', icon: 'none' })
          return
        } else {
          uni.showToast({ title: (e && e.message) || MESSAGES.ERROR, icon: 'none' })
          return
        }
      }
      this.next()
    },
    skip() {
      this.next()
    },
    next() {
      if (this.isRequester) {
        uni.redirectTo({ url: `/pages/settle/letter?id=${this.orderId}` })
      } else {
        uni.redirectTo({ url: `/pages/settle/story?id=${this.orderId}` })
      }
    }
  }
}
</script>

<style lang="scss">
.wg-stars {
  display: flex;
  margin: 8px 0;
}

.wg-star {
  font-size: 40px;
  color: #f5a623;
  margin-right: 12px;
}
</style>