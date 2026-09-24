<template>
  <view class="wg-page" :class="userStore.rootClass">
    <!-- 三入口 -->
    <template v-if="entry === ''">
      <view class="wg-card">
        <text class="wg-title">想请邻居帮什么忙？</text>
        <text class="wg-label">选一个模板，或者用自己的话说说</text>
      </view>
      <view class="wg-grid">
        <view v-for="tpl in templates" :key="tpl.id" class="wg-grid-item" @click="chooseTemplate(tpl)">
          <text class="wg-grid-icon">{{ tpl.icon }}</text>
          <text>{{ tpl.name }}</text>
          <text class="wg-label">{{ tpl.coverDesc }}</text>
        </view>
      </view>
      <view class="wg-btn" @click="startFree">自由描述（智能小助手帮我整理）</view>
      <view class="wg-btn wg-btn-plain" style="margin-top: 12px" @click="loadRepeatOrders">再来一次（选一条历史求助）</view>
      <view v-if="repeatOrders.length > 0" class="wg-card" style="margin-top: 12px">
        <view v-for="order in repeatOrders" :key="order.id" class="wg-repeat-item" @click="chooseRepeat(order)">
          {{ order.title }}（{{ order.createdAt }}）
        </view>
      </view>
    </template>

    <!-- 自由描述 + AI 建议 -->
    <view v-if="entry === 'free' && !aiResult" class="wg-card">
      <text class="wg-title">用一句话说说您的需要</text>
      <textarea class="wg-textarea" v-model="freeText" placeholder="例如：帮我去药店取一下降压药" maxlength="500" />
      <view class="wg-btn" @click="doAiSuggest">{{ aiLoading ? '智能小助手正在整理…' : '让小助手帮我整理' }}</view>
      <view class="wg-btn wg-btn-plain" style="margin-top: 12px" @click="entry = ''">返回</view>
    </view>

    <!-- 发布表单 -->
    <template v-if="entry !== '' && (entry !== 'free' || aiResult)">
      <view v-if="aiResult" class="wg-card">
        <text>小助手建议：{{ typeLabel(form.orderType) }} · {{ form.tags.join('、') || '暂无标签' }} · {{ timeLabel(form.timeExpect) }}</text>
        <text v-if="aiResult.confidence < 0.5" class="wg-label">我猜的，您看看对不对</text>
        <text v-if="aiResult.degraded" class="wg-label">智能小助手在休息，我先用简单办法帮您整理</text>
      </view>

      <view class="wg-card">
        <view class="wg-label">这是哪一类帮助</view>
        <view class="wg-tags">
          <view
            v-for="item in typeOptions"
            :key="item.value"
            class="wg-chip"
            :class="{ 'wg-chip-active': form.orderType === item.value }"
            @click="form.orderType = item.value"
          >
            {{ item.label }}
          </view>
        </view>

        <view class="wg-label" style="margin-top: 12px">标题（不超过 20 字）</view>
        <input class="wg-input" v-model="form.title" maxlength="20" placeholder="一句话说明白" />

        <view class="wg-label" style="margin-top: 12px">再补充两句（可选，不超过 200 字）</view>
        <textarea class="wg-textarea" v-model="form.description" maxlength="200" />

        <view class="wg-label" style="margin-top: 12px">地址</view>
        <view v-if="form.orderType === 'REMOTE'" class="wg-label">远程帮助全程线上完成，不需要地址</view>
        <template v-else>
          <picker v-if="addresses.length > 0" mode="selector" :range="addressLabels" @change="onAddressChange">
            <view class="wg-input">{{ currentAddressLabel || '请选择常用地址' }}</view>
          </picker>
          <view v-else>
            <view class="wg-label">您还没有常用地址，先添加一个吧</view>
            <input class="wg-input" v-model="newAddress.label" maxlength="20" placeholder="标签，如：家" />
            <input class="wg-input" v-model="newAddress.areaText" maxlength="100" placeholder="小区/区域" />
            <input class="wg-input" v-model="newAddress.detailText" maxlength="100" placeholder="楼栋门牌（选填）" />
            <view class="wg-btn wg-btn-plain" style="margin-top: 12px" @click="saveNewAddress">保存这个地址</view>
          </view>
          <view class="wg-label">上门类在您点"确认开始"后，才会把楼栋门牌给接单的邻居看</view>
        </template>

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

      <view class="wg-btn" @click="submit">发布求助</view>
      <view class="wg-btn wg-btn-plain" style="margin-top: 12px" @click="backToEntry">重新选择</view>
    </template>

    <!-- 底部常驻紧急提示条 -->
    <view class="wg-emergency">{{ emergencyTip }}</view>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { aiSuggest, listMyOrders, listTemplates, publishOrder } from '@/api/order'
import { listAddresses, createAddress } from '@/api/address'
import { EMERGENCY_TIP, HELP_TAGS, ORDER_TYPE_LABELS, TIME_EXPECT_LABELS, TIME_EXPECT_OPTIONS } from '@/utils/constants'

const URGENT_CONFIRM = '勾选"急"请确认情况真的紧急。紧急情况请直接拨打120/110。滥用"急"会影响您的微光值。'
const CARE_NOTICE = '夜深了，不急的话，明早再找邻居；如果很急，请直接拨打120/110'

export default {
  data() {
    return {
      entry: '',
      templates: [],
      freeText: '',
      aiResult: null,
      aiLoading: false,
      addresses: [],
      newAddress: { label: '', areaText: '', detailText: '' },
      repeatOrders: [],
      tagOptions: HELP_TAGS,
      timeOptions: TIME_EXPECT_OPTIONS,
      emergencyTip: EMERGENCY_TIP,
      typeOptions: [
        { label: '上门', value: 'HOME_VISIT' },
        { label: '陪同外出', value: 'ACCOMPANY' },
        { label: '远程', value: 'REMOTE' }
      ],
      form: {
        orderType: 'REMOTE',
        title: '',
        description: '',
        addressId: null,
        tags: [],
        timeExpect: 'ANY',
        urgentFlag: 0,
        source: 'FREE',
        repeatFromOrderId: null
      }
    }
  },
  computed: {
    userStore() {
      return useUserStore()
    },
    addressLabels() {
      return this.addresses.map((item) => `${item.label}（${item.areaText}）`)
    },
    currentAddressLabel() {
      const found = this.addresses.find((item) => item.id === this.form.addressId)
      return found ? `${found.label}（${found.areaText}）` : ''
    }
  },
  onLoad() {
    this.loadTemplates()
    this.loadAddresses()
  },
  methods: {
    typeLabel(type) {
      return ORDER_TYPE_LABELS[type] || type
    },
    timeLabel(expect) {
      return TIME_EXPECT_LABELS[expect] || expect
    },
    async loadTemplates() {
      this.templates = await listTemplates()
    },
    async loadAddresses() {
      this.addresses = await listAddresses()
    },
    async loadRepeatOrders() {
      const result = await listMyOrders({ tab: 'DONE' })
      this.repeatOrders = (result.records || []).filter((item) => item.status === 'EXPIRED')
      if (this.repeatOrders.length === 0) {
        uni.showToast({ title: '还没有可以重发的求助', icon: 'none' })
      }
    },
    chooseTemplate(tpl) {
      this.entry = 'template'
      this.aiResult = null
      this.form.source = 'TEMPLATE'
      this.form.orderType = tpl.defaultType
      this.form.title = tpl.title
      this.form.tags = []
    },
    startFree() {
      this.entry = 'free'
      this.aiResult = null
      this.form.source = 'FREE'
    },
    async doAiSuggest() {
      if (!this.freeText.trim()) {
        uni.showToast({ title: '先写一句话吧', icon: 'none' })
        return
      }
      this.aiLoading = true
      try {
        const result = await aiSuggest(this.freeText.trim())
        this.aiResult = result
        this.form.orderType = result.orderType
        this.form.title = result.title
        this.form.tags = result.tags || []
        this.form.description = this.freeText.trim()
        this.form.timeExpect = result.urgencySuggest === 'URGENT' ? 'ASAP' : 'ANY'
      } finally {
        this.aiLoading = false
      }
    },
    chooseRepeat(order) {
      this.entry = 'repeat'
      this.aiResult = null
      this.form.source = 'REPEAT'
      this.form.repeatFromOrderId = order.id
      this.form.orderType = order.orderType
      this.form.title = order.title
      this.form.tags = (order.tags || []).slice(0, 3)
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
    onAddressChange(event) {
      const index = Number(event.detail.value)
      this.form.addressId = this.addresses[index] ? this.addresses[index].id : null
    },
    async saveNewAddress() {
      if (!this.newAddress.areaText || !this.newAddress.areaText.trim()) {
        uni.showToast({ title: '请填写小区/区域', icon: 'none' })
        return
      }
      const created = await createAddress({
        label: this.newAddress.label || '家',
        areaText: this.newAddress.areaText,
        detailText: this.newAddress.detailText || null,
        isDefault: this.addresses.length === 0 ? 1 : 0
      })
      await this.loadAddresses()
      this.form.addressId = created.id
      this.newAddress = { label: '', areaText: '', detailText: '' }
      uni.showToast({ title: '地址已保存', icon: 'none' })
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
    backToEntry() {
      this.entry = ''
      this.aiResult = null
    },
    submit() {
      uni.showModal({
        title: '确认发布',
        content: `确定发布"${this.form.title}"吗？`,
        success: (res) => {
          if (res.confirm) {
            this.doPublish()
          }
        }
      })
    },
    async doPublish() {
      if (!this.form.title || !this.form.title.trim()) {
        uni.showToast({ title: '请先填写标题', icon: 'none' })
        return
      }
      if (this.form.orderType !== 'REMOTE' && !this.form.addressId) {
        uni.showToast({ title: '上门/陪同类需要先选一个常用地址', icon: 'none' })
        return
      }
      const payload = {
        source: this.form.source,
        orderType: this.form.orderType,
        urgentFlag: this.form.urgentFlag,
        title: this.form.title,
        description: this.form.description || null,
        tags: this.form.tags,
        timeExpect: this.form.timeExpect
      }
      if (this.form.orderType !== 'REMOTE') {
        payload.addressId = this.form.addressId
      }
      if (this.form.source === 'REPEAT') {
        payload.repeatFromOrderId = this.form.repeatFromOrderId
      }
      try {
        const result = await publishOrder(payload)
        uni.showToast({ title: '已发布，邻居们很快就能看到了。', icon: 'none' })
        if (result.careNotice) {
          setTimeout(() => {
            uni.showModal({ title: '夜深了', content: CARE_NOTICE, showCancel: false, confirmText: '我知道了' })
          }, 1200)
        }
        setTimeout(() => {
          uni.redirectTo({ url: '/pages/index/index' })
        }, result.careNotice ? 3000 : 1200)
      } catch (e) {
        console.error('[微光] 发布失败', e)
      }
    }
  }
}
</script>

<style lang="scss">
.wg-grid {
  display: flex;
  flex-wrap: wrap;
}

.wg-grid-item {
  width: 46%;
  margin: 0 2% 12px 2%;
  padding: var(--wg-space);
  background-color: #ffffff;
  border-radius: var(--wg-radius);
  display: flex;
  flex-direction: column;
  align-items: center;
  font-size: var(--wg-font-base);
}

.wg-grid-icon {
  font-size: 34px;
  margin-bottom: 6px;
}

.wg-repeat-item {
  padding: 12px 0;
  border-bottom: 1px solid #f0e9dd;
  font-size: var(--wg-font-base);
}

.wg-emergency {
  margin-top: var(--wg-gap);
  padding: 12px;
  text-align: center;
  color: #b06a00;
  background-color: var(--wg-primary-light);
  border-radius: var(--wg-radius);
  font-size: var(--wg-font-base);
}
</style>
