<template>
  <view class="wg-page" :class="userStore.rootClass">
    <!-- 适老差异：年长模式两个 Tab 合并为单页顺序表单（详设 §6.2 P17） -->
    <view v-if="!userStore.isElder" class="wg-seg">
      <view class="wg-seg-item" :class="{ 'wg-seg-item-active': tab === 'APPEAL' }" @click="switchTab('APPEAL')">申诉</view>
      <view class="wg-seg-item" :class="{ 'wg-seg-item-active': tab === 'FEEDBACK' }" @click="switchTab('FEEDBACK')">反馈</view>
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
        <view class="wg-input wg-input-view">{{ selectedLabel }}</view>
      </picker>
      <view v-else>
        <view class="wg-label" style="margin-top: 8px">暂无可申诉的求助</view>
        <!-- B：说明为什么没有可申诉的单 -->
        <view class="wg-label">可申诉期为完成或取消后的 7 天内；这笔求助已有处理中的申诉时，也要等它先处理完。</view>
      </view>

      <view class="wg-label" style="margin-top: 12px">说说发生了什么</view>
      <textarea
        v-model="appealReason"
        class="wg-textarea"
        maxlength="200"
        placeholder="写清楚发生了什么，我们会尽快处理"
      />
      <!-- B：上传凭证（可选，一张） -->
      <view class="wg-label" style="margin-top: 12px">上传凭证（可选，最多一张）</view>
      <view class="wg-evidence">
        <view v-if="evidencePreview" class="wg-evidence-thumb">
          <WgImage
            class="wg-evidence-img"
            :src="evidencePreview"
            :width="96"
            :height="96"
            radius="8px"
            mode="aspectFill"
            @click="previewEvidence"
          />
          <view class="wg-evidence-remove" @click.stop="removeEvidence">×</view>
        </view>
        <view v-else class="wg-evidence-add" @click="pickEvidence">
          <text class="wg-evidence-plus">+</text>
          <text class="wg-evidence-add-tip">选择图片</text>
        </view>
      </view>
      <view v-if="evidencePreview" class="wg-label">点击图片可看大图，右上角 × 可移除</view>
      <view class="wg-btn" style="margin-top: 12px" @click="doSubmitAppeal">提交申诉</view>
    </view>

    <view v-if="tab === 'FEEDBACK' || userStore.isElder" class="wg-card">
      <view v-if="userStore.isElder" class="wg-title">反馈</view>
      <view class="wg-label">您的建议或遇到的问题（最多 500 字）</view>
      <!-- B：问题分类快选，便于后台分流 -->
      <view class="wg-tags">
        <view v-for="c in feedbackCategories" :key="c" class="wg-chip" @click="pickCategory(c)">{{ c }}</view>
      </view>
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
      <view v-if="list.length === 0" class="wg-empty">{{ emptyText }}</view>
      <view v-else class="wg-rows">
        <view v-for="item in list" :key="item.id" class="wg-row wg-appeal-item">
          <view class="wg-appeal-item-head">
            <text class="wg-tag" style="margin: 0">{{ item.type === 'FEEDBACK' ? '反馈' : '申诉' }}</text>
            <text class="wg-label">{{ statusLabel(item) }}</text>
          </view>
          <view v-if="item.orderTitle" class="wg-label">关联求助：{{ item.orderTitle }}</view>
          <view class="wg-label">{{ item.reason }}</view>
          <WgImage
            v-if="item.evidenceUrl"
            class="wg-evidence-img"
            :src="item.evidenceUrl"
            :width="96"
            :height="96"
            radius="8px"
            mode="aspectFill"
            @click="previewImage(item.evidenceUrl)"
          />
          <view v-if="item.conclusion" class="wg-label">处理结果：{{ item.conclusion }}</view>
          <view class="wg-label">
            {{ item.createdAt }}<template v-if="item.resolvedAt"> · 已处理 {{ item.resolvedAt }}</template>
          </view>
          <!-- B：查看处理详情（数据经本地传递，免新增接口） -->
          <view class="wg-btn wg-btn-plain" style="margin-top: 8px" @click.stop="goDetail(item)">
            查看处理详情
          </view>
          <!-- B：处理中的申诉/反馈可撤回 -->
          <view
            v-if="item.status === 'PENDING'"
            class="wg-btn wg-btn-plain"
            style="margin-top: 8px"
            @click.stop="withdraw(item)"
          >
            撤回
          </view>
        </view>
      </view>
    </template>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { listMyOrders } from '@/api/order'
import { getMyAppeals, submitAppeal, submitFeedback, withdrawAppeal } from '@/api/appeal'
import { uploadImage } from '@/utils/upload'
import { MESSAGES, STORAGE_KEYS } from '@/utils/constants'
import { resolveLocalImage } from '@/utils/image'
import WgImage from '@/components/WgImage.vue'

/* P17 写死文案（详设 §6.2）：空态与提交成功 */
const EMPTY_TEXT = '您还没有提交过申诉或反馈'
const SUBMIT_SUCCESS = '收到啦，我们会尽快处理。'

export default {
  components: { WgImage },
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
      /** B：申诉凭证（服务端相对地址 + 本地预览地址） */
      evidenceUrl: '',
      evidencePreview: '',
      // 反馈表单
      feedbackContent: '',
      feedbackCategories: ['用不了', '看不懂', '提建议'],
      // F3 列表
      list: []
    }
  },
  computed: {
    userStore() {
      return useUserStore()
    },
    /** B：空态文案按 Tab 区分（年长模式两 Tab 合并时用合并文案） */
    emptyText() {
      if (this.userStore.isElder) {
        return EMPTY_TEXT
      }
      return this.tab === 'FEEDBACK' ? '您还没有提交过反馈' : '您还没有提交过申诉'
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
      if (item.status === 'WITHDRAWN') {
        return '已撤回'
      }
      return item.status === 'RESOLVED' ? '已处理' : '正在处理'
    },
    goDetail(item) {
      uni.setStorageSync(STORAGE_KEYS.APPEAL_DETAIL, item)
      uni.navigateTo({ url: '/pages/user/appeal-detail' })
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
     * （数据源=「我发布的」+「我接取的求助」合并：F1 允许订单双方提交，只读 DONE 会漏掉接单侧的单） */
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
          reason: this.appealReason.trim(),
          evidenceUrl: this.evidenceUrl || undefined
        })
        uni.showToast({ title: SUBMIT_SUCCESS, icon: 'none' })
        this.appealReason = ''
        this.evidenceUrl = ''
        this.evidencePreview = ''
        this.preselectOrderId = null
        await this.load()
      } catch (e) {
        uni.showToast({ title: (e && e.message) || MESSAGES.ERROR, icon: 'none' })
      }
    },
    /** B：选择并上传凭证图片（复用 /api/upload/image） */
    pickEvidence() {
      uni.chooseImage({
        count: 1,
        success: async (res) => {
          const path = res.tempFilePaths && res.tempFilePaths[0]
          if (!path) {
            return
          }
          uni.showLoading({ title: '上传中' })
          try {
            const url = await uploadImage(path)
            this.evidenceUrl = url
            // 预览先用本地临时路径，避免远端地址在真机上加载失败出现白块
            this.evidencePreview = path
          } catch (e) {
            console.error('[微光] 凭证上传失败', e)
          } finally {
            uni.hideLoading()
          }
        }
      })
    },
    removeEvidence() {
      this.evidenceUrl = ''
      this.evidencePreview = ''
    },
    previewEvidence() {
      if (this.evidencePreview) {
        uni.previewImage({ urls: [this.evidencePreview] })
      }
    },
    async previewImage(url) {
      const src = await resolveLocalImage(url)
      if (src) {
        uni.previewImage({ urls: [src] })
      }
    },
    /** B：分类快选只改开头标记，不覆盖已写正文 */
    pickCategory(category) {
      const prefix = `【${category}】`
      const body = this.feedbackContent.replace(/^【[^】]*】/, '')
      this.feedbackContent = (prefix + body).slice(0, 500)
    },
    /** B：撤回处理中的申诉/反馈 */
    withdraw(item) {
      uni.showModal({
        title: '撤回',
        content: '撤回后这条就不会再被处理了，确定吗？',
        success: async (res) => {
          if (!res.confirm) {
            return
          }
          try {
            await withdrawAppeal(item.id)
            uni.showToast({ title: '已撤回', icon: 'none' })
            await Promise.all([this.loadList(), this.loadAppealableOrders()])
          } catch (e) {
            uni.showToast({ title: (e && e.message) || MESSAGES.ERROR, icon: 'none' })
          }
        }
      })
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

<style lang="scss">
/* B：申诉凭证——单张方形缩略图，未选时为虚线“+”，避免整行按钮突兀 */
.wg-evidence {
  display: flex;
  margin-top: 8px;
}

.wg-evidence-thumb {
  position: relative;
  width: 96px;
  height: 96px;
  flex-shrink: 0;
}

.wg-evidence-img {
  display: block;
  width: 96px;
  height: 96px;
  border-radius: var(--wg-radius-sm);
  background-color: #f7f6f2;
}

.wg-evidence-add {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 96px;
  height: 96px;
  border: 1px dashed var(--wg-line);
  border-radius: var(--wg-radius-sm);
  background-color: var(--wg-surface);
}

.wg-evidence-plus {
  font-size: 26px;
  line-height: 1;
  color: var(--wg-text-weak);
}

.wg-evidence-add-tip {
  margin-top: 4px;
  font-size: var(--wg-font-sm);
  color: var(--wg-text-weak);
}

.wg-evidence-remove {
  position: absolute;
  top: -6px;
  right: -6px;
  width: 22px;
  height: 22px;
  border-radius: 11px;
  background-color: rgba(52, 52, 47, 0.75);
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  line-height: 1;
}

/* 进度列表项：单行 → 纵向块（复用 wg-row 的容器与描边） */
.wg-rows .wg-appeal-item {
  flex-direction: column;
  align-items: stretch;
  justify-content: flex-start;
}

.wg-appeal-item-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
</style>