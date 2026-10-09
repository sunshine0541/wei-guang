<template>
  <view class="wg-page" :class="userStore.rootClass">
    <view v-if="loading" class="wg-card">
      <text>正在为您准备，请稍等</text>
    </view>

    <template v-else-if="card">
      <view class="wg-card wg-story">
        <text class="wg-story-title">微光 · 光之故事卡</text>
        <text class="wg-story-text">{{ card.requesterName }} 遇到了 "{{ card.thing }}"，{{ card.helperName }} 伸出了手。</text>
        <text class="wg-story-text">{{ card.summary }}</text>
        <text class="wg-label" style="margin-top: 10px">{{ card.createdAt }}</text>
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
import { MESSAGES } from '@/utils/constants'

export default {
  data() {
    return {
      loading: true,
      orderId: null,
      card: null
    }
  },
  computed: {
    userStore() {
      return useUserStore()
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
        this.card = await getStoryCard(this.orderId)
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
      ctx.setFillStyle('#FFF8EE')
      ctx.fillRect(0, 0, 600, 800)
      ctx.setFillStyle('#B06A00')
      ctx.setFontSize(36)
      ctx.fillText('微光 · 光之故事卡', 40, 96)
      ctx.setFillStyle('#3A3A3A')
      ctx.setFontSize(30)
      ctx.fillText(`${this.card.requesterName} 遇到了`, 40, 240)
      ctx.fillText(`"${this.card.thing}"`, 40, 300)
      ctx.fillText(`${this.card.helperName} 伸出了手。`, 40, 360)
      ctx.setFillStyle('#B06A00')
      ctx.setFontSize(28)
      ctx.fillText(this.card.summary || '', 40, 470)
      ctx.setFillStyle('#9A9A9A')
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
  color: #b06a00;
  font-size: var(--wg-font-lg, 20px);
  margin-bottom: 12px;
}

.wg-story-text {
  display: block;
  margin-bottom: 8px;
  font-size: var(--wg-font-base);
}

.wg-story-canvas {
  position: fixed;
  left: -2000px;
  top: 0;
  width: 600px;
  height: 800px;
}
</style>