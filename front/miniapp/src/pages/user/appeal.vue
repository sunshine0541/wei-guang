<template>
  <view class="wg-page" :class="userStore.rootClass">
    <!-- 适老差异：年长模式两个 Tab 合并为单页顺序表单（详设 §6.2 P17） -->
    <view v-if="!userStore.isElder" class="wg-tabs">
      <view class="wg-tab" :class="{ 'wg-tab-active': tab === 'APPEAL' }" @click="switchTab('APPEAL')">申诉</view>
      <view class="wg-tab" :class="{ 'wg-tab-active': tab === 'FEEDBACK' }" @click="switchTab('FEEDBACK')">反馈</view>
    </view>

    <view v-if="tab === 'APPEAL' || userStore.isElder" class="wg-card">
      <view v-if="userStore.isElder" class="wg-title">申诉</view>
      <view class="wg-label">选择这次求助</view>
      <picker
        v-if="appealableOrders.length > 0"
        mode="selector"
        :range="appealableLabels"
        :value="pickerIndex"
        @change="onOrderChange"
      >
        <view class="wg-input">{{ selectedLabel }}</view>
      </picker>
      <view v-else class="wg-label" style="margin-top: 8px">暂无可申诉的求助</view>

      <view class="wg-label" style="margin-top: 12px">说说发生了什么</view>
      <textarea
        v-model="appealReason"
        class="wg-textarea"
        maxlength="200"
        placeholder="写清楚发生了什么，我们会尽快处理"
      />
      <view class="wg-btn" style="margin-top: 12px" @click="doSubmitAppeal">提交申诉</view>
    </view>

    <view v-if="tab === 'FEEDBACK' || userStore.isElder" class="wg-card">
      <view v-if="userStore.isElder" class="wg-title">反馈</view>
      <view class="wg-label">您的建议或遇到的问题（最多 500 字）</view>
      <textarea
        v-model="feedbackContent"
        class="wg-textarea"
        maxlength="500"
        placeholder="您的每句话，我们都会认真读"
      />
      <view class="wg-btn" style="margin-top: 12px" @click="doSubmitFeedback">提交反馈</view>
    </view>

    <view v-if="listLoading" class="wg-card">
      <text>正在为您准备，请稍等</text>
    </view>
    <template v-else>
      <view v-if="list.length === 0" class="wg-card">
        <text>{{ emptyText }}</text>
      </view>
      <view v-for="item in list" :key="item.id" class="wg-card">
        <view class="wg-card-head">
          <text>{{ item.type === 'FEEDBACK' ? '反馈' : '申诉' }}</text>
          <text class="wg-label">{{ statusLabel(item) }}</text>
        </view>
        <view v-if="item.orderTitle" class="wg-label" style="margin-top: 6px">关联求助：{{ item.orderTitle }}</view>
        <view class="wg-label" style="margin-top: 6px">{{ item.reason }}</view>
        <view v-if="item.conclusion" class="wg-label" style="margin-top: 6px">处理结果：{{ item.conclusion }}</view>
        <view class="wg-label" style="margin-top: 6px">
          {{ item.createdAt }}<template v-if="item.resolvedAt"> · 已处理 {{ item.resolvedAt }}</template>
        </view>
      </view>
    </template>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { listMyOrders } from '@/api/order'
import { getMyAppeals, submitAppeal, submitFeedback } from '@/api/appeal'
import { MESSAGES } from '@/utils/constants'

/* P17 写死文案（详设 §6.2）：空态与提交成功 */
const EMPTY_TEXT = '您还没有提交过申诉或反馈'
const SUBMIT_SUCCESS = '收到啦，我们会尽快处理。'

export default {
  data() {
    return {
      loading: true,
      listLoading: true,
      tab: 'APPEAL',
      // 申诉表单
      appealableOrders: [],
      pickerIndex: 0,
      preselectOrderId: null,
      appealReason: '',
      // 反馈表单
      feedbackContent: '',
      // F3 列表
      list: [],
      emptyText: EMPTY_TEXT
    }
  },
  computed: {
    userStore() {
      return useUserStore()
    },
    appealableLabels() {
      return this.appealableOrders.map((order) => order.title)
    },
    selectedOrder() {
      return this.appealableOrders[this.pickerIndex] || null
    },
    selectedLabel() {
      return this.selectedOrder ? this.selectedOrder.title : '请选择这次求助'
    }
  },
  onLoad(options) {
    this.preselectOrderId = options && options.orderId ? Number(options.orderId) : null
    this.load()
  },
  methods: {
    statusLabel(item) {
      return item.status === 'RESOLVED' ? '已处理' : '正在处理'
    },
    switchTab(tab) {
      if (this.tab === tab) {
        return
      }
      this.tab = tab
      this.loadList()
    },
    onOrderChange(event) {
      this.pickerIndex = Number(event.detail.value)
    },
    async load() {
      this.loading = false
      await Promise.all([this.loadAppealableOrders(), this.loadList()])
    },
    /** 可申诉范围（详设 §2.19）：本人参与的 COMPLETED/CANCELLED 且窗口内、无进行中申诉的求助
     * （数据源=「我发布的」+「我接的单」合并：F1 允许订单双方提交，只读 DONE 会漏掉接单侧的单） */
    async loadAppealableOrders() {
      try {
        const [published, helping, appeals] = await Promise.all([
          listMyOrders({ tab: 'DONE', page: 1, pageSize: 50 }),
          listMyOrders({ tab: 'HELPING', page: 1, pageSize: 50 }),
          getMyAppeals({ tab: 'APPEAL', page: 1, pageSize: 50 })
        ])
        const merged = {}
        for (const order of [...(published.records || []), ...(helping.records || [])]) {
          merged[order.id] = order
        }
        const pendingOrderIds = (appeals.records || [])
          .filter((item) => item.status === 'PENDING' && item.orderId)
          .map((item) => item.orderId)
        const now = Date.now()
        this.appealableOrders = Object.values(merged).filter((order) => {
          const inRange = order.status === 'COMPLETED' || order.status === 'CANCELLED'
          const inWindow = order.appealDeadline && new Date(order.appealDeadline).getTime() >= now
          return inRange && inWindow && pendingOrderIds.indexOf(order.id) < 0
        })
        if (this.preselectOrderId) {
          const index = this.appealableOrders.findIndex((order) => order.id === this.preselectOrderId)
          if (index >= 0) {
            this.pickerIndex = index
          }
        }
      } catch (e) {
        console.error('[微光] 可申诉求助加载失败', e)
      }
    },
    async loadList() {
      this.listLoading = true
      try {
        const result = await getMyAppeals({ tab: this.tab, page: 1, pageSize: 50 })
        this.list = result.records || []
      } catch (e) {
        console.error('[微光] 申诉记录加载失败', e)
        this.list = []
      } finally {
        this.listLoading = false
      }
    },
    async doSubmitAppeal() {
      if (!this.selectedOrder) {
        uni.showToast({ title: '先选一下这次求助', icon: 'none' })
        return
      }
      if (!this.appealReason.trim()) {
        uni.showToast({ title: '写一句原因吧', icon: 'none' })
        return
      }
      try {
        await submitAppeal({
          orderId: this.selectedOrder.id,
          type: 'POST_ORDER',
          reason: this.appealReason.trim()
        })
        uni.showToast({ title: SUBMIT_SUCCESS, icon: 'none' })
        this.appealReason = ''
        this.preselectOrderId = null
        await this.load()
      } catch (e) {
        uni.showToast({ title: (e && e.message) || MESSAGES.ERROR, icon: 'none' })
      }
    },
    async doSubmitFeedback() {
      if (!this.feedbackContent.trim()) {
        uni.showToast({ title: '写一句原因吧', icon: 'none' })
        return
      }
      try {
        await submitFeedback({ content: this.feedbackContent.trim() })
        uni.showToast({ title: SUBMIT_SUCCESS, icon: 'none' })
        this.feedbackContent = ''
        await this.loadList()
      } catch (e) {
        uni.showToast({ title: (e && e.message) || MESSAGES.ERROR, icon: 'none' })
      }
    }
  }
}
</script>