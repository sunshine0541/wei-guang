<template>
  <view class="wg-page" :class="userStore.rootClass">
    <view v-if="loading" class="wg-card">
      <text>正在为您准备，请稍等</text>
    </view>

    <template v-else>
      <view v-if="addresses.length === 0" class="wg-card">
        <text>还没有常用地址，添加一个"家"吧</text>
      </view>

      <view v-for="item in addresses" :key="item.id" class="wg-card">
        <view class="wg-card-head">
          <text class="wg-title">{{ item.label }}</text>
          <text v-if="item.isDefault === 1" class="wg-tag">默认</text>
        </view>
        <view class="wg-label">{{ item.areaText }}</view>
        <view v-if="item.detailText" class="wg-label">{{ item.detailText }}</view>
        <view class="wg-addr-actions">
          <view v-if="item.isDefault !== 1" class="wg-btn wg-btn-plain wg-addr-btn" @click="onSetDefault(item)">
            设为默认
          </view>
          <view class="wg-btn wg-btn-plain wg-addr-btn" @click="openEdit(item)">编辑</view>
          <view class="wg-btn wg-btn-plain wg-addr-btn wg-danger" @click="askDelete(item)">删除</view>
        </view>
      </view>

      <view class="wg-btn" @click="openCreate">添加常用地址</view>
    </template>

    <!-- 新增/编辑表单（自绘弹层，适老大字） -->
    <view v-if="formVisible" class="wg-dialog-mask">
      <view class="wg-dialog-box">
        <text class="wg-dialog-text">{{ editing ? '修改这个地址' : '添加一个地址' }}</text>
        <view class="wg-label">标签</view>
        <input class="wg-input" placeholder="如：家 / 居委会 / 公园" :value="formLabel" @input="formLabel = $event.detail.value" />
        <view class="wg-label" style="margin-top: 10px">小区/区域</view>
        <input class="wg-input" placeholder="如：阳光小区" :value="formArea" @input="formArea = $event.detail.value" />
        <view class="wg-label" style="margin-top: 10px">楼栋门牌（选填）</view>
        <input class="wg-input" placeholder="上门类确认开始后才会给对方看" :value="formDetail" @input="formDetail = $event.detail.value" />
        <view class="wg-btn" style="margin-top: var(--wg-gap)" @click="submitForm">保存</view>
        <view class="wg-btn wg-btn-plain" style="margin-top: 10px" @click="formVisible = false">再想想</view>
      </view>
    </view>

    <!-- 删除二次确认（自绘：适老加大按钮） -->
    <view v-if="pendingDelete" class="wg-dialog-mask">
      <view class="wg-dialog-box">
        <text class="wg-dialog-text">确定要删除「{{ pendingDelete.label }}」这个地址吗？</text>
        <view class="wg-btn wg-delete-btn" @click="doDelete">删除</view>
        <view class="wg-btn wg-btn-plain" style="margin-top: 10px" @click="pendingDelete = null">再想想</view>
      </view>
    </view>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { listAddresses, createAddress, updateAddress, deleteAddress, setDefaultAddress } from '@/api/address'
import { MESSAGES } from '@/utils/constants'

/** 地址簿上限（详设 §3.3 B 系：每用户 ≤10 条，与后端 ProfileConstants.ADDRESS_MAX_COUNT 一致） */
const ADDRESS_MAX_COUNT = 10

/**
 * P15 地址簿管理（详设 §6.2）：列表 + 新增/编辑/删除/设默认。
 */
export default {
  data() {
    return {
      loading: true,
      addresses: [],
      formVisible: false,
      editing: null,
      formLabel: '',
      formArea: '',
      formDetail: '',
      pendingDelete: null
    }
  },
  computed: {
    userStore() {
      return useUserStore()
    }
  },
  onShow() {
    this.load()
  },
  methods: {
    async load() {
      try {
        this.addresses = await listAddresses()
      } catch (e) {
        console.error('[微光] 地址簿加载失败', e)
      } finally {
        this.loading = false
      }
    },
    openCreate() {
      if (this.addresses.length >= ADDRESS_MAX_COUNT) {
        uni.showToast({ title: '地址簿最多存 10 个常用地址', icon: 'none' })
        return
      }
      this.editing = null
      this.formLabel = ''
      this.formArea = ''
      this.formDetail = ''
      this.formVisible = true
    },
    openEdit(item) {
      this.editing = item
      this.formLabel = item.label || ''
      this.formArea = item.areaText || ''
      this.formDetail = item.detailText || ''
      this.formVisible = true
    },
    async submitForm() {
      const area = this.formArea.trim()
      if (!area) {
        uni.showToast({ title: '先填写小区/区域', icon: 'none' })
        return
      }
      const payload = {
        label: this.formLabel.trim() || '家',
        areaText: area,
        detailText: this.formDetail.trim() || null,
        isDefault: this.editing ? this.editing.isDefault : 0
      }
      try {
        if (this.editing) {
          await updateAddress(this.editing.id, payload)
        } else {
          await createAddress(payload)
        }
        this.formVisible = false
        this.load()
      } catch (e) {
        console.error('[微光] 地址保存失败', e)
      }
    },
    askDelete(item) {
      this.pendingDelete = item
    },
    async doDelete() {
      const target = this.pendingDelete
      this.pendingDelete = null
      try {
        await deleteAddress(target.id)
        this.load()
      } catch (e) {
        console.error('[微光] 地址删除失败', e)
      }
    },
    async onSetDefault(item) {
      try {
        await setDefaultAddress(item.id)
        this.load()
      } catch (e) {
        console.error('[微光] 设默认失败', e)
      }
    }
  }
}
</script>

<style lang="scss">
.wg-page {
  padding-bottom: 40px;
}

/* 适老差异：卡片整行可点、操作按钮加大（详设 §6.2 P15） */
.wg-addr-actions {
  display: flex;
  margin-top: 10px;
}

.wg-addr-btn {
  flex: 1;
  margin-right: 10px;
  font-size: var(--wg-font-base);
}

.wg-addr-btn:last-child {
  margin-right: 0;
}

.wg-delete-btn {
  background-color: var(--wg-danger);
}
</style>