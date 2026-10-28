<template>
  <view class="wg-page" :class="userStore.rootClass">
    <view v-if="loading" class="wg-card">
      <text>正在为您准备，请稍等</text>
    </view>

    <template v-else-if="card">
      <view class="wg-card wg-story wg-card-tint" style="background-color:#d9e2d6;color:#4f6b4a">
        <text class="wg-story-title">微光 · 光之故事卡</text>
        <text class="wg-story-text">{{ card.requesterName }} 遇到了 "{{ card.thing }}"，{{ card.helperName }} 伸出了手。</text>
        <text class="wg-story-text">{{ card.summary }}</text>
        <!-- B：数据感——类型 / 用时 / 浏览数，均取自既有订单字段，不加接口 -->
        <view v-if="order" class="wg-story-stats">
          <view class="wg-story-stat">
            <text class="wg-story-stat-v">{{ typeLabel || '帮助' }}</text>
            <text class="wg-label">求助类型</text>
          </view>
          <view v-if="durationText" class="wg-story-stat">
            <text class="wg-story-stat-v">{{ durationText }}</text>
            <text class="wg-label">从开始到完成</text>
          </view>
          <view class="wg-story-stat">
            <text class="wg-story-stat-v">{{ order.viewCount || 0 }}</text>
            <text class="wg-label">位邻居看过</text>
          </view>
        </view>
        <text class="wg-label" style="margin-top: 10px;color:#4f6b4a;opacity:.8">{{ card.createdAt }}</text>
      </view>
      <view class="wg-btn" @click="saveImage">保存图片</view>
      <view class="wg-btn wg-btn-plain" style="margin-top: 12px" @click="goHome">回到首页</view>
    </template>

    <view v-else class="wg-card">
      <text>这张故事卡还没准备好</text>
    </view>

    <canvas canvas-id="storyCanvas" class="wg-story-canvas"></canvas>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { getStoryCard } from '@/api/incentive'
import { getOrder } from '@/api/order'
import { MESSAGES, ORDER_TYPE_LABELS } from '@/utils/constants'

export default {
  data() {
    return {
      loading: true,
      orderId: null,
      card: null,
      order: null
    }
  },
  computed: {
    userStore() {
      return useUserStore()
    },
    typeLabel() {
      return this.order ? ORDER_TYPE_LABELS[this.order.orderType] || '' : ''
    },
    /** B：从开始帮忙到完成的耗时（优先取助人者标记完成时间，排除等待确认/申诉处理） */
    durationText() {
      if (!this.order) {
        return ''
      }
      const start = this.order.startedAt || this.order.createdAt
      const end = this.order.completeRequestedAt || this.order.completedAt || this.order.createdAt
      if (!start || !end) {
        return ''
      }
      const toMs = (value) => new Date(String(value).replace(' ', 'T')).getTime()
      const minutes = Math.round((toMs(end) - toMs(start)) / 60000)
      if (!Number.isFinite(minutes) || minutes <= 0) {
        return ''
      }
      if (minutes < 60) {
        return minutes + ' 分钟'
      }
      return Math.round((minutes / 60) * 10) / 10 + ' 小时'
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
        const [card, order] = await Promise.all([
          getStoryCard(this.orderId),
          getOrder(this.orderId).catch(() => null)
        ])
        this.card = card
        this.order = order
      } catch (e) {
        console.error('[微光] 故事卡加载失败', e)
        this.card = null
      } finally {
        this.loading = false
      }
    },
    goHome() {
      uni.reLaunch({ url: '/pages/index/index' })
    },
    saveImage() {
      if (!this.card) {
        return
      }
      const ctx = uni.createCanvasContext('storyCanvas', this)
      ctx.setFillStyle('#f2f1ed')
      ctx.fillRect(0, 0, 600, 800)
      ctx.setFillStyle('#4f6b4a')
      ctx.setFontSize(36)
      ctx.fillText('微光 · 光之故事卡', 40, 96)
      ctx.setFillStyle('#34342f')
      ctx.setFontSize(30)
      ctx.fillText(`${this.card.requesterName} 遇到了`, 40, 240)
      ctx.fillText(`"${this.card.thing}"`, 40, 300)
      ctx.fillText(`${this.card.helperName} 伸出了手。`, 40, 360)
      ctx.setFillStyle('#4f6b4a')
      ctx.setFontSize(28)
      ctx.fillText(this.card.summary || '', 40, 470)
      ctx.setFillStyle('#8c8a82')
      ctx.setFontSize(22)
      ctx.fillText(this.card.createdAt || '', 40, 730)
      ctx.draw(false, () => {
        uni.canvasToTempFilePath(
          {
            canvasId: 'storyCanvas',
            success: (res) => {
              uni.saveImageToPhotosAlbum({
                filePath: res.tempFilePath,
                success: () => uni.showToast({ title: '已保存到相册', icon: 'none' }),
                fail: () => uni.showToast({ title: MESSAGES.ERROR, icon: 'none' })
              })
            },
            fail: () => uni.showToast({ title: MESSAGES.ERROR, icon: 'none' })
          },
          this
        )
      })
    }
  }
}
</script>

<style lang="scss">
.wg-story {
  text-align: center;
}

.wg-story-title {
  display: block;
  color: var(--wg-primary-deep);
  font-size: var(--wg-font-lg, 20px);
  margin-bottom: 12px;
}

.wg-story-text {
  display: block;
  margin-bottom: 8px;
  font-size: var(--wg-font-base);
  color: var(--wg-text);
}

.wg-story-canvas {
  position: fixed;
  left: -2000px;
  top: 0;
  width: 600px;
  height: 800px;
}

/* B：故事卡数据条 */
.wg-story-stats {
  display: flex;
  justify-content: space-between;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid rgba(79, 107, 74, 0.2);
}

.wg-story-stat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.wg-story-stat-v {
  font-size: var(--wg-font-lg, 20px);
  font-weight: 800;
  color: #4f6b4a;
}
</style>