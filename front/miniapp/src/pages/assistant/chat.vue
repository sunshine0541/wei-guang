<template>
  <view class="wg-page assistant-page" :class="userStore.rootClass">
    <scroll-view class="chat-scroll" scroll-y :scroll-into-view="scrollInto">
      <view class="wg-card chat-intro">
        <text class="intro-title">AI 小助手</text>
        <text class="intro-sub">可以问我怎么发布、接单、取消或申诉，也可以直接说您需要什么帮助。</text>
      </view>

      <view
        v-for="(item, index) in messages"
        :id="'msg-' + index"
        :key="index"
        class="msg-row"
        :class="item.role"
      >
        <view class="bubble">{{ item.text }}</view>
      </view>

      <view v-if="loading" class="msg-row assistant">
        <view class="bubble">正在整理，请稍等...</view>
      </view>

      <view v-if="missing.includes('ADDRESS')" class="wg-card action-card">
        <view class="card-title">{{ addressOptions.length ? '请选择这次要用的地址' : '还差一个常用地址' }}</view>
        <view v-for="item in addressOptions" :key="item.id" class="option" @click="chooseAddress(item)">
          {{ item.label }}
          <text v-if="item.isDefault === 1" class="option-default">默认</text>
        </view>
        <view v-if="!addressOptions.length" class="card-text">
          发布上门/陪同求助前，需要先在「我的地址」添加一个常用地址。
        </view>
        <view v-if="addressOptions.length" class="option option-add" @click="goAddress">去「我的地址」添加</view>
        <view v-else class="wg-btn primary-btn" @click="goAddress">去添加地址</view>
      </view>

      <view v-if="missing.includes('TIME')" class="wg-card action-card">
        <view class="card-title">什么时候需要？</view>
        <view class="chip-row">
          <view v-for="item in timeOptions" :key="item.value" class="wg-chip" @click="chooseTime(item.value)">
            {{ item.label }}
          </view>
        </view>
      </view>

      <view v-if="showDraft && !editing" class="wg-card action-card">
        <view class="card-title">这次求助</view>
        <view class="preview-row"><text class="preview-label">类型</text><text>{{ orderTypeLabel }}</text></view>
        <view class="preview-row"><text class="preview-label">标题</text><text>{{ draft.title || '—' }}</text></view>
        <view class="preview-row"><text class="preview-label">描述</text><text class="preview-value">{{ draft.description || '—' }}</text></view>
        <view class="preview-row"><text class="preview-label">时间</text><text>{{ timeLabel(draft.timeExpect) }}</text></view>
        <view class="preview-row"><text class="preview-label">地址</text><text>{{ addressText }}</text></view>
        <view class="preview-row">
          <text class="preview-label">急单</text>
          <text>{{ draft.urgentFlag === 1 ? '是（会优先展示）' : '否' }}</text>
        </view>
        <view class="chip-row" v-if="draft">
          <view class="wg-chip" :class="{ 'wg-chip-active': draft.urgentFlag === 0 }" @click="chooseUrgent(0)">不急</view>
          <view class="wg-chip" :class="{ 'wg-chip-active': draft.urgentFlag === 1 }" @click="chooseUrgent(1)">着急</view>
        </view>
        <view class="wg-btn wg-btn-ghost" @click="startEdit">编辑内容</view>
      </view>

      <view v-if="editing" class="wg-card action-card edit-card">
        <view class="card-title">修改这次求助</view>
        <view class="edit-label">标题</view>
        <input class="wg-input edit-input" maxlength="20" :value="editForm.title" @input="editForm.title = $event.detail.value" />
        <view class="edit-label">描述</view>
        <textarea class="wg-textarea edit-textarea" maxlength="200" :value="editForm.description" @input="editForm.description = $event.detail.value" />
        <view class="edit-label">类型</view>
        <view class="chip-row">
          <view class="wg-chip" :class="{ 'wg-chip-active': editForm.orderType === 'HOME_VISIT' }" @click="setEditType('HOME_VISIT')">上门</view>
          <view class="wg-chip" :class="{ 'wg-chip-active': editForm.orderType === 'ACCOMPANY' }" @click="setEditType('ACCOMPANY')">陪同外出</view>
          <view class="wg-chip" :class="{ 'wg-chip-active': editForm.orderType === 'REMOTE' }" @click="setEditType('REMOTE')">远程</view>
        </view>
        <view class="edit-label">时间</view>
        <view class="chip-row">
          <view v-for="item in timeOptions" :key="item.value" class="wg-chip" :class="{ 'wg-chip-active': editForm.timeExpect === item.value }" @click="editForm.timeExpect = item.value">
            {{ item.label }}
          </view>
        </view>
        <view class="edit-label">地址</view>
        <view v-if="editForm.orderType === 'REMOTE'" class="card-text">远程求助不需要地址。</view>
        <view v-else>
          <view
            v-for="item in editAddresses"
            :key="item.id"
            class="option"
            :class="{ 'option-active': editForm.addressId === item.id }"
            @click="editForm.addressId = item.id"
          >
            {{ item.label }}
            <text v-if="editForm.addressId === item.id" class="option-default">已选</text>
          </view>
          <view v-if="!editAddresses.length" class="card-text">还没有常用地址，先去添加一个。</view>
          <view class="option option-add" @click="goAddress">去「我的地址」添加/管理</view>
        </view>
        <view class="edit-label">急单</view>
        <view class="chip-row">
          <view class="wg-chip" :class="{ 'wg-chip-active': editForm.urgentFlag === 0 }" @click="editForm.urgentFlag = 0">不急</view>
          <view class="wg-chip" :class="{ 'wg-chip-active': editForm.urgentFlag === 1 }" @click="editForm.urgentFlag = 1">着急</view>
        </view>
        <view class="wg-btn primary-btn" :class="{ 'wg-btn-disabled': editSaving }" @click="saveEdit">
          {{ editSaving ? '正在保存...' : '保存修改' }}
        </view>
        <view class="wg-btn wg-btn-ghost" @click="cancelEdit">取消修改</view>
      </view>

      <view v-if="missing.includes('EMERGENCY_CONTACT')" class="wg-card action-card">
        <view class="card-title">还差一步</view>
        <view class="card-text">上门/陪同类求助需要先填写紧急联系人。</view>
        <view class="wg-btn primary-btn" @click="goProfile">去完善资料</view>
      </view>

      <view v-if="action === 'READY_TO_CONFIRM'" class="wg-card action-card">
        <view class="card-title">确认发布</view>
        <view class="card-text">确认后我会直接帮您发布这条求助。</view>
        <view class="wg-btn primary-btn" :class="{ 'wg-btn-disabled': confirming }" @click="confirmPublish">
          {{ confirming ? '正在发布...' : '确认发布' }}
        </view>
        <view class="wg-btn wg-btn-ghost" @click="resetDraft">重新填写</view>
      </view>

      <view v-if="action === 'ERROR'" class="wg-card action-card">
        <view class="card-text">这次没有发布成功。可以先改一改，或取消重新填写。</view>
        <view class="wg-btn primary-btn" :class="{ 'wg-btn-disabled': confirming }" @click="confirmPublish">重试发布</view>
        <view class="wg-btn wg-btn-ghost" @click="resetDraft">取消</view>
      </view>

      <view v-if="action === 'PUBLISHED'" class="wg-card action-card success-card">
        <view class="card-title">发布成功</view>
        <view class="card-text">已帮您发布这条求助，邻居们可以看到啦。</view>
        <view class="wg-btn primary-btn" @click="openOrder">查看求助</view>
      </view>

      <view v-if="!loading" class="quick-row">
        <view v-for="item in quickQuestions" :key="item" class="wg-chip" @click="quickAsk(item)">
          {{ item }}
        </view>
      </view>
      <view class="scroll-tail" />
    </scroll-view>

    <view class="input-bar">
      <input
        v-model="input"
        class="wg-input chat-input"
        maxlength="500"
        confirm-type="send"
        placeholder="说说您需要什么帮助"
        @confirm="send"
      />
      <view class="wg-btn send-btn" :class="{ 'wg-btn-disabled': loading }" @click="send">发送</view>
    </view>
  </view>
</template>

<script>
import { assistantChat, assistantConfirm, assistantSession, assistantUpdateDraft } from '@/api/assistant'
import { listAddresses } from '@/api/address'
import { useUserStore } from '@/store/user'

const ASSISTANT_STORAGE_KEY = 'wg_assistant_state'
const ASSISTANT_RETENTION_MS = 2 * 60 * 60 * 1000
const WELCOME_MESSAGE = {
  role: 'assistant',
  text: '您好，我是微光 AI 小助手。可以问我怎么发布、接单、取消或申诉，也可以直接说您需要什么帮助，我帮您发布。'
}
const TYPE_LABELS = { HOME_VISIT: '上门', ACCOMPANY: '陪同外出', REMOTE: '远程' }
const TIME_LABELS = {
  ASAP: '现在 / 尽快',
  TODAY: '今天',
  TOMORROW: '明天',
  THIS_WEEK: '本周',
  ANY: '都行'
}

export default {
  data() {
    return {
      sessionId: '',
      ownerId: null,
      messages: [{ ...WELCOME_MESSAGE }],
      input: '',
      loading: false,
      confirming: false,
      action: 'NONE',
      intent: '',
      draft: null,
      missing: [],
      addressOptions: [],
      confirmToken: '',
      orderId: null,
      scrollInto: '',
      editing: false,
      editSaving: false,
      editAddresses: [],
      editForm: {
        title: '',
        description: '',
        orderType: '',
        timeExpect: '',
        addressId: null,
        urgentFlag: 0
      },
      quickQuestions: ['怎么发布求助？', '怎么接单？', '怎么取消求助？'],
      timeOptions: [
        { label: '现在', value: 'ASAP' },
        { label: '今天', value: 'TODAY' },
        { label: '明天', value: 'TOMORROW' },
        { label: '本周', value: 'THIS_WEEK' },
        { label: '都行', value: 'ANY' }
      ]
    }
  },
  onLoad() {
    const userStore = useUserStore()
    this.ownerId = userStore.userId || null
    this.restoreLocal()
  },
  onShow() {
    if (!this.sessionId) return
    if (this.missing.includes('ADDRESS')) {
      this.refreshAddressOptions()
    }
    if (this.missing.includes('EMERGENCY_CONTACT')) {
      this.validateSession()
    }
    if (this.editing) {
      this.refreshEditAddresses()
    }
  },
  onHide() {
    this.persist()
  },
  onUnload() {
    this.persist()
  },
  computed: {
    userStore() {
      return useUserStore()
    },
    orderTypeLabel() {
      return this.draft ? TYPE_LABELS[this.draft.orderType] || this.draft.orderType || '—' : '—'
    },
    addressText() {
      if (!this.draft) return '—'
      if (this.draft.addressLabel) return this.draft.addressLabel
      if (this.draft.orderType === 'REMOTE') return '远程不需要地址'
      return '待选择地址'
    },
    showDraft() {
      return !!this.draft && ['COLLECTING', 'READY_TO_CONFIRM', 'NEED_ADDRESS', 'NEED_PROFILE', 'ERROR'].includes(this.action)
    }
  },
  methods: {
    timeLabel(value) {
      return TIME_LABELS[value] || '待确认'
    },
    send() {
      const text = (this.input || '').trim()
      if (!text) return
      this.input = ''
      this.callChat({ message: text })
    },
    quickAsk(text) {
      this.callChat({ message: text })
    },
    chooseAddress(item) {
      this.addressOptions = []
      this.callChat({ message: '就用这个地址，继续发布', addressId: item.id })
    },
    chooseTime(value) {
      this.callChat({ message: '时间选好了，继续发布', timeExpect: value })
    },
    chooseUrgent(value) {
      this.callChat({ message: value === 1 ? '要标急' : '不急，正常发布', urgentFlag: value })
    },
    async resetDraft() {
      this.clearLocal()
      await this.callChat({ message: '取消' })
      this.sessionId = ''
      this.clearLocal()
    },
    startEdit() {
      if (!this.draft) return
      this.editForm = {
        title: this.draft.title || '',
        description: this.draft.description || '',
        orderType: this.draft.orderType || '',
        timeExpect: this.draft.timeExpect || '',
        addressId: this.draft.addressId || null,
        urgentFlag: this.draft.urgentFlag === 1 ? 1 : 0
      }
      this.editing = true
      this.refreshEditAddresses()
    },
    cancelEdit() {
      this.editing = false
    },
    setEditType(type) {
      this.editForm.orderType = type
      if (type === 'REMOTE') {
        this.editForm.addressId = null
      }
    },
    async saveEdit() {
      if (this.editSaving || !this.sessionId) return
      if (!this.editForm.orderType) {
        this.pushMessage('assistant', '请先选择求助类型。')
        return
      }
      this.editSaving = true
      try {
        const data = await assistantUpdateDraft({
          sessionId: this.sessionId,
          title: this.editForm.title,
          description: this.editForm.description,
          orderType: this.editForm.orderType,
          timeExpect: this.editForm.timeExpect,
          addressId: this.editForm.addressId,
          urgentFlag: this.editForm.urgentFlag
        })
        this.applyResponse(data)
        this.editing = false
      } catch (e) {
        this.pushMessage('assistant', '修改没有保存成功，请再试一次。')
      } finally {
        this.editSaving = false
        this.scrollToBottom()
      }
    },
    async refreshAddressOptions() {
      try {
        this.addressOptions = (await listAddresses()) || []
      } catch (e) {
        // 地址接口失败时保留原选项
      }
    },
    async refreshEditAddresses() {
      try {
        this.editAddresses = (await listAddresses()) || []
      } catch (e) {
        // 编辑表单仍可修改文字字段
      }
    },
    async validateSession() {
      if (!this.sessionId) return
      try {
        const data = await assistantSession(this.sessionId)
        if (!data || data.action === 'EXPIRED') {
          this.clearLocal()
          this.resetToWelcome()
          this.pushMessage('assistant', '上次对话已过期，我们重新开始吧。')
          return
        }
        this.applyResponse(data)
      } catch (e) {
        // 网络异常时保留本地记录，不打断用户
      }
    },
    restoreLocal() {
      const saved = uni.getStorageSync(ASSISTANT_STORAGE_KEY)
      if (!saved || typeof saved !== 'object') return
      if (saved.userId && this.ownerId && saved.userId !== this.ownerId) {
        this.clearLocal()
        return
      }
      if (!saved.updatedAt || Date.now() - saved.updatedAt > ASSISTANT_RETENTION_MS) {
        this.clearLocal()
        return
      }
      this.sessionId = saved.sessionId || ''
      if (Array.isArray(saved.messages) && saved.messages.length) {
        this.messages = saved.messages
      }
      this.action = saved.action || 'NONE'
      this.draft = saved.draft || null
      this.missing = saved.missing || []
      this.addressOptions = saved.addressOptions || []
      this.confirmToken = saved.confirmToken || ''
      this.orderId = saved.orderId || null
      // 只有存在草稿时才需要校验服务端会话；纯问答记录留在本地即可
      if (this.sessionId && this.draft) {
        this.validateSession()
      }
    },
    persist() {
      if (!this.sessionId) return
      if (this.action === 'PUBLISHED') {
        this.clearLocal()
        return
      }
      uni.setStorageSync(ASSISTANT_STORAGE_KEY, {
        userId: this.ownerId,
        sessionId: this.sessionId,
        messages: this.messages.slice(-100),
        action: this.action,
        draft: this.draft,
        missing: this.missing,
        addressOptions: this.addressOptions,
        confirmToken: this.confirmToken,
        orderId: this.orderId,
        updatedAt: Date.now()
      })
    },
    clearLocal() {
      uni.removeStorageSync(ASSISTANT_STORAGE_KEY)
    },
    resetToWelcome() {
      this.sessionId = ''
      this.action = 'NONE'
      this.draft = null
      this.missing = []
      this.addressOptions = []
      this.confirmToken = ''
      this.orderId = null
      this.editing = false
      this.messages = [{ ...WELCOME_MESSAGE }]
    },
    async callChat(payload) {
      if (this.loading) return
      if (payload.message && payload.message !== '取消') {
        this.pushMessage('user', payload.message)
      }
      this.loading = true
      try {
        const data = await assistantChat({
          sessionId: this.sessionId || undefined,
          message: payload.message,
          addressId: payload.addressId,
          timeExpect: payload.timeExpect,
          urgentFlag: payload.urgentFlag
        })
        this.applyResponse(data)
      } catch (e) {
        this.pushMessage('assistant', '刚才没处理成功，请稍后再试一次。')
      } finally {
        this.loading = false
        this.scrollToBottom()
      }
    },
    async confirmPublish() {
      if (this.confirming || !this.confirmToken) return
      this.confirming = true
      try {
        const data = await assistantConfirm({ sessionId: this.sessionId, confirmToken: this.confirmToken })
        this.applyResponse(data)
      } catch (e) {
        this.pushMessage('assistant', '发布没有成功，请稍后再试。')
      } finally {
        this.confirming = false
        this.scrollToBottom()
      }
    },
    applyResponse(data) {
      if (!data) return
      this.sessionId = data.sessionId || this.sessionId
      this.intent = data.intent || ''
      this.action = data.action || 'NONE'
      this.draft = data.draft || (this.action === 'PUBLISHED' ? this.draft : null)
      this.missing = data.missing || []
      this.addressOptions = data.addressOptions || []
      this.confirmToken = data.confirmToken || ''
      this.orderId = data.orderId || null
      if (data.reply) {
        this.pushMessage('assistant', data.reply)
      }
      if (this.action === 'PUBLISHED') {
        this.clearLocal()
      } else {
        this.persist()
      }
    },
    pushMessage(role, text) {
      this.messages.push({ role, text })
      this.persist()
      this.scrollToBottom()
    },
    scrollToBottom() {
      this.$nextTick(() => {
        this.scrollInto = 'msg-' + (this.messages.length - 1)
      })
    },
    goProfile() {
      uni.navigateTo({ url: '/pages/user/profile' })
    },
    goAddress() {
      uni.navigateTo({ url: '/pages/user/address' })
    },
    openOrder() {
      if (this.orderId) {
        uni.navigateTo({ url: `/pages/order/detail?id=${this.orderId}` })
      }
    }
  }
}
</script>

<style scoped>
.assistant-page {
  padding-bottom: 190rpx;
}
.chat-scroll {
  height: calc(100vh - 210rpx);
}
.intro-title {
  display: block;
  font-size: var(--wg-font-title);
  font-weight: 800;
  line-height: 1.35;
  color: var(--wg-text);
}
.intro-sub {
  display: block;
  margin-top: 4px;
  font-size: var(--wg-font-sm);
  line-height: 1.6;
  color: var(--wg-text-weak);
}
.msg-row {
  display: flex;
  margin-bottom: 12px;
}
.msg-row.user {
  justify-content: flex-end;
}
.bubble {
  max-width: 78%;
  padding: 10px 14px;
  border-radius: var(--wg-radius);
  font-size: var(--wg-font-base);
  line-height: 1.6;
  color: var(--wg-text);
  background-color: var(--wg-surface);
  word-break: break-all;
}
.msg-row.user .bubble {
  background-color: var(--wg-primary);
  color: #ffffff;
}
.card-title {
  font-size: var(--wg-font-lg);
  font-weight: 800;
  line-height: 1.4;
  color: var(--wg-text);
}
.card-text {
  margin-top: 8px;
  font-size: var(--wg-font-sm);
  line-height: 1.6;
  color: var(--wg-text-weak);
}
.edit-label {
  margin-top: 12px;
  font-size: var(--wg-font-sm);
  color: var(--wg-text-weak);
}
.edit-input,
.edit-textarea {
  margin-top: 8px;
}
.preview-row {
  display: flex;
  margin-top: 10px;
  font-size: var(--wg-font-base);
  line-height: 1.55;
  color: var(--wg-text);
}
.preview-label {
  width: 48px;
  flex-shrink: 0;
  font-size: var(--wg-font-sm);
  line-height: 1.75;
  color: var(--wg-text-weak);
}
.preview-value {
  word-break: break-all;
}
.chip-row {
  display: flex;
  flex-wrap: wrap;
  margin-top: 8px;
}
.option {
  margin-top: 8px;
  padding: 10px 12px;
  border-radius: var(--wg-radius-sm);
  font-size: var(--wg-font-base);
  color: var(--wg-text);
  background-color: var(--wg-surface);
  border: var(--wg-border);
}
.option-active {
  background-color: var(--wg-primary-light);
  border-color: var(--wg-primary);
  color: var(--wg-primary-deep);
  font-weight: 700;
}
.option-default {
  margin-left: 8px;
  font-size: var(--wg-font-sm);
  color: var(--wg-text-weak);
}
.option-add {
  color: var(--wg-primary-deep);
}
.primary-btn {
  margin-top: 12px;
}
.success-card {
  border-color: var(--wg-primary);
}
.quick-row {
  display: flex;
  flex-wrap: wrap;
  margin-top: 2px;
}
.scroll-tail {
  height: 12px;
}
.input-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  padding: 10px var(--wg-gap) calc(10px + env(safe-area-inset-bottom));
  background-color: var(--wg-bg);
  border-top: 1px solid var(--wg-line);
}
.chat-input {
  flex: 1;
  min-width: 0;
  margin-top: 0;
  margin-right: 8px;
}
.send-btn {
  min-width: 64px;
  padding: 0 16px;
}
</style>
