<template>
  <view class="wg-page wg-address-page" :class="userStore.rootClass">
    <view v-if="loading" class="wg-card">
      <text>正在为您准备，请稍等</text>
    </view>

    <template v-else>
      <view v-if="addresses.length === 0" class="wg-empty">还没有常用地址，添加一个"家"吧</view>

      <!-- 4D-补：默认地址保底提示（历史数据可能出现零默认） -->
      <view v-else-if="!hasDefault" class="wg-label" style="margin: 0 4px 10px">
        还没有默认地址，点「设为默认」选一个吧，按距离排序会用到它
      </view>

      <view v-for="item in addresses" :key="item.id" class="wg-card">
        <view class="wg-card-head">
          <text class="wg-title">{{ item.label }}</text>
          <text v-if="item.isDefault === 1" class="wg-tag" style="margin: 0">默认</text>
        </view>
        <view class="wg-label">{{ item.areaText }}</view>
        <view v-if="item.detailText" class="wg-label">{{ item.detailText }}</view>
        <view class="wg-label">
          {{ hasGeo(item) ? '已记录地图位置，可用于计算距离' : '未记录地图位置，补上后可按距离排序' }}
        </view>
        <view v-if="!hasGeo(item)" class="wg-btn wg-btn-plain" style="margin-top: 8px" @click="openGeo(item)">
          补全地图位置
        </view>
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
        <input class="wg-input" maxlength="20" placeholder="如：家 / 居委会 / 公园" :value="formLabel" @input="formLabel = $event.detail.value" />
        <view class="wg-label" style="margin-top: 10px">小区/区域</view>
        <AddressAreaInput
          :value="formArea"
          placeholder="如：阳光小区"
          :focus="areaFocus"
          @input="onAreaInput"
          @select="onGeoSelect"
        />
        <view v-if="formLatitude !== null && formLongitude !== null" class="wg-label">
          已记录地图位置，可用于计算距离
        </view>
        <view class="wg-label" style="margin-top: 10px">楼栋门牌（选填）</view>
        <input class="wg-input" maxlength="100" placeholder="上门类确认开始后才会给对方看" :value="formDetail" @input="formDetail = $event.detail.value" />
        <!-- B：新增/编辑时一步设为默认；4D-补：默认地址不能直接取消，至少保留一条 -->
        <view class="wg-tags" style="margin-top: 12px">
          <view class="wg-chip" :class="{ 'wg-chip-active': formIsDefault }" @click="toggleFormDefault">
            {{ formIsDefault ? '已设为默认地址' : '设为默认地址' }}
          </view>
        </view>
        <view class="wg-btn" :class="{ 'wg-btn-off': saving }" style="margin-top: var(--wg-gap)" @click="submitForm">
          {{ saving ? '正在保存…' : '保存' }}
        </view>
        <view class="wg-btn wg-btn-plain" style="margin-top: 10px" @click="formVisible = false">再想想</view>
      </view>
    </view>

    <!-- 删除二次确认（自绘：适老加大按钮） -->
    <view v-if="pendingDelete" class="wg-dialog-mask">
      <view class="wg-dialog-box">
        <text class="wg-dialog-text">确定要删除「{{ pendingDelete.label }}」这个地址吗？</text>
        <view class="wg-btn wg-btn-danger" @click="doDelete">删除</view>
        <view class="wg-btn wg-btn-plain" style="margin-top: 10px" @click="pendingDelete = null">再想想</view>
      </view>
    </view>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { listAddresses, createAddress, updateAddress, deleteAddress, setDefaultAddress } from '@/api/address'
import AddressAreaInput from '@/components/AddressAreaInput.vue'

/** 地址簿上限（详设 §3.3 B 系：每用户 ≤10 条，与后端 ProfileConstants.ADDRESS_MAX_COUNT 一致） */
const ADDRESS_MAX_COUNT = 10

/**
 * P15 地址簿管理（详设 §6.2）：列表 + 新增/编辑/删除/设默认。
 */
export default {
  components: { AddressAreaInput },
  data() {
    return {
      loading: true,
      addresses: [],
      formVisible: false,
      editing: null,
      formLabel: '',
      formArea: '',
      formDetail: '',
      formLatitude: null,
      formLongitude: null,
      formIsDefault: false,
      /** 4D-补5：仅「补全地图位置」进入时聚焦小区输入框 */
      areaFocus: false,
      pendingDelete: null,
      /** 保存在途锁 */
      saving: false
    }
  },
  computed: {
    userStore() {
      return useUserStore()
    },
    /** 4D-补：是否存在默认地址（历史数据可能为零默认） */
    hasDefault() {
      return this.addresses.some((item) => item.isDefault === 1)
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
      this.formLatitude = null
      this.formLongitude = null
      // 第一条地址默认就是默认地址
      this.formIsDefault = this.addresses.length === 0
      this.areaFocus = false
      this.formVisible = true
    },
    openEdit(item) {
      this.editing = item
      this.formLabel = item.label || ''
      this.formArea = item.areaText || ''
      this.formDetail = item.detailText || ''
      this.formLatitude = item.latitude === undefined ? null : item.latitude
      this.formLongitude = item.longitude === undefined ? null : item.longitude
      this.formIsDefault = item.isDefault === 1
      this.areaFocus = false
      this.formVisible = true
    },
    /** 4D-补：该地址是否已记录地图坐标 */
    hasGeo(item) {
      return !!(item
        && item.latitude !== null && item.latitude !== undefined
        && item.longitude !== null && item.longitude !== undefined)
    },
    /** 4D-补3：未定位地址直接打开编辑，候选会出现在小区输入框下方 */
    openGeo(item) {
      this.openEdit(item)
      this.areaFocus = true
    },
    /** 4D-补：默认地址不允许直接取消，保证地址簿始终有一条默认 */
    toggleFormDefault() {
      if (this.formIsDefault) {
        uni.showToast({ title: '默认地址不能直接取消，请先把另一条设为默认', icon: 'none' })
        return
      }
      this.formIsDefault = true
    },
    /** 手动改动小区/区域后旧坐标失效，避免距离按旧位置计算 */
    onAreaInput(value) {
      this.formArea = value
      this.formLatitude = null
      this.formLongitude = null
    },
    onGeoSelect(poi) {
      this.formArea = poi.name || poi.address || ''
      this.formLatitude = poi.latitude
      this.formLongitude = poi.longitude
    },
    async submitForm() {
      if (this.saving) {
        return
      }
      const area = this.formArea.trim()
      if (!area) {
        uni.showToast({ title: '先填写小区/区域', icon: 'none' })
        return
      }
      const payload = {
        label: this.formLabel.trim() || '家',
        areaText: area,
        detailText: this.formDetail.trim() || null,
        latitude: this.formLatitude,
        longitude: this.formLongitude,
        isDefault: this.formIsDefault ? 1 : 0
      }
      this.saving = true
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
      } finally {
        this.saving = false
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
.wg-address-page {
  padding-bottom: 40px;
}

/* 适老差异：卡片整行可点、操作按钮加大（详设 §6.2 P15）；4D-补5：按钮过多时换行 */
.wg-addr-actions {
  display: flex;
  flex-wrap: wrap;
  margin-top: 10px;
}

.wg-addr-btn {
  flex: 1 1 30%;
  margin-right: 10px;
  margin-bottom: 8px;
  font-size: var(--wg-font-base);
}

.wg-addr-btn:last-child {
  margin-right: 0;
}
</style>