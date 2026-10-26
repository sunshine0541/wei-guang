<template>
  <view class="wg-page" :class="userStore.rootClass">
    <view v-if="loading" class="wg-card">
      <text>正在为您准备，请稍等</text>
    </view>

    <template v-else-if="order">
      <!-- 类型与地址只读：修改不改变这两项 -->
      <view class="wg-card">
        <view class="wg-label">这是哪一类帮助</view>
        <view class="wg-label">类型：{{ typeLabel(order.orderType) }}</view>
        <view v-if="order.orderType === 'REMOTE'" class="wg-label">远程帮助全程线上完成，不需要地址</view>
        <view v-else class="wg-label">地址：{{ order.addressArea || '发布时已确定' }}</view>
        <view class="wg-label">类型和地址暂不可修改；如需更换，请撤回后重新发布。</view>
      </view>

      <view class="wg-card">
        <view class="wg-label">标题（不超过 20 字）</view>
        <input class="wg-input" v-model="form.title" maxlength="20" placeholder="一句话说明白" />

        <view class="wg-label" style="margin-top: 12px">再补充两句（可选，不超过 200 字）</view>
        <textarea class="wg-textarea" v-model="form.description" maxlength="200" />

        <view class="wg-label" style="margin-top: 12px">标签（最多 3 个）</view>
        <view class="wg-tags">
          <view
            v-for="tag in tagOptions"
            :key="tag"
            class="wg-chip"
            :class="{ 'wg-chip-active': form.tags.indexOf(tag) >= 0 }"
            @click="toggleTag(tag)"
          >
            {{ tag }}
          </view>
        </view>

        <view class="wg-label" style="margin-top: 12px">自定义标签（可选，最多 10 字）</view>
        <input class="wg-input" v-model="form.extraTag" maxlength="10" placeholder="词表里没有合适的？自己写一个" />

        <view class="wg-label" style="margin-top: 12px">希望什么时候</view>
        <view class="wg-tags">
          <view
            v-for="expect in timeOptions"
            :key="expect"
            class="wg-chip"
            :class="{ 'wg-chip-active': form.timeExpect === expect }"
            @click="form.timeExpect = expect"
          >
            {{ timeLabel(expect) }}
          </view>
        </view>

        <view class="wg-chip" :class="{ 'wg-chip-active': form.urgentFlag === 1 }" style="margin-top: 12px" @click="toggleUrgent">
          {{ form.urgentFlag === 1 ? '已标"急"' : '标"急"（情况真的很急才勾）' }}
        </view>
      </view>

      <view class="wg-btn" @click="save">保存修改</view>
      <view class="wg-btn wg-btn-plain" style="margin-top: 12px" @click="back">先不改了</view>
    </template>

    <view v-else class="wg-card">
      <text>{{ errorText }}</text>
      <view class="wg-btn wg-btn-plain" style="margin-top: 12px" @click="back">返回上一页</view>
    </view>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { getOrder, modifyOrder } from '@/api/order'
import {
  HELP_TAGS,
  MESSAGES,
  ORDER_TYPE_LABELS,
  TIME_EXPECT_LABELS,
  TIME_EXPECT_OPTIONS
} from '@/utils/constants'

const URGENT_CONFIRM = '勾选"急"请确认情况真的紧急。紧急情况请直接拨打120/110。滥用"急"会影响您的微光值。'

/**
 * O16 修改待响应求助（D53）：仅发布者、仅 PENDING；类型与地址只读。
 */
export default {
  data() {
    return {
      loading: true,
      orderId: null,
      order: null,
      errorText: MESSAGES.FORBIDDEN,
      tagOptions: HELP_TAGS,
      timeOptions: TIME_EXPECT_OPTIONS,
      form: {
        title: '',
        description: '',
        tags: [],
        extraTag: '',
        timeExpect: 'ANY',
        urgentFlag: 0
      }
    }
  },
  computed: {
    userStore() {
      return useUserStore()
    }
  },
  onLoad(options) {
    this.orderId = options && options.id
    if (!this.orderId) {
      this.loading = false
      this.errorText = MESSAGES.FORBIDDEN
      return
    }
    this.load()
  },
  methods: {
    typeLabel(type) {
      return ORDER_TYPE_LABELS[type] || type
    },
    timeLabel(expect) {
      return TIME_EXPECT_LABELS[expect] || expect
    },
    async load() {
      this.loading = true
      try {
        const order = await getOrder(this.orderId)
        if (!order || !order.mine || order.status !== 'PENDING') {
          this.order = null
          this.errorText = MESSAGES.FORBIDDEN
          return
        }
        this.order = order
        this.form = {
          title: order.title || '',
          description: order.description || '',
          tags: (order.tags || []).slice(),
          extraTag: order.extraTag || '',
          timeExpect: order.timeExpect || 'ANY',
          urgentFlag: order.urgentFlag === 1 ? 1 : 0
        }
      } catch (e) {
        console.error('[微光] 修改页加载失败', e)
        this.order = null
        this.errorText = MESSAGES.ERROR
      } finally {
        this.loading = false
      }
    },
    toggleTag(tag) {
      const index = this.form.tags.indexOf(tag)
      if (index >= 0) {
        this.form.tags.splice(index, 1)
      } else if (this.form.tags.length >= 3) {
        uni.showToast({ title: '最多选 3 个标签', icon: 'none' })
      } else {
        this.form.tags.push(tag)
      }
    },
    toggleUrgent() {
      if (this.form.urgentFlag === 1) {
        this.form.urgentFlag = 0
        return
      }
      uni.showModal({
        title: '标"急"前请确认',
        content: URGENT_CONFIRM,
        confirmText: '确实很急',
        cancelText: '再想想',
        success: (res) => {
          if (res.confirm) {
            this.form.urgentFlag = 1
          }
        }
      })
    },
    async save() {
      const title = this.form.title.trim()
      if (!title) {
        uni.showToast({ title: '请先填写标题', icon: 'none' })
        return
      }
      try {
        await modifyOrder(this.orderId, {
          title,
          description: this.form.description || null,
          tags: this.form.tags,
          extraTag: this.form.extraTag ? this.form.extraTag.trim() : '',
          timeExpect: this.form.timeExpect,
          urgentFlag: this.form.urgentFlag
        })
        uni.showToast({ title: '已保存修改', icon: 'none' })
        setTimeout(() => {
          this.back()
        }, 800)
      } catch (e) {
        console.error('[微光] 修改失败', e)
        // 仅状态已变化（如刚被接单）时刷新为只读态；校验类失败保留用户输入
        if (e && e.code === 40002) {
          this.load()
        }
      }
    },
    back() {
      uni.navigateBack({
        fail: () => {
          if (this.orderId) {
            uni.redirectTo({ url: `/pages/order/detail?id=${this.orderId}` })
          } else {
            uni.reLaunch({ url: '/pages/index/index' })
          }
        }
      })
    }
  }
}
</script>
