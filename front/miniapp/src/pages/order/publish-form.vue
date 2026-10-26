<template>
  <view class="wg-page" :class="userStore.rootClass">
    <!-- A1 第 3 步：填写表单并发布 -->
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
          <view class="wg-input wg-input-view">{{ currentAddressLabel || '请选择常用地址' }}</view>
        </picker>
        <view v-else>
          <view class="wg-label">您还没有常用地址，先添加一个吧</view>
          <input class="wg-input" v-model="newAddress.label" maxlength="20" placeholder="标签，如：家" />
          <AddressAreaInput :value="newAddress.areaText" placeholder="小区/区域" @input="onNewAreaInput" @select="onNewGeoSelect" />
          <view v-if="newAddress.latitude !== null && newAddress.longitude !== null" class="wg-label">
            已记录地图位置，可用于计算距离
          </view>
          <input class="wg-input" v-model="newAddress.detailText" maxlength="100" placeholder="楼栋门牌（选填）" />
          <view class="wg-btn wg-btn-plain" style="margin-top: 12px" @click="saveNewAddress">保存这个地址</view>
        </view>
        <!-- 4D-补3：选中的地址没有坐标时提醒，避免发布后看不到距离 -->
        <view v-if="selectedAddress && !selectedAddressHasGeo" class="wg-label">
          这个地址还没有地图位置，发布后邻居看不到距离，可去「我的地址」补全
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

      <!-- B：受控词表之外的自定义标签（仅展示、不入推荐） -->
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

    <view class="wg-btn" @click="submit">发布求助</view>
    <view class="wg-btn wg-btn-plain" style="margin-top: 12px" @click="backToEntry">重新选择</view>

    <!-- 底部常驻紧急提示条 -->
    <view class="wg-notice wg-notice-warn">
      <u-icon name="warning" :size="18" />
      <text class="wg-emergency-text">{{ emergencyTip }}</text>
    </view>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { publishOrder } from '@/api/order'
import { createAddress, listAddresses } from '@/api/address'
import { getProfile } from '@/api/user'
import { EMERGENCY_TIP, HELP_TAGS, ORDER_TYPE_LABELS, STORAGE_KEYS, TIME_EXPECT_LABELS, TIME_EXPECT_OPTIONS } from '@/utils/constants'
import AddressAreaInput from '@/components/AddressAreaInput.vue'

const URGENT_CONFIRM = '勾选"急"请确认情况真的紧急。紧急情况请直接拨打120/110。滥用"急"会影响您的微光值。'
const CARE_NOTICE = '夜深了，不急的话，明早再找邻居；如果很急，请直接拨打120/110'

export default {
  components: { AddressAreaInput },
  data() {
    return {
      entry: 'template',
      aiResult: null,
      addresses: [],
      /** 4D-补：内联新增地址带坐标，保证发布后能算距离 */
      newAddress: { label: '', areaText: '', detailText: '', latitude: null, longitude: null },
      tagOptions: HELP_TAGS,
      timeOptions: TIME_EXPECT_OPTIONS,
      emergencyTip: EMERGENCY_TIP,
      /** 紧急联系人（线下类求助发布前置校验） */
      emergencyName: '',
      emergencyPhone: '',
      /** 档案是否成功加载：false 时放行给后端校验，避免网络失败误拦 */
      profileLoaded: false,
      typeOptions: [
        { label: '上门', value: 'HOME_VISIT' },
        { label: '外出', value: 'ACCOMPANY' },
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
        extraTag: '',
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
    },
    /** 4D-补3：当前选中的地址，用于提示“没地图位置” */
    selectedAddress() {
      return this.addresses.find((item) => item.id === this.form.addressId) || null
    },
    selectedAddressHasGeo() {
      const address = this.selectedAddress
      return !!(address
        && address.latitude !== null && address.latitude !== undefined
        && address.longitude !== null && address.longitude !== undefined)
    }
  },
  onLoad() {
    this.loadAddresses()
    // A1：读取上一页写入的跨页草稿并预填
    const draft = uni.getStorageSync(STORAGE_KEYS.PUBLISH_DRAFT)
    if (draft && draft.form) {
      this.entry = draft.entry || 'template'
      this.aiResult = draft.aiResult || null
      this.form = { ...this.form, ...draft.form }
    }
  },
  onShow() {
    // 从「我的资料」返回后刷新紧急联系人，判断线下类求助能否发布
    this.loadEmergencyContact()
  },
  methods: {
    async loadEmergencyContact() {
      try {
        const profile = await getProfile()
        this.emergencyName = profile.emergencyContactName || ''
        this.emergencyPhone = profile.emergencyContactPhone || ''
        this.profileLoaded = true
      } catch (e) {
        // 加载失败不阻塞填写，发布时由后端兜底校验
        this.profileLoaded = false
      }
    },
    promptEmergencyContact() {
      uni.showModal({
        title: '还差一步',
        content: '上门/外出类求助需要先填写紧急联系人的姓名和电话，方便服务过程中有突发情况时联系您。',
        confirmText: '去填写',
        cancelText: '稍后',
        success: (res) => {
          if (res.confirm) {
            uni.navigateTo({ url: '/pages/user/profile' })
          }
        }
      })
    },
    typeLabel(type) {
      return ORDER_TYPE_LABELS[type] || type
    },
    timeLabel(expect) {
      return TIME_EXPECT_LABELS[expect] || expect
    },
    async loadAddresses() {
      this.addresses = await listAddresses()
      // 4D-补：未选地址时自动预选默认地址（草稿里已选的不覆盖）
      if (!this.form.addressId && this.addresses.length > 0) {
        const def = this.addresses.find((item) => item.isDefault === 1) || this.addresses[0]
        this.form.addressId = def.id
      }
    },
    /** 4D-补3：手动改动小区后旧坐标失效 */
    onNewAreaInput(value) {
      this.newAddress.areaText = value
      this.newAddress.latitude = null
      this.newAddress.longitude = null
    },
    onNewGeoSelect(poi) {
      this.newAddress.areaText = poi.name || poi.address || ''
      this.newAddress.latitude = poi.latitude
      this.newAddress.longitude = poi.longitude
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
        latitude: this.newAddress.latitude,
        longitude: this.newAddress.longitude,
        isDefault: this.addresses.length === 0 ? 1 : 0
      })
      await this.loadAddresses()
      this.form.addressId = created.id
      this.newAddress = { label: '', areaText: '', detailText: '', latitude: null, longitude: null }
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
    /** A1：回到上一步（模板页）——自由描述进入时需退两层 */
    backToEntry() {
      const delta = this.entry === 'free' ? 2 : 1
      uni.navigateBack({
        delta,
        fail: () => {
          uni.reLaunch({ url: '/pages/order/publish' })
        }
      })
    },
    submit() {
      if (this.form.orderType !== 'REMOTE' && this.profileLoaded
          && (!this.emergencyName || !this.emergencyPhone)) {
        this.promptEmergencyContact()
        return
      }
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
        uni.showToast({ title: '上门/外出类需要先选一个常用地址', icon: 'none' })
        return
      }
      const payload = {
        source: this.form.source,
        orderType: this.form.orderType,
        urgentFlag: this.form.urgentFlag,
        title: this.form.title,
        description: this.form.description || null,
        tags: this.form.tags,
        timeExpect: this.form.timeExpect,
        extraTag: this.form.extraTag ? this.form.extraTag.trim() : null
      }
      if (this.form.orderType !== 'REMOTE') {
        payload.addressId = this.form.addressId
      }
      if (this.form.source === 'REPEAT') {
        payload.repeatFromOrderId = this.form.repeatFromOrderId
      }
      try {
        const result = await publishOrder(payload)
        // 发布成功 → 清掉跨页草稿，避免下次进入表单页带回旧内容
        uni.removeStorageSync(STORAGE_KEYS.PUBLISH_DRAFT)
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
.wg-emergency-text {
  margin-left: 8px;
  flex: 1;
}
</style>
