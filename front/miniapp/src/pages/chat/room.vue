<template>
  <view class="wg-page wg-chat-page" :class="userStore.rootClass">
    <view v-if="!wsConnected" class="wg-notice wg-notice-warn">
      <u-icon name="warning" :size="18" />
      <text class="wg-chat-connecting">连接中…</text>
    </view>

    <view
      v-if="careTip && careTip.careTip"
      class="wg-card wg-card-soft"
      style="background-color: #ead9d3; color: #8a5f52"
    >
      <text>称呼：{{ careTip.nickname || '邻居' }}；需要留意：{{ careTip.careTip }}</text>
    </view>

    <view v-if="session && session.emergencyContactPhone" class="wg-card">
      <view>紧急联系人：{{ session.emergencyContactName || '家属' }}</view>
      <view class="wg-btn wg-btn-plain" style="margin-top: 8px" @click="callFamily">
        打电话 {{ session.emergencyContactPhone }}
      </view>
    </view>

    <view v-if="session && (session.addressDetail || session.addressArea)" class="wg-card wg-label">
      <text v-if="session.addressDetail">楼栋门牌：{{ session.addressDetail }}</text>
      <text v-else>地点：{{ session.addressArea }}</text>
    </view>

    <scroll-view v-if="session" scroll-y class="wg-msg-scroll" :scroll-into-view="scrollTarget">
      <view
        v-for="(msg, index) in messages"
        :id="'msg-' + index"
        :key="msg.key"
        class="wg-msg-row"
        :class="{ 'wg-msg-mine': isMine(msg) }"
      >
        <view v-if="msg.messageType === 'SYSTEM'" class="wg-msg-system">{{ msg.content }}</view>
        <view v-else class="wg-msg-bubble" :class="{ 'wg-msg-bubble-mine': isMine(msg), 'wg-msg-bubble-warn': msg.riskFlag === 'HIGHLIGHT', 'wg-msg-bubble-strong': msg.riskFlag === 'STRONG' }">
          <WgImage
            v-if="msg.messageType === 'IMAGE'"
            :src="msg.content"
            mode="widthFix"
            fluid
            class="wg-msg-image"
          />
          <text v-else>{{ msg.content }}</text>
          <text v-if="msg.riskFlag === 'HIGHLIGHT'" class="wg-msg-note">涉及敏感内容，请多留个心眼</text>
          <text v-if="msg.pending" class="wg-msg-note">发送中…</text>
          <text v-else-if="msg.failed" class="wg-msg-note">没发出去，再试试</text>
        </view>
      </view>
    </scroll-view>

    <view v-else class="wg-card">
      <template v-if="loadFailed">
        <text>{{ forbiddenText }}</text>
        <!-- A7：20003（无权访问）时给出可理解的原因，不再是干巴巴的错误提示 -->
        <view class="wg-label" style="margin-top: 6px">常见原因：这次帮助已经结束，或者您暂时不在这次求助里</view>
        <view class="wg-btn wg-btn-plain" style="margin-top: 12px" @click="goBack">返回上一页</view>
      </template>
      <text v-else>{{ loadingText }}</text>
    </view>

    <template v-if="session">
      <!-- B：底部固定区（快捷短语 + 输入）整体加深底色，与可滑动消息区区分 -->
      <view class="wg-chat-bottom">
        <scroll-view scroll-x class="wg-quick" :show-scrollbar="false">
          <view class="wg-quick-inner">
            <view
              v-for="phrase in quickPhrases"
              :key="phrase"
              class="wg-chip wg-quick-chip"
              @click="sendText(phrase)"
            >
              {{ phrase }}
            </view>
          </view>
        </scroll-view>
        <view class="wg-input-row">
          <input class="wg-input" v-model="draft" maxlength="500" placeholder="和邻居说点什么" @input="sendTyping" @confirm="sendText(draft)" />
          <view class="wg-btn wg-btn-plain wg-send-side" @click="pickImage">图片</view>
          <view class="wg-btn wg-send-side" @click="sendText(draft)">发送</view>
        </view>
      </view>
    </template>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { getCareTip, getSession, listMessages, listQuickPhrases, readSession, sendMessage } from '@/api/chat'
import { wsClient } from '@/utils/ws'
import { uploadImage } from '@/utils/upload'
import { MESSAGES } from '@/utils/constants'
import WgImage from '@/components/WgImage.vue'

/* 防诈（详设 §5.3 写死；与后端 FraudPipeline.ALERT_MESSAGE / WsEnvelope.CODE_FRAUD_ALERT 一致） */
const FRAUD_ALERT_TEXT = '为了您的安全，请不要把验证码、银行卡、密码等信息告诉任何人。真正的邻居不会向您索要这些。'
const WS_CODE_FRAUD_ALERT = 49001

export default {
  components: { WgImage },
  data() {
    return {
      orderId: null,
      session: null,
      careTip: null,
      messages: [],
      quickPhrases: [],
      draft: '',
      myUid: null,
      wsConnected: false,
      loadFailed: false,
      scrollTarget: '',
      /** 已弹过强提示的消息 id（本页会话内去重） */
      fraudAlerted: [],
      loadingText: MESSAGES.LOADING,
      forbiddenText: MESSAGES.FORBIDDEN,
      /** B："对方正在输入"标题恢复定时器 */
      typingTimer: null,
      lastTypingSentAt: 0
    }
  },
  computed: {
    userStore() {
      return useUserStore()
    }
  },
  onLoad(options) {
    this.orderId = options.orderId || options.id
    this.wsConnected = wsClient.isConnected()
    this.offStatus = wsClient.onStatus((status) => {
      this.wsConnected = status === 'open'
    })
    this.offMessage = wsClient.onMessage((envelope) => this.onWsMessage(envelope))
    this.load()
  },
  onUnload() {
    if (this.offStatus) {
      this.offStatus()
    }
    if (this.offMessage) {
      this.offMessage()
    }
    if (this.typingTimer) {
      clearTimeout(this.typingTimer)
    }
  },
  methods: {
    async load() {
      try {
        this.session = await getSession(this.orderId)
      } catch (e) {
        console.error('[微光] 会话加载失败', e)
        this.loadFailed = true
        return
      }
      this.myUid = this.session.myUserId
      wsClient.connect()
      this.loadMessages()
      this.markRead()
      listQuickPhrases(this.session.myRole)
        .then((list) => {
          this.quickPhrases = list || []
        })
        .catch(() => {})
      getCareTip(this.orderId)
        .then((tip) => {
          this.careTip = tip
        })
        .catch(() => {
          this.careTip = null
        })
    },
    async loadMessages() {
      try {
        const result = await listMessages(this.session.sessionId, { page: 1, pageSize: 50 })
        const records = (result.records || []).slice().reverse()
        this.messages = records.map((item) => ({
          key: 'm:' + item.msgId,
          msgId: item.msgId,
          senderId: item.senderId,
          messageType: item.messageType,
          content: item.content,
          riskFlag: item.riskFlag,
          createdAt: item.createdAt
        }))
        this.scrollToBottom()
      } catch (e) {
        console.error('[微光] 消息加载失败', e)
      }
    },
    isMine(msg) {
      return msg.senderId !== 0 && msg.senderId === this.myUid
    },
    /** A7：会话不可用时的去向（能返回就返回，否则回首页） */
    goBack() {
      const pages = getCurrentPages()
      if (pages.length > 1) {
        uni.navigateBack()
      } else {
        uni.reLaunch({ url: '/pages/index/index' })
      }
    },
    callFamily() {
      uni.makePhoneCall({
        phoneNumber: this.session.emergencyContactPhone,
        fail: () => {}
      })
    },
    scrollToBottom() {
      this.$nextTick(() => {
        this.scrollTarget = 'msg-' + (this.messages.length - 1)
      })
    },
    markRead() {
      if (this.session) {
        readSession(this.session.sessionId).catch(() => {})
      }
    },
    /** 防诈强提示弹窗（详设 §5.3 / P08）：仅在实时收到复核确认高危的 SYSTEM 推送时弹出；
     * 历史 STRONG 消息只显示红色左条（进页零打扰，见《实现偏差清单》D25） */
    alertFraud(msgId, text) {
      const key = msgId || 'unknown'
      if (this.fraudAlerted.indexOf(key) >= 0) {
        return
      }
      this.fraudAlerted.push(key)
      uni.showModal({
        title: '温馨提示',
        content: text || FRAUD_ALERT_TEXT,
        showCancel: false,
        confirmText: '我知道了'
      })
    },
    onWsMessage(envelope) {
      if (!envelope || !this.session) {
        return
      }
      // B：对方正在输入——直接把导航标题换成提示，3 秒无新帧恢复
      if (envelope.type === 'TYPING') {
        const typingPayload = envelope.payload || {}
        if (typingPayload.sessionId === this.session.sessionId) {
          uni.setNavigationBarTitle({ title: '对方正在输入…' })
          if (this.typingTimer) {
            clearTimeout(this.typingTimer)
          }
          this.typingTimer = setTimeout(() => {
            uni.setNavigationBarTitle({ title: '聊天' })
          }, 3000)
        }
        return
      }
      if (envelope.type === 'SYSTEM') {
        const payload = envelope.payload || {}
        if (payload.code === WS_CODE_FRAUD_ALERT) {
          // 防诈强提示（复核确认高危）：弹窗 + 就地升级气泡为 STRONG（即时红条，无需重进页面）
          if (payload.msgId) {
            this.messages = this.messages.map((m) => (m.msgId === payload.msgId ? { ...m, riskFlag: 'STRONG' } : m))
          }
          this.alertFraud(payload.msgId, payload.message)
          return
        }
        if (envelope.clientMsgId) {
          this.markFailed(envelope.clientMsgId)
        }
        uni.showToast({ title: payload.message || MESSAGES.ERROR, icon: 'none' })
        return
      }
      if (envelope.type !== 'CHAT_MESSAGE') {
        return
      }
      const payload = envelope.payload || {}
      if (payload.sessionId !== this.session.sessionId) {
        return
      }
      const item = {
        key: 'm:' + payload.msgId,
        msgId: payload.msgId,
        clientMsgId: envelope.clientMsgId || undefined,
        senderId: payload.senderId,
        messageType: payload.messageType,
        content: payload.content,
        riskFlag: payload.riskFlag,
        createdAt: payload.createdAt
      }
      if (envelope.clientMsgId && this.messages.some((m) => m.clientMsgId === envelope.clientMsgId && m.pending)) {
        // 自己发出的消息回执：把"发送中"气泡替换为服务端消息
        this.messages = this.messages.map((m) => (m.clientMsgId === envelope.clientMsgId ? item : m))
      } else {
        if (this.messages.some((m) => m.msgId === payload.msgId)) {
          return
        }
        this.messages.push(item)
        if (item.riskFlag === 'STRONG') {
          this.alertFraud(item.msgId)
        }
        if (payload.senderId !== this.myUid) {
          this.markRead()
        }
      }
      this.scrollToBottom()
    },
    /** B：输入时节流上报"正在输入"（2 秒最多一次） */
    sendTyping() {
      if (!this.session) {
        return
      }
      const now = Date.now()
      if (now - this.lastTypingSentAt < 2000) {
        return
      }
      this.lastTypingSentAt = now
      wsClient.send({ type: 'TYPING', payload: { sessionId: this.session.sessionId } })
    },
    newClientMsgId() {
      return 'c' + Date.now() + Math.floor(Math.random() * 100000)
    },
    pushPending(clientMsgId, messageType, content) {
      this.messages.push({
        key: 'c:' + clientMsgId,
        clientMsgId,
        senderId: this.myUid,
        messageType,
        content,
        pending: true
      })
      this.scrollToBottom()
    },
    markFailed(clientMsgId) {
      this.messages = this.messages.map((m) =>
        m.clientMsgId === clientMsgId ? { ...m, pending: false, failed: true } : m
      )
    },
    replacePending(clientMsgId, vo) {
      this.messages = this.messages.map((m) =>
        m.clientMsgId === clientMsgId
          ? {
              key: 'm:' + vo.msgId,
              msgId: vo.msgId,
              senderId: vo.senderId,
              messageType: vo.messageType,
              content: vo.content,
              riskFlag: vo.riskFlag,
              createdAt: vo.createdAt
            }
          : m
      )
      this.scrollToBottom()
    },
    async deliver(messageType, content) {
      const clientMsgId = this.newClientMsgId()
      this.pushPending(clientMsgId, messageType, content)
      const sentByWs = wsClient.send({
        type: 'CHAT_MESSAGE',
        payload: { sessionId: this.session.sessionId, messageType, content },
        clientMsgId
      })
      if (sentByWs) {
        return
      }
      // WS 不可用 → REST 兜底（详设 §3.2 C3）
      try {
        const vo = await sendMessage(this.session.sessionId, { clientMsgId, messageType, content })
        this.replacePending(clientMsgId, vo)
      } catch (e) {
        console.error('[微光] 消息发送失败', e)
        this.markFailed(clientMsgId)
      }
    },
    sendText(text) {
      const content = (text || '').trim()
      if (!content) {
        return
      }
      this.draft = ''
      this.deliver('TEXT', content)
    },
    pickImage() {
      uni.chooseImage({
        count: 1,
        success: async (res) => {
          const filePath = res.tempFilePaths && res.tempFilePaths[0]
          if (!filePath) {
            return
          }
          try {
            const url = await uploadImage(filePath)
            this.deliver('IMAGE', url)
          } catch (e) {
            console.error('[微光] 图片上传失败', e)
          }
        }
      })
    }
  }
}
</script>

<style lang="scss">
/* B：底部固定区整体加深，与可滑动消息区形成区分 */
.wg-chat-bottom {
  background-color: #e9e6df;
  border-radius: 14px 14px 0 0;
  padding: 10px var(--wg-space) var(--wg-space);
}

.wg-chat-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
}

.wg-chat-connecting {
  margin-left: 8px;
  flex: 1;
  font-size: var(--wg-font-sm);
}

.wg-msg-scroll {
  flex: 1;
  min-height: 0;
  margin-bottom: 8px;
}

.wg-msg-row {
  display: flex;
  margin-bottom: 12px;
}

.wg-msg-mine {
  justify-content: flex-end;
}

.wg-msg-system {
  width: 100%;
  text-align: center;
  color: var(--wg-text-weak);
  font-size: var(--wg-font-sm);
}

.wg-msg-bubble {
  max-width: 76%;
  padding: 10px 14px;
  border-radius: var(--wg-radius);
  background-color: #ffffff;
  font-size: var(--wg-font-base);
  word-break: break-all;
}

.wg-msg-bubble-mine {
  background-color: var(--wg-primary);
  color: #ffffff;
}

/* 防诈分级（详设 §5.3 / P08）：用左侧色条标识风险，不改气泡底色与文字色，确保消息清晰可读 */
.wg-msg-bubble-warn {
  border-left: 4px solid var(--sg-sand-d, #8a7648);
}

.wg-msg-bubble-strong {
  border: 1px solid var(--wg-danger);
  border-left: 4px solid var(--wg-danger);
}

.wg-msg-image {
  width: 200px;
  border-radius: var(--wg-radius);
}

.wg-msg-note {
  display: block;
  margin-top: 4px;
  font-size: var(--wg-font-sm);
  opacity: 0.7;
}

.wg-quick {
  white-space: nowrap;
  margin-bottom: 10px;
}

.wg-quick-inner {
  display: inline-flex;
  align-items: center;
}

.wg-quick-chip {
  flex-shrink: 0;
  margin-right: 8px;
}

.wg-input-row {
  display: flex;
  align-items: center;
}

.wg-input-row .wg-input {
  flex: 1;
  min-width: 0;
  margin-right: 8px;
}

.wg-send-side {
  min-width: 64px;
  margin-left: 8px;
}
</style>