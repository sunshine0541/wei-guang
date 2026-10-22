<template>
  <view class="wg-page" :class="userStore.rootClass">
    <view v-if="loading" class="wg-skel">
      <view class="wg-skel-block"></view>
      <view class="wg-skel-block"></view>
      <text class="wg-label" style="margin-top: 6px">正在为您准备，请稍等</text>
    </view>

    <template v-else-if="order">
      <view v-if="!inWindow" class="wg-card">
        <text>这次帮助已完成很久啦</text>
      </view>
      <template v-else>
        <view v-if="isRequester" id="wgEvaluateCard" class="wg-card wg-card-tint" :class="{ 'wg-focus-flash': highlight === 'evaluate' }" style="background-color:#ede3d0;color:#8a7648">
          <view class="wg-label" style="color:#8a7648;opacity:.9">这次帮助怎么样？（必填）</view>
          <view class="wg-stars">
            <view v-for="n in 5" :key="n" class="wg-star" @click="stars = n">
              <u-icon :name="n <= stars ? 'star-fill' : 'star'" :size="34" />
            </view>
          </view>
          <!-- B：随星数变化的暖心文案（低星也给建设性提示） -->
          <view class="wg-star-hint">{{ starHint }}</view>
        </view>

        <!-- B：建议与悄悄话合并为一张卡的两个分区 -->
        <view class="wg-card">
          <template v-if="isRequester">
            <view id="wgAdviceSection">
              <view class="wg-label">想给邻居一句建议（可选，最多 100 字）</view>
              <input class="wg-input" v-model="advice" maxlength="100" placeholder="想给邻居一句建议（可选）" />
              <view class="wg-quick-row">
                <view v-for="w in adviceTags" :key="w" class="wg-chip" @click="appendAdvice(w)">{{ w }}</view>
              </view>
              <view class="wg-count">{{ advice.length }}/100</view>
            </view>
            <view class="wg-divider" />
          </template>

          <view id="wgWhisperSection" :class="{ 'wg-focus-flash': highlight === 'whisper' }">
            <view class="wg-label">悄悄话（可选）</view>
            <textarea
              class="wg-textarea"
              v-model="whisper"
              maxlength="200"
              :focus="focusWhisper"
              placeholder="这句话只有对方能看到，不会公开"
            />
            <view class="wg-quick-row">
              <view v-for="w in whisperQuick" :key="w" class="wg-chip" @click="whisper = w">{{ w }}</view>
            </view>
            <view class="wg-count">{{ whisper.length }}/200</view>
          </view>
        </view>

        <!-- B：未选星时提交置灰 -->
        <view class="wg-btn" :class="{ 'wg-btn-off': isRequester && !stars }" @click="submit">提交</view>
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
      whisper: '',
      adviceTags: ['准时', '耐心', '细心'],
      whisperQuick: ['谢谢你', '下次还找您'],
      focus: '',
      /** B：页面较短时用高亮/聚焦代替滚动锚点 */
      highlight: '',
      focusWhisper: false
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
    },
    /** B：随星数变化的暖心文案（低星也给建设性提示） */
    starHint() {
      const hints = {
        0: '点亮星星，给这次帮助一个评价吧',
        1: '谢谢您如实反馈，我们会更用心',
        2: '还有可以改进的地方，谢谢您告诉我们',
        3: '还不错，谢谢您的鼓励',
        4: '很满意，谢谢您的认可',
        5: '非常满意，这份温暖会被记住'
      }
      return hints[this.stars] || hints[0]
    }
  },
  onLoad(options) {
    this.orderId = options.id
    // B：从 P07 的两个按钮进来时分别锚点定位
    this.focus = options.focus || ''
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
      this.applyFocus()
    },
    /** B：按 P07 按钮意图定位到对应分区；页面不够长时改为高亮 + 输入聚焦 */
    applyFocus() {
      if (this.focus !== 'whisper' && this.focus !== 'evaluate') {
        return
      }
      this.highlight = this.focus === 'whisper' ? 'whisper' : 'evaluate'
      if (this.focus === 'whisper') {
        this.focusWhisper = true
      }
      setTimeout(() => {
        this.highlight = ''
      }, 1600)
      const target = this.focus === 'whisper' ? '#wgWhisperSection' : '#wgEvaluateCard'
      setTimeout(() => {
        const query = uni.createSelectorQuery().in(this)
        query.select(target).boundingClientRect()
        query.selectViewport().scrollOffset()
        query.exec((res) => {
          if (!res || !res[0] || !res[1]) {
            return
          }
          uni.pageScrollTo({ scrollTop: res[0].top + res[1].scrollTop - 12, duration: 200 })
        })
      }, 300)
    },
    /** B：快捷标签追加到建议（去重，超 100 字截断） */
    appendAdvice(word) {
      if (this.advice.indexOf(word) >= 0) {
        return
      }
      const next = this.advice ? `${this.advice}，${word}` : word
      this.advice = next.slice(0, 100)
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
  display: flex;
  align-items: center;
  margin-right: 12px;
  color: var(--sg-sand-d, #8a7648);
}

/* B：星级提示 / 快捷标签 / 字数提示 / 置灰按钮 */
.wg-star-hint {
  font-size: var(--wg-font-sm);
  opacity: 0.9;
}

.wg-quick-row {
  display: flex;
  flex-wrap: wrap;
  margin-top: 8px;
}

.wg-quick-row .wg-chip {
  margin: 0 8px 8px 0;
}

.wg-count {
  text-align: right;
  font-size: var(--wg-font-sm);
  color: var(--wg-text-weak);
}

.wg-divider {
  height: 1px;
  background-color: var(--wg-line);
  margin: 12px 0;
}

.wg-btn-off {
  opacity: 0.5;
}

/* B：入口定位高亮（页面较短无滚动条时替代锚点滚动） */
.wg-focus-flash {
  box-shadow: 0 0 0 2px var(--wg-primary-deep, #4f6b4a);
}
</style>